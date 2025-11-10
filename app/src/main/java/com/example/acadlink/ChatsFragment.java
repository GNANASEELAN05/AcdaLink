package com.example.acadlink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ChatsFragment
 *
 * - Preserves all IDs, layouts and click behaviour.
 * - Ensures each call/sms/whatsapp entry is treated as a separate historical record.
 * - Keeps a persistent archive (chats_history_<uid>) so entries are not lost when primary key is overwritten.
 * - Provides a safe append helper: appendChatEntryToPrefs(...) to add new entries without replacing older ones.
 */
public class ChatsFragment extends Fragment {

    private RecyclerView rvChats;
    private TextView sampleChatText;
    private ChatAdapter adapter;

    public ChatsFragment() { }

    public static ChatsFragment newInstance(String param1, String param2) {
        ChatsFragment fragment = new ChatsFragment();
        Bundle args = new Bundle();
        args.putString("param1", param1);
        args.putString("param2", param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_chats, container, false);
        sampleChatText = v.findViewById(R.id.sampleChatText);
        rvChats = v.findViewById(R.id.rvChats);

        rvChats.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ChatAdapter(requireContext());
        rvChats.setAdapter(adapter);

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadChats();
    }

    /**
     * Load chats from SharedPreferences and build a display list that contains date headers
     * placed between chat items for different dates (Today/Yesterday/dd MMM yyyy).
     *
     * This method maintains an archive key (chats_history_<uid>) to preserve older entries even
     * when the primary key (chats_<uid>) is overwritten by other code.
     */
    private void loadChats() {
        try {
            String uid = "guest";
            if (FirebaseAuth.getInstance() != null && FirebaseAuth.getInstance().getCurrentUser() != null) {
                uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            }

            SharedPreferences prefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            String keyPrimary = "chats_" + uid;
            String keyHistory = "chats_history_" + uid;

            String rawPrimary = prefs.getString(keyPrimary, "[]");
            if (rawPrimary == null) rawPrimary = "[]";
            rawPrimary = rawPrimary.trim();

            String rawHistory = prefs.getString(keyHistory, "[]");
            if (rawHistory == null) rawHistory = "[]";
            rawHistory = rawHistory.trim();

            // Parse both primary and history into arrays
            JSONArray primaryArr = robustlyParseToArray(rawPrimary);
            JSONArray historyArr = robustlyParseToArray(rawHistory);

            // Merge: keep all unique entries using composite key (number + ts + via + name)
            // But we preserve duplicates if they have different timestamps.
            List<JSONObject> mergedList = new ArrayList<>();
            Set<String> fingerprintSet = new HashSet<>();

            // Helper to produce fingerprint string
            java.util.function.Consumer<JSONObject> addIfNew = (obj) -> {
                try {
                    String number = obj.optString("number", "");
                    long ts = obj.optLong("ts", 0L);
                    String via = obj.optString("via", "");
                    String name = obj.optString("name", "");
                    String fp = number + "|" + ts + "|" + via + "|" + name;
                    if (!fingerprintSet.contains(fp)) {
                        fingerprintSet.add(fp);
                        mergedList.add(obj);
                    }
                } catch (Exception ignored) { }
            };

            // First: add all entries from history (old first)
            for (int i = 0; i < historyArr.length(); i++) {
                JSONObject o = historyArr.optJSONObject(i);
                if (o != null) addIfNew.accept(o);
            }

            // Next: add entries from primary (newer) — this ensures primary entries are present and if they are new they will be appended
            for (int i = 0; i < primaryArr.length(); i++) {
                JSONObject o = primaryArr.optJSONObject(i);
                if (o != null) addIfNew.accept(o);
            }

            // If primary was an object (legacy map) and history was empty, we migrate primary into history by saving it into history key.
            // Also if mergedList contains items not present in historyArr, we append them to historyArr and save back.
            boolean historyNeedsSave = false;
            if (rawPrimary.startsWith("{") && historyArr.length() == 0) {
                // Save migrated primary into history so future loads have archive
                historyNeedsSave = true;
            } else {
                // If any primary entries are absent in history, append them to history
                // Build set of history fingerprints to detect
                Set<String> histFp = new HashSet<>();
                for (int i = 0; i < historyArr.length(); i++) {
                    JSONObject o = historyArr.optJSONObject(i);
                    if (o == null) continue;
                    String number = o.optString("number", "");
                    long ts = o.optLong("ts", 0L);
                    String via = o.optString("via", "");
                    String name = o.optString("name", "");
                    String fp = number + "|" + ts + "|" + via + "|" + name;
                    histFp.add(fp);
                }
                // check primary items against histFp
                for (int i = 0; i < primaryArr.length(); i++) {
                    JSONObject o = primaryArr.optJSONObject(i);
                    if (o == null) continue;
                    String number = o.optString("number", "");
                    long ts = o.optLong("ts", 0L);
                    String via = o.optString("via", "");
                    String name = o.optString("name", "");
                    String fp = number + "|" + ts + "|" + via + "|" + name;
                    if (!histFp.contains(fp)) {
                        // Append this primary item into historyArr for persistence
                        historyArr.put(o);
                        historyNeedsSave = true;
                        histFp.add(fp);
                    }
                }
            }

            if (historyNeedsSave) {
                try {
                    prefs.edit().putString(keyHistory, historyArr.toString()).apply();
                } catch (Exception ignored) {}
            }

            // Now we have mergedList with unique entries (history first, primary next) — sort by timestamp descending
            List<ChatItem> items = new ArrayList<>();
            for (JSONObject o : mergedList) {
                try {
                    String name = o.optString("name", "");
                    String number = o.optString("number", "");
                    String via = o.optString("via", "");
                    long ts = o.optLong("ts", 0L);
                    if (via == null || via.trim().isEmpty()) via = "sms";
                    items.add(new ChatItem(name, number, via.toLowerCase(Locale.ROOT), ts));
                } catch (Exception ignored) {}
            }

            // Sort newest first
            Collections.sort(items, (a, b) -> Long.compare(b.ts, a.ts));

            if (items.isEmpty()) {
                sampleChatText.setVisibility(View.VISIBLE);
                rvChats.setVisibility(View.GONE);
                adapter.setDisplayItems(new ArrayList<>()); // clear adapter
            } else {
                sampleChatText.setVisibility(View.GONE);
                rvChats.setVisibility(View.VISIBLE);

                // group items by date label (preserve order)
                Map<String, List<ChatItem>> grouped = new LinkedHashMap<>();
                for (ChatItem it : items) {
                    String dateLabel = formatDateLabelForGrouping(it.ts);
                    List<ChatItem> grp = grouped.get(dateLabel);
                    if (grp == null) {
                        grp = new ArrayList<>();
                        grouped.put(dateLabel, grp);
                    }
                    grp.add(it);
                }

                // build display list interleaving headers and items
                List<DisplayItem> display = new ArrayList<>();
                for (Map.Entry<String, List<ChatItem>> e : grouped.entrySet()) {
                    String header = e.getKey();
                    display.add(DisplayItem.header(header));
                    for (ChatItem ci : e.getValue()) display.add(DisplayItem.item(ci));
                }

                adapter.setDisplayItems(display);
            }
        } catch (Exception ex) {
            sampleChatText.setVisibility(View.VISIBLE);
            rvChats.setVisibility(View.GONE);
            adapter.setDisplayItems(new ArrayList<>());
        }
    }

