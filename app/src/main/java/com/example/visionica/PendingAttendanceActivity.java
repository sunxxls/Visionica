package com.example.visionica;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PendingAttendanceActivity extends AppCompatActivity {

    private RecyclerView             rvPending;
    private LinearLayout             emptyState;
    private TextView                 tvPendingCount;
    private MaterialButton           btnSync;

    private PendingAttendanceAdapter adapter;
    private List<PendingAttendance>  pendingList;
    private final Executor           executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_attendance);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Pendientes de sincronizar");
        }

        // Views
        rvPending      = findViewById(R.id.rvPending);
        emptyState     = findViewById(R.id.emptyState);
        tvPendingCount = findViewById(R.id.tvPendingCount);
        btnSync        = findViewById(R.id.btnSync);

        // RecyclerView
        pendingList = new ArrayList<>();
        adapter     = new PendingAttendanceAdapter(pendingList);
        rvPending.setLayoutManager(new LinearLayoutManager(this));
        rvPending.setAdapter(adapter);

        // Botón sincronizar
        btnSync.setOnClickListener(v -> syncNow());

        loadPending();
    }

    // ── Cargar desde Room ──────────────────────────────────────────────

    private void loadPending() {
        executor.execute(() -> {
            List<PendingAttendance> list =
                    AppDatabase.getInstance(this).pendingAttendanceDao().getAllPending();

            runOnUiThread(() -> {
                pendingList.clear();
                pendingList.addAll(list);
                adapter.notifyDataSetChanged();
                updateUI();
            });
        });
    }

    private void updateUI() {
        int count = pendingList.size();

        if (count == 0) {
            // Estado vacío
            emptyState.setVisibility(View.VISIBLE);
            rvPending.setVisibility(View.GONE);
            tvPendingCount.setVisibility(View.GONE);
            btnSync.setEnabled(false);
            btnSync.setAlpha(0.5f);
        } else {
            emptyState.setVisibility(View.GONE);
            rvPending.setVisibility(View.VISIBLE);
            tvPendingCount.setVisibility(View.VISIBLE);
            tvPendingCount.setText(count + " asistencia" + (count == 1 ? "" : "s") + " pendiente" + (count == 1 ? "" : "s"));
            btnSync.setEnabled(true);
            btnSync.setAlpha(1f);
        }
    }

    // ── Sincronizar ────────────────────────────────────────────────────

    private void syncNow() {
        if (!SyncManager.isOnline(this)) {
            Toast.makeText(this,
                    "Sin conexión a internet. Conéctate e intenta de nuevo.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        btnSync.setEnabled(false);
        btnSync.setText("Sincronizando...");

        SyncManager.syncPending(this, (synced, failed) -> runOnUiThread(() -> {
            if (failed == 0) {
                Toast.makeText(this,
                        "Asistencias sincronizadas correctamente. ✓",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this,
                        synced + " sincronizadas, " + failed + " fallaron. Intenta de nuevo.",
                        Toast.LENGTH_LONG).show();
            }
            btnSync.setText("Sincronizar ahora");
            loadPending(); // recargar lista
        }));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPending();
    }
}