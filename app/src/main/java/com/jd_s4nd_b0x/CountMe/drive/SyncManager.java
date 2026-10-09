package com.jd_s4nd_b0x.CountMe.drive;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.jd_s4nd_b0x.CountMe.repository.AttendanceRepository;
import com.jd_s4nd_b0x.CountMe.repository.LocalAttendanceRepository;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Keeps the local copy and Drive:CountMe/countme_data.json in step. Last-writer-wins on the
 * snapshot's "modified" timestamp. Local edits are pushed after a short debounce.
 */
public final class SyncManager {

    public enum State {
        SIGNED_OUT,
        IDLE,
        RUNNING,
        FAILED,
        NEEDS_SIGN_IN
    }

    public interface Listener {
        /** Called on the main thread whenever {@link #getState()} changed. */
        void onSyncStateChanged();

        /** Called on the main thread when remote data replaced local data. */
        void onRemoteApplied();
    }

    public static final int INTERVAL_MANUAL = -1;
    public static final int INTERVAL_EVERY_CHANGE = 0;

    private static final String PREFS = "SyncPrefs";
    private static final String WORK_TAG = "countme_sync";
    private static final String WORK_PERIODIC = "countme_sync_periodic";
    private static final String WORK_PUSH = "countme_sync_push";
    private static final String KEY_INTERVAL = "interval_min";
    private static final String KEY_LAST_SYNC = "last_sync";

    private static final Object LOCK = new Object();
    private static SyncManager instance;

    private final Context context;
    private final AttendanceRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile Listener listener;
    private volatile State state;

    private final TokenSource tokens;
    private final Function<String, DriveClient> clients;

    /** Supplies a fresh access token, or null when the user has to sign in again. */
    @FunctionalInterface
    interface TokenSource {
        String get() throws ExecutionException, InterruptedException;
    }

    private SyncManager(Context context) {
        this(
                context,
                LocalAttendanceRepository.getInstance(context),
                () -> DriveAuth.silentToken(context.getApplicationContext()),
                DriveClient::new);
    }

    /** Test seam: lets unit tests inject a fake repository, token source and Drive endpoint. */
    SyncManager(
            Context context,
            AttendanceRepository repository,
            TokenSource tokenSource,
            Function<String, DriveClient> clientFactory) {
        this.context = context.getApplicationContext();
        this.repo = repository;
        this.tokens = tokenSource;
        this.clients = clientFactory;
        this.state = DriveAuth.isSignedIn(this.context) ? State.IDLE : State.SIGNED_OUT;
        repo.setChangeListener(this::scheduleSync);
    }

    public static SyncManager getInstance(Context context) {
        synchronized (LOCK) {
            if (instance == null) {
                instance = new SyncManager(context);
            }
            return instance;
        }
    }

    public void setListener(Listener l) {
        this.listener = l;
        if (l != null) {
            l.onSyncStateChanged();
        }
    }

    public State getState() {
        return state;
    }

    /**
     * Called after interactive authorization succeeded: records the account and does a first sync.
     */
    public void onSignedIn(String accessToken) {
        io.execute(
                () -> {
                    try {
                        String[] user = clients.apply(accessToken).getUser();
                        DriveAuth.saveAccount(context, user[0], user[1]);
                        setState(State.IDLE);
                        applySchedule();
                        runSync(accessToken);
                    } catch (IOException e) {
                        setState(State.FAILED);
                    }
                });
    }