    /**
     * Try to interpret the stored raw string as:
     * - a JSONArray of {name, number, via, ts}, OR
     * - a JSONObject whose values are those objects (legacy map keyed by number), OR
     * - a legacy single object (convert into a single-element array).
     *
     * The function guarantees a JSONArray return (never null).
     */
    private JSONArray robustlyParseToArray(String raw) {
        if (raw == null) raw = "[]";
        raw = raw.trim();
        try {
            // If already an array, return as-is after simple validation
            if (raw.startsWith("[")) {
                JSONArray a = new JSONArray(raw);
                return a;
            }

            // If it's an object, try to pull out values.
            if (raw.startsWith("{")) {
                JSONObject obj = new JSONObject(raw);

                JSONArray result = new JSONArray();

                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    try {
                        Object val = obj.get(key);
                        if (val instanceof JSONObject) {
                            JSONObject v = (JSONObject) val;
                            if (!v.has("number")) v.put("number", key);
                            if (!v.has("via")) v.put("via", "call");
                            if (!v.has("ts")) v.put("ts", v.optLong("ts", 0L));
                            result.put(v);
                        } else if (val instanceof JSONArray) {
                            JSONArray nested = (JSONArray) val;
                            for (int i = 0; i < nested.length(); i++) {
                                Object e = nested.get(i);
                                if (e instanceof JSONObject) result.put((JSONObject) e);
                            }
                        } else {
                            JSONObject created = new JSONObject();
                            created.put("number", key);
                            created.put("name", "");
                            created.put("via", "call");
                            long ts = 0L;
                            if (val instanceof Number) ts = ((Number) val).longValue();
                            created.put("ts", ts);
                            result.put(created);
                        }
                    } catch (Exception ignored) {}
                }
                return result;
            }

            // Fallback: try to create a single-element array by parsing the string as an object
            JSONObject single = new JSONObject(raw);
            JSONArray a = new JSONArray();
            a.put(single);
            return a;
        } catch (Exception ex) {
            return new JSONArray();
        }
    }

    // -------------------- models for adapter --------------------
    private static class ChatItem {
        String name;
        String number;
        String via; // "sms" or "call" or "whatsapp"
        long ts;

        ChatItem(String n, String num, String v, long t) {
            name = n;
            number = num;
            via = v;
            ts = t;
        }
    }

    private static class DisplayItem {
        final boolean isHeader;
        final String headerText; // valid if isHeader
        final ChatItem chatItem;  // valid if !isHeader

        private DisplayItem(boolean isHeader, String headerText, ChatItem item) {
            this.isHeader = isHeader;
            this.headerText = headerText;
            this.chatItem = item;
        }

        static DisplayItem header(String text) {
            return new DisplayItem(true, text, null);
        }

        static DisplayItem item(ChatItem it) {
            return new DisplayItem(false, null, it);
        }
    }

    /**
     * Returns "Today", "Yesterday" or "dd MMM yyyy" for grouping headers.
     * This is used for inserting inline headers between list items (like WhatsApp).
     */
    private String formatDateLabelForGrouping(long tsMillis) {
        if (tsMillis <= 0) return "";
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(tsMillis);

        Calendar now = Calendar.getInstance();

        // reset to midnight for comparison
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

    // -------------------- Adapter --------------------
    private class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;

        private final List<DisplayItem> displayItems = new ArrayList<>();
        private final Context ctx;

        ChatAdapter(Context c) { ctx = c; }

        void setDisplayItems(List<DisplayItem> items) {
            displayItems.clear();
            displayItems.addAll(items);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            DisplayItem di = displayItems.get(position);
            return di.isHeader ? TYPE_HEADER : TYPE_ITEM;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater li = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                // use the header xml you provided (date header)
                View v = li.inflate(R.layout.item_date_header, parent, false); // ensure name matches your header xml filename
                return new HeaderVH(v);
            } else {
                View v = li.inflate(R.layout.chat_item, parent, false);
                return new ItemVH(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            DisplayItem di = displayItems.get(position);
            if (di.isHeader) {
                HeaderVH hv = (HeaderVH) holder;
                hv.dateText.setText(di.headerText);
            } else {
                ItemVH iv = (ItemVH) holder;
                ChatItem it = di.chatItem;

                String displayTo = (it.name == null || it.name.trim().isEmpty()) ? it.number : it.name;
                iv.title.setText("To: " + displayTo);

                String viaLabel = "via SMS";
                if ("call".equalsIgnoreCase(it.via)) viaLabel = "via Call";
                else if ("whatsapp".equalsIgnoreCase(it.via)) viaLabel = "via WhatsApp";
                iv.subtitle.setText(viaLabel);

                // icon loading (SMS, call or WhatsApp)
                try {
                    PackageManager pm = ctx.getPackageManager();

                    Drawable icon = null;
                    if (it.via != null && it.via.equalsIgnoreCase("call")) {
                        Intent dialProbe = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:123"));
                        java.util.List<ResolveInfo> dialHandlers = pm.queryIntentActivities(dialProbe, 0);
                        if (dialHandlers != null && !dialHandlers.isEmpty()) {
                            ResolveInfo ri = dialHandlers.get(0);
                            if (ri != null && ri.activityInfo != null) {
                                String pkg = ri.activityInfo.packageName;
                                icon = safeLoadIcon(pm, pkg);
                            }
                        }
                        if (icon == null) icon = ContextCompat.getDrawable(ctx, android.R.drawable.ic_menu_call);
                    } else if (it.via != null && it.via.equalsIgnoreCase("whatsapp")) {
                        try {
                            Intent waProbe = new Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=1234567890"));
                            List<ResolveInfo> waHandlers = pm.queryIntentActivities(waProbe, 0);
                            if (waHandlers != null && !waHandlers.isEmpty()) {
                                ResolveInfo ri = waHandlers.get(0);
                                if (ri != null && ri.activityInfo != null) {
                                    String pkg = ri.activityInfo.packageName;
                                    icon = safeLoadIcon(pm, pkg);
                                }
                            }
                        } catch (Exception ignored) {}

                        if (icon == null) {
                            String[] waPkgs = new String[] {"com.whatsapp", "com.whatsapp.w4b"};
                            for (String p : waPkgs) {
                                try {
                                    pm.getPackageInfo(p, 0);
                                    icon = safeLoadIcon(pm, p);
                                    if (icon != null) break;
                                } catch (Exception ignored) {}
                            }
                        }

                        if (icon == null) {
                            try {
                                icon = ContextCompat.getDrawable(ctx, R.drawable.whatsapp_logo);
                            } catch (Exception ignored) {
                                icon = ContextCompat.getDrawable(ctx, android.R.drawable.sym_action_chat);
                            }
                        }
                    } else {
                        String smsPkg = null;
                        try {
                            smsPkg = Telephony.Sms.getDefaultSmsPackage(ctx);
                        } catch (Exception ignored) { smsPkg = null; }

                        if (smsPkg == null) {
                            Intent smsProbe = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:1234567890"));
                            java.util.List<ResolveInfo> smsHandlers = pm.queryIntentActivities(smsProbe, 0);
                            if (smsHandlers != null && !smsHandlers.isEmpty()) {
                                ResolveInfo ri = smsHandlers.get(0);
                                if (ri != null && ri.activityInfo != null) smsPkg = ri.activityInfo.packageName;
                            }
                        }

                        if (smsPkg != null) icon = safeLoadIcon(pm, smsPkg);
                        if (icon == null) icon = ContextCompat.getDrawable(ctx, android.R.drawable.sym_action_chat);
                    }

                    Drawable rounded = toRoundedDrawable(icon, ctx);
                    iv.icon.setImageDrawable(rounded);
                } catch (Exception e) {
                    if (it.via != null && it.via.equalsIgnoreCase("call"))
                        iv.icon.setImageDrawable(toRoundedDrawable(ContextCompat.getDrawable(ctx, android.R.drawable.ic_menu_call), ctx));
                    else if (it.via != null && it.via.equalsIgnoreCase("whatsapp")) {
                        try {
                            iv.icon.setImageDrawable(toRoundedDrawable(ContextCompat.getDrawable(ctx, R.drawable.whatsapp_logo), ctx));
                        } catch (Exception ignored) {
                            iv.icon.setImageDrawable(toRoundedDrawable(ContextCompat.getDrawable(ctx, android.R.drawable.sym_action_chat), ctx));
                        }
                    } else
                        iv.icon.setImageDrawable(toRoundedDrawable(ContextCompat.getDrawable(ctx, android.R.drawable.sym_action_chat), ctx));
                }

                iv.itemView.setOnClickListener(v -> {
                    if (it.via != null && it.via.equalsIgnoreCase("call")) {
                        try {
                            Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                            dialIntent.setData(Uri.parse("tel:" + Uri.encode(it.number)));
                            ctx.startActivity(dialIntent);
                        } catch (Exception ex) {
                            Toast.makeText(ctx, "No dialer found", Toast.LENGTH_SHORT).show();
                        }
                    } else if (it.via != null && it.via.equalsIgnoreCase("whatsapp")) {
                        String digits = extractDigitsStatic(it.number);
                        if (digits.isEmpty()) {
                            Toast.makeText(ctx, "Invalid phone number for WhatsApp", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        PackageManager pm = ctx.getPackageManager();
                        String[] waPkgs = new String[] {"com.whatsapp", "com.whatsapp.w4b"};
                        String waChosen = null;
                        for (String p : waPkgs) {
                            try {
                                pm.getPackageInfo(p, 0);
                                waChosen = p;
                                break;
                            } catch (Exception ignored) {}
                        }

                        try {
                            if (waChosen != null) {
                                Intent waIntent = new Intent(Intent.ACTION_VIEW);
                                waIntent.setPackage(waChosen);
                                waIntent.setData(Uri.parse("whatsapp://send?phone=" + digits));
                                ctx.startActivity(waIntent);
                            } else {
                                Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + digits));
                                ctx.startActivity(web);
                            }
                        } catch (Exception ex) {
                            try {
                                Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + digits));
                                ctx.startActivity(web);
                            } catch (Exception ex2) {
                                Toast.makeText(ctx, "No WhatsApp/browser found", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } else {
                        try {
                            Intent intent = new Intent(Intent.ACTION_SENDTO);
                            intent.setData(Uri.parse("smsto:" + Uri.encode(it.number)));

                            String smsPkg = null;
                            try {
                                smsPkg = Telephony.Sms.getDefaultSmsPackage(ctx);
                            } catch (Exception ignored) { smsPkg = null; }

                            if (smsPkg == null) {
                                Intent smsProbe = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:1234567890"));
                                java.util.List<ResolveInfo> smsHandlers = ctx.getPackageManager().queryIntentActivities(smsProbe, 0);
                                if (smsHandlers != null && !smsHandlers.isEmpty()) {
                                    ResolveInfo ri = smsHandlers.get(0);
                                    if (ri != null && ri.activityInfo != null) smsPkg = ri.activityInfo.packageName;
                                }
                            }

                            if (smsPkg != null) intent.setPackage(smsPkg);
                            ctx.startActivity(intent);
                        } catch (Exception ex) {
                            Toast.makeText(ctx, "No SMS app found", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return displayItems.size();
        }

        // ----- ViewHolders -----
        class HeaderVH extends RecyclerView.ViewHolder {
            TextView dateText;
            HeaderVH(@NonNull View itemView) {
                super(itemView);
                dateText = itemView.findViewById(R.id.textDateHeader); // your header TextView id
            }
        }

        class ItemVH extends RecyclerView.ViewHolder {
            TextView title, subtitle;
            ImageView icon;
            ItemVH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tvChatTitle);
                subtitle = itemView.findViewById(R.id.tvChatSub);
                icon = itemView.findViewById(R.id.ivChatIcon);
            }
        }

        // ----- helpers -----
        private Drawable safeLoadIcon(PackageManager pm, String pkg) {
            if (pm == null || pkg == null) return null;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                if (ai != null) return ai.loadIcon(pm);
            } catch (Exception ignored) { }
            try {
                return pm.getApplicationIcon(pkg);
            } catch (Exception ignored) { }
            return null;
        }

        /**
         * Convert any Drawable to a circular RoundedBitmapDrawable.
         */
        private Drawable toRoundedDrawable(Drawable src, Context context) {
            if (src == null || context == null) return null;
            try {
                Bitmap bmp;
                if (src instanceof BitmapDrawable) {
                    bmp = ((BitmapDrawable) src).getBitmap();
                } else {
                    int w = src.getIntrinsicWidth() > 0 ? src.getIntrinsicWidth() : 128;
                    int h = src.getIntrinsicHeight() > 0 ? src.getIntrinsicHeight() : 128;
                    bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bmp);
                    src.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                    src.draw(canvas);
                }

                RoundedBitmapDrawable rounded = RoundedBitmapDrawableFactory.create(context.getResources(), bmp);
                rounded.setCircular(true);
                return rounded;
            } catch (Exception e) {
                return src;
            }
        }
    }

    private static String extractDigitsStatic(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        char[] cs = s.toCharArray();
        for (char c : cs) if (Character.isDigit(c)) sb.append(c);
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Public helper: appendChatEntryToPrefs
    // -------------------------------------------------------------------------
    /**
     * Append a chat/call entry to SharedPreferences without overwriting older entries.
     *
     * Call this method from your save logic instead of replacing the whole object/map.
     *
     * Example:
     *   ChatsFragment.appendChatEntryToPrefs(context, uid, "Alice", "+911234567890", "call", System.currentTimeMillis());
     *
     * This ensures each call is added as a distinct entry (no dedupe by number).
     */
    public static void appendChatEntryToPrefs(Context context, String uid, String name, String number, String via, long ts) {
        if (context == null) return;
        if (uid == null || uid.trim().isEmpty()) uid = "guest";
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String keyPrimary = "chats_" + uid;
        String keyHistory = "chats_history_" + uid;

        // We'll append to history (archive) to guarantee persistence; also store to primary as array for backward compatibility.
        try {
            // Ensure history array exists and append new item if not duplicate
            String rawHistory = prefs.getString(keyHistory, "[]");
            if (rawHistory == null) rawHistory = "[]";
            JSONArray hist = new JSONArray(rawHistory);

            // Build fingerprint to avoid exact duplicates
            String fpNew = (number != null ? number : "") + "|" + ts + "|" + (via != null ? via : "") + "|" + (name != null ? name : "");

            boolean found = false;
            for (int i = 0; i < hist.length(); i++) {
                JSONObject o = hist.optJSONObject(i);
                if (o == null) continue;
                String number0 = o.optString("number", "");
                long ts0 = o.optLong("ts", 0L);
                String via0 = o.optString("via", "");
                String name0 = o.optString("name", "");
                String fp0 = number0 + "|" + ts0 + "|" + via0 + "|" + name0;
                if (fp0.equals(fpNew)) { found = true; break; }
            }

            JSONObject newEntry = new JSONObject();
            newEntry.put("name", name != null ? name : "");
            newEntry.put("number", number != null ? number : "");
            newEntry.put("via", via != null ? via : "call");
            newEntry.put("ts", ts);

            if (!found) hist.put(newEntry);
            prefs.edit().putString(keyHistory, hist.toString()).apply();

            // Also ensure primary is a JSONArray and append there (so older code that reads primary as array still works)
            String rawPrimary = prefs.getString(keyPrimary, "[]");
            if (rawPrimary == null) rawPrimary = "[]";
            JSONArray prim;
            if (rawPrimary.trim().startsWith("[")) {
                prim = new JSONArray(rawPrimary);
            } else if (rawPrimary.trim().startsWith("{")) {
                // migrate object to array then append
                JSONArray migrated = new JSONArray();
                JSONObject obj = new JSONObject(rawPrimary);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    try {
                        String k = keys.next();
                        Object v = obj.opt(k);
                        if (v instanceof JSONObject) {
                            JSONObject vv = (JSONObject) v;
                            if (!vv.has("number")) vv.put("number", k);
                            migrated.put(vv);
                        } else {
                            JSONObject created = new JSONObject();
                            created.put("number", k);
                            created.put("name", "");
                            created.put("via", "call");
                            long candidateTs = 0L;
                            if (v instanceof Number) candidateTs = ((Number) v).longValue();
                            created.put("ts", candidateTs);
                            migrated.put(created);
                        }
                    } catch (Exception ignored) {}
                }
                prim = migrated;
            } else {
                prim = new JSONArray();
            }

            // Avoid duplicating same exact entry in primary
            boolean existsInPrim = false;
            for (int i = 0; i < prim.length(); i++) {
                JSONObject o = prim.optJSONObject(i);
                if (o == null) continue;
                String number0 = o.optString("number", "");
                long ts0 = o.optLong("ts", 0L);
                String via0 = o.optString("via", "");
                String name0 = o.optString("name", "");
                String fp0 = number0 + "|" + ts0 + "|" + via0 + "|" + name0;
                if (fp0.equals(fpNew)) { existsInPrim = true; break; }
            }
            if (!existsInPrim) prim.put(newEntry);
            prefs.edit().putString(keyPrimary, prim.toString()).apply();

        } catch (Exception ex) {
            // fallback: overwrite history with single-entry array (best-effort)
            try {
                JSONArray arr = new JSONArray();
                JSONObject newEntry = new JSONObject();
                newEntry.put("name", name != null ? name : "");
                newEntry.put("number", number != null ? number : "");
                newEntry.put("via", via != null ? via : "call");
                newEntry.put("ts", ts);
                arr.put(newEntry);
                prefs.edit().putString(keyHistory, arr.toString()).apply();
                prefs.edit().putString("chats_" + uid, arr.toString()).apply();
            } catch (Exception ignored) {}
        }
    }
}
