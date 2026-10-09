package com.jd_s4nd_b0x.CountMe.drive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.jd_s4nd_b0x.CountMe.model.Subject;
import com.jd_s4nd_b0x.CountMe.repository.AttendanceRepository;
import com.jd_s4nd_b0x.CountMe.repository.LocalAttendanceRepository;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class SyncManagerTest {

    private static final String NO_FILES = "{\"files\":[]}";

    private Context context;
    private AttendanceRepository repo;
    private MockWebServer server;
    private String token = "tok";
    private SyncManager sync;

    @Before
    public void setUp() throws IOException {
        context = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(context);
        context.getSharedPreferences("SyncPrefs", Context.MODE_PRIVATE).edit().clear().apply();
        repo = LocalAttendanceRepository.getInstance(context);
        repo.clearAll();

        server = new MockWebServer();
        server.start();
        String base = server.url("/").toString();
        sync =
                new SyncManager(
                        context,
                        repo,
                        () -> token,
                        t -> new DriveClient(t, base + "drive", base + "upload"));
        repo.setChangeListener(null); // keep WorkManager out of the picture unless a test wants it
        DriveAuth.saveAccount(context, "Sam", "s@x.com");
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
        context.getSharedPreferences("DriveAuthPrefs", Context.MODE_PRIVATE).edit().clear().apply();
        repo.setChangeListener(null);
        repo.clearAll();
    }

    private void enqueue(String... bodies) {
        for (String b : bodies) {
            server.enqueue(new MockResponse().setBody(b));
        }
    }

    private static String snapshot(long modified, String subjectName) throws Exception {
        JSONObject subject =
                new JSONObject()
                        .put("id", "s1")
                        .put("name", subjectName)
                        .put("code", "")
                        .put("category", "Core")
                        .put("presentCount", 1)
                        .put("totalClasses", 2)
                        .put("targetPercentage", 75)
                        .put("lastUpdated", modified);
        return new JSONObject()
                .put("app", "CountMe")
                .put("version", 1)
                .put("modified", modified)
                .put("subjects", new org.json.JSONArray().put(subject))
                .put("logs", new org.json.JSONArray())
                .toString();
    }

    @Test
    public void signedOutDoesNothing() {
        context.getSharedPreferences("DriveAuthPrefs", Context.MODE_PRIVATE).edit().clear().apply();
        assertEquals(SyncManager.State.SIGNED_OUT, sync.syncBlocking());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void missingTokenMeansTheUserMustSignInAgain() {
        token = null;
        assertEquals(SyncManager.State.NEEDS_SIGN_IN, sync.syncBlocking());
        assertEquals(SyncManager.State.NEEDS_SIGN_IN, sync.getState());
    }

    @Test
    public void firstSyncUploadsLocalDataWhenDriveHasNoFile() throws Exception {
        repo.addSubject(new Subject(null, "Maths", "", "Core", 1, 2, 75));
        enqueue("{\"files\":[{\"id\":\"root\"}]}", NO_FILES, NO_FILES, "{\"id\":\"file1\"}");

        assertEquals(SyncManager.State.IDLE, sync.syncBlocking());

        assertEquals(4, server.getRequestCount());
        server.takeRequest();
        server.takeRequest();
        server.takeRequest();
        RecordedRequest upload = server.takeRequest();
        assertTrue(upload.getPath().contains("uploadType=multipart"));
        String body = upload.getBody().readUtf8();
        assertTrue(body.contains(DriveClient.DATA_FILE));
        assertTrue(body.contains("Maths"));
    }

    @Test
    public void newerRemoteDataReplacesLocalAndNotifiesTheListener() throws Exception {
        repo.addSubject(new Subject(null, "Old", "", "Core", 1, 2, 75));
        long future = System.currentTimeMillis() + 100_000;
        enqueue(
                "{\"files\":[{\"id\":\"root\"}]}",
                "{\"files\":[{\"id\":\"d1\"}]}",
                snapshot(future, "FromDrive"));
        AtomicInteger applied = new AtomicInteger();
        sync.setListener(
                new SyncManager.Listener() {
                    @Override
                    public void onSyncStateChanged() {
                        // state changes are asserted through the return value
                    }

                    @Override
                    public void onRemoteApplied() {
                        applied.incrementAndGet();
                    }
                });

        assertEquals(SyncManager.State.IDLE, sync.syncBlocking());
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(1, repo.getAllSubjects().size());
        assertEquals("FromDrive", repo.getAllSubjects().get(0).getName());
        assertEquals(3, server.getRequestCount()); // nothing uploaded
        assertEquals(1, applied.get());
    }

    @Test
    public void newerLocalDataOverwritesTheDriveFile() throws Exception {
        enqueue(
                "{\"files\":[{\"id\":\"root\"}]}",
                "{\"files\":[{\"id\":\"d1\"}]}",
                snapshot(1L, "Stale"));
        repo.addSubject(new Subject(null, "Fresh", "", "Core", 1, 2, 75)); // modified = now
        server.enqueue(new MockResponse().setBody("{\"files\":[{\"id\":\"d1\"}]}"));
        server.enqueue(new MockResponse().setBody("{}"));

        assertEquals(SyncManager.State.IDLE, sync.syncBlocking());

        assertEquals("Fresh", repo.getAllSubjects().get(0).getName());
        server.takeRequest();
        server.takeRequest();
        server.takeRequest();
        server.takeRequest();
        RecordedRequest patch = server.takeRequest();
        assertEquals("PATCH", patch.getHeader("X-HTTP-Method-Override"));
        assertTrue(patch.getBody().readUtf8().contains("Fresh"));
    }

    @Test
    public void unauthorizedFromDriveAsksForSignIn() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{}"));
        assertEquals(SyncManager.State.NEEDS_SIGN_IN, sync.syncBlocking());
    }

    @Test
    public void serverErrorsAreReportedAsFailedSoWorkManagerRetries() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));
        assertEquals(SyncManager.State.FAILED, sync.syncBlocking());
    }

    @Test
    public void corruptRemoteDataFailsWithoutTouchingLocalData() throws Exception {
        repo.addSubject(new Subject(null, "Mine", "", "Core", 1, 2, 75));
        enqueue("{\"files\":[{\"id\":\"root\"}]}", "{\"files\":[{\"id\":\"d1\"}]}", "not json");
        assertEquals(SyncManager.State.FAILED, sync.syncBlocking());
        assertEquals("Mine", repo.getAllSubjects().get(0).getName());
    }

    @Test
    public void exportsGoIntoTheExportsFolder() throws Exception {
        enqueue(
                "{\"files\":[{\"id\":\"root\"}]}",
                NO_FILES,
                "{\"id\":\"exp\"}",
                NO_FILES,
                "{\"id\":\"f\"}");
        sync.uploadExport("r.csv", "text/csv", "a,b".getBytes(StandardCharsets.UTF_8));
        server.takeRequest();
        server.takeRequest();
        server.takeRequest();
        server.takeRequest();
        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"parents\":[\"exp\"]"));
        assertTrue(body.contains("a,b"));
    }

    @Test
    public void exportWithoutTokenIsRejected() throws Exception {
        token = null;
        try {
            sync.uploadExport("r.csv", "text/csv", new byte[0]);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void intervalDefaultsToEveryChangeAndIsRemembered() {
        assertEquals(SyncManager.INTERVAL_EVERY_CHANGE, sync.getIntervalMinutes());
        sync.setIntervalMinutes(60);
        assertEquals(60, sync.getIntervalMinutes());
    }

    @Test
    public void periodicWorkIsScheduledForTimedIntervalsAndCancelledOtherwise() throws Exception {
        WorkManager wm = WorkManager.getInstance(context);
        sync.setIntervalMinutes(30);
        assertEquals(1, activeWork(wm, "countme_sync_periodic"));
        sync.setIntervalMinutes(SyncManager.INTERVAL_MANUAL);
        assertEquals(0, activeWork(wm, "countme_sync_periodic"));
    }

    @Test
    public void everyChangeModeQueuesAPushWhenDataChanges() throws Exception {
        sync =
                new SyncManager(
                        context,
                        repo,
                        () -> token,
                        t -> new DriveClient(t, "http://x/", "http://x/"));
        repo.addSubject(new Subject(null, "A", "", "Core", 0, 0, 75));
        assertEquals(1, activeWork(WorkManager.getInstance(context), "countme_sync_push"));
        repo.setChangeListener(null);
    }

    @Test
    public void manualModeNeverQueuesAutomaticPushes() throws Exception {
        sync.setIntervalMinutes(SyncManager.INTERVAL_MANUAL);
        sync =
                new SyncManager(
                        context,
                        repo,
                        () -> token,
                        t -> new DriveClient(t, "http://x/", "http://x/"));
        repo.addSubject(new Subject(null, "A", "", "Core", 0, 0, 75));
        assertEquals(0, activeWork(WorkManager.getInstance(context), "countme_sync_push"));
        repo.setChangeListener(null);
    }

    private static int activeWork(WorkManager wm, String name)
            throws ExecutionException, InterruptedException {
        List<WorkInfo> infos = wm.getWorkInfosForUniqueWork(name).get();
        int active = 0;
        for (WorkInfo info : infos) {
            if (!info.getState().isFinished()) {
                active++;
            }
        }
        return active;
    }
}
