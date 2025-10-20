package com.example.wifibasedattendanceapplication.chatbot;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for managing chat conversations and context
 */
public class ChatRepository {
    
    private static final String TAG = "ChatRepository";
    
    private GeminiService geminiService;
    private StudentContextBuilder contextBuilder;
    private List<ChatMessage> conversationHistory;
    private String currentContext;
    private boolean contextLoaded;
    
    public interface ChatCallback {
        void onMessageReceived(ChatMessage message);
        void onError(String error);
        void onTypingStarted();
        void onTypingStopped();
    }
    
    public ChatRepository(String apiKey) {
        this.geminiService = new GeminiService(apiKey);
        this.contextBuilder = new StudentContextBuilder();
        this.conversationHistory = new ArrayList<>();
        this.contextLoaded = false;
    }
    
    public void initializeContext(ChatCallback callback) {
        if (contextLoaded) {
            return;
        }
        
        contextBuilder.buildStudentContext(new StudentContextBuilder.ContextCallback() {
            @Override
            public void onContextReady(String context) {
                currentContext = context;
                contextLoaded = true;
                Log.d(TAG, "Context loaded successfully");
                
                // Send welcome message
                ChatMessage welcomeMessage = new ChatMessage(
                    "Hello! I'm your AI assistant for the WiFi Attendance App. " +
                    "I can help you with attendance queries, fees information, app features, and more. " +
                    "What would you like to know?",
                    ChatMessage.MessageType.BOT
                );
                conversationHistory.add(welcomeMessage);
                callback.onMessageReceived(welcomeMessage);
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading context: " + error);
                currentContext = "Student context unavailable. Please try again later.";
                contextLoaded = true;
                
                // Send error message
                ChatMessage errorMessage = new ChatMessage(
                    "I'm having trouble loading your student information. " +
                    "I can still help you with general questions about the app. What would you like to know?",
                    ChatMessage.MessageType.BOT
                );
                conversationHistory.add(errorMessage);
                callback.onMessageReceived(errorMessage);
            }
        });
    }
    
