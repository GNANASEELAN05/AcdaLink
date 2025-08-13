package com.example.acadlink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MyProjectSummaryActivity extends AppCompatActivity {

    private TextView titleTv, typeTv, levelTv, abstractTv, methodologyTv, similarityTv, aiTv, filesTv;

    // Intent / runtime fields
    private String projectId;
    private String intentTitle, intentProjectType1, intentProjectLevel, intentAbstract, intentMethodology, intentSimilarity, intentAi;
    private ArrayList<String> intentFileInfoList;

    // prefs prefix keys
    private static final String PREFS_NAME = "proj_temp";
    private static final String LEGACY_KEY = "current_project"; // kept for compatibility

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_my_project_summary);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageButton backBtn = findViewById(R.id.psBackBtn);
        backBtn.setOnClickListener(v -> onBackPressed());

        titleTv = findViewById(R.id.psTitleTv);
        typeTv = findViewById(R.id.psTypeTv);
        levelTv = findViewById(R.id.psLevelTv);
        abstractTv = findViewById(R.id.psAbstractTv);
        methodologyTv = findViewById(R.id.psMethodologyTv);
        similarityTv = findViewById(R.id.psSimilarityTv);
        aiTv = findViewById(R.id.psAiTv);
        filesTv = findViewById(R.id.psFilesTv);

        // read raw intent extras
        Intent it = getIntent();
        projectId = safeTrim(it.getStringExtra("projectId"));

        intentTitle = safeTrim(it.getStringExtra("title"));
        intentProjectType1 = safeTrim(it.getStringExtra("projectType1"));
        intentProjectLevel = safeTrim(it.getStringExtra("projectLevel"));
        intentAbstract = safeTrim(it.getStringExtra("abstract"));
        intentMethodology = safeTrim(it.getStringExtra("methodology"));
        intentSimilarity = safeTrim(it.getStringExtra("similarity"));
        intentAi = safeTrim(it.getStringExtra("aiGenerated"));
        intentFileInfoList = it.getStringArrayListExtra("fileInfoList");

        if (projectId != null && !projectId.isEmpty()) {
            fetchFromFirebase(projectId);
        } else if (hasIntentData()) {
            populateFromIntentAndCache();
        } else {
            loadFromPrefsAndPopulate(null);
        }
    }

    // -------------------- Firebase fetch --------------------
    private void fetchFromFirebase(String id) {
        DatabaseReference projectsRef = FirebaseDatabase.getInstance().getReference("projects").child(id);
        projectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                try {
                    if (snapshot != null && snapshot.exists()) {
                        String title = findStringInSnapshot(snapshot, "projectTitle", "title", "name");
                        String type1 = findStringInSnapshot(snapshot, "projectType1", "projectType", "type");
                        String level = findStringInSnapshot(snapshot, "projectType2", "projectLevel", "level");
                        String abs = findStringInSnapshot(snapshot, "abstract", "Abstract");
                        String meth = findStringInSnapshot(snapshot, "methodology", "method");
                        String sim = findStringInSnapshot(snapshot, "similarity");
                        String ai = findStringInSnapshot(snapshot, "aiGenerated", "ai");

                        List<Map<String, Object>> filesList = null;
                        if (snapshot.hasChild("files")) {
                            Object fobj = snapshot.child("files").getValue();
                            if (fobj instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> fl = (List<Map<String, Object>>) fobj;
                                filesList = fl;
                            }
                        } else {
                            Object topVal = snapshot.getValue();
                            if (topVal instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> m = (Map<String, Object>) topVal;
                                Object candidate = findObjectInMapByKeyNames(m, new String[]{"files", "file", "attachments"});
                                if (candidate instanceof List) {
                                    @SuppressWarnings("unchecked")
                                    List<Map<String, Object>> fl = (List<Map<String, Object>>) candidate;
                                    filesList = fl;
                                }
                            }
                        }

                        String finalTitle = firstNonEmpty(title, intentTitle, "N/A");
                        String finalType = firstNonEmpty(type1, intentProjectType1, "N/A");
                        String finalLevel = firstNonEmpty(level, intentProjectLevel, "N/A");
                        String finalAbstract = firstNonEmpty(abs, intentAbstract, "N/A");
                        String finalMethod = firstNonEmpty(meth, intentMethodology, "N/A");
                        String finalSim = firstNonEmpty(sim, intentSimilarity, "N/A");
                        String finalAi = firstNonEmpty(ai, intentAi, "N/A");

                        populateUI(finalTitle, finalType, finalLevel, finalAbstract, finalMethod, finalSim, finalAi, filesList);
                        saveSnapshotToPrefs(projectId, finalTitle, finalType, finalLevel, finalAbstract, finalMethod, finalSim, finalAi, filesList);
                        return;
                    }
                    loadFromPrefsAndPopulate(projectId);
                } catch (Exception e) {
                    Log.e("ProjectSummary", "Error reading Firebase snapshot: " + e.getMessage());
                    loadFromPrefsAndPopulate(projectId);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("ProjectSummary", "Firebase read cancelled: " + error.getMessage());
                loadFromPrefsAndPopulate(projectId);
            }
        });
    }

    // -------------------- Populate --------------------
    private void populateFromIntentAndCache() {
        String finalTitle = firstNonEmpty(intentTitle, "N/A");
        String finalType = firstNonEmpty(intentProjectType1, "N/A");
        String finalLevel = firstNonEmpty(intentProjectLevel, "N/A");
        String finalAbstract = firstNonEmpty(intentAbstract, "N/A");
        String finalMethod = firstNonEmpty(intentMethodology, "N/A");
        String finalSim = firstNonEmpty(intentSimilarity, "N/A");
        String finalAi = firstNonEmpty(intentAi, "N/A");

        populateUI(finalTitle, finalType, finalLevel, finalAbstract, finalMethod, finalSim, finalAi, null);
        String keyForTitle = keyForProject(null, finalTitle);
        saveSnapshotToPrefsKey(keyForTitle, finalTitle, finalType, finalLevel, finalAbstract, finalMethod, finalSim, finalAi, null);
    }

    private void populateUI(String title, String type, String level, String abs, String meth, String sim, String ai, List<Map<String, Object>> filesList) {
        runOnUiThread(() -> {
            titleTv.setText(notEmptyOrDefault(title, "N/A"));
            typeTv.setText(notEmptyOrDefault(type, "N/A"));
            levelTv.setText(notEmptyOrDefault(level, "N/A"));
            abstractTv.setText(notEmptyOrDefault(abs, "N/A"));
            methodologyTv.setText(notEmptyOrDefault(meth, "N/A"));
            similarityTv.setText(notEmptyOrDefault(sim, "N/A"));
            aiTv.setText(notEmptyOrDefault(ai, "N/A"));

            if (filesList != null && !filesList.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                int c = 1;
                for (Map<String, Object> f : filesList) {
                    String name = String.valueOf(f.getOrDefault("name", "unknown"));
                    String size = String.valueOf(f.getOrDefault("size", "N/A"));

                    // Check if this is the primary folder
                    if (name.toLowerCase(Locale.ROOT).startsWith("primary folder")) {
                        sb.append(name).append("\n"); // no serial number or size
                    } else {
                        sb.append(c++).append(". ").append(name).append(" (").append(size).append(")\n");
                    }
                }
                filesTv.setText(sb.toString().trim());
            } else if (intentFileInfoList != null && !intentFileInfoList.isEmpty()) {
                filesTv.setText(formatFileInfoList(intentFileInfoList));
            } else {
                filesTv.setText("No files");
            }
        });
    }

    // -------------------- Prefs caching --------------------
    private void saveSnapshotToPrefs(String id, String title, String type, String level, String abs, String meth, String sim, String ai, List<Map<String, Object>> filesList) {
        String key = keyForProject(id, title);
        saveSnapshotToPrefsKey(key, title, type, level, abs, meth, sim, ai, filesList);
        saveSnapshotToPrefsKey(LEGACY_KEY, title, type, level, abs, meth, sim, ai, filesList);
    }

    private void saveSnapshotToPrefsKey(String key, String title, String type, String level, String abs, String meth, String sim, String ai, List<Map<String, Object>> filesList) {
        try {
            JSONObject root = new JSONObject();
            root.put("projectTitle", notNull(title));
            root.put("projectType1", notNull(type));
            root.put("projectType2", notNull(level));
            root.put("abstract", notNull(abs));
            root.put("methodology", notNull(meth));
            root.put("similarity", notNull(sim));
            root.put("aiGenerated", notNull(ai));

            JSONArray filesArr = new JSONArray();
            if (filesList != null) {
                for (Map<String, Object> f : filesList) {
                    JSONObject fo = new JSONObject();
                    fo.put("name", String.valueOf(f.getOrDefault("name", "")));
                    fo.put("size", String.valueOf(f.getOrDefault("size", "N/A")));
                    fo.put("path", String.valueOf(f.getOrDefault("path", "")));
                    filesArr.put(fo);
                }
            }
            root.put("files", filesArr);

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean ok = prefs.edit().putString(key, root.toString()).commit();
            if (!ok) {
                prefs.edit().putString(key, root.toString()).apply();
            }
        } catch (Exception e) {
            Log.e("ProjectSummary", "save snapshot error: " + e.getMessage());
        }
    }

    private String keyForProject(String id, String title) {
        if (id != null && !id.trim().isEmpty()) return "current_project_" + id.trim();
        if (title != null && !title.trim().isEmpty()) {
            String s = title.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
            if (s.length() > 64) s = s.substring(0, 64);
            return "current_project_title_" + s;
        }
        return LEGACY_KEY;
    }

    // -------------------- Load from prefs --------------------
    private void loadFromPrefsAndPopulate(String id) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String perKey = keyForProject(id, intentTitle);
            String jsonStr = prefs.getString(perKey, null);

            if (jsonStr == null) {
                jsonStr = prefs.getString(LEGACY_KEY, null);
            }
            if (jsonStr == null || jsonStr.isEmpty()) {
                if (hasIntentData()) {
                    populateFromIntentAndCache();
                } else {
                    populateUI("N/A", "N/A", "N/A", "N/A", "N/A", "N/A", "N/A", null);
                }
                return;
            }

            JSONObject root = new JSONObject(jsonStr);
            String title = firstNonEmpty(root.optString("projectTitle", null), intentTitle, "N/A");
            String type = firstNonEmpty(root.optString("projectType1", null), intentProjectType1, "N/A");
            String level = firstNonEmpty(root.optString("projectType2", null), intentProjectLevel, "N/A");
            String abs = firstNonEmpty(root.optString("abstract", null), intentAbstract, "N/A");
            String meth = firstNonEmpty(root.optString("methodology", null), intentMethodology, "N/A");
            String sim = firstNonEmpty(root.optString("similarity", null), intentSimilarity, "N/A");
            String ai = firstNonEmpty(root.optString("aiGenerated", null), intentAi, "N/A");

            JSONArray arr = root.optJSONArray("files");
            List<Map<String, Object>> filesList = null;
            if (arr != null && arr.length() > 0) {
                StringBuilder sb = new StringBuilder();
                int c = 1;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject fo = arr.optJSONObject(i);
                    if (fo == null) continue;
                    String name = fo.optString("name", "");
                    String size = fo.optString("size", "N/A");
                    if (name.toLowerCase(Locale.ROOT).startsWith("primary folder")) {
                        sb.append(name).append("\n");
                    } else {
                        sb.append(c++).append(". ").append(name).append(" (").append(size).append(")\n");
                    }
                }
                filesTv.setText(sb.toString().trim());
            } else {
                if (intentFileInfoList != null && !intentFileInfoList.isEmpty()) {
                    filesTv.setText(formatFileInfoList(intentFileInfoList));
                } else {
                    filesTv.setText("No files");
                }
            }

            populateUI(title, type, level, abs, meth, sim, ai, null);

        } catch (Exception e) {
            Log.e("ProjectSummary", "loadFromPrefs error: " + e.getMessage());
            if (hasIntentData()) populateFromIntentAndCache();
            else populateUI("N/A", "N/A", "N/A", "N/A", "N/A", "N/A", "N/A", null);
        }
    }

    // -------------------- Utility helpers --------------------
    private static String notNull(String s) {
        return s == null ? "" : s;
    }

    private static String safeTrim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private boolean hasIntentData() {
        return (intentTitle != null && !intentTitle.isEmpty()) ||
                (intentProjectType1 != null && !intentProjectType1.isEmpty()) ||
                (intentProjectLevel != null && !intentProjectLevel.isEmpty()) ||
                (intentAbstract != null && !intentAbstract.isEmpty()) ||
                (intentMethodology != null && !intentMethodology.isEmpty()) ||
                (intentSimilarity != null && !intentSimilarity.isEmpty()) ||
                (intentAi != null && !intentAi.isEmpty()) ||
                (intentFileInfoList != null && !intentFileInfoList.isEmpty());
    }

    private static String firstNonEmpty(String... vals) {
        if (vals == null) return null;
        for (String s : vals) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }

    private String notEmptyOrDefault(String val, String def) {
        return (val != null && !val.trim().isEmpty()) ? val : def;
    }

    private String formatFileInfoList(List<String> fileInfoList) {
        if (fileInfoList == null || fileInfoList.isEmpty()) return "No files";
        StringBuilder sb = new StringBuilder();
        int c = 1;
        for (String fi : fileInfoList) {
            if (fi == null) continue;
            if (fi.contains("||")) {
                String[] parts = fi.split("\\|\\|");
                String name = parts.length > 0 ? parts[0] : fi;
                String size = parts.length > 1 ? parts[1] : "N/A";
                if (name.toLowerCase(Locale.ROOT).startsWith("primary folder")) {
                    sb.append(name).append("\n");
                } else {
                    sb.append(c++).append(". ").append(name).append(" (").append(size).append(")\n");
                }
            } else {
                sb.append(c++).append(". ").append(fi).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static Object findObjectInMapByKeyNames(Map<String, Object> map, String[] keys) {
        if (map == null || keys == null) return null;
        for (String k : keys) {
            if (k == null) continue;
            if (map.containsKey(k) && map.get(k) != null) return map.get(k);
        }
        for (String key : keys) {
            String nk = normalizeKey(key);
            for (String mkey : map.keySet()) {
                if (normalizeKey(mkey).equals(nk)) return map.get(mkey);
            }
        }
        return null;
    }

    private String findStringInSnapshot(DataSnapshot snapshot, String... desiredKeys) {
        try {
            for (String k : desiredKeys) {
                if (k == null) continue;
                if (snapshot.hasChild(k)) {
                    Object v = snapshot.child(k).getValue();
                    if (v != null) return String.valueOf(v).trim();
                }
            }
            Object top = snapshot.getValue();
            if (top instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) top;
                for (String k : desiredKeys) {
                    Object v = findObjectInMapByKeyNames(map, new String[]{k});
                    if (v != null) return String.valueOf(v).trim();
                }
                Deque<Object> queue = new ArrayDeque<>();
                queue.add(map);
                while (!queue.isEmpty()) {
                    Object cur = queue.poll();
                    if (cur instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mcur = (Map<String, Object>) cur;
                        for (Map.Entry<String, Object> e : mcur.entrySet()) {
                            String candidateKey = e.getKey();
                            Object value = e.getValue();
                            for (String desired : desiredKeys) {
                                if (normalizeKey(candidateKey).equals(normalizeKey(desired)) && value != null) {
                                    return String.valueOf(value).trim();
                                }
                            }
                            if (value instanceof Map || value instanceof List) {
                                queue.add(value);
                            }
                        }
                    } else if (cur instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> lst = (List<Object>) cur;
                        for (Object o : lst) if (o instanceof Map || o instanceof List) queue.add(o);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String normalizeKey(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
