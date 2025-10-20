package com.example.wifibasedattendanceapplication.chatbot;

import com.example.wifibasedattendanceapplication.BuildConfig;

public class ChatConfig {

    public static final String GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY;
    
    // API Configuration
    public static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/";
    public static final String GEMINI_MODEL = "gemini-2.0-flash-exp:generateContent";
    
    // Chat Configuration
    public static final int MAX_CONVERSATION_HISTORY = 10;
    public static final int MAX_RESPONSE_TOKENS = 1024;
    public static final double TEMPERATURE = 0.7;
    
    // UI Configuration
    public static final int TYPING_DELAY_MS = 1000;
    public static final int MESSAGE_ANIMATION_DURATION = 300;
    
    // Quick Actions
    public static final String[] QUICK_ACTIONS = {
        "What's my attendance percentage?",
        "What's my fees status?",
        "How do I mark attendance?",
        "What holidays are coming up?",
        "Show me my profile",
        "How do I use this app?"
    };

    public static boolean isApiKeyConfigured() {
        return GEMINI_API_KEY != null && 
               !GEMINI_API_KEY.isEmpty();
    }

    public static String getApiKey() {
        if (!isApiKeyConfigured()) {
            throw new IllegalStateException("Gemini API key not configured.");
        }
        return GEMINI_API_KEY;
    }
}
