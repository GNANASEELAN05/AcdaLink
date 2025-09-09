package com.example.acadlink;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.graphics.Color;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.example.acadlink.databinding.ActivityForgetPasswordBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

public class ForgetPasswordActivity extends AppCompatActivity {

    private ActivityForgetPasswordBinding binding;
    private FirebaseAuth firebaseAuth;
    private ProgressDialog progressDialog;
    private DatabaseReference usersRef;

    private String email = "";
    private static final String UNIVERSITY_EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@klu\\.ac\\.in$";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Make sure system bars do not draw behind our toolbar on modern Android versions
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        super.onCreate(savedInstanceState);

        binding = ActivityForgetPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // ensure keyboard resizing so inputs and submit button are visible when keyboard shows
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // Force background color to white
        binding.getRoot().setBackgroundColor(Color.WHITE);

        firebaseAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("Users");

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Please wait...");
        progressDialog.setCanceledOnTouchOutside(false);

        // Set static text color for toolbar title
        binding.toolbarTitleTv.setTextColor(Color.WHITE);

        binding.toolbarBackBtn.setOnClickListener(v -> onBackPressed());
        binding.submitBtn.setOnClickListener(v -> validateData());
    }

    private void validateData() {
        email = binding.emailEt.getText().toString().trim();
        binding.emailEt.setError(null);

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailEt.setError("Invalid Email Pattern!");
            binding.emailEt.requestFocus();
        } else if (!email.matches(UNIVERSITY_EMAIL_REGEX)) {
            binding.emailEt.setError("Use your registered university email ID");
            binding.emailEt.requestFocus();
        } else {
            checkIfEmailRegistered();
        }
    }

    private void checkIfEmailRegistered() {
        progressDialog.setMessage("Checking email...");
        progressDialog.show();

        usersRef.orderByChild("email").equalTo(email)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            sendPasswordResetEmail();
                        } else {
                            progressDialog.dismiss();
                            binding.emailEt.setError("This email ID is not registered");
                            binding.emailEt.requestFocus();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        progressDialog.dismiss();
                        binding.emailEt.setError("Database error: " + error.getMessage());
                        binding.emailEt.requestFocus();
                    }
                });
    }

    private void sendPasswordResetEmail() {
        progressDialog.setMessage("Sending reset instructions...");
        firebaseAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> {
                    progressDialog.dismiss();
                    showSuccessDialog();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    binding.emailEt.setError("Failed to send email: " + e.getMessage());
                    binding.emailEt.requestFocus();
                });
    }

    private void showSuccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Email Sent")
                .setMessage("Instructions to reset your password have been sent to " + email)
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    startActivity(new Intent(ForgetPasswordActivity.this, LoginEmailActivity.class));
                    finishAffinity();
                })
                .show();
    }
}
