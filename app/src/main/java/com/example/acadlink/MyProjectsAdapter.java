package com.example.acadlink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Generic adapter for project list.
 * - Inflates the layout resource passed in constructor (so same adapter can be reused with different item layouts).
 * - Delegates click action to the provided listener (so AllProjects can open a different Activity than MyProjects).
 *
 * NOTE: bookmarkIcon is optional. If an item layout does not include R.id.bookmarkIcon,
 * the adapter will skip bookmark UI/logic for that layout to avoid crashes.
 *
 * IMPORTANT: item layout should contain the following IDs (if present):
 * - itemSerialNumber, itemProjectTitle, itemProjectType, itemProjectLevel, itemSimilarity, itemAiGenerated
 * - bookmarkIcon (optional)
 */
public class MyProjectsAdapter extends RecyclerView.Adapter<MyProjectsAdapter.ViewHolder> {

    public interface OnItemClick {
        void onClick(ProjectModel project);
    }

    private final List<ProjectModel> items;
    private final int itemLayoutResId;
    private final OnItemClick listener;

    // SharedPrefs keys & broadcast action
    private static final String PREFS_NAME = "bookmarks_prefs";
    private static final String PREFS_KEY_BOOKMARKS = "bookmarked_ids";
    public static final String ACTION_BOOKMARKS_UPDATED = "com.example.acadlink.BOOKMARKS_UPDATED";

    public MyProjectsAdapter(List<ProjectModel> items, int itemLayoutResId, OnItemClick listener) {
        this.items = items;
        this.itemLayoutResId = itemLayoutResId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MyProjectsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(itemLayoutResId, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull MyProjectsAdapter.ViewHolder holder, int position) {
        ProjectModel model = items.get(position);

        // Serial number
        holder.serialNumber.setText((position + 1) + ".");

        // Title (fallbacks added)
        String title = safeString(model.getTitle());
        if (title.isEmpty()) {
            title = safeFromExtraKeys(model, "title", "projectTitle", "name", "project_title", "project_name");
        }
        holder.title.setText("Title : " + (title.isEmpty() ? "Untitled" : title));

        // Project type (fallbacks)
        String type = safeString(model.getProjectType1());
        if (type.isEmpty()) {
            type = safeFromExtraKeys(model, "projectType1", "type", "projectType", "project_type");
        }
        holder.projectType.setText("Project type : " + (type.isEmpty() ? "N/A" : type));

        // Level
        String level = safeString(model.getProjectLevel());
        if (level.isEmpty()) {
            level = safeFromExtraKeys(model, "projectLevel", "level", "project_level");
        }
        holder.level.setText("Project level : " + (level.isEmpty() ? "N/A" : level));

        // Similarity
        String sim = safeString(model.getSimilarity());
        if (sim.isEmpty()) {
            sim = safeFromExtraKeys(model, "similarity", "plagiarism", "similarityScore", "similarity_percent");
        }
        holder.similarity.setText("Similarity : " + (sim.isEmpty() ? "N/A" : sim));
        try {
            float simVal = parsePercent(sim);
            holder.similarity.setTextColor(simVal <= 15f ? 0xFF00AA00 : 0xFFFF0000);
        } catch (Exception e) {
            holder.similarity.setTextColor(0xFF000000);
        }

        // AI Generated
        String ai = safeString(model.getAiGenerated());
        if (ai.isEmpty()) {
            ai = safeFromExtraKeys(model, "aiGenerated", "aiScore", "aiContent", "ai");
        }
        holder.aiGenerated.setText("AI generated content : " + (ai.isEmpty() ? "N/A" : ai));
        try {
            float aiVal = parsePercent(ai);
            holder.aiGenerated.setTextColor(aiVal <= 15f ? 0xFF00AA00 : 0xFFFF0000);
        } catch (Exception e) {
            holder.aiGenerated.setTextColor(0xFF000000);
        }

        // Whole item click (keeps previous behavior)
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(model);
        });

