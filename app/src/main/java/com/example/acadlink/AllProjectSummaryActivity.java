package com.example.acadlink;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import android.provider.Telephony; // <-- added

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class AllProjectSummaryActivity extends AppCompatActivity {

    private static final String TAG = "AllProjectSummary";

    private TextView uploadedByTv, departmentTv, phoneTv, emailTv;
    private TextView titleTv, typeTv, levelTv, abstractTv, methodologyTv, similarityTv, aiGeneratedTv, filesTv;

    // intent/runtime
    private String projectId;
    private String intentTitle, intentProjectType1, intentProjectLevel, intentAbstract, intentMethodology, intentSimilarity, intentAi;
    private List<String> intentFileInfoList;

    // self-contact prevention
    private String uploaderUidResolved = null;
    private String uploaderEmailResolved = null;
    private String uploaderPhoneResolvedDigits = null;

    private String currentUid = null;
    private String currentEmail = null;
    private String currentPhoneDigits = null;

    // cache
    private static final String PREFS_NAME = "proj_temp";

    // cache title for primary-folder heuristics
    private String cachedProjectTitle = null;

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
        if (toolbarBackBtn != null) toolbarBackBtn.setOnClickListener(v -> finish());

        // toolbar 3-line menu
        ImageButton menuBtn = findViewById(R.id.toolbarMenuBtns);
        if (menuBtn != null) {
            menuBtn.setOnClickListener(this::showOverflowMenu);
        }

        // bind UI
        titleTv = findViewById(R.id.summaryTitle);
        typeTv = findViewById(R.id.summaryType);
        levelTv = findViewById(R.id.summaryLevel);
        abstractTv = findViewById(R.id.summaryAbstract);
        methodologyTv = findViewById(R.id.summaryMethodology);
        similarityTv = findViewById(R.id.summarySimilarity);
        aiGeneratedTv = findViewById(R.id.summaryAiGenerated);
        filesTv = findViewById(R.id.summaryFiles);

        uploadedByTv = findViewById(R.id.summaryUploadedByName);
        departmentTv = findViewById(R.id.summaryDepartment);
        phoneTv = findViewById(R.id.summaryPhone);
        emailTv = findViewById(R.id.summaryEmail);

        // self info
        if (FirebaseAuth.getInstance() != null && FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            currentEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            loadCurrentUserProfilePhone(); // sets currentPhoneDigits
        }

        ImageButton btnCall = findViewById(R.id.btn_call);
        ImageButton btnSms = findViewById(R.id.btn_sms);
        ImageButton btnWhatsapp = findViewById(R.id.btn_whatsapp);

        if (btnCall != null) {
            btnCall.setOnClickListener(v -> {
                String phone = safe(phoneTv.getText());
                String name = safe(uploadedByTv.getText());
                if (phone.isEmpty() || phone.equalsIgnoreCase("N/A")) {
                    Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (isSelfAttempt(phone)) {
                    Toast.makeText(this, "You cannot call yourself", Toast.LENGTH_SHORT).show();
                    return;
                }
                saveChatRecord(name, phone, "call");
                Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + extractForTel(phone)));
                startActivity(dial);
            });
        }

        if (btnSms != null) {
            btnSms.setOnClickListener(v -> {
                String phone = safe(phoneTv.getText());
                String name = safe(uploadedByTv.getText());
                if (phone.isEmpty() || phone.equalsIgnoreCase("N/A")) {
                    Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (isSelfAttempt(phone)) {
                    Toast.makeText(this, "You cannot message yourself", Toast.LENGTH_SHORT).show();
                    return;
                }
                // store message history (deduped)
                saveChatRecord(name, phone, "sms");

                // --- OPEN DEFAULT SMS APP DIRECTLY (no chooser) ---
                String to = extractForTel(phone);
                Intent sms = new Intent(Intent.ACTION_SENDTO);
                sms.setData(Uri.parse("smsto:" + to));
                String defaultSmsPkg = Telephony.Sms.getDefaultSmsPackage(this);
                if (defaultSmsPkg != null) {
                    sms.setPackage(defaultSmsPkg);
                }
                try {
                    startActivity(sms);
                } catch (Exception e) {
                    // fallback (rare: no default set). Still restrict to SMS handlers.
                    Intent fallback = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + to));
                    startActivity(fallback);
                }
            });
        }

        if (btnWhatsapp != null) {
            btnWhatsapp.setOnClickListener(v -> {
                String phone = safe(phoneTv.getText());
                String name = safe(uploadedByTv.getText());
                if (phone.isEmpty() || phone.equalsIgnoreCase("N/A")) {
                    Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (isSelfAttempt(phone)) {
                    Toast.makeText(this, "You cannot message yourself", Toast.LENGTH_SHORT).show();
                    return;
                }
                // store whatsapp history (deduped)
                saveChatRecord(name, phone, "whatsapp");

                String to = extractForTel(phone);
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse("whatsapp://send?phone=" + to));
                    startActivity(i);
                } catch (Exception e) {
                    Intent i2 = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + to));
                    startActivity(i2);
                }
            });
        }

        // read intent extras
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

        // FAST: load cache first
        loadFromPrefsAndPopulate(projectId);

        // then network
        if (projectId != null && !projectId.isEmpty()) {
            fetchFromFirebase(projectId);
        } else {
            // fall back to intent-only
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

            // name fallback with regno-skip + email-derive
            String rawName = firstNonEmpty(it.getStringExtra("uploadedBy"),
                    it.getStringExtra("uploaderName"),
                    it.getStringExtra("ownerName"),
                    it.getStringExtra("userName"));
            String email = firstNonEmpty(it.getStringExtra("uploaderEmail"), it.getStringExtra("email"));
            String bestName = bestHumanName(
                    rawName,
                    deriveNameFromEmail(email)
            );
            uploadedByTv.setText(firstNonEmpty(bestName, "N/A"));

            departmentTv.setText(firstNonEmpty(it.getStringExtra("uploaderDepartment"), "N/A"));
            phoneTv.setText(firstNonEmpty(it.getStringExtra("uploaderPhone"), "N/A"));
            emailTv.setText(firstNonEmpty(email, "N/A"));

            uploaderEmailResolved = safeTrim(email);
            uploaderPhoneResolvedDigits = extractDigits(safe(it.getStringExtra("uploaderPhone")));
        }
    }

    // ========== Overflow menu (black bg + white text through DarkPopupMenu) ==========
    private void showOverflowMenu(View anchor) {
        ContextThemeWrapper wrapper = new ContextThemeWrapper(this, R.style.DarkPopupMenu);
        PopupMenu popup = new PopupMenu(wrapper, anchor);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.menu_all_project_summary, popup.getMenu());
        popup.setOnMenuItemClickListener(this::onOverflowItemSelected);
        popup.show();
    }

    private boolean onOverflowItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_request_download) {
            requestDownload();
            return true;
        }
        return false;
    }

    /**
     * Modified requestDownload:
     * - Builds a request object with requester and project/uploader info.
     * - Writes the request both to:
     *   - downloadRequests/<projectId>/<requesterUid>  (keeps prior behavior / compatibility)
     *   - downloadRequestsReceived/<uploaderUid>/<projectId>/<requesterUid>
     *   - downloadRequestsSent/<requesterUid>/<projectId>
     *
     * Status stored: "pending" initially. Accept/Reject updates status in both received & sent nodes.
     *
     * NOTE: This keeps all original ids/names intact and adds only the minimal writes required.
     */
    private void requestDownload() {
        if (projectId == null || projectId.isEmpty()) {
            Toast.makeText(this, "Project ID missing.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "Please sign in to request.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String requesterEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        String displayName = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
        String requesterName = firstNonEmpty(displayName, deriveNameFromEmail(requesterEmail), "Unknown");

        String requesterPhone = currentPhoneDigits != null ? currentPhoneDigits : "";

        // Resolve uploader info (best-effort)
        String uploaderUid = uploaderUidResolved != null ? uploaderUidResolved : "unknown_uploader";
        String uploaderName = safe(uploadedByTv != null ? uploadedByTv.getText() : null);
        String projectTitle = firstNonEmpty(cachedProjectTitle, intentTitle, safe(titleTv != null ? titleTv.getText() : null), "N/A");

        // build payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("requesterUid", uid);
        payload.put("requesterName", requesterName);
        payload.put("requesterEmail", requesterEmail != null ? requesterEmail : "");
        payload.put("requesterPhone", requesterPhone);
        payload.put("projectId", projectId);
        payload.put("projectTitle", projectTitle);
        payload.put("uploaderUid", uploaderUid);
        payload.put("uploaderName", uploaderName);
        payload.put("status", "pending");
        payload.put("requestedAt", ServerValue.TIMESTAMP);

        // 1) original compatibility location (project-scoped)
        DatabaseReference compatRef = FirebaseDatabase.getInstance()
                .getReference("downloadRequests")
                .child(projectId)
                .child(uid);

        compatRef.setValue(payload)
                .addOnFailureListener(e -> {
                    // log only; continue to attempt inbox/sent writes
                    Log.w(TAG, "compatRef write failed: " + e.getMessage());
                });

        // 2) received inbox for uploader
        DatabaseReference inboxRef = FirebaseDatabase.getInstance()
                .getReference("downloadRequestsReceived")
                .child(uploaderUid)
                .child(projectId)
                .child(uid);

        // 3) sent list for requester
        DatabaseReference sentRef = FirebaseDatabase.getInstance()
                .getReference("downloadRequestsSent")
                .child(uid)
                .child(projectId);

        // write both (atomic-ish, but we write separately)
        inboxRef.setValue(payload)
                .addOnSuccessListener(unused -> {
                    sentRef.setValue(payload)
                            .addOnSuccessListener(u2 -> Toast.makeText(AllProjectSummaryActivity.this, "Request sent", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(AllProjectSummaryActivity.this, "Failed to save sent record: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(AllProjectSummaryActivity.this, "Failed to send request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // ========== Firebase fetch ==========
    private void fetchFromFirebase(String id) {
        DatabaseReference projRef = FirebaseDatabase.getInstance().getReference("projects").child(id);
        projRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    if (snapshot.exists()) {
                        String title = findStringInSnapshot(snapshot, "projectTitle", "title", "name");
                        String type1 = findStringInSnapshot(snapshot, "projectType1", "projectType", "type");
                        String level = findStringInSnapshot(snapshot, "projectType2", "projectLevel", "level");
                        String abs   = findStringInSnapshot(snapshot, "abstract", "Abstract", "abstractText", "projectAbstract");
                        String meth  = findStringInSnapshot(snapshot, "methodology", "method", "methods");
                        String sim   = findStringInSnapshot(snapshot, "similarity", "similarityPercent");
                        String ai    = findStringInSnapshot(snapshot, "aiGenerated", "ai", "aiDetected");

                        List<Map<String, Object>> filesList = extractFilesList(snapshot);

                        // uploader details (best-effort across schema variants)
                        String uploaderName  = firstNonEmpty(
                                findStringInSnapshot(snapshot, "uploaderName", "ownerName", "userName"), "N/A");
                        String uploaderDept  = firstNonEmpty(
                                findStringInSnapshot(snapshot, "uploaderDepartment", "department", "dept"), "N/A");
                        String uploaderPhone = firstNonEmpty(
                                findStringInSnapshot(snapshot, "uploaderPhone", "phoneNumber", "phone"), "N/A");
                        String uploaderEmail = firstNonEmpty(
                                findStringInSnapshot(snapshot, "uploaderEmail", "email"), "N/A");

                        // Try to resolve UID reliably (top-level or inside uploadedBy{})
                        uploaderUidResolved = firstNonEmpty(
                                findStringInSnapshot(snapshot, "uploaderId", "uploaderUid", "uid", "userId"),
                                parseFromUploadedBy(snapshot, "uid"),
                                null
                        );

                        // If uploadedBy{} exists, pull nested fields like name/department/phone/email from it
                        if (isBlankOrNA(uploaderName) || looksLikeRegisterNumber(uploaderName)) {
                            String ubName = coerceFromUploadedBy(snapshot,
                                    new String[]{"fullName","displayName","studentName","name","userName","username","ownerName"});
                            // combine first+last if needed
                            if (isBlankOrNA(ubName) || looksLikeRegisterNumber(ubName)) {
                                String fn = coerceFromUploadedBy(snapshot, new String[]{"firstName","first_name","fname"});
                                String ln = coerceFromUploadedBy(snapshot, new String[]{"lastName","last_name","lname"});
                                String comb = combine(fn, ln);
                                if (notEmpty(comb)) ubName = comb;
                            }
                            if (notEmpty(ubName)) uploaderName = ubName;
                        }
                        if (isBlankOrNA(uploaderDept)) {
                            uploaderDept = coerceFromUploadedBy(snapshot, new String[]{"department","dept","branch"});
                        }
                        if (isBlankOrNA(uploaderPhone) || "0".equals(uploaderPhone)) {
                            String code = coerceFromUploadedBy(snapshot, new String[]{"phoneCode","countryCode"});
                            String num  = coerceFromUploadedBy(snapshot, new String[]{"phone","phoneNumber"});
                            if (notEmpty(code) || notEmpty(num)) {
                                uploaderPhone = (notEmpty(code) ? code + " " : "") + (notEmpty(num) ? num : "");
                            } else {
                                uploaderPhone = coerceFromUploadedBy(snapshot, new String[]{"phone"});
                            }
                        }
                        if (isBlankOrNA(uploaderEmail)) {
                            uploaderEmail = coerceFromUploadedBy(snapshot, new String[]{"email"});
                        }

                        // Final best name: skip regno; fallback to email-based
                        String bestName = bestHumanName(
                                uploaderName,
                                deriveNameFromEmail(uploaderEmail)
                        );

                        uploaderEmailResolved = notEmpty(uploaderEmail) ? uploaderEmail : null;
                        uploaderPhoneResolvedDigits = extractDigits(notEmpty(uploaderPhone) ? uploaderPhone : "");

                        // Paint main UI
                        populateUI(
                                firstNonEmpty(title, intentTitle, "N/A"),
                                firstNonEmpty(type1, intentProjectType1, "N/A"),
                                firstNonEmpty(level, intentProjectLevel, "N/A"),
                                firstNonEmpty(abs, intentAbstract, "N/A"),
                                firstNonEmpty(meth, intentMethodology, "N/A"),
                                firstNonEmpty(sim, intentSimilarity, "N/A"),
                                firstNonEmpty(ai, intentAi, "N/A"),
                                filesList
                        );
                        uploadedByTv.setText(firstNonEmpty(bestName, "N/A"));
                        departmentTv.setText(firstNonEmpty(uploaderDept, "N/A"));
                        phoneTv.setText(firstNonEmpty(uploaderPhone, "N/A"));
                        emailTv.setText(firstNonEmpty(uploaderEmail, "N/A"));

                        // Need profile lookup if name still bad (map-like/N-A/regno) or other blanks
                        if (notEmpty(uploaderUidResolved)) {
                            String shownName = uploadedByTv.getText().toString();
                            boolean needProfile =
                                    isMapLike(shownName) || "N/A".equals(shownName) || looksLikeRegisterNumber(shownName) ||
                                            "N/A".equals(departmentTv.getText().toString()) ||
                                            "N/A".equals(phoneTv.getText().toString());
                            if (needProfile) {
                                fetchUploaderProfileAndFill(uploaderUidResolved);
                            }
                        }

                        // cache whole snapshot
                        saveSnapshotToPrefs(projectId, snapshot);
                    } else {
                        Toast.makeText(AllProjectSummaryActivity.this, "Project not found.", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "parse error", e);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "db error: " + error.getMessage());
            }
        });
    }

    /** Lookup Users/{uid} and fill missing: name, department, phone, email */
    private void fetchUploaderProfileAndFill(@NonNull String uid) {
        FirebaseDatabase.getInstance().getReference("Users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        try {
                            // Prefer real name keys, skip register-number-looking values
                            String name = bestHumanName(
                                    val(snap,"fullName"),
                                    val(snap,"displayName"),
                                    val(snap,"studentName"),
                                    combine(val(snap,"firstName"), val(snap,"lastName")),
                                    val(snap,"userName"),
                                    val(snap,"username"),
                                    val(snap,"name"), // last
                                    deriveNameFromEmail(val(snap,"email"))
                            );
                            String dept = firstNonEmpty(val(snap,"department"), val(snap,"dept"), val(snap,"branch"), "N/A");
                            String code = firstNonEmpty(val(snap,"phoneCode"), val(snap,"countryCode"), "");
                            String num  = firstNonEmpty(val(snap,"phoneNumber"), val(snap,"phone"), "");
                            String phone = notEmpty(code) || notEmpty(num) ? (notEmpty(code) ? code + " " : "") + num : "N/A";
                            String email = firstNonEmpty(val(snap,"email"), emailTv.getText().toString(), "N/A");

                            String currentShown = uploadedByTv.getText().toString();
                            if (isMapLike(currentShown) || "N/A".equals(currentShown) || looksLikeRegisterNumber(currentShown)) {
                                uploadedByTv.setText(firstNonEmpty(name, currentShown, "N/A"));
                            }
                            if ("N/A".equals(departmentTv.getText().toString())) {
                                departmentTv.setText(firstNonEmpty(dept, "N/A"));
                            }
                            if ("N/A".equals(phoneTv.getText().toString())) {
                                phoneTv.setText(firstNonEmpty(phone, "N/A"));
                            }
                            if ("N/A".equals(emailTv.getText().toString())) {
                                emailTv.setText(firstNonEmpty(email, "N/A"));
                            }

                            uploaderEmailResolved = firstNonEmpty(email, uploaderEmailResolved);
                            uploaderPhoneResolvedDigits = extractDigits(firstNonEmpty(phone, uploaderPhoneResolvedDigits));
                        } catch (Exception ignored) {}
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) { }
                });
    }

    // Helpers for reading DataSnapshot children safely
    private static String val(DataSnapshot s, String key) {
        try { Object v = s.child(key).getValue(); return v == null ? null : String.valueOf(v).trim(); }
        catch (Exception e) { return null; }
    }
    private static String combine(String a, String b) {
        if (!notEmpty(a) && !notEmpty(b)) return null;
        return (notEmpty(a) ? a.trim() : "") + (notEmpty(a) && notEmpty(b) ? " " : "") + (notEmpty(b) ? b.trim() : "");
    }

    // Pull a simple string from uploadedBy{} if present, by preferred keys
    private String coerceFromUploadedBy(DataSnapshot root, String[] keys) {
        try {
            DataSnapshot ub = root.child("uploadedBy");
            if (!ub.exists()) return null;
            Object obj = ub.getValue();
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked") Map<String,Object> m = (Map<String, Object>) obj;
                for (String k: keys) {
                    Object v = m.get(k);
                    if (v == null) {
                        for (String mk : m.keySet()) {
                            if (normalizeKey(mk).equals(normalizeKey(k))) { v = m.get(mk); break; }
                        }
                    }
                    if (v != null && String.valueOf(v).trim().length() > 0) return String.valueOf(v).trim();
                }
                Object f = pickByNames(m, new String[]{"firstName","first_name","fname"});
                Object l = pickByNames(m, new String[]{"lastName","last_name","lname"});
                String combined = combine(f == null ? null : String.valueOf(f), l == null ? null : String.valueOf(l));
                if (notEmpty(combined)) return combined;
            }
        } catch (Exception ignored) {}
        return null;
    }
    private String parseFromUploadedBy(DataSnapshot root, String key) {
        return coerceFromUploadedBy(root, new String[]{key});
    }
    private static Object pickByNames(Map<String,Object> m, String[] keys) {
        for (String k : keys) {
            if (m.containsKey(k)) return m.get(k);
            for (String mk : m.keySet()) {
                if (normalizeKey(mk).equals(normalizeKey(k))) return m.get(mk);
            }
        }
        return null;
    }

    private List<Map<String, Object>> extractFilesList(DataSnapshot snapshot) {
        try {
            if (snapshot.hasChild("files")) {
                Object fobj = snapshot.child("files").getValue();
                if (fobj instanceof List) {
                    //noinspection unchecked
                    return (List<Map<String, Object>>) fobj;
                }
            }
            Object topVal = snapshot.getValue();
            if (topVal instanceof Map) {
                //noinspection unchecked
                Map<String, Object> m = (Map<String, Object>) topVal;
                Object candidate = findObjectInMapByKeyNames(m, new String[]{"files", "file", "attachments"});
                if (candidate instanceof List) {
                    //noinspection unchecked
                    return (List<Map<String, Object>>) candidate;
                }
            }
        } catch (Exception ignore) { }
        return null;
    }

    private void populateUI(String title, String type1, String level, String abs, String meth,
                            String sim, String ai, List<Map<String, Object>> filesList) {
        cachedProjectTitle = firstNonEmpty(title, ""); // for primary-folder heuristics

        titleTv.setText(firstNonEmpty(title, "N/A"));
        typeTv.setText(firstNonEmpty(type1, "N/A"));
        levelTv.setText(firstNonEmpty(level, "N/A"));
        abstractTv.setText(firstNonEmpty(abs, "N/A"));
        methodologyTv.setText(firstNonEmpty(meth, "N/A"));
        similarityTv.setText(firstNonEmpty(sim, "N/A"));
        aiGeneratedTv.setText(firstNonEmpty(ai, "N/A"));

        if (filesList != null && !filesList.isEmpty()) {
            filesTv.setText(formatFileInfoList(filesList));
        } else if (intentFileInfoList != null && !intentFileInfoList.isEmpty()) {
            // Number selected files, skip numbering for the first entry (assumed primary folder)
            StringBuilder sb = new StringBuilder();
            int serial = 1;
            for (int i = 0; i < intentFileInfoList.size(); i++) {
                String s = intentFileInfoList.get(i);
                if (s == null) continue;
                s = s.trim();
                if (s.isEmpty()) continue;
                if (sb.length() > 0) sb.append("\n");
                if (i == 0) {
                    // Primary folder: no serial number
                    sb.append(s);
                } else {
                    sb.append(serial++).append(". ").append(s);
                }
            }
            filesTv.setText(sb.length() == 0 ? "N/A" : sb.toString());
        } else {
            filesTv.setText("N/A");
        }
    }

    // ====== cache (instant paint) ======
    private void saveSnapshotToPrefs(String id, DataSnapshot snapshot) {
        try {
            if (id == null) return;
            Object obj = snapshot.getValue();
            if (obj == null) return;
            JSONObject json = new JSONObject((Map<?, ?>) obj);
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(keyForProject(id), json.toString())
                    .apply();
        } catch (Exception ignored) {}
    }

    private void loadFromPrefsAndPopulate(String id) {
        try {
            if (id == null) return;
            String js = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(keyForProject(id), null);
            if (js == null) return;
            JSONObject obj = new JSONObject(js);

            String title = obj.optString("projectTitle", obj.optString("title", obj.optString("name", "")));
            String type1 = obj.optString("projectType1", obj.optString("projectType", obj.optString("type", "")));
            String level = obj.optString("projectType2", obj.optString("projectLevel", obj.optString("level", "")));
            String abs   = obj.optString("abstract", obj.optString("Abstract", obj.optString("abstractText", obj.optString("projectAbstract", ""))));
            String meth  = obj.optString("methodology", obj.optString("method", obj.optString("methods", "")));
            String sim   = obj.optString("similarity", obj.optString("similarityPercent", ""));
            String ai    = obj.optString("aiGenerated", obj.optString("ai", obj.optString("aiDetected", "")));

            // quick paint
            populateUI(
                    firstNonEmpty(title, intentTitle, "N/A"),
                    firstNonEmpty(type1, intentProjectType1, "N/A"),
                    firstNonEmpty(level, intentProjectLevel, "N/A"),
                    firstNonEmpty(abs, intentAbstract, "N/A"),
                    firstNonEmpty(meth, intentMethodology, "N/A"),
                    firstNonEmpty(sim, intentSimilarity, "N/A"),
                    firstNonEmpty(ai, intentAi, "N/A"),
                    null
            );

            // Uploaded-by from cache with nested object fallbacks and regno filter
            String rawName = firstNonEmpty(
                    obj.optString("uploaderName", null),
                    obj.optString("ownerName", null),
                    obj.optString("userName", null)
            );
            // Try uploadedBy { ... }
            if (isBlankOrNA(rawName) || looksLikeRegisterNumber(rawName)) {
                JSONObject ub = obj.optJSONObject("uploadedBy");
                if (ub != null) {
                    rawName = firstNonEmpty(
                            ub.optString("fullName", null),
                            ub.optString("displayName", null),
                            ub.optString("studentName", null),
                            combine(ub.optString("firstName", null), ub.optString("lastName", null)),
                            ub.optString("name", null),
                            ub.optString("userName", null),
                            ub.optString("username", null)
                    );
                }
            }
            String email = firstNonEmpty(
                    obj.optString("uploaderEmail", null),
                    obj.optString("email", null),
                    obj.optJSONObject("uploadedBy") != null ? obj.optJSONObject("uploadedBy").optString("email", null) : null
            );
            String bestName = bestHumanName(rawName, deriveNameFromEmail(email));
            uploadedByTv.setText(firstNonEmpty(bestName, "N/A"));

            // Dept
            String dept = firstNonEmpty(
                    obj.optString("uploaderDepartment", null),
                    obj.optString("department", null),
                    obj.optString("dept", null)
            );
            if (isBlankOrNA(dept) && obj.optJSONObject("uploadedBy") != null) {
                dept = firstNonEmpty(
                        obj.optJSONObject("uploadedBy").optString("department", null),
                        obj.optJSONObject("uploadedBy").optString("dept", null),
                        obj.optJSONObject("uploadedBy").optString("branch", null)
                );
            }
            departmentTv.setText(firstNonEmpty(dept, "N/A"));

            // Phone
            String code = firstNonEmpty(
                    obj.optString("phoneCode", null),
                    obj.optJSONObject("uploadedBy") != null ? obj.optJSONObject("uploadedBy").optString("phoneCode", null) : null,
                    obj.optJSONObject("uploadedBy") != null ? obj.optJSONObject("uploadedBy").optString("countryCode", null) : null
            );
            String num  = firstNonEmpty(
                    obj.optString("uploaderPhone", null),
                    obj.optString("phoneNumber", null),
                    obj.optString("phone", null),
                    obj.optJSONObject("uploadedBy") != null ? obj.optJSONObject("uploadedBy").optString("phoneNumber", null) : null,
                    obj.optJSONObject("uploadedBy") != null ? obj.optJSONObject("uploadedBy").optString("phone", null) : null
            );
            if (notEmpty(code) || notEmpty(num)) {
                phoneTv.setText((notEmpty(code)? code + " " : "") + num);
            } else {
                phoneTv.setText("N/A");
            }

            emailTv.setText(firstNonEmpty(email, "N/A"));
        } catch (Exception ignored) {}
    }

    private static String keyForProject(String id) { return "proj_" + id; }

    // ========== helpers ==========
    private void loadCurrentUserProfilePhone() {
        try {
            if (currentUid == null) return;
            FirebaseDatabase.getInstance().getReference("Users").child(currentUid)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                            try {
                                String code = String.valueOf(snapshot.child("phoneCode").getValue());
                                String num  = String.valueOf(snapshot.child("phoneNumber").getValue());
                                String merged = ((code != null && !code.trim().isEmpty()) ? code + " " : "") + (num == null ? "" : num);
                                currentPhoneDigits = extractDigits(merged);
                            } catch (Exception ignored) {}
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) {}
                    });
        } catch (Exception ignored) {}
    }

    private boolean isSelfAttempt(String candidateNumber) {
        String cand = extractDigits(candidateNumber);
        if (currentPhoneDigits != null && !currentPhoneDigits.isEmpty() && !cand.isEmpty()) {
            if (cand.equals(currentPhoneDigits)) return true;
            if (cand.endsWith(currentPhoneDigits) || currentPhoneDigits.endsWith(cand)) return true;
        }
        if (currentEmail != null && uploaderEmailResolved != null && currentEmail.equalsIgnoreCase(uploaderEmailResolved)) return true;
        if (currentUid != null && uploaderUidResolved != null && currentUid.equals(uploaderUidResolved)) return true;
        return false;
    }

    private void saveChatRecord(String peerName, String peerPhone, String via) {
        try {
            String me = (FirebaseAuth.getInstance().getCurrentUser() != null)
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";

            // --- keep existing backend write (unchanged) ---
            DatabaseReference chats = FirebaseDatabase.getInstance()
                    .getReference("chats").child(me).push();
            chats.child("peerName").setValue(peerName);
            chats.child("peerPhone").setValue(peerPhone);
            chats.child("via").setValue(via);
            chats.child("timestamp").setValue(ServerValue.TIMESTAMP);

            // --- Dedup locally so only one row per (peer, via). Update timestamp instead of adding duplicates ---
            Context ctx = getApplicationContext();
            android.content.SharedPreferences prefs = ctx.getSharedPreferences("app_prefs", MODE_PRIVATE);
            String key = "chats_" + me;
            String existing = prefs.getString(key, "[]");
            JSONArray arr = new JSONArray(existing);

            String candDigits = extractDigits(peerPhone);
            int foundIndex = -1;
            JSONObject found = null;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String v = String.valueOf(o.optString("via", ""));
                String num = String.valueOf(o.optString("number", ""));
                String numDigits = extractDigits(num);
                boolean numbersMatch = (!candDigits.isEmpty() && !numDigits.isEmpty()) &&
                        (candDigits.equals(numDigits) || candDigits.endsWith(numDigits) || numDigits.endsWith(candDigits));
                if (numbersMatch && v.equalsIgnoreCase(via)) {
                    foundIndex = i;
                    found = o;
                    break;
                }
            }

            long now = System.currentTimeMillis();
            if (found != null) {
                // update fields & move to end (most recent)
                found.put("name", peerName);
                found.put("number", peerPhone);
                found.put("via", via);
                found.put("ts", now);

                JSONArray newArr = new JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    if (i == foundIndex) continue;
                    Object item = arr.opt(i);
                    if (item != null) newArr.put(item);
                }
                newArr.put(found);
                prefs.edit().putString(key, newArr.toString()).apply();
            } else {
                JSONObject obj = new JSONObject();
                obj.put("name", peerName);
                obj.put("number", peerPhone);
                obj.put("via", via);              // "call" or "sms" or "whatsapp"
                obj.put("ts", now);
                arr.put(obj);
                prefs.edit().putString(key, arr.toString()).apply();
            }
        } catch (Exception ignored) {}
    }

    private static String firstNonEmpty(String... vals) {
        if (vals == null) return "N/A";
        for (String s : vals) {
            if (s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s.trim())) return s.trim();
        }
        return "N/A";
    }

    private static boolean notEmpty(String s) { return s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s.trim()); }
    private static boolean isBlankOrNA(String s) { return (s == null || s.trim().isEmpty() || "N/A".equalsIgnoreCase(s.trim())); }

    private static boolean isMapLike(String s) {
        if (s == null) return false;
        String t = s.trim();
        return t.startsWith("{") && t.endsWith("}") && t.contains("=");
    }

    private static String safe(Object tvText) {
        if (tvText == null) return "";
        String s = String.valueOf(tvText);
        return (s == null) ? "" : s.trim();
    }
    private static String safeTrim(String s) { return s == null ? null : s.trim(); }

    private static String extractForTel(String s) {
        if (s == null) return "";
        s = s.trim();
        StringBuilder sb = new StringBuilder();
        char[] cs = s.toCharArray();
        for (int i = 0; i < cs.length; i++) {
            char c = cs[i];
            if (Character.isDigit(c)) sb.append(c);
            else if (c == '+' && sb.length() == 0) sb.append('+');
        }
        return sb.toString();
    }

    private static String extractDigits(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        char[] cs = s.toCharArray();
        for (char c : cs) if (Character.isDigit(c)) sb.append(c);
        return sb.toString();
    }

    private Object findObjectInMapByKeyNames(Map<String, Object> map, String[] keys) {
        if (map == null || keys == null) return null;
        for (String k : keys) {
            if (k == null) continue;
            String nk = normalizeKey(k);
            if (map.containsKey(k)) return map.get(k);
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
                    if (v == null) continue;
                    if (v instanceof Map) {
                        @SuppressWarnings("unchecked") Map<String,Object> m = (Map<String, Object>) v;
                        Object nameLike = pickByNames(m, new String[]{"fullName","displayName","studentName","name","userName","username","ownerName","value","title","text"});
                        if (nameLike != null) {
                            String s = String.valueOf(nameLike).trim();
                            return s;
                        }
                        Object emailLike = pickByNames(m, new String[]{"email"});
                        if (emailLike != null) return String.valueOf(emailLike).trim();
                        continue;
                    }
                    return String.valueOf(v).trim();
                }
            }
            Object top = snapshot.getValue();
            if (top instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) top;
                for (String k : desiredKeys) {
                    Object v = findObjectInMapByKeyNames(map, new String[]{k});
                    if (v != null && !(v instanceof Map)) return String.valueOf(v).trim();
                    if (v instanceof Map) {
                        @SuppressWarnings("unchecked") Map<String,Object> mm = (Map<String, Object>) v;
                        Object nameLike = pickByNames(mm, new String[]{"fullName","displayName","studentName","name","userName","username","ownerName","value","title","text","email"});
                        if (nameLike != null) return String.valueOf(nameLike).trim();
                    }
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
                                    if (value instanceof Map) {
                                        @SuppressWarnings("unchecked") Map<String,Object> m2 = (Map<String, Object>) value;
                                        Object v2 = pickByNames(m2, new String[]{"fullName","displayName","studentName","name","userName","username","ownerName","value","title","text","email"});
                                        if (v2 != null) return String.valueOf(v2).trim();
                                    } else {
                                        return String.valueOf(value).trim();
                                    }
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

    private static boolean isTrue(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean) return (Boolean) o;
        String s = String.valueOf(o).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s) || "y".equalsIgnoreCase(s);
    }

    private static boolean equalsIgnoreCase(Object o, String s) {
        return o != null && s != null && s.equalsIgnoreCase(String.valueOf(o).trim());
    }

    private boolean isFolderEntry(Map<String, Object> m, String name) {
        try {
            if (m == null) return false;
            Object flag = findObjectInMapByKeyNames(m, new String[]{"isFolder","isDirectory","folder","directory"});
            if (isTrue(flag)) return true;
            Object type = findObjectInMapByKeyNames(m, new String[]{"type","mime","kind"});
            if (equalsIgnoreCase(type, "folder") || equalsIgnoreCase(type, "directory") || equalsIgnoreCase(type, "dir")) return true;
            Object children = findObjectInMapByKeyNames(m, new String[]{"children","items"});
            if (children instanceof List) return true;
            if (name != null) {
                if (name.endsWith("/")) return true;
                String nm = name.toLowerCase(Locale.ROOT);
                if (!nm.contains(".") && nm.length() <= 40) return true; // heuristic
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isPrimaryFolderEntry(Map<String, Object> m, String name, int index, boolean alreadyFound) {
        if (alreadyFound) return false;
        if (isTrue(findObjectInMapByKeyNames(m, new String[]{"primary","isPrimary","root","isRoot"}))) return true;
        if (name != null && notEmpty(cachedProjectTitle) && name.trim().equalsIgnoreCase(cachedProjectTitle.trim())) return true;
        // Fallback: first folder in the list
        return index == 1 && isFolderEntry(m, name);
    }

    private String formatFileInfoList(List<Map<String, Object>> list) {
        try {
            StringBuilder sb = new StringBuilder();
            int serial = 1;
            boolean primaryMarked = false;
            int index = 0;

            for (Map<String, Object> m : list) {
                index++;
                if (m == null) continue;
                String name = String.valueOf(findObjectInMapByKeyNames(m, new String[]{"name", "fileName", "title"}));
                if ("null".equalsIgnoreCase(name)) name = null;
                String size = String.valueOf(findObjectInMapByKeyNames(m, new String[]{"size", "fileSize"}));
                if ("null".equalsIgnoreCase(size)) size = null;

                boolean isFolder = isFolderEntry(m, name);
                boolean isPrimary = isFolder && isPrimaryFolderEntry(m, name, index, primaryMarked);
                if (isPrimary) primaryMarked = true;

                if (sb.length() > 0) sb.append("\n");

                String displayName = notEmpty(name) ? name.trim() : "Item";

                if (isPrimary) {
                    // Primary folder: no serial, no size
                    sb.append(displayName);
                } else if (isFolder) {
                    // Other folders: no serial (safer), no size
                    sb.append(displayName);
                } else {
                    // Files: show serial + size if present
                    sb.append(serial++).append(". ").append(displayName);
                    if (notEmpty(size)) {
                        sb.append(" (").append(size.trim()).append(")");
                    }
                }
            }
            return sb.length() == 0 ? "N/A" : sb.toString();
        } catch (Exception e) {
            return "N/A";
        }
    }

    private static String deriveNameFromEmail(String email) {
        try {
            if (!notEmpty(email)) return null;
            String local = email.split("@")[0];
            local = local.replaceAll("[^a-zA-Z0-9._-]", " ");
            String[] parts = local.split("[._-]+");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (p.isEmpty()) continue;
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (p.length() > 1) sb.append(p.substring(1));
                sb.append(" ");
            }
            String s = sb.toString().trim();
            return s.isEmpty() ? null : s;
        } catch (Exception e) { return null; }
    }

    /** Choose first non-empty, non-register-number-like value. */
    private static String bestHumanName(String... candidates) {
        // pass 1: good human-like
        for (String c : candidates) {
            if (notEmpty(c) && !looksLikeRegisterNumber(c)) return c.trim();
        }
        // pass 2: anything non-empty if all look like regnos
        for (String c : candidates) {
            if (notEmpty(c)) return c.trim();
        }
        return null;
    }

    /** Heuristic: detect register-number-looking strings (no spaces, has digits, length 5-20, mostly alnum, often uppercase). */
    private static boolean looksLikeRegisterNumber(String s) {
        if (!notEmpty(s)) return false;
        String t = s.trim();
        if (t.contains("@")) return false;     // emails are fine
        if (t.contains(" ")) return false;     // names usually have spaces
        int len = t.length();
        if (len < 5 || len > 20) return false;

        int digits = 0, letters = 0, others = 0, lowers = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isDigit(c)) digits++;
            else if (Character.isLetter(c)) {
                letters++;
                if (Character.isLowerCase(c)) lowers++;
            } else if (c=='_' || c=='-' || c=='.') {
                // allowed
            } else {
                others++;
            }
        }
        if (others > 0) return false;
        if (digits == 0) return false;
        // Strong signals: no lowercase + some digits -> likely reg no
        if (lowers == 0 && digits >= 2) return true;
        // If digits dominate letters, also likely a reg no
        return digits >= 3 && digits >= letters;
    }
}
