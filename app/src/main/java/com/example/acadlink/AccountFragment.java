package com.example.acadlink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.acadlink.databinding.FragmentAccountBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AccountFragment extends Fragment {

    private FragmentAccountBinding binding;
    private Context mContext;
    private FirebaseAuth firebaseAuth;
    private FirebaseUser firebaseUser;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAccountBinding.inflate(inflater, container, false);
        mContext = getContext();

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();

        loadUserInfoFromCache();
        fetchUserDetailsFromFirebase();

        binding.refreshBtn.setOnClickListener(v -> {
            Toast.makeText(mContext, "Refreshing...", Toast.LENGTH_SHORT).show();
            fetchUserDetailsFromFirebase();
        });

        binding.verifyAccountCv.setOnClickListener(v -> {
            if (firebaseUser != null) {
                firebaseUser.sendEmailVerification()
                        .addOnSuccessListener(unused -> {
                            String email = firebaseUser.getEmail();
                            Toast.makeText(mContext, "Verification link sent to your email: " + email, Toast.LENGTH_LONG).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(mContext, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });

        binding.logoutCv.setOnClickListener(v -> {
            if (firebaseUser != null && !firebaseUser.isEmailVerified()) {
                Toast.makeText(mContext, "Verify your account to logout", Toast.LENGTH_SHORT).show();
            } else {
                firebaseAuth.signOut();
                startActivity(new Intent(mContext, LoginOptionsActivity.class));
                requireActivity().finish();
            }
        });

        binding.editProfileCv.setOnClickListener(v -> {
            Intent intent = new Intent(mContext, ProfileEditActivity.class);
            startActivity(intent);
        });

        binding.deleteAccountCv.setOnClickListener(v -> {
            Intent intent = new Intent(mContext, DeleteAccountActivity.class);
            startActivity(intent);
        });

        return binding.getRoot();
    }

    private void loadUserInfoFromCache() {
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("UserInfo", Context.MODE_PRIVATE);

        binding.nameTv.setText(sharedPreferences.getString("name", ""));
        binding.emailTv.setText(sharedPreferences.getString("email", ""));
        binding.phoneTv.setText(sharedPreferences.getString("phone", ""));
        binding.departmentTv.setText(sharedPreferences.getString("department", ""));
        binding.dobTv.setText(sharedPreferences.getString("dob", ""));

        try {
            String timestamp = sharedPreferences.getString("memberSince", "");
            if (!timestamp.isEmpty()) {
                long timeMillis = Long.parseLong(timestamp);
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy");
                binding.memberSinceTv.setText(sdf.format(new java.util.Date(timeMillis)));
            } else {
                binding.memberSinceTv.setText("N/A");
            }
        } catch (Exception e) {
            binding.memberSinceTv.setText("N/A");
        }

        String profileImageUrl = sharedPreferences.getString("profileImage", "");
        if (!profileImageUrl.isEmpty()) {
            Glide.with(mContext)
                    .load(profileImageUrl)
                    .placeholder(R.drawable.ic_person_white)
                    .into(binding.profileIv);
        }

        if (firebaseUser != null && firebaseUser.isEmailVerified()) {
            showVerifiedStatus();
            binding.verifyAccountCv.setVisibility(View.GONE);
        } else {
            showUnverifiedStatus();
            binding.verifyAccountCv.setVisibility(View.VISIBLE);
        }
    }

    private void fetchUserDetailsFromFirebase() {
        if (firebaseUser == null) return;

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Users");
        ref.child(firebaseUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                String name = String.valueOf(snapshot.child("name").getValue());
                String email = String.valueOf(snapshot.child("email").getValue());
                String phoneCode = String.valueOf(snapshot.child("phoneCode").getValue());
                String phoneNumber = String.valueOf(snapshot.child("phoneNumber").getValue());
                String phone = (phoneCode + " " + phoneNumber).trim();
                String department = String.valueOf(snapshot.child("department").getValue());
                String profileImageUrl = String.valueOf(snapshot.child("profileImage").getValue());
                String dob = String.valueOf(snapshot.child("dob").getValue());
                String timestamp = String.valueOf(snapshot.child("timestamp").getValue());

                binding.nameTv.setText(name);
                binding.emailTv.setText(email);
                binding.phoneTv.setText(phone);
                binding.departmentTv.setText(department);
                binding.dobTv.setText(dob);

                try {
                    long timeMillis = Long.parseLong(timestamp);
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy");
                    binding.memberSinceTv.setText(sdf.format(new java.util.Date(timeMillis)));
                } catch (Exception e) {
                    binding.memberSinceTv.setText("N/A");
                }

                if (!profileImageUrl.isEmpty()) {
                    Glide.with(mContext)
                            .load(profileImageUrl)
                            .placeholder(R.drawable.ic_person_white)
                            .into(binding.profileIv);
                }

                SharedPreferences.Editor editor = requireActivity().getSharedPreferences("UserInfo", Context.MODE_PRIVATE).edit();
                editor.putString("name", name);
                editor.putString("email", email);
                editor.putString("phone", phone);
                editor.putString("department", department);
                editor.putString("dob", dob);
                editor.putString("memberSince", timestamp);
                editor.putString("profileImage", profileImageUrl);
                editor.apply();

                firebaseUser.reload().addOnCompleteListener(task -> {
                    if (firebaseUser.isEmailVerified()) {
                        showVerifiedStatus();
                        binding.verifyAccountCv.setVisibility(View.GONE);
                    } else {
                        showUnverifiedStatus();
                        binding.verifyAccountCv.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("AccountFragment", "Database error: " + error.getMessage());
            }
        });
    }

    private void showVerifiedStatus() {
        binding.verificationTv.setText("Verified");
        binding.verificationTv.setTextColor(ContextCompat.getColor(mContext, R.color.green));
        Drawable icon = ContextCompat.getDrawable(mContext, R.drawable.ic_verify_green);
        if (icon != null) {
            binding.verificationTv.setCompoundDrawablesWithIntrinsicBounds(null, null, icon, null);
            binding.verificationTv.setCompoundDrawablePadding(10);
        }
    }

    private void showUnverifiedStatus() {
        binding.verificationTv.setText("Not Verified");
        binding.verificationTv.setTextColor(ContextCompat.getColor(mContext, R.color.purple_700));
        binding.verificationTv.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
    }
}
