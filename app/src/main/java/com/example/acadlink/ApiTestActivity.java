package com.example.acadlink;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiTestActivity extends AppCompatActivity {

    ApiService apiService;
    Button testButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_test);

        testButton = findViewById(R.id.testApiBtn); // Ensure this ID matches your XML

        // Initialize Retrofit API service
        apiService = RetrofitClient.getClient().create(ApiService.class);

        testButton.setOnClickListener(v -> {
            // ✅ SIMILARITY CHECK
            Map<String, String> similarityBody = new HashMap<>();
            similarityBody.put("text1", "The sun is bright.");
            similarityBody.put("text2", "It is a sunny day.");

            Call<SimilarityResponse> similarityCall = apiService.checkSimilarity(similarityBody);
            similarityCall.enqueue(new Callback<SimilarityResponse>() {
                @Override
                public void onResponse(Call<SimilarityResponse> call, Response<SimilarityResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        double similarityScore = response.body().getSimilarity();
                        Log.d("Similarity API", "Similarity Score: " + similarityScore);
                    } else {
                        Log.e("Similarity API", "Response error: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<SimilarityResponse> call, Throwable t) {
                    Log.e("Similarity API", "Failure: " + t.getMessage());
                }
            });

            // ✅ AI-GENERATED DETECTION
            Map<String, String> aiBody = new HashMap<>();
            aiBody.put("text", "The mitochondria is the powerhouse of the cell.");

            Call<AiDetectionResponse> aiCall = apiService.detectAiContent(aiBody);
            aiCall.enqueue(new Callback<AiDetectionResponse>() {
                @Override
                public void onResponse(Call<AiDetectionResponse> call, Response<AiDetectionResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        double aiScore = response.body().getPredictionScore();
                        String aiLabel = response.body().getPredictionLabel();
                        Log.d("AI Detection API", "Label: " + aiLabel + ", Score: " + aiScore);
                    } else {
                        Log.e("AI Detection API", "Response error: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<AiDetectionResponse> call, Throwable t) {
                    Log.e("AI Detection API", "Failure: " + t.getMessage());
                }
            });
        });
    }
}
