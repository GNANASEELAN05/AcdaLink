package com.example.acadlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNv;
    private View uploadProject;
    private TextView toobarTitleTv;
    private FirebaseAuth firebaseAuth;
    private ViewPager2 fragmentsVp;

    // helpers to keep bottom nav and viewpager in sync
    private boolean isProgrammaticNav = false;
    private int currentPage = 0;

    // keep same menu IDs/order as your menu_bottom (so we changed nothing else)
    private final int[] menuIds = {
            R.id.menu_home,
            R.id.menu_chats,
            R.id.menu_my_fav,
            R.id.menu_account
    };

    private final String[] titles = {
            "Home",
            "Chats",
            "My Favorites",
            "Account"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // same filename as before

        firebaseAuth = FirebaseAuth.getInstance();

        // Bind views
        bottomNv = findViewById(R.id.bottomNv);
        uploadProject = findViewById(R.id.uploadProject);
        toobarTitleTv = findViewById(R.id.toobarTitleTv);
        fragmentsVp = findViewById(R.id.fragmentsFl); // now ViewPager2 in layout

        // Setup ViewPager2 with adapter
        fragmentsVp.setAdapter(new ScreenSlidePagerAdapter(this));
        fragmentsVp.setOffscreenPageLimit(3);

        // Initialize to Home
        fragmentsVp.setCurrentItem(0, false);
        toobarTitleTv.setText(titles[0]);
        isProgrammaticNav = true;
        bottomNv.setSelectedItemId(menuIds[0]);
        isProgrammaticNav = false;

        // Handle page changes (swipes)
        fragmentsVp.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);

                // If the page requires login and user is not logged in -> revert and show login
                if (requiresLogin(position) && firebaseAuth.getCurrentUser() == null) {
                    try {
                        Utils.toast(MainActivity.this, "Login Required.");
                    } catch (Exception e) {
                        // fallback to Android Toast if Utils is not available
                        android.widget.Toast.makeText(MainActivity.this, "Login Required.", android.widget.Toast.LENGTH_SHORT).show();
                    }
                    startLoginOption();

                    // revert to previous page (no animation)
                    fragmentsVp.setCurrentItem(currentPage, false);

                    // keep bottom nav selection consistent
                    isProgrammaticNav = true;
                    bottomNv.setSelectedItemId(menuIds[currentPage]);
                    isProgrammaticNav = false;
                } else {
                    // allowed page: update title and bottom navigation
                    currentPage = position;
                    toobarTitleTv.setText(titles[position]);

                    isProgrammaticNav = true;
                    bottomNv.setSelectedItemId(menuIds[position]);
                    isProgrammaticNav = false;
                }
            }
        });

        // Bottom navigation selection -> change ViewPager page (with login guard)
        bottomNv.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                // if this selection is coming from programmatic change (ViewPager), ignore here
                if (isProgrammaticNav) {
                    return true;
                }

                int id = item.getItemId();
                int pos = indexOfMenuId(id);
                if (pos < 0) return false;

                if (requiresLogin(pos) && firebaseAuth.getCurrentUser() == null) {
                    try {
                        Utils.toast(MainActivity.this, "Login Required.");
                    } catch (Exception e) {
                        android.widget.Toast.makeText(MainActivity.this, "Login Required.", android.widget.Toast.LENGTH_SHORT).show();
                    }
                    startLoginOption();
                    return false; // do not select the protected item
                } else {
                    // allowed: move viewpager (no animation) — changed smooth scroll to false to jump instantly
                    fragmentsVp.setCurrentItem(pos, false);
                    return true;
                }
            }
        });

        // FAB click - Upload project (same logic as before)
        uploadProject.setOnClickListener(v -> {
            if (firebaseAuth.getCurrentUser() == null) {
                try {
                    Utils.toast(MainActivity.this, "Login Required.");
                } catch (Exception e) {
                    android.widget.Toast.makeText(MainActivity.this, "Login Required.", android.widget.Toast.LENGTH_SHORT).show();
                }
                startLoginOption();
            } else {
                startActivity(new Intent(MainActivity.this, UploadProjectActivity.class));
            }
        });
    }

    private boolean requiresLogin(int position) {
        // Home (position 0) is public; chats, my favorites and account (positions 1..3) require login
        return position != 0;
    }

    private int indexOfMenuId(int id) {
        for (int i = 0; i < menuIds.length; i++) {
            if (menuIds[i] == id) return i;
        }
        return -1;
    }

    private void startLoginOption() {
        Intent intent = new Intent(MainActivity.this, LoginOptionsActivity.class);
        startActivity(intent);
    }

    /**
     * Adapter for ViewPager2: returns the appropriate Fragment for each position.
     * Order matches your bottom navigation (menu_home, menu_chats, menu_my_fav, menu_account)
     */
    private static class ScreenSlidePagerAdapter extends FragmentStateAdapter {

        public ScreenSlidePagerAdapter(@NonNull FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 1:
                    return new ChatsFragment();
                case 2:
                    return new MyBookMarkFragment();
                case 3:
                    return new AccountFragment();
                default:
                    return new HomeFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 4; // same number of items as bottom menu
        }
    }
}
