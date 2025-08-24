package com.example.acadlink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
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
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * DownloadsActivity
 *
 * Shows only downloads that belong to the current signed-in user.
 * Sources:
 *  - Primary: Firebase Realtime Database at /userDownloads/{currentUid} (preferred)
 *  - Fallback: local SharedPreferences "downloads_pref" -> "downloads_list" (but only entries where downloaderUid matches)
 *
 * IMPORTANT: kept view IDs and layout expectations the same (RecyclerView id R.id.card; item layout must include
 * tv_title, tv_uploader, tv_downloaded). Minimal changes: only the data source/filter logic.
 */
public class DownloadsActivity extends AppCompatActivity {

    private static final String TAG = "DownloadsActivity";
    private RecyclerView rv;
    private final List<DownloadRec> items = new ArrayList<>();

    // current user identifiers (for filtering / firebase path)
    private String currentUid = null;
    private String currentUserEmail = null;
    private String currentUserName = null;

    // firebase listener ref (so we could detach if needed)
    private DatabaseReference userDownloadsRef;
    private ValueEventListener userDownloadsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_downloads);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return insets;
        });

        ImageButton back = findViewById(R.id.toolbarBackBtn);
        if (back != null) back.setOnClickListener(v -> finish());

        TextView title = findViewById(R.id.toolbarTitleTv);
        if (title != null) title.setText("Downloads");

        ImageButton menuBtn = findViewById(R.id.toolbarMenuBtns);
        if (menuBtn != null) {
            menuBtn.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(DownloadsActivity.this, menuBtn);
                popup.getMenu().add(0, 1, 0, "Clear downloads");
                popup.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == 1) {
                        try {
                            SharedPreferences prefs = getSharedPreferences("downloads_pref", MODE_PRIVATE);
                            prefs.edit().remove("downloads_list").apply();
                            Toast.makeText(DownloadsActivity.this, "Local downloads cleared", Toast.LENGTH_SHORT).show();
                            load(); // reload after clearing local prefs
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to clear downloads_pref", e);
                        }
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }

        rv = findViewById(R.id.card); // keep same id as before
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new Adapter());

        FirebaseUser fu = FirebaseAuth.getInstance().getCurrentUser();
        if (fu == null) {
            Toast.makeText(this, "Please sign in to see your downloads", Toast.LENGTH_LONG).show();
            // still try to load local filtered prefs (will be empty)
            load();
            return;
        }

        currentUid = fu.getUid();
        currentUserEmail = fu.getEmail();
        currentUserName = fu.getDisplayName();

        // load items (server first, then local prefs merge)
        load();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // detach firebase listener if set
        try {
            if (userDownloadsRef != null && userDownloadsListener != null) userDownloadsRef.removeEventListener(userDownloadsListener);
        } catch (Exception ignored) {}
    }

    /**
     * Load downloads from firebase (/userDownloads/{uid}) and local prefs (filtered).
     * Ensures only entries belonging to currentUid are shown.
     */
    private void load() {
        items.clear();

        // 1) Try to read server-side userDownloads (preferred). If signed in, attach listener.
        if (currentUid != null) {
            userDownloadsRef = FirebaseDatabase.getInstance().getReference("userDownloads").child(currentUid);
            // detach previous if any
            if (userDownloadsListener != null) {
                try { userDownloadsRef.removeEventListener(userDownloadsListener); } catch (Exception ignored) {}
            }
            userDownloadsListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    items.clear();
                    try {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            DownloadRec r = parseDownloadSnapshot(child);
                            if (r != null) items.add(r);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to parse userDownloads snapshot", e);
                    }

                    // Merge local SharedPreferences entries that belong to currentUid (avoid duplicates)
                    mergeLocalPrefsForCurrentUser();

                    // sort newest first
                    Collections.sort(items, Comparator.comparingLong((DownloadRec rr) -> rr.downloadedAt).reversed());
                    if (rv.getAdapter() != null) rv.getAdapter().notifyDataSetChanged();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "userDownloads listener cancelled: " + error);
                    // fallback to local prefs only
                    items.clear();
                    mergeLocalPrefsForCurrentUser();
                    Collections.sort(items, Comparator.comparingLong((DownloadRec rr) -> rr.downloadedAt).reversed());
                    if (rv.getAdapter() != null) rv.getAdapter().notifyDataSetChanged();
                }
            };
            userDownloadsRef.addValueEventListener(userDownloadsListener);
            return;
        }

        // If not signed in, or currentUid null: only load local prefs (but don't show other users' entries)
        mergeLocalPrefsForCurrentUser();
        Collections.sort(items, Comparator.comparingLong((DownloadRec rr) -> rr.downloadedAt).reversed());
        if (rv.getAdapter() != null) rv.getAdapter().notifyDataSetChanged();
    }

    // read local SharedPreferences downloads and add only those with downloaderUid == currentUid
    private void mergeLocalPrefsForCurrentUser() {
        try {
            SharedPreferences prefs = getSharedPreferences("downloads_pref", MODE_PRIVATE);
            String raw = prefs.getString("downloads_list", "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                // find downloader id from a number of potential keys
                String downloaderId = firstNonEmptyString(
                        optStringSafe(o, "downloaderUid"),
                        optStringSafe(o, "downloaderId"),
                        optStringSafe(o, "downloadedByUid"),
                        optStringSafe(o, "downloadedUid")
                );
                // only include entries that explicitly belong to currentUid
                if (currentUid != null && !currentUid.isEmpty()) {
                    if (downloaderId == null || downloaderId.isEmpty()) continue;
                    if (!currentUid.equals(downloaderId)) continue;
                } else {
                    // if no currentUid (user not signed in) skip local entries that have downloader metadata.
                    continue;
                }

                DownloadRec r = new DownloadRec();
                r.projectTitle = firstNonEmptyString(optStringSafe(o, "projectTitle"), "Project");
                r.uploaderName = firstNonEmptyString(optStringSafe(o, "uploaderName"), optStringSafe(o, "uploadedBy"), "Unknown");
                r.folder = optStringSafe(o, "folder");
                long at = 0;
                try { at = o.optLong("downloadedAt", 0); } catch (Exception ignored) {}
                if (at <= 0) at = System.currentTimeMillis();
                r.downloadedAt = at;
                items.add(r);
            }
        } catch (JSONException e) {
            Log.w(TAG, "Failed to read local downloads_pref", e);
        } catch (Exception ignored) {}
    }

    // helper to parse a DataSnapshot from /userDownloads/{uid}/{downloadKey}
    private DownloadRec parseDownloadSnapshot(DataSnapshot child) {
        try {
            DownloadRec r = new DownloadRec();
            Object pv = child.child("projectTitle").getValue();
            r.projectTitle = pv == null ? "Project" : String.valueOf(pv);

            Object up = child.child("uploaderName").getValue();
            r.uploaderName = up == null ? "Unknown" : String.valueOf(up);

            Object folder = child.child("folderName").getValue();
            if (folder == null) folder = child.child("folder").getValue(); // fallback
            r.folder = folder == null ? "" : String.valueOf(folder);

            // downloadedAt can be Long, Double, String, or a Map (if server timestamp not resolved yet)
            Object atObj = child.child("downloadedAt").getValue();
            r.downloadedAt = parseLongTimestamp(atObj);
            if (r.downloadedAt <= 0) r.downloadedAt = System.currentTimeMillis();

            return r;
        } catch (Exception e) {
            Log.w(TAG, "parseDownloadSnapshot failed", e);
            return null;
        }
    }

    // robust parsing of timestamp values from firebase snapshots
    private static long parseLongTimestamp(Object o) {
        if (o == null) return 0L;
        try {
            if (o instanceof Long) return (Long) o;
            if (o instanceof Integer) return ((Integer) o).longValue();
            if (o instanceof Double) return ((Double) o).longValue();
            if (o instanceof String) {
                String s = (String) o;
                if (s.isEmpty()) return 0L;
                return Long.parseLong(s);
            }
            // if it's a Map (rare, unresolved ServerValue) try to get ".sv" keys - not needed most times
            return 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String optStringSafe(JSONObject o, String key) {
        try {
            if (o == null || key == null) return "";
            String v = o.optString(key, "");
            if (v == null) return "";
            return v;
        } catch (Exception e) {
            return "";
        }
    }

    // Varargs helper: returns first non-empty trimmed string, or empty string
    private static String firstNonEmptyString(String... s) {
        if (s == null) return "";
        for (String x : s) {
            if (x != null && !x.trim().isEmpty()) return x.trim();
        }
        return "";
    }

    private static class DownloadRec {
        String projectTitle;
        String uploaderName;
        String folder;
        long downloadedAt;
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // item layout must exist in project (kept same as before)
            View v = LayoutInflater.from(parent.getContext()).inflate(findItemLayout(parent), parent, false);
            return new VH(v);
        }

        private int findItemLayout(ViewGroup parent) {
            int id = parent.getResources().getIdentifier("item_download", "layout", getPackageName());
            if (id == 0) id = parent.getResources().getIdentifier("item_download_entry", "layout", getPackageName());
            if (id == 0) id = parent.getResources().getIdentifier("item_download_entry_layout", "layout", getPackageName());
            if (id == 0) id = parent.getResources().getIdentifier("item_download_card", "layout", getPackageName());
            if (id == 0) id = parent.getResources().getIdentifier("item_download_layout", "layout", getPackageName());
            if (id == 0) id = parent.getResources().getIdentifier("item_downloadentry", "layout", getPackageName());
            // last fallback to a simple layout you must already have in your project
            if (id == 0) id = getResources().getIdentifier("item_download", "layout", getPackageName());
            if (id == 0) throw new IllegalStateException("No item layout found (expected item_download.xml or similar).");
            return id;
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            DownloadRec r = items.get(pos);
            h.title.setText("Project Title: " + r.projectTitle);
            h.uploader.setText("Uploaded by: " + r.uploaderName);

            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(r.downloadedAt);
            String ts = DateFormat.format("dd MMM yyyy h:mm:ss a", cal).toString();

            String folderText = r.folder != null && !r.folder.trim().isEmpty() ? r.folder : r.projectTitle;
            h.downloaded.setText("Downloaded: " + ts + "  (Folder: " + folderText + ")");

            if (h.card != null) {
                h.card.setCardBackgroundColor(0xFFF5F5F5);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView title, uploader, downloaded;
            final CardView card;
            VH(View v) {
                super(v);
                title = v.findViewById(R.id.tv_title);
                uploader = v.findViewById(R.id.tv_uploader);
                downloaded = v.findViewById(R.id.tv_downloaded);
                card = findCardViewInView(v);
            }
        }
    }

    /**
     * Recursively search the view tree for the first CardView instance.
     * Returns null if none found.
     */
    private CardView findCardViewInView(View v) {
        if (v == null) return null;
        if (v instanceof CardView) return (CardView) v;
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                CardView cv = findCardViewInView(vg.getChildAt(i));
                if (cv != null) return cv;
            }
        }
        return null;
    }
}
