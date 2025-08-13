package com.example.acadlink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
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
 * - robust WhatsApp detection & icon loading
 * - safe Telephony usage for SMS icon/opener
 * (IDs/behaviour preserved)
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
                items.add(new ChatItem(name, number, via, ts));
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
        String via; // "sms" or "whatsapp"
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
            String viaLabel = "via " + (("whatsapp".equalsIgnoreCase(it.via)) ? "WhatsApp" : "SMS");
            holder.subtitle.setText(viaLabel);

            // icon loading (robust)
            try {
                PackageManager pm = ctx.getPackageManager();
                if ("whatsapp".equalsIgnoreCase(it.via)) {
                    String waPkg = findInstalledWhatsAppPackage(pm);
                    Drawable icon = null;
                    if (waPkg != null) icon = safeLoadIcon(pm, waPkg);

                    if (icon == null) {
                        ResolveInfo ri = pm.resolveActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/12345")), PackageManager.MATCH_DEFAULT_ONLY);
                        if (ri != null && ri.activityInfo != null) {
                            icon = safeLoadIcon(pm, ri.activityInfo.packageName);
                        }
                    }

                    if (icon != null) holder.icon.setImageDrawable(icon);
                    else holder.icon.setImageDrawable(ContextCompat.getDrawable(ctx, android.R.drawable.sym_action_chat));
                } else {
                    // SMS icon - guarded
                    String smsPkg = null;
                    try {
                        smsPkg = Telephony.Sms.getDefaultSmsPackage(ctx);
                    } catch (Exception ignored) { smsPkg = null; }

                    if (smsPkg == null) {
                        Intent smsProbe = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:1234567890"));
                        List<ResolveInfo> smsHandlers = pm.queryIntentActivities(smsProbe, 0);
                        if (smsHandlers != null && !smsHandlers.isEmpty()) {
                            ResolveInfo ri = smsHandlers.get(0);
                            if (ri != null && ri.activityInfo != null) smsPkg = ri.activityInfo.packageName;
                        }
                    }

                    Drawable icon = null;
                    if (smsPkg != null) icon = safeLoadIcon(pm, smsPkg);
                    if (icon != null) holder.icon.setImageDrawable(icon);
                    else holder.icon.setImageDrawable(ContextCompat.getDrawable(ctx, android.R.drawable.sym_action_chat));
                }
            } catch (Exception e) {
                holder.icon.setImageDrawable(ContextCompat.getDrawable(ctx, android.R.drawable.sym_action_chat));
            }

            holder.itemView.setOnClickListener(v -> {
                if ("whatsapp".equalsIgnoreCase(it.via)) {
                    String digits = extractDigitsStatic(it.number);
                    if (digits.length() == 0) {
                        Toast.makeText(ctx, "Invalid number for WhatsApp", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String waUrl = "https://wa.me/" + digits;
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(waUrl));
                        String waPkg = findInstalledWhatsAppPackage(ctx.getPackageManager());
                        if (waPkg != null) intent.setPackage(waPkg);
                        ctx.startActivity(intent);
                    } catch (Exception ex) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(waUrl));
                            ctx.startActivity(intent);
                        } catch (Exception exc) {
                            Toast.makeText(ctx, "Unable to open WhatsApp", Toast.LENGTH_SHORT).show();
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
                            List<ResolveInfo> smsHandlers = ctx.getPackageManager().queryIntentActivities(smsProbe, 0);
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

        private String findInstalledWhatsAppPackage(PackageManager pm) {
            if (pm == null) return null;

            String[] preferred = new String[] {
                    "com.whatsapp",
                    "com.whatsapp.w4b",
                    "com.whatsapp.beta",
                    "com.whatsapp.android"
            };

            for (String p : preferred) {
                try {
                    PackageInfo pi = pm.getPackageInfo(p, 0);
                    if (pi != null) return p;
                } catch (Exception ignored) { }
            }

            // scan installed packages for any package name containing "whatsapp"
            try {
                List<PackageInfo> installed = pm.getInstalledPackages(0);
                if (installed != null) {
                    for (PackageInfo pi : installed) {
                        if (pi != null && pi.packageName != null &&
                                pi.packageName.toLowerCase(Locale.ROOT).contains("whatsapp")) {
                            return pi.packageName;
                        }
                    }
                }
            } catch (Exception ignored) { }

            // fallback: resolve wa.me handlers
            try {
                Intent test = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/1234567890"));
                List<ResolveInfo> handlers = pm.queryIntentActivities(test, PackageManager.MATCH_DEFAULT_ONLY);
                if (handlers != null && !handlers.isEmpty()) {
                    for (ResolveInfo ri : handlers) {
                        if (ri != null && ri.activityInfo != null && ri.activityInfo.packageName != null) {
                            String pkg = ri.activityInfo.packageName.toLowerCase(Locale.ROOT);
                            if (pkg.contains("whatsapp")) return ri.activityInfo.packageName;
                        }
                    }
                }
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
