package com.example.acadlink;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ProjectReportActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_project_report);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Handle back button
        ImageButton toolbarBackBtn = findViewById(R.id.toolbarBackBtn);
        toolbarBackBtn.setOnClickListener(v -> onBackPressed());

        // Get data from intent
        String projectTitle = getIntent().getStringExtra("projectTitle");
        String similarity = getIntent().getStringExtra("similarity");
        String aiGenerated = getIntent().getStringExtra("aiGenerated");

        // Populate table with received data
        TableLayout tableLayout = findViewById(R.id.projectReportTable);
        addRow(tableLayout, "Project Title", projectTitle);
        addRow(tableLayout, "Similarity", similarity);
        addRow(tableLayout, "AI Generated", aiGenerated);
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
