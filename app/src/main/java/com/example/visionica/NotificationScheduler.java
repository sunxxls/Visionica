package com.example.visionica;

import android.content.Context;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class NotificationScheduler {

    /**
     * Programa una notificación en el celular exactamente a la hora del evento.
     *
     * @param context        contexto de la Activity
     * @param eventTitle     título del evento
     * @param eventTimestamp milisegundos Unix del momento del evento
     */
    public static void scheduleEventReminder(Context context,
                                             String eventTitle,
                                             long eventTimestamp) {

        long now   = System.currentTimeMillis();
        long delay = eventTimestamp - now;

        // Si el evento ya pasó o es en menos de 10 segundos, no programar
        if (delay < 10_000) return;

        // ── Datos que recibirá el Worker ───────────────────────────────
        String message = "Tu evento \"" + eventTitle + "\" es ahora";

        Data inputData = new Data.Builder()
                .putString(EventReminderWorker.KEY_TITLE,    "📅 " + eventTitle)
                .putString(EventReminderWorker.KEY_MESSAGE,  message)
                .putInt   (EventReminderWorker.KEY_NOTIF_ID, (int)(eventTimestamp % Integer.MAX_VALUE))
                .build();

        // ── Crear el trabajo con el delay exacto ───────────────────────
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(
                EventReminderWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                // Tag único por evento — permite cancelar si se borra el evento
                .addTag("event_" + eventTimestamp)
                .build();

        WorkManager.getInstance(context).enqueue(workRequest);
    }

    /**
     * Cancela la notificación programada para un evento específico.
     * Llama esto cuando el usuario borra un evento del calendario.
     *
     * @param context        contexto
     * @param eventTimestamp timestamp del evento a cancelar
     */
    public static void cancelEventReminder(Context context, long eventTimestamp) {
        WorkManager.getInstance(context)
                .cancelAllWorkByTag("event_" + eventTimestamp);
    }
}