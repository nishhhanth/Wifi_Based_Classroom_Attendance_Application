package com.example.wifibasedattendanceapplication.chatbot;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wifibasedattendanceapplication.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying chat messages in RecyclerView
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    
    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_BOT = 2;
    private static final int VIEW_TYPE_TYPING = 3;
    private static final int VIEW_TYPE_ERROR = 4;
    
    private List<ChatMessage> messages;
    private OnMessageClickListener messageClickListener;
    
    public interface OnMessageClickListener {
        void onMessageClick(ChatMessage message);
    }
    
    public ChatAdapter() {
        this.messages = new ArrayList<>();
    }
    
    public void setOnMessageClickListener(OnMessageClickListener listener) {
        this.messageClickListener = listener;
    }
    
    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messages.get(position);
        switch (message.getType()) {
            case USER:
                return VIEW_TYPE_USER;
            case BOT:
                return VIEW_TYPE_BOT;
            case TYPING:
                return VIEW_TYPE_TYPING;
            case ERROR:
                return VIEW_TYPE_ERROR;
            default:
                return VIEW_TYPE_BOT;
        }
    }
    
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        
        switch (viewType) {
            case VIEW_TYPE_USER:
                View userView = inflater.inflate(R.layout.item_message_user, parent, false);
                return new UserMessageViewHolder(userView);
            case VIEW_TYPE_BOT:
                View botView = inflater.inflate(R.layout.item_message_bot, parent, false);
                return new BotMessageViewHolder(botView);
            case VIEW_TYPE_TYPING:
                View typingView = inflater.inflate(R.layout.item_message_typing, parent, false);
                return new TypingMessageViewHolder(typingView);
            case VIEW_TYPE_ERROR:
                View errorView = inflater.inflate(R.layout.item_message_error, parent, false);
                return new ErrorMessageViewHolder(errorView);
            default:
                View defaultView = inflater.inflate(R.layout.item_message_bot, parent, false);
                return new BotMessageViewHolder(defaultView);
        }
    }
    
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        
        if (holder instanceof UserMessageViewHolder) {
            ((UserMessageViewHolder) holder).bind(message);
        } else if (holder instanceof BotMessageViewHolder) {
            ((BotMessageViewHolder) holder).bind(message);
        } else if (holder instanceof TypingMessageViewHolder) {
            ((TypingMessageViewHolder) holder).bind(message);
        } else if (holder instanceof ErrorMessageViewHolder) {
            ((ErrorMessageViewHolder) holder).bind(message);
        }
    }
    
    @Override
    public int getItemCount() {
        return messages.size();
    }
    
    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }
    
    public void addMessageAt(int position, ChatMessage message) {
        messages.add(position, message);
        notifyItemInserted(position);
    }
    
    public void removeMessage(int position) {
        if (position >= 0 && position < messages.size()) {
            messages.remove(position);
            notifyItemRemoved(position);
        }
    }
    
    public void updateMessage(int position, ChatMessage message) {
        if (position >= 0 && position < messages.size()) {
            messages.set(position, message);
            notifyItemChanged(position);
        }
    }
    
    public void clearMessages() {
        messages.clear();
        notifyDataSetChanged();
    }
    
    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }
    
    // ViewHolder classes
    public static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        private TextView tvContent, tvTime;
        
        public UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_message_content);
            tvTime = itemView.findViewById(R.id.tv_message_time);
        }
        
        public void bind(ChatMessage message) {
            tvContent.setText(message.getContent());
            tvTime.setText(message.getFormattedTime());
        }
    }
    
    public static class BotMessageViewHolder extends RecyclerView.ViewHolder {
        private TextView tvContent, tvTime;
        
        public BotMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_message_content);
            tvTime = itemView.findViewById(R.id.tv_message_time);
        }
        
        public void bind(ChatMessage message) {
            tvContent.setText(message.getContent());
            tvTime.setText(message.getFormattedTime());
        }
    }
    
    public static class TypingMessageViewHolder extends RecyclerView.ViewHolder {
        private TextView tvContent;
        
        public TypingMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_typing_content);
        }
        
        public void bind(ChatMessage message) {
            tvContent.setText(message.getContent());
        }
    }
    
    public static class ErrorMessageViewHolder extends RecyclerView.ViewHolder {
        private TextView tvContent, tvTime;
        
        public ErrorMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_message_content);
            tvTime = itemView.findViewById(R.id.tv_message_time);
        }
        
        public void bind(ChatMessage message) {
            tvContent.setText(message.getContent());
            tvTime.setText(message.getFormattedTime());
        }
    }
}
