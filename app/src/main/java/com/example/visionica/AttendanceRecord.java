package com.example.visionica;

public class AttendanceRecord {
    private String id;
    private String classId;
    private String studentId;
    private String studentName;
    private String studentPhoto;
    private String status; // "present", "late", "absent"
    private long timestamp;

    public AttendanceRecord() {}

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentPhoto() { return studentPhoto; }
    public void setStudentPhoto(String studentPhoto) { this.studentPhoto = studentPhoto; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}