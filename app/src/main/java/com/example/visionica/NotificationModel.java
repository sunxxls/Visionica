package com.example.visionica;

import com.google.firebase.firestore.DocumentId;
import java.util.Date;

public class NotificationModel {

    @DocumentId
    private String id;

    private String userId;
    private String type;          // "attendance" | "calendar"
    private String title;
    private String message;

    // Campos asistencia
    private String classId;
    private String className;
    private String status;        // present | late | license | absent

    // Campos calendario
    private String eventTitle;
    private String description;
    private long   eventTimestamp;

    // Campos comunes
    private long    timestamp;
    private boolean read;

    // ── Constructor vacío requerido por Firestore ──────────────────────
    public NotificationModel() {}

    // ── Constructor de asistencia ──────────────────────────────────────
    public NotificationModel(String userId, String classId,
                             String className, String status) {
        this.userId    = userId;
        this.type      = "attendance";
        this.classId   = classId;
        this.className = className;
        this.status    = status;
        this.timestamp = System.currentTimeMillis();
        this.read      = false;

        switch (status) {
            case "present":
                this.title   = "Asistencia registrada";
                this.message = "Tu asistencia fue registrada como Presente en la clase " + className;
                break;
            case "late":
                this.title   = "Atraso registrado";
                this.message = "Tu asistencia fue registrada como Atraso en la clase " + className;
                break;
            case "license":
                this.title   = "Licencia registrada";
                this.message = "Tu asistencia fue registrada como Licencia en la clase " + className;
                break;
            case "absent":
                this.title   = "Falta registrada";
                this.message = "Tu asistencia fue registrada como Ausente en la clase " + className;
                break;
            default:
                this.title   = "Asistencia registrada";
                this.message = "Tu asistencia fue registrada en la clase " + className;
        }
    }

    // ── Constructor de calendario ──────────────────────────────────────
    public NotificationModel(String userId, String eventTitle,
                             String description, long eventTimestamp,
                             @SuppressWarnings("unused") boolean isCalendar) {
        this.userId         = userId;
        this.type           = "calendar";
        this.eventTitle     = eventTitle;
        this.description    = description;
        this.eventTimestamp = eventTimestamp;
        this.timestamp      = System.currentTimeMillis();
        this.read           = false;
        this.title          = "Recordatorio de evento";

        if (eventTimestamp > 0) {
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
            String timeStr = sdf.format(new Date(eventTimestamp));
            this.message = "Recordatorio: tienes el evento " + eventTitle + " a las " + timeStr;
        } else {
            this.message = "Tu evento " + eventTitle + " está programado en el calendario";
        }
    }

    // ── Getters y Setters ──────────────────────────────────────────────

    public String  getId()                         { return id; }
    public void    setId(String id)                { this.id = id; }

    public String  getUserId()                     { return userId; }
    public void    setUserId(String v)             { this.userId = v; }

    public String  getType()                       { return type; }
    public void    setType(String v)               { this.type = v; }

    public String  getTitle()                      { return title; }
    public void    setTitle(String v)              { this.title = v; }

    public String  getMessage()                    { return message; }
    public void    setMessage(String v)            { this.message = v; }

    public String  getClassId()                    { return classId; }
    public void    setClassId(String v)            { this.classId = v; }

    public String  getClassName()                  { return className; }
    public void    setClassName(String v)          { this.className = v; }

    public String  getStatus()                     { return status; }
    public void    setStatus(String v)             { this.status = v; }

    public String  getEventTitle()                 { return eventTitle; }
    public void    setEventTitle(String v)         { this.eventTitle = v; }

    public String  getDescription()                { return description; }
    public void    setDescription(String v)        { this.description = v; }

    public long    getEventTimestamp()             { return eventTimestamp; }
    public void    setEventTimestamp(long v)       { this.eventTimestamp = v; }

    public long    getTimestamp()                  { return timestamp; }
    public void    setTimestamp(long v)            { this.timestamp = v; }

    public boolean isRead()                        { return read; }
    public void    setRead(boolean v)              { this.read = v; }
}