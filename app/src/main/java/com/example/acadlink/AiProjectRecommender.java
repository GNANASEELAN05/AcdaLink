package com.example.acadlink;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ViewTreeObserver;
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
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AiProjectRecommender
 *
 * - Keeps original behavior and IDs unchanged.
 * - Fixes crash when all previous chats are deleted and user types / sends a new message.
 * - Defensive null-checks added and scrolling guarded so no invalid positions are requested.
 * - Uses push().setValue(...) safely (avoids relying on getKey()).
 *
 * Copy-paste into your project (replace the old file).
 */
public class AiProjectRecommender extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private List<MessageModel> chatList;

    private EditText messageEditText;
    private ImageButton sendBtn, backBtn, menuBtn;

    private GeminiApiHelper geminiApiHelper;
    private DatabaseReference chatRef;
    private String userId;

    // Track placeholder "Generating..." message
    private int generatingIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Adjust for keyboard input
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ai_project_recommender);

        // Handle system insets (guard if main view not present)
        if (findViewById(R.id.main) != null) {
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        // Init UI
        recyclerView = findViewById(R.id.chatRecyclerView);
        messageEditText = findViewById(R.id.messageEditText);
        sendBtn = findViewById(R.id.sendButton);
        backBtn = findViewById(R.id.toolbarBackBtn);
        menuBtn = findViewById(R.id.toolbarMenuBtns);

        // Setup RecyclerView
        chatList = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatList);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // keeps layout similar to WhatsApp
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(chatAdapter);

        // Gemini API
        geminiApiHelper = new GeminiApiHelper();

        // Firebase (safe initialization)
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getUid() != null) {
            userId = currentUser.getUid();
            chatRef = FirebaseDatabase.getInstance()
                    .getReference("AI Users")
                    .child(userId)
                    .child("Chats");
        } else {
            // Fallback: create a temp path to avoid NPEs (this keeps app stable if auth state is somehow missing)
            userId = null;
            chatRef = FirebaseDatabase.getInstance()
                    .getReference("AI Users")
                    .child("unknown_user")
                    .child("Chats");
        }

        // Load chat history
        loadChatHistory();

        // Back button → go to HomePage
        backBtn.setOnClickListener(v -> {
            Intent intent = new Intent(AiProjectRecommender.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        // Menu button → PopupMenu with "Clear Chat"
        menuBtn.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(AiProjectRecommender.this, menuBtn);
            popupMenu.getMenu().add("Clear Chat");
            popupMenu.setOnMenuItemClickListener(item -> {
                if ("Clear Chat".contentEquals(item.getTitle())) {
                    clearChatHistory();
                }
                return true;
            });
            popupMenu.show();
        });

        // Make EditText behave like WhatsApp
        messageEditText.setHorizontallyScrolling(false);
        messageEditText.setMaxLines(Integer.MAX_VALUE);

        // Hide send button initially
        if (sendBtn != null) sendBtn.setVisibility(android.view.View.GONE);

        // Show/hide send button like Instagram
        messageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (sendBtn == null) return;
                if (s == null || s.toString().trim().isEmpty()) {
                    sendBtn.setVisibility(android.view.View.GONE);
                } else {
                    sendBtn.setVisibility(android.view.View.VISIBLE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Send button
        sendBtn.setOnClickListener(v -> {
            try {
                String question = messageEditText.getText().toString().trim();
                if (!TextUtils.isEmpty(question)) {
                    // Add user message to chat
                    addMessage("user", question);

                    // Save to Firebase (safe push)
                    saveMessageToFirebase("user", question);

                    // Clear input
                    messageEditText.setText("");

                    // Show "Generating..." while waiting for AI
                    generatingIndex = addMessage("ai", "Generating...");

                    // Call Gemini
                    geminiApiHelper.askGemini(question, new GeminiApiHelper.GeminiCallback() {
                        @Override
                        public void onResponse(String reply) {
                            runOnUiThread(() -> {
                                replaceGeneratingMessage(reply);
                                saveMessageToFirebase("ai", reply);
                            });
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {
                                replaceGeneratingMessage("Error: " + error);
                            });
                        }
                    });
                }
            } catch (Exception ex) {
                // Prevent crash on unexpected errors (keep app stable)
                ex.printStackTrace();
            }
        });

        // Auto-scroll chat when keyboard opens ONLY if at bottom
        recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            private int previousHeight = 0;

            @Override
            public void onGlobalLayout() {
                try {
                    int height = recyclerView.getHeight();
                    if (previousHeight != 0 && height < previousHeight) {
                        // keyboard likely opened → scroll only if user is already at bottom and there's something to scroll to
                        if (isAtBottom() && chatList != null && chatList.size() > 0) {
                            final int lastIndex = chatList.size() - 1;
                            if (lastIndex >= 0) {
                                recyclerView.post(() -> {
                                    try {
                                        recyclerView.smoothScrollToPosition(lastIndex);
                                    } catch (Exception ignore) {}
                                });
                            }
                        }
                    }
                    previousHeight = height;
                } catch (Exception ignored) { }
            }
        });
    }

    // Check if user is at bottom
    private boolean isAtBottom() {
        if (recyclerView == null || recyclerView.getLayoutManager() == null) return true;
        try {
            LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            int lastVisible = layoutManager.findLastCompletelyVisibleItemPosition();
            return lastVisible == (chatList.size() - 1);
        } catch (Exception e) {
            return true;
        }
    }

    // Load messages from Firebase ordered by timestamp
    private void loadChatHistory() {
        if (chatRef == null) return;
        Query query = chatRef.orderByChild("timestamp");
        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                try {
                    chatList.clear();
                    for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                        MessageModel message = dataSnapshot.getValue(MessageModel.class);
                        if (message != null) {
                            chatList.add(message);
                        }
                    }
                    if (chatAdapter != null) chatAdapter.notifyDataSetChanged();

                    // Scroll only when there's something to scroll to
                    if (!chatList.isEmpty() && isAtBottom()) {
                        final int last = chatList.size() - 1;
                        if (last >= 0) {
                            recyclerView.post(() -> {
                                try {
                                    recyclerView.smoothScrollToPosition(last);
                                } catch (Exception ignore) {}
                            });
                        }
                    }
                } catch (Exception ignored) { }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Optional: Log the error
            }
        });
    }

    // Add message to RecyclerView → return its index
    private int addMessage(String sender, String text) {
        if (chatList == null) chatList = new ArrayList<>();
        MessageModel mm = new MessageModel(sender, text);
        chatList.add(mm);
        int index = chatList.size() - 1;
        try {
            if (chatAdapter != null) chatAdapter.notifyItemInserted(index);
        } catch (Exception ignored) {}

        // Only scroll to newly inserted index if index valid and user is at bottom
        if (index >= 0 && isAtBottom()) {
            recyclerView.post(() -> {
                try {
                    recyclerView.smoothScrollToPosition(index);
                } catch (Exception ignore) {}
            });
        }
        return index;
    }

    // Replace "Generating..." with actual reply
    private void replaceGeneratingMessage(String newText) {
        try {
            if (generatingIndex >= 0 && generatingIndex < chatList.size()) {
                chatList.set(generatingIndex, new MessageModel("ai", newText));
                if (chatAdapter != null) chatAdapter.notifyItemChanged(generatingIndex);
                int lastIndex = chatList.size() - 1;
                if (lastIndex >= 0 && isAtBottom()) {
                    recyclerView.post(() -> {
                        try {
                            recyclerView.smoothScrollToPosition(lastIndex);
                        } catch (Exception ignore) {}
                    });
                }
            } else {
                addMessage("ai", newText);
            }
        } catch (Exception ignored) {}
        generatingIndex = -1;
    }

    // Save message in Firebase with timestamp (safe push)
    private void saveMessageToFirebase(String sender, String text) {
        if (chatRef == null) return;
        try {
            DatabaseReference newRef = chatRef.push(); // always returns a fresh ref
            Map<String, Object> map = new HashMap<>();
            map.put("sender", sender);
            map.put("message", text);
            map.put("timestamp", System.currentTimeMillis());
            newRef.setValue(map);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Clear chat both locally and in Firebase
    private void clearChatHistory() {
        try {
            if (chatRef != null) chatRef.removeValue(); // clear from Firebase
        } catch (Exception ignored) {}
        if (chatList != null) chatList.clear();      // clear local list
        if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
        // reset generating index
        generatingIndex = -1;
        // ensure recycler doesn't try to scroll to -1
        recyclerView.post(() -> {
            try {
                if (chatList != null && chatList.size() > 0) {
                    recyclerView.smoothScrollToPosition(chatList.size() - 1);
                }
            } catch (Exception ignore) {}
        });
    }
}
