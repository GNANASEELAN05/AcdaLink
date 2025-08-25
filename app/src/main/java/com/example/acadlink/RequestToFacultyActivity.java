package com.example.acadlink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
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

import android.content.res.ColorStateList;
import android.graphics.Color;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Student-facing RequestToFacultyActivity.
 *
 * Behavior:
 *  - Reads from requestArchives and shows only requests belonging to the current user.
 *  - Card layout matches your provided reference but reduced in size (padding/text/gaps).
 *  - Upload visible only when archive.status == "Accepted" and not yet uploaded.
 *  - Upload action copies the archive entry to /projects/<id> and updates archive.status = "Uploaded".
 *  - Archive entry is NOT deleted (remains visible).
 */
public class RequestToFacultyActivity extends AppCompatActivity {

    private MaterialCardView requestCardTemplate;
    private ImageButton toolbarBackBtn;
    private LinearLayout requestsContainer;

    private DatabaseReference archiveRef;
    private DatabaseReference projectsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_request_to_faculty);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        archiveRef = FirebaseDatabase.getInstance().getReference("requestArchives");
        projectsRef = FirebaseDatabase.getInstance().getReference("projects");

        requestCardTemplate = findViewById(R.id.requestCard);
        toolbarBackBtn = findViewById(R.id.toolbarBackBtn);

        if (requestCardTemplate != null) {
            ViewGroup parent = (ViewGroup) requestCardTemplate.getParent();
            if (parent != null) {
                int idx = parent.indexOfChild(requestCardTemplate);
                parent.removeView(requestCardTemplate);
                requestsContainer = new LinearLayout(this);
                requestsContainer.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, dp(6), 0, dp(6)); // 6dp gap
                requestsContainer.setLayoutParams(lp);
                parent.addView(requestsContainer, idx);
            }
        }

        if (toolbarBackBtn != null) {
            toolbarBackBtn.setOnClickListener(v -> {
                Intent intent = new Intent(RequestToFacultyActivity.this, MainActivity.class);
                finish();
            });
        }

        loadStudentRequests();
    }

    private void loadStudentRequests() {
        if (requestsContainer == null) return;

        archiveRef.orderByChild("timestamp").addValueEventListener(new com.google.firebase.database.ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                requestsContainer.removeAllViews();

                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                String myUid = user != null ? user.getUid() : null;
                String myEmail = user != null ? user.getEmail() : null;
                SharedPreferences ui = getSharedPreferences("UserInfo", MODE_PRIVATE);
                if (myEmail == null) myEmail = ui.getString("email", null);

                boolean any = false;
                for (DataSnapshot child : snapshot.getChildren()) {
                    String uid = child.child("uid").getValue() != null ? child.child("uid").getValue().toString() : null;
                    String email = child.child("userEmail").getValue() != null ? child.child("userEmail").getValue().toString() : null;
                    String uploaderEmail = child.child("uploader").child("email").getValue() != null
                            ? child.child("uploader").child("email").getValue().toString() : null;

                    boolean mine = false;
                    if (myUid != null && myUid.equals(uid)) mine = true;
                    else if (myEmail != null && (myEmail.equalsIgnoreCase(email) || myEmail.equalsIgnoreCase(uploaderEmail))) mine = true;

                    if (mine) {
                        View card = makeStudentRequestCard(child);
                        requestsContainer.addView(card);
                        any = true;
                    }
                }

                if (!any) {
                    requestsContainer.addView(makeInfoCard("You have no requests."));
                }
            }

            @Override public void onCancelled(DatabaseError error) {
                Toast.makeText(RequestToFacultyActivity.this,
                        "Failed to load requests: " + (error != null ? error.getMessage() : "unknown"),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private View makeInfoCard(String message) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(12), dp(6), dp(12), dp(6));
        card.setLayoutParams(cardLp);
        card.setRadius(dp(12));
        card.setUseCompatPadding(true);
        card.setCardElevation(dp(3));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        box.addView(tv);
        card.addView(box);
        return card;
    }

    private View makeStudentRequestCard(final DataSnapshot snapshot) {
        final String pid = snapshot.getKey();
        String title = getStringChild(snapshot, "projectTitle", getStringChild(snapshot, "title", "Untitled"));
        String similarity = getStringChild(snapshot, "similarity", "N/A");
        String ai = getStringChild(snapshot, "aiGenerated", "N/A");
        String rawStatus = getStringChild(snapshot, "status", "Pending");

        // Always capitalize the first letter of status
        String status = capitalizeStatus(rawStatus);

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(12), dp(6), dp(12), dp(6));
        card.setLayoutParams(cardLp);
        card.setRadius(dp(12));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.addView(container);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Project title: " + title);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvTitle.setTypeface(tvTitle.getTypeface(), android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(0xFF000000);
        container.addView(tvTitle);

        TextView tvSim = new TextView(this);
        tvSim.setText("Similarity: " + similarity);
        tvSim.setPadding(0, dp(6), 0, 0);
        tvSim.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSim.setTextColor(0xFF000000);
        container.addView(tvSim);

        TextView tvAi = new TextView(this);
        tvAi.setText("AI generated: " + ai);
        tvAi.setPadding(0, dp(6), 0, 0);
        tvAi.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvAi.setTextColor(0xFF000000);
        container.addView(tvAi);

        TextView tvStatus = new TextView(this);
        tvStatus.setText("Status: " + status);
        tvStatus.setPadding(0, dp(8), 0, 0);
        tvStatus.setTypeface(tvStatus.getTypeface(), android.graphics.Typeface.ITALIC);
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvStatus.setTextColor(0xFF000000);
        container.addView(tvStatus);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnRowLp.setMargins(0, dp(12), 0, 0);
        btnRow.setLayoutParams(btnRowLp);

        MaterialButton btnUpload = new MaterialButton(this);
        btnUpload.setText("Upload");
        btnUpload.setCornerRadius(dp(10));
        btnUpload.setPadding(dp(16), dp(8), dp(16), dp(8));
        btnUpload.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnUpload.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#03A9F4")));
        btnUpload.setTextColor(Color.WHITE);

        MaterialButton btnRemove = new MaterialButton(this);
        btnRemove.setText("Remove");
        btnRemove.setCornerRadius(dp(10));
        LinearLayout.LayoutParams remLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        remLp.setMargins(dp(8), 0, 0, 0);
        btnRemove.setLayoutParams(remLp);
        btnRemove.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#9E9E9E")));
        btnRemove.setTextColor(Color.WHITE);

        boolean accepted = "Accepted".equalsIgnoreCase(status);
        boolean alreadyUploaded = "Uploaded".equalsIgnoreCase(status);
        btnUpload.setVisibility(accepted && !alreadyUploaded ? View.VISIBLE : View.GONE);

        btnRow.addView(btnUpload);
        btnRow.addView(btnRemove);
        container.addView(btnRow);

        btnRemove.setOnClickListener(v -> {
            if (requestsContainer != null) requestsContainer.removeView(card);
        });

        btnUpload.setOnClickListener(v -> {
            btnUpload.setEnabled(false);
            Toast.makeText(RequestToFacultyActivity.this, "Uploading project (moving archive → projects)...", Toast.LENGTH_SHORT).show();
            moveArchivedToProjects(pid, tvStatus, btnUpload);
        });

        return card;
    }

    private void moveArchivedToProjects(final String archiveId, TextView statusTv, MaterialButton uploadBtn) {
        if (archiveId == null) return;
        archiveRef.child(archiveId).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                Toast.makeText(RequestToFacultyActivity.this, "Request not found in archive.", Toast.LENGTH_LONG).show();
                if (uploadBtn != null) uploadBtn.setEnabled(true);
                return;
            }
            DataSnapshot snap = task.getResult();
            Object raw = snap.getValue();
            Map<String, Object> projectMap = new HashMap<>();

            try {
                if (raw instanceof Map) {
                    //noinspection unchecked
                    projectMap.putAll((Map<String, Object>) raw);
                }
            } catch (Exception ignored) {}

            projectMap.put("id", archiveId);
            projectMap.put("projectTitle", projectMap.getOrDefault("projectTitle", projectMap.getOrDefault("title", "N/A")));
            projectMap.put("title", projectMap.getOrDefault("title", projectMap.getOrDefault("projectTitle", "N/A")));
            projectMap.put("request", false);
            projectMap.put("status", "Uploaded");
            projectMap.put("uploadedAt", System.currentTimeMillis());
            projectMap.put("timestamp", System.currentTimeMillis());

            projectsRef.child(archiveId).setValue(projectMap).addOnCompleteListener(pTask -> {
                if (!pTask.isSuccessful()) {
                    String msg = pTask.getException() != null ? pTask.getException().getMessage() : "unknown";
                    Toast.makeText(RequestToFacultyActivity.this, "Failed to move project: " + msg, Toast.LENGTH_LONG).show();
                    if (uploadBtn != null) uploadBtn.setEnabled(true);
                    return;
                }
                // Mark archive as Uploaded but KEEP archive node
                archiveRef.child(archiveId).child("status").setValue("Uploaded").addOnCompleteListener(uTask -> {
                    if (uTask.isSuccessful()) {
                        Toast.makeText(RequestToFacultyActivity.this, "Project moved to Projects.", Toast.LENGTH_SHORT).show();
                        if (statusTv != null) statusTv.setText("Status: Uploaded");
                        if (uploadBtn != null) uploadBtn.setVisibility(View.GONE);
                        startActivity(new Intent(RequestToFacultyActivity.this, MyProjects.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        finish();
                    } else {
                        String msg = uTask.getException() != null ? uTask.getException().getMessage() : "unknown";
                        Toast.makeText(RequestToFacultyActivity.this, "Moved but failed to update archive status: " + msg, Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
    }

    // ---------- utility helpers ----------
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

    private String capitalizeStatus(String raw) {
        if (raw == null || raw.isEmpty()) return "Pending";
        String lower = raw.toLowerCase(Locale.getDefault());
        switch (lower) {
            case "accepted": return "Accepted";
            case "rejected": return "Rejected";
            case "uploaded": return "Uploaded";
            case "pending": return "Pending";
            default:
                return raw.substring(0, 1).toUpperCase(Locale.getDefault()) + raw.substring(1).toLowerCase(Locale.getDefault());
        }
    }

    private String tryGetNameFromUriString(String uriStr) {
        try {
            Uri u = Uri.parse(uriStr);
            try (android.database.Cursor cursor = getContentResolver().query(u, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) return cursor.getString(idx);
                }
            }
        } catch (Exception ignored) {}
        try {
            return new File(uriStr).getName();
        } catch (Exception ignored) {}
        return "unknown";
    }

    private String tryGetFormattedSizeFromUriString(String uriStr) {
        try {
            Uri u = Uri.parse(uriStr);
            long b = getFileSizeBytesFromUri(u);
            if (b > 0) {
                long kb = b / 1024;
                if (kb >= 1024) return String.format(Locale.getDefault(), "%.1f MB", kb / 1024f);
                else return kb + " KB";
            }
        } catch (Exception ignored) {}
        return null;
    }

    private long getFileSizeBytesFromUri(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx != -1) return cursor.getLong(idx);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
