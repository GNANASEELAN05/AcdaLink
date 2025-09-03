package com.example.acadlink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
 * Adds WhatsApp-like badges without changing IDs or existing flows:
 *  - Small numeric badge on the toolbar menu button.
 *  - Tiny dot next to relevant popup items ("Request Sent"/"Request Received"/"Request To Faculty").
 *
 * Everything else stays the same.  (Only minimal fixes applied so the dot centers and clicks work.)
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
    private static final String KEY_LAST_SEEN_RECEIVED_PREFIX = "last_seen_received_";
    private static final String KEY_LAST_SEEN_FACULTY_PREFIX = "last_seen_faculty_";

    // accepted / rejected words (robust to variations)
    private static final Set<String> ACCEPTED = new HashSet<>(Arrays.asList(
            "accepted","approved","granted","allow","allowed","ready","true","yes"
    ));
    private static final Set<String> REJECTED = new HashSet<>(Arrays.asList(
            "rejected","declined","denied","no","notallowed","not_allowed"
    ));

    private TextView menuBadgeView;
    private int countSentUpdates = 0;       // accepted/rejected AFTER last seen
    private int countReceivedPending = 0;   // current pending incoming (new since last seen)
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

            popup.setOnMenuItemClickListener(item -> {
                // Use contains(...) checks so decorated titles (with dot span) still match
                String rawTitle = String.valueOf(item.getTitle()).trim().toLowerCase(Locale.ROOT);

                if (rawTitle.contains("my projects")) {
                    startActivity(new Intent(getActivity(), MyProjects.class));
                    return true;
                } else if (rawTitle.contains("all projects")) {
                    startActivity(new Intent(getActivity(), AllProjectsActivity.class));
                    return true;
                } else if (rawTitle.contains("request to faculty")) {
                    // mark faculty seen so dot disappears when opening
                    markFacultySeenNow();
                    countFaculty = 0;
                    updateMenuBadgeNow();
                    startActivity(new Intent(getActivity(), RequestToFacultyActivity.class));
                    return true;
                } else if (rawTitle.contains("request received")) {
                    // mark "received" seen before opening so the dot is cleared
                    markReceivedSeenNow();
                    countReceivedPending = 0;
                    updateMenuBadgeNow();
                    startActivity(new Intent(getActivity(), RequestReceivedActivity.class));
                    return true;
                } else if (rawTitle.contains("request sent")) {
                    // Mark "seen" for SENT before opening, so accepted/rejected updates clear the badge
                    markSentSeenNow();
                    // Instant UI feedback
                    countSentUpdates = 0;
                    updateMenuBadgeNow();
                    startActivity(new Intent(getActivity(), RequestSentActivity.class));
                    return true;
                } else if (rawTitle.contains("downloads")) {
                    startActivity(new Intent(getActivity(), DownloadsActivity.class));
                    return true;
                }

                Toast.makeText(getContext(), item.getTitle(), Toast.LENGTH_SHORT).show();
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

        // Ensure badge is on top
        menuBadgeView.bringToFront();

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
                        Long up = asLong(projSnap.child("updatedAt").getValue());
                        if (isAcceptedOrRejected(status) && (up == null || up > lastSeen)) {
                            c++;
                        }
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
                long lastSeen = getPrefs().getLong(KEY_LAST_SEEN_RECEIVED_PREFIX + currentUid, 0L);
                int pending = 0;
                try {
                    for (DataSnapshot projSnap : snapshot.getChildren()) {
                        // structure: downloadRequestsReceived/<uid>/<projectId>/<requesterUid> = {...}
                        for (DataSnapshot requesterSnap : projSnap.getChildren()) {
                            String status = val(requesterSnap, "status");
                            if (status == null || status.trim().isEmpty() || "pending".equalsIgnoreCase(status)) {
                                // try updatedAt then createdAt
                                Long up = asLong(requesterSnap.child("updatedAt").getValue());
                                if (up == null) up = asLong(requesterSnap.child("createdAt").getValue());

                                if (up == null) {
                                    // If there is no timestamp, treat as new only if user never saw received before.
                                    if (lastSeen == 0L) pending++;
                                } else {
                                    if (up > lastSeen) pending++;
                                }
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
            if (title.toLowerCase(Locale.ROOT).contains("request sent")) {
                showDot = countSentUpdates > 0;
            } else if (title.toLowerCase(Locale.ROOT).contains("request received")) {
                showDot = countReceivedPending > 0;
            } else if (title.toLowerCase(Locale.ROOT).contains("request to faculty")) {
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
        // Build "Title  [dot]" using an ImageSpan with a small #03A9F4 circle and vertically center it.
        SpannableStringBuilder sb = new SpannableStringBuilder(title + "  .");
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(0xFF03A9F4);
        int size = dp(8);
        dot.setSize(size, size);
        android.graphics.Bitmap bm = drawableToBitmap(dot);

        CenteredImageSpan span = new CenteredImageSpan(requireContext(), bm);
        int start = sb.length() - 1;
        int end = sb.length();
        sb.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    // Custom ImageSpan that vertically centers the drawable in the menu item's text line.
    private static class CenteredImageSpan extends ImageSpan {
        public CenteredImageSpan(Context context, android.graphics.Bitmap bitmap) {
            super(context, bitmap);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
                         float x, int top, int y, int bottom, @NonNull Paint paint) {
            Drawable b = getDrawable();
            canvas.save();
            // center vertically between top and bottom
            int transY = ((bottom - top) - b.getBounds().bottom) / 2 + top;
            canvas.translate(x, transY);
            b.draw(canvas);
            canvas.restore();
        }
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
        getPrefs().edit().putLong(KEY_LAST_SEEN_SENT_PREFIX + currentUid, System.currentTimeMillis()).apply();
    }

    private void markReceivedSeenNow() {
        if (currentUid == null) return;
        getPrefs().edit().putLong(KEY_LAST_SEEN_RECEIVED_PREFIX + currentUid, System.currentTimeMillis()).apply();
    }

    private void markFacultySeenNow() {
        if (currentUid == null) return;
        getPrefs().edit().putLong(KEY_LAST_SEEN_FACULTY_PREFIX + currentUid, System.currentTimeMillis()).apply();
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

    private static String val(DataSnapshot s, String key) {
        try { Object v = s.child(key).getValue(); return v == null ? null : String.valueOf(v).trim(); }
        catch (Exception e) { return null; }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    // Minimal placeholder to retain existing fields/usage visible in your file
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
