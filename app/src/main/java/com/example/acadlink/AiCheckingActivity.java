package com.example.acadlink;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiCheckingActivity extends AppCompatActivity {

    String projectTitle = "", abstractText = "", methodology = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ai_checking);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageButton toolbarBackBtn = findViewById(R.id.toolbarBackBtn);
        toolbarBackBtn.setOnClickListener(v -> onBackPressed());

        projectTitle = getIntent().getStringExtra("projectTitle");
        abstractText = getIntent().getStringExtra("abstract");
        methodology = getIntent().getStringExtra("methodology");

        TableLayout tableLayout = findViewById(R.id.detailsTable);
        addRow(tableLayout, "Project Title", projectTitle);
        addRow(tableLayout, "Abstract", abstractText);
        addRow(tableLayout, "Methodology", methodology);

        TextView checkButton = findViewById(R.id.checkButton);
        checkButton.setOnClickListener(v -> {
            ProgressDialog dialog = new ProgressDialog(AiCheckingActivity.this);
            dialog.setMessage("Checking contents, please wait...");
            dialog.setCancelable(false);
            dialog.show();

            new Handler().postDelayed(() -> {
                String combinedText = (abstractText + " " + methodology).toLowerCase();

                int score = 0;

                // 1. Formal academic phrases
                String[] formalPhrases = {
                        "enhances efficiency", "streamline workflows", "structured format",
                        "seamless integration", "maximize clarity", "refined documentation",
                        "logical flow", "precise articulation", "ensures accuracy",
                        "minimizing redundancy", "maintain consistency"
                };
                for (String phrase : formalPhrases) {
                    if (combinedText.contains(phrase)) {
                        score += 10;
                        break;
                    }
                }

                // 2. Repetitive sentence structure
                String[] sentences = combinedText.split("[.!?]");
                int totalLength = 0;
                for (String s : sentences) totalLength += s.trim().split("\\s+").length;
                int avgLen = (sentences.length > 0) ? totalLength / sentences.length : 0;
                int countSimilarLength = 0;
                for (String s : sentences) {
                    int len = s.trim().split("\\s+").length;
                    if (Math.abs(len - avgLen) <= 3) countSimilarLength++;
                }
                if (sentences.length > 0 && (countSimilarLength * 100 / sentences.length) > 70) {
                    score += 10;
                }

                // 3. Lack of spelling errors (too clean)
                if (!combinedText.matches(".*\\b(thier|teh|recieve|definately|enviroment)\\b.*")) {
                    score += 10;
                }

                // 4. Passive + long sentences
                int passiveCount = 0;
                for (String s : sentences) {
                    if (s.trim().split("\\s+").length > 25 && s.contains("was")) {
                        passiveCount++;
                    }
                }
                if (passiveCount >= 2) {
                    score += 10;
                }

                // 5. Overly repeated vague academic words
                Pattern p = Pattern.compile("\\b(approach|system|solution|implementation|designed|developed)\\b");
                Matcher m = p.matcher(combinedText);
                int wordMatchCount = 0;
                while (m.find()) wordMatchCount++;
                if (wordMatchCount >= 6) score += 10;

                int aiPercent = Math.min(100, score);
                String similarity = "12%"; // placeholder
                String aiGenerated = aiPercent + "% AI-generated";

                dialog.dismiss();

                Intent intent = new Intent(AiCheckingActivity.this, ProjectReportActivity.class);
                intent.putExtra("projectTitle", projectTitle);
                intent.putExtra("similarity", similarity);
                intent.putExtra("aiGenerated", aiGenerated);
                startActivity(intent);

            }, 5000); // 5 sec delay
        });
    }

    private void addRow(TableLayout table, String label, String value) {
        TableRow row = new TableRow(this);
        row.setBackgroundColor(0xFFFFFFFF);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));

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
}
