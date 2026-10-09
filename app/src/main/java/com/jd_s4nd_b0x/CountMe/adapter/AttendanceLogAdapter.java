package com.jd_s4nd_b0x.CountMe.adapter;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.jd_s4nd_b0x.CountMe.R;
import com.jd_s4nd_b0x.CountMe.model.AttendanceLog;
import com.jd_s4nd_b0x.CountMe.util.ListDiff;

import java.util.ArrayList;
import java.util.List;

public class AttendanceLogAdapter extends RecyclerView.Adapter<AttendanceLogAdapter.LogViewHolder> {

    @FunctionalInterface
    public interface OnLogDeleteListener {
        void onDeleteLog(AttendanceLog log);
    }

    private List<AttendanceLog> logs = new ArrayList<>();
    private List<String> ids = new ArrayList<>();
    private List<String> keys = new ArrayList<>();
    private final OnLogDeleteListener listener;

    public AttendanceLogAdapter(OnLogDeleteListener listener) {
        this.listener = listener;
    }

    public void setLogs(List<AttendanceLog> logs) {
        List<String> oldIds = ids;
        List<String> oldKeys = keys;
        this.logs = logs == null ? new ArrayList<>() : logs;
        ids = new ArrayList<>();
        keys = new ArrayList<>();
        for (AttendanceLog l : this.logs) {
            ids.add(l.getId());
            keys.add(l.getStatus() + "|" + l.getTimestamp() + "|" + l.getNote());
        }
        ListDiff.update(this, oldIds, oldKeys, ids, keys);
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_attendance_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
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

        String dateStr = DateFormat.format("MMM dd, yyyy - hh:mm a", log.getTimestamp()).toString();
        holder.tvLogDate.setText(dateStr);

        if (log.getNote() != null && !log.getNote().isEmpty()) {
            holder.tvLogNote.setVisibility(View.VISIBLE);
            holder.tvLogNote.setText(log.getNote());
        } else {
            holder.tvLogNote.setVisibility(View.GONE);
        }

        holder.btnDeleteLog.setOnClickListener(
                v -> {
                    if (listener != null) {
                        listener.onDeleteLog(log);
                    }
                });
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView tvLogStatus;
        TextView tvLogDate;
        TextView tvLogNote;
        ImageButton btnDeleteLog;

        LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLogStatus = itemView.findViewById(R.id.tvLogStatus);
            tvLogDate = itemView.findViewById(R.id.tvLogDate);
            tvLogNote = itemView.findViewById(R.id.tvLogNote);
            btnDeleteLog = itemView.findViewById(R.id.btnDeleteLog);
        }
    }
}
