package com.example.wifibasedattendanceapplication.chatbot;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Data models for Gemini API requests and responses
 */
public class GeminiApiService {
    
    // Request models
    public static class GeminiRequest {
        @SerializedName("contents")
        public List<Content> contents;
        
        @SerializedName("generationConfig")
        public GenerationConfig generationConfig;
        
        public GeminiRequest(List<Content> contents, GenerationConfig generationConfig) {
            this.contents = contents;
            this.generationConfig = generationConfig;
        }
    }
    
    public static class Content {
        @SerializedName("parts")
        public List<Part> parts;
        
        public Content(List<Part> parts) {
            this.parts = parts;
        }
    }
    
    public static class Part {
        @SerializedName("text")
        public String text;
        
        public Part(String text) {
            this.text = text;
        }
    }
    
    public static class GenerationConfig {
        @SerializedName("temperature")
        public double temperature = 0.7;
        
        @SerializedName("topK")
        public int topK = 40;
        
        @SerializedName("topP")
        public double topP = 0.95;
        
        @SerializedName("maxOutputTokens")
        public int maxOutputTokens = 1024;
    }
    
    // Response models
    public static class GeminiResponse {
        @SerializedName("candidates")
        public List<Candidate> candidates;
        
        @SerializedName("usageMetadata")
        public UsageMetadata usageMetadata;
    }
    
    public static class Candidate {
        @SerializedName("content")
        public Content content;
        
        @SerializedName("finishReason")
        public String finishReason;
        
        @SerializedName("safetyRatings")
        public List<SafetyRating> safetyRatings;
    }
    
    public static class SafetyRating {
        @SerializedName("category")
        public String category;
        
        @SerializedName("probability")
        public String probability;
    }
    
    public static class UsageMetadata {
        @SerializedName("promptTokenCount")
        public int promptTokenCount;
        
        @SerializedName("candidatesTokenCount")
        public int candidatesTokenCount;
        
        @SerializedName("totalTokenCount")
        public int totalTokenCount;
    }
}
