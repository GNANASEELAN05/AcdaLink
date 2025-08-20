package com.example.acadlink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class FacultyLoginActivity extends AppCompatActivity {

    private static final String TAG = "FacultyLoginActivity";
    private static final String PREFS = "FacultyPrefs";
    private static final String KEY_LOGGED_IN = "isFacultyLoggedIn";
    private static final String KEY_EMAIL = "facultyEmail";

    private TextInputEditText emailEt, passwordEt;
    private TextInputLayout emailTil, passwordTil;
    private MaterialButton loginBtn;
    private ImageButton toolbarBackBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            EdgeToEdge.enable(this);
        } catch (Throwable t) {
            Log.w(TAG, "EdgeToEdge.enable not available: " + t.getMessage());
        }

        setContentView(R.layout.activity_faculty_login);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Init views
        toolbarBackBtn = findViewById(R.id.toolbarBackBtn);
        emailEt = findViewById(R.id.emailEt);
        passwordEt = findViewById(R.id.passwordEt);
        emailTil = findViewById(R.id.emailTil);
        passwordTil = findViewById(R.id.passwordTil);
        loginBtn = findViewById(R.id.loginBtn2);

        toolbarBackBtn.setOnClickListener(v -> {
            startActivity(new Intent(FacultyLoginActivity.this, LoginOptionsActivity.class));
            finish();
        });

        FacultyAuthManager.addDefaultCredentialsIfEmpty();

        loginBtn.setOnClickListener(v -> {
            emailTil.setError(null);
            passwordTil.setError(null);

            String email = emailEt.getText() != null ? emailEt.getText().toString().trim() : "";
            String pass = passwordEt.getText() != null ? passwordEt.getText().toString().trim() : "";

            if (email.isEmpty()) {
                emailTil.setError("Please enter email");
                return;
            }
            if (pass.isEmpty()) {
                passwordTil.setError("Please enter password");
                return;
            }

            if (FacultyAuthManager.isValid(email, pass)) {
                Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show();

                // ✅ Save faculty login state
                SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                prefs.edit()
                        .putBoolean(KEY_LOGGED_IN, true)
                        .putString(KEY_EMAIL, email)
                        .apply();

                startActivity(new Intent(FacultyLoginActivity.this, RequestFromStudentsToFacultyActivity.class));
                finishAffinity();
            } else {
                passwordTil.setError("Invalid email or password");
            }
        });
    }
}
