package com.jd_s4nd_b0x.CountMe.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.jd_s4nd_b0x.CountMe.R;
import com.jd_s4nd_b0x.CountMe.adapter.SubjectAdapter;
import com.jd_s4nd_b0x.CountMe.model.AttendanceLog;
import com.jd_s4nd_b0x.CountMe.model.Subject;
import com.jd_s4nd_b0x.CountMe.repository.AttendanceRepository;
import com.jd_s4nd_b0x.CountMe.repository.LocalAttendanceRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReportsActivity extends AppCompatActivity {

    private AttendanceRepository repository;

    private TextView tvReportOverallPct, tvReportTotalAttended, tvReportTotalMissed, tvReportNoLogs;
    private RecyclerView rvReportSubjects, rvReportLogs;

    private SubjectAdapter subjectAdapter;
    private ReportLogAdapter logAdapter;

    public static void start(Context context) {
        Intent intent = new Intent(context, ReportsActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reports);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.reportsMainLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        repository = LocalAttendanceRepository.getInstance(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvReportOverallPct = findViewById(R.id.tvReportOverallPct);
        tvReportTotalAttended = findViewById(R.id.tvReportTotalAttended);
        tvReportTotalMissed = findViewById(R.id.tvReportTotalMissed);
        tvReportNoLogs = findViewById(R.id.tvReportNoLogs);

        rvReportSubjects = findViewById(R.id.rvReportSubjects);
        rvReportLogs = findViewById(R.id.rvReportLogs);

        rvReportSubjects.setLayoutManager(new LinearLayoutManager(this));
        rvReportLogs.setLayoutManager(new LinearLayoutManager(this));

        setupAdapters();
        loadReportsData();
    }

    private void setupAdapters() {
        subjectAdapter = new SubjectAdapter(new SubjectAdapter.OnSubjectActionListener() {
            @Override
            public void onMarkPresent(Subject subject, int position) {
                subject.markPresent();
                repository.updateSubject(subject);
                repository.addLog(new AttendanceLog(null, subject.getId(), AttendanceLog.Status.PRESENT, System.currentTimeMillis(), "Report Mark"));
                loadReportsData();
            }

            @Override
            public void onMarkAbsent(Subject subject, int position) {
                subject.markAbsent();
                repository.updateSubject(subject);
                repository.addLog(new AttendanceLog(null, subject.getId(), AttendanceLog.Status.ABSENT, System.currentTimeMillis(), "Report Mark"));
                loadReportsData();
            }

            @Override
            public void onItemClick(Subject subject, int position) {
                SubjectDetailActivity.start(ReportsActivity.this, subject.getId());
            }

            @Override
            public void onEditSubject(Subject subject, int position) {
                // Handled in MainActivity
            }

            @Override
            public void onDeleteSubject(Subject subject, int position) {
                // Handled in MainActivity
            }
        });

        rvReportSubjects.setAdapter(subjectAdapter);
    }

    private void loadReportsData() {
        List<Subject> subjects = repository.getAllSubjects();
        subjectAdapter.setSubjects(subjects);

        int totalAttended = 0;
        int totalMissed = 0;
        int totalClasses = 0;

        Map<String, String> subjectMap = new HashMap<>();
        for (Subject s : subjects) {
            totalAttended += s.getPresentCount();
            totalMissed += s.getAbsentCount();
            totalClasses += s.getTotalClasses();
            subjectMap.put(s.getId(), s.getName());
        }

        double overallPct = totalClasses == 0 ? 100.0 : (totalAttended * 100.0) / totalClasses;

        tvReportOverallPct.setText(String.format(Locale.getDefault(), "%.1f%%", overallPct));
        tvReportTotalAttended.setText(String.valueOf(totalAttended));
        tvReportTotalMissed.setText(String.valueOf(totalMissed));

        List<AttendanceLog> logs = repository.getAllLogs();
        logAdapter = new ReportLogAdapter(logs, subjectMap);
        rvReportLogs.setAdapter(logAdapter);

        if (logs.isEmpty()) {
            tvReportNoLogs.setVisibility(View.VISIBLE);
            rvReportLogs.setVisibility(View.GONE);
        } else {
            tvReportNoLogs.setVisibility(View.GONE);
            rvReportLogs.setVisibility(View.VISIBLE);
        }
    }

    private static class ReportLogAdapter extends RecyclerView.Adapter<ReportLogAdapter.ReportLogViewHolder> {

        private final List<AttendanceLog> logs;
        private final Map<String, String> subjectNameMap;

        ReportLogAdapter(List<AttendanceLog> logs, Map<String, String> subjectNameMap) {
            this.logs = logs == null ? new ArrayList<>() : logs;
            this.subjectNameMap = subjectNameMap == null ? new HashMap<>() : subjectNameMap;
        }

        @NonNull
        @Override
        public ReportLogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attendance_log, parent, false);
            return new ReportLogViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ReportLogViewHolder holder, int position) {
            AttendanceLog log = logs.get(position);
            Context context = holder.itemView.getContext();

            holder.tvLogStatus.setText(log.getStatus().name());
            if (log.getStatus() == AttendanceLog.Status.PRESENT) {
                holder.tvLogStatus.setTextColor(ContextCompat.getColor(context, R.color.status_green));
            } else if (log.getStatus() == AttendanceLog.Status.ABSENT) {
                holder.tvLogStatus.setTextColor(ContextCompat.getColor(context, R.color.status_red));
            } else {
                holder.tvLogStatus.setTextColor(ContextCompat.getColor(context, R.color.status_orange));
            }

            String subjectName = subjectNameMap.containsKey(log.getSubjectId()) ? subjectNameMap.get(log.getSubjectId()) : "Subject";
            String dateStr = DateFormat.format("MMM dd, yyyy - hh:mm a", log.getTimestamp()).toString();
            holder.tvLogDate.setText(String.format("%s • %s", subjectName, dateStr));

            if (log.getNote() != null && !log.getNote().isEmpty()) {
                holder.tvLogNote.setVisibility(View.VISIBLE);
                holder.tvLogNote.setText(log.getNote());
            } else {
                holder.tvLogNote.setVisibility(View.GONE);
            }

            // Hide individual delete button on summary report view
            holder.btnDeleteLog.setVisibility(View.GONE);
        }

        @Override
        public int getItemCount() {
            return logs.size();
        }

        static class ReportLogViewHolder extends RecyclerView.ViewHolder {
            TextView tvLogStatus, tvLogDate, tvLogNote;
            View btnDeleteLog;

            ReportLogViewHolder(@NonNull View itemView) {
                super(itemView);
                tvLogStatus = itemView.findViewById(R.id.tvLogStatus);
                tvLogDate = itemView.findViewById(R.id.tvLogDate);
                tvLogNote = itemView.findViewById(R.id.tvLogNote);
                btnDeleteLog = itemView.findViewById(R.id.btnDeleteLog);
            }
        }
    }
}
