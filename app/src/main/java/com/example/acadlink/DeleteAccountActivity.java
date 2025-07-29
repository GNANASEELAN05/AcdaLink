package com.example.acadlink;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.example.acadlink.databinding.ActivityDeleteAccountBinding;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

public class DeleteAccountActivity extends AppCompatActivity {

    private ActivityDeleteAccountBinding binding;
    private FirebaseAuth firebaseAuth;
    private FirebaseUser firebaseUser;
    private ProgressDialog progressDialog;

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

    private void deleteFromDatabaseAndAuth() {
        String uid = firebaseUser.getUid();

        FirebaseDatabase.getInstance().getReference("Users").child(uid)
                .removeValue()
                .addOnSuccessListener(unused -> {
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
