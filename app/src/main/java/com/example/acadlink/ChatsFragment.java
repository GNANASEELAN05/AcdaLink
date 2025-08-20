package com.example.acadlink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * ChatsFragment: shows recent chat cards saved from AllProjectSummaryActivity.
 * - Modified: Shows SMS, Call and WhatsApp entries.
 * - Modified: Clicking a chat item opens SMS, Dialer or WhatsApp depending on 'via'.
 * - Other IDs/behaviour preserved.
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

    private void loadChats() {
        try {
            String uid = "guest";
            if (FirebaseAuth.getInstance() != null && FirebaseAuth.getInstance().getCurrentUser() != null) {
                uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            }

            SharedPreferences prefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            String raw = prefs.getString("chats_" + uid, "[]");
            JSONArray arr = new JSONArray(raw);

            List<ChatItem> items = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name", "");
                String number = o.optString("number", "");
                String via = o.optString("via", "");
                long ts = o.optLong("ts", 0L);

                // Normalize blank/unknown -> sms (compatibility)
                if (via == null || via.trim().isEmpty()) via = "sms";

                // include WhatsApp entries as well (do NOT skip)
                String viaNorm = via.toLowerCase(Locale.ROOT);
                items.add(new ChatItem(name, number, viaNorm, ts));
            }

            Collections.sort(items, (a, b) -> Long.compare(b.ts, a.ts));

            if (items.isEmpty()) {
                sampleChatText.setVisibility(View.VISIBLE);
                rvChats.setVisibility(View.GONE);
            } else {
                sampleChatText.setVisibility(View.GONE);
                rvChats.setVisibility(View.VISIBLE);
                adapter.setItems(items);
            }
        } catch (Exception e) {
            sampleChatText.setVisibility(View.VISIBLE);
            rvChats.setVisibility(View.GONE);
        }
    }

    // -------------------- ChatAdapter & models --------------------
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

    private class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {

        private final List<ChatItem> items = new ArrayList<>();
        private final Context ctx;

        ChatAdapter(Context c) { ctx = c; }

        void setItems(List<ChatItem> data) {
            items.clear();
            items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ChatAdapter.VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_item, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ChatAdapter.VH holder, int position) {
            ChatItem it = items.get(position);
            String displayTo = (it.name == null || it.name.trim().isEmpty()) ? it.number : it.name;
            holder.title.setText("To: " + displayTo);

            // Show appropriate label
            String viaLabel = "via SMS";
            if ("call".equalsIgnoreCase(it.via)) viaLabel = "via Call";
            else if ("whatsapp".equalsIgnoreCase(it.via)) viaLabel = "via WhatsApp";
            holder.subtitle.setText(viaLabel);

            // icon loading (SMS, call or WhatsApp)
            try {
                PackageManager pm = ctx.getPackageManager();

                Drawable icon = null;
                if (it.via != null && it.via.equalsIgnoreCase("call")) {
                    // Attempt to load default dialer icon
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
                    // Improved WhatsApp icon resolution:
                    // 1) Try to find an activity that can handle the whatsapp://send URI (preferred).
                    // 2) If none, try known package names (standard & business).
                    // 3) Fallback to generic chat drawable.
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

                    if (icon == null) icon = ContextCompat.getDrawable(ctx, android.R.drawable.sym_action_chat);
                } else {
                    // SMS icon - prefer default sms app's icon
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

                holder.icon.setImageDrawable(icon);
            } catch (Exception e) {
                // fallback
                if (it.via != null && it.via.equalsIgnoreCase("call"))
                    holder.icon.setImageDrawable(ContextCompat.getDrawable(ctx, android.R.drawable.ic_menu_call));
                else
                    holder.icon.setImageDrawable(ContextCompat.getDrawable(ctx, android.R.drawable.sym_action_chat));
            }

            holder.itemView.setOnClickListener(v -> {
                // Open either SMS, Dialer or WhatsApp depending on via
                if (it.via != null && it.via.equalsIgnoreCase("call")) {
                    try {
                        Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                        dialIntent.setData(Uri.parse("tel:" + Uri.encode(it.number)));
                        ctx.startActivity(dialIntent);
                    } catch (Exception ex) {
                        Toast.makeText(ctx, "No dialer found", Toast.LENGTH_SHORT).show();
                    }
                } else if (it.via != null && it.via.equalsIgnoreCase("whatsapp")) {
                    // Try open in WhatsApp app (standard or business), else wa.me in browser
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
                            // Use whatsapp URI with package to open directly in the app
                            Intent waIntent = new Intent(Intent.ACTION_VIEW);
                            waIntent.setPackage(waChosen);
                            // whatsapp URI expects full number in international format without "+" as per wa.me
                            waIntent.setData(Uri.parse("whatsapp://send?phone=" + digits));
                            ctx.startActivity(waIntent);
                        } else {
                            // Fallback to web wa.me
                            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + digits));
                            ctx.startActivity(web);
                        }
                    } catch (Exception ex) {
                        // Last resort: try wa.me
                        try {
                            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + digits));
                            ctx.startActivity(web);
                        } catch (Exception ex2) {
                            Toast.makeText(ctx, "No WhatsApp/browser found", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    // SMS
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

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView title, subtitle;
            ImageView icon;

            VH(@NonNull View itemView) {
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
    }

    private static String extractDigitsStatic(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        char[] cs = s.toCharArray();
        for (char c : cs) if (Character.isDigit(c)) sb.append(c);
        return sb.toString();
    }
}
