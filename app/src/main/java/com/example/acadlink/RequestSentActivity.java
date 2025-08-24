package com.example.acadlink;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * RequestSentActivity
 *
 * - Shows sent requests and allows download when accepted.
 * - When a download completes we now write downloader metadata so only the downloader sees the card.
 * - PDF export prints uploader contact details.
 *
 * NOTE: Non-essential download/attachment logic was intentionally left as in original.
 */
public class RequestSentActivity extends AppCompatActivity {

    private static final String TAG = "RequestSentActivity";
    private static final int REQ_WRITE_EXTERNAL = 1423;

    // treat any of these as "accepted"
    private static final Set<String> ACCEPTED_STATUSES = new HashSet<>(Arrays.asList(
            "accepted","approved","granted","allow","allowed","ready","true","yes"));

    // NEW: normalized marker for downloaded
    private static final String DOWNLOADED_STATUS = "downloaded";

    private RecyclerView rv;
    private SentAdapter adapter;
    private final List<SentItem> items = new ArrayList<>();
    private String currentUid;

    // Pending values when permission request is in-flight
    private String pendingProjectId = null;
    private String pendingProjectTitle = null;
    private String pendingUploaderName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_request_sent);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageButton back = findViewById(R.id.toolbarBackBtn);
        if (back != null) back.setOnClickListener(v -> finish());

        TextView title = findViewById(R.id.toolbarTitleTv);
        if (title != null) title.setText("Requests Sent");

        rv = findViewById(R.id.rv_requests_sent);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SentAdapter();
        rv.setAdapter(adapter);

        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
            loadSentRequests();
        } else {
            Toast.makeText(this, "Please sign in", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSentRequests() {
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("downloadRequestsSent")
                .child(currentUid);
        ref.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                items.clear();
                try {
                    for (DataSnapshot projSnap : snapshot.getChildren()) {
                        SentItem s = new SentItem();
                        s.projectId = projSnap.getKey();
                        s.uploaderName = safeString(projSnap.child("uploaderName").getValue());
                        s.projectTitle = safeString(projSnap.child("projectTitle").getValue());
                        s.status = safeString(projSnap.child("status").getValue());
                        s.downloadUrl = safeString(projSnap.child("downloadUrl").getValue()); // optional fallback
                        items.add(s);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "parse error", e);
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private static class SentItem {
        String projectId;
        String uploaderName;
        String projectTitle;
        String status;
        String downloadUrl;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
    private static boolean isAccepted(String status) {
        return ACCEPTED_STATUSES.contains(norm(status));
    }
    // NEW: helper to detect downloaded status
    private static boolean isDownloaded(String status) {
        return DOWNLOADED_STATUS.equals(norm(status));
    }

    private class SentAdapter extends RecyclerView.Adapter<SentAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_request_sent, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            SentItem s = items.get(position);
            holder.tvTo.setText("Request to: " + (s.uploaderName != null ? s.uploaderName : "Uploader"));
            holder.tvProj.setText("Project: " + (s.projectTitle != null ? s.projectTitle : "N/A"));

            boolean downloaded = isDownloaded(s.status);
            String statusText = downloaded ? "Downloaded" : (s.status != null ? s.status : "Pending");
            holder.tvStatus.setText("Status: " + statusText);

            boolean accepted = isAccepted(s.status);
            boolean hasDirectUrl = s.downloadUrl != null && !s.downloadUrl.trim().isEmpty();

            // Show the button if accepted OR if there is a direct link we can open — but NOT if already downloaded
            holder.btnDownload.setVisibility((accepted || hasDirectUrl) && !downloaded ? View.VISIBLE : View.GONE);

            holder.btnDownload.setOnClickListener(v -> {
                if (accepted && s.projectId != null && !s.projectId.isEmpty()) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        if (ContextCompat.checkSelfPermission(RequestSentActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                != PackageManager.PERMISSION_GRANTED) {
                            pendingProjectId = s.projectId;
                            pendingProjectTitle = s.projectTitle;
                            pendingUploaderName = s.uploaderName;
                            ActivityCompat.requestPermissions(RequestSentActivity.this,
                                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                    REQ_WRITE_EXTERNAL);
                            return;
                        }
                    }
                    startExportAndDownloadForProject(s.projectId, s.projectTitle, s.uploaderName);
                } else if (hasDirectUrl) {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(s.downloadUrl));
                        startActivity(i);
                        // Mark as downloaded immediately after launching direct link
                        markAsDownloaded(s);
                    } catch (Exception e) {
                        Toast.makeText(RequestSentActivity.this, "Unable to open URL", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(RequestSentActivity.this, "Waiting for uploader to accept.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTo, tvProj, tvStatus;
            MaterialButton btnDownload;
            VH(View v) {
                super(v);
                tvTo = v.findViewById(R.id.tv_request_to);
                tvProj = v.findViewById(R.id.tv_project_title_sent);
                tvStatus = v.findViewById(R.id.tv_status_sent);
                btnDownload = v.findViewById(R.id.btn_download);
            }
        }
    }

    // ---------- permissions result ----------
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQ_WRITE_EXTERNAL) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingProjectId != null) {
                    startExportAndDownloadForProject(pendingProjectId, pendingProjectTitle, pendingUploaderName);
                }
            } else {
                Toast.makeText(this, "Storage permission required to save downloads on older Android.", Toast.LENGTH_LONG).show();
            }
            pendingProjectId = pendingProjectTitle = pendingUploaderName = null;
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    // ---------- main flow ----------
    private void startExportAndDownloadForProject(final String projectId, final String projectTitleCandidate, final String uploaderNameCandidate) {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Preparing download...");
        pd.setCancelable(false);
        pd.show();

        DatabaseReference projRef = FirebaseDatabase.getInstance().getReference("projects").child(projectId);
        projRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                pd.setMessage("Fetched project — preparing files...");

                String title = findStringInSnapshot(snapshot, "projectTitle", "title", "name");
                String type1 = findStringInSnapshot(snapshot, "projectType1", "projectType", "type");
                String level = findStringInSnapshot(snapshot, "projectType2", "projectLevel", "level");
                String abs = findStringInSnapshot(snapshot, "abstract", "Abstract", "projectAbstract");
                String meth = findStringInSnapshot(snapshot, "methodology", "method", "methods");
                String sim = findStringInSnapshot(snapshot, "similarity", "similarityPercent");
                String ai = findStringInSnapshot(snapshot, "aiGenerated", "ai");

                // NEW: robustly resolve uploader contact using same multi-key + nested technique
                UploaderContact contact = resolveUploaderContact(snapshot, uploaderNameCandidate);
                String uploaderName = contact.name;
                String department = contact.department;
                String phone = contact.phone;
                String uploaderEmail = contact.email;

                if (!notEmpty(title)) title = firstNonEmpty(projectTitleCandidate, "Project");
                final String finalTitle = sanitizeFileName(firstNonEmpty(title, "Project"));
                final String folderName = finalTitle; // Downloads/<folderName>

                // If phone or department are missing (or "N/A") — attempt to enrich from Users/{uid}
                boolean deptMissing = department == null || department.trim().isEmpty() || "N/A".equalsIgnoreCase(department);
                boolean phoneMissing = phone == null || phone.trim().isEmpty() || "N/A".equalsIgnoreCase(phone);

                // Try to read uploaderUid from project top-level or uploadedBy nested object
                String uploaderUid = findStringInSnapshot(snapshot, "uploaderId", "uploaderUid", "uid", "userId");
                if (!notEmpty(uploaderUid)) {
                    // Fall back to nested uploadedBy.uid
                    try {
                        String ubUid = coerceFromUploadedBy(snapshot, new String[]{"uid", "userId", "uploaderId"});
                        uploaderUid = ubUid;
                    } catch (Exception ignored) {}
                }

                if ((deptMissing || phoneMissing) && notEmpty(uploaderUid)) {
                    // Enrich from Users node asynchronously and proceed once fetched
                    pd.setMessage("Fetching uploader profile...");
                    final String uploaderUidFinal = uploaderUid; // <-- make final for inner class capture

                    FirebaseDatabase.getInstance().getReference("Users").child(uploaderUidFinal)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override public void onDataChange(@NonNull DataSnapshot usnap) {
                                    try {
                                        // department
                                        String uDept = findStringInSnapshot(usnap, "department", "dept", "branch", "departmentName", "department_name");
                                        if ((department == null || department.trim().isEmpty() || "N/A".equalsIgnoreCase(department)) && notEmpty(uDept)) {
                                            contact.department = uDept;
                                        }

                                        // phone: try code + phoneNumber or phone
                                        String code = safeString(usnap.child("phoneCode").getValue());
                                        String num = firstNonEmpty(safeString(usnap.child("phoneNumber").getValue()),
                                                safeString(usnap.child("phone").getValue()),
                                                safeString(usnap.child("mobile").getValue()),
                                                safeString(usnap.child("whatsapp").getValue())
                                        );
                                        String combined = combinePhone(code, num);
                                        if ((phone == null || phone.trim().isEmpty() || "N/A".equalsIgnoreCase(phone)) && notEmpty(combined)) {
                                            contact.phone = combined;
                                        }

                                        // optionally ensure name/email if missing
                                        if (!notEmpty(contact.name) || "N/A".equalsIgnoreCase(contact.name)) {
                                            String uName = findStringInSnapshot(usnap, "fullName", "displayName", "name", "userName", "username");
                                            if (notEmpty(uName)) contact.name = uName;
                                        }
                                        if (!notEmpty(contact.email) || "N/A".equalsIgnoreCase(contact.email)) {
                                            String uEmail = findStringInSnapshot(usnap, "email");
                                            if (notEmpty(uEmail)) contact.email = uEmail;
                                        }
                                    } catch (Exception ignored) {}

                                    // Proceed to make PDF and record
                                    performPdfExportAndRecord(pd, folderName, finalTitle, type1, level, abs, meth, sim, ai, contact, uploaderNameCandidate, projectId);
                                }

                                @Override public void onCancelled(@NonNull DatabaseError error) {
                                    // if error, proceed with whatever we have
                                    Log.w(TAG, "Failed to fetch Users/" + uploaderUidFinal + ": " + (error != null ? error.getMessage() : "unknown"));
                                    performPdfExportAndRecord(pd, folderName, finalTitle, type1, level, abs, meth, sim, ai, contact, uploaderNameCandidate, projectId);
                                }
                            });
                    return; // wait for asynchronous enrichment
                }

                // No enrichment needed -> proceed immediately
                performPdfExportAndRecord(pd, folderName, finalTitle, type1, level, abs, meth, sim, ai, contact, uploaderNameCandidate, projectId);
                return;
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                pd.dismiss();
                Toast.makeText(RequestSentActivity.this, "Failed to load project: " + error.getMessage(), Toast.LENGTH_LONG).show();
                Log.e(TAG, "db error", error.toException());
            }
        });
    }

    /**
     * Centralized small helper to perform PDF export + record & navigate.
     * Keeps behavior identical to previous code but allows the caller to wait for user-profile enrichment.
     */
    private void performPdfExportAndRecord(final ProgressDialog pd,
                                           final String folderName,
                                           final String finalTitle,
                                           final String type1,
                                           final String level,
                                           final String abs,
                                           final String meth,
                                           final String sim,
                                           final String ai,
                                           final UploaderContact contact,
                                           final String uploaderNameCandidate,
                                           final String projectId) {
        try {
            pd.setMessage("Exporting PDF...");
            boolean pdfSaved = saveProjectSummaryAsPdf(folderName, finalTitle, type1, level, abs, meth, sim, ai,
                    firstNonEmpty(contact.name, firstNonEmpty(uploaderNameCandidate, "Unknown")),
                    contact.department, contact.phone, contact.email);
            if (!pdfSaved) { Log.w(TAG, "PDF export may have failed for project " + finalTitle); }
        } catch (Exception e) {
            Log.w(TAG, "Error while exporting PDF: " + e.getMessage());
        } finally {
            try { pd.dismiss(); } catch (Exception ignored) {}
            // record & open downloads + mark as downloaded (UI + DB)
            addDownloadRecordAndOpenDownloads(finalTitle, firstNonEmpty(uploaderNameCandidate, "Unknown"), folderName, projectId);
        }
    }

    // ---------- record and open downloads (modified: include downloader metadata) ----------
    private void addDownloadRecordAndOpenDownloads(String projectTitle, String uploaderName, String folderName, String projectId) {
        try {
            SharedPreferences prefs = getSharedPreferences("downloads_pref", MODE_PRIVATE);
            String raw = prefs.getString("downloads_list", "[]");
            JSONArray arr = new JSONArray(raw);
            JSONObject rec = new JSONObject();
            rec.put("projectTitle", firstNonEmpty(projectTitle, "Project"));
            rec.put("uploaderName", firstNonEmpty(uploaderName, "Unknown"));
            if (folderName != null) rec.put("folder", folderName);
            rec.put("downloadedAt", System.currentTimeMillis());

            // include downloader metadata so DownloadsActivity can filter properly
            try {
                com.google.firebase.auth.FirebaseUser fu = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                if (fu != null) {
                    String uid = fu.getUid();
                    // Primary canonical key
                    rec.put("downloaderUid", uid);
                    // Compatibility / legacy keys (so older variants still match)
                    rec.put("downloaderId", uid);
                    rec.put("downloadedByUid", uid);
                    if (fu.getDisplayName() != null && !fu.getDisplayName().isEmpty()) {
                        rec.put("downloaderName", fu.getDisplayName());
                        rec.put("downloadedByName", fu.getDisplayName());
                    }
                    if (fu.getEmail() != null && !fu.getEmail().isEmpty()) {
                        rec.put("downloaderEmail", fu.getEmail());
                        rec.put("downloadedByEmail", fu.getEmail());
                    }
                }
            } catch (Exception ignored) {}

            // device id (fallback)
            try {
                String deviceId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
                if (deviceId != null && !deviceId.isEmpty()) rec.put("deviceId", deviceId);
            } catch (Exception ignored) {}

            // small marker so future code can detect v2-style records
            try { rec.put("__source", "downloads_pref_v2"); } catch (Exception ignored) {}

            arr.put(rec);
            prefs.edit().putString("downloads_list", arr.toString()).apply();
        } catch (Exception ignored) {}

        // NEW: persist "downloaded" status to the user's sent-requests node
        try {
            if (currentUid != null && projectId != null) {
                FirebaseDatabase.getInstance().getReference("downloadRequestsSent")
                        .child(currentUid).child(projectId).child("status")
                        .setValue(DOWNLOADED_STATUS);
            }
        } catch (Exception ignored) {}

        // NEW: update in-memory list + UI immediately
        try {
            for (int i = 0; i < items.size(); i++) {
                SentItem it = items.get(i);
                if (it != null && projectId != null && projectId.equals(it.projectId)) {
                    it.status = DOWNLOADED_STATUS;
                    if (adapter != null) adapter.notifyItemChanged(i);
                    break;
                }
            }
        } catch (Exception ignored) {}

        try {
            Intent i = new Intent(this, DownloadsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    // NEW: helper used when direct-link flow is taken
    private void markAsDownloaded(SentItem s) {
        try {
            if (s == null) return;
            s.status = DOWNLOADED_STATUS;
            if (adapter != null) {
                int index = items.indexOf(s);
                if (index >= 0) adapter.notifyItemChanged(index);
                else adapter.notifyDataSetChanged();
            }
            if (currentUid != null && s.projectId != null) {
                FirebaseDatabase.getInstance().getReference("downloadRequestsSent")
                        .child(currentUid).child(s.projectId).child("status")
                        .setValue(DOWNLOADED_STATUS);
            }
        } catch (Exception ignored) {}
    }

    // ---------- PDF export (points-based A4; mobile-friendly; prints uploader contact details) ----------
    private boolean saveProjectSummaryAsPdf(String folderName, String titleSafe, String type1, String level,
                                            String abs, String meth, String sim, String ai,
                                            String uploaderName, String department, String phone, String uploaderEmail) {
        android.print.PrintAttributes attrs = new android.print.PrintAttributes.Builder()
                .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                .setResolution(new android.print.PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                .setMinMargins(new android.print.PrintAttributes.Margins(0,0,0,0))
                .build();

        android.print.pdf.PrintedPdfDocument pdf = null;
        OutputStream os = null;
        try {
            // create PDF document
            pdf = new android.print.pdf.PrintedPdfDocument(this, attrs);

            // Use POINTS (1 inch = 72 points). Convert mils -> points (1 inch = 1000 mils)
            final int pageW = Math.round(attrs.getMediaSize().getWidthMils()  * 72f / 1000f); // ~595
            final int pageH = Math.round(attrs.getMediaSize().getHeightMils() * 72f / 1000f); // ~842

            // margins in points
            final float mL = 0.6f * 72f, mR = 0.6f * 72f, mT = 0.6f * 72f, mB = 0.6f * 72f;
            final float contentW = pageW - mL - mR;

            // Paints (sizes in points)
            android.graphics.Paint hSmall = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            hSmall.setTextSize(10f);
            hSmall.setColor(0xFF666666);

            android.graphics.Paint hTitle = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            hTitle.setTextSize(18f);
            hTitle.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
            hTitle.setColor(0xFF000000);

            android.graphics.Paint section = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            section.setTextSize(13f);
            section.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
            section.setColor(0xFF000000);

            android.graphics.Paint body = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            body.setTextSize(11f);
            body.setColor(0xFF000000);

            android.graphics.Paint label = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            label.setTextSize(11f);
            label.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
            label.setColor(0xFF000000);

            android.graphics.Paint value = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            value.setTextSize(11f);
            value.setColor(0xFF000000);

            android.graphics.Paint line = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            line.setStyle(android.graphics.Paint.Style.STROKE);
            line.setStrokeWidth(0.75f);
            line.setColor(0xFF000000);

            android.graphics.Paint footerPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            footerPaint.setTextSize(9f);
            footerPaint.setColor(0xFF666666);

            final android.print.pdf.PrintedPdfDocument pdfRef = pdf;

            // Local drawer (same style as other PDF writer)
            class Drawer {
                int pageNo = 0;
                android.graphics.pdf.PdfDocument.Page page;
                android.graphics.Canvas canvas;
                float y;

                void newPage() {
                    android.graphics.pdf.PdfDocument.PageInfo info =
                            new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, ++pageNo).create();
                    page = pdfRef.startPage(info);
                    canvas = page.getCanvas();
                    y = mT;

                    canvas.drawText("Project Summary", mL, y, hSmall);
                    y += (hSmall.getFontMetrics().descent - hSmall.getFontMetrics().ascent) + 3f;
                    canvas.drawText(titleSafe, mL, y, hTitle);
                    y += (hTitle.getFontMetrics().descent - hTitle.getFontMetrics().ascent) + 6f;

                    canvas.drawLine(mL, y, mL + contentW, y, line);
                    y += 8f;
                }

                void ensureSpace(float needed) {
                    if (y + needed > pageH - mB) {
                        finishPageFooter();
                        pdfRef.finishPage(page);
                        newPage();
                    }
                }

                void finishPageFooter() {
                    String pn = "Page " + pageNo;
                    float w = footerPaint.measureText(pn);
                    float cx = mL + (contentW - w) / 2f;
                    float fy = pageH - (mB / 2f);
                    canvas.drawText(pn, cx, fy, footerPaint);
                }

                java.util.List<String> wrap(String text, android.graphics.Paint p, float maxWidth) {
                    java.util.List<String> lines = new java.util.ArrayList<>();
                    if (text == null) return lines;
                    String[] paras = text.replace("\r", "").split("\n");
                    for (String para : paras) {
                        if (para.trim().isEmpty()) { lines.add(""); continue; }
                        String[] words = para.split("\\s+");
                        StringBuilder cur = new StringBuilder();
                        for (String w : words) {
                            String test = (cur.length() == 0) ? w : cur + " " + w;
                            if (p.measureText(test) <= maxWidth) {
                                cur.setLength(0); cur.append(test);
                            } else {
                                if (cur.length() > 0) lines.add(cur.toString());
                                cur.setLength(0); cur.append(w);
                            }
                        }
                        if (cur.length() > 0) lines.add(cur.toString());
                    }
                    return lines;
                }

                float lineHeight(android.graphics.Paint p) {
                    android.graphics.Paint.FontMetrics fm = p.getFontMetrics();
                    return (fm.descent - fm.ascent) * 1.15f;
                }

                void drawWrappedText(String text, android.graphics.Paint p, float x, float maxWidth) {
                    java.util.List<String> lines = wrap(text, p, maxWidth);
                    float lh = lineHeight(p);
                    for (String l : lines) {
                        ensureSpace(lh);
                        canvas.drawText(l, x, y - p.getFontMetrics().ascent, p);
                        y += lh;
                    }
                }

                float drawTableRow(String left, String right, float leftW, float rightW, float pad) {
                    java.util.List<String> l1 = wrap(left, label, leftW - 2 * pad);
                    java.util.List<String> l2 = wrap(right, value, rightW - 2 * pad);
                    float h1 = Math.max(1, l1.size()) * lineHeight(label) + 2 * pad;
                    float h2 = Math.max(1, l2.size()) * lineHeight(value) + 2 * pad;
                    float rowH = Math.max(h1, h2);

                    ensureSpace(rowH);

                    float x1 = mL;
                    float x2 = mL + leftW;
                    float top = y;
                    float bottom = y + rowH;

                    canvas.drawRect(new android.graphics.RectF(x1, top, x1 + leftW, bottom), line);
                    canvas.drawRect(new android.graphics.RectF(x2, top, x2 + rightW, bottom), line);

                    float textY = y + pad - label.getFontMetrics().ascent;
                    for (String s : l1) { canvas.drawText(s, x1 + pad, textY, label); textY += lineHeight(label); }

                    textY = y + pad - value.getFontMetrics().ascent;
                    for (String s : l2) { canvas.drawText(s, x2 + pad, textY, value); textY += lineHeight(value); }

                    y = bottom;
                    return rowH;
                }
            }

            Drawer d = new Drawer();
            d.newPage();

            float tablePad = 6f;
            float leftColW = contentW * 0.32f;
            float rightColW = contentW - leftColW;

            d.drawTableRow("Project Title", titleSafe, leftColW, rightColW, tablePad);
            d.drawTableRow("Type",          firstNonEmpty(type1, "N/A"), leftColW, rightColW, tablePad);
            d.drawTableRow("Level",         firstNonEmpty(level, "N/A"), leftColW, rightColW, tablePad);

            // include uploader contact rows explicitly
            d.drawTableRow("Uploaded by", firstNonEmpty(uploaderName, "N/A"), leftColW, rightColW, tablePad);
            d.drawTableRow("Department", firstNonEmpty(department, "N/A"), leftColW, rightColW, tablePad);
            d.drawTableRow("Phone", firstNonEmpty(phone, "N/A"), leftColW, rightColW, tablePad);
            d.drawTableRow("Email", firstNonEmpty(uploaderEmail, "N/A"), leftColW, rightColW, tablePad);

            d.drawTableRow("Similarity",    firstNonEmpty(sim, "N/A"), leftColW, rightColW, tablePad);
            d.drawTableRow("AI Generated",  firstNonEmpty(ai, "N/A"), leftColW, rightColW, tablePad);

            d.y += 12f;

            d.canvas.drawText("Abstract", mL, d.y - section.getFontMetrics().ascent, section);
            d.y += (section.getFontMetrics().descent - section.getFontMetrics().ascent) + 4f;
            d.drawWrappedText(firstNonEmpty(abs, "N/A"), body, mL, contentW);

            d.y += 10f;

            d.canvas.drawText("Methodology", mL, d.y - section.getFontMetrics().ascent, section);
            d.y += (section.getFontMetrics().descent - section.getFontMetrics().ascent) + 4f;
            d.drawWrappedText(firstNonEmpty(meth, "N/A"), body, mL, contentW);

            d.y += 10f;

            d.canvas.drawText("Files", mL, d.y - section.getFontMetrics().ascent, section);
            d.y += (section.getFontMetrics().descent - section.getFontMetrics().ascent) + 4f;
            d.drawWrappedText("Contact Project Uploader for Other Files of " + folderName, body, mL, contentW);

            d.finishPageFooter();
            pdfRef.finishPage(d.page);

            // Save exactly where you saved before: Downloads/<folderName>/<unique file>
            String baseName = "Project Summary - " + titleSafe + ".pdf";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                String unique = ensureUniqueDisplayNameQ(folderName, baseName);
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, unique);
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + folderName + "/");
                cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new IllegalStateException("Storage insert failed");
                OutputStream osLocal = getContentResolver().openOutputStream(uri);
                if (osLocal == null) throw new IllegalStateException("Output stream null for pdf");
                pdf.writeTo(osLocal);
                osLocal.flush();
                osLocal.close();
                ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(uri, done, null, null);
            } else {
                File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folderName);
                if (!folder.exists()) folder.mkdirs();
                String unique = ensureUniqueDisplayNameLegacy(folder, baseName);
                File dst = new File(folder, unique);
                OutputStream osLocal = new FileOutputStream(dst);
                pdf.writeTo(osLocal);
                osLocal.flush();
                osLocal.close();
                try { MediaScannerConnection.scanFile(this, new String[]{dst.getAbsolutePath()}, new String[]{"application/pdf"}, null); } catch (Exception ignored) {}
            }
            pdf.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "PDF export failed", e);
            try { if (pdf != null) pdf.close(); } catch (Exception ignored) {}
            try { if (os != null) os.close(); } catch (Exception ignored) {}
            return false;
        }
    }

    // --- helper methods used above (firstNonEmpty, sanitizeFileName, ensureUniqueDisplayNameQ/Legacy, etc.)
    // (If these already exist elsewhere in your project keep them; they're included here so the file compiles standalone.)

    private static String firstNonEmpty(String... vals) {
        if (vals == null) return "N/A";
        for (String s : vals) if (s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s.trim())) return s.trim();
        return "N/A";
    }

    private static boolean notEmpty(String s) { return s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s.trim()); }

    private static String sanitizeFileName(String s) {
        if (s == null) return "Project";
        String cleaned = s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", " ").trim();
        if (cleaned.isEmpty()) return "Project";
        return cleaned.length() > 120 ? cleaned.substring(0, 120).trim() : cleaned;
    }

    // (ensureUniqueDisplayNameQ, ensureUniqueDisplayNameLegacy and existsInDownloadsQ are used by saveProjectSummaryAsPdf.)
    private String ensureUniqueDisplayNameQ(String folderName, String displayName) {
        String base = displayName;
        String ext = "";
        int dot = displayName.lastIndexOf('.');
        if (dot > 0 && dot < displayName.length() - 1) { base = displayName.substring(0, dot); ext = displayName.substring(dot); }

        String rel = Environment.DIRECTORY_DOWNLOADS + "/" + folderName + "/";
        String candidate = displayName;
        int i = 1;
        while (existsInDownloadsQ(rel, candidate)) {
            candidate = base + " (" + i++ + ")" + ext;
            if (i > 500) break;
        }
        return candidate;
    }

    private boolean existsInDownloadsQ(String relativePath, String displayName) {
        try {
            Uri uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String[] proj = { MediaStore.MediaColumns._ID };
            String sel = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?";
            String[] args = { relativePath, displayName };
            try (android.database.Cursor c = getContentResolver().query(uri, proj, sel, args, null)) {
                return c != null && c.moveToFirst();
            } catch (Exception e) { return false; }
        } catch (Exception e) { return false; }
    }

    private String ensureUniqueDisplayNameLegacy(File folder, String displayName) {
        String base = displayName;
        String ext = "";
        int dot = displayName.lastIndexOf('.');
        if (dot > 0 && dot < displayName.length() - 1) { base = displayName.substring(0, dot); ext = displayName.substring(dot); }

        File candidate = new File(folder, displayName);
        int i = 1;
        while (candidate.exists()) {
            candidate = new File(folder, base + " (" + i++ + ")" + ext);
            if (i > 500) break;
        }
        return candidate.getName();
    }

    // findStringInSnapshot helper (a robust attempt to find one of the desired keys)
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
            if (top instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String,Object> map = (java.util.Map<String,Object>) top;
                for (String k : desiredKeys) {
                    for (String mk : map.keySet()) {
                        if (mk == null) continue;
                        if (normalize(mk).equals(normalize(k))) {
                            Object v = map.get(mk);
                            if (v != null) return String.valueOf(v).trim();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String safeString(Object o) {
        try { return o == null ? null : String.valueOf(o); } catch (Exception e) { return null; }
    }

    // small convenience: derive a displayable name from an email if displayName not available
    private static String deriveNameFromEmail(String email) {
        try {
            if (email == null) return "";
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
            return s.isEmpty() ? "" : s;
        } catch (Exception e) { return ""; }
    }

    // =========================
    // Robust contact resolver used by PDF export
    // =========================
    private static class UploaderContact {
        String name;
        String department;
        String phone;
        String email;
    }

    /**
     * Resolve contact details attempting:
     * 1) Top-level keys (many aliases)
     * 2) uploadedBy{} nested keys (many aliases)
     * 3) combine phone code + number where present
     * 4) avoid returning raw email as "name" (use as email only; derive friendly name if needed)
     */
    private UploaderContact resolveUploaderContact(DataSnapshot snapshot, String uploaderNameCandidate) {
        UploaderContact c = new UploaderContact();

        // --- Top-level reads (multiple key fallbacks) ---
        String topEmail = findStringInSnapshot(snapshot,
                "uploaderEmail","email","contactEmail","uploader_email");
        String topName = findStringInSnapshot(snapshot,
                "uploadedByName","uploaderName","uploader","owner","ownerName","userName","uploaded_by");
        // ignore raw emails if they were accidentally returned as "name"
        if (topName != null && topName.contains("@")) topName = null;

        String topDept = findStringInSnapshot(snapshot,
                "department","dept","departmentName","department_name","faculty","branch");
        String topPhone = findStringInSnapshot(snapshot,
                "phone","phoneNumber","phone_no","contact","contactNumber","uploaderPhone","mobile","whatsapp");

        // --- Nested uploadedBy{} object fallbacks (coerceFromUploadedBy extracts clean values from map) ---
        String ubFullName = coerceFromUploadedBy(snapshot, new String[]{"fullName","displayName","studentName","name","userName","username","ownerName"});
        if (!notEmpty(ubFullName)) {
            // fallback to first+last inside uploadedBy
            String fn = coerceFromUploadedBy(snapshot, new String[]{"firstName","first_name","fname"});
            String ln = coerceFromUploadedBy(snapshot, new String[]{"lastName","last_name","lname"});
            if (notEmpty(fn) || notEmpty(ln)) ubFullName = combine(fn, ln);
        }

        String ubDept = coerceFromUploadedBy(snapshot, new String[]{"department","dept","branch","departmentName","department_name"});
        String ubPhoneCode = coerceFromUploadedBy(snapshot, new String[]{"phoneCode","countryCode","phone_code"});
        String ubPhoneNum  = coerceFromUploadedBy(snapshot, new String[]{"phoneNumber","phone","mobile","whatsapp","contactNumber"});
        String ubPhoneCombined = combinePhone(ubPhoneCode, ubPhoneNum);

        String ubEmail = coerceFromUploadedBy(snapshot, new String[]{"email","uploaderEmail","contactEmail","uploader_email"});

        // --- Decide final values with fallbacks ---
        c.email = firstNonEmpty(topEmail, ubEmail);

        // Name priority:
        // 1) nested full name, 2) top-level name (if not an email/regno), 3) uploaderNameCandidate (UI passed), 4) derive from email, 5) Unknown
        String derivedFromEmail = deriveNameFromEmail(c.email);
        c.name = firstNonEmpty(
                cleanIfRegister(ubFullName),
                cleanIfRegister(topName),
                cleanIfRegister(uploaderNameCandidate),
                cleanIfRegister(derivedFromEmail),
                "Unknown"
        );

        c.department = firstNonEmpty(topDept, ubDept);
        c.phone = firstNonEmpty(normalizePhone(topPhone), normalizePhone(ubPhoneCombined));

        return c;
    }

    /** Helper: read nested uploadedBy object intelligently */
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

    private static Object pickByNames(Map<String,Object> m, String[] keys) {
        try {
            for (String k : keys) {
                if (m.containsKey(k)) return m.get(k);
                for (String mk : m.keySet()) {
                    if (normalizeKey(mk).equals(normalizeKey(k))) return m.get(mk);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String normalizeKey(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String combine(String a, String b) {
        if (!notEmpty(a) && !notEmpty(b)) return null;
        return (notEmpty(a) ? a.trim() : "") + (notEmpty(a) && notEmpty(b) ? " " : "") + (notEmpty(b) ? b.trim() : "");
    }

    private String combinePhone(String code, String number) {
        if (notEmpty(code) && notEmpty(number)) {
            String c = code.trim();
            if (!c.startsWith("+") && c.matches("^[0-9]{1,4}$")) c = "+" + c;
            return c + " " + number.trim();
        }
        if (notEmpty(number)) return number.trim();
        if (notEmpty(code)) {
            String c = code.trim();
            if (!c.startsWith("+") && c.matches("^[0-9]{1,4}$")) c = "+" + c;
            return c;
        }
        return null;
    }

    private String normalizePhone(String p) {
        if (!notEmpty(p)) return null;
        String s = p.trim();
        // Collapse multiple spaces
        s = s.replaceAll("\\s+", " ");
        return s;
    }

    // If a "name" looks like a register/roll id, drop it so we can fall back to email-derived name
    private String cleanIfRegister(String maybeName) {
        if (!notEmpty(maybeName)) return null;
        String s = maybeName.trim();
        // Heuristics: lots of digits or no spaces and long alnum cluster like '21CS1234'
        int digits = 0;
        for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) digits++;
        boolean longAlnum = s.matches("^[A-Za-z0-9\\-_/]{6,}$") && !s.contains(" ");
        if (digits >= 4 || longAlnum) return null;
        // Also ignore raw emails (we don't want email as name)
        if (s.contains("@")) return null;
        return s;
    }

    // small convenience: derive a displayable name from an email if displayName not available
    // (this returns "" if email null)
    // deriveNameFromEmail already exists above

    // ========================= END contact resolver =========================

}
