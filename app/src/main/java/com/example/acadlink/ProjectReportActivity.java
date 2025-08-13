package com.example.acadlink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Layout;
import android.util.Log;
import android.view.Gravity;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ProjectReportActivity extends AppCompatActivity {

    String projectTitle;
    String similarity;
    String aiGenerated;

    String projectType1;
    String projectLevel;
    String abstractText;
    String methodology;
    ArrayList<FileTempItem> tempFileItems = new ArrayList<>();

    private DatabaseReference projectsRef;
    private StorageReference storageRootRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_project_report);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageButton toolbarBackBtn = findViewById(R.id.toolbarBackBtn);
        toolbarBackBtn.setOnClickListener(v -> {
            Intent i = new Intent(ProjectReportActivity.this, AiCheckingActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });

        projectTitle = getIntent().getStringExtra("projectTitle");
        similarity = getIntent().getStringExtra("similarity");
        aiGenerated = getIntent().getStringExtra("aiGenerated");

        projectType1 = getIntent().getStringExtra("projectType1");
        projectLevel = getIntent().getStringExtra("projectType2");
        abstractText = getIntent().getStringExtra("abstract");
        methodology = getIntent().getStringExtra("methodology");
        ArrayList<String> fileInfoListFromIntent = getIntent().getStringArrayListExtra("fileInfoList");
        ArrayList<String> fileUriListFromIntent = getIntent().getStringArrayListExtra("fileUriList");


        if ((fileInfoListFromIntent == null || fileInfoListFromIntent.isEmpty()) &&
                (fileUriListFromIntent == null || fileUriListFromIntent.isEmpty())) {
            loadProjectFromPrefs();
        } else {
            ArrayList<String> info = fileInfoListFromIntent != null ? new ArrayList<>(fileInfoListFromIntent) : new ArrayList<>();
            ArrayList<String> uris = fileUriListFromIntent != null ? new ArrayList<>(fileUriListFromIntent) : new ArrayList<>();

            int uriIndex = 0;
            for (String raw : info) {
                String str = raw != null ? raw.trim() : "";
                String stripped = str.replaceFirst("^\\d+\\.\\s*", "").trim();

                if (stripped.startsWith("Primary Folder:")) {
                    String name = "Primary Folder: " + stripped.substring("Primary Folder:".length()).trim();
                    tempFileItems.add(new FileTempItem("", name, "N/A"));
                    continue;
                }

                String parsedSize = "N/A";
                int idxOpen = stripped.lastIndexOf('(');
                int idxClose = stripped.lastIndexOf(')');
                String namePart = stripped;
                if (idxOpen != -1 && idxClose != -1 && idxClose > idxOpen) {
                    parsedSize = stripped.substring(idxOpen + 1, idxClose).trim();
                    namePart = stripped.substring(0, idxOpen).trim();
                }

                String usePath = "";
                if (uriIndex < uris.size()) {
                    usePath = uris.get(uriIndex++);
                }
                tempFileItems.add(new FileTempItem(usePath != null ? usePath : "", namePart, parsedSize != null ? parsedSize : "N/A"));
            }

            for (int i = uriIndex; i < uris.size(); ++i) {
                String leftover = uris.get(i);
                String uName = tryGetNameFromUriString(leftover);
                String uSize = tryGetFormattedSizeFromUriString(leftover);
                tempFileItems.add(new FileTempItem(leftover, uName != null ? uName : "unknown", uSize != null ? uSize : "N/A"));
            }
        }

        TableLayout tableLayout = findViewById(R.id.projectReportTable);
        if (tableLayout != null) {
            addRow(tableLayout, "Project Title", projectTitle, false);
            addRow(tableLayout, "Similarity", similarity, true);
            addRow(tableLayout, "AI Generated", aiGenerated, true);
        }

        android.view.View uploadButton = findViewById(R.id.uploadVerifiedProjectCv);
        android.view.View requestButton = findViewById(R.id.requestToFacultyCv);

        boolean similarityUnder15 = isPercentUnderThreshold(similarity, 15f);
        boolean aiUnder15 = isPercentUnderThreshold(aiGenerated, 15f);

        if (similarityUnder15 && aiUnder15) {
            uploadButton.setVisibility(android.view.View.VISIBLE);
            requestButton.setVisibility(android.view.View.GONE);
        } else {
            uploadButton.setVisibility(android.view.View.GONE);
            requestButton.setVisibility(android.view.View.VISIBLE);
        }

        projectsRef = FirebaseDatabase.getInstance().getReference("projects");
        storageRootRef = FirebaseStorage.getInstance().getReference().child("project_files");

        uploadButton.setOnClickListener(v -> {
            uploadButton.setEnabled(false);
            Toast.makeText(ProjectReportActivity.this, "Uploading project...", Toast.LENGTH_SHORT).show();
            uploadProjectToFirebase();
        });

        requestButton.setOnClickListener(v -> {
            Toast.makeText(ProjectReportActivity.this, "Request to faculty flow", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadProjectFromPrefs() {
        try {
            SharedPreferences prefs = getSharedPreferences("proj_temp", MODE_PRIVATE);
            String json = prefs.getString("current_project", null);
            if (json == null) return;

            JSONObject obj = new JSONObject(json);
            projectTitle = projectTitle != null ? projectTitle : obj.optString("projectTitle", "");
            projectType1 = obj.optString("projectType1", "");
            projectLevel = obj.optString("projectType2", "");
            abstractText = obj.optString("abstract", "");
            methodology = obj.optString("methodology", "");
            JSONArray files = obj.optJSONArray("files");
            if (files != null) {
                for (int i = 0; i < files.length(); ++i) {
                    JSONObject f = files.getJSONObject(i);
                    String path = f.optString("path", "");
                    String name = f.optString("name", "");
                    String size = f.optString("size", "N/A");
                    tempFileItems.add(new FileTempItem(path, name, size));
                }
            }
        } catch (Exception e) {
            Log.e("ProjectReportActivity", "Failed loading prefs: " + e.getMessage());
        }
    }

    private void uploadProjectToFirebase() {
        String key = projectsRef.push().getKey();
        if (key == null) {
            Toast.makeText(this, "Failed to create project key.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (tempFileItems != null && !tempFileItems.isEmpty()) {
            final ArrayList<Map<String, String>> fileEntries = new ArrayList<>();
            final AtomicInteger doneCounter = new AtomicInteger(0);
            final int total = tempFileItems.size();

            for (FileTempItem fti : tempFileItems) {
                String path = fti.path;
                String name = fti.name;
                String size = fti.size;

                if (name != null && name.startsWith("Primary Folder:")) {
                    Map<String, String> map = new HashMap<>();
                    map.put("name", name);
                    map.put("size", "N/A");
                    map.put("url", "");
                    fileEntries.add(map);
                    if (doneCounter.incrementAndGet() == total) {
                        writeProjectToDatabase(key, fileEntries);
                    }
                    continue;
                }

                Uri fileUri = null;
                try {
                    if (path != null && !path.isEmpty() &&
                            (path.startsWith("content://") || path.startsWith("file://") || path.contains("/"))) {
                        fileUri = Uri.parse(path);
                    }
                } catch (Exception ignored) {}

                if (fileUri == null) {
                    Map<String, String> map = new HashMap<>();
                    map.put("name", name != null ? name : "unknown");
                    map.put("size", size != null ? size : "N/A");
                    map.put("url", "");
                    fileEntries.add(map);
                    if (doneCounter.incrementAndGet() == total) {
                        writeProjectToDatabase(key, fileEntries);
                    }
                    continue;
                }

                String finalSize = size;
                if (finalSize == null || finalSize.equalsIgnoreCase("N/A")) {
                    long bytes = getFileSizeBytesFromUri(fileUri);
                    if (bytes <= 0) {
                        try {
                            String p = fileUri.getPath();
                            if (p != null) {
                                File f = new File(p);
                                if (f.exists()) bytes = f.length();
                            }
                        } catch (Exception ignored) {}
                    }
                    finalSize = bytes > 0 ? formatBytesToHuman(bytes) : "N/A";
                }

                String filename = name != null && !name.isEmpty() ? name : (new File(fileUri.getPath())).getName();
                StorageReference fileRef = storageRootRef.child(key + "/" + System.currentTimeMillis() + "_" + filename);

                UploadTask uploadTask = fileRef.putFile(fileUri);
                Task<Uri> urlTask = uploadTask.continueWithTask((Continuation<UploadTask.TaskSnapshot, Task<Uri>>) task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return fileRef.getDownloadUrl();
                });

                final String useSize = finalSize;
                final String useName = filename;
                urlTask.addOnCompleteListener((OnCompleteListener<Uri>) task -> {
                    String downloadUrl = "";
                    if (task.isSuccessful() && task.getResult() != null) {
                        downloadUrl = task.getResult().toString();
                    }
                    Map<String, String> map = new HashMap<>();
                    map.put("name", useName);
                    map.put("size", useSize);
                    map.put("url", downloadUrl);
                    fileEntries.add(map);

                    if (doneCounter.incrementAndGet() == total) {
                        writeProjectToDatabase(key, fileEntries);
                    }
                });
            }
        } else {
            writeProjectToDatabase(key, new ArrayList<>());
        }
    }

    private void writeProjectToDatabase(String key, ArrayList<Map<String, String>> fileEntries) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", key);
        map.put("title", projectTitle != null ? projectTitle : "N/A");
        map.put("projectTitle", projectTitle != null ? projectTitle : "N/A");
        map.put("projectType1", projectType1 != null ? projectType1 : "N/A");
        map.put("projectLevel", projectLevel != null ? projectLevel : "N/A");
        map.put("projectType2", projectLevel != null ? projectLevel : "N/A");
        map.put("abstract", abstractText != null ? abstractText : "N/A");
        map.put("methodology", methodology != null ? methodology : "N/A");
        map.put("similarity", similarity != null ? similarity : "N/A");
        map.put("aiGenerated", aiGenerated != null ? aiGenerated : "N/A");
        map.put("files", fileEntries);
        map.put("timestamp", System.currentTimeMillis());

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String uploaderUidTemp = null;
        String uploaderEmailTemp = null;
        if (currentUser != null) {
            uploaderUidTemp = currentUser.getUid();
            uploaderEmailTemp = currentUser.getEmail();

            map.put("uid", uploaderUidTemp);
            map.put("userId", uploaderUidTemp);
            map.put("uploaderId", uploaderUidTemp);
            map.put("userEmail", uploaderEmailTemp);
            map.put("email", uploaderEmailTemp);

            Map<String, Object> uploaderObj = new HashMap<>();
            uploaderObj.put("uid", uploaderUidTemp);
            uploaderObj.put("email", uploaderEmailTemp != null ? uploaderEmailTemp : "");
            map.put("uploader", uploaderObj);
            map.put("uploadedBy", uploaderObj);
            map.put("authorUid", uploaderUidTemp);
        }

        final String uploaderUid = uploaderUidTemp;

        projectsRef.child(key).setValue(map).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (uploaderUid != null && !uploaderUid.isEmpty()) {
                    try {
                        Map<String, Object> refMap = new HashMap<>();
                        refMap.put("refId", key);
                        projectsRef.child(uploaderUid).child(key).setValue(refMap)
                                .addOnCompleteListener(inner -> {
                                    if (!inner.isSuccessful()) {
                                        Log.w("ProjectReport", "Per-user reference write failed: " +
                                                (inner.getException() != null ? inner.getException().getMessage() : "unknown"));
                                    }
                                });
                    } catch (Exception e) {
                        Log.w("ProjectReport", "Failed to create per-user reference: " + e.getMessage());
                    }
                }

                Toast.makeText(ProjectReportActivity.this, "Project uploaded successfully", Toast.LENGTH_LONG).show();
                try {
                    getSharedPreferences("proj_temp", MODE_PRIVATE).edit().remove("current_project").apply();
                } catch (Exception ignored) {}

                Intent intent = new Intent(ProjectReportActivity.this, MyProjects.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(ProjectReportActivity.this, "Upload failed: " +
                        (task.getException() != null ? task.getException().getMessage() : ""), Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean isPercentUnderThreshold(String percentStr, float threshold) {
        if (percentStr == null || !percentStr.contains("%")) return false;
        try {
            String numStr = percentStr.replaceAll("[^0-9.]", "");
            float percent = Float.parseFloat(numStr);
            return percent <= threshold;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * IMPORTANT PART: create table rows so the second column wraps/multi-lines.
     */
    private void addRow(TableLayout table, String label, String value, boolean isPercentage) {
        try {
            TableRow row = new TableRow(this);
            // row fills width
            TableLayout.LayoutParams rowLp = new TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT);
            row.setLayoutParams(rowLp);
            row.setBackgroundColor(0xFFFFFFFF);

            // Label view: wrap content
            TextView labelView = new TextView(this);
            labelView.setText(label + ":");
            labelView.setPadding(dpToPx(16), dpToPx(16), dpToPx(8), dpToPx(16));
            labelView.setTextColor(0xFF000000);
            labelView.setTextSize(14f);
            labelView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

            TableRow.LayoutParams labelLp = new TableRow.LayoutParams(
                    TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            labelView.setLayoutParams(labelLp);

            // Value view: IMPORTANT - width = 0, weight = 1 so it uses remaining space and wraps
            TextView valueView = new TextView(this);
            valueView.setText(value != null ? value : "N/A");
            valueView.setPadding(dpToPx(8), dpToPx(16), dpToPx(16), dpToPx(16));
            valueView.setTextSize(14f);
            valueView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

            // Make multi-line and prevent horizontal scrolling/ellipsize so wrapping works
            valueView.setSingleLine(false);
            valueView.setHorizontallyScrolling(false);
            valueView.setMaxLines(10); // adjust if you want more
            // Better line break on newer APIs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                valueView.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
            }

            TableRow.LayoutParams valueLp = new TableRow.LayoutParams(
                    0, TableRow.LayoutParams.WRAP_CONTENT, 1f);
            valueView.setLayoutParams(valueLp);

            // Coloring for percentage
            if (isPercentage && value != null && value.contains("%")) {
                try {
                    String numStr = value.replaceAll("[^0-9.]", "");
                    float percent = Float.parseFloat(numStr);
                    if (percent <= 15f) valueView.setTextColor(0xFF00AA00); // green
                    else valueView.setTextColor(0xFFFF0000); // red
                } catch (Exception ignored) {
                    valueView.setTextColor(0xFF000000);
                }
            } else {
                valueView.setTextColor(0xFF000000);
            }

            row.addView(labelView);
            row.addView(valueView);
            table.addView(row);
        } catch (Exception e) {
            Log.e("ProjectReportActivity", "addRow error: " + e.getMessage());
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private long getFileSizeBytesFromUri(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1) return cursor.getLong(sizeIndex);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private String tryGetFormattedSizeFromUriString(String uriStr) {
        try {
            Uri u = Uri.parse(uriStr);
            long b = getFileSizeBytesFromUri(u);
            if (b > 0) return formatBytesToHuman(b);
        } catch (Exception ignored) {}
        return null;
    }

    private String tryGetNameFromUriString(String uriStr) {
        try {
            Uri u = Uri.parse(uriStr);
            try (Cursor cursor = getContentResolver().query(u, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) return cursor.getString(idx);
                }
            }
        } catch (Exception ignored) {}
        try {
            return new File(uriStr).getName();
        } catch (Exception ignored) {}
        return "unknown";
    }

    private String formatBytesToHuman(long bytes) {
        long sizeKB = bytes / 1024;
        if (sizeKB >= 1024) {
            return String.format("%.1f MB", sizeKB / 1024f);
        } else {
            return sizeKB + " KB";
        }
    }

    public static class FileTempItem {
        public String path;
        public String name;
        public String size;

        public FileTempItem(String p, String n, String s) {
            this.path = p;
            this.name = n;
            this.size = s;
        }
    }
}
