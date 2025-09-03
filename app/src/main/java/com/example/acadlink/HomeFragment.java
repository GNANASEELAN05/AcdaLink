package com.example.acadlink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * HomeFragment
 *
 * - Keeps original behaviour and IDs unchanged.
 * - Shows badge/dot only for accepted/rejected requests that are NEW since the user's last "seen".
 * - If requests lack updatedAt, falls back to comparing saved per-request statuses so we don't repeatedly show old statuses.
 * - When user opens "Request Sent", we mark as seen and snapshot current statuses so old accepted/rejected won't reappear.
 * - Centers the small dot in the popup menu using a custom ImageSpan.
 *
 * Only minimal, necessary changes applied. All IDs and other behaviours are preserved.
 */
public class HomeFragment extends Fragment {

    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private String mParam2;

    // UI
    private EditText searchEt;
    private LinearLayout resultsContainer;

    // Data
    private final List<Project> allProjects = new ArrayList<>();

    // Realtime Database reference (existing)
    private DatabaseReference projectsRef;

    // ---------- BADGE: config ----------
    private static final String BADGE_PREF = "menu_badge_prefs";
    private static final String KEY_LAST_SEEN_SENT_PREFIX = "last_seen_sent_";
    private static final String KEY_KNOWN_STATUS_PREFIX = "known_status_";
    // accepted / rejected words (robust to variations)
    private static final Set<String> ACCEPTED = new HashSet<>(Arrays.asList(
            "accepted","approved","granted","allow","allowed","ready","true","yes"
    ));
    private static final Set<String> REJECTED = new HashSet<>(Arrays.asList(
            "rejected","declined","denied","no","notallowed","not_allowed"
    ));

    private TextView menuBadgeView;
    private int countSentUpdates = 0;       // accepted/rejected AFTER last seen
    private int countReceivedPending = 0;   // current pending incoming
    private int countFaculty = 0;           // placeholder (wire your node if needed)

    private ValueEventListener sentListener;
    private ValueEventListener receivedListener;
    private DatabaseReference sentRef;
    private DatabaseReference receivedRef;

    private String currentUid;

