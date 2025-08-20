package com.example.acadlink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.List;
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

    // incoming lists so we can forward
    private ArrayList<String> fileInfoListIntent;
    private ArrayList<String> fileUriListIntent;

    // Actual projects path
    private DatabaseReference projectsRef;
    private StorageReference storageRootRef;

    // NEW: request archive paths
    private DatabaseReference requestArchivesRef;
    private StorageReference archiveStorageRootRef;

    private static final String ARCHIVE_NODE = "requestArchives";
    private static final String ARCHIVE_FILES_BUCKET = "request_archive_files";

    // Colors
    private static final int COLOR_LIGHT_RED = 0xFFFFCDD2;
    private static final int COLOR_LIGHT_YELLOW = 0xFFFFD700;

    private final Random rnd = new Random();

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

        fileInfoListIntent = (fileInfoListFromIntent != null) ? new ArrayList<>(fileInfoListFromIntent) : null;
        fileUriListIntent = (fileUriListFromIntent != null) ? new ArrayList<>(fileUriListFromIntent) : null;

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

        if ((fileInfoListIntent == null || fileInfoListIntent.isEmpty()) && tempFileItems != null && !tempFileItems.isEmpty()) {
            fileInfoListIntent = new ArrayList<>();
            fileUriListIntent = new ArrayList<>();
            for (FileTempItem f : tempFileItems) {
                fileInfoListIntent.add(f.name + " (" + (f.size != null ? f.size : "N/A") + ")");
                fileUriListIntent.add(f.path != null ? f.path : "");
            }
        }

        try {
            SharedPreferences pd = getSharedPreferences("ProjectData", MODE_PRIVATE);
            if ((abstractText == null || abstractText.trim().isEmpty()) && pd.contains("last_abstract")) {
                abstractText = pd.getString("last_abstract", abstractText);
            }
            if ((methodology == null || methodology.trim().isEmpty()) && pd.contains("last_methodology")) {
                methodology = pd.getString("last_methodology", methodology);
            }
        } catch (Exception ignored) {}

        // Firebase refs
        projectsRef = FirebaseDatabase.getInstance().getReference("projects");
        storageRootRef = FirebaseStorage.getInstance().getReference().child("project_files");
        requestArchivesRef = FirebaseDatabase.getInstance().getReference(ARCHIVE_NODE);
        archiveStorageRootRef = FirebaseStorage.getInstance().getReference().child(ARCHIVE_FILES_BUCKET);

        TableLayout tableLayout = findViewById(R.id.projectReportTable);
        if (tableLayout != null) {
            addRow(tableLayout, "Project Title", projectTitle, false);
            addRow(tableLayout, "Similarity", similarity, true);
            addRow(tableLayout, "AI Generated", aiGenerated, true);

            float aiPct = parsePercent(aiGenerated);
            fetchPreviousProjectsAndHighlight(tableLayout, aiPct);
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

        uploadButton.setOnClickListener(v -> {
            uploadButton.setEnabled(false);
            Toast.makeText(ProjectReportActivity.this, "Uploading project...", Toast.LENGTH_SHORT).show();
            uploadProjectToFirebase(); // original “actual project” upload remains unchanged
        });

        // NEW: On "Request to Faculty", save to requestArchives (not projects)
        requestButton.setOnClickListener(v -> {
            requestButton.setEnabled(false);
            createArchivedRequestAndOpenStudentView(); // archive first; student uploads later
        });
    }

    /**
     * Create an archive entry under requestArchives/<id> with all project details.
     * Upload the current files under request_archive_files/<id>/...
     * Then open RequestToFacultyActivity to show the pending request.
     */
    private void createArchivedRequestAndOpenStudentView() {
        try {
            final String key = requestArchivesRef.push().getKey();
            if (key == null) {
                Toast.makeText(this, "Failed to create request key.", Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences userInfo = getSharedPreferences("UserInfo", MODE_PRIVATE);
            String requesterName = userInfo.getString("name", "");
            String requesterEmail = userInfo.getString("email", "");
            String department = userInfo.getString("department", "");

            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            String uploaderUidTemp = null;
            if ((requesterName == null || requesterName.trim().isEmpty()) && currentUser != null) {
                String email = currentUser.getEmail();
                if (email != null && !email.isEmpty()) {
                    requesterName = toPrettyName(email);
                }
            }
            if (currentUser != null) {
                uploaderUidTemp = currentUser.getUid();
                if (requesterEmail == null || requesterEmail.trim().isEmpty()) requesterEmail = currentUser.getEmail();
            }

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
            map.put("files", new ArrayList<>()); // filled after storage uploads
            map.put("timestamp", System.currentTimeMillis());
            map.put("request", true);
            map.put("status", "requested");
            map.put("requestFromName", requesterName != null ? requesterName : "");
            map.put("department", department != null ? department : "");

            if (uploaderUidTemp != null) {
                map.put("uid", uploaderUidTemp);
                map.put("userId", uploaderUidTemp);
                map.put("uploaderId", uploaderUidTemp);
                map.put("userEmail", requesterEmail != null ? requesterEmail : "");
                map.put("email", requesterEmail != null ? requesterEmail : "");

                Map<String, Object> uploaderObj = new HashMap<>();
                uploaderObj.put("uid", uploaderUidTemp);
                uploaderObj.put("email", requesterEmail != null ? requesterEmail : "");
                map.put("uploader", uploaderObj);
                map.put("uploadedBy", uploaderObj);
                map.put("authorUid", uploaderUidTemp);
            }

            // make final copies for lambdas (prevents “must be final” errors)
            final String finalRequesterName = requesterName;
            final String finalDepartment = department;
            final ArrayList<String> finalFileInfoList =
                    (fileInfoListIntent != null) ? new ArrayList<>(fileInfoListIntent) : null;
            final ArrayList<String> finalFileUriList =
                    (fileUriListIntent != null) ? new ArrayList<>(fileUriListIntent) : null;

            Intent intent = new Intent(ProjectReportActivity.this, RequestToFacultyActivity.class);
            intent.putExtra("projectId", key);
            intent.putExtra("requestFromName", finalRequesterName);
            intent.putExtra("department", finalDepartment);
            intent.putExtra("projectTitle", projectTitle);
            intent.putExtra("similarity", similarity);
            intent.putExtra("aiGenerated", aiGenerated);
            if (finalFileInfoList != null) intent.putStringArrayListExtra("fileInfoList", finalFileInfoList);
            if (finalFileUriList != null) intent.putStringArrayListExtra("fileUriList", finalFileUriList);

            // write the archive node first
            requestArchivesRef.child(key).setValue(map).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // upload files (if any) under ARCHIVE bucket, then open student view
                    if (finalFileUriList != null && !finalFileUriList.isEmpty()) {
                        uploadArchiveFilesThenOpen(key, finalFileUriList, finalFileInfoList, intent);
                    } else {
                        startActivity(intent);
                        finish();
                    }
                } else {
                    Toast.makeText(ProjectReportActivity.this, "Failed to create request: " +
                            (task.getException() != null ? task.getException().getMessage() : ""), Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Log.e("ProjectReportActivity", "createArchivedRequestAndOpenStudentView error: " + e.getMessage());
            Toast.makeText(this, "Failed to create request: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Upload URIs to request_archive_files/<id>/..., write files[] to archive node, then open the activity.
     */
    private void uploadArchiveFilesThenOpen(String key, ArrayList<String> uriList, ArrayList<String> infoList, Intent intentAfter) {
        try {
            final ArrayList<Map<String, String>> fileEntries = new ArrayList<>();
            final AtomicInteger doneCounter = new AtomicInteger(0);
            final int total = Math.max(1, uriList.size());

            for (String raw : uriList) {
                final String path = raw != null ? raw : "";
                Uri fileUri = null;
                try {
                    if (!path.isEmpty() && (path.startsWith("content://") || path.startsWith("file://") || path.contains("/"))) {
                        fileUri = Uri.parse(path);
                    }
                } catch (Exception ignored) {}

                String displayName = tryGetNameFromUriString(path);
                String displaySize = tryGetFormattedSizeFromUriString(path);
                if (displaySize == null) displaySize = "N/A";

                if (fileUri == null) {
                    Map<String, String> map = new HashMap<>();
                    map.put("name", displayName != null ? displayName : "unknown");
                    map.put("size", displaySize);
                    map.put("url", "");
                    fileEntries.add(map);
                    if (doneCounter.incrementAndGet() == total) {
                        requestArchivesRef.child(key).child("files").setValue(fileEntries).addOnCompleteListener(t -> {
                            startActivity(intentAfter);
                            finish();
                        });
                    }
                    continue;
                }

                String filename;
                try {
                    filename = (displayName != null && !displayName.isEmpty())
                            ? displayName
                            : new File(fileUri.getPath()).getName();
                } catch (Exception e) {
                    filename = "file_" + System.currentTimeMillis();
                }

                final String finalSize = displaySize;
                final String finalName = filename;

                StorageReference fileRef = archiveStorageRootRef.child(key + "/" + System.currentTimeMillis() + "_" + filename);
                UploadTask uploadTask = fileRef.putFile(fileUri);
                Task<Uri> urlTask = uploadTask.continueWithTask((Continuation<UploadTask.TaskSnapshot, Task<Uri>>) task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return fileRef.getDownloadUrl();
                });

                urlTask.addOnCompleteListener((OnCompleteListener<Uri>) task -> {
                    String downloadUrl = "";
                    if (task.isSuccessful() && task.getResult() != null) {
                        downloadUrl = task.getResult().toString();
                    }
                    Map<String, String> map = new HashMap<>();
                    map.put("name", finalName);       // FIXED: final for lambda
                    map.put("size", finalSize);       // FIXED: final for lambda
                    map.put("url", downloadUrl);
                    fileEntries.add(map);

                    if (doneCounter.incrementAndGet() == total) {
                        requestArchivesRef.child(key).child("files").setValue(fileEntries).addOnCompleteListener(t -> {
                            if (!t.isSuccessful()) {
                                Toast.makeText(ProjectReportActivity.this, "Saved request but failed to write file info: " +
                                        (t.getException() != null ? t.getException().getMessage() : ""), Toast.LENGTH_LONG).show();
                            }
                            startActivity(intentAfter);
                            finish();
                        });
                    }
                });
            }
        } catch (Exception e) {
            Log.e("ProjectReportActivity", "uploadArchiveFilesThenOpen error: " + e.getMessage());
            try {
                startActivity(intentAfter);
                finish();
            } catch (Exception ignored) {}
        }
    }

    private void fetchPreviousProjectsAndHighlight(final TableLayout tableLayout, final float aiPct) {
        try {
            projectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    List<Set<String>> previousProjectTokenSets = new ArrayList<>();

                    for (DataSnapshot child : snapshot.getChildren()) {
                        try {
                            String abs = "";
                            String meth = "";
                            Object aObj = child.child("abstract").getValue();
                            Object mObj = child.child("methodology").getValue();
                            if (aObj != null) abs = aObj.toString();
                            if (mObj != null) meth = mObj.toString();

                            String combined = (abs == null ? "" : abs) + " " + (meth == null ? "" : meth);
                            Set<String> tokens = tokenizeAndFilter(combined);
                            if (!tokens.isEmpty()) previousProjectTokenSets.add(tokens);
                        } catch (Exception ignored) {}
                    }

                    CharSequence highlightedAbstract = createHighlightedSpannableForFieldUsingProjects(abstractText, previousProjectTokenSets, true, aiPct);
                    CharSequence highlightedMethod = createHighlightedSpannableForFieldUsingProjects(methodology, previousProjectTokenSets, false, aiPct);

                    addRowWithSpannable(tableLayout, "Abstract", highlightedAbstract);
                    addRowWithSpannable(tableLayout, "Methodology", highlightedMethod);
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    CharSequence safeAbs = abstractText != null ? abstractText : "N/A";
                    CharSequence safeMeth = methodology != null ? methodology : "N/A";
                    addRowWithSpannable(tableLayout, "Abstract", safeAbs);
                    addRowWithSpannable(tableLayout, "Methodology", safeMeth);
                }
            });
        } catch (Exception e) {
            addRowWithSpannable(tableLayout, "Abstract", abstractText != null ? abstractText : "N/A");
            addRowWithSpannable(tableLayout, "Methodology", methodology != null ? methodology : "N/A");
            Log.e("ProjectReportActivity", "fetchPreviousProjectsAndHighlight error: " + e.getMessage());
        }
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

    // ORIGINAL actual-project upload remains (unchanged behavior)
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

    private void addRow(TableLayout table, String label, String value, boolean isPercentage) {
        try {
            TableRow row = new TableRow(this);
            TableLayout.LayoutParams rowLp = new TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT);
            row.setLayoutParams(rowLp);
            row.setBackgroundColor(0xFFFFFFFF);

            TextView labelView = new TextView(this);
            labelView.setText(label + ":");
            labelView.setPadding(dpToPx(16), dpToPx(16), dpToPx(8), dpToPx(16));
            labelView.setTextColor(0xFF000000);
            labelView.setTextSize(14f);
            labelView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

            TableRow.LayoutParams labelLp = new TableRow.LayoutParams(
                    TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            labelView.setLayoutParams(labelLp);

            TextView valueView = new TextView(this);
            valueView.setText(value != null ? value : "N/A");
            valueView.setPadding(dpToPx(8), dpToPx(16), dpToPx(16), dpToPx(16));
            valueView.setTextSize(14f);
            valueView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            valueView.setSingleLine(false);
            valueView.setHorizontallyScrolling(false);
            valueView.setMaxLines(10);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                valueView.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
            }

            TableRow.LayoutParams valueLp = new TableRow.LayoutParams(
                    0, TableRow.LayoutParams.WRAP_CONTENT, 1f);
            valueView.setLayoutParams(valueLp);

            if (isPercentage && value != null && value.contains("%")) {
                try {
                    String numStr = value.replaceAll("[^0-9.]", "");
                    float percent = Float.parseFloat(numStr);
                    if (percent <= 15f) valueView.setTextColor(0xFF00AA00);
                    else valueView.setTextColor(0xFFFF0000);
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

    private void addRowWithSpannable(TableLayout table, String label, CharSequence valueSpannable) {
        try {
            TableRow row = new TableRow(this);
            TableLayout.LayoutParams rowLp = new TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT);
            row.setLayoutParams(rowLp);
            row.setBackgroundColor(0xFFFFFFFF);

            TextView labelView = new TextView(this);
            labelView.setText(label + ":");
            labelView.setPadding(dpToPx(16), dpToPx(16), dpToPx(8), dpToPx(16));
            labelView.setTextColor(0xFF000000);
            labelView.setTextSize(14f);
            labelView.setGravity(Gravity.START | Gravity.TOP);

            TableRow.LayoutParams labelLp = new TableRow.LayoutParams(
                    TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            labelView.setLayoutParams(labelLp);

            TextView valueView = new TextView(this);
            valueView.setText(valueSpannable != null ? valueSpannable : "N/A");
            valueView.setPadding(dpToPx(8), dpToPx(16), dpToPx(16), dpToPx(16));
            valueView.setTextSize(14f);
            valueView.setGravity(Gravity.START | Gravity.TOP);

            valueView.setSingleLine(false);
            valueView.setHorizontallyScrolling(false);
            valueView.setMaxLines(Integer.MAX_VALUE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                valueView.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
            }

            TableRow.LayoutParams valueLp = new TableRow.LayoutParams(
                    0, TableRow.LayoutParams.WRAP_CONTENT, 1f);
            valueView.setLayoutParams(valueLp);

            valueView.setTextColor(0xFF000000);

            row.addView(labelView);
            row.addView(valueView);
            table.addView(row);
        } catch (Exception e) {
            Log.e("ProjectReportActivity", "addRowWithSpannable error: " + e.getMessage());
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
            return String.format(Locale.getDefault(), "%.1f MB", sizeKB / 1024f);
        } else {
            return sizeKB + " KB";
        }
    }

    private float parsePercent(String percentStr) {
        if (percentStr == null) return 0f;
        try {
            String numStr = percentStr.replaceAll("[^0-9.]", "");
            if (numStr.isEmpty()) return 0f;
            return Float.parseFloat(numStr);
        } catch (Exception e) {
            return 0f;
        }
    }

    private CharSequence createHighlightedSpannableForFieldUsingProjects(String text, List<Set<String>> previousProjectTokenSets, boolean isAbstract, float aiPercent) {
        if (text == null || text.trim().isEmpty()) return "N/A";

        String[] lines = splitToLines(text);

        Set<Integer> similarityIndices = new HashSet<>();
        for (int i = 0; i < lines.length; ++i) {
            String ln = lines[i];
            if (ln == null || ln.trim().isEmpty()) continue;

            Set<String> lineTokens = tokenizeAndFilter(ln);
            if (lineTokens.isEmpty()) continue;

            boolean isSimilar = false;
            for (Set<String> projTokens : previousProjectTokenSets) {
                if (projTokens == null || projTokens.isEmpty()) continue;
                Set<String> tmp = new HashSet<>(lineTokens);
                tmp.retainAll(projTokens);
                int intersection = tmp.size();

                int threshold = Math.max(5, (int) Math.ceil(lineTokens.size() * 0.3));
                if (lineTokens.size() >= 6) threshold = Math.max(threshold, 3);

                if (intersection >= threshold) {
                    isSimilar = true;
                    break;
                }
            }
            if (isSimilar) similarityIndices.add(i);
        }

        Set<Integer> aiRandomIndices = new HashSet<>();
        if (aiPercent > 15f) {
            int totalLines = Math.max(1, lines.length);
            float above = (aiPercent - 12f) / 100f;
            if (above < 0f) above = 0f;
            if (above > 0.7f) above = 0.7f;

            float minPortion = 0.10f;
            int toPick = Math.max(1, Math.round(totalLines * Math.max(minPortion, above)));

            int attempts = 0;
            while (aiRandomIndices.size() < toPick && attempts < totalLines * 4) {
                int pick = rnd.nextInt(totalLines);
                if (similarityIndices.contains(pick)) { attempts++; continue; }
                String ln = lines[pick];
                if (ln == null || ln.trim().isEmpty()) { attempts++; continue; }
                aiRandomIndices.add(pick);
                attempts++;
            }
        }

        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < lines.length; ++i) {
            String ln = lines[i];
            int start = sb.length();
            sb.append(ln == null ? "" : ln);
            int end = sb.length();

            if (i != lines.length - 1) sb.append("\n");

            if (!ln.trim().isEmpty()) {
                if (similarityIndices.contains(i)) {
                    sb.setSpan(new BackgroundColorSpan(COLOR_LIGHT_RED), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (aiRandomIndices.contains(i)) {
                    sb.setSpan(new BackgroundColorSpan(COLOR_LIGHT_YELLOW), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        return sb;
    }

    private String[] splitToLines(String text) {
        if (text == null) return new String[]{""};
        if (text.contains("\r") || text.contains("\n")) {
            return text.split("\\r?\\n");
        }
        String[] sentences = text.split("(?<=[.!?])\\s+");
        if (sentences.length == 0) return new String[]{text};
        return sentences;
    }

    private Set<String> tokenizeAndFilter(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        s = s.toLowerCase(Locale.getDefault());
        String[] tokens = s.split("\\W+");
        Set<String> stopwords = new HashSet<>(Arrays.asList(
                "the", "and", "a", "an", "of", "in", "on", "for", "to", "is", "are",
                "this", "that", "with", "by", "from", "as", "at", "be", "or", "which",
                "it", "its", "we", "our", "using", "use", "based", "these", "those", "have", "has", "were", "was"
        ));
        for (String t : tokens) {
            if (t == null) continue;
            t = t.trim();
            if (t.length() < 2) continue;
            if (stopwords.contains(t)) continue;
            out.add(t);
        }
        return out;
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

    private String toPrettyName(String email) {
        try {
            if (email == null) return "Unknown User";
            String local = email.substring(0, email.indexOf('@'));
            String[] parts = local.split("[^A-Za-z0-9]+");
            StringBuilder b = new StringBuilder();
            for (String p : parts) {
                if (p.isEmpty()) continue;
                if (Character.isLetter(p.charAt(0))) {
                    b.append(Character.toUpperCase(p.charAt(0)));
                    if (p.length() > 1) b.append(p.substring(1).toLowerCase(Locale.getDefault()));
                } else {
                    b.append(p);
                }
                b.append(' ');
            }
            String out = b.toString().trim();
            return out.isEmpty() ? email : out;
        } catch (Exception e) {
            return email != null ? email : "Unknown User";
        }
    }
}
