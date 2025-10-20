package com.example.wifibasedattendanceapplication;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wifibasedattendanceapplication.chatbot.ChatAdapter;
import com.example.wifibasedattendanceapplication.chatbot.ChatConfig;
import com.example.wifibasedattendanceapplication.chatbot.ChatMessage;
import com.example.wifibasedattendanceapplication.chatbot.ChatRepository;

/**
 * Chat activity for AI assistant
 */
public class ChatActivity extends AppCompatActivity implements ChatRepository.ChatCallback {
    
    private static final String TAG = "ChatActivity";
    
    private RecyclerView rvMessages;
    private EditText etMessageInput;
    private ImageView btnSend;
    private ImageView btnBack;
    private ImageView btnClear;
    private TextView tvStatus;
    private LinearLayout llQuickActions;
    private TextView btnQuickAttendance;
    private TextView btnQuickFees;
    private TextView btnQuickHelp;
    
    private ChatAdapter chatAdapter;
    private ChatRepository chatRepository;
    private boolean isInitialized = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        
        initViews();
        setupClickListeners();
        setupRecyclerView();
        initializeChat();
    }
    
    private void initViews() {
        rvMessages = findViewById(R.id.rv_messages);
        etMessageInput = findViewById(R.id.et_message_input);
        btnSend = findViewById(R.id.btn_send);
        btnBack = findViewById(R.id.btn_back);
        btnClear = findViewById(R.id.btn_clear);
        tvStatus = findViewById(R.id.tv_status);
        llQuickActions = findViewById(R.id.ll_quick_actions);
        btnQuickAttendance = findViewById(R.id.btn_quick_attendance);
        btnQuickFees = findViewById(R.id.btn_quick_fees);
        btnQuickHelp = findViewById(R.id.btn_quick_help);
    }
    
    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> onBackPressed());
        
        btnSend.setOnClickListener(v -> sendMessage());
        
        btnClear.setOnClickListener(v -> showClearDialog());
        
        etMessageInput.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
        
        // Quick action buttons
        btnQuickAttendance.setOnClickListener(v -> sendQuickMessage("What's my attendance percentage?"));
        btnQuickFees.setOnClickListener(v -> sendQuickMessage("What's my fees status?"));
        btnQuickHelp.setOnClickListener(v -> sendQuickMessage("How do I use this app?"));
    }
    
    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter();
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(chatAdapter);
        
        // Scroll to bottom when new message is added
        chatAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int positionEnd) {
                super.onItemRangeInserted(positionStart, positionEnd);
                if (positionEnd == chatAdapter.getItemCount() - 1) {
                    rvMessages.scrollToPosition(chatAdapter.getItemCount() - 1);
                }
            }
        });
    }
    
    private void initializeChat() {
        if (!ChatConfig.isApiKeyConfigured()) {
            showApiKeyDialog();
            return;
        }
        
        try {
            chatRepository = new ChatRepository(ChatConfig.getApiKey());
            chatRepository.initializeContext(this);
            isInitialized = true;
        } catch (Exception e) {
            showErrorDialog("Failed to initialize chat: " + e.getMessage());
        }
    }
    
    private void showApiKeyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("API Key Required")
                .setMessage("Please set your Gemini API key in the .env file at the project root to use the AI assistant.\n\n1. Get your API key from: https://makersuite.google.com/app/apikey\n2. Add a line to .env: GEMINI_API_KEY=your_key_here\n3. Rebuild the app")
                .setPositiveButton("OK", (dialog, which) -> {
                    finish();
                })
                .setCancelable(false)
                .show();
    }
    
    private void showErrorDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> {
                    finish();
                })
                .setCancelable(false)
                .show();
    }
    
    private void sendMessage() {
        String message = etMessageInput.getText().toString().trim();
        if (TextUtils.isEmpty(message)) {
            return;
        }
        
        if (!isInitialized) {
            Toast.makeText(this, "Chat is initializing, please wait...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Clear input
        etMessageInput.setText("");
        
        // Send message
        chatRepository.sendMessage(message, this);
    }
    
    private void sendQuickMessage(String message) {
        etMessageInput.setText(message);
        sendMessage();
    }
    
    private void showClearDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Conversation")
                .setMessage("Are you sure you want to clear the conversation history?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    chatRepository.clearConversation();
                    chatAdapter.clearMessages();
                    Toast.makeText(this, "Conversation cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    // ChatRepository.ChatCallback implementation
    @Override
    public void onMessageReceived(ChatMessage message) {
        runOnUiThread(() -> {
            chatAdapter.addMessage(message);
            Log.d(TAG, "Message received: " + message.getContent());
        });
    }
    
    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show();
            Log.e(TAG, "Chat error: " + error);
        });
    }
    
    @Override
    public void onTypingStarted() {
        runOnUiThread(() -> {
            tvStatus.setText("AI is typing...");
            btnSend.setEnabled(false);
        });
    }
    
    @Override
    public void onTypingStopped() {
        runOnUiThread(() -> {
            tvStatus.setText("Online");
            btnSend.setEnabled(true);
        });
    }
    
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up resources if needed
    }
}