    public void sendMessage(String userMessage, ChatCallback callback) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            callback.onError("Please enter a message");
            return;
        }
        
        // Add user message to history
        ChatMessage userChatMessage = new ChatMessage(userMessage.trim(), ChatMessage.MessageType.USER);
        conversationHistory.add(userChatMessage);
        callback.onMessageReceived(userChatMessage);
        
        // Show typing indicator
        ChatMessage typingMessage = new ChatMessage("AI is thinking...", ChatMessage.MessageType.TYPING, true);
        conversationHistory.add(typingMessage);
        callback.onTypingStarted();
        callback.onMessageReceived(typingMessage);
        
        // Build enhanced context for this conversation
        String conversationContext = buildConversationContext(userMessage);
        
        // Send to Gemini
        geminiService.sendMessage(userMessage, conversationContext, new GeminiService.ChatCallback() {
            @Override
            public void onSuccess(String response) {
                // Remove typing indicator
                removeTypingMessage();
                callback.onTypingStopped();
                
                // Add bot response to history
                ChatMessage botMessage = new ChatMessage(response, ChatMessage.MessageType.BOT);
                conversationHistory.add(botMessage);
                callback.onMessageReceived(botMessage);
                
                Log.d(TAG, "Message sent successfully");
            }
            
            @Override
            public void onError(String error) {
                // Remove typing indicator
                removeTypingMessage();
                callback.onTypingStopped();
                
                // Add error message to history
                ChatMessage errorMessage = new ChatMessage(
                    "Sorry, I'm having trouble connecting right now. Please try again later. " +
                    "Error: " + error,
                    ChatMessage.MessageType.ERROR
                );
                conversationHistory.add(errorMessage);
                callback.onMessageReceived(errorMessage);
                
                Log.e(TAG, "Error sending message: " + error);
            }
        });
    }
    
    private String buildConversationContext() {
        StringBuilder context = new StringBuilder();
        
        // Add student context if available
        if (currentContext != null && !currentContext.isEmpty()) {
            context.append(currentContext);
            context.append("\n\n");
        }
        
        // Add recent conversation history (last 5 messages)
        context.append("RECENT CONVERSATION:\n");
        int startIndex = Math.max(0, conversationHistory.size() - 5);
        for (int i = startIndex; i < conversationHistory.size(); i++) {
            ChatMessage message = conversationHistory.get(i);
            if (message.getType() == ChatMessage.MessageType.USER) {
                context.append("Student: ").append(message.getContent()).append("\n");
            } else if (message.getType() == ChatMessage.MessageType.BOT) {
                context.append("Assistant: ").append(message.getContent()).append("\n");
            }
        }
        
        return context.toString();
    }
    
    private String buildConversationContext(String userMessage) {
        StringBuilder context = new StringBuilder();
        
        // Add student context if available
        if (currentContext != null && !currentContext.isEmpty()) {
            context.append(currentContext);
            context.append("\n\n");
        }
        
        // Add recent conversation history (last 5 messages)
        context.append("RECENT CONVERSATION:\n");
        int startIndex = Math.max(0, conversationHistory.size() - 5);
        for (int i = startIndex; i < conversationHistory.size(); i++) {
            ChatMessage message = conversationHistory.get(i);
            if (message.getType() == ChatMessage.MessageType.USER) {
                context.append("Student: ").append(message.getContent()).append("\n");
            } else if (message.getType() == ChatMessage.MessageType.BOT) {
                context.append("Assistant: ").append(message.getContent()).append("\n");
            }
        }
        
        // Enhance context based on user message
        return buildEnhancedContext(userMessage, context.toString());
    }
    
    private void removeTypingMessage() {
        for (int i = conversationHistory.size() - 1; i >= 0; i--) {
            ChatMessage message = conversationHistory.get(i);
            if (message.getType() == ChatMessage.MessageType.TYPING) {
                conversationHistory.remove(i);
                break;
            }
        }
    }
    
    public List<ChatMessage> getConversationHistory() {
        return new ArrayList<>(conversationHistory);
    }
    
    public void clearConversation() {
        conversationHistory.clear();
        Log.d(TAG, "Conversation cleared");
    }
    
    public boolean isContextLoaded() {
        return contextLoaded;
    }
    
    public String getCurrentContext() {
        return currentContext;
    }
    
    // Quick response methods for common queries
    public boolean isQuickQuery(String message) {
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("hello") || 
               lowerMessage.contains("hi") || 
               lowerMessage.contains("help") ||
               lowerMessage.contains("what can you do") ||
               lowerMessage.contains("features");
    }
    
    public String getQuickResponse(String message) {
        String lowerMessage = message.toLowerCase();
        
        if (lowerMessage.contains("hello") || lowerMessage.contains("hi")) {
            return "Hello! I'm here to help you with your attendance app. What would you like to know?";
        } else if (lowerMessage.contains("help") || lowerMessage.contains("what can you do")) {
            return "I can help you with:\n" +
                   "• Attendance queries and percentages\n" +
                   "• Fees information and payment status\n" +
                   "• App features and navigation\n" +
                   "• Academic calendar and holidays\n" +
                   "• General questions about the app\n\n" +
                   "Just ask me anything!";
        } else if (lowerMessage.contains("features")) {
            return "Here are the main features of your attendance app:\n" +
                   "• Mark attendance via WiFi\n" +
                   "• View detailed attendance reports\n" +
                   "• Check fees and payment status\n" +
                   "• View academic calendar\n" +
                   "• Manage your profile\n" +
                   "• Get AI assistance (that's me!)\n\n" +
                   "Is there anything specific you'd like to know about?";
        }
        
        return null; // Not a quick query
    }
    
    // Enhanced context building for better responses
    public String buildEnhancedContext(String userMessage, String baseContext) {
        StringBuilder enhancedContext = new StringBuilder();
        
        // Add base context
        enhancedContext.append(baseContext);
        enhancedContext.append("\n\n");
        
        // Add specific instructions based on query type
        String lowerMessage = userMessage.toLowerCase();
        
        if (lowerMessage.contains("holiday") || lowerMessage.contains("calendar") || lowerMessage.contains("date")) {
            enhancedContext.append("IMPORTANT: When asked about holidays, calendar, or dates, provide the actual data from the context above. ");
            enhancedContext.append("Do NOT give instructions to go to the app. Instead, give the specific holiday dates and information. ");
            enhancedContext.append("If the user asks about upcoming holidays, list the actual dates and names from the context.\n\n");
        }
        
        if (lowerMessage.contains("attendance") || lowerMessage.contains("present") || lowerMessage.contains("absent") || 
            lowerMessage.contains("class") || lowerMessage.contains("75%") || lowerMessage.contains("percentage")) {
            enhancedContext.append("IMPORTANT: When asked about attendance, provide the actual attendance data from the context above. ");
            enhancedContext.append("Do NOT give instructions to go to the app. Instead, give the specific attendance percentages, ");
            enhancedContext.append("recent attendance records, and subject-wise data from the context. ");
            enhancedContext.append("For questions about specific dates (like '16th October'), look for that date in the 'Recent Daily Attendance' section. ");
            enhancedContext.append("For questions about reaching 75% attendance, use the 'Classes needed for 75%' data from the context.\n\n");
        }
        
        if (lowerMessage.contains("fees") || lowerMessage.contains("payment") || lowerMessage.contains("due")) {
            enhancedContext.append("IMPORTANT: When asked about fees, provide the actual fees data from the context above. ");
            enhancedContext.append("Do NOT give instructions to go to the app. Instead, give the specific fees status, ");
            enhancedContext.append("amounts, and due dates from the context.\n\n");
        }
        
        // Handle specific date queries
        if (lowerMessage.contains("16th") || lowerMessage.contains("16 october") || lowerMessage.contains("16 oct")) {
            enhancedContext.append("SPECIAL INSTRUCTION: The user is asking about October 16th specifically. ");
            enhancedContext.append("Look for '16/10/24' or '16 Oct 2024' in the Recent Daily Attendance section. ");
            enhancedContext.append("If found, list all the classes they attended on that date with subjects and times. ");
            enhancedContext.append("If not found, say 'No attendance data available for 16 Oct 2024'.\n\n");
        }
        
        enhancedContext.append("RESPONSE GUIDELINES:\n");
        enhancedContext.append("- Always provide actual data from the context when available\n");
        enhancedContext.append("- Be specific and direct with numbers, dates, and percentages\n");
        enhancedContext.append("- Only give instructions if the data is not available in the context\n");
        enhancedContext.append("- Use a friendly and helpful tone\n");
        enhancedContext.append("- Format dates as 'DD MMM YYYY' (e.g., '15 Oct 2024')\n");
        enhancedContext.append("- Format percentages with one decimal place (e.g., '85.5%')\n");
        enhancedContext.append("- When calculating attendance, show the math: (present/total) * 100\n");
        enhancedContext.append("- For 'classes needed' questions, use the pre-calculated values from context\n\n");
        
        return enhancedContext.toString();
    }
}
