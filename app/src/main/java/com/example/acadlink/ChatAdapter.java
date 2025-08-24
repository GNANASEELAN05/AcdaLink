package com.example.acadlink;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_AI = 1;

    private List<MessageModel> chatList;

    public ChatAdapter(List<MessageModel> chatList) {
        if (chatList == null) {
            this.chatList = new ArrayList<>();
        } else {
            this.chatList = chatList;
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (chatList.get(position).getSender() != null &&
                chatList.get(position).getSender().equals("user")) {
            return VIEW_TYPE_USER;
        } else {
            return VIEW_TYPE_AI;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_user_message, parent, false);
            return new UserViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ai_message, parent, false);
            return new AIViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MessageModel message = chatList.get(position);
        if (message == null) return;

        // ✅ Format timestamp
        String time = formatTime(message.getTimestamp());

        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).messageText.setText(message.getMessage());
            ((UserViewHolder) holder).timeText.setText(time);
        } else if (holder instanceof AIViewHolder) {
            ((AIViewHolder) holder).messageText.setText(message.getMessage());
            ((AIViewHolder) holder).timeText.setText(time);
        }
    }

    @Override
    public int getItemCount() {
        return chatList != null ? chatList.size() : 0;
    }

    // ✅ To update chat history from Firebase
    public void setData(List<MessageModel> newMessages) {
        this.chatList.clear();
        this.chatList.addAll(newMessages);
        notifyDataSetChanged();
    }

    // ✅ Format timestamp into readable time
    private String formatTime(long timestamp) {
        if (timestamp <= 0) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, timeText;
        UserViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textMessageUser);
            timeText = itemView.findViewById(R.id.textTimeUser);
            messageText.setTextIsSelectable(true); // ✅ Copyable
        }
    }

    static class AIViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, timeText;
        AIViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textMessageAI);
            timeText = itemView.findViewById(R.id.textTimeAI);
            messageText.setTextIsSelectable(true); // ✅ Copyable
        }
    }
}
