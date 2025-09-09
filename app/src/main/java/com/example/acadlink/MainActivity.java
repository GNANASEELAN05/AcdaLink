package com.example.acadlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNv;
    private View uploadProject;
    private TextView toobarTitleTv;
    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Ensure this matches your XML filename

        firebaseAuth = FirebaseAuth.getInstance();

        // Bind views
        bottomNv = findViewById(R.id.bottomNv);
        uploadProject = findViewById(R.id.uploadProject);
        toobarTitleTv = findViewById(R.id.toobarTitleTv);

        // Set initial fragment
        showHomeFragment();

        // Bottom navigation selection handler
        bottomNv.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                return onNavItemSelected(item);
            }
        });

        // FAB click - Upload project
        uploadProject.setOnClickListener(v -> {
            if (firebaseAuth.getCurrentUser() == null) {
                // Utils.toast(...) in your codebase may exist; if not, replace with Toast.makeText(...)
                try {
                    Utils.toast(MainActivity.this, "Login Required.");
                } catch (Exception e) {
                    // fallback
                    // android.widget.Toast.makeText(MainActivity.this, "Login Required.", android.widget.Toast.LENGTH_SHORT).show();
                }
                startLoginOption();
            } else {
                startActivity(new Intent(MainActivity.this, UploadProjectActivity.class));
            }
        });
    }

    private boolean onNavItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_home) {
            showHomeFragment();
            return true;
        } else if (id == R.id.menu_chats) {
            return handleProtectedSection(new ChatsFragment(), "Chats");
        } else if (id == R.id.menu_my_fav) {
            return handleProtectedSection(new MyBookMarkFragment(), "My Favorites");
        } else if (id == R.id.menu_account) {
            return handleProtectedSection(new AccountFragment(), "Account");
        }

        return false;
    }

    private boolean handleProtectedSection(Fragment fragment, String title) {
        if (firebaseAuth.getCurrentUser() == null) {
            try {
                Utils.toast(MainActivity.this, "Login Required.");
            } catch (Exception e) {
                // fallback: ignore
            }
            startLoginOption();
            return false;
        } else {
            toobarTitleTv.setText(title);
            replaceFragment(fragment);
            return true;
        }
    }

    private void showHomeFragment() {
        toobarTitleTv.setText("Home");
        replaceFragment(new HomeFragment());
    }

    private void replaceFragment(Fragment fragment) {
        try {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            // safer for modern FragmentManager behavior
            ft.setReorderingAllowed(true);
            ft.replace(R.id.fragmentsFl, fragment);
            // use allowing state loss to avoid IllegalStateException in edge cases (preferred to crash)
            ft.commitAllowingStateLoss();
        } catch (Exception e) {
            // very defensive: avoid crashing during state restore on some OS versions
            e.printStackTrace();
        }
    }

    private void startLoginOption() {
        Intent intent = new Intent(MainActivity.this, LoginOptionsActivity.class);
        startActivity(intent);
    }
}
