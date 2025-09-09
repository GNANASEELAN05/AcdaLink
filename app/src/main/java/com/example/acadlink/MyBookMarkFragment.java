package com.example.acadlink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * MyBookMarkFragment - shows bookmarked projects (reads bookmarked IDs from SharedPreferences
 * and fetches project details from Firebase 'projects' node).
 *
 * Clicking an item opens AllProjectSummaryActivity (same extras as AllProjectsActivity).
 *
 * This version registers a SharedPreferences.OnSharedPreferenceChangeListener so removals/additions
 * of bookmarks update the UI instantly (no need to navigate away and back). It still keeps the
 * broadcast receiver fallback in case some parts of the app send ACTION_BOOKMARKS_UPDATED.
 */
public class MyBookMarkFragment extends Fragment {

    private RecyclerView recyclerView;
    private MyProjectsAdapter adapter;
    private final ArrayList<ProjectModel> bookmarkList = new ArrayList<>();
    private TextView emptyText;

    private DatabaseReference projectsRef;
    private BroadcastReceiver bookmarkReceiver;
    private boolean isReceiverRegistered = false;

    private static final String PREFS_NAME = "bookmarks_prefs";
    private static final String PREFS_KEY_BOOKMARKS = "bookmarked_ids";

    // track currently-known bookmarked IDs for this user so we can detect removals/additions quickly
    private Set<String> currentBookmarked = new HashSet<>();

    // SharedPreferences listener for immediate updates
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

    public MyBookMarkFragment() {
        // Required empty public constructor
    }

