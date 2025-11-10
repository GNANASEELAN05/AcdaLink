package com.example.acadlink;

public class MessageModel {
    private String sender;
    private String message;
    private long timestamp; // Used for chat ordering & display

    // Empty constructor required for Firebase deserialization
    public MessageModel() {}

    // Constructor for new messages (auto-sets current time)
    public MessageModel(String sender, String message) {
        this.sender = sender;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    // Constructor when timestamp is explicitly provided
    public MessageModel(String sender, String message, long timestamp) {
        this.sender = sender;
        this.message = message;
        this.timestamp = timestamp;
    }

    // ===== Getters =====
    public String getSender() {
        return sender;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // ===== Setters =====
    public void setSender(String sender) {
        this.sender = sender;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
