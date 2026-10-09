package com.jd_s4nd_b0x.CountMe.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.jd_s4nd_b0x.CountMe.R;
import com.jd_s4nd_b0x.CountMe.drive.DriveAuth;
import com.jd_s4nd_b0x.CountMe.drive.SyncManager;
import com.jd_s4nd_b0x.CountMe.export.Exporter;
import com.jd_s4nd_b0x.CountMe.repository.AttendanceRepository;
import com.jd_s4nd_b0x.CountMe.repository.LocalAttendanceRepository;
import com.jd_s4nd_b0x.CountMe.util.ThemePrefs;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;

public class SettingsActivity extends AppCompatActivity implements SyncManager.Listener {

    private static final int[] INTERVALS = {
        SyncManager.INTERVAL_EVERY_CHANGE, 15, 60, 24 * 60, SyncManager.INTERVAL_MANUAL
    };
    private static final int[] INTERVAL_LABELS = {
        R.string.sync_every_change,
        R.string.sync_15m,
        R.string.sync_hourly,
        R.string.sync_daily,
        R.string.sync_manual
    };

    private AttendanceRepository repository;
    private SyncManager sync;
    private TextView tvAccountSub;
    private TextView tvSyncSub;
    private byte[] pendingExportData;

    private final ActivityResultLauncher<IntentSenderRequest> authLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartIntentSenderForResult(),
                    result -> {
                        Intent resultData = result.getData();
                        if (result.getResultCode() != Activity.RESULT_OK || resultData == null) {
                            toast(R.string.sign_in_failed);
                            return;
                        }
                        try {
                            AuthorizationResult auth =
                                    Identity.getAuthorizationClient(this)
                                            .getAuthorizationResultFromIntent(resultData);
                            if (auth.getAccessToken() == null) {
                                toast(R.string.sign_in_failed);
                            } else {
                                sync.onSignedIn(auth.getAccessToken());
                            }
                        } catch (ApiException e) {
                            toast(R.string.sign_in_failed);
                        }
                    });

    private final ActivityResultLauncher<Intent> saveLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Intent saved = result.getData();
                        Uri uri = saved == null ? null : saved.getData();
                        byte[] data = pendingExportData;
                        pendingExportData = null;
                        if (result.getResultCode() != Activity.RESULT_OK
                                || uri == null
                                || data == null) {
                            return;
                        }
                        new Thread(
                                        () -> {
                                            boolean ok = writeToUri(uri, data);
                                            runOnUiThread(
                                                    () ->
                                                            toast(
                                                                    ok
                                                                            ? R.string.export_done
                                                                            : R.string
                                                                                    .export_failed));
                                        })
                                .start();
                    });

    private boolean writeToUri(Uri uri, byte[] data) {
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) {
                return false;
            }
            os.write(data);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.settingsMain),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        repository = LocalAttendanceRepository.getInstance(this);
        sync = SyncManager.getInstance(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        tvAccountSub = findViewById(R.id.tvAccountSub);
        tvSyncSub = findViewById(R.id.tvSyncSub);

        MaterialSwitch switchDark = findViewById(R.id.switchDark);
        switchDark.setChecked(ThemePrefs.isDark(this));
        switchDark.setOnCheckedChangeListener((b, checked) -> ThemePrefs.setDark(this, checked));

        findViewById(R.id.rowAccount)
                .setOnClickListener(
                        v -> {
                            if (DriveAuth.isSignedIn(this)) {
                                showAccountDialog();
                            } else {
                                startSignIn();
                            }
                        });
        findViewById(R.id.rowPrivacy)
                .setOnClickListener(
                        v -> startActivity(new Intent(this, PrivacyPolicyActivity.class)));
        findViewById(R.id.rowTerms)
                .setOnClickListener(
                        v -> startActivity(new Intent(this, TermsOfServiceActivity.class)));
        findViewById(R.id.rowSync).setOnClickListener(v -> showSyncDialog());
        findViewById(R.id.rowExport).setOnClickListener(v -> showExportDialog());
        findViewById(R.id.rowDelete)
                .setOnClickListener(
                        v ->
                                new MaterialAlertDialogBuilder(this)
                                        .setTitle(R.string.settings_delete_all)
                                        .setMessage(R.string.delete_all_confirm)
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .setPositiveButton(
                                                android.R.string.ok,
                                                (d, w) -> {
                                                    repository.deleteAllSubjects();
                                                    toast(R.string.delete_all_done);
                                                })
                                        .show());
    }

    @Override
    protected void onStart() {
        super.onStart();
        sync.setListener(this);
        updateSummaries();
    }

    @Override
    protected void onStop() {
        sync.setListener(null);
        super.onStop();
    }

    @Override
    public void onSyncStateChanged() {
        updateSummaries();
    }

    @Override
    public void onRemoteApplied() {}

    private void updateSummaries() {
        if (DriveAuth.isSignedIn(this)) {
            String status;
            switch (sync.getState()) {
                case RUNNING:
                    status = getString(R.string.sync_running);
                    break;
                case FAILED:
                    status = getString(R.string.sync_failed);
                    break;
                case NEEDS_SIGN_IN:
                    status = getString(R.string.sign_in_failed);
                    break;
                default:
                    status = getString(R.string.sync_idle);
            }
            tvAccountSub.setText(
                    getString(R.string.account_status, DriveAuth.getEmail(this), status));
        } else {
            tvAccountSub.setText(R.string.sign_in_google);
        }
        int current = sync.getIntervalMinutes();
        for (int i = 0; i < INTERVALS.length; i++) {
            if (INTERVALS[i] == current) {
                tvSyncSub.setText(INTERVAL_LABELS[i]);
            }
        }
    }

    // ---- sign-in -------------------------------------------------------------------------

    private void startSignIn() {
        Identity.getAuthorizationClient(this)
                .authorize(DriveAuth.request())
                .addOnSuccessListener(
                        result -> {
                            PendingIntent pending = result.getPendingIntent();
                            if (result.hasResolution() && pending != null) {
                                authLauncher.launch(
                                        new IntentSenderRequest.Builder(pending.getIntentSender())
                                                .build());
                            } else if (result.getAccessToken() != null) {
                                sync.onSignedIn(result.getAccessToken());
                            } else {
                                toast(R.string.sign_in_failed);
                            }
                        })
                .addOnFailureListener(e -> toast(R.string.sign_in_failed));
    }

    private void showAccountDialog() {
        String name = DriveAuth.getName(this);
        String email = DriveAuth.getEmail(this);
        new MaterialAlertDialogBuilder(this)
                .setTitle(name == null || name.isEmpty() ? email : name + "\n" + email)
                .setMessage(R.string.drive_privacy_note)
                .setNeutralButton(R.string.sync_now, (d, w) -> sync.syncNow())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(
                        R.string.sign_out,
                        (d, w) ->
                                new MaterialAlertDialogBuilder(this)
                                        .setMessage(R.string.sign_out_confirm)
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .setPositiveButton(
                                                R.string.sign_out, (d2, w2) -> sync.signOut())
                                        .show())
                .show();
    }

    private void showSyncDialog() {
        String[] labels = new String[INTERVALS.length];
        int checked = 0;
        for (int i = 0; i < INTERVALS.length; i++) {
            labels[i] = getString(INTERVAL_LABELS[i]);
            if (INTERVALS[i] == sync.getIntervalMinutes()) {
                checked = i;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_sync_frequency)
                .setSingleChoiceItems(
                        labels,
                        checked,
                        (d, which) -> {
                            sync.setIntervalMinutes(INTERVALS[which]);
                            updateSummaries();
                            d.dismiss();
                        })
                .show();
    }

    // ---- export --------------------------------------------------------------------------

    private void showExportDialog() {
        String[] labels = {
            getString(R.string.export_json),
            getString(R.string.export_csv),
            getString(R.string.export_pdf)
        };
        Exporter.Format[] formats = Exporter.Format.values();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.export_data)
                .setItems(labels, (d, which) -> showDestinationDialog(formats[which]))
                .show();
    }

    private void showDestinationDialog(Exporter.Format format) {
        String[] where = {getString(R.string.export_device), getString(R.string.export_drive)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.export_where)
                .setItems(
                        where,
                        (d, which) -> {
                            if (which == 0) {
                                exportToDevice(format);
                            } else {
                                exportToDrive(format);
                            }
                        })
                .show();
    }

    private void exportToDevice(Exporter.Format format) {
        new Thread(
                        () -> {
                            try {
                                byte[] data = Exporter.export(repository, format);
                                runOnUiThread(
                                        () -> {
                                            pendingExportData = data;
                                            saveLauncher.launch(
                                                    new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                                            .addCategory(Intent.CATEGORY_OPENABLE)
                                                            .setType(format.mime)
                                                            .putExtra(
                                                                    Intent.EXTRA_TITLE,
                                                                    Exporter.fileName(format)));
                                        });
                            } catch (IOException e) {
                                runOnUiThread(() -> toast(R.string.export_failed));
                            }
                        })
                .start();
    }

    private void exportToDrive(Exporter.Format format) {
        if (!DriveAuth.isSignedIn(this)) {
            toast(R.string.export_drive_needs_signin);
            return;
        }
        new Thread(
                        () -> {
                            try {
                                byte[] data = Exporter.export(repository, format);
                                sync.uploadExport(Exporter.fileName(format), format.mime, data);
                                runOnUiThread(() -> toast(R.string.export_done));
                            } catch (IOException | ExecutionException e) {
                                runOnUiThread(() -> toast(R.string.export_failed));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                runOnUiThread(() -> toast(R.string.export_failed));
                            }
                        })
                .start();
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }
}
