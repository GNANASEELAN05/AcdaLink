package com.example.acadlink;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AiDetectionResponse {

    @SerializedName("prediction")
    private List<PredictionItem> prediction;

    public List<PredictionItem> getPrediction() {
        return prediction;
    }

    public double getPredictionScore() {
        if (prediction != null && !prediction.isEmpty()) {
            return prediction.get(0).getScore();
        }
        return 0.0;
    }

    public String getPredictionLabel() {
        if (prediction != null && !prediction.isEmpty()) {
            return prediction.get(0).getLabel();
        }
        return "Unknown";
    }

    public static class PredictionItem {
        @SerializedName("label")
        private String label;

        @SerializedName("score")
        private double score;

        public String getLabel() {
            return label;
        }

        public double getScore() {
            return score;
        }
    }
}
