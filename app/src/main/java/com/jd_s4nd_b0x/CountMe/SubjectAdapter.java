package com.jd_s4nd_b0x.CountMe.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.jd_s4nd_b0x.CountMe.R;
import com.jd_s4nd_b0x.CountMe.model.Subject;
import com.jd_s4nd_b0x.CountMe.util.AttendanceCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder> {

    public interface OnSubjectActionListener {
        void onMarkPresent(Subject subject, int position);
        void onMarkAbsent(Subject subject, int position);
        void onItemClick(Subject subject, int position);
        void onEditSubject(Subject subject, int position);
        void onDeleteSubject(Subject subject, int position);
    }

    private List<Subject> subjects = new ArrayList<>();
    private final OnSubjectActionListener listener;

    public SubjectAdapter(OnSubjectActionListener listener) {
        this.listener = listener;
    }

    public void setSubjects(List<Subject> subjects) {
        this.subjects = subjects == null ? new ArrayList<>() : subjects;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SubjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_subject, parent, false);
        return new SubjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SubjectViewHolder holder, int position) {
        Subject subject = subjects.get(position);
        Context context = holder.itemView.getContext();

        holder.tvSubjectName.setText(subject.getName());
        holder.tvSubjectCode.setText(subject.getCode() == null || subject.getCode().isEmpty() ? "" : subject.getCode());
        holder.tvCategoryBadge.setText(subject.getCategory());

        double pct = subject.getAttendancePercentage();
        holder.tvPercentage.setText(String.format(Locale.getDefault(), "%.1f%%", pct));

        int pctInt = (int) Math.round(pct);
        holder.progressAttendance.setProgress(pctInt);

        // Color coding logic based on target percentage
        int colorRes;
        if (pct >= subject.getTargetPercentage()) {
            colorRes = R.color.status_green;
        } else if (pct >= subject.getTargetPercentage() - 10) {
            colorRes = R.color.status_orange;
        } else {
            colorRes = R.color.status_red;
        }
        int color = ContextCompat.getColor(context, colorRes);
        holder.tvPercentage.setTextColor(color);
        holder.progressAttendance.setIndicatorColor(color);

        holder.tvClassesCount.setText(String.format(Locale.getDefault(), "%d Attended • %d Missed • %d Total", subject.getPresentCount(), subject.getAbsentCount(), subject.getTotalClasses()));

        // Status advice calculation
        if (pct >= subject.getTargetPercentage()) {
            int safeMiss = AttendanceCalculator.getSafeMissCount(subject);
            if (safeMiss > 0) {
                holder.tvStatusAdvice.setText(context.getString(R.string.safe_miss, safeMiss));
            } else {
                holder.tvStatusAdvice.setText(R.string.status_on_track);
            }
            holder.tvStatusAdvice.setTextColor(ContextCompat.getColor(context, R.color.status_green));
        } else {
            int required = AttendanceCalculator.getRequiredClassesToAttend(subject);
            holder.tvStatusAdvice.setText(context.getString(R.string.must_attend, required, subject.getTargetPercentage()));
            holder.tvStatusAdvice.setTextColor(ContextCompat.getColor(context, R.color.status_red));
        }

        // Click listeners
        holder.btnMarkPresent.setOnClickListener(v -> {
            if (listener != null) listener.onMarkPresent(subject, holder.getAdapterPosition());
        });

        holder.btnMarkAbsent.setOnClickListener(v -> {
            if (listener != null) listener.onMarkAbsent(subject, holder.getAdapterPosition());
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(subject, holder.getAdapterPosition());
        });

        holder.btnMoreOptions.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, holder.btnMoreOptions);
            popup.getMenu().add(R.string.edit_subject);
            popup.getMenu().add(R.string.delete_subject);
            popup.setOnMenuItemClickListener(item -> {
                if (item.getTitle().equals(context.getString(R.string.edit_subject))) {
                    if (listener != null) listener.onEditSubject(subject, holder.getAdapterPosition());
                    return true;
                } else if (item.getTitle().equals(context.getString(R.string.delete_subject))) {
                    if (listener != null) listener.onDeleteSubject(subject, holder.getAdapterPosition());
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    @Override
    public int getItemCount() {
        return subjects.size();
    }

    static class SubjectViewHolder extends RecyclerView.ViewHolder {
        TextView tvSubjectName, tvSubjectCode, tvCategoryBadge, tvPercentage, tvClassesCount, tvStatusAdvice;
        LinearProgressIndicator progressAttendance;
        MaterialButton btnMarkPresent, btnMarkAbsent;
        ImageButton btnMoreOptions;

        SubjectViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSubjectName = itemView.findViewById(R.id.tvSubjectName);
            tvSubjectCode = itemView.findViewById(R.id.tvSubjectCode);
            tvCategoryBadge = itemView.findViewById(R.id.tvCategoryBadge);
            tvPercentage = itemView.findViewById(R.id.tvPercentage);
            tvClassesCount = itemView.findViewById(R.id.tvClassesCount);
            tvStatusAdvice = itemView.findViewById(R.id.tvStatusAdvice);
            progressAttendance = itemView.findViewById(R.id.progressAttendance);
            btnMarkPresent = itemView.findViewById(R.id.btnMarkPresent);
            btnMarkAbsent = itemView.findViewById(R.id.btnMarkAbsent);
            btnMoreOptions = itemView.findViewById(R.id.btnMoreOptions);
        }
    }
}
