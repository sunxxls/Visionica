package com.example.visionica;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Base de datos Room de la app.
 * Singleton — siempre se usa AppDatabase.getInstance(context).
 */
@Database(entities = {PendingAttendance.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract PendingAttendanceDao pendingAttendanceDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "visionica_db"
                            )
                            .fallbackToDestructiveMigration() // en dev está bien; en prod usa migraciones
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}