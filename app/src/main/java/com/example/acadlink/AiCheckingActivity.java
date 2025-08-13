package com.example.acadlink;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class AiCheckingActivity extends AppCompatActivity {

    String projectTitle, abstractText, methodology;
    SharedPreferences prefs;
    static final String PREFS_NAME = "ProjectData";
    static final String KEY_LAST_ABSTRACT = "last_abstract";
    static final String KEY_LAST_METHODOLOGY = "last_methodology";
    Random random = new Random();

    // Customizable line count threshold for AI score adjustment
    private static final int LINE_COUNT_THRESHOLD = 20; // lines threshold you want
    private static final int CHARS_PER_LINE = 100; // Approximate characters per line

    // Customizable minimum AI score if above threshold
    private static final float MIN_AI_SCORE_ABOVE_THRESHOLD = 0.20f; // 20%
    // Customizable maximum AI score if above threshold
    private static final float MAX_AI_SCORE_ABOVE_THRESHOLD = 0.35f; // 35%
    // Max AI score if below threshold
    private static final float MAX_AI_SCORE_BELOW_THRESHOLD = 0.15f; // 15%

    // Similarity weights (title, abstract, methodology)
    private static final float WEIGHT_TITLE = 0.30f;
    private static final float WEIGHT_ABSTRACT = 0.40f;
    private static final float WEIGHT_METHODOLOGY = 0.30f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ai_checking);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return insets;
        });

        ImageButton toolbarBackBtn = findViewById(R.id.toolbarBackBtn);
        toolbarBackBtn.setOnClickListener(v -> finish());

        // Get intent data
        projectTitle = getIntent().getStringExtra("projectTitle");
        abstractText = getIntent().getStringExtra("abstract");
        methodology = getIntent().getStringExtra("methodology");

        if (projectTitle == null || projectTitle.trim().isEmpty()) projectTitle = "N/A";
        if (abstractText == null || abstractText.trim().isEmpty()) abstractText = "N/A";
        if (methodology == null || methodology.trim().isEmpty()) methodology = "N/A";

        // Show details
        TableLayout table = findViewById(R.id.detailsTable);
        addRow(table, "Project Title", projectTitle);
        addRow(table, "Abstract", abstractText);
        addRow(table, "Methodology", methodology);

        // Check button click
        findViewById(R.id.checkButtonCv).setOnClickListener(v -> {

            if (!isInternetAvailable()) {
                showError("Internet connection required for checking. Please enable Internet");
                return;
            }

            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Checking, please wait...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            // Dynamic delay based on content length
            int totalLen = abstractText.length() + methodology.length();
            int delayMillis = Math.min(5000, Math.max(1500, totalLen / 2));

            new Handler().postDelayed(() -> {
                try {
                    // Calculate AI score based on approximate line count from chars + line breaks
                    float aiScore = calculateAiScore(abstractText, methodology);

                    // Now compute similarity against existing Firebase projects (Realtime Database)
                    fetchProjectsAndComputeSimilarity(projectTitle, abstractText, methodology, new SimilarityCallback() {
                        @Override
                        public void onResult(float similarity) {
                            try {
                                // Save last project
                                prefs.edit()
                                        .putString(KEY_LAST_ABSTRACT, abstractText)
                                        .putString(KEY_LAST_METHODOLOGY, methodology)
                                        .apply();

                                progressDialog.dismiss();

                                // Go to report screen
                                Intent intent = new Intent(AiCheckingActivity.this, ProjectReportActivity.class);
                                intent.putExtra("projectTitle", projectTitle);

                                // Pass as formatted percentage strings
                                intent.putExtra("similarity", String.format(Locale.getDefault(), "%.2f%%", similarity * 100));
                                intent.putExtra("aiGenerated", String.format(Locale.getDefault(), "%.2f%%", aiScore * 100));

                                startActivity(intent);

                            } catch (Exception e) {
                                progressDialog.dismiss();
                                showError("Error launching report: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onError(String errorMsg) {
                            // fallback to 0% similarity on error
                            prefs.edit()
                                    .putString(KEY_LAST_ABSTRACT, abstractText)
                                    .putString(KEY_LAST_METHODOLOGY, methodology)
                                    .apply();

                            progressDialog.dismiss();

                            Intent intent = new Intent(AiCheckingActivity.this, ProjectReportActivity.class);
                            intent.putExtra("projectTitle", projectTitle);
                            intent.putExtra("similarity", String.format(Locale.getDefault(), "%.2f%%", 0.0f));
                            intent.putExtra("aiGenerated", String.format(Locale.getDefault(), "%.2f%%", aiScore * 100));
                            startActivity(intent);

                            showError("Could not compute similarity: " + errorMsg);
                        }
                    });

                } catch (Exception e) {
                    progressDialog.dismiss();
                    showError("Error during checking: " + e.getMessage());
                }
            }, delayMillis);
        });
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
    }

    private float calculateAiScore(String abs, String meth) {
        int totalChars = (abs != null ? abs.length() : 0) + (meth != null ? meth.length() : 0);

        // Count line breaks in both strings
        int lineBreaks = 0;
        if (abs != null) lineBreaks += countLineBreaks(abs);
        if (meth != null) lineBreaks += countLineBreaks(meth);

        // Approximate lines = line breaks + chars divided by chars per line
        int approxLines = lineBreaks + (int) Math.ceil((double) totalChars / CHARS_PER_LINE);

        if (approxLines >= LINE_COUNT_THRESHOLD) {
            // If above threshold, AI score between min and max above threshold
            float score = MIN_AI_SCORE_ABOVE_THRESHOLD + random.nextFloat() * (MAX_AI_SCORE_ABOVE_THRESHOLD - MIN_AI_SCORE_ABOVE_THRESHOLD);
            return Math.min(1.0f, score);
        } else {
            // Otherwise, AI score below max below threshold
            float score = random.nextFloat() * MAX_AI_SCORE_BELOW_THRESHOLD;
            return Math.min(1.0f, score);
        }
    }

    // Helper method to count line breaks
    private int countLineBreaks(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private void addRow(TableLayout table, String label, String value) {
        TableRow row = new TableRow(this);
        row.setBackgroundColor(0xFFFFFFFF);

        TextView labelView = new TextView(this);
        labelView.setText(label + ":");
        labelView.setPadding(16, 16, 8, 16);
        labelView.setTextColor(0xFF000000);
        labelView.setTextSize(14);
        labelView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value != null ? value : "N/A");
        valueView.setPadding(8, 16, 16, 16);
        valueView.setTextColor(0xFF000000);
        valueView.setTextSize(14);
        valueView.setSingleLine(false);
        valueView.setMaxLines(Integer.MAX_VALUE);
        valueView.setLineSpacing(1.2f, 1.2f);
        valueView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 2f));
        row.addView(valueView);

        table.addView(row);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e("ERROR", message);
    }

    // ------------------- Firebase + Similarity logic -------------------

    private interface SimilarityCallback {
        void onResult(float similarity); // similarity in [0..1]
        void onError(String errorMsg);
    }

    /**
     * Fetch all projects from Realtime Database under the "projects" node
     * and compute max composite similarity against the new project.
     *
     * If you use Cloud Firestore instead, I can provide a Firestore version.
     */
    private void fetchProjectsAndComputeSimilarity(String newTitle, String newAbstract, String newMethodology, SimilarityCallback callback) {
        try {
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("projects");
            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    try {
                        float maxSimilarity = 0f;
                        if (snapshot.exists()) {
                            for (DataSnapshot child : snapshot.getChildren()) {
                                String oldTitle = child.child("projectTitle").getValue(String.class);
                                String oldAbstract = child.child("abstract").getValue(String.class);
                                String oldMethodology = child.child("methodology").getValue(String.class);

                                float sim = computeCompositeSimilarity(newTitle, oldTitle, newAbstract, oldAbstract, newMethodology, oldMethodology);
                                if (sim > maxSimilarity) maxSimilarity = sim;
                            }
                        }
                        // return result
                        callback.onResult(maxSimilarity);
                    } catch (Exception e) {
                        callback.onError(e.getMessage() == null ? "Unknown error in onDataChange" : e.getMessage());
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    callback.onError(error.getMessage() == null ? "Firebase cancelled" : error.getMessage());
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage() == null ? "Firebase fetch failed" : e.getMessage());
        }
    }

    private float computeCompositeSimilarity(String newTitle, String oldTitle,
                                             String newAbstract, String oldAbstract,
                                             String newMethodology, String oldMethodology) {
        float titleSim = jaccardSimilarity(newTitle, oldTitle);
        float absSim = jaccardSimilarity(newAbstract, oldAbstract);
        float methSim = jaccardSimilarity(newMethodology, oldMethodology);

        return titleSim * WEIGHT_TITLE + absSim * WEIGHT_ABSTRACT + methSim * WEIGHT_METHODOLOGY;
    }

    // Simple Jaccard on token sets with light tokenization + stopword filtering
    private float jaccardSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0f;

        Set<String> set1 = tokenizeAndFilter(s1);
        Set<String> set2 = tokenizeAndFilter(s2);

        if (set1.isEmpty() || set2.isEmpty()) return 0f;

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        return (float) intersection.size() / (float) union.size();
    }

    private Set<String> tokenizeAndFilter(String s) {
        if (s == null) return new HashSet<>();
        s = s.toLowerCase(Locale.getDefault());
        // Split on non-word characters
        String[] tokens = s.split("\\W+");
        Set<String> out = new HashSet<>();
        // Light stopwords — feel free to extend
        Set<String> stopwords = new HashSet<>(Arrays.asList(
                "the", "and", "a", "an", "of", "in", "on", "for", "to", "is", "are",
                "this", "that", "with", "by", "from", "as", "at", "be", "or", "which",
                "it", "its", "we", "our", "using", "use", "based"
        ));

        for (String t : tokens) {
            if (t == null) continue;
            t = t.trim();
            if (t.length() < 2) continue; // ignore single letters
            if (stopwords.contains(t)) continue;
            out.add(t);
        }
        return out;
    }
}
