package com.example.acadlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
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
        bottomNv.setOnItemSelectedListener(this::onNavItemSelected);

        // FAB click - Upload project
        uploadProject.setOnClickListener(v -> {
            if (firebaseAuth.getCurrentUser() == null) {
                Utils.toast(MainActivity.this, "Login Required.");
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
            return handleProtectedSection(new MyFavFragment(), "My Favorites");
        } else if (id == R.id.menu_account) {
            return handleProtectedSection(new AccountFragment(), "Account");
        }

        return false;
    }

    private boolean handleProtectedSection(Fragment fragment, String title) {
        if (firebaseAuth.getCurrentUser() == null) {
            Utils.toast(MainActivity.this, "Login Required.");
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
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.fragmentsFl, fragment);
        ft.commit();
    }

    private void startLoginOption() {
        Intent intent = new Intent(MainActivity.this, LoginOptionsActivity.class);
        startActivity(intent);
}
}
