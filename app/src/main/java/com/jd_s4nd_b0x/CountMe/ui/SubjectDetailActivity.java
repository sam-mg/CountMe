package com.jd_s4nd_b0x.CountMe.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.jd_s4nd_b0x.CountMe.R;
import com.jd_s4nd_b0x.CountMe.adapter.AttendanceLogAdapter;
import com.jd_s4nd_b0x.CountMe.model.AttendanceLog;
import com.jd_s4nd_b0x.CountMe.model.Subject;
import com.jd_s4nd_b0x.CountMe.repository.AttendanceRepository;
import com.jd_s4nd_b0x.CountMe.repository.LocalAttendanceRepository;
import com.jd_s4nd_b0x.CountMe.util.AttendanceCalculator;

import java.util.List;
import java.util.Locale;

public class SubjectDetailActivity extends AppCompatActivity {

    public static final String EXTRA_SUBJECT_ID = "extra_subject_id";

    private AttendanceRepository repository;
    private Subject subject;

    private TextView tvDetailSubjectName;
    private TextView tvDetailSubjectCode;
    private TextView tvDetailPercentage;
    private TextView tvDetailAttendedCount;
    private TextView tvDetailMissedCount;
    private TextView tvDetailTotalCount;
    private TextView tvDetailStatusBanner;
    private TextView tvNoLogs;
    private LinearProgressIndicator progressDetail;
    private RecyclerView rvAttendanceLogs;
    private AttendanceLogAdapter logAdapter;

    public static void start(Context context, String subjectId) {
        Intent intent = new Intent(context, SubjectDetailActivity.class);
        intent.putExtra(EXTRA_SUBJECT_ID, subjectId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_subject_detail);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.detailMainLayout),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        repository = LocalAttendanceRepository.getInstance(this);

        String subjectId = getIntent().getStringExtra(EXTRA_SUBJECT_ID);
        if (subjectId == null) {
            finish();
            return;
        }

        subject = repository.getSubjectById(subjectId);
        if (subject == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvDetailSubjectName = findViewById(R.id.tvDetailSubjectName);
        tvDetailSubjectCode = findViewById(R.id.tvDetailSubjectCode);
        tvDetailPercentage = findViewById(R.id.tvDetailPercentage);
        tvDetailAttendedCount = findViewById(R.id.tvDetailAttendedCount);
        tvDetailMissedCount = findViewById(R.id.tvDetailMissedCount);
        tvDetailTotalCount = findViewById(R.id.tvDetailTotalCount);
        tvDetailStatusBanner = findViewById(R.id.tvDetailStatusBanner);
        tvNoLogs = findViewById(R.id.tvNoLogs);
        progressDetail = findViewById(R.id.progressDetail);

        MaterialButton btnDetailPresent = findViewById(R.id.btnDetailPresent);
        btnDetailPresent.setOnClickListener(v -> markStatus(AttendanceLog.Status.PRESENT));
        MaterialButton btnDetailAbsent = findViewById(R.id.btnDetailAbsent);
        btnDetailAbsent.setOnClickListener(v -> markStatus(AttendanceLog.Status.ABSENT));

        rvAttendanceLogs = findViewById(R.id.rvAttendanceLogs);
        rvAttendanceLogs.setLayoutManager(new LinearLayoutManager(this));

        logAdapter =
                new AttendanceLogAdapter(
                        (log, position) -> {
                            repository.deleteLog(log.getId());
                            // Revert subject count if needed
                            boolean wasPresent = log.getStatus() == AttendanceLog.Status.PRESENT;
                            subject.undoLastMark(wasPresent);
                            repository.updateSubject(subject);
                            updateUI();
                            Toast.makeText(this, R.string.log_removed, Toast.LENGTH_SHORT).show();
                        });
        rvAttendanceLogs.setAdapter(logAdapter);

        updateUI();
    }

    private void markStatus(AttendanceLog.Status status) {
        if (status == AttendanceLog.Status.PRESENT) {
            subject.markPresent();
        } else {
            subject.markAbsent();
        }
        repository.updateSubject(subject);

        AttendanceLog log =
                new AttendanceLog(
                        null,
                        subject.getId(),
                        status,
                        System.currentTimeMillis(),
                        getString(R.string.log_manual_entry));
        repository.addLog(log);

        updateUI();
    }

    private void updateUI() {
        tvDetailSubjectName.setText(subject.getName());
        String codeCategory =
                (subject.getCode() == null || subject.getCode().isEmpty()
                                ? ""
                                : subject.getCode() + " • ")
                        + subject.getCategory();
        tvDetailSubjectCode.setText(codeCategory);

        double pct = subject.getAttendancePercentage();
        tvDetailPercentage.setText(String.format(Locale.getDefault(), "%.1f%%", pct));
        progressDetail.setProgress((int) Math.round(pct));

        tvDetailAttendedCount.setText(String.valueOf(subject.getPresentCount()));
        tvDetailMissedCount.setText(String.valueOf(subject.getAbsentCount()));
        tvDetailTotalCount.setText(String.valueOf(subject.getTotalClasses()));

        int colorRes =
                pct >= subject.getTargetPercentage()
                        ? R.color.status_green
                        : (pct >= subject.getTargetPercentage() - 10
                                ? R.color.status_orange
                                : R.color.status_red);
        int color = ContextCompat.getColor(this, colorRes);
        tvDetailPercentage.setTextColor(color);
        progressDetail.setIndicatorColor(color);

        if (pct >= subject.getTargetPercentage()) {
            int safeMiss = AttendanceCalculator.getSafeMissCount(subject);
            if (safeMiss > 0) {
                tvDetailStatusBanner.setText(
                        getResources().getQuantityString(R.plurals.safe_miss, safeMiss, safeMiss));
            } else {
                tvDetailStatusBanner.setText(R.string.status_on_track);
            }
            tvDetailStatusBanner.setTextColor(ContextCompat.getColor(this, R.color.status_green));
        } else {
            int required = AttendanceCalculator.getRequiredClassesToAttend(subject);
            tvDetailStatusBanner.setText(
                    getResources()
                            .getQuantityString(
                                    R.plurals.must_attend,
                                    required,
                                    required,
                                    subject.getTargetPercentage()));
            tvDetailStatusBanner.setTextColor(ContextCompat.getColor(this, R.color.status_red));
        }

        List<AttendanceLog> logs = repository.getLogsForSubject(subject.getId());
        logAdapter.setLogs(logs);

        if (logs.isEmpty()) {
            tvNoLogs.setVisibility(View.VISIBLE);
            rvAttendanceLogs.setVisibility(View.GONE);
        } else {
            tvNoLogs.setVisibility(View.GONE);
            rvAttendanceLogs.setVisibility(View.VISIBLE);
        }
    }
}
