package com.example.acadlink;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RequestReceivedActivity extends AppCompatActivity {

    private static final String TAG = "RequestReceived";
    private RecyclerView rv;
    private RequestsAdapter adapter;
    private final List<RequestItem> items = new ArrayList<>();

    private String currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_request_received);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageButton back = findViewById(R.id.toolbarBackBtn);
        if (back != null) back.setOnClickListener(v -> finish());

        TextView title = findViewById(R.id.toolbarTitleTv);
        if (title != null) title.setText("Requests Received");

        rv = findViewById(R.id.rv_requests_received);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RequestsAdapter();
        rv.setAdapter(adapter);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            loadReceivedRequests();
        } else {
            Toast.makeText(this, "Please sign in", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadReceivedRequests() {
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("downloadRequestsReceived")
                .child(currentUid);
        ref.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                items.clear();
                try {
                    for (DataSnapshot projectSnap : snapshot.getChildren()) {
                        String projId = projectSnap.getKey();
                        for (DataSnapshot reqSnap : projectSnap.getChildren()) {
                            RequestItem r = new RequestItem();
                            r.projectId = projId;
                            r.requesterUid = safeString(reqSnap.child("requesterUid").getValue());
                            r.requesterName = safeString(reqSnap.child("requesterName").getValue());
                            r.projectTitle = safeString(reqSnap.child("projectTitle").getValue());
                            r.status = safeString(reqSnap.child("status").getValue());
                            r.requestedAt = reqSnap.child("requestedAt").getValue();
                            items.add(r);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "parse error", e);
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private static class RequestItem {
        String requesterUid;
        String requesterName;
        String projectId;
        String projectTitle;
        String status;
        Object requestedAt;
    }

    private class RequestsAdapter extends RecyclerView.Adapter<RequestsAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_request_received, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            RequestItem r = items.get(position);

            // show requester name as "Request from: <name>" (matches reference)
            holder.tvFrom.setText("Request from: " + (r.requesterName != null ? r.requesterName : "Unknown"));
            holder.tvMsg.setText("Has Requested to Download Your Project");
            holder.tvTitle.setText("Title : " + (r.projectTitle != null ? r.projectTitle : "N/A"));
            holder.tvStatus.setText("Status: " + (r.status != null ? r.status : "Pending"));

            boolean isPending = "Pending".equalsIgnoreCase(r.status) || r.status == null || r.status.isEmpty();

            // Accept/Reject should only be visible when pending
            holder.btnAccept.setVisibility(isPending ? View.VISIBLE : View.GONE);
            holder.btnReject.setVisibility(isPending ? View.VISIBLE : View.GONE);

            // IMPORTANT: uploader (receiver) page must NOT show download button for themselves.
            // The download action should appear only in the requester's "Requests Sent" UI.
            holder.btnDownloadAllowed.setVisibility(View.GONE);

            // Accept handler: immediately disable both buttons & update UI locally
            holder.btnAccept.setOnClickListener(v -> {
                // immediate UI feedback: disable both buttons
                holder.btnAccept.setEnabled(false);
                holder.btnReject.setEnabled(false);
                holder.btnAccept.setAlpha(0.5f);
                holder.btnReject.setAlpha(0.5f);

                // set local state & update status text immediately
                String oldStatus = r.status != null ? r.status : "Pending";
                r.status = "Accepted";
                holder.tvStatus.setText("Status: Accepted");
                holder.btnAccept.setVisibility(View.GONE);
                holder.btnReject.setVisibility(View.GONE);

                // perform DB update; if it fails, revert local UI via adapter notification
                updateStatusForRequest(r, "Accepted", holder.getAdapterPosition(), oldStatus);
            });

            // Reject handler: same UX as accept (disable, update local UI)
            holder.btnReject.setOnClickListener(v -> {
                holder.btnAccept.setEnabled(false);
                holder.btnReject.setEnabled(false);
                holder.btnAccept.setAlpha(0.5f);
                holder.btnReject.setAlpha(0.5f);

                String oldStatus = r.status != null ? r.status : "Pending";
                r.status = "Rejected";
                holder.tvStatus.setText("Status: Rejected");
                holder.btnAccept.setVisibility(View.GONE);
                holder.btnReject.setVisibility(View.GONE);

                updateStatusForRequest(r, "Rejected", holder.getAdapterPosition(), oldStatus);
            });

            // The download button is intentionally not wired for uploader's view.
            holder.btnDownloadAllowed.setOnClickListener(v -> {
                // safety fallback in case visibility was toggled externally
                Toast.makeText(RequestReceivedActivity.this, "This action is for the requester only.", Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvFrom, tvMsg, tvTitle, tvStatus;
            MaterialButton btnAccept, btnReject, btnDownloadAllowed;

            VH(View v) {
                super(v);
                tvFrom = v.findViewById(R.id.tv_request_from);
                tvMsg = v.findViewById(R.id.tv_request_message);
                tvTitle = v.findViewById(R.id.tv_project_title);
                tvStatus = v.findViewById(R.id.tv_status);
                btnAccept = v.findViewById(R.id.btn_accept);
                btnReject = v.findViewById(R.id.btn_reject);
                btnDownloadAllowed = v.findViewById(R.id.btn_download_allowed);
            }
        }
    }

    /**
     * Update status in both inbox (received) and sent nodes.
     * If update fails, revert the local item status and notify adapter.
     *
     * @param r older item (already updated locally by caller)
     * @param newStatus new status to write
     * @param position adapter position (used to revert UI on failure)
     * @param oldStatus original status before change (used to revert on failure)
     */
    private void updateStatusForRequest(RequestItem r, String newStatus, int position, String oldStatus) {
        if (r == null || r.projectId == null || r.requesterUid == null) return;

        String requesterUid = r.requesterUid;
        String projectId = r.projectId;
        String uploaderUid = currentUid;

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);
        updates.put("decisionAt", ServerValue.TIMESTAMP);

        // Update received inbox node
        DatabaseReference inboxRef = FirebaseDatabase.getInstance()
                .getReference("downloadRequestsReceived")
                .child(uploaderUid)
                .child(projectId)
                .child(requesterUid);

        // Update sent node for requester
        DatabaseReference sentRef = FirebaseDatabase.getInstance()
                .getReference("downloadRequestsSent")
                .child(requesterUid)
                .child(projectId);

        inboxRef.updateChildren(updates)
                .addOnSuccessListener(u -> {
                    sentRef.updateChildren(updates)
                            .addOnSuccessListener(u2 -> {
                                Toast.makeText(RequestReceivedActivity.this, "Request " + newStatus, Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e2 -> {
                                // revert local if sent update fails
                                Toast.makeText(RequestReceivedActivity.this, "Failed to update sent record: " + e2.getMessage(), Toast.LENGTH_SHORT).show();
                                if (position >= 0 && position < items.size()) {
                                    RequestItem ri = items.get(position);
                                    ri.status = oldStatus;
                                    runOnUiThread(() -> adapter.notifyItemChanged(position));
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    // revert local on failure
                    Toast.makeText(RequestReceivedActivity.this, "Failed to update request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    if (position >= 0 && position < items.size()) {
                        RequestItem ri = items.get(position);
                        ri.status = oldStatus;
                        runOnUiThread(() -> adapter.notifyItemChanged(position));
                    }
                });
    }

    private static String safeString(Object o) {
        try { return o == null ? null : String.valueOf(o); } catch (Exception e) { return null; }
    }
}
