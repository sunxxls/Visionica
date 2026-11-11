package com.example.visionica;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private RecyclerView            rvNotifications;
    private LinearLayout            emptyNotifications;
    private FirebaseFirestore       db;
    private FirebaseAuth            mAuth;
    private List<NotificationModel> notificationsList;
    private NotificationAdapter     adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Notificaciones");
        }

        rvNotifications    = findViewById(R.id.rvNotifications);
        emptyNotifications = findViewById(R.id.emptyNotifications);

        notificationsList = new ArrayList<>();
        adapter = new NotificationAdapter(this, notificationsList);
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rvNotifications.setAdapter(adapter);

        loadNotifications();
    }

    // ── Cargar desde la colección "notifications" ──────────────────────
    private void loadNotifications() {
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();

        db.collection("notifications")
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(60)
                .get()
                .addOnSuccessListener(snapshots -> {
                    notificationsList.clear();

                    for (QueryDocumentSnapshot doc : snapshots) {
                        NotificationModel n = doc.toObject(NotificationModel.class);
                        n.setId(doc.getId());
                        notificationsList.add(n);
                    }

                    updateUI();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Error al cargar notificaciones", Toast.LENGTH_SHORT).show();
                    updateUI();
                });
    }

    // ── Marcar todas como leídas ───────────────────────────────────────
    private void markAllRead() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        db.collection("notifications")
                .whereEqualTo("userId", userId)
                .whereEqualTo("read", false)
                .get()
                .addOnSuccessListener(snapshots -> {
                    for (QueryDocumentSnapshot doc : snapshots) {
                        doc.getReference().update("read", true);
                    }
                    // Actualizar modelos locales
                    for (NotificationModel n : notificationsList) n.setRead(true);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this,
                            "Todas marcadas como leídas", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateUI() {
        boolean empty = notificationsList.isEmpty();
        rvNotifications.setVisibility(empty ? View.GONE  : View.VISIBLE);
        emptyNotifications.setVisibility(empty ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    // ── Menú "Marcar todas como leídas" ───────────────────────────────
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_notifications, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_mark_all_read) {
            markAllRead();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}