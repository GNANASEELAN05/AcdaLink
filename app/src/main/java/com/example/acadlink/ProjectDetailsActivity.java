package com.example.acadlink;

import android.content.Intent;
import android.os.Bundle;
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

public class ProjectDetailsActivity extends AppCompatActivity {

    String projectTitle, projectType1, projectType2, abstractText, methodology, fileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_project_details);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Toolbar back button
        ImageButton toolbarBackBtn = findViewById(R.id.toolbarBackBtn);
        toolbarBackBtn.setOnClickListener(v -> onBackPressed());

        TableLayout tableLayout = findViewById(R.id.detailsTable);

        // Get data from intent
        projectTitle = getIntent().getStringExtra("projectTitle");
        projectType1 = getIntent().getStringExtra("projectType1");
        projectType2 = getIntent().getStringExtra("projectType2");
        abstractText = getIntent().getStringExtra("abstract");
        methodology = getIntent().getStringExtra("methodology");
        fileName = getIntent().getStringExtra("fileName");

        // Add rows to table
        addRow(tableLayout, "Project Title", projectTitle);
        addRow(tableLayout, "Project Type", projectType1);
        addRow(tableLayout, "Project Level", projectType2);
        addRow(tableLayout, "Abstract", abstractText);
        addRow(tableLayout, "Methodology", methodology);
        addRow(tableLayout, "Chosen Project File", fileName);

        // Navigate to AI Checking Activity
        findViewById(R.id.checkAIButtonCv).setOnClickListener(v -> {
            Intent intent = new Intent(ProjectDetailsActivity.this, AiCheckingActivity.class);
            intent.putExtra("projectTitle", projectTitle);
            intent.putExtra("abstract", abstractText);
            intent.putExtra("methodology", methodology);
            startActivity(intent);
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
        valueView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 2f));
        valueView.setSingleLine(false);
        valueView.setMaxLines(Integer.MAX_VALUE);
        valueView.setLineSpacing(1.2f, 1.2f);
        row.addView(valueView);

        table.addView(row);
    }
}
