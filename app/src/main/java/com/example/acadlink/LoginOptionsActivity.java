package com.example.acadlink;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.acadlink.databinding.ActivityLoginOptionsBinding;
import com.google.firebase.auth.FirebaseAuth;

public class LoginOptionsActivity extends AppCompatActivity {

    private ActivityLoginOptionsBinding binding;
    private FirebaseAuth firebaseAuth;
    private static final String TAG = "LoginOptionsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance();

        // If already logged in, go to main screen
        if (firebaseAuth.getCurrentUser() != null) {
            Toast.makeText(this, "User already logged in", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(LoginOptionsActivity.this, MainActivity.class));
            finishAffinity();
            return;
        }

        // Setup view
        binding = ActivityLoginOptionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Handle edge insets
        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Close app
        binding.closeBtn.setOnClickListener(v -> finishAffinity());

        // Navigate to LoginEmailActivity
        binding.loginEmailBtn.setOnClickListener(v -> {
            try {
                Toast.makeText(this, "Opening Email Login...", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(LoginOptionsActivity.this, LoginEmailActivity.class);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                Log.e(TAG, "Failed to open LoginEmailActivity", e);
            }
   });
}
}
