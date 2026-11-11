package com.example.visionica;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class EventReminderWorker extends Worker {

    public static final String CHANNEL_ID   = "visionica_events";
    public static final String KEY_TITLE    = "event_title";
    public static final String KEY_MESSAGE  = "event_message";
    public static final String KEY_NOTIF_ID = "notif_id";

    public EventReminderWorker(@NonNull Context context,
                               @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String title   = getInputData().getString(KEY_TITLE);
        String message = getInputData().getString(KEY_MESSAGE);
        int    notifId = getInputData().getInt(KEY_NOTIF_ID, 0);

        if (title == null)   title   = "Recordatorio";
        if (message == null) message = "Tienes un evento ahora";

        showNotification(title, message, notifId);
        return Result.success();
    }

    private void showNotification(String title, String message, int notifId) {
        Context context = getApplicationContext();
        NotificationManager manager =
                (NotificationManager) context
                        .getSystemService(Context.NOTIFICATION_SERVICE);

        // ── Crear canal (obligatorio Android 8+) ───────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Eventos del calendario",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Recordatorios de tus eventos programados");
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }

        // ── Al tocar → abre CalendarActivity ──────────────────────────
        Intent intent = new Intent(context, CalendarActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // ── Construir notificación ────────────────────────────────────
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_face_notification)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(message))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setVibrate(new long[]{0, 300, 200, 300});

        manager.notify(notifId, builder.build());
    }
}