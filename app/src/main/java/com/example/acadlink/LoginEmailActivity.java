package com.example.acadlink;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.acadlink.databinding.ActivityLoginEmailBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

public class LoginEmailActivity extends AppCompatActivity {

    private ActivityLoginEmailBinding binding;
    private ProgressDialog progressDialog;
    private FirebaseAuth firebaseAuth;
    private DatabaseReference usersRef;

    private String email, password;
    private static final String UNIVERSITY_DOMAIN = "@klu.ac.in";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep existing edge-to-edge and system window handling
        EdgeToEdge.enable(this);

        // Allow scroll when keyboard is shown and make system insets delivered to the root view
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // View Binding
        binding = ActivityLoginEmailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Proper padding with keyboard and system bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, ime.bottom);
            return insets;
        });

        // Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("Users");

        // Progress Dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Please Wait...");
        progressDialog.setCanceledOnTouchOutside(false);

        // Toolbar back button
        ImageButton backBtn = findViewById(R.id.toolbarBackBtn);
        backBtn.setOnClickListener(view -> onBackPressed());

        // Click listeners
        binding.noAccountTv.setOnClickListener(view ->
                startActivity(new Intent(LoginEmailActivity.this, RegisterEmailActivity.class)));

        binding.forgotPasswordTv.setOnClickListener(view ->
                startActivity(new Intent(LoginEmailActivity.this, ForgetPasswordActivity.class)));

        binding.loginBtn.setOnClickListener(view -> validateData());
    }

    private void validateData() {
        email = binding.emailEt.getText().toString().trim();
        password = binding.passwordEt.getText().toString();

        binding.emailEt.setError(null);
        binding.passwordEt.setError(null);

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailEt.setError("Invalid Email Format");
            return;
        }

        if (!email.toLowerCase().endsWith(UNIVERSITY_DOMAIN)) {
            binding.emailEt.setError("Use your registered university email ID");
            return;
        }

        if (password.isEmpty()) {
            binding.passwordEt.setError("Enter Password");
            return;
        }

        checkIfEmailRegistered();
    }

    private void checkIfEmailRegistered() {
        progressDialog.setMessage("Checking email...");
        progressDialog.show();

        usersRef.orderByChild("email").equalTo(email)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            loginUser();
                        } else {
                            progressDialog.dismiss();
                            binding.emailEt.setError("This email ID is not registered");
                            binding.emailEt.requestFocus();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        Utils.toast(LoginEmailActivity.this, "Database error: " + error.getMessage());
                    }
                });
    }

    private void loginUser() {
        progressDialog.setMessage("Logging In...");
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    progressDialog.dismiss();
                    // ALLOW login regardless of email verification status
                    startActivity(new Intent(LoginEmailActivity.this, MainActivity.class));
                    finishAffinity();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    if (e instanceof FirebaseAuthInvalidCredentialsException) {
                        binding.passwordEt.setError("Incorrect password. Try again.");
                        binding.passwordEt.requestFocus();
                    } else {
                        Utils.toast(LoginEmailActivity.this, "Login failed: " + e.getMessage());
                    }
                });
    }
}
