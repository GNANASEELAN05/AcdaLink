package com.example.acadlink;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
 */
public class MyBookMarkFragment extends Fragment {

    private RecyclerView recyclerView;
    private MyProjectsAdapter adapter;
    private ArrayList<ProjectModel> bookmarkList = new ArrayList<>();
    private TextView emptyText;

    private DatabaseReference projectsRef;
    private BroadcastReceiver bookmarkReceiver;

    private static final String PREFS_NAME = "bookmarks_prefs";
    private static final String PREFS_KEY_BOOKMARKS = "bookmarked_ids";

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

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        // Listen for bookmark changes (adapter broadcasts this action)
        bookmarkReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // reload bookmarked projects
                loadBookmarkedProjects();
            }
        };
        requireActivity().registerReceiver(bookmarkReceiver, new IntentFilter(MyProjectsAdapter.ACTION_BOOKMARKS_UPDATED));

        // initial load
        loadBookmarkedProjects();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (bookmarkReceiver != null) {
            try {
                requireActivity().unregisterReceiver(bookmarkReceiver);
            } catch (Exception ignored) {}
            bookmarkReceiver = null;
        }
    }

    private void loadBookmarkedProjects() {
        // read bookmarked IDs
        SharedPreferences sp = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> bookmarked = new HashSet<>(sp.getStringSet(PREFS_KEY_BOOKMARKS, new HashSet<>()));

        if (bookmarked.isEmpty()) {
            // show empty view
            bookmarkList.clear();
            adapter.notifyDataSetChanged();
            recyclerView.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
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

                    // If this project id is bookmarked, add it (no per-user filtering here,
                    // bookmarks may refer to any project id)
                    if (bookmarked.contains(key)) {
                        try {
                            ProjectModel model = child.getValue(ProjectModel.class);
                            if (model != null) {
                                model.setId(key);
                                bookmarkList.add(model);
                            }
                        } catch (Exception ignored) {}
                    }
                }

                if (bookmarkList.isEmpty()) {
                    recyclerView.setVisibility(View.GONE);
                    emptyText.setVisibility(View.VISIBLE);
                } else {
                    recyclerView.setVisibility(View.VISIBLE);
                    emptyText.setVisibility(View.GONE);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(requireContext(), "Failed to load bookmarks: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
