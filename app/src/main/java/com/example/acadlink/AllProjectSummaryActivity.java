package com.example.acadlink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AllProjectSummaryActivity
 * - robust WhatsApp detection + icon loading (regular/business/beta/any package containing "whatsapp")
 * - chooser spelling fixed to "WhatsApp"
 * - prevents calling/messaging yourself (per-user) on this screen
 * - preserves all IDs/behaviour
 */
public class AllProjectSummaryActivity extends AppCompatActivity {

    private static final String TAG = "AllProjectSummary";

    private TextView uploadedByTv, departmentTv, phoneTv, emailTv;
    private TextView titleTv, typeTv, levelTv, abstractTv, methodologyTv, similarityTv, aiGeneratedTv, filesTv;

    // Intent / runtime fields (fallback)
    private String projectId;
    private String intentTitle, intentProjectType1, intentProjectLevel, intentAbstract, intentMethodology, intentSimilarity, intentAi;
    private List<String> intentFileInfoList;

    // --- self-contact prevention context ---
    private String uploaderUidResolved = null;
    private String uploaderEmailResolved = null;
    private String uploaderPhoneResolvedDigits = null;

    private String currentUid = null;
    private String currentEmail = null;
    private String currentPhoneDigits = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_all_project_summary);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageButton toolbarBackBtn = findViewById(R.id.toolbarBackBtn);
        toolbarBackBtn.setOnClickListener(v -> finish());

        titleTv = findViewById(R.id.summaryTitle);
        typeTv = findViewById(R.id.summaryType);
        levelTv = findViewById(R.id.summaryLevel);
        abstractTv = findViewById(R.id.summaryAbstract);
        methodologyTv = findViewById(R.id.summaryMethodology);
        similarityTv = findViewById(R.id.summarySimilarity);
        aiGeneratedTv = findViewById(R.id.summaryAiGenerated);
        filesTv = findViewById(R.id.summaryFiles);

        // uploader fields
        uploadedByTv = findViewById(R.id.summaryUploadedByName);
        departmentTv = findViewById(R.id.summaryDepartment);
        phoneTv = findViewById(R.id.summaryPhone);
        emailTv = findViewById(R.id.summaryEmail);

        // Capture current user (for self-check)
        if (FirebaseAuth.getInstance() != null && FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            currentEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            loadCurrentUserProfilePhone(); // sets currentPhoneDigits (best-effort)
        }

        // Bottom buttons
        ImageButton btnCall = findViewById(R.id.btn_call);
        ImageButton btnSms = findViewById(R.id.btn_sms);

        btnCall.setOnClickListener(v -> {
            String phone = phoneTv.getText().toString();
            if (phone == null || phone.trim().isEmpty() || phone.equalsIgnoreCase("N/A")) {
                Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isSelfAttempt(phone)) {
                Toast.makeText(this, "You cannot call yourself", Toast.LENGTH_SHORT).show();
                return;
            }
            String telUri = "tel:" + extractForTel(phone);
            Intent dialIntent = new Intent(Intent.ACTION_DIAL);
            dialIntent.setData(Uri.parse(telUri));
            startActivity(dialIntent);
        });

        btnSms.setOnClickListener(v -> {
            String phone = phoneTv.getText().toString();
            String name = uploadedByTv.getText().toString();
            if (phone == null || phone.trim().isEmpty() || phone.equalsIgnoreCase("N/A")) {
                Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isSelfAttempt(phone)) {
                Toast.makeText(this, "You cannot message yourself", Toast.LENGTH_SHORT).show();
                return;
            }
            showMessagingChooser(phone, name);
        });

        // read raw intent extras (fallback)
        Intent it = getIntent();
        projectId = safeTrim(it.getStringExtra("projectId"));

        intentTitle = safeTrim(it.getStringExtra("title"));
        intentProjectType1 = safeTrim(it.getStringExtra("projectType1"));
        intentProjectLevel = safeTrim(it.getStringExtra("projectLevel"));
        intentAbstract = safeTrim(it.getStringExtra("abstract"));
        intentMethodology = safeTrim(it.getStringExtra("methodology"));
        intentSimilarity = safeTrim(it.getStringExtra("similarity"));
        intentAi = safeTrim(it.getStringExtra("aiGenerated"));
        intentFileInfoList = it.getStringArrayListExtra("fileInfoList");

        if (projectId != null && !projectId.isEmpty()) {
            fetchFromFirebase(projectId);
        } else {
            // fallback to intent values
            populateUI(
                    firstNonEmpty(intentTitle, "N/A"),
                    firstNonEmpty(intentProjectType1, "N/A"),
                    firstNonEmpty(intentProjectLevel, "N/A"),
                    firstNonEmpty(intentAbstract, "N/A"),
                    firstNonEmpty(intentMethodology, "N/A"),
                    firstNonEmpty(intentSimilarity, "N/A"),
                    firstNonEmpty(intentAi, "N/A"),
                    null
            );
            uploadedByTv.setText(safe(it.getStringExtra("uploadedBy")));
            departmentTv.setText(safe(it.getStringExtra("uploaderDepartment")));
            phoneTv.setText(safe(it.getStringExtra("uploaderPhone")));
            emailTv.setText(safe(it.getStringExtra("uploaderEmail")));

            // also store for self-check best-effort
            uploaderEmailResolved = safeTrim(it.getStringExtra("uploaderEmail"));
            uploaderPhoneResolvedDigits = extractDigits(safe(it.getStringExtra("uploaderPhone")));
        }
    }

    /** load logged-in user's phone from Realtime DB: Users/<uid> */
    private void loadCurrentUserProfilePhone() {
        try {
            if (currentUid == null) return;
            DatabaseReference meRef = FirebaseDatabase.getInstance()
                    .getReference("Users")
                    .child(currentUid);
            meRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot snapshot) {
                    try {
                        String code = String.valueOf(snapshot.child("phoneCode").getValue());
                        String num  = String.valueOf(snapshot.child("phoneNumber").getValue());
                        String merged = ((code != null && !code.trim().isEmpty()) ? code + " " : "") + (num == null ? "" : num);
                        currentPhoneDigits = extractDigits(merged);
                    } catch (Exception ignored) {}
                }
                @Override public void onCancelled(DatabaseError error) { /* ignore */ }
            });
        } catch (Exception ignored) {}
    }

    // --------------- Firebase fetch & resolution ---------------
    private void fetchFromFirebase(String id) {
        DatabaseReference projRef = FirebaseDatabase.getInstance().getReference("projects").child(id);
        projRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                try {
                    if (snapshot != null && snapshot.exists()) {
                        String title = findStringInSnapshot(snapshot, "projectTitle", "title", "name");
                        String type1 = findStringInSnapshot(snapshot, "projectType1", "projectType", "type");
                        String level = findStringInSnapshot(snapshot, "projectType2", "projectLevel", "level");
                        String abs = findStringInSnapshot(snapshot, "abstract", "Abstract", "abstractText", "projectAbstract");
                        String meth = findStringInSnapshot(snapshot, "methodology", "method", "methods");
                        String sim = findStringInSnapshot(snapshot, "similarity", "similarityPercent");
                        String ai = findStringInSnapshot(snapshot, "aiGenerated", "ai", "aiDetected");

                        List<Map<String, Object>> filesList = null;
                        if (snapshot.hasChild("files")) {
                            Object fobj = snapshot.child("files").getValue();
                            if (fobj instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> fl = (List<Map<String, Object>>) fobj;
                                filesList = fl;
                            } else if (fobj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> m = (Map<String, Object>) fobj;
                                List<Map<String, Object>> fl = new ArrayList<>();
                                for (Object v : m.values()) {
                                    if (v instanceof Map) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> mm = (Map<String, Object>) v;
                                        fl.add(mm);
                                    }
                                }
                                if (!fl.isEmpty()) filesList = fl;
                            }
                        } else {
                            Object topVal = snapshot.getValue();
                            if (topVal instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> m = (Map<String, Object>) topVal;
                                Object candidate = findObjectInMapByKeyNames(m, new String[]{"files", "file", "attachments"});
                                if (candidate instanceof List) {
                                    @SuppressWarnings("unchecked")
                                    List<Map<String, Object>> fl = (List<Map<String, Object>>) candidate;
                                    filesList = fl;
                                }
                            }
                        }

                        String finalTitle = firstNonEmpty(title, intentTitle, "N/A");
                        String finalType = firstNonEmpty(type1, intentProjectType1, "N/A");
                        String finalLevel = firstNonEmpty(level, intentProjectLevel, "N/A");
                        String finalAbstract = firstNonEmpty(abs, intentAbstract, "N/A");
                        String finalMethod = firstNonEmpty(meth, intentMethodology, "N/A");
                        String finalSim = firstNonEmpty(sim, intentSimilarity, "N/A");
                        String finalAi = firstNonEmpty(ai, intentAi, "N/A");

                        populateUI(finalTitle, finalType, finalLevel, finalAbstract, finalMethod, finalSim, finalAi, filesList);

                        // Now resolve uploader info (best-effort) + store for self-check.
                        resolveAndPopulateUploader(snapshot);
                        return;
                    }

                    // no snapshot -> fallback to intent
                    populateUI(
                            firstNonEmpty(intentTitle, "N/A"),
                            firstNonEmpty(intentProjectType1, "N/A"),
                            firstNonEmpty(intentProjectLevel, "N/A"),
                            firstNonEmpty(intentAbstract, "N/A"),
                            firstNonEmpty(intentMethodology, "N/A"),
                            firstNonEmpty(intentSimilarity, "N/A"),
                            firstNonEmpty(intentAi, "N/A"),
                            null
                    );

                } catch (Exception e) {
                    Log.e(TAG, "Error reading Firebase snapshot: " + e.getMessage());
                    // fallback
                    populateUI(
                            firstNonEmpty(intentTitle, "N/A"),
                            firstNonEmpty(intentProjectType1, "N/A"),
                            firstNonEmpty(intentProjectLevel, "N/A"),
                            firstNonEmpty(intentAbstract, "N/A"),
                            firstNonEmpty(intentMethodology, "N/A"),
                            firstNonEmpty(intentSimilarity, "N/A"),
                            firstNonEmpty(intentAi, "N/A"),
                            null
                    );
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Firebase read cancelled: " + error.getMessage());
                populateUI(
                        firstNonEmpty(intentTitle, "N/A"),
                        firstNonEmpty(intentProjectType1, "N/A"),
                        firstNonEmpty(intentProjectLevel, "N/A"),
                        firstNonEmpty(intentAbstract, "N/A"),
                        firstNonEmpty(intentMethodology, "N/A"),
                        firstNonEmpty(intentSimilarity, "N/A"),
                        firstNonEmpty(intentAi, "N/A"),
                        null
                );
            }
        });
    }

    private void resolveAndPopulateUploader(DataSnapshot projectSnap) {
        try {
            String[] candidateKeys = new String[]{
                    "uploaderId", "uid", "userId", "uploadedBy", "ownerId", "authorUid", "author", "uploader", "createdBy", "uploadedByUid"
            };

            String uploaderCandidate = findStringInSnapshot(projectSnap, candidateKeys);

            if (uploaderCandidate != null && !uploaderCandidate.trim().isEmpty()) {
                if (uploaderCandidate.contains("@")) {
                    // uploader stored as email in project
                    String name = findStringInSnapshot(projectSnap, "uploadedByName", "uploaderName", "uploadedBy", "author", "name");
                    uploadedByTv.setText(notEmptyOrDefault(name, uploaderCandidate));
                    emailTv.setText(uploaderCandidate);
                    String phone = findStringInSnapshot(projectSnap, "phone", "phoneNumber", "uploaderPhone");
                    phoneTv.setText(safe(phone));
                    String dept = findStringInSnapshot(projectSnap, "department", "uploaderDepartment");
                    departmentTv.setText(safe(dept));

                    uploaderUidResolved = null;
                    uploaderEmailResolved = uploaderCandidate;
                    uploaderPhoneResolvedDigits = extractDigits(phone);
                    return;
                }

                // uploader stored as UID — fetch profile
                DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(uploaderCandidate);
                userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot userSnap) {
                        if (userSnap != null && userSnap.exists()) {
                            String uname = String.valueOf(userSnap.child("name").getValue());
                            String uemail = String.valueOf(userSnap.child("email").getValue());
                            String phoneCode = String.valueOf(userSnap.child("phoneCode").getValue());
                            String phoneNumber = String.valueOf(userSnap.child("phoneNumber").getValue());
                            String dept = String.valueOf(userSnap.child("department").getValue());

                            String phoneCombined = (phoneCode != null && !phoneCode.trim().isEmpty()) ?
                                    (phoneCode + " " + phoneNumber).trim() : safe(phoneNumber);

                            uploadedByTv.setText(safe(uname));
                            emailTv.setText(safe(uemail));
                            phoneTv.setText(safe(phoneCombined));
                            departmentTv.setText(safe(dept));

                            // store for self-check
                            uploaderUidResolved = uploaderCandidate;
                            uploaderEmailResolved = uemail;
                            uploaderPhoneResolvedDigits = extractDigits(phoneCombined);
                        } else {
                            String name = findStringInSnapshot(projectSnap, "uploadedByName", "uploaderName", "uploadedBy", "author", "name");
                            String email = findStringInSnapshot(projectSnap, "uploaderEmail", "uploadedByEmail", "email", "userEmail");
                            String phone = findStringInSnapshot(projectSnap, "phone", "phoneNumber", "uploaderPhone");
                            String dept = findStringInSnapshot(projectSnap, "department", "uploaderDepartment");
                            uploadedByTv.setText(safe(name));
                            emailTv.setText(safe(email));
                            phoneTv.setText(safe(phone));
                            departmentTv.setText(safe(dept));

                            uploaderUidResolved = uploaderCandidate;
                            uploaderEmailResolved = email;
                            uploaderPhoneResolvedDigits = extractDigits(phone);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.w(TAG, "Failed to load uploader profile: " + error.getMessage());
                        String name = findStringInSnapshot(projectSnap, "uploadedByName", "uploaderName", "uploadedBy", "author", "name");
                        String email = findStringInSnapshot(projectSnap, "uploaderEmail", "uploadedByEmail", "email", "userEmail");
                        String phone = findStringInSnapshot(projectSnap, "phone", "phoneNumber", "uploaderPhone");
                        String dept = findStringInSnapshot(projectSnap, "department", "uploaderDepartment");
                        uploadedByTv.setText(safe(name));
                        emailTv.setText(safe(email));
                        phoneTv.setText(safe(phone));
                        departmentTv.setText(safe(dept));

                        uploaderUidResolved = uploaderCandidate;
                        uploaderEmailResolved = email;
                        uploaderPhoneResolvedDigits = extractDigits(phone);
                    }
                });
                return;
            }

            // No uploader field; pull best we can & store
            String name = findStringInSnapshot(projectSnap, "uploadedByName", "uploaderName", "uploadedBy", "author", "name");
            String email = findStringInSnapshot(projectSnap, "uploaderEmail", "uploadedByEmail", "email", "userEmail");
            String phone = findStringInSnapshot(projectSnap, "phone", "phoneNumber", "uploaderPhone");
            String dept = findStringInSnapshot(projectSnap, "department", "uploaderDepartment");

            uploadedByTv.setText(safe(name));
            emailTv.setText(safe(email));
            phoneTv.setText(safe(phone));
            departmentTv.setText(safe(dept));

            uploaderUidResolved = null;
            uploaderEmailResolved = email;
            uploaderPhoneResolvedDigits = extractDigits(phone);

        } catch (Exception e) {
            Log.w(TAG, "resolveAndPopulateUploader error: " + e.getMessage());
            uploadedByTv.setText("N/A");
            departmentTv.setText("N/A");
            phoneTv.setText("N/A");
            emailTv.setText("N/A");

            uploaderUidResolved = null;
            uploaderEmailResolved = null;
            uploaderPhoneResolvedDigits = null;
        }
    }

    // --------------- Populate UI ---------------
    private void populateUI(String title, String type, String level, String abs, String meth, String sim, String ai, List<Map<String, Object>> filesList) {
        runOnUiThread(() -> {
            titleTv.setText(notEmptyOrDefault(title, "N/A"));
            typeTv.setText(notEmptyOrDefault(type, "N/A"));
            levelTv.setText(notEmptyOrDefault(level, "N/A"));
            abstractTv.setText(notEmptyOrDefault(abs, "N/A"));
            methodologyTv.setText(notEmptyOrDefault(meth, "N/A"));
            similarityTv.setText(notEmptyOrDefault(sim, "N/A"));
            aiGeneratedTv.setText(notEmptyOrDefault(ai, "N/A"));

            if (filesList != null && !filesList.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                int c = 1;
                for (Map<String, Object> f : filesList) {
                    String name = String.valueOf(f.getOrDefault("name", "unknown"));
                    String size = String.valueOf(f.getOrDefault("size", "N/A"));

                    if (name.toLowerCase(Locale.ROOT).startsWith("primary folder")) {
                        sb.append(name).append("\n");
                    } else {
                        sb.append(c++).append(". ").append(name).append(" (").append(size).append(")\n");
                    }
                }
                filesTv.setText(sb.toString().trim());
            } else if (intentFileInfoList != null && !intentFileInfoList.isEmpty()) {
                filesTv.setText(formatFileInfoList(intentFileInfoList));
            } else {
                filesTv.setText("No files");
            }
        });
    }

    // --------------- Messaging chooser & improved WhatsApp handling ---------------

    /** Safely load an icon for the provided package name. */
    private Drawable safeLoadIcon(PackageManager pm, String pkg) {
        if (pm == null || pkg == null) return null;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            if (ai != null) return ai.loadIcon(pm);
        } catch (Exception ignored) { }
        try {
            return pm.getApplicationIcon(pkg);
        } catch (Exception ignored) { }
        return null;
    }

    /**
     * Find installed WhatsApp package. Checks preferred names first then scans installed packages,
     * then tries to resolve wa.me handlers. Returns package name or null.
     */
    private String findInstalledWhatsAppPackage(PackageManager pm) {
        if (pm == null) return null;

        String[] preferred = new String[] {
                "com.whatsapp",
                "com.whatsapp.w4b",   // WhatsApp Business
                "com.whatsapp.beta",
                "com.whatsapp.android"
        };

        for (String p : preferred) {
            try {
                PackageInfo pi = pm.getPackageInfo(p, 0);
                if (pi != null) return p;
            } catch (Exception ignored) { }
        }

        // scan installed packages for any package name containing "whatsapp"
        try {
            List<PackageInfo> installed = pm.getInstalledPackages(0);
            if (installed != null) {
                for (PackageInfo pi : installed) {
                    if (pi != null && pi.packageName != null &&
                            pi.packageName.toLowerCase(Locale.ROOT).contains("whatsapp")) {
                        return pi.packageName;
                    }
                }
            }
        } catch (Exception ignored) { }

        // resolve wa.me handlers
        try {
            Intent test = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/1234567890"));
            List<ResolveInfo> handlers = pm.queryIntentActivities(test, PackageManager.MATCH_DEFAULT_ONLY);
            if (handlers != null && !handlers.isEmpty()) {
                for (ResolveInfo ri : handlers) {
                    if (ri != null && ri.activityInfo != null && ri.activityInfo.packageName != null) {
                        String pkg = ri.activityInfo.packageName.toLowerCase(Locale.ROOT);
                        if (pkg.contains("whatsapp")) return ri.activityInfo.packageName;
                    }
                }
            }
        } catch (Exception ignored) { }

        return null;
    }

    private void showMessagingChooser(String phone, String uploaderName) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Choose messaging app");

            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.HORIZONTAL);
            int pad = (int) (getResources().getDisplayMetrics().density * 16);
            container.setPadding(pad, pad, pad, pad);
            container.setGravity(Gravity.CENTER);

            PackageManager pm = getPackageManager();

            // SMS icon & package — guarded
            Drawable smsIcon = null;
            String smsPackage = null;
            try {
                try {
                    smsPackage = Telephony.Sms.getDefaultSmsPackage(this);
                } catch (Exception ignored) {
                    smsPackage = null;
                }

                if (smsPackage == null) {
                    Intent smsProbe = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:1234567890"));
                    List<ResolveInfo> smsHandlers = pm.queryIntentActivities(smsProbe, 0);
                    if (smsHandlers != null && !smsHandlers.isEmpty()) {
                        ResolveInfo ri = smsHandlers.get(0);
                        if (ri != null && ri.activityInfo != null) smsPackage = ri.activityInfo.packageName;
                    }
                }

                if (smsPackage != null) smsIcon = safeLoadIcon(pm, smsPackage);
            } catch (Exception ignored) { smsPackage = null; smsIcon = null; }

            if (smsIcon == null) smsIcon = ContextCompat.getDrawable(this, android.R.drawable.sym_action_chat);

            // WhatsApp detection & icon
            String waPackage = findInstalledWhatsAppPackage(pm);
            Drawable waIcon = null;
            boolean waInstalled = waPackage != null;
            if (waInstalled) {
                waIcon = safeLoadIcon(pm, waPackage);
                if (waIcon == null) waInstalled = false;
            }
            if (waIcon == null) waIcon = ContextCompat.getDrawable(this, android.R.drawable.sym_action_chat);

            // Make final copies for lambdas
            final Drawable finalSmsIcon = smsIcon;
            final String finalSmsPackage = smsPackage;
            final Drawable finalWaIcon = waIcon;
            final boolean finalWaInstalled = waInstalled;
            final String finalWaPackage = waPackage;

            // SMS layout
            LinearLayout smsLayout = new LinearLayout(this);
            smsLayout.setOrientation(LinearLayout.VERTICAL);
            smsLayout.setGravity(Gravity.CENTER);
            ImageView smsIv = new ImageView(this);
            int size = (int) (getResources().getDisplayMetrics().density * 56);
            LinearLayout.LayoutParams ivParams = new LinearLayout.LayoutParams(size, size);
            smsIv.setLayoutParams(ivParams);
            smsIv.setImageDrawable(finalSmsIcon);
            smsLayout.addView(smsIv);
            TextView smsTv = new TextView(this);
            smsTv.setText("SMS");
            smsTv.setGravity(Gravity.CENTER);
            smsLayout.addView(smsTv);

            smsLayout.setOnClickListener(v -> {
                saveChatRecord(uploaderName, phone, "sms");
                try {
                    Intent intent = new Intent(Intent.ACTION_SENDTO);
                    intent.setData(Uri.parse("smsto:" + Uri.encode(phone)));
                    if (finalSmsPackage != null) intent.setPackage(finalSmsPackage);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "No SMS app found", Toast.LENGTH_SHORT).show();
                }
            });

            // WhatsApp layout
            LinearLayout waLayout = new LinearLayout(this);
            waLayout.setOrientation(LinearLayout.VERTICAL);
            waLayout.setGravity(Gravity.CENTER);
            ImageView waIv = new ImageView(this);
            waIv.setLayoutParams(ivParams);
            waIv.setImageDrawable(finalWaIcon);
            waLayout.addView(waIv);
            TextView waTv = new TextView(this);
            waTv.setText("WhatsApp"); // exact label
            waTv.setGravity(Gravity.CENTER);
            waLayout.addView(waTv);

            waLayout.setOnClickListener(v -> {
                if (!finalWaInstalled) {
                    Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
                    return;
                }
                saveChatRecord(uploaderName, phone, "whatsapp");

                String digits = extractDigits(phone);
                if (digits.length() == 0) {
                    Toast.makeText(this, "Invalid phone number for WhatsApp", Toast.LENGTH_SHORT).show();
                    return;
                }
                String waUrl = "https://wa.me/" + digits;
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(waUrl));
                    if (finalWaPackage != null) intent.setPackage(finalWaPackage);
                    startActivity(intent);
                } catch (Exception e) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(waUrl));
                        startActivity(intent);
                    } catch (Exception ex) {
                        Toast.makeText(this, "Unable to open WhatsApp", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            // Add spacing between icons
            LinearLayout.LayoutParams childLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            childLp.setMargins(pad / 2, 0, pad / 2, 0);

            container.addView(smsLayout, childLp);
            container.addView(waLayout, childLp);

            builder.setView(container);
            builder.setNegativeButton("Cancel", (d, w) -> d.dismiss());
            AlertDialog dialog = builder.create();
            dialog.show();

        } catch (Exception e) {
            Log.w(TAG, "showMessagingChooser error: " + e.getMessage());
            Toast.makeText(this, "Could not open messaging options", Toast.LENGTH_SHORT).show();
        }
    }

    /** Save a chat record (for Chats tab). */
    private void saveChatRecord(String name, String number, String via) {
        try {
            String uid = "guest";
            if (FirebaseAuth.getInstance() != null && FirebaseAuth.getInstance().getCurrentUser() != null) {
                uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            }

            SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            String key = "chats_" + uid;
            String raw = prefs.getString(key, "[]");
            JSONArray old = new JSONArray(raw);

            JSONArray updated = new JSONArray();
            JSONObject newObj = new JSONObject();
            newObj.put("name", name == null ? "" : name);
            newObj.put("number", number == null ? "" : number);
            newObj.put("via", via == null ? "" : via);
            newObj.put("ts", System.currentTimeMillis());
            updated.put(newObj);

            // copy old, skipping duplicate number+via
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o == null) continue;
                String onum = o.optString("number", "");
                String ov = o.optString("via", "");
                if (onum.equals(number) && ov.equals(via)) continue;
                updated.put(o);
            }

            prefs.edit().putString(key, updated.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "saveChatRecord error: " + e.getMessage());
        }
    }

    // -------- Self-contact prevention helper --------
    private boolean isSelfAttempt(String phoneText) {
        String digits = extractDigits(phoneText);

        // 1) If uploader UID matches current UID → self
        if (currentUid != null && uploaderUidResolved != null && currentUid.equals(uploaderUidResolved)) {
            return true;
        }
        // 2) If emails match → self
        if (currentEmail != null && uploaderEmailResolved != null
                && currentEmail.trim().equalsIgnoreCase(uploaderEmailResolved.trim())) {
            return true;
        }
        // 3) If phone digits match → self
        if (currentPhoneDigits != null && digits != null && digits.length() > 0
                && digits.equals(currentPhoneDigits)) {
            return true;
        }
        // 4) Also compare against whatever digits we could resolve for uploader
        if (uploaderPhoneResolvedDigits != null && digits != null && digits.length() > 0
                && digits.equals(uploaderPhoneResolvedDigits)) {
            // if uploader digits == displayed digits, and we already checked current user above,
            // this doesn't change result, but keeps logic tidy
        }
        return false;
    }

    // --------------- Utilities ---------------
    private static String notNull(String s) { return s == null ? "" : s; }

    private static String safeTrim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String firstNonEmpty(String... vals) {
        if (vals == null) return null;
        for (String s : vals) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }

    private String notEmptyOrDefault(String val, String def) {
        return (val != null && !val.trim().isEmpty()) ? val : def;
    }

    private String safe(String s) {
        return s == null || s.trim().isEmpty() ? "N/A" : s;
    }

    private String formatFileInfoList(List<String> fileInfoList) {
        if (fileInfoList == null || fileInfoList.isEmpty()) return "No files";
        StringBuilder sb = new StringBuilder();
        int c = 1;
        for (String fi : fileInfoList) {
            if (fi == null) continue;
            if (fi.contains("||")) {
                String[] parts = fi.split("\\|\\|");
                String name = parts.length > 0 ? parts[0] : fi;
                String size = parts.length > 1 ? parts[1] : "N/A";
                if (name.toLowerCase(Locale.ROOT).startsWith("primary folder")) {
                    sb.append(name).append("\n");
                } else {
                    sb.append(c++).append(". ").append(name).append(" (").append(size).append(")\n");
                }
            } else {
                sb.append(c++).append(". ").append(fi).append("\n");
            }
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static Object findObjectInMapByKeyNames(Map<String, Object> map, String[] keys) {
        if (map == null || keys == null) return null;
        for (String k : keys) {
            if (k == null) continue;
            if (map.containsKey(k) && map.get(k) != null) return map.get(k);
        }
        for (String key : keys) {
            String nk = normalizeKey(key);
            for (String mkey : map.keySet()) {
                if (normalizeKey(mkey).equals(nk)) return map.get(mkey);
            }
        }
        return null;
    }

    private String findStringInSnapshot(DataSnapshot snapshot, String... desiredKeys) {
        try {
            for (String k : desiredKeys) {
                if (k == null) continue;
                if (snapshot.hasChild(k)) {
                    Object v = snapshot.child(k).getValue();
                    if (v != null) return String.valueOf(v).trim();
                }
            }
            Object top = snapshot.getValue();
            if (top instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) top;
                for (String k : desiredKeys) {
                    Object v = findObjectInMapByKeyNames(map, new String[]{k});
                    if (v != null) return String.valueOf(v).trim();
                }
                Deque<Object> queue = new ArrayDeque<>();
                queue.add(map);
                while (!queue.isEmpty()) {
                    Object cur = queue.poll();
                    if (cur instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mcur = (Map<String, Object>) cur;
                        for (Map.Entry<String, Object> e : mcur.entrySet()) {
                            String candidateKey = e.getKey();
                            Object value = e.getValue();
                            for (String desired : desiredKeys) {
                                if (normalizeKey(candidateKey).equals(normalizeKey(desired)) && value != null) {
                                    return String.valueOf(value).trim();
                                }
                            }
                            if (value instanceof Map || value instanceof List) {
                                queue.add(value);
                            }
                        }
                    } else if (cur instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> lst = (List<Object>) cur;
                        for (Object o : lst) if (o instanceof Map || o instanceof List) queue.add(o);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String normalizeKey(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // Prepare a cleaned tel string preserving a leading '+' if present (for ACTION_DIAL).
    private static String extractForTel(String s) {
        if (s == null) return "";
        s = s.trim();
        StringBuilder sb = new StringBuilder();
        char[] cs = s.toCharArray();
        for (int i = 0; i < cs.length; i++) {
            char c = cs[i];
            if (Character.isDigit(c)) sb.append(c);
            else if (c == '+' && sb.length() == 0) sb.append('+'); // only allow leading plus
        }
        return sb.toString();
    }

    // Extract only digits (useful for constructing wa.me links).
    private static String extractDigits(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        char[] cs = s.toCharArray();
        for (char c : cs) if (Character.isDigit(c)) sb.append(c);
        return sb.toString();
    }
}
