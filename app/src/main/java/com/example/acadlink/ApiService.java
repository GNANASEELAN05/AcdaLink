package com.example.acadlink;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface ApiService {

    @Headers("Content-Type: application/json")
    @POST("/check-similarity")
    Call<SimilarityResponse> checkSimilarity(@Body Map<String, String> body);

    @Headers("Content-Type: application/json")
    @POST("/check-ai")
    Call<AiDetectionResponse> detectAiContent(@Body Map<String, String> body);

    @Headers("Content-Type: application/json")
    @POST("/upload-project")
    Call<Void> uploadProject(@Body Map<String, String> body); // 👈 NEW ENDPOINT
}
