package com.example.acadlink;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.pdf.PrintedPdfDocument;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ScrollView;
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
import androidx.core.widget.NestedScrollView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MyProjectSummaryActivity extends AppCompatActivity {

    private TextView titleTv, typeTv, levelTv, abstractTv, methodologyTv, similarityTv, aiTv, filesTv;

    // intent/runtime
    private String projectId;
    private String intentTitle, intentProjectType1, intentProjectLevel, intentAbstract, intentMethodology, intentSimilarity, intentAi;
    private ArrayList<String> intentFileInfoList;

    // cache
    private static final String PREFS_NAME = "proj_temp";
    private static final String LEGACY_KEY = "current_project"; // keep for compatibility

    // cached project title for primary-folder heuristics
    private String cachedProjectTitle = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_my_project_summary);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageButton backBtn = findViewById(R.id.psBackBtn);
        if (backBtn != null) backBtn.setOnClickListener(v -> onBackPressed());

        // SAME id as AllProjectSummary (per your XML)
        ImageButton menuBtn = findViewById(R.id.toolbarMenuBtns);
        if (menuBtn != null) {
            menuBtn.setOnClickListener(this::showOverflowMenu);
        }

        titleTv = findViewById(R.id.psTitleTv);
        typeTv = findViewById(R.id.psTypeTv);
        levelTv = findViewById(R.id.psLevelTv);
        abstractTv = findViewById(R.id.psAbstractTv);
        methodologyTv = findViewById(R.id.psMethodologyTv);
        similarityTv = findViewById(R.id.psSimilarityTv);
        aiTv = findViewById(R.id.psAiTv);
        filesTv = findViewById(R.id.psFilesTv);

        // read extras
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

        // FAST: cache first (AccountFragment technique)
        loadFromPrefsAndPopulate(projectId);

        if (projectId != null && !projectId.isEmpty()) {
            fetchFromFirebase(projectId);
        } else if (hasIntentData()) {
            populateFromIntentAndCache();
        } else {
            // nothing to show
        }
    }

    // ======= Overflow menu (black bg + white text via DarkPopupMenu) =======
    private void showOverflowMenu(View anchor) {
        ContextThemeWrapper wrapper = new ContextThemeWrapper(this, R.style.DarkPopupMenu);
        PopupMenu popup = new PopupMenu(wrapper, anchor);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.menu_my_project_summary, popup.getMenu()); // export + delete
        popup.setOnMenuItemClickListener(this::onOverflowItemSelected);
        popup.show();
    }

    private boolean onOverflowItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_export_pdf) {
            exportCurrentSummaryToPdf();
            return true;
        } else if (id == R.id.action_delete_project) {
            confirmAndDeleteProject();
            return true;
        }
        return false;
    }

    // ======= Firebase fetch =======
    private void fetchFromFirebase(String id) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("projects").child(id);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    if (!snapshot.exists()) {
                        Toast.makeText(MyProjectSummaryActivity.this, "Project not found", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String title = findStringInSnapshot(snapshot, "projectTitle", "title", "name");
                    String type1 = findStringInSnapshot(snapshot, "projectType1", "projectType", "type");
                    String level = findStringInSnapshot(snapshot, "projectType2", "projectLevel", "level");
                    String abs   = findStringInSnapshot(snapshot, "abstract", "Abstract");
                    String meth  = findStringInSnapshot(snapshot, "methodology", "method");
                    String sim   = findStringInSnapshot(snapshot, "similarity");
                    String ai    = findStringInSnapshot(snapshot, "aiGenerated", "ai");

                    List<Map<String, Object>> filesList = null;
                    if (snapshot.hasChild("files")) {
                        Object fobj = snapshot.child("files").getValue();
                        if (fobj instanceof List) {
                            //noinspection unchecked
                            filesList = (List<Map<String, Object>>) fobj;
                        }
                    } else {
                        Object topVal = snapshot.getValue();
                        if (topVal instanceof Map) {
                            //noinspection unchecked
                            Map<String, Object> m = (Map<String, Object>) topVal;
                            Object candidate = findObjectInMapByKeyNames(m, new String[]{"files", "file", "attachments"});
                            if (candidate instanceof List) {
                                //noinspection unchecked
                                filesList = (List<Map<String, Object>>) candidate;
                            }
                        }
                    }

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

                    // cache entire snapshot for instant next open
                    saveSnapshotToPrefs(projectId, snapshot);
                } catch (Exception e) {
                    Log.e("MyProjectSummary", "parse error", e);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e("MyProjectSummary", "db error: " + error.getMessage());
            }
        });
    }

    private boolean hasIntentData() {
        return (notEmpty(intentTitle) || notEmpty(intentProjectType1) || notEmpty(intentProjectLevel)
                || notEmpty(intentAbstract) || notEmpty(intentMethodology) || notEmpty(intentSimilarity)
                || notEmpty(intentAi) || (intentFileInfoList != null && !intentFileInfoList.isEmpty()));
    }

    private void populateFromIntentAndCache() {
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
        if (projectId != null) {
            // also save a minimal cache so the page opens instantly next time
            try {
                JSONObject obj = new JSONObject();
                obj.put("title", firstNonEmpty(intentTitle, "N/A"));
                obj.put("projectType1", firstNonEmpty(intentProjectType1, "N/A"));
                obj.put("projectLevel", firstNonEmpty(intentProjectLevel, "N/A"));
                obj.put("abstract", firstNonEmpty(intentAbstract, "N/A"));
                obj.put("methodology", firstNonEmpty(intentMethodology, "N/A"));
                obj.put("similarity", firstNonEmpty(intentSimilarity, "N/A"));
                obj.put("aiGenerated", firstNonEmpty(intentAi, "N/A"));
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putString(keyForProject(projectId), obj.toString())
                        .apply();
            } catch (Exception ignored) {}
        }
    }

    private void populateUI(String title, String type1, String level, String abs, String meth,
                            String sim, String ai, List<Map<String, Object>> filesList) {

        // store title for primary-folder heuristics
        cachedProjectTitle = firstNonEmpty(title, "");

        titleTv.setText(firstNonEmpty(title, "N/A"));
        typeTv.setText(firstNonEmpty(type1, "N/A"));
        levelTv.setText(firstNonEmpty(level, "N/A"));
        abstractTv.setText(firstNonEmpty(abs, "N/A"));
        methodologyTv.setText(firstNonEmpty(meth, "N/A"));
        similarityTv.setText(firstNonEmpty(sim, "N/A"));
        aiTv.setText(firstNonEmpty(ai, "N/A"));

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

    // ======= Export to PDF (points, true A4; mobile-friendly fit) =======
    private void exportCurrentSummaryToPdf() {
        PrintedPdfDocument pdf = null;
        OutputStream os = null;
        try {
            final String title = firstNonEmpty(String.valueOf(titleTv.getText()), getBestProjectTitleForFileName());
            final String type1 = firstNonEmpty(String.valueOf(typeTv.getText()), "N/A");
            final String level = firstNonEmpty(String.valueOf(levelTv.getText()), "N/A");
            final String similarity = firstNonEmpty(String.valueOf(similarityTv.getText()), "N/A");
            final String ai = firstNonEmpty(String.valueOf(aiTv.getText()), "N/A");
            final String abs = firstNonEmpty(String.valueOf(abstractTv.getText()), "N/A");
            final String meth = firstNonEmpty(String.valueOf(methodologyTv.getText()), "N/A");
            final String filesBlock = firstNonEmpty(String.valueOf(filesTv.getText()), "N/A");

            // A4 page; drawing units in points
            final int dpi = 300;
            PrintAttributes attrs = new PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(new PrintAttributes.Resolution("pdf", "pdf", dpi, dpi))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build();

            // mils -> points (1 inch = 72 points; 1 inch = 1000 mils)
            final int pageW = Math.round(attrs.getMediaSize().getWidthMils()  * 72f / 1000f);  // ~595
            final int pageH = Math.round(attrs.getMediaSize().getHeightMils() * 72f / 1000f);  // ~842

            // Margins in points
            final float mL = 0.6f * 72f, mR = 0.6f * 72f, mT = 0.6f * 72f, mB = 0.6f * 72f;
            final float contentW = pageW - mL - mR;

            // Paints (sizes in points)
            Paint hSmall = new Paint(Paint.ANTI_ALIAS_FLAG); hSmall.setTextSize(10f); hSmall.setColor(0xFF666666);
            Paint hTitle = new Paint(Paint.ANTI_ALIAS_FLAG); hTitle.setTextSize(18f);
            hTitle.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); hTitle.setColor(0xFF000000);
            Paint section = new Paint(Paint.ANTI_ALIAS_FLAG); section.setTextSize(13f);
            section.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); section.setColor(0xFF000000);
            Paint body = new Paint(Paint.ANTI_ALIAS_FLAG); body.setTextSize(11f); body.setColor(0xFF000000);
            Paint label = new Paint(Paint.ANTI_ALIAS_FLAG); label.setTextSize(11f);
            label.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); label.setColor(0xFF000000);
            Paint value = new Paint(Paint.ANTI_ALIAS_FLAG); value.setTextSize(11f); value.setColor(0xFF000000);
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG); line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(0.75f); line.setColor(0xFF000000);
            Paint footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG); footerPaint.setTextSize(9f); footerPaint.setColor(0xFF666666);

            pdf = new PrintedPdfDocument(this, attrs);
            // >>> FIX: use a final reference inside the inner class
            final PrintedPdfDocument pdfDoc = pdf;

            class Drawer {
                int pageNo = 0;
                PdfDocument.Page page;
                Canvas canvas;
                float y;

                void newPage() {
                    PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(pageW, pageH, ++pageNo).create();
                    page = pdfDoc.startPage(info);        // use pdfDoc (final)
                    canvas = page.getCanvas();
                    y = mT;

                    canvas.drawText("Project Summary", mL, y, hSmall);
                    y += (hSmall.getFontMetrics().descent - hSmall.getFontMetrics().ascent) + 3f;
                    canvas.drawText(title, mL, y, hTitle);
                    y += (hTitle.getFontMetrics().descent - hTitle.getFontMetrics().ascent) + 6f;

                    canvas.drawLine(mL, y, mL + contentW, y, line);
                    y += 8f;
                }

                void ensureSpace(float needed) {
                    if (y + needed > pageH - mB) {
                        finishPageFooter();
                        pdfDoc.finishPage(page);         // use pdfDoc (final)
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

                java.util.List<String> wrap(String text, Paint p, float maxWidth) {
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

                float lineHeight(Paint p) {
                    Paint.FontMetrics fm = p.getFontMetrics();
                    return (fm.descent - fm.ascent) * 1.15f;
                }

                void drawWrappedText(String text, Paint p, float x, float maxWidth) {
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

                    canvas.drawRect(new RectF(x1, top, x1 + leftW, bottom), line);
                    canvas.drawRect(new RectF(x2, top, x2 + rightW, bottom), line);

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

            d.drawTableRow("Project Title", title, leftColW, rightColW, tablePad);
            d.drawTableRow("Type",          type1, leftColW, rightColW, tablePad);
            d.drawTableRow("Level",         level, leftColW, rightColW, tablePad);
            d.drawTableRow("Similarity",    similarity, leftColW, rightColW, tablePad);
            d.drawTableRow("AI Generated",  ai, leftColW, rightColW, tablePad);

            d.y += 12f;

            d.canvas.drawText("Abstract", mL, d.y - section.getFontMetrics().ascent, section);
            d.y += (section.getFontMetrics().descent - section.getFontMetrics().ascent) + 4f;
            d.drawWrappedText(abs, body, mL, contentW);

            d.y += 10f;

            d.canvas.drawText("Methodology", mL, d.y - section.getFontMetrics().ascent, section);
            d.y += (section.getFontMetrics().descent - section.getFontMetrics().ascent) + 4f;
            d.drawWrappedText(meth, body, mL, contentW);

            d.y += 10f;

            d.canvas.drawText("Files", mL, d.y - section.getFontMetrics().ascent, section);
            d.y += (section.getFontMetrics().descent - section.getFontMetrics().ascent) + 4f;

            if (!"N/A".equalsIgnoreCase(filesBlock.trim())) {
                String[] linesArr = filesBlock.split("\\r?\\n");
                float lh = d.lineHeight(body);
                int counter = 1;
                for (String ln : linesArr) {
                    String txt = (ln == null) ? "" : ln.trim();
                    if (txt.isEmpty()) { d.ensureSpace(lh); d.y += lh; continue; }
                    String prefix = counter + ". ";
                    float prefixW = body.measureText(prefix);
                    java.util.List<String> wrapped = d.wrap(txt, body, contentW - prefixW);

                    d.ensureSpace(lh);
                    d.canvas.drawText(prefix + wrapped.get(0), mL, d.y - body.getFontMetrics().ascent, body);
                    d.y += lh;

                    for (int i = 1; i < wrapped.size(); i++) {
                        d.ensureSpace(lh);
                        d.canvas.drawText(wrapped.get(i), mL + prefixW, d.y - body.getFontMetrics().ascent, body);
                        d.y += lh;
                    }
                    counter++;
                }
            } else {
                d.drawWrappedText("N/A", body, mL, contentW);
            }

            d.finishPageFooter();
            pdfDoc.finishPage(d.page); // use final reference

            String fileName = "Project Summary - " + sanitizeFileName(getBestProjectTitleForFileName()) + ".pdf";
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            } else {
                uri = Uri.fromFile(new java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS), fileName));
            }
            if (uri == null) throw new IllegalStateException("Storage unavailable");
            os = getContentResolver().openOutputStream(uri);
            if (os == null) throw new IllegalStateException("Output stream null");
            pdf.writeTo(os);
            Toast.makeText(this, "Saved to Downloads: " + fileName, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e("ExportPDF", "failed", e);
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            try { if (os != null) os.close(); } catch (Exception ignored) {}
            try { if (pdf != null) pdf.close(); } catch (Exception ignored) {}
        }
    }

    // ======= Delete (confirm + remove across relevant nodes) =======
    private void confirmAndDeleteProject() {
        if (projectId == null || projectId.isEmpty()) {
            Toast.makeText(this, "Project ID missing.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage("Are you sure? If you delete, it cannot be undone.")
                .setPositiveButton("OK", (d, w) -> deleteProjectEverywhere(projectId))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteProjectEverywhere(String id) {
        try {
            String uid = (FirebaseAuth.getInstance().getCurrentUser() != null)
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

            DatabaseReference root = FirebaseDatabase.getInstance().getReference();

            List<Task<Void>> tasks = new ArrayList<>();
            tasks.add(root.child("projects").child(id).removeValue());              // main
            if (uid != null) {
                tasks.add(root.child("MyProjects").child(uid).child(id).removeValue()); // typical mapping
                tasks.add(root.child("Users").child(uid).child("myProjects").child(id).removeValue()); // alternate mapping
            }
            // defensively remove from possible collections
            tasks.add(root.child("AllProjects").child(id).removeValue());
            tasks.add(root.child("downloadRequests").child(id).removeValue());

            Tasks.whenAllComplete(tasks).addOnCompleteListener(done -> {
                Toast.makeText(this, "Project deleted.", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            });
        } catch (Exception e) {
            Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ======= cache =======
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
        } catch (Exception ignored) {}
    }

    private static String keyForProject(String id) { return "proj_" + id; }

    // ======= helpers / fixes =======
    private static boolean notEmpty(String s) { return s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s.trim()); }
    private static String safeTrim(String s) { return s == null ? null : s.trim(); }

    private static String firstNonEmpty(String... vals) {
        if (vals == null) return "N/A";
        for (String s : vals) if (s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s.trim())) return s.trim();
        return "N/A";
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

    // ----- added helpers used by formatFileInfoList -----
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
        // Fallback: first folder in the list (index is 1-based)
        return index == 1 && isFolderEntry(m, name);
    }

    /**
     * Format files list:
     * - primary folder: no serial, no size
     * - other folders: no serial, no size
     * - files: numbered, include size if present
     */
    private String formatFileInfoList(List<Map<String, Object>> list) {
        try {
            StringBuilder sb = new StringBuilder();
            int serial = 1;
            boolean primaryMarked = false;
            int index = 0;

            for (Map<String, Object> m : list) {
                index++;
                if (m == null) continue;
                Object nameObj = findObjectInMapByKeyNames(m, new String[]{"name", "fileName", "title"});
                String name = nameObj == null ? null : String.valueOf(nameObj);
                if ("null".equalsIgnoreCase(name)) name = null;

                Object sizeObj = findObjectInMapByKeyNames(m, new String[]{"size", "fileSize"});
                String size = sizeObj == null ? null : String.valueOf(sizeObj);
                if ("null".equalsIgnoreCase(size)) size = null;

                boolean isFolder = isFolderEntry(m, name);
                boolean isPrimary = isFolder && isPrimaryFolderEntry(m, name, index, primaryMarked);
                if (isPrimary) primaryMarked = true;

                if (sb.length() > 0) sb.append("\n");

                String displayName = notEmpty(name) ? name.trim() : "Primary Folder";

                if (isPrimary) {
                    // Primary folder: labeled + name (no serial, no size)
                    sb.append("").append(displayName);
                } else if (isFolder) {
                    // Other folders: no serial, no size
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

    private String formatFileInfoListFallback(List<String> items) {
        // kept for completeness - not used in current flow but safe to have
        try {
            StringBuilder sb = new StringBuilder();
            int serial = 1;
            for (int i = 0; i < items.size(); i++) {
                String s = items.get(i);
                if (s == null) continue;
                if (sb.length() > 0) sb.append("\n");
                if (i == 0) {
                    sb.append(s);
                } else {
                    sb.append(serial++).append(". ").append(s);
                }
            }
            return sb.length() == 0 ? "N/A" : sb.toString();
        } catch (Exception ignored) {
            return "N/A";
        }
    }

    // ======= small helpers for PDF naming =======
    private String getBestProjectTitleForFileName() {
        String fromTv = (titleTv != null) ? String.valueOf(titleTv.getText()) : null;
        if (notEmpty(fromTv) && !"N/A".equalsIgnoreCase(fromTv.trim())) return fromTv.trim();
        if (notEmpty(cachedProjectTitle) && !"N/A".equalsIgnoreCase(cachedProjectTitle)) return cachedProjectTitle.trim();
        if (notEmpty(intentTitle) && !"N/A".equalsIgnoreCase(intentTitle)) return intentTitle.trim();
        return (projectId != null && !projectId.isEmpty()) ? projectId : "Project";
    }

    private static String sanitizeFileName(String s) {
        String cleaned = (s == null) ? "Project" : s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", " ").trim();
        if (cleaned.isEmpty()) cleaned = "Project";
        // Keep file names reasonable in length
        return cleaned.length() > 120 ? cleaned.substring(0, 120).trim() : cleaned;
    }
}
