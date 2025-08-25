package com.example.acadlink;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AiProjectRecommender extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private List<MessageModel> chatList;

    private EditText messageEditText;
    private ImageButton sendBtn, backBtn, menuBtn;

    private GeminiApiHelper geminiApiHelper;
    private DatabaseReference chatRef;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ✅ Adjust for keyboard input
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ai_project_recommender);

        // Handle system insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // ✅ Init UI
        recyclerView = findViewById(R.id.chatRecyclerView);
        messageEditText = findViewById(R.id.messageEditText);
        sendBtn = findViewById(R.id.sendButton);
        backBtn = findViewById(R.id.toolbarBackBtn);
        menuBtn = findViewById(R.id.toolbarMenuBtns);

        // ✅ Setup RecyclerView
        chatList = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatList);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // ✅ Always scroll to last message
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(chatAdapter);

        // ✅ Gemini API
        geminiApiHelper = new GeminiApiHelper();

        // ✅ Firebase (Chats are already separate for each user by userId)
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        chatRef = FirebaseDatabase.getInstance()
                .getReference("AI Users")
                .child(userId)
                .child("Chats");

        // ✅ Load chat history
        loadChatHistory();

        // ✅ Back button → go to HomePage
        backBtn.setOnClickListener(v -> {
            Intent intent = new Intent(AiProjectRecommender.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        // ✅ Menu button → PopupMenu with "Clear Chat"
        menuBtn.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(AiProjectRecommender.this, menuBtn);
            popupMenu.getMenu().add("Clear Chat");
            popupMenu.setOnMenuItemClickListener(item -> {
                if (item.getTitle().equals("Clear Chat")) {
                    clearChatHistory();
                }
                return true;
            });
            popupMenu.show();
        });

        // ✅ Show/hide send button like Instagram
        messageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().trim().isEmpty()) {
                    sendBtn.setVisibility(android.view.View.GONE);
                } else {
                    sendBtn.setVisibility(android.view.View.VISIBLE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // ✅ Send button
        sendBtn.setOnClickListener(v -> {
            String question = messageEditText.getText().toString().trim();
            if (!TextUtils.isEmpty(question)) {
                // Add user message to chat
                addMessage("user", question);

                // Save to Firebase
                saveMessageToFirebase("user", question);

                // Clear input
                messageEditText.setText("");

                // Call Gemini
                geminiApiHelper.askGemini(question, new GeminiApiHelper.GeminiCallback() {
                    @Override
                    public void onResponse(String reply) {
                        runOnUiThread(() -> {
                            addMessage("ai", reply);
                            saveMessageToFirebase("ai", reply);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            addMessage("ai", "Error: " + error);
                        });
                    }
                });
            }
        });
    }

    // ✅ Load messages from Firebase ordered by timestamp
    private void loadChatHistory() {
        Query query = chatRef.orderByChild("timestamp");
        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                chatList.clear();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    MessageModel message = dataSnapshot.getValue(MessageModel.class);
                    if (message != null) {
                        chatList.add(message);
                    }
                }
                chatAdapter.notifyDataSetChanged();
                if (!chatList.isEmpty()) {
                    recyclerView.scrollToPosition(chatList.size() - 1);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Optional: Log the error
            }
        });
    }

    // ✅ Add message to RecyclerView
    private void addMessage(String sender, String text) {
        chatList.add(new MessageModel(sender, text));
        chatAdapter.notifyItemInserted(chatList.size() - 1);
        recyclerView.scrollToPosition(chatList.size() - 1);
    }

    // ✅ Save message in Firebase with timestamp
    private void saveMessageToFirebase(String sender, String text) {
        String key = chatRef.push().getKey();
        HashMap<String, Object> map = new HashMap<>();
        map.put("sender", sender);
        map.put("message", text);
        map.put("timestamp", System.currentTimeMillis());
        chatRef.child(key).setValue(map);
    }

    // ✅ Clear chat both locally and in Firebase
    private void clearChatHistory() {
        chatRef.removeValue(); // clear from Firebase
        chatList.clear();      // clear local list
        chatAdapter.notifyDataSetChanged();
    }
}
