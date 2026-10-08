import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final List<SubjectItem> subjects = new ArrayList<>();
    private SubjectAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        subjects.add(new SubjectItem("Data Structures", 12, 14));
        subjects.add(new SubjectItem("Database Systems", 10, 14));
        subjects.add(new SubjectItem("Computer Networks", 9, 13));
        subjects.add(new SubjectItem("Mathematics", 15, 16));

        RecyclerView recyclerView = findViewById(R.id.subjectRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubjectAdapter(subjects);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.addSubjectButton).setOnClickListener(view -> showAddSubjectDialog());
    }

    private void showAddSubjectDialog() {
        EditText input = new EditText(this);
        input.setHint("Subject name");

        new AlertDialog.Builder(this)
                .setTitle("Add a subject")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = input.getText().toString().trim();

                    if (!name.isEmpty()) {
                        subjects.add(new SubjectItem(name, 0, 0));
                        adapter.notifyItemInserted(subjects.size() - 1);
                    }
                })
                .show();
    }
}