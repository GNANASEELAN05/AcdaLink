package com.example.acadlink;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.acadlink.databinding.ActivityRegisterEmailBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;

public class RegisterEmailActivity extends AppCompatActivity {

    private ActivityRegisterEmailBinding binding;
    private static final String TAG = "REGISTER_TAG";

    private FirebaseAuth firebaseAuth;
    private ProgressDialog progressDialog;
    private DatabaseReference usersRef;

    private String email, password, cPassword, name, phoneCode, phoneNumber, department;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityRegisterEmailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        firebaseAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("Users");

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Please wait...");
        progressDialog.setCanceledOnTouchOutside(false);

        // Back button click
        ImageButton backBtn = findViewById(R.id.toolbarBackBtn);
        backBtn.setOnClickListener(v -> onBackPressed());

        // Already have account
        binding.haveAccountTv.setOnClickListener(view -> onBackPressed());

        // Register click
        binding.registerBtn.setOnClickListener(view -> validateData());

        // Department Dropdown Setup
        String[] departments = new String[]{
                "Computer Science",
                "Information Technology",
                "Electronics and Communication",
                "Electrical and Electronics",
                "Mechanical Engineering",
                "Civil Engineering",
                "Artificial Intelligence",
                "Data Science",
                "Cyber Security",
                "Pharmacy",
                "Biotechnology",
                "Biomedical",
                "Food Technology",
                "Arts"
        };

        ArrayAdapter<String> departmentAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                departments
        );

        binding.departmentDropdown.setAdapter(departmentAdapter);
        binding.departmentDropdown.setOnClickListener(v -> binding.departmentDropdown.showDropDown());
    }

    private void validateData() {
        name = binding.nameEt.getText().toString().trim();
        phoneCode = binding.countryCodePicker.getSelectedCountryCodeWithPlus();
        phoneNumber = binding.phoneNumberEt.getText().toString().trim();
        email = binding.emailEt.getText().toString().trim();
        password = binding.passwordEt.getText().toString().trim();
        cPassword = binding.cPasswordEt.getText().toString().trim();
        department = binding.departmentDropdown.getText().toString().trim();

        clearAllErrors();

        if (name.isEmpty()) {
            binding.nameEt.setError("Enter name");
            return;
        }

        if (phoneNumber.isEmpty()) {
            binding.phoneNumberEt.setError("Enter phone number");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailEt.setError("Invalid email format");
            return;
        }

        if (!email.toLowerCase().endsWith("@klu.ac.in")) {
            binding.emailEt.setError("Use your university email (@klu.ac.in)");
            Utils.toast(this, "Use your university email (@klu.ac.in)");
            return;
        }

        if (password.isEmpty()) {
            binding.passwordEt.setError("Enter password");
            return;
        }

        if (!password.equals(cPassword)) {
            binding.cPasswordEt.setError("Passwords do not match");
            return;
        }

        if (department.isEmpty()) {
            binding.departmentDropdown.setError("Select department");
            return;
        }

        registerUser();
    }

    private void clearAllErrors() {
        binding.nameEt.setError(null);
        binding.phoneNumberEt.setError(null);
        binding.emailEt.setError(null);
        binding.passwordEt.setError(null);
        binding.cPasswordEt.setError(null);
        binding.departmentDropdown.setError(null);
    }

    private void registerUser() {
        progressDialog.setMessage("Creating account...");
        progressDialog.show();

        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    Log.d(TAG, "onSuccess: Registered");
                    updateUserInfo();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Utils.toast(this, "Failed: " + e.getMessage());
                });
    }

    private void updateUserInfo() {
        progressDialog.setMessage("Saving user info...");

        String uid = firebaseAuth.getUid();
        long timestamp = Utils.getTimestamp();

        HashMap<String, Object> userData = new HashMap<>();
        userData.put("uid", uid);
        userData.put("email", email);
        userData.put("name", name);
        userData.put("phoneCode", phoneCode);
        userData.put("phoneNumber", phoneNumber);
        userData.put("dob", "");
        userData.put("department", department);
        userData.put("profileImageUrl", "");
        userData.put("userType", "Email");
        userData.put("typingTo", "");
        userData.put("timestamp", timestamp);
        userData.put("onlineStatus", true);

        usersRef.child(uid)
                .setValue(userData)
                .addOnSuccessListener(unused -> {
                    progressDialog.dismiss();
                    Utils.toast(this, "Account created successfully");
                    startActivity(new Intent(this, MainActivity.class));
                    finishAffinity();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Utils.toast(this, "Failed to save: " + e.getMessage());
           });
}
}
