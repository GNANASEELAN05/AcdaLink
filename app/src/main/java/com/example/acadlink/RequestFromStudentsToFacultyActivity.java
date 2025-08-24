package com.example.acadlink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Faculty-facing activity: shows requests from students (reads from requestArchives).
 *
 * Behavior:
 *  - Compact card layout matching your provided reference, but reduced size (padding/text/gaps).
 *  - Accept -> updates requestArchives/<id>/status = "accepted" and adds facultyApproval; disables both buttons.
 *  - Reject -> updates requestArchives/<id>/status = "rejected" and adds facultyApproval; disables both buttons.
 *  - Cards remain visible in the archive (history).
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

        // Replace the XML sample card with a vertical container (so we can render many)
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

        // Show entire archive (history). Cards remain visible regardless of status.
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

        // Status from archive
        String status = getStringChild(snapshot, "Status", "Requested");

        // Card using your reference style, but reduced padding/fonts for a compact look
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(8), dp(6), dp(8), dp(8)); // horizontal margin reduced, vertical ~6-8dp
        card.setLayoutParams(cardLp);
        card.setRadius(dp(10));
        card.setUseCompatPadding(true);
        card.setCardElevation(dp(3));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(12), dp(10), dp(12), dp(10)); // compact padding

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

        TextView tvSim = new TextView(this);
        tvSim.setText("Similarity: " + (similarity != null ? similarity : "N/A"));
        tvSim.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvSim.setPadding(0, dp(6), 0, 0);
        tvSim.setTextColor(0xFF000000);
        container.addView(tvSim);

        TextView tvAi = new TextView(this);
        tvAi.setText("AI generated: " + (ai != null ? ai : "N/A"));
        tvAi.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvAi.setPadding(0, dp(6), 0, 0);
        tvAi.setTextColor(0xFF000000);
        container.addView(tvAi);

        TextView tvStatus = new TextView(this);
        tvStatus.setText("Status: " + status);
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvStatus.setPadding(0, dp(10), 0, 0);
        tvStatus.setTypeface(tvStatus.getTypeface(), android.graphics.Typeface.ITALIC);
        tvStatus.setTextColor(0xFF000000);
        container.addView(tvStatus);

        LinearLayout btnRow = new LinearLayout(this);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnRowLp.setMargins(0, dp(10), 0, 0);
        btnRow.setLayoutParams(btnRowLp);
        btnRow.setGravity(Gravity.END);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        MaterialButton btnReject = new MaterialButton(this);
        btnReject.setText("Reject");
        btnReject.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnReject.setCornerRadius(dp(8));
        btnReject.setPadding(dp(13), dp(8), dp(13), dp(8));
        btnReject.setLayoutParams(buttonLp);
        btnReject.setMinWidth(dp(80));
        btnReject.setMaxLines(1);
        btnReject.setAllCaps(false);
        btnReject.setTextColor(Color.WHITE);
        btnReject.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F44336")));

        MaterialButton btnAccept = new MaterialButton(this);
        btnAccept.setText("Accept");
        btnAccept.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnAccept.setCornerRadius(dp(8));
        LinearLayout.LayoutParams acceptLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        acceptLp.setMargins(dp(8), 0, dp(8), 0);
        btnAccept.setLayoutParams(acceptLp);
        btnAccept.setPadding(dp(13), dp(8), dp(13), dp(8));
        btnAccept.setMinWidth(dp(80));
        btnAccept.setMaxLines(1);
        btnAccept.setAllCaps(false);
        btnAccept.setTextColor(Color.WHITE);
        btnAccept.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));

        MaterialButton btnRemove = new MaterialButton(this);
        btnRemove.setText("Hide");
        btnRemove.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnRemove.setCornerRadius(dp(8));
        btnRemove.setLayoutParams(buttonLp);
        btnRemove.setPadding(dp(13), dp(8), dp(13), dp(8));
        btnRemove.setMinWidth(dp(84));
        btnRemove.setMaxLines(1);
        btnRemove.setAllCaps(false);
        btnRemove.setTextColor(Color.WHITE);
        btnRemove.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#9E9E9E")));

        // disable accept/reject if already decided
        boolean decided = "accepted".equalsIgnoreCase(status) || "rejected".equalsIgnoreCase(status) || "uploaded".equalsIgnoreCase(status);
        btnAccept.setEnabled(!decided);
        btnReject.setEnabled(!decided);
        btnAccept.setAlpha(!decided ? 1f : 0.5f);
        btnReject.setAlpha(!decided ? 1f : 0.5f);

        btnRow.addView(btnReject);
        btnRow.addView(btnAccept);
        btnRow.addView(btnRemove);

        container.addView(btnRow);
        card.addView(container);

        // Remove/hide only the UI card
        btnRemove.setOnClickListener(v -> {
            if (requestsContainer != null) requestsContainer.removeView(card);
        });

        // Accept: update archive node only (status + facultyApproval)
        btnAccept.setOnClickListener(v -> {
            btnAccept.setEnabled(false);
            btnReject.setEnabled(false);
            btnAccept.setAlpha(0.5f);
            btnReject.setAlpha(0.5f);

            FirebaseUser user = auth.getCurrentUser();
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String finalStatus = "Accepted";

            Map<String, Object> facultyApproval = new HashMap<>();
            facultyApproval.put("status", finalStatus);
            facultyApproval.put("reviewedAt", System.currentTimeMillis());
            if (user != null) {
                facultyApproval.put("reviewedBy", user.getUid());
                facultyApproval.put("reviewedByEmail", user.getEmail() != null ? user.getEmail() : "");
            } else if (prefs.getBoolean(KEY_LOGGED_IN, false)) {
                facultyApproval.put("reviewedBy", "faculty_local");
                facultyApproval.put("reviewedByEmail", prefs.getString(KEY_EMAIL, ""));
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("status", finalStatus);
            updates.put("facultyApproval", facultyApproval);
            updates.put("acceptedAt", System.currentTimeMillis());

            archiveRef.child(pid).updateChildren(updates).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(RequestFromStudentsToFacultyActivity.this, "Accepted", Toast.LENGTH_SHORT).show();
                    tvStatus.setText("Status: " + finalStatus);
                    btnAccept.setEnabled(false);
                    btnReject.setEnabled(false);
                } else {
                    String msg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                    Toast.makeText(RequestFromStudentsToFacultyActivity.this, "Failed to accept: " + msg, Toast.LENGTH_LONG).show();
                    Log.w(TAG, "accept failed: " + msg);
                    // revert UI
                    btnAccept.setEnabled(true);
                    btnReject.setEnabled(true);
                    btnAccept.setAlpha(1f);
                    btnReject.setAlpha(1f);
                }
            });
        });

        // Reject: update archive node only (status + facultyApproval) — do NOT delete
        btnReject.setOnClickListener(v -> {
            btnAccept.setEnabled(false);
            btnReject.setEnabled(false);
            btnAccept.setAlpha(0.5f);
            btnReject.setAlpha(0.5f);

            FirebaseUser user = auth.getCurrentUser();
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String finalStatus = "Rejected";

            Map<String, Object> facultyApproval = new HashMap<>();
            facultyApproval.put("status", finalStatus);
            facultyApproval.put("reviewedAt", System.currentTimeMillis());
            if (user != null) {
                facultyApproval.put("reviewedBy", user.getUid());
                facultyApproval.put("reviewedByEmail", user.getEmail() != null ? user.getEmail() : "");
            } else if (prefs.getBoolean(KEY_LOGGED_IN, false)) {
                facultyApproval.put("reviewedBy", "faculty_local");
                facultyApproval.put("reviewedByEmail", prefs.getString(KEY_EMAIL, ""));
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("status", finalStatus);
            updates.put("facultyApproval", facultyApproval);
            updates.put("rejectedAt", System.currentTimeMillis());

            archiveRef.child(pid).updateChildren(updates).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(RequestFromStudentsToFacultyActivity.this, "Rejected (archived)", Toast.LENGTH_SHORT).show();
                    tvStatus.setText("Status: " + finalStatus);
                    btnAccept.setEnabled(false);
                    btnReject.setEnabled(false);
                } else {
                    String msg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                    Toast.makeText(RequestFromStudentsToFacultyActivity.this, "Failed to reject: " + msg, Toast.LENGTH_LONG).show();
                    Log.w(TAG, "reject failed: " + msg);
                    // revert UI
                    btnAccept.setEnabled(true);
                    btnReject.setEnabled(true);
                    btnAccept.setAlpha(1f);
                    btnReject.setAlpha(1f);
                }
            });
        });

        return card;
    }

    // Helpers
    private String getStringChild(DataSnapshot parent, String key, String def) {
        try {
            Object v = parent.child(key).getValue();
            if (v == null) return def;
            String s = v.toString().trim();
            return s.isEmpty() ? def : s;
        } catch (Exception e) {
            return def;
        }
    }

    private void goToLogin() {
        Intent i = new Intent(RequestFromStudentsToFacultyActivity.this, LoginOptionsActivity.class);
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
                } else {
                    b.append(p);
                }
                b.append(' ');
            }
            String out = b.toString().trim();
            return out.isEmpty() ? email : out;
        } catch (Exception e) {
            return email != null ? email : "Unknown User";
        }
    }
}
