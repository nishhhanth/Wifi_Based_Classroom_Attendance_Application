package com.example.wifibasedattendanceapplication.chatbot;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

/**
 * Service for communicating with Gemini API
 */
public class GeminiService {
    
    private static final String TAG = "GeminiService";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/";
    private static final String MODEL_NAME = "gemini-2.0-flash-exp:generateContent";
    
    private final GeminiApiInterface apiInterface;
    private final String apiKey;
    
    public interface GeminiApiInterface {
        @POST("models/" + MODEL_NAME)
        Call<GeminiApiService.GeminiResponse> generateContent(
            @Header("Content-Type") String contentType,
            @Header("x-goog-api-key") String apiKey,
            @Body GeminiApiService.GeminiRequest request
        );
    }
    
    public interface ChatCallback {
        void onSuccess(String response);
        void onError(String error);
    }
    
    public GeminiService(String apiKey) {
        this.apiKey = apiKey;
        
        // Create OkHttp client with logging
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
        // Create Gson instance
        Gson gson = new GsonBuilder()
                .setLenient()
                .create();
        
        // Create Retrofit instance
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        
        this.apiInterface = retrofit.create(GeminiApiInterface.class);
    }
    
    public void sendMessage(String userMessage, String context, ChatCallback callback) {
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("API key not configured");
            return;
        }
        
        // Build the prompt with context
        String fullPrompt = buildPrompt(userMessage, context);
        
        // Create request
        List<GeminiApiService.Part> parts = new ArrayList<>();
        parts.add(new GeminiApiService.Part(fullPrompt));
        
        List<GeminiApiService.Content> contents = new ArrayList<>();
        contents.add(new GeminiApiService.Content(parts));
        
        GeminiApiService.GenerationConfig config = new GeminiApiService.GenerationConfig();
        config.temperature = 0.7;
        config.maxOutputTokens = 1024;
        
        GeminiApiService.GeminiRequest request = new GeminiApiService.GeminiRequest(contents, config);
        
        // Make API call
        Call<GeminiApiService.GeminiResponse> call = apiInterface.generateContent(
            "application/json",
            apiKey,
            request
        );
        
        call.enqueue(new Callback<GeminiApiService.GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiApiService.GeminiResponse> call, Response<GeminiApiService.GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    GeminiApiService.GeminiResponse geminiResponse = response.body();
                    
                    if (geminiResponse.candidates != null && !geminiResponse.candidates.isEmpty()) {
                        GeminiApiService.Candidate candidate = geminiResponse.candidates.get(0);
                        
                        if (candidate.content != null && 
                            candidate.content.parts != null && 
                            !candidate.content.parts.isEmpty()) {
                            
                            String responseText = candidate.content.parts.get(0).text;
                            Log.d(TAG, "Gemini response: " + responseText);
                            callback.onSuccess(responseText);
                        } else {
                            callback.onError("Empty response from Gemini");
                        }
                    } else {
                        callback.onError("No candidates in response");
                    }
                } else {
                    String errorMessage = "API Error: " + response.code();
                    try {
                        if (response.errorBody() != null) {
                            errorMessage += " - " + response.errorBody().string();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading error body", e);
                    }
                    Log.e(TAG, errorMessage);
                    callback.onError(errorMessage);
                }
            }
            
            @Override
            public void onFailure(Call<GeminiApiService.GeminiResponse> call, Throwable t) {
                String errorMessage = "Network error: " + t.getMessage();
                Log.e(TAG, errorMessage, t);
                callback.onError(errorMessage);
            }
        });
    }
    
    private String buildPrompt(String userMessage, String context) {
        StringBuilder prompt = new StringBuilder();
        
        // System prompt
        prompt.append("You are an AI assistant for a WiFi-based attendance management app. ");
        prompt.append("You help students with attendance, fees, calendar, and general app-related questions. ");
        prompt.append("Be helpful, friendly, and provide specific data when available. ");
        prompt.append("Always prioritize giving actual data over instructions when the data is available in the context.\n\n");
        
        // Add context if available
        if (context != null && !context.isEmpty()) {
            prompt.append("Student Context:\n");
            prompt.append(context);
            prompt.append("\n\n");
        }
        
        // Add specific instructions based on query type
        String lowerMessage = userMessage.toLowerCase();
        
        if (lowerMessage.contains("holiday") || lowerMessage.contains("calendar") || lowerMessage.contains("date")) {
            prompt.append("IMPORTANT: The user is asking about calendar/holidays. ");
            prompt.append("If you have holiday data in the context above, provide the specific dates and names. ");
            prompt.append("Do NOT give instructions to go to the app - give the actual data from the context.\n\n");
        }
        
        if (lowerMessage.contains("attendance") || lowerMessage.contains("present") || lowerMessage.contains("absent") || 
            lowerMessage.contains("class") || lowerMessage.contains("75%") || lowerMessage.contains("percentage")) {
            prompt.append("IMPORTANT: The user is asking about attendance. ");
            prompt.append("If you have attendance data in the context above, provide the specific percentages, records, and calculations. ");
            prompt.append("For questions about reaching 75% attendance, use the 'Classes needed for 75%' data from the context. ");
            prompt.append("For questions about specific dates, use the 'Recent Daily Attendance' data from the context. ");
            prompt.append("Do NOT give instructions to go to the app - give the actual data from the context.\n\n");
        }
        
        if (lowerMessage.contains("fees") || lowerMessage.contains("payment") || lowerMessage.contains("due")) {
            prompt.append("IMPORTANT: The user is asking about fees. ");
            prompt.append("If you have fees data in the context above, provide the specific amounts, status, and due dates. ");
            prompt.append("Do NOT give instructions to go to the app - give the actual data from the context.\n\n");
        }
        
        // Add response guidelines
        prompt.append("RESPONSE GUIDELINES:\n");
        prompt.append("- Always provide actual data from the context when available\n");
        prompt.append("- Be specific and direct with numbers, dates, and percentages\n");
        prompt.append("- Only give instructions if the data is not available in the context\n");
        prompt.append("- Use a friendly and helpful tone\n");
        prompt.append("- Format dates as 'DD MMM YYYY' (e.g., '15 Oct 2024')\n");
        prompt.append("- Format percentages with one decimal place (e.g., '85.5%')\n");
        prompt.append("- When calculating attendance, show the math: (present/total) * 100\n");
        prompt.append("- For 'classes needed' questions, use the pre-calculated values from context\n\n");
        
        // Add user message
        prompt.append("Student Question: ");
        prompt.append(userMessage);
        
        return prompt.toString();
    }
}