        // ----- Bookmark handling (only if bookmarkIcon exists in layout) -----
        if (holder.bookmarkIcon != null) {
            Context ctx = holder.itemView.getContext();
            SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            Set<String> stored = sp.getStringSet(PREFS_KEY_BOOKMARKS, new HashSet<>());
            Set<String> bookmarks = new HashSet<>(stored);

            String projectId = model.getId();
            boolean isBookmarked = projectId != null && bookmarks.contains(projectId);

            // initial icon state (outline or filled)
            if (isBookmarked) {
                holder.bookmarkIcon.setImageResource(R.drawable.ic_bookmark_filled);
            } else {
                holder.bookmarkIcon.setImageResource(R.drawable.ic_bookmark1);
            }

            holder.bookmarkIcon.setOnClickListener(null);

            // Toggle bookmark
            holder.bookmarkIcon.setOnClickListener(v -> {
                SharedPreferences.Editor editor = sp.edit();
                Set<String> newSet = new HashSet<>(sp.getStringSet(PREFS_KEY_BOOKMARKS, new HashSet<>()));

                if (projectId == null) {
                    Toast.makeText(ctx, "Unable to bookmark this project", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (newSet.contains(projectId)) {
                    // remove bookmark
                    newSet.remove(projectId);
                    editor.putStringSet(PREFS_KEY_BOOKMARKS, newSet).apply();
                    holder.bookmarkIcon.setImageResource(R.drawable.ic_bookmark1);
                    Toast.makeText(ctx, "This project removed from bookmark", Toast.LENGTH_SHORT).show();
                } else {
                    // add bookmark
                    newSet.add(projectId);
                    editor.putStringSet(PREFS_KEY_BOOKMARKS, newSet).apply();
                    holder.bookmarkIcon.setImageResource(R.drawable.ic_bookmark_filled);
                    Toast.makeText(ctx, "This project added to bookmark", Toast.LENGTH_SHORT).show();
                }

                // notify other components
                Intent it = new Intent(ACTION_BOOKMARKS_UPDATED);
                ctx.sendBroadcast(it);
            });
        }
        // ----- end bookmark handling -----
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private float parsePercent(String s) {
        if (s == null) throw new IllegalArgumentException();
        String num = s.replaceAll("[^0-9.]", "");
        if (num.isEmpty()) throw new IllegalArgumentException();
        return Float.parseFloat(num);
    }

    private String safeString(String s) {
        return s != null ? s.trim() : "";
    }

    private String safeFromExtraKeys(ProjectModel model, String... keys) {
        Map<String, Object> data = model.getExtraData();
        if (data == null) return "";

        List<String> normDesired = new ArrayList<>();
        for (String k : keys) {
            if (k != null) normDesired.add(normalizeKey(k));
        }

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String rawKey = entry.getKey();
            Object val = entry.getValue();
            if (rawKey == null || val == null) continue;
            String nk = normalizeKey(rawKey);
            if (normDesired.contains(nk)) {
                String s = String.valueOf(val).trim();
                if (!s.isEmpty()) return s;
            }
        }

        for (Object v : data.values()) {
            if (v instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nm = (Map<String, Object>) v;
                for (Map.Entry<String, Object> e : nm.entrySet()) {
                    String rk = e.getKey();
                    Object vv = e.getValue();
                    if (rk == null || vv == null) continue;
                    String nk = normalizeKey(rk);
                    if (normDesired.contains(nk)) {
                        String s = String.valueOf(vv).trim();
                        if (!s.isEmpty()) return s;
                    }
                }
            }
        }

        return "";
    }

    private static String normalizeKey(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView serialNumber, title, projectType, level, similarity, aiGenerated;
        ImageView bookmarkIcon; // optional

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            serialNumber = itemView.findViewById(R.id.itemSerialNumber);
            title = itemView.findViewById(R.id.itemProjectTitle);
            projectType = itemView.findViewById(R.id.itemProjectType);
            level = itemView.findViewById(R.id.itemProjectLevel);
            similarity = itemView.findViewById(R.id.itemSimilarity);
            aiGenerated = itemView.findViewById(R.id.itemAiGenerated);
            bookmarkIcon = itemView.findViewById(R.id.bookmarkIcon);
        }
    }
}