    public void signOut() {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG);
        io.execute(
                () -> {
                    DriveAuth.signOut(context);
                    repo.clearAll();
                    setState(State.SIGNED_OUT);
                    main.post(
                            () -> {
                                Listener l = listener;
                                if (l != null) {
                                    l.onRemoteApplied();
                                }
                            });
                });
    }

    /** Minutes between automatic syncs; see INTERVAL_* constants. */
    public int getIntervalMinutes() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_INTERVAL, INTERVAL_EVERY_CHANGE);
    }

    public void setIntervalMinutes(int minutes) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_INTERVAL, minutes)
                .apply();
        applySchedule();
    }

    private static Constraints networkConstraint() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    /**
     * (Re)plans background sync with WorkManager. It survives app/process death, reboots and Doze,
     * and needs no special permission or battery-optimisation exemption. Android's minimum period
     * for periodic work is 15 minutes.
     */
    public void applySchedule() {
        WorkManager wm = WorkManager.getInstance(context);
        int interval = getIntervalMinutes();
        if (!DriveAuth.isSignedIn(context) || interval <= 0) {
            wm.cancelUniqueWork(WORK_PERIODIC);
            return;
        }
        wm.enqueueUniquePeriodicWork(
                WORK_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                new PeriodicWorkRequest.Builder(
                                SyncWorker.class, Math.max(15, interval), TimeUnit.MINUTES)
                        .setConstraints(networkConstraint())
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                        .addTag(WORK_TAG)
                        .build());
    }

    /** Local data changed: in "after every change" mode push soon, even if the app gets killed. */
    private void scheduleSync() {
        if (!DriveAuth.isSignedIn(context) || getIntervalMinutes() != INTERVAL_EVERY_CHANGE) {
            return;
        }
        WorkManager.getInstance(context)
                .enqueueUniqueWork(
                        WORK_PUSH,
                        ExistingWorkPolicy.REPLACE,
                        new OneTimeWorkRequest.Builder(SyncWorker.class)
                                .setInitialDelay(2, TimeUnit.SECONDS)
                                .setConstraints(networkConstraint())
                                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                                .addTag(WORK_TAG)
                                .build());
    }

    /** Sync on app start, honouring the user's chosen frequency. */
    public void autoSync() {
        int interval = getIntervalMinutes();
        if (!DriveAuth.isSignedIn(context) || interval == INTERVAL_MANUAL) {
            return;
        }
        long last =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getLong(KEY_LAST_SYNC, 0L);
        if (System.currentTimeMillis() - last >= Math.max(0, interval) * 60_000L) {
            syncNow();
        }
    }

    public void syncNow() {
        if (!DriveAuth.isSignedIn(context)) {
            return;
        }
        io.execute(this::syncBlocking);
    }

    /** Full sync on the calling (background) thread. Used by the UI executor and by SyncWorker. */
    public State syncBlocking() {
        if (!DriveAuth.isSignedIn(context)) {
            return State.SIGNED_OUT;
        }
        try {
            String token = tokens.get();
            if (token == null) {
                setState(State.NEEDS_SIGN_IN);
                return State.NEEDS_SIGN_IN;
            }
            return runSync(token);
        } catch (ExecutionException e) {
            setState(State.FAILED);
            return State.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            setState(State.FAILED);
            return State.FAILED;
        }
    }

    /** Runs on a background thread. */
    private synchronized State runSync(String token) {
        setState(State.RUNNING);
        try {
            DriveClient drive = clients.apply(token);
            String folder = drive.ensureRootFolder();
            String fileId = drive.findId(DriveClient.DATA_FILE, folder, null);
            boolean applied = false;
            if (fileId != null) {
                String remote = drive.downloadText(fileId);
                long remoteModified = new JSONObject(remote).optLong("modified", 0L);
                if (remoteModified > repo.getLastModified()) {
                    repo.importSnapshot(remote);
                    applied = true;
                }
            }
            if (!applied) {
                drive.upsert(
                        DriveClient.DATA_FILE,
                        "application/json",
                        folder,
                        repo.exportSnapshot().getBytes(StandardCharsets.UTF_8));
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                    .apply();
            setState(State.IDLE);
            if (applied) {
                main.post(
                        () -> {
                            Listener l = listener;
                            if (l != null) {
                                l.onRemoteApplied();
                            }
                        });
            }
            return State.IDLE;
        } catch (DriveClient.DriveException e) {
            State st = e.code == 401 || e.code == 403 ? State.NEEDS_SIGN_IN : State.FAILED;
            setState(st);
            return st;
        } catch (IOException | JSONException e) {
            setState(State.FAILED);
            return State.FAILED;
        }
    }

    /** Blocking helper for exports: uploads to Drive:CountMe/Exports. */
    public void uploadExport(String name, String mime, byte[] data)
            throws IOException, ExecutionException, InterruptedException {
        String token = tokens.get();
        if (token == null) {
            throw new IllegalStateException("Sign-in required");
        }
        DriveClient drive = clients.apply(token);
        drive.upsert(name, mime, drive.ensureExportsFolder(), data);
    }

    private void setState(State s) {
        state = s;
        main.post(
                () -> {
                    Listener l = listener;
                    if (l != null) {
                        l.onSyncStateChanged();
                    }
                });
    }
}
