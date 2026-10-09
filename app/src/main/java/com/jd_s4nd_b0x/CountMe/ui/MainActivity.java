package com.jd_s4nd_b0x.CountMe.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.jd_s4nd_b0x.CountMe.R;
import com.jd_s4nd_b0x.CountMe.adapter.CategoryAdapter;
import com.jd_s4nd_b0x.CountMe.adapter.SubjectAdapter;
import com.jd_s4nd_b0x.CountMe.drive.SyncManager;
import com.jd_s4nd_b0x.CountMe.model.AttendanceLog;
import com.jd_s4nd_b0x.CountMe.model.Category;
import com.jd_s4nd_b0x.CountMe.model.Subject;
import com.jd_s4nd_b0x.CountMe.repository.AttendanceRepository;
import com.jd_s4nd_b0x.CountMe.repository.LocalAttendanceRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements SyncManager.Listener {

    private AttendanceRepository repository;
    private SyncManager sync;

    private TextView tvOverallPercentage;
    private TextView tvOverallStatus;
    private TextView tvEmptyState;
    private CircularProgressIndicator progressOverall;
    private SubjectAdapter subjectAdapter;
    private CategoryAdapter categoryAdapter;
    private final List<Category> categories = new ArrayList<>();
    private String searchQuery = "";
    private String selectedCategory; // null = all

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        repository = LocalAttendanceRepository.getInstance(this);
        sync = SyncManager.getInstance(this);

        tvOverallPercentage = findViewById(R.id.tvOverallPercentage);
        tvOverallStatus = findViewById(R.id.tvOverallStatus);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        progressOverall = findViewById(R.id.progressOverall);

        RecyclerView rvSubjects = findViewById(R.id.rvSubjects);
        rvSubjects.setLayoutManager(new LinearLayoutManager(this));
        subjectAdapter =
                new SubjectAdapter(
                        new SubjectAdapter.OnSubjectActionListener() {
                            @Override
                            public void onMarkPresent(Subject subject) {
                                mark(subject, AttendanceLog.Status.PRESENT);
                            }

                            @Override
                            public void onMarkAbsent(Subject subject) {
                                mark(subject, AttendanceLog.Status.ABSENT);
                            }

                            @Override
                            public void onItemClick(Subject subject) {
                                SubjectDetailActivity.start(MainActivity.this, subject.getId());
                            }

                            @Override
                            public void onEditSubject(Subject subject) {
                                showSubjectDialog(subject);
                            }

                            @Override
                            public void onDeleteSubject(Subject subject) {
                                new MaterialAlertDialogBuilder(MainActivity.this)
                                        .setTitle(R.string.delete_subject)
                                        .setMessage(subject.getName())
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .setPositiveButton(
                                                android.R.string.ok,
                                                (d, w) -> {
                                                    repository.deleteSubject(subject.getId());
                                                    refresh();
                                                })
                                        .show();
                            }
                        });
        rvSubjects.setAdapter(subjectAdapter);

        RecyclerView rvCategories = findViewById(R.id.rvCategories);
        rvCategories.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter =
                new CategoryAdapter(
                        categories,
                        (category, position) -> {
                            selectedCategory = position == 0 ? null : category.getName();
                            refresh();
                        });
        rvCategories.setAdapter(categoryAdapter);

        ((TextInputEditText) findViewById(R.id.etSearch))
                .addTextChangedListener(
                        new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

                            @Override
                            public void onTextChanged(CharSequence s, int a, int b, int c) {}

                            @Override
                            public void afterTextChanged(Editable s) {
                                searchQuery = s.toString().trim().toLowerCase(Locale.getDefault());
                                refresh();
                            }
                        });

        findViewById(R.id.fabAddSubject).setOnClickListener(v -> showSubjectDialog(null));
        findViewById(R.id.btnReports).setOnClickListener(v -> ReportsActivity.start(this));
        findViewById(R.id.btnSettings)
                .setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        sync.setListener(this);
        sync.autoSync();
    }

    @Override
    protected void onStop() {
        sync.setListener(null);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onSyncStateChanged() {}

    @Override
    public void onRemoteApplied() {
        refresh();
    }

    // ---- subjects ------------------------------------------------------------------------

    private void mark(Subject subject, AttendanceLog.Status status) {
        if (status == AttendanceLog.Status.PRESENT) {
            subject.markPresent();
        } else {
            subject.markAbsent();
        }
        repository.updateSubject(subject);
        repository.addLog(
                new AttendanceLog(null, subject.getId(), status, System.currentTimeMillis(), ""));
        refresh();
    }

    private void showSubjectDialog(Subject existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_subject, null);
        TextInputEditText etName = view.findViewById(R.id.etSubjectName);
        TextInputEditText etCode = view.findViewById(R.id.etSubjectCode);
        MaterialAutoCompleteTextView etCategory = view.findViewById(R.id.etCategory);
        setupCategoryDropdown(etCategory, existing == null ? "General" : existing.getCategory());
        TextInputEditText etPresent = view.findViewById(R.id.etPresentCount);
        TextInputEditText etTotal = view.findViewById(R.id.etTotalClasses);
        TextInputEditText etTarget = view.findViewById(R.id.etTargetPercentage);

        if (existing != null) {
            TextView dialogTitle = view.findViewById(R.id.tvDialogTitle);
            dialogTitle.setText(R.string.edit_subject);
            etName.setText(existing.getName());
            etCode.setText(existing.getCode());
            etPresent.setText(String.valueOf(existing.getPresentCount()));
            etTotal.setText(String.valueOf(existing.getTotalClasses()));
            etTarget.setText(String.valueOf(existing.getTargetPercentage()));
        }

        new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(
                        android.R.string.ok,
                        (d, w) -> {
                            String name = text(etName);
                            if (name.isEmpty()) {
                                return;
                            }
                            String category = text(etCategory);
                            if (category.isEmpty()) {
                                category = "General";
                            }
                            int total = Math.max(0, parse(etTotal, 0));
                            int present = Math.min(total, Math.max(0, parse(etPresent, 0)));
                            int target = Math.min(100, Math.max(1, parse(etTarget, 75)));
                            if (existing == null) {
                                repository.addSubject(
                                        new Subject(
                                                null,
                                                name,
                                                text(etCode),
                                                category,
                                                present,
                                                total,
                                                target));
                            } else {
                                existing.setName(name);
                                existing.setCode(text(etCode));
                                existing.setCategory(category);
                                existing.setPresentCount(present);
                                existing.setTotalClasses(total);
                                existing.setTargetPercentage(target);
                                existing.setLastUpdated(System.currentTimeMillis());
                                repository.updateSubject(existing);
                            }
                            refresh();
                        })
                .show();
    }

    /** Dropdown of known categories; the last entry lets the user create a new one. */
    private void setupCategoryDropdown(MaterialAutoCompleteTextView field, String initial) {
        Set<String> names = new LinkedHashSet<>(Arrays.asList("General", "Theory", "Lab"));
        for (Subject s : repository.getAllSubjects()) {
            names.add(s.getCategory());
        }
        if (initial != null && !initial.isEmpty()) {
            names.add(initial);
        }
        List<String> options = new ArrayList<>(names);
        String addNew = getString(R.string.category_add_new);
        options.add(addNew);

        field.setSimpleItems(options.toArray(new String[0]));
        field.setText(initial, false);
        field.setOnItemClickListener(
                (parent, v, position, id) -> {
                    if (!addNew.equals(options.get(position))) {
                        return;
                    }
                    field.setText(initial, false); // revert until a valid name is entered
                    EditText input = new EditText(this);
                    input.setHint(R.string.category_new_hint);
                    input.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                    input.setSingleLine();
                    FrameLayout box = new FrameLayout(this);
                    int pad = Math.round(20 * getResources().getDisplayMetrics().density);
                    box.setPadding(pad, pad / 2, pad, 0);
                    box.addView(input);
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.category_new_title)
                            .setView(box)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(
                                    android.R.string.ok,
                                    (d, w) -> {
                                        String name = input.getText().toString().trim();
                                        if (name.isEmpty()) {
                                            return;
                                        }
                                        options.add(options.size() - 1, name);
                                        field.setSimpleItems(options.toArray(new String[0]));
                                        field.setText(name, false);
                                    })
                            .show();
                });
    }

    private static String text(TextView et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private static int parse(TextView et, int fallback) {
        try {
            return Integer.parseInt(text(et));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---- rendering -----------------------------------------------------------------------

    private void refresh() {
        List<Subject> all = repository.getAllSubjects();
        renderCategories(all);
        renderOverall(all);
        List<Subject> shown = filter(all);
        subjectAdapter.setSubjects(shown);
        tvEmptyState.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void renderCategories(List<Subject> all) {
        Set<String> names = new LinkedHashSet<>();
        for (Subject s : all) {
            names.add(s.getCategory());
        }
        if (selectedCategory != null && !names.contains(selectedCategory)) {
            selectedCategory = null;
        }
        List<Category> updated = new ArrayList<>();
        updated.add(new Category(getString(R.string.category_all), selectedCategory == null));
        for (String n : names) {
            updated.add(new Category(n, n.equals(selectedCategory)));
        }
        categoryAdapter.setCategories(updated);
    }

    private void renderOverall(List<Subject> all) {
        int present = 0;
        int total = 0;
        int onTrack = 0;
        for (Subject s : all) {
            present += s.getPresentCount();
            total += s.getTotalClasses();
            if (s.getAttendancePercentage() >= s.getTargetPercentage()) {
                onTrack++;
            }
        }
        double overall = total == 0 ? 100.0 : present * 100.0 / total;
        tvOverallPercentage.setText(String.format(Locale.getDefault(), "%.1f%%", overall));
        progressOverall.setProgress((int) Math.round(overall));
        tvOverallStatus.setText(
                getResources()
                        .getQuantityString(
                                R.plurals.overall_status, all.size(), onTrack, all.size()));
    }

    private List<Subject> filter(List<Subject> all) {
        List<Subject> shown = new ArrayList<>();
        for (Subject s : all) {
            if (matchesCategory(s) && matchesSearch(s)) {
                shown.add(s);
            }
        }
        return shown;
    }

    private boolean matchesCategory(Subject s) {
        return selectedCategory == null || selectedCategory.equals(s.getCategory());
    }

    private boolean matchesSearch(Subject s) {
        if (searchQuery.isEmpty()) {
            return true;
        }
        String code = s.getCode() == null ? "" : s.getCode();
        return s.getName().toLowerCase(Locale.getDefault()).contains(searchQuery)
                || code.toLowerCase(Locale.getDefault()).contains(searchQuery);
    }
}
