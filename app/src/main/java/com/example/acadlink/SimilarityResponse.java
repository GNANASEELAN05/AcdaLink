package com.example.acadlink;

import com.google.gson.annotations.SerializedName;

public class SimilarityResponse {

    @SerializedName("similarity")
    private double similarity;

    public double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(double similarity) {
        this.similarity = similarity;
    }
}
