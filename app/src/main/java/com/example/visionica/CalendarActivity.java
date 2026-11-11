package com.example.visionica;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import android.widget.CalendarView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalendarActivity extends AppCompatActivity {

    // ── Views ──────────────────────────────────────────────────────────
    private CalendarView  calendarView;
    private RecyclerView  rvEvents;
    private LinearLayout  tvEmptyEvents;
    private ProgressBar   progressEvents;

    // ── Firebase ───────────────────────────────────────────────────────
    private FirebaseFirestore db;
    private FirebaseAuth      mAuth;

    // ── Data ───────────────────────────────────────────────────────────
    private List<EventAdapter.ListItem> displayList;
    private EventAdapter                adapter;
    private long                        selectedDate;

    // ── Colores de categorías ──────────────────────────────────────────
    private static final String COLOR_PERSONAL = "#8B5394";
    private static final String COLOR_CLASES   = "#1976D2";
    private static final String COLOR_TAREA    = "#2E7D32";
    private static final String COLOR_URGENTE  = "#C62828";

    // ── Estado del diálogo ─────────────────────────────────────────────
    private String selectedColor    = COLOR_PERSONAL;
    private String selectedCategory = "Personal";
    private String selectedTime     = "";

    // ── Permiso notificaciones (Android 13+) ───────────────────────────
    private final ActivityResultLauncher<String> notifPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> { /* silencioso — si niega simplemente no llega */ }
            );

    // ──────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Pedir permiso de notificaciones en Android 13+
        requestNotificationPermission();

        initViews();
        setupToolbar();
        setupCalendar();
    }

    // ── Permiso notificaciones ─────────────────────────────────────────

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    // ── Init ───────────────────────────────────────────────────────────

    private void initViews() {
        calendarView   = findViewById(R.id.calendarView);
        rvEvents       = findViewById(R.id.rvEvents);
        tvEmptyEvents  = (LinearLayout) findViewById(R.id.tvEmptyEvents);
        progressEvents = findViewById(R.id.progressEvents);

        displayList = new ArrayList<>();
        adapter     = new EventAdapter(displayList, this::onDeleteEvent);
        rvEvents.setLayoutManager(new LinearLayoutManager(this));
        rvEvents.setAdapter(adapter);
        rvEvents.setNestedScrollingEnabled(false);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Calendario");
        }
    }

    private void setupCalendar() {
        selectedDate = System.currentTimeMillis();
        loadAllEvents();

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            Calendar cal = Calendar.getInstance();
            cal.set(year, month, dayOfMonth, 0, 0, 0);
            cal.set(Calendar.MILLISECOND, 0);
            selectedDate = cal.getTimeInMillis();
            showAddEventDialog();
        });
    }

    // ── Cargar TODOS los eventos ───────────────────────────────────────

    private void loadAllEvents() {
        if (mAuth.getCurrentUser() == null) {
            showLoading(false);
            displayList.clear();
            adapter.notifyDataSetChanged();
            updateEmptyState();
            return;
        }

        showLoading(true);
        String userId = mAuth.getCurrentUser().getUid();

        db.collection("user_events")
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<EventAdapter.EventItem> rawEvents = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : snapshots) {
                        String title       = doc.getString("title");
                        String description = doc.getString("description");
                        String category    = doc.getString("category");
                        String color       = doc.getString("color");
                        Long   timestamp   = doc.getLong("timestamp");

                        String time = "";
                        if (timestamp != null && timestamp > 0) {
                            SimpleDateFormat fmt =
                                    new SimpleDateFormat("HH:mm", Locale.getDefault());
                            time = fmt.format(new Date(timestamp));
                            if ("00:00".equals(time)) time = "";
                        }

                        rawEvents.add(new EventAdapter.EventItem(
                                doc.getId(),
                                title       != null ? title       : "",
                                description != null ? description : "",
                                time,
                                color       != null ? color       : COLOR_PERSONAL,
                                category    != null ? category    : "Personal",
                                timestamp   != null ? timestamp   : 0L
                        ));
                    }

                    displayList.clear();
                    displayList.addAll(buildListWithSeparators(rawEvents));
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Error al cargar eventos: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    displayList.clear();
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                    showLoading(false);
                });
    }

    // ── Construir lista con separadores de fecha ───────────────────────

    private List<EventAdapter.ListItem> buildListWithSeparators(
            List<EventAdapter.EventItem> events) {

        List<EventAdapter.ListItem> result = new ArrayList<>();
        String lastDateKey = "";

        SimpleDateFormat keyFmt   =
                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat labelFmt =
                new SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy",
                        new Locale("es", "ES"));

        for (EventAdapter.EventItem event : events) {
            String dateKey = keyFmt.format(new Date(event.timestamp));

            if (!dateKey.equals(lastDateKey)) {
                String label = labelFmt.format(new Date(event.timestamp));
                label = label.substring(0, 1).toUpperCase() + label.substring(1);
                result.add(new EventAdapter.SeparatorItem(label));
                lastDateKey = dateKey;
            }

            result.add(event);
        }

        return result;
    }

    // ── Diálogo agregar evento ─────────────────────────────────────────

    private void showAddEventDialog() {
        selectedColor    = COLOR_PERSONAL;
        selectedCategory = "Personal";
        selectedTime     = "";

        Context themedContext = new ContextThemeWrapper(
                this,
                com.google.android.material.R.style
                        .Theme_MaterialComponents_Light_NoActionBar
        );
        LayoutInflater themedInflater = LayoutInflater.from(themedContext);

        BottomSheetDialog dialog = new BottomSheetDialog(themedContext);
        View view = themedInflater.inflate(R.layout.dialog_add_event, null);
        dialog.setContentView(view);

        TextInputEditText etTitle       = view.findViewById(R.id.etTitle);
        TextInputEditText etDescription = view.findViewById(R.id.etDescription);
        TextView          tvTime        = view.findViewById(R.id.tvSelectedTime);
        MaterialButton    btnTime       = view.findViewById(R.id.btnPickTime);
        MaterialButton    btnSave       = view.findViewById(R.id.btnSaveEvent);

        LinearLayout chipPersonal = view.findViewById(R.id.chipPersonal);
        LinearLayout chipClases   = view.findViewById(R.id.chipClases);
        LinearLayout chipTarea    = view.findViewById(R.id.chipTarea);
        LinearLayout chipUrgente  = view.findViewById(R.id.chipUrgente);

        setupChips(chipPersonal, chipClases, chipTarea, chipUrgente);

        btnTime.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new TimePickerDialog(this, (tp, h, m) -> {
                selectedTime = String.format(Locale.getDefault(), "%02d:%02d", h, m);
                tvTime.setText(selectedTime);
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        });

        btnSave.setOnClickListener(v -> {
            String title = etTitle.getText() != null
                    ? etTitle.getText().toString().trim() : "";
            if (title.isEmpty()) {
                etTitle.setError("El título es obligatorio");
                return;
            }
            String description = etDescription.getText() != null
                    ? etDescription.getText().toString().trim() : "";

            saveEvent(title, description);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void setupChips(LinearLayout... chips) {
        String[] colors     = {COLOR_PERSONAL, COLOR_CLASES, COLOR_TAREA, COLOR_URGENTE};
        String[] categories = {"Personal", "Clases", "Tarea", "Urgente"};

        for (int i = 0; i < chips.length; i++) {
            final int idx = i;
            chips[i].setOnClickListener(v -> {
                selectedColor    = colors[idx];
                selectedCategory = categories[idx];
                for (LinearLayout chip : chips) chip.setAlpha(0.45f);
                chips[idx].setAlpha(1f);
            });
        }
        chips[0].setAlpha(1f);
        for (int i = 1; i < chips.length; i++) chips[i].setAlpha(0.45f);
    }

    // ── Guardar evento ─────────────────────────────────────────────────

    private void saveEvent(String title, String description) {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "No hay usuario activo", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(selectedDate);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if (!selectedTime.isEmpty()) {
            String[] parts = selectedTime.split(":");
            cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
            cal.set(Calendar.MINUTE,      Integer.parseInt(parts[1]));
        } else {
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE,      0);
        }

        long eventTimestamp = cal.getTimeInMillis();

        Map<String, Object> event = new HashMap<>();
        event.put("userId",      userId);
        event.put("title",       title);
        event.put("description", description);
        event.put("timestamp",   eventTimestamp);
        event.put("color",       selectedColor);
        event.put("category",    selectedCategory);

        showLoading(true);

        db.collection("user_events")
                .add(event)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Evento guardado ✓", Toast.LENGTH_SHORT).show();

                    // ── 1. Notificación interna en Firestore ───────────
                    saveCalendarNotification(userId, title, description, eventTimestamp);

                    // ── 2. Notificación real en el celular ─────────────
                    // Solo si el usuario puso una hora (timestamp != medianoche)
                    if (!selectedTime.isEmpty()) {
                        NotificationScheduler.scheduleEventReminder(
                                this, title, eventTimestamp);
                    }

                    loadAllEvents();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this,
                            "Error al guardar: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    // ── Notificación Firestore ─────────────────────────────────────────

    private void saveCalendarNotification(String userId, String eventTitle,
                                          String description, long eventTimestamp) {
        NotificationModel notif =
                new NotificationModel(userId, eventTitle, description,
                        eventTimestamp, true);

        Map<String, Object> data = new HashMap<>();
        data.put("userId",         notif.getUserId());
        data.put("type",           notif.getType());
        data.put("title",          notif.getTitle());
        data.put("message",        notif.getMessage());
        data.put("eventTitle",     notif.getEventTitle());
        data.put("description",    notif.getDescription());
        data.put("eventTimestamp", notif.getEventTimestamp());
        data.put("timestamp",      notif.getTimestamp());
        data.put("read",           false);

        db.collection("notifications")
                .add(data)
                .addOnSuccessListener(r ->
                        android.util.Log.d("CalendarActivity",
                                "Notificación Firestore guardada"))
                .addOnFailureListener(e ->
                        android.util.Log.e("CalendarActivity",
                                "Error notificación: " + e.getMessage()));
    }

    // ── Eliminar evento ────────────────────────────────────────────────

    private void onDeleteEvent(EventAdapter.EventItem item, int position) {
        db.collection("user_events").document(item.docId)
                .delete()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Evento eliminado", Toast.LENGTH_SHORT).show();
                    // Cancelar la notificación programada si tenía hora
                    if (item.timestamp > 0) {
                        NotificationScheduler.cancelEventReminder(
                                this, item.timestamp);
                    }
                    loadAllEvents();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error al eliminar",
                                Toast.LENGTH_SHORT).show());
    }

    // ── UI helpers ─────────────────────────────────────────────────────

    private void showLoading(boolean show) {
        if (progressEvents != null)
            progressEvents.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void updateEmptyState() {
        if (tvEmptyEvents == null) return;
        boolean empty = displayList.isEmpty();
        tvEmptyEvents.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvEvents.setVisibility    (empty ? View.GONE    : View.VISIBLE);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}