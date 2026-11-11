package com.example.visionica;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Modelo Room — una asistencia guardada localmente sin conexión.
 * Cada campo mapea a una columna en la tabla "pending_attendances".
 */
@Entity(tableName = "pending_attendances")
public class PendingAttendance {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String classId;
    public String className;
    public String studentId;
    public String studentName;

    /** "present" | "late" | "absent" | "license" */
    public String status;

    /** Timestamp en milisegundos del momento del registro */
    public long timestamp;

    /** "pending" | "synced" */
    public String syncStatus;

    // Constructor vacío requerido por Room
    public PendingAttendance() {}

    public PendingAttendance(String classId, String className,
                             String studentId, String studentName,
                             String status) {
        this.classId     = classId;
        this.className   = className;
        this.studentId   = studentId;
        this.studentName = studentName;
        this.status      = status;
        this.timestamp   = System.currentTimeMillis();
        this.syncStatus  = "pending";
    }
}