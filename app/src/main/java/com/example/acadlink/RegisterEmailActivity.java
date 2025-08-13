package com.example.acadlink;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
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

    // Password rule views
    private LinearLayout passwordRulesLayout;
    private TextView ruleLength, ruleUpper, ruleLower, ruleDigit, ruleSpecial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityRegisterEmailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, imeInsets.bottom);
            return insets;
        });

        firebaseAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("Users");

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Please wait...");
        progressDialog.setCanceledOnTouchOutside(false);

        ImageButton backBtn = findViewById(R.id.toolbarBackBtn);
        backBtn.setOnClickListener(v -> onBackPressed());

        binding.haveAccountTv.setOnClickListener(view -> onBackPressed());

        binding.registerBtn.setOnClickListener(view -> validateData());

        // Department Dropdown
        String[] departments = new String[]{
                "Computer Science", "Information Technology", "Electronics and Communication",
                "Electrical and Electronics", "Mechanical Engineering", "Civil Engineering",
                "Artificial Intelligence", "Data Science", "Cyber Security", "Pharmacy",
                "Biotechnology", "Biomedical", "Food Technology", "Arts"
        };

        ArrayAdapter<String> departmentAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, departments
        );
        binding.departmentDropdown.setAdapter(departmentAdapter);
        binding.departmentDropdown.setOnClickListener(v -> binding.departmentDropdown.showDropDown());

        // Initialize password rule views
        passwordRulesLayout = findViewById(R.id.passwordRulesLayout);
        ruleLength = findViewById(R.id.ruleLength);
        ruleUpper = findViewById(R.id.ruleUpper);
        ruleLower = findViewById(R.id.ruleLower);
        ruleDigit = findViewById(R.id.ruleDigit);
        ruleSpecial = findViewById(R.id.ruleSpecial);

        passwordRulesLayout.setVisibility(View.GONE); // Initially hidden

        // Password TextWatcher
        binding.passwordEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    passwordRulesLayout.setVisibility(View.VISIBLE);
                } else {
                    passwordRulesLayout.setVisibility(View.GONE);
                }
                updatePasswordRules(s.toString());
            }
        });
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

        if (!isStrongPassword(password)) {
            binding.passwordEt.setError("Password must be strong (8+ chars, upper, lower, digit, special)");
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

    private boolean isStrongPassword(String password) {
        return password.matches("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$");
    }

    private void clearAllErrors() {
        binding.nameEt.setError(null);
        binding.phoneNumberEt.setError(null);
        binding.emailEt.setError(null);
        binding.passwordEt.setError(null);
        binding.cPasswordEt.setError(null);
        binding.departmentDropdown.setError(null);
    }

    private void updatePasswordRules(String password) {
        int green = Color.parseColor("#4CAF50");
        int red = Color.RED;

        ruleLength.setTextColor(password.length() >= 8 ? green : red);
        ruleUpper.setTextColor(password.matches(".*[A-Z].*") ? green : red);
        ruleLower.setTextColor(password.matches(".*[a-z].*") ? green : red);
        ruleDigit.setTextColor(password.matches(".*\\d.*") ? green : red);
        ruleSpecial.setTextColor(password.matches(".*[!@#$%^&+=].*") ? green : red);
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
        // store under the exact key the AccountFragment expects:
        userData.put("profileImage", "");
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
