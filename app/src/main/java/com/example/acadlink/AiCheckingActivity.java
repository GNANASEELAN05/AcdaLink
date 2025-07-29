package com.example.acadlink;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
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

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AiCheckingActivity extends AppCompatActivity {

    String projectTitle, abstractText, methodology;
    ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ai_checking);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return insets;
        });

        ImageButton toolbarBackBtn = findViewById(R.id.toolbarBackBtn);
        toolbarBackBtn.setOnClickListener(v -> finish());

        // Get intent data with fallback values
        projectTitle = getIntent().getStringExtra("projectTitle");
        abstractText = getIntent().getStringExtra("abstract");
        methodology = getIntent().getStringExtra("methodology");

        if (projectTitle == null || projectTitle.trim().isEmpty()) projectTitle = "N/A";
        if (abstractText == null || abstractText.trim().isEmpty()) abstractText = "N/A";
        if (methodology == null || methodology.trim().isEmpty()) methodology = "N/A";

        // Add to table layout
        TableLayout table = findViewById(R.id.detailsTable);
        addRow(table, "Project Title", projectTitle);
        addRow(table, "Abstract", abstractText);
        addRow(table, "Methodology", methodology);

        // Init Retrofit service
        apiService = RetrofitClient.getClient().create(ApiService.class);

        // Button click listener
        findViewById(R.id.checkButtonCv).setOnClickListener(v -> {
            ProgressDialog d = new ProgressDialog(this);
            d.setMessage("Checking, please wait...");
            d.setCancelable(false);
            d.show();

            // Upload project
            Map<String, String> uploadRequest = new HashMap<>();
            uploadRequest.put("title", projectTitle);
            uploadRequest.put("abstract", abstractText);
            uploadRequest.put("methodology", methodology);

            apiService.uploadProject(uploadRequest).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {

                        // Prepare similarity request
                        Map<String, String> similarityRequest = new HashMap<>();
                        similarityRequest.put("abstract", abstractText);
                        similarityRequest.put("methodology", methodology);

                        Log.d("SIMILARITY", "text1: " + abstractText);
                        Log.d("SIMILARITY", "text2: " + methodology);

                        apiService.checkSimilarity(similarityRequest).enqueue(new Callback<SimilarityResponse>() {
                            @Override
                            public void onResponse(Call<SimilarityResponse> call, Response<SimilarityResponse> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    double similarityScore = response.body().getSimilarity();

                                    // ✅ FIXED: Send separate fields instead of "text"
                                    Map<String, String> aiRequest = new HashMap<>();
                                    aiRequest.put("abstract", abstractText);
                                    aiRequest.put("methodology", methodology);

                                    Log.d("AI_DETECTION", "Abstract: " + abstractText);
                                    Log.d("AI_DETECTION", "Methodology: " + methodology);

                                    apiService.detectAiContent(aiRequest).enqueue(new Callback<AiDetectionResponse>() {
                                        @Override
                                        public void onResponse(Call<AiDetectionResponse> call, Response<AiDetectionResponse> response2) {
                                            d.dismiss();
                                            if (response2.isSuccessful() && response2.body() != null) {
                                                double aiScore = response2.body().getPredictionScore();

                                                Intent i = new Intent(AiCheckingActivity.this, ProjectReportActivity.class);
                                                i.putExtra("projectTitle", projectTitle);
                                                i.putExtra("similarity", String.format("%.1f%%", similarityScore * 100));
                                                i.putExtra("aiGenerated", String.format("%.1f%% AI-generated", aiScore * 100));
                                                startActivity(i);
                                            } else {
                                                showError("AI Detection failed: " + response2.code());
                                            }
                                        }

                                        @Override
                                        public void onFailure(Call<AiDetectionResponse> call, Throwable t) {
                                            d.dismiss();
                                            showError("AI Detection error: " + t.getMessage());
                                        }
                                    });

                                } else {
                                    d.dismiss();
                                    showError("Similarity check failed: " + response.code());
                                }
                            }

                            @Override
                            public void onFailure(Call<SimilarityResponse> call, Throwable t) {
                                d.dismiss();
                                showError("Similarity check error: " + t.getMessage());
                            }
                        });

                    } else {
                        d.dismiss();
                        showError("Upload failed: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    d.dismiss();
                    showError("Upload error: " + t.getMessage());
                }
            });
        });
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
}
