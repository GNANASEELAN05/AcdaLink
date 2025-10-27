package com.example.acadlink;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/**
 * Summary Activity — shows project details summary in table form.
 *
 * Modifications:
 *  - Highlighting for Abstract & Methodology now matches ProjectReportActivity:
 *      - It fetches previous projects from "projects" node, tokenizes them, compares
 *        lines, and applies line-based background highlighting for similar lines.
 *      - Also applies AI-based random-line highlighting based on AI percentage.
 *  - Selected Files row now shows numbered files; if a Primary file exists,
 *    it is listed first without a serial number.
 *  - Kept all other logic and behavior unchanged.
 */
public class Summary extends AppCompatActivity {

    private static final String TAG = "Summary";
    private static final double HIGHLIGHT_THRESHOLD = 15.0;

    private TableLayout table;
    private NestedScrollView mainScroll;

    private FirebaseAuth auth;
    private DatabaseReference archiveRef;
    private DatabaseReference projectsRef;

    private static final String PREFS = "FacultyPrefs";
    private static final String KEY_LOGGED_IN = "isFacultyLoggedIn";
    private static final String KEY_EMAIL = "facultyEmail";

    private static final int COLOR_LIGHT_RED = 0xFFFFCDD2;
    private static final int COLOR_LIGHT_YELLOW = 0xFFFFD700;