    public static MyBookMarkFragment newInstance(String param1, String param2) {
        MyBookMarkFragment fragment = new MyBookMarkFragment();
        Bundle args = new Bundle();
        args.putString("param1", param1);
        args.putString("param2", param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_book_mark, container, false);

        recyclerView = view.findViewById(R.id.bookmarkedRecyclerView);
        emptyText = view.findViewById(R.id.emptyText);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MyProjectsAdapter(bookmarkList, R.layout.item_all_project, project -> {
            // open summary activity with same extras as AllProjectsActivity
            Intent i = new Intent(requireContext(), AllProjectSummaryActivity.class);
            i.putExtra("projectId", project.getId());
            i.putExtra("title", project.getTitle());
            i.putExtra("projectType1", project.getProjectType1());
            i.putExtra("projectLevel", project.getProjectLevel());
            i.putExtra("abstract", project.getAbstractText());
            i.putExtra("methodology", project.getMethodology());
            i.putExtra("similarity", project.getSimilarity());
            i.putExtra("aiGenerated", project.getAiGenerated());

            if (project.getFileInfoList() != null) {
                i.putStringArrayListExtra("fileInfoList", new ArrayList<>(project.getFileInfoList()));
            }

            startActivity(i);
        });
        recyclerView.setAdapter(adapter);

        projectsRef = FirebaseDatabase.getInstance().getReference("projects");

        // initial UI state
        recyclerView.setVisibility(View.GONE);
        emptyText.setVisibility(View.VISIBLE);

        // initialize prefs listener
        prefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                // Only respond to the per-user bookmarks key
                String uid = getCurrentUid();
                if (uid == null) uid = "guest";
                String perUserKey = PREFS_KEY_BOOKMARKS + "_" + uid;
                if (!perUserKey.equals(key)) {
                    return;
                }

                // Read new set
                Set<String> newSet = sharedPreferences.getStringSet(key, new HashSet<>());
                Set<String> newBookmarked = new HashSet<>(newSet != null ? newSet : new HashSet<>());

                // compute removals and additions
                final Set<String> removed = new HashSet<>(currentBookmarked);
                removed.removeAll(newBookmarked);

                final Set<String> added = new HashSet<>(newBookmarked);
                added.removeAll(currentBookmarked);

                // Update currentBookmarked reference
                currentBookmarked = new HashSet<>(newBookmarked);

                // If there are removals, remove items from bookmarkList instantly (with notifyItemRemoved)
                if (!removed.isEmpty()) {
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            boolean removedAny = false;
                            for (String removedId : removed) {
                                for (int i = 0; i < bookmarkList.size(); i++) {
                                    ProjectModel pm = bookmarkList.get(i);
                                    if (pm != null && removedId != null && removedId.equals(pm.getId())) {
                                        bookmarkList.remove(i);
                                        adapter.notifyItemRemoved(i);
                                        removedAny = true;
                                        break;
                                    }
                                }
                            }
                            // Update empty view if needed
                            if (bookmarkList.isEmpty()) {
                                recyclerView.setVisibility(View.GONE);
                                emptyText.setVisibility(View.VISIBLE);
                            }
                            if (!removedAny) {
                                // fallback: reload from Firebase if nothing was removed locally
                                loadBookmarkedProjects();
                            }
                        });
                    } else {
                        // fallback
                        loadBookmarkedProjects();
                    }
                }

                // If items were added, just reload from Firebase (ensures full project details are retrieved)
                if (!added.isEmpty()) {
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> loadBookmarkedProjects());
                    } else {
                        loadBookmarkedProjects();
                    }
                }
            }
        };

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        // Create receiver if needed (fallback to adapter broadcasts)
        if (bookmarkReceiver == null) {
            bookmarkReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    // reload bookmarked projects; do it safely
                    try {
                        if (isAdded() && getActivity() != null) {
                            getActivity().runOnUiThread(() -> loadBookmarkedProjects());
                        } else {
                            loadBookmarkedProjects();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
        }

        // Register broadcast receiver safely only once
        if (!isReceiverRegistered && getActivity() != null) {
            try {
                requireActivity().registerReceiver(bookmarkReceiver, new IntentFilter(MyProjectsAdapter.ACTION_BOOKMARKS_UPDATED));
                isReceiverRegistered = true;
            } catch (Exception e) {
                e.printStackTrace();
                isReceiverRegistered = false;
            }
        }

        // Register SharedPreferences listener for instant updates
        try {
            SharedPreferences sp = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            sp.registerOnSharedPreferenceChangeListener(prefsListener);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // initial load
        loadBookmarkedProjects();
    }

    @Override
    public void onStop() {
        super.onStop();
        // unregister broadcast receiver safely
        if (isReceiverRegistered && bookmarkReceiver != null && getActivity() != null) {
            try {
                requireActivity().unregisterReceiver(bookmarkReceiver);
            } catch (Exception ignored) { }
            isReceiverRegistered = false;
        }

        // unregister prefs listener
        try {
            SharedPreferences sp = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            sp.unregisterOnSharedPreferenceChangeListener(prefsListener);
        } catch (Exception ignored) { }
    }

    private void loadBookmarkedProjects() {
        // If fragment isn't attached, abort
        if (!isAdded()) return;

        // determine current user id (fallback to guest)
        String uid = getCurrentUid();
        if (uid == null) uid = "guest";

        // read bookmarked IDs for this user
        SharedPreferences sp = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String perUserKey = PREFS_KEY_BOOKMARKS + "_" + uid;
        Set<String> rawSet = sp.getStringSet(perUserKey, new HashSet<>());
        // copy to avoid direct modification of the stored Set
        final Set<String> bookmarked = new HashSet<>(rawSet != null ? rawSet : new HashSet<>());

        // store current set for prefs listener diffs
        currentBookmarked = new HashSet<>(bookmarked);

        // If no bookmarks - show empty
        if (bookmarked.isEmpty()) {
            bookmarkList.clear();
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    recyclerView.setVisibility(View.GONE);
                    emptyText.setVisibility(View.VISIBLE);
                });
            } else {
                adapter.notifyDataSetChanged();
                recyclerView.setVisibility(View.GONE);
                emptyText.setVisibility(View.VISIBLE);
            }
            return;
        }

        // fetch projects from Firebase and filter by bookmarked ids
        projectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                bookmarkList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String key = child.getKey();
                    if (key == null) continue;

                    if (bookmarked.contains(key)) {
                        try {
                            ProjectModel model = child.getValue(ProjectModel.class);
                            if (model != null) {
                                model.setId(key);
                                bookmarkList.add(model);
                            }
                        } catch (Exception ignored) { }
                    }
                }

                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (bookmarkList.isEmpty()) {
                            recyclerView.setVisibility(View.GONE);
                            emptyText.setVisibility(View.VISIBLE);
                        } else {
                            recyclerView.setVisibility(View.VISIBLE);
                            emptyText.setVisibility(View.GONE);
                        }
                        adapter.notifyDataSetChanged();
                    });
                } else {
                    // fallback (very rare)
                    if (bookmarkList.isEmpty()) {
                        recyclerView.setVisibility(View.GONE);
                        emptyText.setVisibility(View.VISIBLE);
                    } else {
                        recyclerView.setVisibility(View.VISIBLE);
                        emptyText.setVisibility(View.GONE);
                    }
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "Failed to load bookmarks: " + error.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            }
        });
    }

    // helper to fetch current user id safely
    private String getCurrentUid() {
        try {
            FirebaseUser u = FirebaseAuth.getInstance() != null ? FirebaseAuth.getInstance().getCurrentUser() : null;
            if (u != null && u.getUid() != null) return u.getUid();
        } catch (Exception ignored) {}
        return "guest";
    }
}
