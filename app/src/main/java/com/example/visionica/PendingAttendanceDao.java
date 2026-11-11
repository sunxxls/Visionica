package com.example.visionica;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * DAO de Room para acceder a las asistencias pendientes.
 */
@Dao
public interface PendingAttendanceDao {

    /** Inserta una asistencia pendiente */
    @Insert
    void insert(PendingAttendance attendance);

    /** Devuelve todas las asistencias aún pendientes de sincronizar */
    @Query("SELECT * FROM pending_attendances WHERE syncStatus = 'pending' ORDER BY timestamp DESC")
    List<PendingAttendance> getAllPending();

    /** Cuenta cuántas hay pendientes (para el badge del menú) */
    @Query("SELECT COUNT(*) FROM pending_attendances WHERE syncStatus = 'pending'")
    int countPending();

    /** Marca una asistencia como sincronizada por su ID */
    @Query("UPDATE pending_attendances SET syncStatus = 'synced' WHERE id = :id")
    void markAsSynced(int id);

    /** Elimina todas las ya sincronizadas (limpieza opcional) */
    @Query("DELETE FROM pending_attendances WHERE syncStatus = 'synced'")
    void deleteSynced();
}