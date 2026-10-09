package com.jd_s4nd_b0x.CountMe.adapter;

import android.content.Context;
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
import com.jd_s4nd_b0x.CountMe.util.ListDiff;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder> {

    public interface OnSubjectActionListener {
        void onMarkPresent(Subject subject);

        void onMarkAbsent(Subject subject);

        void onItemClick(Subject subject);

        void onEditSubject(Subject subject);

        void onDeleteSubject(Subject subject);
    }

    private List<Subject> subjects = new ArrayList<>();
    private List<String> ids = new ArrayList<>();
    private List<String> keys = new ArrayList<>();
    private final OnSubjectActionListener listener;

    public SubjectAdapter(OnSubjectActionListener listener) {
        this.listener = listener;
    }

    public void setSubjects(List<Subject> subjects) {
        List<String> oldIds = ids;
        List<String> oldKeys = keys;
        this.subjects = subjects == null ? new ArrayList<>() : subjects;
        ids = new ArrayList<>();
        keys = new ArrayList<>();
        for (Subject s : this.subjects) {
            ids.add(s.getId());
            keys.add(
                    String.join(
                            "|",
                            s.getName(),
                            String.valueOf(s.getCode()),
                            String.valueOf(s.getCategory()),
                            String.valueOf(s.getPresentCount()),
                            String.valueOf(s.getTotalClasses()),
                            String.valueOf(s.getTargetPercentage())));
        }
        ListDiff.update(this, oldIds, oldKeys, ids, keys);
    }

    @NonNull
    @Override
    public SubjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_subject, parent, false);
        return new SubjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SubjectViewHolder holder, int position) {
        Subject subject = subjects.get(position);
        Context context = holder.itemView.getContext();
        bindSummary(holder, subject, context);
        bindAdvice(holder, subject, context);
        bindActions(holder, subject, context);
    }

    private void bindSummary(SubjectViewHolder holder, Subject subject, Context context) {
        holder.tvSubjectName.setText(subject.getName());
        holder.tvSubjectCode.setText(subject.getCode() == null ? "" : subject.getCode());
        holder.tvCategoryBadge.setText(subject.getCategory());

        double pct = subject.getAttendancePercentage();
        holder.tvPercentage.setText(String.format(Locale.getDefault(), "%.1f%%", pct));
        holder.progressAttendance.setProgress((int) Math.round(pct));

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

        holder.tvClassesCount.setText(
                context.getString(
                        R.string.class_counts,
                        subject.getPresentCount(),
                        subject.getAbsentCount(),
                        subject.getTotalClasses()));
    }

    private void bindAdvice(SubjectViewHolder holder, Subject subject, Context context) {
        if (subject.getAttendancePercentage() >= subject.getTargetPercentage()) {
            int safeMiss = AttendanceCalculator.getSafeMissCount(subject);
            if (safeMiss > 0) {
                holder.tvStatusAdvice.setText(
                        context.getResources()
                                .getQuantityString(R.plurals.safe_miss, safeMiss, safeMiss));
            } else {
                holder.tvStatusAdvice.setText(R.string.status_on_track);
            }
            holder.tvStatusAdvice.setTextColor(
                    ContextCompat.getColor(context, R.color.status_green));
        } else {
            int required = AttendanceCalculator.getRequiredClassesToAttend(subject);
            holder.tvStatusAdvice.setText(
                    context.getResources()
                            .getQuantityString(
                                    R.plurals.must_attend,
                                    required,
                                    required,
                                    subject.getTargetPercentage()));
            holder.tvStatusAdvice.setTextColor(ContextCompat.getColor(context, R.color.status_red));
        }
    }

    private void bindActions(SubjectViewHolder holder, Subject subject, Context context) {
        holder.btnMarkPresent.setOnClickListener(
                v -> {
                    if (listener != null) {
                        listener.onMarkPresent(subject);
                    }
                });
        holder.btnMarkAbsent.setOnClickListener(
                v -> {
                    if (listener != null) {
                        listener.onMarkAbsent(subject);
                    }
                });
        holder.itemView.setOnClickListener(
                v -> {
                    if (listener != null) {
                        listener.onItemClick(subject);
                    }
                });
        holder.btnMoreOptions.setOnClickListener(v -> showMenu(holder, subject, context));
    }

    private void showMenu(SubjectViewHolder holder, Subject subject, Context context) {
        PopupMenu popup = new PopupMenu(context, holder.btnMoreOptions);
        popup.getMenu().add(0, R.string.edit_subject, 0, R.string.edit_subject);
        popup.getMenu().add(0, R.string.delete_subject, 1, R.string.delete_subject);
        popup.setOnMenuItemClickListener(
                item -> {
                    if (listener == null) {
                        return false;
                    }
                    if (item.getItemId() == R.string.edit_subject) {
                        listener.onEditSubject(subject);
                        return true;
                    }
                    if (item.getItemId() == R.string.delete_subject) {
                        listener.onDeleteSubject(subject);
                        return true;
                    }
                    return false;
                });
        popup.show();
    }

    @Override
    public int getItemCount() {
        return subjects.size();
    }

    static class SubjectViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSubjectName;
        final TextView tvSubjectCode;
        final TextView tvCategoryBadge;
        final TextView tvPercentage;
        final TextView tvClassesCount;
        final TextView tvStatusAdvice;
        final LinearProgressIndicator progressAttendance;
        final MaterialButton btnMarkPresent;
        final MaterialButton btnMarkAbsent;
        final ImageButton btnMoreOptions;

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
