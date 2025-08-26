package com.example.acadlink;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.acadlink.databinding.ActivityDeleteAccountBinding;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.ListResult;
import com.google.firebase.storage.StorageReference;

import java.util.concurrent.atomic.AtomicInteger;

public class DeleteAccountActivity extends AppCompatActivity {

    private ActivityDeleteAccountBinding binding;
    private FirebaseAuth firebaseAuth;
    private FirebaseUser firebaseUser;
    private ProgressDialog progressDialog;

    private static final String TAG = "DeleteAccount";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityDeleteAccountBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Force white background
        binding.getRoot().setBackgroundColor(Color.WHITE);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Please wait...");
        progressDialog.setCanceledOnTouchOutside(false);

        // Toolbar back navigation
        binding.toolbarBackBtn.setOnClickListener(v -> onBackPressed());

        // Set fixed black text for title
        binding.toolbarTitleTv.setTextColor(Color.BLACK);

        // Submit delete action
        binding.submitBtn.setOnClickListener(v -> showDeleteConfirmation());
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account permanently?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    if (firebaseUser != null && firebaseUser.getEmail() != null) {
                        promptPasswordAndDelete();
                    } else {
                        Utils.toast(this, "No user to delete. Please login again.");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptPasswordAndDelete() {
        EditText passwordInput = new EditText(this);
        passwordInput.setHint("Enter password");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setHintTextColor(Color.GRAY);
        passwordInput.setTextColor(Color.BLACK);

        new AlertDialog.Builder(this)
                .setTitle("Re-authenticate")
                .setMessage("Please enter your password to delete the account")
                .setView(passwordInput)
                .setPositiveButton("Delete", (dialog, which) -> {
                    String password = passwordInput.getText().toString().trim();
                    if (password.isEmpty()) {
                        Utils.toast(this, "Password is required.");
                        return;
                    }
                    reAuthenticateAndDelete(password);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void reAuthenticateAndDelete(String password) {
        String email = firebaseUser.getEmail();
        AuthCredential credential = EmailAuthProvider.getCredential(email, password);

        progressDialog.setMessage("Authenticating...");
        progressDialog.show();

        firebaseUser.reauthenticate(credential)
                .addOnSuccessListener(authResult -> {
                    progressDialog.setMessage("Deleting account...");
                    deleteFromDatabaseAndAuth();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Utils.toast(this, "Re-authentication failed: " + e.getMessage());
                });
    }

    /**
     * Modified:
     * - Deletes all projects uploaded by the user (database + storage)
     * - Deletes user profile from /Users/<uid>
     * - Deletes FirebaseAuth user
     */
    private void deleteFromDatabaseAndAuth() {
        String uid = firebaseUser.getUid();

        progressDialog.setMessage("Deleting your account...");
        progressDialog.show();

        // 1) Start background project deletion (Storage can take time, don't block UI)
        deleteAllUserProjects(uid);

        // 2) Delete Users/<uid> immediately
        FirebaseDatabase.getInstance().getReference("Users").child(uid)
                .removeValue()
                .addOnSuccessListener(unused -> {
                    // 3) Delete Auth user
                    firebaseUser.delete()
                            .addOnSuccessListener(unused1 -> {
                                progressDialog.dismiss();
                                showSuccessDialog();
                            })
                            .addOnFailureListener(e -> {
                                progressDialog.dismiss();
                                Utils.toast(this, "Failed to delete from Auth: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Utils.toast(this, "Failed to delete from Database: " + e.getMessage());
                });
    }

    // Run project deletion in background but don't wait
    private void deleteAllUserProjects(@NonNull String uid) {
        final DatabaseReference projectsRef = FirebaseDatabase.getInstance().getReference("projects");
        final StorageReference projectFilesRoot = FirebaseStorage.getInstance().getReference().child("project_files");

        projectsRef.orderByChild("authorUid").equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot proj : snapshot.getChildren()) {
                            String projectId = proj.getKey();
                            if (projectId == null) continue;

                            // Delete project DB node
                            projectsRef.child(projectId).removeValue();

                            // Delete project folder in Storage (fire and forget)
                            projectFilesRoot.child(projectId).listAll()
                                    .addOnSuccessListener(listResult -> {
                                        for (StorageReference item : listResult.getItems()) {
                                            item.delete(); // async, no waiting
                                        }
                                        for (StorageReference prefix : listResult.getPrefixes()) {
                                            deleteStorageFolderQuick(prefix);
                                        }
                                    });
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    // Non-blocking recursive delete
    private void deleteStorageFolderQuick(StorageReference folderRef) {
        folderRef.listAll()
                .addOnSuccessListener(listResult -> {
                    for (StorageReference item : listResult.getItems()) {
                        item.delete();
                    }
                    for (StorageReference prefix : listResult.getPrefixes()) {
                        deleteStorageFolderQuick(prefix);
                    }
                });
    }


    private void deleteStorageFolderRecursive(StorageReference folderRef, AtomicInteger pending) {
        folderRef.listAll()
                .addOnSuccessListener((ListResult listResult) -> {
                    for (StorageReference item : listResult.getItems()) {
                        pending.incrementAndGet();
                        item.delete().addOnCompleteListener(t -> completeOne(pending, null));
                    }
                    for (StorageReference prefix : listResult.getPrefixes()) {
                        pending.incrementAndGet();
                        deleteStorageFolderRecursive(prefix, pending);
                    }
                    completeOne(pending, null);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Folder delete skipped: " + e.getMessage());
                    completeOne(pending, null);
                });
    }

    private void completeOne(AtomicInteger pending, Runnable onAllDone) {
        if (pending.decrementAndGet() == 0 && onAllDone != null) {
            runOnUiThread(onAllDone);
        }
    }

    // ---------------------- (UNCHANGED) ----------------------

    private void showSuccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Account Deleted")
                .setMessage("Your account was deleted successfully.")
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    Intent intent = new Intent(DeleteAccountActivity.this, LoginOptionsActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .show();
    }
}
