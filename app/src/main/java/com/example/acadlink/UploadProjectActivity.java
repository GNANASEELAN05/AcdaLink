package com.example.acadlink;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

public class UploadProjectActivity extends AppCompatActivity {

    private static final int FILE_PICKER_REQUEST_CODE = 101;
    private static final long MAX_FILE_SIZE_MB = 10;

    private MaterialAutoCompleteTextView projectType1Dropdown, projectType2Dropdown;
    private EditText projectTitleEt, abstractEt, methodologyEt;
    private TextView chooseFileBtn, uploadBtn, chooseFileError;
    private MaterialCardView chooseFileCv, uploadBtnCv;
    private TextInputLayout projectTitleTil, projectType1Til, projectType2Til, abstractTil, methodologyTil;
    private ImageView clearFileBtn;

    private Uri selectedFileUri = null;
    private boolean isFolderSelected = false;

    private final ColorStateList vibrantRed = ColorStateList.valueOf(0xFFD32F2F); // Dark Vibrant Red

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ❌ Removed EdgeToEdge
        setContentView(R.layout.activity_upload_project); // Make sure your root layout is ScrollView with fillViewport=true

        // Toolbar
        ImageButton toolbarBackBtn = findViewById(R.id.toolbarBackBtn);
        toolbarBackBtn.setOnClickListener(v -> onBackPressed());

        // Views
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

        // TextInputLayouts
        projectTitleTil = findViewById(R.id.projectTitleTil);
        projectType1Til = findViewById(R.id.projectType1Til);
        projectType2Til = findViewById(R.id.projectType2Til);
        abstractTil = findViewById(R.id.abstractTil);
        methodologyTil = findViewById(R.id.methodologyTil);

        setErrorColors();
        setupDropdowns();

        chooseFileCv.setOnClickListener(v -> showFileOrFolderChooser());

        clearFileBtn.setOnClickListener(v -> {
            selectedFileUri = null;
            chooseFileBtn.setText("Choose File");
            clearFileBtn.setVisibility(View.GONE);
            chooseFileCv.setStrokeColor(Color.parseColor("#7A7A7A"));
            chooseFileError.setVisibility(View.GONE);
        });

        uploadBtnCv.setOnClickListener(v -> {
            if (validateAndUpload()) {
                Intent intent = new Intent(UploadProjectActivity.this, ProjectDetailsActivity.class);
                intent.putExtra("projectTitle", projectTitleEt.getText().toString());
                intent.putExtra("projectType1", projectType1Dropdown.getText().toString());
                intent.putExtra("projectType2", projectType2Dropdown.getText().toString());
                intent.putExtra("abstract", abstractEt.getText().toString());
                intent.putExtra("methodology", methodologyEt.getText().toString());
                intent.putExtra("fileName", chooseFileBtn.getText().toString());
                startActivity(intent);
            }
        });
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

        ArrayAdapter<String> adapter1 = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, type1Options);
        projectType1Dropdown.setAdapter(adapter1);
        projectType1Dropdown.setOnClickListener(v -> projectType1Dropdown.showDropDown());

        ArrayAdapter<String> adapter2 = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, type2Options);
        projectType2Dropdown.setAdapter(adapter2);
        projectType2Dropdown.setOnClickListener(v -> projectType2Dropdown.showDropDown());
    }

    private void showFileOrFolderChooser() {
        Intent zipIntent = new Intent(Intent.ACTION_GET_CONTENT);
        zipIntent.setType("application/zip");
        zipIntent.addCategory(Intent.CATEGORY_OPENABLE);

        Intent folderIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        folderIntent.addCategory(Intent.CATEGORY_DEFAULT);

        Intent chooser = Intent.createChooser(zipIntent, "Select ZIP file or Folder");
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{folderIntent});
        startActivityForResult(chooser, FILE_PICKER_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        if (isTreeUri(uri)) {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            selectedFileUri = uri;
            isFolderSelected = true;
            chooseFileBtn.setText("Folder: " + getLastSegment(uri));
        } else {
            isFolderSelected = false;
            if (!uri.toString().endsWith(".zip") && !getFileName(uri).toLowerCase().endsWith(".zip")) {
                chooseFileError.setText("Only ZIP files or folders are allowed");
                chooseFileError.setVisibility(View.VISIBLE);
                chooseFileCv.setStrokeColor(Color.parseColor("#D32F2F"));
                return;
            }

            long fileSizeMB = getFileSize(uri) / (1024 * 1024);
            if (fileSizeMB > MAX_FILE_SIZE_MB) {
                chooseFileError.setText("ZIP file too large (Max 10MB)");
                chooseFileError.setVisibility(View.VISIBLE);
                chooseFileCv.setStrokeColor(Color.parseColor("#D32F2F"));
                return;
            }

            selectedFileUri = uri;
            chooseFileBtn.setText("ZIP: " + getFileName(uri));
        }

        clearFileBtn.setVisibility(View.VISIBLE);
        chooseFileError.setVisibility(View.GONE);
        chooseFileCv.setStrokeColor(Color.parseColor("#7A7A7A"));
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

        if (selectedFileUri == null) {
            chooseFileCv.setStrokeColor(Color.parseColor("#D32F2F"));
            chooseFileError.setText("Please select a ZIP file or folder");
            chooseFileError.setVisibility(View.VISIBLE);
            valid = false;
        } else {
            chooseFileCv.setStrokeColor(Color.parseColor("#7A7A7A"));
            chooseFileError.setVisibility(View.GONE);
        }

        return valid;
    }

    private boolean isTreeUri(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority()) ||
                "com.android.providers.downloads.documents".equals(uri.getAuthority()) ||
                "com.android.providers.media.documents".equals(uri.getAuthority()) ||
                "com.android.providers.documents.documents".equals(uri.getAuthority());
    }

    private long getFileSize(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        int sizeIndex = cursor != null ? cursor.getColumnIndex(OpenableColumns.SIZE) : -1;
        long size = 0;
        if (cursor != null && cursor.moveToFirst() && sizeIndex != -1) {
            size = cursor.getLong(sizeIndex);
        }
        if (cursor != null) cursor.close();
        return size;
    }

    private String getFileName(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        int nameIndex = cursor != null ? cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) : -1;
        String name = null;
        if (cursor != null && cursor.moveToFirst() && nameIndex != -1) {
            name = cursor.getString(nameIndex);
        }
        if (cursor != null) cursor.close();
        return name;
    }

    private String getLastSegment(Uri uri) {
        String path = uri.getLastPathSegment();
        if (path == null) return "Selected";
        if (path.contains("/")) return path.substring(path.lastIndexOf('/') + 1);
        return path;
    }
}
