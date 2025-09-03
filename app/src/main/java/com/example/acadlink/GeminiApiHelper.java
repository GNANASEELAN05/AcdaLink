package com.example.acadlink;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.*;

public class GeminiApiHelper {
    private static final String TAG = "GeminiApiHelper";
    private static final String API_KEY = "AIzaSyBt_26v2SNqhxyrz4MOImnlaF1BmA4cW20"; // paste your key
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    private final OkHttpClient client = new OkHttpClient();

    public interface GeminiCallback {
        void onResponse(String reply);
        void onError(String error);
    }

    public void askGemini(String userMessage, GeminiCallback callback) {
        try {
            // ✅ Correct request body format
            JSONObject textPart = new JSONObject().put("text", userMessage);
            JSONArray parts = new JSONArray().put(textPart);
            JSONObject content = new JSONObject().put("parts", parts);
            JSONArray contents = new JSONArray().put(content);

            JSONObject requestJson = new JSONObject().put("contents", contents);

            RequestBody body = RequestBody.create(
                    requestJson.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(BASE_URL + API_KEY)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        callback.onError("Error: " + response.code() + " " + response.message());
                        return;
                    }
                    try {
                        String jsonResponse = response.body().string();
                        Log.d(TAG, "Raw Response: " + jsonResponse);

                        JSONObject root = new JSONObject(jsonResponse);
                        String reply = root.getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text");

                        callback.onResponse(reply);
                    } catch (Exception e) {
                        callback.onError(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }
}
