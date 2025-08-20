package com.example.acadlink;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private String mParam2;

    // UI
    private EditText searchEt;
    private LinearLayout resultsContainer;

    // Data
    private final List<Project> allProjects = new ArrayList<>();

    // Realtime Database reference
    private DatabaseReference projectsRef;

    public static HomeFragment newInstance(String param1, String param2) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    public HomeFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        // Init Realtime DB
        projectsRef = FirebaseDatabase.getInstance().getReference("projects");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_home, container, false);

        // Header elements
        ImageButton menuBtn = rootView.findViewById(R.id.toolbarMenuBtns);
        searchEt = rootView.findViewById(R.id.searchEditText);

        // Menu popup
        menuBtn.setOnClickListener(v -> {
            ContextThemeWrapper ctw = new ContextThemeWrapper(requireContext(), R.style.DarkPopupMenu);
            PopupMenu popup = new PopupMenu(ctw, v);

            String[] items = getResources().getStringArray(R.array.home_menu_items);
            for (int i = 0; i < items.length; i++) {
                popup.getMenu().add(Menu.NONE, i, i, items[i]);
            }

            popup.setOnMenuItemClickListener(item -> {
                String title = String.valueOf(item.getTitle()).trim();

                if (title.equalsIgnoreCase("My Projects")) {
                    startActivity(new Intent(getActivity(), MyProjects.class));
                    return true;
                } else if (title.equalsIgnoreCase("All Projects")) {
                    startActivity(new Intent(getActivity(), AllProjectsActivity.class));
                    return true;
                } else if (title.equalsIgnoreCase("Request To Faculty")) {
                    // ✅ Navigate to RequestToFacultyActivity
                    startActivity(new Intent(getActivity(), RequestToFacultyActivity.class));
                    return true;
                } else if (title.equalsIgnoreCase("Request Received")) {
                    // ✅ Navigate to RequestReceivedActivity
                    startActivity(new Intent(getActivity(), RequestReceivedActivity.class));
                    return true;
                } else if (title.equalsIgnoreCase("Request Sent")) {
                    // ✅ Navigate to RequestSentActivity
                    startActivity(new Intent(getActivity(), RequestSentActivity.class));
                    return true;
                }

                Toast.makeText(getContext(), title, Toast.LENGTH_SHORT).show();
                return true;
            });

            popup.show();
        });

        // Inject container under search bar
        ViewGroup parent = (ViewGroup) searchEt.getParent();
        resultsContainer = new LinearLayout(requireContext());
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        resultsContainer.setPadding(dp(16), dp(8), dp(16), 0);

        int searchIndex = parent.indexOfChild(searchEt);
        parent.addView(resultsContainer, searchIndex + 1);

        // Load projects
        loadProjectsFromRealtimeDb();

        // Search filter
        searchEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderResults(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        return rootView;
    }

    // ---------------- REALTIME DATABASE ----------------

    private void loadProjectsFromRealtimeDb() {
        projectsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allProjects.clear();

                for (DataSnapshot child : snapshot.getChildren()) {
                    if (child.hasChild("title") || child.hasChild("projectTitle")) {
                        String id = child.getKey();
                        String title = safe(child.child("title").getValue());
                        if (title.isEmpty()) title = safe(child.child("projectTitle").getValue());

                        String type1 = safe(child.child("projectType1").getValue());
                        String level = safe(child.child("projectLevel").getValue());
                        String abs = safe(child.child("abstract").getValue());

                        String subtitle = buildSubtitle(type1, level, abs);
                        allProjects.add(new Project(id, title, subtitle));
                    }
                }

                renderResults(searchEt.getText() != null ? searchEt.getText().toString() : "");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to load projects", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String safe(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String buildSubtitle(String type1, String level, String abstractText) {
        StringBuilder sb = new StringBuilder();
        if (!type1.isEmpty()) sb.append(type1);
        if (!level.isEmpty()) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append(level);
        }
        if (sb.length() == 0 && !abstractText.isEmpty()) {
            String s = abstractText.trim();
            if (s.length() > 80) s = s.substring(0, 80) + "…";
            sb.append(s);
        }
        return sb.toString();
    }

    // ---------------- UI HELPERS ----------------

    private void renderResults(String query) {
        resultsContainer.removeAllViews();

        String q = query.trim().toLowerCase(Locale.getDefault());
        if (q.isEmpty()) {
            return;
        }

        List<Project> matches = new ArrayList<>();
        for (Project p : allProjects) {
            if (p.title.toLowerCase(Locale.getDefault()).contains(q)) {
                matches.add(p);
            }
        }

        if (matches.isEmpty()) {
            resultsContainer.addView(makeInfoText("No projects found"));
            return;
        }

        for (Project p : matches) {
            resultsContainer.addView(makeProjectCard(p));
        }
    }

    private View makeProjectCard(Project p) {
        CardView card = new CardView(requireContext());
        CardView.LayoutParams cardLp = new CardView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(0, dp(8), 0, dp(8));
        card.setLayoutParams(cardLp);
        card.setRadius(dp(12));
        card.setUseCompatPadding(true);
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(0xFFF5F5F5); // light gray background

        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView titleTv = new TextView(requireContext());
        titleTv.setText(p.title);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleTv.setTypeface(titleTv.getTypeface(), android.graphics.Typeface.BOLD);
        titleTv.setTextColor(0xFF000000); // black bold title

        TextView subTv = new TextView(requireContext());
        subTv.setText(p.subtitle);
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subTv.setTextColor(0xFF000000); // black normal text

        box.addView(titleTv);
        if (p.subtitle != null && !p.subtitle.isEmpty()) box.addView(subTv);

        card.addView(box);

        // Card click → go to ProjectSummaryActivity
        card.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AllProjectSummaryActivity.class);
            intent.putExtra("projectId", p.id);
            startActivity(intent);
        });

        return card;
    }

    private View makeInfoText(String message) {
        TextView tv = new TextView(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, 0);
        tv.setLayoutParams(lp);
        tv.setText(message);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTextColor(0xFF6E6E6E);
        return tv;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    // Model
    private static class Project {
        final String id;
        final String title;
        final String subtitle;
        Project(String id, String title, String subtitle) {
            this.id = id == null ? "" : id;
            this.title = title == null ? "" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
        }
    }
}
