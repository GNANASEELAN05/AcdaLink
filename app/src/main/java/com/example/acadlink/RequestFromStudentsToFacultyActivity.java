package com.example.acadlink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Faculty-facing activity: shows requests from students (reads from requestArchives).
 *
 * Behavior:
 *  - Compact card layout matching your provided reference, but reduced size (padding/text/gaps).
 *  - Accept/Reject buttons moved to Summary (see Summary.java).
 *  - View -> opens Summary activity with the project details (title, similarity, aiGenerated, abstract, methodology, files...)
 */
public class RequestFromStudentsToFacultyActivity extends AppCompatActivity {

    private static final String PREFS = "FacultyPrefs";
    private static final String KEY_LOGGED_IN = "isFacultyLoggedIn";
    private static final String KEY_EMAIL = "facultyEmail";
    private static final String TAG = "ReqFacultyAct";

    private FirebaseAuth auth;
    private DatabaseReference archiveRef; // requestArchives

    private MaterialCardView requestCardTemplate;
    private LinearLayout requestsContainer;
    private ImageButton logoutBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_request_from_students_to_faculty);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        archiveRef = FirebaseDatabase.getInstance().getReference("requestArchives");

        logoutBtn = findViewById(R.id.LogoutBtn);
        requestCardTemplate = findViewById(R.id.requestCard);

        if (logoutBtn != null) {
            logoutBtn.setOnClickListener(v -> {
                try {
                    auth.signOut();
                    SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                    prefs.edit().clear().apply();
                    Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {}
                goToLogin();
            });
        }

        if (requestCardTemplate != null) {
            ViewGroup parent = (ViewGroup) requestCardTemplate.getParent();
            if (parent != null) {
                int idx = parent.indexOfChild(requestCardTemplate);
                parent.removeView(requestCardTemplate);
                requestsContainer = new LinearLayout(this);
                requestsContainer.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, dp(6), 0, dp(6)); // reduced gap 6dp
                requestsContainer.setLayoutParams(lp);
                parent.addView(requestsContainer, idx);
            }
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean facultyLoggedIn = prefs.getBoolean(KEY_LOGGED_IN, false);
        FirebaseUser currentUser = auth.getCurrentUser();

        if (!facultyLoggedIn && currentUser == null) {
            goToLogin();
            return;
        }

        loadAllArchivedRequests();
    }

    private void loadAllArchivedRequests() {
        if (requestsContainer == null) return;

        archiveRef.orderByChild("timestamp").addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                requestsContainer.removeAllViews();

                if (!snapshot.hasChildren()) {
                    requestsContainer.addView(makeInfoCard("No archived requests found"));
                    return;
                }

                for (DataSnapshot child : snapshot.getChildren()) {
                    requestsContainer.addView(makeRequestCardForFaculty(child));
                }
            }

            @Override public void onCancelled(DatabaseError error) {
                Toast.makeText(RequestFromStudentsToFacultyActivity.this,
                        "Failed to load requests: " + (error != null ? error.getMessage() : "unknown"),
                        Toast.LENGTH_LONG).show();
                Log.w(TAG, "loadAllArchivedRequests cancelled: " + (error != null ? error.getMessage() : "null"));
            }
        });
    }

    private View makeInfoCard(String message) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(12), dp(6), dp(12), dp(6));
        card.setLayoutParams(cardLp);
        card.setRadius(dp(10));
        card.setUseCompatPadding(true);
        card.setCardElevation(dp(3));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(14), dp(10), dp(14), dp(10));
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(0xFF333333);
        box.addView(tv);
        card.addView(box);
        return card;
    }

    private View makeRequestCardForFaculty(final DataSnapshot snapshot) {
        final String pid = snapshot.getKey();

        String name = getStringChild(snapshot, "requestFromName",
                getStringChild(snapshot, "userEmail", "Unknown User"));
        if (!TextUtils.isEmpty(name) && name.contains("@")) name = toPrettyName(name);

        String dept = getStringChild(snapshot, "department", getStringChild(snapshot, "dept", "N/A"));
        String title = getStringChild(snapshot, "projectTitle", getStringChild(snapshot, "title", "N/A"));
        String similarity = getStringChild(snapshot, "similarity", "N/A");
        String ai = getStringChild(snapshot, "aiGenerated", "N/A");

        // read current status (case-insensitive)
        String status = getStringChild(snapshot, "status", "Requested");

        // Capitalize first letter for status display
        String displayStatus = status.substring(0, 1).toUpperCase(Locale.ROOT) + status.substring(1).toLowerCase(Locale.ROOT);

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(8), dp(6), dp(8), dp(8));
        card.setLayoutParams(cardLp);
        card.setRadius(dp(10));
        card.setUseCompatPadding(true);
        card.setCardElevation(dp(3));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView tvFrom = new TextView(this);
        tvFrom.setText("Request from: " + (name != null ? name : "Unknown"));
        tvFrom.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvFrom.setTypeface(tvFrom.getTypeface(), android.graphics.Typeface.BOLD);
        tvFrom.setTextColor(0xFF000000);
        container.addView(tvFrom);

        TextView tvDept = new TextView(this);
        tvDept.setText("Department: " + (dept != null ? dept : "N/A"));
        tvDept.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvDept.setPadding(0, dp(6), 0, 0);
        tvDept.setTextColor(0xFF000000);
        container.addView(tvDept);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Project title: " + (title != null ? title : "N/A"));
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvTitle.setPadding(0, dp(6), 0, 0);
        tvTitle.setTextColor(0xFF000000);
        container.addView(tvTitle);

        // Similarity with green/red percentage logic
        TextView tvSim = new TextView(this);
        tvSim.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvSim.setPadding(0, dp(6), 0, 0);
        tvSim.setTextColor(0xFF000000);
        tvSim.setText(createLabeledSpannableWithColoredPercent("Similarity: ", similarity));
        container.addView(tvSim);

        // AI generated with same percent logic
        TextView tvAi = new TextView(this);
        tvAi.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvAi.setPadding(0, dp(6), 0, 0);
        tvAi.setTextColor(0xFF000000);
        tvAi.setText(createLabeledSpannableWithColoredPercent("AI generated: ", ai));
        container.addView(tvAi);

        TextView tvStatus = new TextView(this);
        tvStatus.setText("Status: " + displayStatus);
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvStatus.setPadding(0, dp(10), 0, 0);
        tvStatus.setTypeface(tvStatus.getTypeface(), android.graphics.Typeface.ITALIC);
        tvStatus.setTextColor(0xFF000000);
        container.addView(tvStatus);

        // Buttons row: kept only View button here (Accept/Reject moved to Summary)
        LinearLayout btnRow = new LinearLayout(this);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnRowLp.setMargins(0, dp(10), 0, 0);
        btnRow.setLayoutParams(btnRowLp);
        btnRow.setGravity(Gravity.END);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        MaterialButton btnView = new MaterialButton(this);
        btnView.setText("View");
        btnView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btnView.setBackgroundTintList(ColorStateList.valueOf(0xFF9E9E9E));
        btnView.setCornerRadius(dp(6));
        btnView.setLayoutParams(buttonLp);
        btnRow.addView(btnView);

        btnView.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(RequestFromStudentsToFacultyActivity.this, Summary.class);
                intent.putExtra("projectId", pid);
                intent.putExtra("projectTitle", title != null ? title : "N/A");
                intent.putExtra("similarity", similarity != null ? similarity : "N/A");
                intent.putExtra("aiGenerated", ai != null ? ai : "N/A");
                intent.putExtra("projectType1", getStringChild(snapshot, "projectType1", getStringChild(snapshot, "projectType", "N/A")));
                intent.putExtra("projectType2", getStringChild(snapshot, "projectType2", getStringChild(snapshot, "projectTypeLevel", getStringChild(snapshot, "projectLevel", "N/A"))));
                intent.putExtra("abstract", getStringChild(snapshot, "abstract", "N/A"));
                intent.putExtra("methodology", getStringChild(snapshot, "methodology", "N/A"));
                ArrayList<String> fileInfoList = new ArrayList<>();
                ArrayList<String> fileUriList = new ArrayList<>();
                try {
                    for (DataSnapshot f : snapshot.child("files").getChildren()) {
                        String fname = getStringChild(f, "name", "");
                        String fsize = getStringChild(f, "size", "");
                        String downloadUrl = getStringChild(f, "downloadUrl", "");
                        String url = getStringChild(f, "url", "");
                        String storagePath = getStringChild(f, "storagePath", "");
                        String chosenUri = !downloadUrl.isEmpty() ? downloadUrl : (!url.isEmpty() ? url : (!storagePath.isEmpty() ? storagePath : ""));
                        String info = fname.isEmpty() ? "unknown" : fname;
                        if (!fsize.isEmpty() && !"N/A".equalsIgnoreCase(fsize)) info += " (" + fsize + ")";
                        fileInfoList.add(info);
                        fileUriList.add(chosenUri);
                    }
                } catch (Exception ignored) {}
                if (!fileInfoList.isEmpty()) intent.putStringArrayListExtra("fileInfoList", fileInfoList);
                if (!fileUriList.isEmpty()) intent.putStringArrayListExtra("fileUriList", fileUriList);
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to open summary: " + e.getMessage());
                Toast.makeText(this, "Unable to open summary", Toast.LENGTH_SHORT).show();
            }
        });

        container.addView(btnRow);
        card.addView(container);
        return card;
    }

    // highlight percentage in red if >15, else green
    private CharSequence createLabeledSpannableWithColoredPercent(String label, String value) {
        if (value == null) value = "N/A";
        String display = label + value;
        SpannableString ss = new SpannableString(display);
        try {
            String trimmed = value.trim();
            int percentIndex = trimmed.indexOf('%');
            if (percentIndex != -1) {
                int startNum = percentIndex - 1;
                while (startNum >= 0 && (Character.isDigit(trimmed.charAt(startNum)) || trimmed.charAt(startNum) == '.')) startNum--;
                startNum++;
                String numStr = trimmed.substring(startNum, percentIndex);
                float val = Float.parseFloat(numStr);
                int color = (val > 15f) ? Color.RED : Color.parseColor("#388E3C");
                int offset = label.length();
                int colorStart = offset + startNum;
                int colorEnd = offset + percentIndex + 1;
                if (colorStart >= 0 && colorEnd <= ss.length() && colorStart < colorEnd) {
                    ss.setSpan(new ForegroundColorSpan(color), colorStart, colorEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        } catch (Exception ignored) {}
        return ss;
    }

    private String getStringChild(DataSnapshot parent, String key, String def) {
        try {
            Object v = parent.child(key).getValue();
            if (v == null) return def;
            String s = v.toString().trim();
            return s.isEmpty() ? def : s;
        } catch (Exception e) { return def; }
    }

    private void goToLogin() {
        Intent i = new Intent(this, LoginOptionsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private String toPrettyName(String email) {
        try {
            if (email == null) return "Unknown User";
            String local = email.substring(0, email.indexOf('@'));
            String[] parts = local.split("[^A-Za-z0-9]+");
            StringBuilder b = new StringBuilder();
            for (String p : parts) {
                if (p.isEmpty()) continue;
                if (Character.isLetter(p.charAt(0))) {
                    b.append(Character.toUpperCase(p.charAt(0)));
                    if (p.length() > 1) b.append(p.substring(1).toLowerCase(Locale.getDefault()));
                } else b.append(p);
                b.append(' ');
            }
            String out = b.toString().trim();
            return out.isEmpty() ? email : out;
        } catch (Exception e) { return email != null ? email : "Unknown User"; }
    }
}
