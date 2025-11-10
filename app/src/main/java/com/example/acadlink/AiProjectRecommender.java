package com.example.acadlink;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;
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

import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

// Concurrency & synchronization classes
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// Graphics for ItemDecoration
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.RectF;

/**
 * AiProjectRecommender
 *
 * - Keeps original behavior and IDs unchanged.
 * - Adds a RecyclerView.ItemDecoration that draws a date header/pill above the day's first message.
 * - Keeps popup/date, scroll, firebase and other behavior unchanged.
 */
public class AiProjectRecommender extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private List<MessageModel> chatList;

    private EditText messageEditText;
    private ImageButton sendBtn, backBtn, menuBtn;

    // Use the original geminiApiHelper if present; we will wrap it with ReliableGeminiWrapper
    private GeminiApiHelper geminiApiHelper;
    private ReliableGeminiWrapper reliableGemini;

    private DatabaseReference chatRef;
    private String userId;

    // Track placeholder "Generating..." message
    private int generatingIndex = -1;

    // Date popup
    private TextView datePopup;
    private boolean popupAttached = false;
    private final Handler popupHandler = new Handler(Looper.getMainLooper());
    private final Runnable hidePopupRunnable = this::hideDatePopup;
    private static final int AUTO_HIDE_THRESHOLD = 7;
    private static final long AUTO_HIDE_DELAY_MS = 5000L;

    // Decoration instance (so we can add it once)
    private DateDividerDecoration dateDividerDecoration;

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

        // add date divider decoration (draws date pill above the day's first message)
        dateDividerDecoration = new DateDividerDecoration();
        recyclerView.addItemDecoration(dateDividerDecoration);

        // Gemini API helper (unchanged original)
        geminiApiHelper = new GeminiApiHelper();

        // Wrap with reliable wrapper: configurable timeouts & retries
        reliableGemini = new ReliableGeminiWrapper(geminiApiHelper);

        // Firebase (safe initialization)
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getUid() != null) {
            userId = currentUser.getUid();
            chatRef = FirebaseDatabase.getInstance()
                    .getReference("AI Users")
                    .child(userId)
                    .child("Chats");
        } else {
            // Fallback: create a temp path to avoid NPEs
            userId = null;
            chatRef = FirebaseDatabase.getInstance()
                    .getReference("AI Users")
                    .child("unknown_user")
                    .child("Chats");
        }

        // Prepare date popup view (not attached yet)
        createDatePopupView();

        // Attach scroll listener to show popup when different date appears
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private int lastSeenPos = -1;

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                int firstPos = lm.findFirstVisibleItemPosition();
                if (firstPos == RecyclerView.NO_POSITION) return;
                if (firstPos != lastSeenPos) {
                    lastSeenPos = firstPos;
                    // only show popup when this activity is visible
                    if (!isFinishing() && !isDestroyed()) {
                        ensurePopupAttachedIfNeeded();
                        showDateForPosition(firstPos);
                    }
                }
            }
        });

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
        if (sendBtn != null) sendBtn.setVisibility(View.GONE);

        // Show/hide send button based on input text
        messageEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (sendBtn == null) return;
                if (s == null || s.toString().trim().isEmpty()) {
                    sendBtn.setVisibility(View.GONE);
                } else {
                    sendBtn.setVisibility(View.VISIBLE);
                }
            }
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

                    // Call Gemini via reliable wrapper (handles timeouts & retries)
                    reliableGemini.askGemini(question, new ReliableGeminiWrapper.ReliableCallback() {
                        @Override
                        public void onSuccess(String reply) {
                            runOnUiThread(() -> {
                                replaceGeneratingMessage(reply);
                                saveMessageToFirebase("ai", reply);
                            });
                        }

                        @Override
                        public void onFailure(String error) {
                            runOnUiThread(() -> {
                                replaceGeneratingMessage("Error: " + error);
                            });
                        }
                    });
                }
            } catch (Exception ex) {
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
                        // keyboard likely opened → scroll only if user is already at bottom
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

    @Override
    protected void onResume() {
        super.onResume();
        // attach popup (so it will show while this activity is active)
        ensurePopupAttachedIfNeeded();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // remove popup so it won't appear on other activities
        removePopupIfAttached();
        popupHandler.removeCallbacks(hidePopupRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        popupHandler.removeCallbacks(hidePopupRunnable);
        removePopupIfAttached();
    }

    // -------------------- Date popup helpers --------------------

    private void createDatePopupView() {
        if (datePopup != null) {
            removePopupIfAttached();
        }
        datePopup = new TextView(this);
        datePopup.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        datePopup.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        // Use your desired background drawable if present; otherwise fallback
        try {
            datePopup.setBackground(ContextCompat.getDrawable(this, R.drawable.date_header_background));
        } catch (Exception ignored) {
            datePopup.setBackground(ContextCompat.getDrawable(this, android.R.drawable.dialog_holo_light_frame));
        }
        datePopup.setVisibility(View.GONE);
        datePopup.setElevation(dpToPx(6));
        // Use white text by default to match WhatsApp dots (you can change if needed)
        datePopup.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        datePopup.setClickable(false);
        datePopup.setFocusable(false);
    }

    private void ensurePopupAttachedIfNeeded() {
        if (datePopup == null) createDatePopupView();
        if (datePopup == null) return;
        if (popupAttached) return;

        ViewGroup activityContent = null;
        try {
            activityContent = findViewById(android.R.id.content);
        } catch (Exception ignored) { }

        if (activityContent == null) return;

        // compute top margin: status bar + action bar + small extra
        int statusBarHeight = 0;
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resId);
        }

        int actionBarHeight = 0;
        TypedValue tv = new TypedValue();
        if (getTheme() != null && getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
        }

        int extra = dpToPx(8);
        int topMargin = statusBarHeight + actionBarHeight + extra;

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = topMargin;
        datePopup.setLayoutParams(lp);

        try {
            activityContent.addView(datePopup);
            datePopup.bringToFront();
            popupAttached = true;
        } catch (Exception ignored) {
            popupAttached = false;
        }
    }

    private void removePopupIfAttached() {
        if (datePopup == null) return;
        if (!popupAttached) return;
        try {
            ViewGroup parent = (ViewGroup) datePopup.getParent();
            if (parent != null) parent.removeView(datePopup);
        } catch (Exception ignored) { }
        popupAttached = false;
    }

    private void showDateForPosition(int pos) {
        if (chatAdapter == null) return;
        MessageModel it = chatAdapter.getItemAt(pos);
        if (it == null) return;
        String label = formatDateLabel(it.getTimestamp());
        if (label == null || label.trim().isEmpty()) return;

        ensurePopupAttachedIfNeeded();

        datePopup.setText(label);
        datePopup.setVisibility(View.VISIBLE);
        datePopup.bringToFront();

        popupHandler.removeCallbacks(hidePopupRunnable);
        if (chatList != null && chatList.size() > AUTO_HIDE_THRESHOLD) {
            popupHandler.postDelayed(hidePopupRunnable, AUTO_HIDE_DELAY_MS);
        } else {
            // keep visible for small lists
            popupHandler.removeCallbacks(hidePopupRunnable);
        }
    }

    private void hideDatePopup() {
        if (datePopup != null) datePopup.setVisibility(View.GONE);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private String formatDateLabel(long tsMillis) {
        if (tsMillis <= 0) return "";
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(tsMillis);

        Calendar now = Calendar.getInstance();

        Calendar tMid = (Calendar) target.clone();
        tMid.set(Calendar.HOUR_OF_DAY, 0);
        tMid.set(Calendar.MINUTE, 0);
        tMid.set(Calendar.SECOND, 0);
        tMid.set(Calendar.MILLISECOND, 0);

        Calendar nMid = (Calendar) now.clone();
        nMid.set(Calendar.HOUR_OF_DAY, 0);
        nMid.set(Calendar.MINUTE, 0);
        nMid.set(Calendar.SECOND, 0);
        nMid.set(Calendar.MILLISECOND, 0);

        long diff = nMid.getTimeInMillis() - tMid.getTimeInMillis();
        long days = diff / (24L * 60L * 60L * 1000L);

        if (days == 0) return "Today";
        if (days == 1) return "Yesterday";

        Date d = new Date(tsMillis);
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(d);
    }

    // -------------------- Chat & Firebase logic (unchanged except for safe UI updates) --------------------

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

                    // keep natural order (older->newer). Your layoutManager uses stackFromEnd to show bottom.
                    // Already stored in ascending order but ensure it's sorted by timestamp ascending just in case:
                    Collections.sort(chatList, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

                    if (chatAdapter != null) {
                        chatAdapter.setData(chatList);
                    }

                    // redraw decoration when data changes
                    recyclerView.invalidateItemDecorations();

                    // Show popup for top visible item (if any) so user sees date on load
                    recyclerView.post(() -> {
                        try {
                            LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                            if (lm != null && chatList != null && chatList.size() > 0) {
                                int firstVisible = lm.findFirstVisibleItemPosition();
                                int pos = firstVisible >= 0 ? firstVisible : 0;
                                ensurePopupAttachedIfNeeded();
                                showDateForPosition(pos);
                            }
                        } catch (Exception ignored) {}
                    });

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

        // ensure headers updated
        recyclerView.invalidateItemDecorations();

        // Only scroll to newly inserted index if index valid and user is at bottom
        if (index >= 0 && isAtBottom()) {
            recyclerView.post(() -> {
                try {
                    recyclerView.smoothScrollToPosition(index);
                } catch (Exception ignore) {}
            });
        }

        // show popup for the current bottom item
        recyclerView.post(() -> {
            try {
                int pos = index >= 0 ? index : 0;
                ensurePopupAttachedIfNeeded();
                showDateForPosition(pos);
            } catch (Exception ignored) {}
        });

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
            DatabaseReference newRef = chatRef.push(); // fresh ref
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
        generatingIndex = -1;
        recyclerView.post(() -> {
            try {
                if (chatList != null && chatList.size() > 0) {
                    recyclerView.smoothScrollToPosition(chatList.size() - 1);
                }
            } catch (Exception ignore) {}
        });

        // update decoration
        recyclerView.invalidateItemDecorations();
    }

    // -------------------- Reliable Gemini wrapper --------------------
    /**
     * ReliableGeminiWrapper
     *
     * - Wraps an existing GeminiApiHelper instance and runs it with:
     *   - attemptTimeoutMs: how long to wait for each attempt's callback
     *   - maxAttempts: number of attempts (first attempt + retries)
     * - Uses CountDownLatch to wait for underlying callback to arrive.
     * - Calls callback.onSuccess(...) or callback.onFailure(...) on completion.
     */
    private static class ReliableGeminiWrapper {
        private final GeminiApiHelper underlying;
        private final long attemptTimeoutMs;
        private final int maxAttempts;
        private final long baseBackoffMs;

        public interface ReliableCallback {
            void onSuccess(String reply);
            void onFailure(String error);
        }

        public ReliableGeminiWrapper(GeminiApiHelper underlying) {
            this(underlying, 30000L /* 30s per attempt */, 3 /* attempts */, 1500L /* base backoff */);
        }

        public ReliableGeminiWrapper(GeminiApiHelper underlying, long attemptTimeoutMs, int maxAttempts, long baseBackoffMs) {
            this.underlying = underlying;
            this.attemptTimeoutMs = attemptTimeoutMs;
            this.maxAttempts = Math.max(1, maxAttempts);
            this.baseBackoffMs = Math.max(0, baseBackoffMs);
        }

        public void askGemini(final String prompt, final ReliableCallback callback) {
            // Run attempts off the UI thread
            new Thread(() -> {
                String lastError = "timeout";
                boolean success = false;
                String finalReply = null;

                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                    final CountDownLatch latch = new CountDownLatch(1);
                    final AtomicBoolean gotResponse = new AtomicBoolean(false);
                    final String[] replyHolder = new String[1];
                    final String[] errorHolder = new String[1];

                    try {
                        // Use underlying callback. When it fires, capture result and count down.
                        underlying.askGemini(prompt, new GeminiApiHelper.GeminiCallback() {
                            @Override
                            public void onResponse(String reply) {
                                replyHolder[0] = reply;
                                gotResponse.set(true);
                                latch.countDown();
                            }

                            @Override
                            public void onError(String error) {
                                errorHolder[0] = error;
                                gotResponse.set(true);
                                latch.countDown();
                            }
                        });

                        // Wait for the attempt to finish up to attemptTimeoutMs
                        boolean arrived = latch.await(attemptTimeoutMs, TimeUnit.MILLISECONDS);
                        if (arrived && gotResponse.get()) {
                            if (replyHolder[0] != null) {
                                // Success
                                finalReply = replyHolder[0];
                                success = true;
                                break;
                            } else if (errorHolder[0] != null) {
                                // Underlying returned a direct error, record and possibly retry
                                lastError = errorHolder[0];
                            } else {
                                lastError = "unknown_error";
                            }
                        } else {
                            // Attempt timed out
                            lastError = "timeout";
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        lastError = "interrupted";
                        break; // don't retry if interrupted
                    } catch (Exception ex) {
                        lastError = ex.getMessage() != null ? ex.getMessage() : "exception";
                    }

                    // If not last attempt, sleep an exponential backoff
                    if (!success && attempt < maxAttempts) {
                        try {
                            long sleepMs = baseBackoffMs * (1L << (attempt - 1));
                            Thread.sleep(sleepMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } // attempts loop

                // Post result on UI thread via callback
                if (success && finalReply != null) {
                    callback.onSuccess(finalReply);
                } else {
                    callback.onFailure(lastError != null ? lastError : "timeout");
                }
            }).start();
        }
    }

    // -------------------- Date divider ItemDecoration --------------------
    /**
     * DateDividerDecoration
     *
     * Draws a small date pill above the first message of each day.
     * Uses chatAdapter.getItemAt(position).getTimestamp() to read timestamps.
     *
     * This is non-invasive (no changes to adapter or item layouts required).
     */
    private class DateDividerDecoration extends RecyclerView.ItemDecoration {

        private final Paint textPaint;
        private final Paint bgPaint;
        private final Rect textBounds = new Rect();
        private final int verticalPadding;
        private final int horizontalPadding;
        private final int cornerRadius;
        private final int headerTopOffset; // extra offset above child to draw header

        public DateDividerDecoration() {
            float density = getResources().getDisplayMetrics().density;

            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(14 * density);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setColor(ContextCompat.getColor(AiProjectRecommender.this, android.R.color.white));

            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            // Try to use drawable color if present; otherwise fallback to a blue-ish default.
            int bgColor = ContextCompat.getColor(AiProjectRecommender.this,R.color.my_custom_blue);
            bgPaint.setColor(bgColor);

            verticalPadding = dpToPx(6);
            horizontalPadding = dpToPx(12);
            cornerRadius = dpToPx(12);
            headerTopOffset = dpToPx(6);
        }

        @Override
        public void onDrawOver(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
            super.onDrawOver(canvas, parent, state);

            if (chatAdapter == null || chatList == null || chatList.size() == 0) return;

            final int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);
                int adapterPos = parent.getChildAdapterPosition(child);
                if (adapterPos == RecyclerView.NO_POSITION) continue;
                // Only draw if this position is the first of the day (or first overall)
                boolean drawHeader = shouldDrawHeaderForPosition(adapterPos);
                if (!drawHeader) continue;

                MessageModel item = chatAdapter.getItemAt(adapterPos);
                if (item == null) continue;
                String label = formatDateLabelHeader(item.getTimestamp());
                if (label == null || label.isEmpty()) continue;

                textPaint.getTextBounds(label, 0, label.length(), textBounds);
                int textWidth = textBounds.width();
                int textHeight = textBounds.height();

                // determine header rect: centered horizontally above the child
                int childLeft = child.getLeft();
                int childRight = child.getRight();
                int childTop = child.getTop();

                int pillWidth = textWidth + horizontalPadding * 2;
                int pillHeight = textHeight + verticalPadding * 2;

                int centerX = (childLeft + childRight) / 2;
                int left = centerX - pillWidth / 2;
                int top = childTop - pillHeight - headerTopOffset;
                int right = centerX + pillWidth / 2;
                int bottom = top + pillHeight;

                // If top would be off-screen above the RecyclerView, clamp it
                int parentTop = parent.getTop();
                if (top < parentTop + dpToPx(4)) {
                    top = parentTop + dpToPx(4);
                    bottom = top + pillHeight;
                }

                RectF rect = new RectF(left, top, right, bottom);

                // Draw background (rounded rect)
                // Prefer using drawable if available (keeps style consistent)
                try {
                    // if drawable exists, draw behind text for consistency
                    if (getResources().getIdentifier("date_header_background", "drawable", getPackageName()) != 0) {
                        // fallback to bgPaint (we won't load drawable to keep this simple & safe)
                        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint);
                    } else {
                        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint);
                    }
                } catch (Exception ex) {
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint);
                }

                // Draw text centered in rect vertically and horizontally
                float textX = rect.left + (rect.width() - textWidth) / 2f - textBounds.left;
                float textY = rect.top + (rect.height() + textHeight) / 2f - textBounds.bottom;
                canvas.drawText(label, textX, textY, textPaint);
            }
        }

        // Check if position is the first item of its date group
        private boolean shouldDrawHeaderForPosition(int pos) {
            if (pos < 0 || chatAdapter == null) return false;
            MessageModel current = chatAdapter.getItemAt(pos);
            if (current == null) return false;
            long currTs = current.getTimestamp();
            if (currTs <= 0) return false;

            // First item always gets a header
            if (pos == 0) return true;

            MessageModel prev = chatAdapter.getItemAt(pos - 1);
            if (prev == null) return true;
            long prevTs = prev.getTimestamp();
            // If days differ, draw header for current
            return !isSameDay(currTs, prevTs);
        }

        // Helper: header label should be like formatDateLabel but we want "dd MMM yyyy" for older dates,
        // and Today/Yesterday for recent days to match popup style.
        private String formatDateLabelHeader(long tsMillis) {
            return formatDateLabel(tsMillis);
        }

        private boolean isSameDay(long t1, long t2) {
            if (t1 <= 0 || t2 <= 0) return false;
            Calendar c1 = Calendar.getInstance();
            c1.setTimeInMillis(t1);
            Calendar c2 = Calendar.getInstance();
            c2.setTimeInMillis(t2);
            return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                    c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
        }
    }
}
