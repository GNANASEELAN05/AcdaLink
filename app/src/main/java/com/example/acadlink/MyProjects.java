package com.example.acadlink;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

public class MyProjects extends AppCompatActivity {

    private RecyclerView recyclerView;
    private MyProjectsAdapter adapter;
    private ArrayList<ProjectModel> projectList = new ArrayList<>();
    private DatabaseReference projectsRef;
    private static final String TAG = "MyProjects";

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
            Intent intent = new Intent(MyProjects.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("navigateTo", "home");
            startActivity(intent);
            finish();
        });

        TextView toolbarTitle = findViewById(R.id.toolbarTitleTv);
        toolbarTitle.setText("My Projects");

        recyclerView = findViewById(R.id.projectsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new MyProjectsAdapter(projectList, R.layout.item_my_project, project -> {
            Intent i = new Intent(MyProjects.this, MyProjectSummaryActivity.class);
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

        projectsRef = FirebaseDatabase.getInstance().getReference("projects");
        projectsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                projectList.clear();
                HashSet<String> addedIds = new HashSet<>(); // Track added project IDs

                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                String currentUid = currentUser != null ? currentUser.getUid() : null;

                try {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        if (currentUid != null && child.getKey() != null && child.getKey().equals(currentUid)) {
                            for (DataSnapshot projSnap : child.getChildren()) {
                                ProjectModel model = buildModelFromSnapshot(projSnap);
                                if (model != null && !addedIds.contains(model.getId())) {
                                    addedIds.add(model.getId());
                                    projectList.add(model);
                                }
                            }
                        } else if (looksLikeProjectNode(child)) {
                            ProjectModel model = buildModelFromSnapshot(child);
                            if (model != null && isOwnedByCurrentUser(child, currentUid) && !addedIds.contains(model.getId())) {
                                addedIds.add(model.getId());
                                projectList.add(model);
                            }
                        } else {
                            for (DataSnapshot projSnap : child.getChildren()) {
                                ProjectModel model = buildModelFromSnapshot(projSnap);
                                if (model != null && isOwnedByCurrentUser(projSnap, currentUid) && !addedIds.contains(model.getId())) {
                                    addedIds.add(model.getId());
                                    projectList.add(model);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing projects", e);
                }

                adapter.notifyDataSetChanged();
                if (projectList.isEmpty()) {
                    Toast.makeText(MyProjects.this, "No projects uploaded", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(MyProjects.this,
                        "Failed to load projects: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private ProjectModel buildModelFromSnapshot(DataSnapshot snap) {
        try {
            ProjectModel model = snap.getValue(ProjectModel.class);
            if (model == null) model = new ProjectModel();

            if (model.getId() == null || model.getId().trim().isEmpty()) {
                model.setId(snap.getKey() != null ? snap.getKey() : "");
            }

            Map<String, Object> map = null;
            try {
                GenericTypeIndicator<Map<String, Object>> t = new GenericTypeIndicator<Map<String, Object>>() {};
                map = snap.getValue(t);
            } catch (Exception ignored) {}
            if (map == null) {
                Object v = snap.getValue();
                if (v instanceof Map) {
                    map = (Map<String, Object>) v;
                } else {
                    map = new HashMap<>();
                }
            }
            model.setExtraData(map);

            if (isEmpty(model.getTitle())) model.setTitle(lookup(map, "projectTitle", "title", "name"));
            if (isEmpty(model.getProjectType1())) model.setProjectType1(lookup(map, "projectType1", "projectType", "type"));
            if (isEmpty(model.getProjectLevel())) model.setProjectLevel(lookup(map, "projectLevel", "projectType2", "level"));
            if (isEmpty(model.getAbstractText())) model.setAbstractText(lookup(map, "abstract", "abstractText", "projectAbstract"));
            if (isEmpty(model.getMethodology())) model.setMethodology(lookup(map, "methodology", "method", "methods"));
            if (isEmpty(model.getSimilarity())) model.setSimilarity(lookup(map, "similarity", "similarityScore", "plagiarism"));
            if (isEmpty(model.getAiGenerated())) model.setAiGenerated(lookup(map, "aiGenerated", "ai", "aiScore", "aiContent"));

            return model;
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse project snapshot", e);
            return null;
        }
    }

    private boolean looksLikeProjectNode(DataSnapshot snap) {
        return snap.hasChild("projectTitle") || snap.hasChild("title") || snap.hasChild("abstract")
                || snap.hasChild("projectType1") || snap.hasChild("aiGenerated");
    }

    private boolean isOwnedByCurrentUser(DataSnapshot snap, String uid) {
        if (uid == null) return false;
        if (snap.hasChild("uid") && uid.equals(snap.child("uid").getValue(String.class))) return true;
        if (snap.hasChild("userId") && uid.equals(snap.child("userId").getValue(String.class))) return true;
        if (snap.hasChild("authorUid") && uid.equals(snap.child("authorUid").getValue(String.class))) return true;
        if (snap.hasChild("uploaderId") && uid.equals(snap.child("uploaderId").getValue(String.class))) return true;
        return false;
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String lookup(Map<String, Object> map, String... keys) {
        if (map == null) return "";
        for (String key : keys) {
            if (map.containsKey(key)) {
                Object val = map.get(key);
                if (val != null) return String.valueOf(val).trim();
            }
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String nk = normalizeKey(e.getKey());
            for (String k : keys) {
                if (nk.equals(normalizeKey(k)) && e.getValue() != null) {
                    return String.valueOf(e.getValue()).trim();
                }
            }
        }
        return "";
    }

    private String normalizeKey(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
