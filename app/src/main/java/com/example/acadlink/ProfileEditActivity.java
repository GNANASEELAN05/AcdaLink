package com.example.acadlink;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.acadlink.databinding.ActivityProfileEditBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.Calendar;
import java.util.HashMap;

public class ProfileEditActivity extends AppCompatActivity {

    private ActivityProfileEditBinding binding;
    private static final String TAG = "PROFILE_EDIT_TAG";

    private FirebaseAuth firebaseAuth;
    private DatabaseReference usersRef;
    private ProgressDialog progressDialog;

    private final String[] departmentList = new String[]{
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false); // ensures full edge-to-edge support

        binding = ActivityProfileEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // ✅ Fix for keyboard + status bar (adds padding to top and bottom)
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

        setupDepartmentDropdown();
        loadUserDetails();

        binding.toolbarBackBtn.setOnClickListener(v -> onBackPressed());
        binding.dobEt.setOnClickListener(v -> showDatePicker());
        binding.updateBtn.setOnClickListener(v -> validateAndUpdate());
    }

    private void setupDepartmentDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                departmentList
        );
        binding.departmentDropdown.setAdapter(adapter);
        binding.departmentDropdown.setOnClickListener(v -> binding.departmentDropdown.showDropDown());
    }

    private void showDatePicker() {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dpd = new DatePickerDialog(
                this,
                (view, year1, month1, dayOfMonth) -> {
                    String dob = String.format("%02d/%02d/%04d", dayOfMonth, (month1 + 1), year1);
                    binding.dobEt.setText(dob);
                },
                year, month, day
        );
        dpd.show();
    }

    private void loadUserDetails() {
        usersRef.child(firebaseAuth.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (!snapshot.exists()) return;

                        String name = "" + snapshot.child("name").getValue();
                        String email = "" + snapshot.child("email").getValue();
                        String phoneCode = "" + snapshot.child("phoneCode").getValue();
                        String phoneNumber = "" + snapshot.child("phoneNumber").getValue();
                        String dob = "" + snapshot.child("dob").getValue();
                        String department = "" + snapshot.child("department").getValue();
                        String profileImageUrl = "" + snapshot.child("profileImageUrl").getValue();

                        binding.nameEt.setText(name);
                        binding.emailEt.setText(email);
                        binding.phoneNumberEt.setText(phoneNumber);
                        binding.dobEt.setText(dob);
                        binding.departmentDropdown.setText(department, false);

                        try {
                            int codeInt = Integer.parseInt(phoneCode.replace("+", ""));
                            binding.countryCodePicker.setCountryForPhoneCode(codeInt);
                        } catch (Exception e) {
                            Log.e(TAG, "Invalid phone code: " + e.getMessage());
                        }

                        try {
                            Glide.with(ProfileEditActivity.this)
                                    .load(profileImageUrl)
                                    .placeholder(R.drawable.ic_person_white)
                                    .into(binding.profileIv);
                        } catch (Exception e) {
                            Log.e(TAG, "Image load failed", e);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Database error: " + error.getMessage());
                    }
                });
    }

    private void validateAndUpdate() {
        String name = binding.nameEt.getText().toString().trim();
        String dob = binding.dobEt.getText().toString().trim();
        String phoneNumber = binding.phoneNumberEt.getText().toString().trim();
        String phoneCode = binding.countryCodePicker.getSelectedCountryCodeWithPlus();
        String department = binding.departmentDropdown.getText().toString().trim();

        boolean valid = true;

        if (TextUtils.isEmpty(name)) {
            binding.nameTil.setError("Enter name");
            valid = false;
        } else {
            binding.nameTil.setError(null);
        }

        if (TextUtils.isEmpty(dob)) {
            binding.dobTil.setError("Enter DOB");
            valid = false;
        } else {
            binding.dobTil.setError(null);
        }

        if (TextUtils.isEmpty(phoneNumber)) {
            binding.phoneNumberTil.setError("Enter phone number");
            valid = false;
        } else {
            binding.phoneNumberTil.setError(null);
        }

        if (TextUtils.isEmpty(department)) {
            binding.departmentTil.setError("Select department");
            valid = false;
        } else {
            binding.departmentTil.setError(null);
        }

        if (!valid) return;

        progressDialog.setMessage("Updating profile...");
        progressDialog.show();

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("name", name);
        hashMap.put("dob", dob);
        hashMap.put("phoneCode", phoneCode);
        hashMap.put("phoneNumber", phoneNumber);
        hashMap.put("department", department);

        usersRef.child(firebaseAuth.getUid()).updateChildren(hashMap)
                .addOnSuccessListener(unused -> {
                    progressDialog.dismiss();
                    Utils.toast(ProfileEditActivity.this, "Profile updated successfully! Refresh and check.");
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Utils.toast(ProfileEditActivity.this, "Update failed: " + e.getMessage());
                });
    }
}
