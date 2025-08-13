package com.example.acadlink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

public class ProjectDetailsActivity extends AppCompatActivity {

    String projectTitle, projectType1, projectType2, abstractText, methodology;
    ArrayList<String> fileInfoList;
    ArrayList<String> fileUriList; // NEW - URIs passed from UploadProjectActivity

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_project_details);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageButton toolbarBackBtn = findViewById(R.id.toolbarBackBtn);
        toolbarBackBtn.setOnClickListener(v -> onBackPressed());

        TableLayout tableLayout = findViewById(R.id.detailsTable);

        // Get data from intent
        projectTitle = getIntent().getStringExtra("projectTitle");
        projectType1 = getIntent().getStringExtra("projectType1");
        projectType2 = getIntent().getStringExtra("projectType2");
        abstractText = getIntent().getStringExtra("abstract");
        methodology = getIntent().getStringExtra("methodology");
        fileInfoList = getIntent().getStringArrayListExtra("fileInfoList");
        fileUriList = getIntent().getStringArrayListExtra("fileUriList"); // may be null

        Log.d("ProjectDetailsActivity", "Received fileInfoList: " + fileInfoList);
        Log.d("ProjectDetailsActivity", "Received fileUriList: " + fileUriList);

        // Populate table
        addRow(tableLayout, "Project Title", projectTitle);
        addRow(tableLayout, "Project Type", projectType1);
        addRow(tableLayout, "Project Level", projectType2);
        addRow(tableLayout, "Abstract", abstractText);
        addRow(tableLayout, "Methodology", methodology);
        addFileListRow(tableLayout, "Selected File(s)", fileInfoList);

        // Save a snapshot to SharedPreferences so ProjectSummaryActivity (and others) can read it later
        // Use synchronous commit so other activities can immediately read it
        saveProjectSnapshotToPrefs();

        findViewById(R.id.checkAIButtonCv).setOnClickListener(v -> {
            // Ensure snapshot saved before launching next activity
            saveProjectSnapshotToPrefs();
            Intent intent = new Intent(ProjectDetailsActivity.this, AiCheckingActivity.class);
            intent.putExtra("projectTitle", projectTitle);
            intent.putExtra("abstract", abstractText);
            intent.putExtra("methodology", methodology);
            startActivity(intent);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // ensure snapshot is always up-to-date before leaving activity
        saveProjectSnapshotToPrefs();
    }

    private void addRow(TableLayout table, String label, String value) {
        TableRow row = new TableRow(this);
        row.setBackgroundColor(0xFFFFFFFF);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));

        TextView labelView = new TextView(this);
        labelView.setText(label + ":");
        labelView.setPadding(16, 16, 8, 16);
        labelView.setTextColor(0xFF000000);
        labelView.setTextSize(14);
        labelView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value != null ? value : "N/A");
        valueView.setPadding(8, 16, 16, 16);
        valueView.setTextColor(0xFF000000);
        valueView.setTextSize(14);
        valueView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 2f));
        valueView.setSingleLine(false);
        valueView.setMaxLines(Integer.MAX_VALUE);
        valueView.setLineSpacing(1.2f, 1.2f);
        row.addView(valueView);

        table.addView(row);
    }

    private void addFileListRow(TableLayout table, String label, ArrayList<String> fileList) {
        TableRow row = new TableRow(this);
        row.setBackgroundColor(0xFFFFFFFF);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));

        TextView labelView = new TextView(this);
        labelView.setText(label + ":");
        labelView.setPadding(16, 16, 8, 16);
        labelView.setTextColor(0xFF000000);
        labelView.setTextSize(14);
        labelView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setPadding(8, 16, 16, 16);
        valueView.setTextColor(0xFF000000);
        valueView.setTextSize(14);
        valueView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 2f));
        valueView.setSingleLine(false);
        valueView.setMaxLines(Integer.MAX_VALUE);
        valueView.setLineSpacing(1.2f, 1.2f);

        if (fileList != null && !fileList.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (String item : fileList) {
                String cleanName;
                if (item.contains("/") || item.contains("\\")) {
                    cleanName = new File(item).getName();
                } else {
                    int index = item.indexOf(" (");
                    cleanName = (index != -1) ? item.substring(0, index) : item;
                }
                builder.append(cleanName).append("\n");
            }
            valueView.setText(builder.toString().trim());
        } else {
            valueView.setText("No file selected");
        }

        row.addView(valueView);
        table.addView(row);
    }

    // ---------------------------
    // Snapshot saver (ORDER-BASED mapping)
    // ---------------------------
    private void saveProjectSnapshotToPrefs() {
        try {
            JSONObject root = new JSONObject();
            root.put("projectTitle", projectTitle != null ? projectTitle : "");
            root.put("projectType1", projectType1 != null ? projectType1 : "");
            root.put("projectType2", projectType2 != null ? projectType2 : "");
            root.put("abstract", abstractText != null ? abstractText : "");
            root.put("methodology", methodology != null ? methodology : "");

            JSONArray filesArr = new JSONArray();

            ArrayList<String> infoListCopy = fileInfoList != null ? new ArrayList<>(fileInfoList) : new ArrayList<>();
            ArrayList<String> uriListCopy = fileUriList != null ? new ArrayList<>(fileUriList) : new ArrayList<>();

            int uriIndex = 0;
            for (int i = 0; i < infoListCopy.size(); ++i) {
                String info = infoListCopy.get(i);
                String stripped = info != null ? info.trim() : "";
                // strip leading numbering like "1. "
                stripped = stripped.replaceFirst("^\\d+\\.\\s*", "").trim();

                if (stripped.startsWith("Primary Folder:")) {
                    // Save the primary folder as a marker (no URI consumed)
                    JSONObject f = new JSONObject();
                    f.put("path", "");
                    // keep the "Primary Folder: ..." text so downstream code can detect it easily
                    f.put("name", stripped);
                    f.put("size", "N/A");
                    filesArr.put(f);
                    continue;
                }

                // Parse name and size from the info string (if present)
                String namePart = stripped;
                String sizePart = null;
                int idxOpen = stripped.lastIndexOf('(');
                int idxClose = stripped.lastIndexOf(')');
                if (idxOpen != -1 && idxClose != -1 && idxClose > idxOpen) {
                    sizePart = stripped.substring(idxOpen + 1, idxClose).trim();
                    namePart = stripped.substring(0, idxOpen).trim();
                }

                // Consume next URI from uriListCopy in order (if present)
                String matchedPath = "";
                if (uriIndex < uriListCopy.size()) {
                    matchedPath = uriListCopy.get(uriIndex++);
                }

                // If size not present in info, try to obtain it from URI
                String sizeToSave = (sizePart != null && !sizePart.isEmpty()) ? sizePart : tryGetFormattedSizeFromUriString(matchedPath);
                if (sizeToSave == null) sizeToSave = "N/A";

                // If namePart empty, try get from URI
                String nameToSave = (namePart != null && !namePart.isEmpty()) ? namePart : getFileNameFromUriString(matchedPath);
                if (nameToSave == null) nameToSave = "";

                JSONObject f = new JSONObject();
                f.put("path", matchedPath != null ? matchedPath : "");
                f.put("name", nameToSave);
                f.put("size", sizeToSave);
                filesArr.put(f);
            }

            // Leftover URIs (if any) — append them
            for (int j = uriIndex; j < uriListCopy.size(); ++j) {
                String leftover = uriListCopy.get(j);
                String uriName = getFileNameFromUriString(leftover);
                String sizeStr = tryGetFormattedSizeFromUriString(leftover);
                JSONObject f = new JSONObject();
                f.put("path", leftover);
                f.put("name", uriName != null ? uriName : "");
                f.put("size", sizeStr != null ? sizeStr : "N/A");
                filesArr.put(f);
            }

            root.put("files", filesArr);

            SharedPreferences prefs = getSharedPreferences("proj_temp", MODE_PRIVATE);
            // Use commit() to synchronously write the snapshot so other activities can read it immediately
            boolean ok = prefs.edit().putString("current_project", root.toString()).commit();
            if (!ok) {
                // fallback to apply if commit fails for any reason
                prefs.edit().putString("current_project", root.toString()).apply();
            }

            Log.d("ProjectDetailsActivity", "Saved project snapshot to prefs: " + root.toString());
        } catch (Exception e) {
            Log.e("ProjectDetailsActivity", "Failed saving snapshot: " + e.getMessage());
        }
    }

    // parse helpers (not used by new mapping but keep for compatibility)
    private String parseNameFromInfo(String s) {
        if (s == null) return "";
        String t = s.trim();
        t = t.replaceFirst("^\\d+\\.\\s*", "").trim();
        if (t.startsWith("Primary Folder:")) {
            return t.substring("Primary Folder:".length()).trim();
        }
        int idxOpen = t.lastIndexOf('(');
        if (idxOpen != -1) {
            return t.substring(0, idxOpen).trim();
        }
        return t;
    }

    private String parseSizeFromInfo(String s) {
        if (s == null) return null;
        try {
            int idxOpen = s.lastIndexOf('(');
            int idxClose = s.lastIndexOf(')');
            if (idxOpen != -1 && idxClose != -1 && idxClose > idxOpen) {
                String inside = s.substring(idxOpen + 1, idxClose).trim();
                if (!inside.isEmpty()) return inside;
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ---------------------------
    // helpers to read name/size from URI strings using ContentResolver
    // ---------------------------
    private String getFileNameFromUriString(String uriStr) {
        if (uriStr == null || uriStr.isEmpty()) return null;
        try {
            Uri uri = Uri.parse(uriStr);
            try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) return cursor.getString(idx);
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
        // fallback to last segment
        try {
            return new File(uriStr).getName();
        } catch (Exception ignored) {}
        return null;
    }

    private String tryGetFormattedSizeFromUriString(String uriStr) {
        if (uriStr == null || uriStr.isEmpty()) return null;
        try {
            Uri uri = Uri.parse(uriStr);
            try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (idx != -1) {
                        long bytes = cursor.getLong(idx);
                        if (bytes > 0) {
                            return formatBytesToHuman(bytes);
                        }
                    }
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
        return null;
    }

    private String formatBytesToHuman(long bytes) {
        long sizeKB = bytes / 1024;
        if (sizeKB >= 1024) {
            return String.format("%.1f MB", sizeKB / 1024f);
        } else {
            return sizeKB + " KB";
        }
    }
}