    public static HomeFragment newInstance(String param1, String param2) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    public HomeFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
        // Init Realtime DB (existing)
        projectsRef = FirebaseDatabase.getInstance().getReference("projects");
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_home, container, false);

        // Header elements (existing)
        ImageButton menuBtn = rootView.findViewById(R.id.toolbarMenuBtns);
        searchEt = rootView.findViewById(R.id.searchEditText);

        // ---- attach a badge to the menu button (no XML changes) ----
        attachMenuBadge(menuBtn, (ViewGroup) rootView);

        // ---- Menu popup (existing, with tiny additions for dots + seen) ----
        menuBtn.setOnClickListener(v -> {
            ContextThemeWrapper ctw = new ContextThemeWrapper(requireContext(), R.style.DarkPopupMenu);
            PopupMenu popup = new PopupMenu(ctw, v);

            String[] items = getResources().getStringArray(R.array.home_menu_items);
            for (int i = 0; i < items.length; i++) {
                popup.getMenu().add(Menu.NONE, i, i, items[i]);
            }

            // Before show: decorate titles with dots where needed
            decoratePopupWithDots(popup.getMenu());

            // Use the item id (the index) to decide action — avoids title-matching issues introduced by spans/drawables
            popup.setOnMenuItemClickListener(item -> {
                int idx = item.getItemId();
                String title = (idx >= 0 && idx < items.length) ? items[idx].trim() : String.valueOf(item.getTitle()).trim();

                if (equalsIgnoreCase(title, "My Projects")) {
                    startActivity(new Intent(getActivity(), MyProjects.class));
                    return true;
                } else if (equalsIgnoreCase(title, "All Projects")) {
                    startActivity(new Intent(getActivity(), AllProjectsActivity.class));
                    return true;
                } else if (equalsIgnoreCase(title, "Request To Faculty")) {
                    // If you later count faculty updates, optionally clear here similar to "Request Sent"
                    startActivity(new Intent(getActivity(), RequestToFacultyActivity.class));
                    return true;
                } else if (equalsIgnoreCase(title, "Request Received")) {
                    startActivity(new Intent(getActivity(), RequestReceivedActivity.class));
                    return true;
                } else if (equalsIgnoreCase(title, "Request Sent")) {
                    // Mark "seen" for SENT before opening, so accepted/rejected updates clear the badge
                    markSentSeenNow();
                    // Instant UI feedback
                    countSentUpdates = 0;
                    updateMenuBadgeNow();
                    startActivity(new Intent(getActivity(), RequestSentActivity.class));
                    return true;
                } else if (equalsIgnoreCase(title, "Downloads")) {
                    startActivity(new Intent(getActivity(), DownloadsActivity.class));
                    return true;
                }

                Toast.makeText(getContext(), title, Toast.LENGTH_SHORT).show();
                return true;
            });

            popup.show();
        });

        // ---- existing: inject container under search bar ----
        ViewGroup parent = (ViewGroup) searchEt.getParent();
        resultsContainer = new LinearLayout(requireContext());
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        resultsContainer.setPadding(dp(16), dp(8), dp(16), 0);
        int searchIndex = parent.indexOfChild(searchEt);
        parent.addView(resultsContainer, searchIndex + 1);

        // Setup search & load projects (copied from reference search implementation)
        loadProjectsFromRealtimeDb();

        searchEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderResults(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // AI logo click (keeps existing behaviour if present in layout)
        ImageView aiLogo = rootView.findViewById(R.id.aiLogo);
        if (aiLogo != null) {
            aiLogo.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), AiProjectRecommender.class);
                startActivity(intent);
            });
        }

        // Start listeners after view ready
        beginBadgeListeners();

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // remove listeners to avoid leaks
        if (sentRef != null && sentListener != null) sentRef.removeEventListener(sentListener);
        if (receivedRef != null && receivedListener != null) receivedRef.removeEventListener(receivedListener);
        sentListener = null;
        receivedListener = null;
    }

    // --------------------------------------------------------------------------------------------
    // Badge logic (self-contained, minimal)
    // --------------------------------------------------------------------------------------------

    private void attachMenuBadge(@NonNull ImageButton menuBtn, @NonNull ViewGroup rootView) {
        // Create once and add to the same parent for easy positioning
        ViewGroup parent = (ViewGroup) menuBtn.getParent();
        if (parent == null) parent = rootView;

        menuBadgeView = new TextView(requireContext());
        menuBadgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        menuBadgeView.setTextColor(0xFFFFFFFF);
        menuBadgeView.setPadding(dp(6), dp(1), dp(6), dp(1));
        menuBadgeView.setMinWidth(dp(18));
        menuBadgeView.setMinHeight(dp(18));
        menuBadgeView.setGravity(android.view.Gravity.CENTER);
        menuBadgeView.setEllipsize(TextUtils.TruncateAt.END);
        menuBadgeView.setMaxLines(1);
        menuBadgeView.setVisibility(View.GONE);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF03A9F4); // #03A9F4
        bg.setCornerRadius(dp(9));
        menuBadgeView.setBackground(bg);

        // Add and position relative to the button (top-right)
        parent.addView(menuBadgeView,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final ViewGroup finalParent = parent;
        menuBtn.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            try {
                int[] parentLoc = new int[2];
                int[] btnLoc = new int[2];
                finalParent.getLocationOnScreen(parentLoc);
                menuBtn.getLocationOnScreen(btnLoc);

                float x = (btnLoc[0] - parentLoc[0]) + menuBtn.getWidth() - dp(12);
                float y = (btnLoc[1] - parentLoc[1]) - dp(4);
                menuBadgeView.setX(x);
                menuBadgeView.setY(y);
            } catch (Exception ignored) {}
        });
    }

    private void beginBadgeListeners() {
        if (currentUid == null) return;

        // ----- SENT: accepted/rejected updates since last seen -----
        sentRef = FirebaseDatabase.getInstance().getReference("downloadRequestsSent").child(currentUid);
        sentListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                long lastSeen = getPrefs().getLong(KEY_LAST_SEEN_SENT_PREFIX + currentUid, 0L);
                int c = 0;
                try {
                    for (DataSnapshot projSnap : snapshot.getChildren()) {
                        String status = val(projSnap, "status");
                        if (!isAcceptedOrRejected(status)) continue;

                        String projId = projSnap.getKey();
                        Long upRaw = asLong(projSnap.child("updatedAt").getValue());
                        boolean isNew = false;

                        if (upRaw != null) {
                            long upMillis = toMillis(upRaw);
                            if (upMillis > lastSeen) {
                                isNew = true;
                            }
                        } else {
                            // No updatedAt: fallback to comparing stored known status for that request
                            if (projId != null) {
                                String knownKey = KEY_KNOWN_STATUS_PREFIX + currentUid + "_" + projId;
                                String known = getPrefs().getString(knownKey, "");
                                if (!known.equals(status)) {
                                    // If the status changed compared to our snapshot, treat as new.
                                    // If we never snapshot (known == ""), we still count it as new until user marks seen.
                                    isNew = true;
                                }
                            } else {
                                // If no id and no updatedAt, if user never marked seen treat as new
                                if (lastSeen == 0L) isNew = true;
                            }
                        }

                        if (isNew) c++;
                    }
                } catch (Exception ignored) { }
                countSentUpdates = c;
                updateMenuBadgeNow();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        };
        sentRef.addValueEventListener(sentListener);

        // ----- RECEIVED: current pending incoming (dot only) -----
        receivedRef = FirebaseDatabase.getInstance().getReference("downloadRequestsReceived").child(currentUid);
        receivedListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                int pending = 0;
                try {
                    for (DataSnapshot projSnap : snapshot.getChildren()) {
                        // structure: downloadRequestsReceived/<uid>/<projectId>/<requesterUid> = {...}
                        for (DataSnapshot requesterSnap : projSnap.getChildren()) {
                            String status = val(requesterSnap, "status");
                            if (status == null || status.trim().isEmpty() || "pending".equalsIgnoreCase(status)) {
                                pending++;
                            }
                        }
                    }
                } catch (Exception ignored) { }
                countReceivedPending = pending;
                updateMenuBadgeNow();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        };
        receivedRef.addValueEventListener(receivedListener);

        // Faculty (optional): keep zero unless you wire a node
        countFaculty = 0;
        updateMenuBadgeNow();
    }

    private void decoratePopupWithDots(@NonNull Menu menu) {
        // We keep original titles; we only add a tiny colored dot to the right where needed
        for (int i = 0; i < menu.size(); i++) {
            CharSequence t = menu.getItem(i).getTitle();
            if (t == null) continue;
            String title = t.toString().trim();

            boolean showDot = false;
            if (equalsIgnoreCase(title, "Request Sent")) {
                showDot = countSentUpdates > 0;
            } else if (equalsIgnoreCase(title, "Request Received")) {
                showDot = countReceivedPending > 0;
            } else if (equalsIgnoreCase(title, "Request To Faculty")) {
                showDot = countFaculty > 0;
            }

            if (showDot) {
                menu.getItem(i).setTitle(makeTitleWithDot(title));
            } else {
                menu.getItem(i).setTitle(title);
            }
        }
    }

    private CharSequence makeTitleWithDot(String title) {
        // Build "Title  [dot]" using a centered ImageSpan
        SpannableStringBuilder sb = new SpannableStringBuilder(title + "  .");
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(0xFF03A9F4);
        int size = dp(8);
        dot.setSize(size, size);

        android.graphics.Bitmap bmp = drawableToBitmap(dot);
        ImageSpan span = new CenteredImageSpan(requireContext(), bmp);
        int start = sb.length() - 1;
        int end = sb.length();
        sb.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    private android.graphics.Bitmap drawableToBitmap(GradientDrawable d) {
        int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : dp(8);
        int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : dp(8);
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        return b;
    }

    private void updateMenuBadgeNow() {
        if (menuBadgeView == null) return;
        int total = countSentUpdates + countReceivedPending + countFaculty;
        if (total <= 0) {
            menuBadgeView.setVisibility(View.GONE);
            return;
        }
        menuBadgeView.setVisibility(View.VISIBLE);
        menuBadgeView.setText(total > 99 ? "99+" : String.valueOf(total));
    }

    private void markSentSeenNow() {
        if (currentUid == null) return;
        final SharedPreferences prefs = getPrefs();
        final long now = System.currentTimeMillis();
        // Save last seen immediately
        prefs.edit().putLong(KEY_LAST_SEEN_SENT_PREFIX + currentUid, now).apply();

        // Snapshot current statuses so previously accepted/rejected ones do not reappear after reopening the app.
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("downloadRequestsSent").child(currentUid);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                SharedPreferences.Editor editor = prefs.edit();
                try {
                    for (DataSnapshot projSnap : snapshot.getChildren()) {
                        String id = projSnap.getKey();
                        String status = val(projSnap, "status");
                        if (id != null) {
                            editor.putString(KEY_KNOWN_STATUS_PREFIX + currentUid + "_" + id, status == null ? "" : status);
                        }
                    }
                } catch (Exception ignored) { }
                editor.apply();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { /* ignore */ }
        });
    }

    // --------------------------------------------------------------------------------------------
    // Search & Realtime DB (copied/merged from reference search implementation)
    // --------------------------------------------------------------------------------------------

    private void loadProjectsFromRealtimeDb() {
        if (projectsRef == null) return;
        projectsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allProjects.clear();

                for (DataSnapshot child : snapshot.getChildren()) {
                    if (child.hasChild("title") || child.hasChild("projectTitle")) {
                        String id = child.getKey();
                        String title = safe(child.child("title").getValue());
                        if (title.isEmpty()) title = safe(child.child("projectTitle").getValue());

                        String type1 = safe(child.child("projectType1").getValue());
                        String level = safe(child.child("projectLevel").getValue());
                        String abs = safe(child.child("abstract").getValue());

                        String subtitle = buildSubtitle(type1, level, abs);
                        allProjects.add(new Project(id, title, subtitle));
                    }
                }

                renderResults(searchEt.getText() != null ? searchEt.getText().toString() : "");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to load projects", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String safe(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String buildSubtitle(String type1, String level, String abstractText) {
        StringBuilder sb = new StringBuilder();
        if (!type1.isEmpty()) sb.append(type1);
        if (!level.isEmpty()) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append(level);
        }
        if (sb.length() == 0 && !abstractText.isEmpty()) {
            String s = abstractText.trim();
            if (s.length() > 80) s = s.substring(0, 80) + "…";
            sb.append(s);
        }
        return sb.toString();
    }

    private void renderResults(String query) {
        resultsContainer.removeAllViews();

        String q = query.trim().toLowerCase(Locale.getDefault());
        if (q.isEmpty()) {
            return;
        }

        List<Project> matches = new ArrayList<>();
        for (Project p : allProjects) {
            if (p.title.toLowerCase(Locale.getDefault()).contains(q)) {
                matches.add(p);
            }
        }

        if (matches.isEmpty()) {
            resultsContainer.addView(makeInfoText("No projects found"));
            return;
        }

        for (Project p : matches) {
            resultsContainer.addView(makeProjectCard(p));
        }
    }

    private View makeProjectCard(Project p) {
        CardView card = new CardView(requireContext());
        CardView.LayoutParams cardLp = new CardView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(0, dp(8), 0, dp(8));
        card.setLayoutParams(cardLp);
        card.setRadius(dp(12));
        card.setUseCompatPadding(true);
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(0xFFF5F5F5); // light gray background

        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView titleTv = new TextView(requireContext());
        titleTv.setText(p.title);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleTv.setTypeface(titleTv.getTypeface(), android.graphics.Typeface.BOLD);
        titleTv.setTextColor(0xFF000000);

        TextView subTv = new TextView(requireContext());
        subTv.setText(p.subtitle);
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subTv.setTextColor(0xFF000000);

        box.addView(titleTv);
        if (p.subtitle != null && !p.subtitle.isEmpty()) box.addView(subTv);

        card.addView(box);

        // Card click → go to ProjectSummaryActivity
        card.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AllProjectSummaryActivity.class);
            intent.putExtra("projectId", p.id);
            startActivity(intent);
        });

        return card;
    }

    private View makeInfoText(String message) {
        TextView tv = new TextView(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, 0);
        tv.setLayoutParams(lp);
        tv.setText(message);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTextColor(0xFF6E6E6E);
        return tv;
    }

    // --------------------------------------------------------------------------------------------
    // Helpers (kept minimal so nothing else changes)
    // --------------------------------------------------------------------------------------------

    private SharedPreferences getPrefs() {
        return requireContext().getSharedPreferences(BADGE_PREF, Context.MODE_PRIVATE);
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private static boolean isAcceptedOrRejected(String status) {
        if (status == null) return false;
        String s = status.trim().toLowerCase(Locale.ROOT);
        return ACCEPTED.contains(s) || REJECTED.contains(s);
    }

    private static Long asLong(Object o) {
        try {
            if (o instanceof Long) return (Long) o;
            if (o instanceof Integer) return ((Integer) o).longValue();
            if (o instanceof Double) return ((Double) o).longValue();
            if (o != null) return Long.parseLong(String.valueOf(o));
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Normalize timestamp values to milliseconds.
     * If the stored value looks like seconds (less than 1e12) we convert it to ms.
     * Returns -1 when input is null.
     */
    private static long toMillis(Long t) {
        if (t == null) return -1L;
        // if it's clearly in seconds, convert to ms
        if (t < 1_000_000_000_000L) {
            return t * 1000L;
        }
        return t;
    }

    private static String val(DataSnapshot s, String key) {
        try { Object v = s.child(key).getValue(); return v == null ? null : String.valueOf(v).trim(); }
        catch (Exception e) { return null; }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    // Custom ImageSpan that vertically centers the drawable relative to the menu item line
    private static class CenteredImageSpan extends ImageSpan {
        public CenteredImageSpan(Context context, android.graphics.Bitmap b) {
            super(context, b);
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, Paint paint) {
            Drawable d = getDrawable();
            canvas.save();
            // center vertically in the line
            int transY = top + ((bottom - top) - d.getBounds().bottom) / 2;
            canvas.translate(x, transY);
            d.draw(canvas);
            canvas.restore();
        }
    }

    // Model
    private static class Project {
        final String id;
        final String title;
        final String subtitle;
        Project(String id, String title, String subtitle) {
            this.id = id == null ? "" : id;
            this.title = title == null ? "" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
        }
    }
}
