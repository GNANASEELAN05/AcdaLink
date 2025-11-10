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

/**
 * Robust ChatAdapter replacement.
 *
 * - Keeps your view types and layouts identical.
 * - Provides safe update methods (setData, addItem, replaceItem) so Activity/Firebase updates
 *   are correctly reflected in the UI.
 * - Defensive null checks and equalsIgnoreCase for sender checks.
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_AI = 1;

    // internal list owned by adapter (Activity can pass data via setData/addItem)
    private final List<MessageModel> chatList = new ArrayList<>();

    public ChatAdapter() {
        // empty constructor - use setData(...) to populate
    }

    // Optional convenience constructor that copies initial list
    public ChatAdapter(List<MessageModel> initial) {
        if (initial != null && !initial.isEmpty()) {
            this.chatList.addAll(initial);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position < 0 || position >= chatList.size()) return VIEW_TYPE_AI;
        MessageModel m = chatList.get(position);
        if (m == null) return VIEW_TYPE_AI;
        String sender = m.getSender();
        if (sender == null) return VIEW_TYPE_AI;
        if ("user".equalsIgnoreCase(sender.trim())) return VIEW_TYPE_USER;
        return VIEW_TYPE_AI;
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
        if (position < 0 || position >= chatList.size()) return;
        MessageModel message = chatList.get(position);
        if (message == null) return;

        String time = formatTime(message.getTimestamp());

        if (holder instanceof UserViewHolder) {
            UserViewHolder uv = (UserViewHolder) holder;
            uv.messageText.setText(safe(message.getMessage()));
            uv.timeText.setText(safe(time));
        } else if (holder instanceof AIViewHolder) {
            AIViewHolder av = (AIViewHolder) holder;
            av.messageText.setText(safe(message.getMessage()));
            av.timeText.setText(safe(time));
        }
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    // ======== Public API for updating adapter ========

    /**
     * Replace entire dataset and refresh list (use when Firebase returns snapshot)
     */
    public void setData(List<MessageModel> newMessages) {
        chatList.clear();
        if (newMessages != null && !newMessages.isEmpty()) {
            chatList.addAll(newMessages);
        }
        // full refresh (safe)
        notifyDataSetChanged();
    }

    /**
     * Append a single message and notify adapter in a precise way.
     * Returns inserted index.
     */
    public int addItem(MessageModel message) {
        if (message == null) return -1;
        chatList.add(message);
        int idx = chatList.size() - 1;
        try {
            notifyItemInserted(idx);
        } catch (Exception ignored) { notifyDataSetChanged(); }
        return idx;
    }

    /**
     * Replace item at index (used for "Generating..." replacement)
     */
    public void replaceItem(int index, MessageModel message) {
        if (index < 0 || index >= chatList.size() || message == null) return;
        chatList.set(index, message);
        try {
            notifyItemChanged(index);
        } catch (Exception ignored) { notifyDataSetChanged(); }
    }

    /**
     * Safe accessor used by Activity to read timestamps / items (read-only)
     */
    public MessageModel getItemAt(int pos) {
        if (pos < 0 || pos >= chatList.size()) return null;
        return chatList.get(pos);
    }

    /**
     * Return a copy of current list (defensive)
     */
    public List<MessageModel> getAll() {
        return new ArrayList<>(chatList);
    }

    // ======== Helpers & ViewHolders ========

    private String safe(String s) {
        return s == null ? "" : s;
    }

    // Format timestamp into readable time
    private String formatTime(long timestamp) {
        if (timestamp <= 0) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        } catch (Exception e) {
            return "";
        }
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, timeText;
        UserViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textMessageUser);
            timeText = itemView.findViewById(R.id.textTimeUser);
            if (messageText != null) messageText.setTextIsSelectable(true);
        }
    }

    static class AIViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, timeText;
        AIViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textMessageAI);
            timeText = itemView.findViewById(R.id.textTimeAI);
            if (messageText != null) messageText.setTextIsSelectable(true);
        }
    }
}
