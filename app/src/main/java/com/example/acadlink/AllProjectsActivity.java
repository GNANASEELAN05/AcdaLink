package com.example.acadlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.regex.Pattern;

public class AllProjectsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private MyProjectsAdapter adapter;
    private ArrayList<ProjectModel> projectList = new ArrayList<>();
    private DatabaseReference projectsRef;

    // Regex to roughly detect Firebase UIDs (28 chars alphanumeric, mixed case, no spaces)
    private static final Pattern UID_PATTERN = Pattern.compile("^[A-Za-z0-9]{28}$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_my_projects);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageButton toolbarBackBtn = findViewById(R.id.toolbarBackBtn);
        toolbarBackBtn.setOnClickListener(v -> {
            Intent intent = new Intent(AllProjectsActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("navigateTo", "home");
            startActivity(intent);
            finish();
        });

        TextView toolbarTitle = findViewById(R.id.toolbarTitleTv);
        toolbarTitle.setText("All Projects");

        recyclerView = findViewById(R.id.projectsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new MyProjectsAdapter(projectList, R.layout.item_all_project, project -> {
            Intent i = new Intent(AllProjectsActivity.this, AllProjectSummaryActivity.class);
            i.putExtra("projectId", project.getId());
            i.putExtra("title", project.getTitle());
            i.putExtra("projectType1", project.getProjectType1());
            i.putExtra("projectLevel", project.getProjectLevel());
            i.putExtra("abstract", project.getAbstractText());
            i.putExtra("methodology", project.getMethodology());
            i.putExtra("similarity", project.getSimilarity());
            i.putExtra("aiGenerated", project.getAiGenerated());

            if (project.getFileInfoList() != null) {
                i.putStringArrayListExtra("fileInfoList", new ArrayList<>(project.getFileInfoList()));
            }

            startActivity(i);
        });

        recyclerView.setAdapter(adapter);

        // Load only global projects
        projectsRef = FirebaseDatabase.getInstance().getReference("projects");
        projectsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                projectList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String key = child.getKey();
                    if (key == null) continue;

                    // Skip per-user folders
                    if (UID_PATTERN.matcher(key).matches()) {
                        continue;
                    }

                    // Only add valid project nodes
                    try {
                        ProjectModel model = child.getValue(ProjectModel.class);
                        if (model != null) {
                            model.setId(key);
                            projectList.add(model);
                        }
                    } catch (Exception ignored) {}
                }

                adapter.notifyDataSetChanged();

                if (projectList.isEmpty()) {
                    Toast.makeText(AllProjectsActivity.this, "No projects found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(AllProjectsActivity.this,
                        "Failed to load projects: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}
