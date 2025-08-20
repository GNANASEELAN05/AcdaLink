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

/**
 * Shows ONLY meaningful projects for the signed-in user.
 * Skips deleted/empty placeholder nodes to prevent "Untitled / N/A" ghosts.
 */
public class MyProjects extends AppCompatActivity {

    private RecyclerView recyclerView;
    private MyProjectsAdapter adapter;
    private final ArrayList<ProjectModel> projectList = new ArrayList<>();
    private DatabaseReference projectsRef;
    private static final String TAG = "MyProjects";

    // Keys we consider as "project-like"
    private static final String[] TITLE_KEYS = new String[]{"projectTitle","title","name"};
    private static final String[] TYPE_KEYS  = new String[]{"projectType1","projectType","type"};
    private static final String[] LEVEL_KEYS = new String[]{"projectLevel","projectType2","level"};
    private static final String[] ABS_KEYS   = new String[]{"abstract","abstractText","projectAbstract"};
    private static final String[] FILE_KEYS  = new String[]{"files","file","attachments"};

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
                HashSet<String> addedIds = new HashSet<>();

                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                String currentUid = currentUser != null ? currentUser.getUid() : null;

                try {
                    for (DataSnapshot child : snapshot.getChildren()) {

                        // If projects are stored under /projects/{uid}/{projectId}
                        if (currentUid != null && child.getKey() != null && child.getKey().equals(currentUid)) {
                            for (DataSnapshot projSnap : child.getChildren()) {
                                if (!looksLikeMeaningfulProject(projSnap)) continue; // ⛔ skip empty/deleted nodes
                                ProjectModel model = buildModelFromSnapshot(projSnap);
                                if (model != null && !addedIds.contains(model.getId())) {
                                    addedIds.add(model.getId());
                                    projectList.add(model);
                                }
                            }
                            continue;
                        }

                        // If /projects/{projectId} (flat) – still ensure it's owned by current user
                        if (looksLikeProjectNode(child)) {
                            if (!isOwnedByCurrentUser(child, currentUid)) continue;
                            if (!looksLikeMeaningfulProject(child)) continue; // ⛔ skip empty/deleted nodes
                            ProjectModel model = buildModelFromSnapshot(child);
                            if (model != null && !addedIds.contains(model.getId())) {
                                addedIds.add(model.getId());
                                projectList.add(model);
                            }
                            continue;
                        }

                        // If /projects/{group}/{projectId} (grouped by dept/level/etc.)
                        for (DataSnapshot projSnap : child.getChildren()) {
                            if (!isOwnedByCurrentUser(projSnap, currentUid)) continue;
                            if (!looksLikeMeaningfulProject(projSnap)) continue; // ⛔ skip empty/deleted nodes
                            ProjectModel model = buildModelFromSnapshot(projSnap);
                            if (model != null && !addedIds.contains(model.getId())) {
                                addedIds.add(model.getId());
                                projectList.add(model);
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

    /** Return a model ONLY if the snapshot actually contains project fields. */
    private ProjectModel buildModelFromSnapshot(DataSnapshot snap) {
        try {
            if (!looksLikeMeaningfulProject(snap)) {
                return null; // 🚫 do not create ghosts
            }

            ProjectModel model = snap.getValue(ProjectModel.class);
            if (model == null) model = new ProjectModel();

            if (isEmpty(model.getId())) {
                model.setId(snap.getKey() != null ? snap.getKey() : "");
            }

            Map<String, Object> map;
            try {
                GenericTypeIndicator<Map<String, Object>> t = new GenericTypeIndicator<Map<String, Object>>() {};
                map = snap.getValue(t);
                if (map == null) map = new HashMap<>();
            } catch (Exception e) {
                Object v = snap.getValue();
                if (v instanceof Map) {
                    //noinspection unchecked
                    map = (Map<String, Object>) v;
                } else {
                    map = new HashMap<>();
                }
            }
            model.setExtraData(map);

            if (isEmpty(model.getTitle()))        model.setTitle(lookup(map, TITLE_KEYS));
            if (isEmpty(model.getProjectType1())) model.setProjectType1(lookup(map, TYPE_KEYS));
            if (isEmpty(model.getProjectLevel())) model.setProjectLevel(lookup(map, LEVEL_KEYS));
            if (isEmpty(model.getAbstractText())) model.setAbstractText(lookup(map, ABS_KEYS));
            if (isEmpty(model.getMethodology()))  model.setMethodology(lookup(map, "methodology","method","methods"));
            if (isEmpty(model.getSimilarity()))   model.setSimilarity(lookup(map, "similarity","similarityScore","plagiarism"));
            if (isEmpty(model.getAiGenerated()))  model.setAiGenerated(lookup(map, "aiGenerated","ai","aiScore","aiContent"));

            return model;
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse project snapshot", e);
            return null;
        }
    }

    /** Quick shape check to decide if a node *could* be a project. */
    private boolean looksLikeProjectNode(DataSnapshot snap) {
        return hasAnyChild(snap, TITLE_KEYS) ||
                hasAnyChild(snap, TYPE_KEYS)  ||
                hasAnyChild(snap, LEVEL_KEYS) ||
                hasAnyChild(snap, ABS_KEYS)   ||
                hasAnyChild(snap, FILE_KEYS);
    }

    /** Stronger check: confirms there is at least one non-empty project field or a files array/map. */
    private boolean looksLikeMeaningfulProject(DataSnapshot snap) {
        if (snap == null || !snap.exists()) return false;

        // If it has one of the project keys with a non-empty value, it's meaningful
        if (hasAnyChildWithNonEmptyValue(snap, TITLE_KEYS) ||
                hasAnyChildWithNonEmptyValue(snap, TYPE_KEYS)  ||
                hasAnyChildWithNonEmptyValue(snap, LEVEL_KEYS) ||
                hasAnyChildWithNonEmptyValue(snap, ABS_KEYS)) {
            return true;
        }

        // Files/attachments present?
        for (String k : FILE_KEYS) {
            DataSnapshot files = snap.child(k);
            if (files.exists() && files.getChildrenCount() > 0) return true;
        }

        // If the node is literally empty (no children), it's not meaningful.
        if (snap.getChildrenCount() == 0) return false;

        // Some schemas store everything in a map under a single node; consider non-empty maps
        for (DataSnapshot c : snap.getChildren()) {
            Object v = c.getValue();
            if (v instanceof Map && !((Map<?,?>) v).isEmpty()) {
                // still make sure at least *one* of the recognized keys exists somewhere
                if (hasAnyDescendantKey(snap, TITLE_KEYS, TYPE_KEYS, LEVEL_KEYS, ABS_KEYS, FILE_KEYS)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasAnyChild(DataSnapshot snap, String... keys) {
        for (String k : keys) if (snap.hasChild(k)) return true;
        return false;
    }

    private boolean hasAnyChildWithNonEmptyValue(DataSnapshot snap, String... keys) {
        for (String k : keys) {
            if (snap.hasChild(k)) {
                Object v = snap.child(k).getValue();
                if (v != null && !String.valueOf(v).trim().isEmpty() && !"null".equalsIgnoreCase(String.valueOf(v).trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAnyDescendantKey(DataSnapshot snap, String[]... keyGroups) {
        for (DataSnapshot c : snap.getChildren()) {
            for (String[] keys : keyGroups) {
                for (String k : keys) {
                    if (normalizeKey(c.getKey()).equals(normalizeKey(k))) return true;
                }
            }
            if (hasAnyDescendantKey(c, keyGroups)) return true;
        }
        return false;
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
        // direct
        for (String key : keys) {
            if (map.containsKey(key)) {
                Object val = map.get(key);
                if (val != null) return String.valueOf(val).trim();
            }
        }
        // case/format-insensitive
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
