package com.example.wifibasedattendanceapplication.chatbot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Data model for chat messages
 */
public class ChatMessage {
    public enum MessageType {
        USER, BOT, TYPING, ERROR
    }
    
    private String content;
    private MessageType type;
    private long timestamp;
    private boolean isTyping;
    
    public ChatMessage(String content, MessageType type) {
        this.content = content;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.isTyping = false;
    }
    
    public ChatMessage(String content, MessageType type, boolean isTyping) {
        this.content = content;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.isTyping = isTyping;
    }
    
    // Getters
    public String getContent() {
        return content;
    }
    
    public MessageType getType() {
        return type;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public boolean isTyping() {
        return isTyping;
    }
    
    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
    
    // Setters
    public void setContent(String content) {
        this.content = content;
    }
    
    public void setType(MessageType type) {
        this.type = type;
    }
    
    public void setTyping(boolean typing) {
        this.isTyping = typing;
    }
}
