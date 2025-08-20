package com.example.acadlink;

import android.content.Intent;
import android.net.Uri;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class RequestSentActivity extends AppCompatActivity {

    private static final String TAG = "RequestSent";
    private RecyclerView rv;
    private SentAdapter adapter;
    private final List<SentItem> items = new ArrayList<>();
    private String currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_request_sent);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageButton back = findViewById(R.id.toolbarBackBtn);
        if (back != null) back.setOnClickListener(v -> finish());

        TextView title = findViewById(R.id.toolbarTitleTv);
        if (title != null) title.setText("Requests Sent");

        rv = findViewById(R.id.rv_requests_sent);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SentAdapter();
        rv.setAdapter(adapter);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            loadSentRequests();
        } else {
            Toast.makeText(this, "Please sign in", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSentRequests() {
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("downloadRequestsSent")
                .child(currentUid);
        ref.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                items.clear();
                try {
                    for (DataSnapshot projSnap : snapshot.getChildren()) {
                        SentItem s = new SentItem();
                        s.projectId = projSnap.getKey();
                        s.uploaderName = safeString(projSnap.child("uploaderName").getValue());
                        s.projectTitle = safeString(projSnap.child("projectTitle").getValue());
                        s.status = safeString(projSnap.child("status").getValue());
                        s.downloadUrl = safeString(projSnap.child("downloadUrl").getValue()); // optional
                        items.add(s);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "parse error", e);
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private static class SentItem {
        String projectId;
        String uploaderName;
        String projectTitle;
        String status;
        String downloadUrl;
    }

    private class SentAdapter extends RecyclerView.Adapter<SentAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_request_sent, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            SentItem s = items.get(position);
            holder.tvTo.setText("Request to: " + (s.uploaderName != null ? s.uploaderName : "Uploader"));
            holder.tvProj.setText("Project: " + (s.projectTitle != null ? s.projectTitle : "N/A"));
            holder.tvStatus.setText("Status: " + (s.status != null ? s.status : "Pending"));

            boolean accepted = "Accepted".equalsIgnoreCase(s.status);

            holder.btnDownload.setVisibility(accepted ? View.VISIBLE : View.GONE);
            holder.btnDownload.setOnClickListener(v -> {
                if (s.downloadUrl != null && !s.downloadUrl.isEmpty()) {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(s.downloadUrl));
                        startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(RequestSentActivity.this, "Unable to open URL", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(RequestSentActivity.this, "Download permitted — contact uploader or check Projects.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTo, tvProj, tvStatus;
            MaterialButton btnDownload;

            VH(View v) {
                super(v);
                tvTo = v.findViewById(R.id.tv_request_to);
                tvProj = v.findViewById(R.id.tv_project_title_sent);
                tvStatus = v.findViewById(R.id.tv_status_sent);
                btnDownload = v.findViewById(R.id.btn_download);
            }
        }
    }

    private static String safeString(Object o) {
        try { return o == null ? null : String.valueOf(o); } catch (Exception e) { return null; }
    }
}
