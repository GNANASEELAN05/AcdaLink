package com.example.acadlink;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;

public class UploadProjectActivity extends AppCompatActivity {

    private static final int REQUEST_FILE = 101;
    private static final int REQUEST_FOLDER = 102;
    private static final long MAX_TOTAL_FILE_SIZE_MB = 5;

    private MaterialAutoCompleteTextView projectType1Dropdown, projectType2Dropdown;
    private EditText projectTitleEt, abstractEt, methodologyEt;
    private TextView chooseFileBtn, uploadBtn, chooseFileError;
    private MaterialCardView chooseFileCv, uploadBtnCv;
    private TextInputLayout projectTitleTil, projectType1Til, projectType2Til, abstractTil, methodologyTil;
    private ImageView clearFileBtn;
    private LinearLayout fileListLayout;

    private final ArrayList<Uri> selectedUris = new ArrayList<>();
    private final ArrayList<String> selectedFileInfo = new ArrayList<>();
    private final ColorStateList vibrantRed = ColorStateList.valueOf(0xFFD32F2F);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Ensure system bars are NOT drawing behind content on 13+ (prevents toolbar overlap)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_project);

        ImageButton toolbarBackBtn = findViewById(R.id.toolbarBackBtn);
        toolbarBackBtn.setOnClickListener(v -> onBackPressed());

        projectTitleEt = findViewById(R.id.projectTitleEt);
        abstractEt = findViewById(R.id.abstractEt);
        methodologyEt = findViewById(R.id.methodologyEt);
        projectType1Dropdown = findViewById(R.id.projectType1Dropdown);
        projectType2Dropdown = findViewById(R.id.projectType2Dropdown);
        chooseFileBtn = findViewById(R.id.chooseFileBtn);
        uploadBtn = findViewById(R.id.uploadBtn);
        chooseFileError = findViewById(R.id.chooseFileError);
        chooseFileCv = findViewById(R.id.chooseFileCv);
        uploadBtnCv = findViewById(R.id.uploadBtnCv);
        clearFileBtn = findViewById(R.id.clearFileBtn);
        fileListLayout = findViewById(R.id.fileListLayout);

        projectTitleTil = findViewById(R.id.projectTitleTil);
        projectType1Til = findViewById(R.id.projectType1Til);
        projectType2Til = findViewById(R.id.projectType2Til);
        abstractTil = findViewById(R.id.abstractTil);
        methodologyTil = findViewById(R.id.methodologyTil);

        applyOutlinedStyle(projectTitleTil);
        applyOutlinedStyle(projectType1Til);
        applyOutlinedStyle(projectType2Til);
        applyOutlinedStyle(abstractTil);
        applyOutlinedStyle(methodologyTil);

        setErrorColors();
        setupDropdowns();

        chooseFileCv.setOnClickListener(v -> showChooserDialog());

        clearFileBtn.setOnClickListener(v -> {
            selectedUris.clear();
            selectedFileInfo.clear();
            chooseFileBtn.setText("Choose File(s)/Folder");
            clearFileBtn.setVisibility(View.GONE);
            chooseFileCv.setStrokeColor(Color.parseColor("#7A7A7A"));
            chooseFileError.setVisibility(View.GONE);
            fileListLayout.removeAllViews();
        });

        uploadBtnCv.setOnClickListener(v -> {
            if (validateAndUpload()) {
                Intent intent = new Intent(this, ProjectDetailsActivity.class);
                intent.putExtra("projectTitle", projectTitleEt.getText().toString());
                intent.putExtra("projectType1", projectType1Dropdown.getText().toString());
                intent.putExtra("projectType2", projectType2Dropdown.getText().toString());
                intent.putExtra("abstract", abstractEt.getText().toString());
                intent.putExtra("methodology", methodologyEt.getText().toString());
                intent.putExtra("fileCount", selectedUris.size());
                intent.putStringArrayListExtra("fileInfoList", selectedFileInfo);

                ArrayList<String> uriStrings = new ArrayList<>();
                for (Uri u : selectedUris) uriStrings.add(u.toString());
                intent.putStringArrayListExtra("fileUriList", uriStrings);

                startActivity(intent);
            }
        });
    }

    private void showChooserDialog() {
        String[] options = {"Choose Files", "Choose Folder"};
        new AlertDialog.Builder(this)
                .setTitle("Select Upload Type")
                .setItems(options, (dialog, which) -> {
                    Intent intent;
                    if (which == 0) {
                        intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.setType("*/*");
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        startActivityForResult(intent, REQUEST_FILE);
                    } else {
                        intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        startActivityForResult(intent, REQUEST_FOLDER);
                    }
                }).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        fileListLayout.removeAllViews();
        selectedUris.clear();
        selectedFileInfo.clear();
        chooseFileError.setVisibility(View.GONE);

        long totalSizeBytes = 0;
        boolean hasOversized = false;

        if (requestCode == REQUEST_FILE) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    long size = getFileSize(uri);
                    if (totalSizeBytes + size > MAX_TOTAL_FILE_SIZE_MB * 1024 * 1024) {
                        hasOversized = true;
                        continue;
                    }
                    selectedUris.add(uri);
                    totalSizeBytes += size;
                    addFileToList(uri, selectedUris.size());
                }
            } else {
                Uri uri = data.getData();
                long size = getFileSize(uri);
                if (size > MAX_TOTAL_FILE_SIZE_MB * 1024 * 1024) {
                    hasOversized = true;
                } else {
                    selectedUris.add(uri);
                    addFileToList(uri, 1);
                }
            }
        } else if (requestCode == REQUEST_FOLDER) {
            Uri treeUri = data.getData();
            if (treeUri == null) return;

            // Persist read (and write) permission for future access. Use read|write mask for takePersistableUriPermission.
            final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                // takePersistableUriPermission requires at least read or write flag present; guard against exceptions.
                if (takeFlags != 0) {
                    getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
                }
            } catch (SecurityException | IllegalArgumentException ignored) { /* ignore if permission can't be taken */ }

            DocumentFile folder = DocumentFile.fromTreeUri(this, treeUri);
            if (folder != null && folder.isDirectory()) {
                int count = 1;
                // Primary folder line (for display only)
                addFileToList(folder.getUri(), 0);

                DocumentFile[] files = folder.listFiles();
                if (files != null) {
                    for (DocumentFile file : files) {
                        try {
                            if (file != null && file.isFile()) {
                                long size = file.length();
                                if (totalSizeBytes + size > MAX_TOTAL_FILE_SIZE_MB * 1024 * 1024) {
                                    hasOversized = true;
                                    continue;
                                }
                                Uri fileUri = file.getUri();
                                selectedUris.add(fileUri);
                                totalSizeBytes += size;
                                addFileToList(fileUri, count++);
                            }
                        } catch (Exception ignore) {
                            // Robustly ignore problematic file entries so the whole operation doesn't crash.
                        }
                    }
                }
            }
        }

        if (!selectedUris.isEmpty()) {
            chooseFileBtn.setText(selectedUris.size() + " file(s) selected");
            clearFileBtn.setVisibility(View.VISIBLE);
            chooseFileCv.setStrokeColor(Color.parseColor("#7A7A7A"));
        }

        if (hasOversized) {
            chooseFileError.setText("Some files exceed the total 5MB limit and were not selected");
            chooseFileError.setVisibility(View.VISIBLE);
            chooseFileCv.setStrokeColor(Color.parseColor("#D32F2F"));
        }
    }

    private void addFileToList(Uri uri, int count) {
        String name = getFileName(uri);
        long sizeKB = getFileSize(uri) / 1024;
        String sizeText = sizeKB >= 1024
                ? String.format("%.1f MB", sizeKB / 1024f)
                : sizeKB + " KB";

        String info = count == 0
                ? "Primary Folder: " + name
                : count + ". " + name + " (" + sizeText + ")";
        selectedFileInfo.add(info);

        TextView fileView = new TextView(this);
        fileView.setText(info);
        fileView.setTextColor(Color.DKGRAY);
        fileView.setTextSize(14);
        fileListLayout.addView(fileView);
    }

    private boolean validateAndUpload() {
        boolean valid = true;

        if (projectTitleEt.getText().toString().trim().isEmpty()) {
            projectTitleTil.setError("Enter project title");
            valid = false;
        } else projectTitleTil.setError(null);

        if (projectType1Dropdown.getText().toString().trim().isEmpty()) {
            projectType1Til.setError("Select project type");
            valid = false;
        } else projectType1Til.setError(null);

        if (projectType2Dropdown.getText().toString().trim().isEmpty()) {
            projectType2Til.setError("Select project level");
            valid = false;
        } else projectType2Til.setError(null);

        if (abstractEt.getText().toString().trim().isEmpty()) {
            abstractTil.setError("Enter abstract");
            valid = false;
        } else abstractTil.setError(null);

        if (methodologyEt.getText().toString().trim().isEmpty()) {
            methodologyTil.setError("Enter methodology");
            valid = false;
        } else methodologyTil.setError(null);

        if (selectedUris.isEmpty()) {
            chooseFileCv.setStrokeColor(Color.parseColor("#D32F2F"));
            chooseFileError.setText("Please select at least one file/folder");
            chooseFileError.setVisibility(View.VISIBLE);
            valid = false;
        }

        return valid;
    }

    private void applyOutlinedStyle(TextInputLayout til) {
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setBoxStrokeWidth(2);
        til.setBoxCornerRadii(10, 10, 10, 10);
        til.setBoxStrokeColor(Color.parseColor("#03A9F4"));
    }

    private void setErrorColors() {
        projectTitleTil.setErrorTextColor(vibrantRed);
        projectType1Til.setErrorTextColor(vibrantRed);
        projectType2Til.setErrorTextColor(vibrantRed);
        abstractTil.setErrorTextColor(vibrantRed);
        methodologyTil.setErrorTextColor(vibrantRed);
    }

    private void setupDropdowns() {
        String[] type1Options = {"Software", "Hardware"};
        String[] type2Options = {"Ordinary Project", "Final Year Capstone Project"};

        ArrayAdapter<String> adapter1 =
                new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, type1Options);
        projectType1Dropdown.setAdapter(adapter1);
        projectType1Dropdown.setOnClickListener(v -> projectType1Dropdown.showDropDown());

        ArrayAdapter<String> adapter2 =
                new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, type2Options);
        projectType2Dropdown.setAdapter(adapter2);
        projectType2Dropdown.setOnClickListener(v -> projectType2Dropdown.showDropDown());
    }

    private long getFileSize(Uri uri) {
        if (uri == null) return 0;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1) {
                    try {
                        return cursor.getLong(sizeIndex);
                    } catch (Exception ignore) {
                        // some providers may return non-long or throw; ignore and fall back
                    }
                }
            }
        } catch (Exception ignore) {
            // defensive: some providers may throw for certain URIs; return 0 if so
        }
        return 0;
    }

    private String getFileName(Uri uri) {
        if (uri == null) return "Unknown";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    try {
                        String name = cursor.getString(nameIndex);
                        if (name != null) return name;
                    } catch (Exception ignore) { /* fallback below */ }
                }
            }
        } catch (Exception ignore) {
            // ignore and fall back
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "Unknown";
    }
}