    private final Random rnd = new Random();
    private String projectId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);

        auth = FirebaseAuth.getInstance();
        archiveRef = FirebaseDatabase.getInstance().getReference("requestArchives");
        projectsRef = FirebaseDatabase.getInstance().getReference("projects");

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageButton toolbarBackBtn = findViewById(R.id.toolbarBackBtn);
        toolbarBackBtn.setOnClickListener(v -> finish());

        mainScroll = findViewById(R.id.summaryScroll);
        table = findViewById(R.id.summaryTable);

        try {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Color.parseColor("#FFFFFF"));
            gd.setStroke(Math.max(1, (int) (getResources().getDisplayMetrics().density)),
                    Color.parseColor("#DDDDDD"));
            table.setBackground(gd);
            int pad = Math.max(1, (int) (getResources().getDisplayMetrics().density));
            table.setPadding(pad, pad, pad, pad);
        } catch (Exception ignored) {}

        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        projectId = intent.getStringExtra("projectId");
        String title = safe(intent.getStringExtra("projectTitle"));
        String type1 = safe(intent.getStringExtra("projectType1"));
        String type2 = safe(intent.getStringExtra("projectType2"));
        String similarityRaw = safe(intent.getStringExtra("similarity"));
        String aiRaw = safe(intent.getStringExtra("aiGenerated"));
        String absText = safe(intent.getStringExtra("abstract"));
        String methText = safe(intent.getStringExtra("methodology"));
        ArrayList<String> fileInfoList = intent.getStringArrayListExtra("fileInfoList");
        ArrayList<String> fileUriList = intent.getStringArrayListExtra("fileUriList");

        double simVal = parsePercent(similarityRaw);
        double aiVal = parsePercent(aiRaw);

        addRow(table, "Project Title", title);
        addRow(table, "Project Type", type1);
        addRow(table, "Project Level", type2);
        addRowWithPercentColor(table, "Similarity", similarityRaw, simVal);
        addRowWithPercentColor(table, "AI Generated", aiRaw, aiVal);

        if ((simVal > HIGHLIGHT_THRESHOLD) || (aiVal > HIGHLIGHT_THRESHOLD)) {
            fetchPreviousProjectsAndHighlight(table, (float) aiVal, absText, methText, simVal);
        } else {
            addRow(table, "Abstract", absText);
            addRow(table, "Methodology", methText);
        }

        // ✅ Selected Files row — after Methodology, numbered + primary file first
        addFileListRow(table, "Selected File(s)", fileInfoList, fileUriList);

        MaterialButton btnReject = findViewById(R.id.btnReject);
        MaterialButton btnAccept = findViewById(R.id.btnAccept);

        if (projectId != null && !projectId.trim().isEmpty()) {
            archiveRef.child(projectId).child("status")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                String status = String.valueOf(snapshot.getValue());
                                if (status != null) status = status.trim().toLowerCase(Locale.ROOT);

                                if ("accepted".equals(status) || "rejected".equals(status)) {
                                    btnAccept.setEnabled(false);
                                    btnReject.setEnabled(false);
                                    btnAccept.setAlpha(0.5f);
                                    btnReject.setAlpha(0.5f);
                                } else {
                                    btnAccept.setEnabled(true);
                                    btnReject.setEnabled(true);
                                    btnAccept.setAlpha(1f);
                                    btnReject.setAlpha(1f);
                                }
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            Log.w(TAG, "Status check failed: " + error.getMessage());
                        }
                    });
        }

        btnReject.setOnClickListener(v -> handleDecision(false, btnAccept, btnReject));
        btnAccept.setOnClickListener(v -> handleDecision(true, btnAccept, btnReject));
    }

    private void fetchPreviousProjectsAndHighlight(final TableLayout tableLayout, final float aiPct,
                                                   final String absText, final String methText,
                                                   final double simVal) {
        try {
            projectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    List<Set<String>> previousProjectTokenSets = new ArrayList<>();

                    for (DataSnapshot child : snapshot.getChildren()) {
                        try {
                            String abs = "";
                            String meth = "";
                            Object aObj = child.child("abstract").getValue();
                            Object mObj = child.child("methodology").getValue();
                            if (aObj != null) abs = aObj.toString();
                            if (mObj != null) meth = mObj.toString();

                            String combined = (abs == null ? "" : abs) + " " + (meth == null ? "" : meth);
                            Set<String> tokens = tokenizeAndFilter(combined);
                            if (!tokens.isEmpty()) previousProjectTokenSets.add(tokens);
                        } catch (Exception ignored) {}
                    }

                    CharSequence highlightedAbstract = createHighlightedSpannableForFieldUsingProjects(
                            absText != null ? absText : "N/A",
                            previousProjectTokenSets,
                            true,
                            aiPct);

                    CharSequence highlightedMethod = createHighlightedSpannableForFieldUsingProjects(
                            methText != null ? methText : "N/A",
                            previousProjectTokenSets,
                            false,
                            aiPct);

                    addRowWithSpannable(tableLayout, "Abstract", highlightedAbstract);
                    addRowWithSpannable(tableLayout, "Methodology", highlightedMethod);
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    CharSequence safeAbs = absText != null ? absText : "N/A";
                    CharSequence safeMeth = methText != null ? methText : "N/A";
                    addRowWithSpannable(tableLayout, "Abstract", safeAbs);
                    addRowWithSpannable(tableLayout, "Methodology", safeMeth);
                }
            });
        } catch (Exception e) {
            addRowWithSpannable(tableLayout, "Abstract", absText != null ? absText : "N/A");
            addRowWithSpannable(tableLayout, "Methodology", methText != null ? methText : "N/A");
            Log.w(TAG, "fetchPreviousProjectsAndHighlight error: " + e.getMessage());
        }
    }

    private void handleDecision(boolean isAccept, MaterialButton btnAccept, MaterialButton btnReject) {
        if (projectId == null || projectId.trim().isEmpty()) {
            Toast.makeText(this, "Invalid project id", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            btnAccept.setEnabled(false);
            btnReject.setEnabled(false);
            btnAccept.setAlpha(0.6f);
            btnReject.setAlpha(0.6f);

            String status = isAccept ? "accepted" : "rejected";

            Map<String, Object> updates = new HashMap<>();
            updates.put("status", status);

            FirebaseUser user = auth.getCurrentUser();
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            Map<String, Object> facultyApproval = new HashMap<>();
            facultyApproval.put("status", status);
            facultyApproval.put("reviewedAt", System.currentTimeMillis());
            if (user != null) {
                facultyApproval.put("reviewedBy", user.getUid());
                facultyApproval.put("reviewedByEmail", user.getEmail());
            } else if (prefs.getBoolean(KEY_LOGGED_IN, false)) {
                facultyApproval.put("reviewedBy", "faculty_local");
                facultyApproval.put("reviewedByEmail", prefs.getString(KEY_EMAIL, ""));
            }

            updates.put("facultyApproval", facultyApproval);
            if (isAccept) updates.put("acceptedAt", System.currentTimeMillis());
            else updates.put("rejectedAt", System.currentTimeMillis());

            archiveRef.child(projectId).updateChildren(updates).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    String capitalized = status.substring(0,1).toUpperCase(Locale.ROOT) + status.substring(1).toLowerCase(Locale.ROOT);
                    Toast.makeText(this, "Request " + capitalized, Toast.LENGTH_SHORT).show();
                    btnAccept.setEnabled(false);
                    btnReject.setEnabled(false);
                    btnAccept.setAlpha(0.5f);
                    btnReject.setAlpha(0.5f);
                } else {
                    btnAccept.setEnabled(true);
                    btnReject.setEnabled(true);
                    btnAccept.setAlpha(1f);
                    btnReject.setAlpha(1f);
                    Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "decision error: " + e.getMessage());
            btnAccept.setEnabled(true);
            btnReject.setEnabled(true);
            btnAccept.setAlpha(1f);
            btnReject.setAlpha(1f);
            Toast.makeText(this, "Error while updating status", Toast.LENGTH_SHORT).show();
        }
    }

    private void addRow(TableLayout table, String label, String value) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));

        TextView labelView = new TextView(this);
        labelView.setText(label + ":");
        labelView.setPadding(dp(16), dp(8), dp(8), dp(8));
        labelView.setTextColor(0xFF212121);
        labelView.setTextSize(14f);
        row.addView(labelView, new TableRow.LayoutParams(0,
                TableRow.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setText(value == null || value.isEmpty() ? "N/A" : value);
        valueView.setPadding(dp(8), dp(8), dp(16), dp(8));
        valueView.setTextColor(0xFF424242);
        valueView.setTextSize(14f);
        valueView.setSingleLine(false);
        valueView.setMaxLines(Integer.MAX_VALUE);
        row.addView(valueView, new TableRow.LayoutParams(0,
                TableRow.LayoutParams.WRAP_CONTENT, 2f));

        table.addView(row);
    }

    private void addRowWithPercentColor(TableLayout table, String label,
                                        String rawValue, double numericValue) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));

        TextView labelView = new TextView(this);
        labelView.setText(label + ":");
        labelView.setPadding(dp(16), dp(8), dp(8), dp(8));
        labelView.setTextColor(0xFF212121);
        labelView.setTextSize(14f);
        row.addView(labelView, new TableRow.LayoutParams(0,
                TableRow.LayoutParams.WRAP_CONTENT, 1f));

        String display = rawValue == null || rawValue.isEmpty() ? "N/A" : rawValue;
        TextView valueView = new TextView(this);
        valueView.setPadding(dp(8), dp(8), dp(16), dp(8));
        valueView.setTextSize(14f);
        valueView.setSingleLine(false);
        valueView.setMaxLines(Integer.MAX_VALUE);

        if (display.contains("%")) {
            int percentIndex = display.indexOf('%');
            int startNum = percentIndex - 1;
            while (startNum >= 0 && (Character.isDigit(display.charAt(startNum))
                    || display.charAt(startNum) == '.')) startNum--;
            startNum++;
            android.text.SpannableString ss = new android.text.SpannableString(display);
            int color = numericValue > HIGHLIGHT_THRESHOLD ? Color.RED : Color.parseColor("#388E3C");
            ss.setSpan(new android.text.style.ForegroundColorSpan(color),
                    startNum, percentIndex + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            valueView.setText(ss);
        } else {
            valueView.setText(display);
            valueView.setTextColor(0xFF424242);
        }

        row.addView(valueView, new TableRow.LayoutParams(0,
                TableRow.LayoutParams.WRAP_CONTENT, 2f));
        table.addView(row);
    }

    /** ✅ Modified: Adds file list row with numbering and primary file handling */
    private void addFileListRow(TableLayout table, String label,
                                ArrayList<String> fileInfoList,
                                ArrayList<String> fileUriList) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));

        TextView labelView = new TextView(this);
        labelView.setText(label + ":");
        labelView.setPadding(dp(16), dp(8), dp(8), dp(8));
        labelView.setTextColor(0xFF212121);
        labelView.setTextSize(14f);
        row.addView(labelView, new TableRow.LayoutParams(0,
                TableRow.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setPadding(dp(8), dp(8), dp(16), dp(8));
        valueView.setTextColor(0xFF424242);
        valueView.setTextSize(14f);
        valueView.setSingleLine(false);
        valueView.setMaxLines(Integer.MAX_VALUE);

        if (fileInfoList != null && !fileInfoList.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            // Separate primary and normal files
            List<String> normalFiles = new ArrayList<>();
            String primaryFile = null;

            for (String file : fileInfoList) {
                if (file != null && file.toLowerCase(Locale.ROOT).contains("primary")) {
                    primaryFile = file;
                } else {
                    normalFiles.add(file);
                }
            }

            // Add primary file (no serial number)
            if (primaryFile != null) {
                String name = extractFileName(primaryFile);
                sb.append(name).append("\n");
            }

            // Add remaining files with numbering
            for (int i = 0; i < normalFiles.size(); i++) {
                String fileName = extractFileName(normalFiles.get(i));
                sb.append((i + 1)).append(". ").append(fileName);
                if (i < normalFiles.size() - 1) sb.append("\n");
            }

            valueView.setText(sb.toString());
        } else {
            valueView.setText("No file selected");
        }

        if (fileUriList != null && !fileUriList.isEmpty()) {
            ArrayList<String> uris = new ArrayList<>(fileUriList);
            valueView.setOnClickListener(v -> openUriSafely(uris.get(0)));
        }

        row.addView(valueView, new TableRow.LayoutParams(0,
                TableRow.LayoutParams.WRAP_CONTENT, 2f));
        table.addView(row);
    }

    private String extractFileName(String path) {
        if (path == null || path.isEmpty()) return "Unnamed File";
        String fileName = path;
        if (fileName.contains("/") || fileName.contains("\\")) {
            fileName = new File(fileName).getName();
        } else {
            int idx = fileName.indexOf(" (");
            if (idx != -1) fileName = fileName.substring(0, idx);
        }
        return fileName;
    }

    private CharSequence createHighlightedSpannableForFieldUsingProjects(String text,
                                                                         List<Set<String>> previousProjectTokenSets,
                                                                         boolean isAbstract,
                                                                         float aiPercent) {
        if (text == null || text.trim().isEmpty()) return "N/A";
        String[] lines = splitToLines(text);
        Set<Integer> similarityIndices = new HashSet<>();

        for (int i = 0; i < lines.length; ++i) {
            String ln = lines[i];
            if (ln == null || ln.trim().isEmpty()) continue;
            Set<String> lineTokens = tokenizeAndFilter(ln);
            if (lineTokens.isEmpty()) continue;

            boolean isSimilar = false;
            for (Set<String> projTokens : previousProjectTokenSets) {
                if (projTokens == null || projTokens.isEmpty()) continue;
                Set<String> tmp = new HashSet<>(lineTokens);
                tmp.retainAll(projTokens);
                int intersection = tmp.size();
                int threshold = Math.max(5, (int) Math.ceil(lineTokens.size() * 0.3));
                if (lineTokens.size() >= 6) threshold = Math.max(threshold, 3);
                if (intersection >= threshold) {
                    isSimilar = true;
                    break;
                }
            }
            if (isSimilar) similarityIndices.add(i);
        }

        Set<Integer> aiRandomIndices = new HashSet<>();
        if (aiPercent > HIGHLIGHT_THRESHOLD) {
            int totalLines = Math.max(1, lines.length);
            float above = (aiPercent - 12f) / 100f;
            if (above < 0f) above = 0f;
            if (above > 0.7f) above = 0.7f;
            float minPortion = 0.10f;
            int toPick = Math.max(1, Math.round(totalLines * Math.max(minPortion, above)));
            int attempts = 0;
            while (aiRandomIndices.size() < toPick && attempts < totalLines * 4) {
                int pick = rnd.nextInt(totalLines);
                if (similarityIndices.contains(pick)) { attempts++; continue; }
                String ln = lines[pick];
                if (ln == null || ln.trim().isEmpty()) { attempts++; continue; }
                aiRandomIndices.add(pick);
                attempts++;
            }
        }

        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        for (int i = 0; i < lines.length; ++i) {
            String ln = lines[i];
            int start = sb.length();
            sb.append(ln == null ? "" : ln);
            int end = sb.length();
            if (i != lines.length - 1) sb.append("\n");
            if (!ln.trim().isEmpty()) {
                if (similarityIndices.contains(i)) {
                    sb.setSpan(new android.text.style.BackgroundColorSpan(COLOR_LIGHT_RED), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (aiRandomIndices.contains(i)) {
                    sb.setSpan(new android.text.style.BackgroundColorSpan(COLOR_LIGHT_YELLOW), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        return sb;
    }

    private String[] splitToLines(String text) {
        if (text == null) return new String[]{""};
        if (text.contains("\r") || text.contains("\n")) {
            return text.split("\\r?\\n");
        }
        String[] sentences = text.split("(?<=[.!?])\\s+");
        if (sentences.length == 0) return new String[]{text};
        return sentences;
    }

    private Set<String> tokenizeAndFilter(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        s = s.toLowerCase(Locale.getDefault());
        String[] tokens = s.split("\\W+");
        Set<String> stopwords = new HashSet<>(Arrays.asList(
                "the", "and", "a", "an", "of", "in", "on", "for", "to", "is", "are",
                "this", "that", "with", "by", "from", "as", "at", "be", "or", "which",
                "it", "its", "we", "our", "using", "use", "based", "these", "those", "have", "has", "were", "was"
        ));
        for (String t : tokens) {
            if (t == null) continue;
            t = t.trim();
            if (t.length() < 2) continue;
            if (stopwords.contains(t)) continue;
            out.add(t);
        }
        return out;
    }

    private void openUriSafely(String uriString) {
        try {
            if (uriString == null || uriString.trim().isEmpty()) {
                Toast.makeText(this, "Invalid file link", Toast.LENGTH_SHORT).show();
                return;
            }
            Uri uri = Uri.parse(uriString);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException ae) {
            Toast.makeText(this, "No application found to open this file", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open file", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "openUriSafely error: " + e.getMessage());
        }
    }

    private double parsePercent(String raw) {
        if (raw == null) return 0.0;
        String s = raw.trim();
        if (s.isEmpty()) return 0.0;
        try {
            if (s.endsWith("%")) s = s.substring(0, s.length() - 1).trim();
            double v = Double.parseDouble(s);
            if (v > 0 && v <= 1.0) return v * 100.0;
            if (v >= 0 && v <= 100) return v;
        } catch (Exception ignored) {}
        return 0.0;
    }

    private String safe(@Nullable String s) {
        return (s == null) ? "" : s.trim();
    }

    private int dp(int v) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(v * density);
    }

    private void addRowWithSpannable(TableLayout table, String label, CharSequence value) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));

        TextView labelView = new TextView(this);
        labelView.setText(label + ":");
        labelView.setPadding(dp(16), dp(8), dp(8), dp(8));
        labelView.setTextColor(0xFF212121);
        labelView.setTextSize(14f);
        row.addView(labelView, new TableRow.LayoutParams(
                0, TableRow.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setText(value == null ? "N/A" : value);
        valueView.setPadding(dp(8), dp(8), dp(16), dp(8));
        valueView.setTextColor(0xFF424242);
        valueView.setTextSize(14f);
        valueView.setSingleLine(false);
        valueView.setMaxLines(Integer.MAX_VALUE);

        row.addView(valueView, new TableRow.LayoutParams(
                0, TableRow.LayoutParams.WRAP_CONTENT, 2f));

        table.addView(row);
    }
}
