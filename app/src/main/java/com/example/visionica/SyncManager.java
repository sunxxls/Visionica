package com.example.visionica;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * SyncManager — Punto central para guardar asistencias y sincronizar pendientes.
 *
 * USO desde FaceAttendanceActivity:
 *   SyncManager.saveAttendance(context, classId, className, studentId, studentName, status,
 *       () -> runOnUiThread(() -> Toast.makeText(...).show()),
 *       () -> runOnUiThread(() -> Toast.makeText(...).show())
 *   );
 */
public class SyncManager {

    private static final Executor executor = Executors.newSingleThreadExecutor();

    // ── Interfaces de callback (se ejecutan en hilo de background) ─────
    public interface OnSavedOnline  { void run(); }
    public interface OnSavedOffline { void run(); }
    public interface OnSyncResult   { void run(int synced, int failed); }

    // ──────────────────────────────────────────────────────────────────
    /**
     * Guarda una asistencia:
     * - Si hay internet → Firebase directamente.
     * - Si no hay internet → Room local como "pending".
     */
    public static void saveAttendance(
            Context context,
            String classId, String className,
            String studentId, String studentName,
            String status,
            OnSavedOnline  onOnline,
            OnSavedOffline onOffline) {

        if (isOnline(context)) {
            // ── Online: subir a Firebase ───────────────────────────────
            Map<String, Object> data = buildFirestoreMap(
                    classId, className, studentId, studentName, status);

            FirebaseFirestore.getInstance()
                    .collection("attendance")
                    .add(data)
                    .addOnSuccessListener(ref -> { if (onOnline != null) onOnline.run(); })
                    .addOnFailureListener(e -> {
                        // Si falla aunque haya red, guardar offline igual
                        saveLocally(context, classId, className, studentId, studentName, status);
                        if (onOffline != null) onOffline.run();
                    });
        } else {
            // ── Offline: guardar en Room ───────────────────────────────
            saveLocally(context, classId, className, studentId, studentName, status);
            if (onOffline != null) onOffline.run();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    /**
     * Sincroniza todas las asistencias pendientes con Firebase.
     * Llama a onResult con (cuántas se subieron, cuántas fallaron).
     */
    public static void syncPending(Context context, OnSyncResult onResult) {
        executor.execute(() -> {
            PendingAttendanceDao dao = AppDatabase.getInstance(context).pendingAttendanceDao();
            List<PendingAttendance> pending = dao.getAllPending();

            if (pending.isEmpty()) {
                if (onResult != null) onResult.run(0, 0);
                return;
            }

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            int[] counts = {0, 0}; // [synced, failed]
            int total = pending.size();

            for (PendingAttendance p : pending) {
                Map<String, Object> data = buildFirestoreMap(
                        p.classId, p.className, p.studentId, p.studentName, p.status);
                data.put("timestamp", p.timestamp); // conservar timestamp original

                db.collection("attendance")
                        .add(data)
                        .addOnSuccessListener(ref -> {
                            dao.markAsSynced(p.id);
                            counts[0]++;
                            if (counts[0] + counts[1] == total && onResult != null)
                                onResult.run(counts[0], counts[1]);
                        })
                        .addOnFailureListener(e -> {
                            counts[1]++;
                            if (counts[0] + counts[1] == total && onResult != null)
                                onResult.run(counts[0], counts[1]);
                        });
            }
        });
    }

    // ──────────────────────────────────────────────────────────────────
    /** Cuenta pendientes (en background). Útil para badges. */
    public static void countPending(Context context, java.util.function.Consumer<Integer> callback) {
        executor.execute(() -> {
            int count = AppDatabase.getInstance(context).pendingAttendanceDao().countPending();
            if (callback != null) callback.accept(count);
        });
    }

    // ── Helpers privados ───────────────────────────────────────────────

    private static void saveLocally(Context context,
                                    String classId, String className,
                                    String studentId, String studentName,
                                    String status) {
        executor.execute(() -> {
            PendingAttendance p = new PendingAttendance(
                    classId, className, studentId, studentName, status);
            AppDatabase.getInstance(context).pendingAttendanceDao().insert(p);
        });
    }

    private static Map<String, Object> buildFirestoreMap(
            String classId, String className,
            String studentId, String studentName,
            String status) {
        Map<String, Object> data = new HashMap<>();
        data.put("classId",     classId);
        data.put("className",   className);
        data.put("studentId",   studentId);
        data.put("studentName", studentName);
        data.put("status",      status);
        data.put("timestamp",   System.currentTimeMillis());
        return data;
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null &&
                (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }
}