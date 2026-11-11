package com.example.visionica;

import java.util.List;

public class ClassModel {
    private String id;
    private String name;
    private String section;
    private String room;
    private String subject;
    private String code;
    private String teacherId;
    private String teacherName;
    private List<String> students;
    private long createdAt;
    private boolean archived;

    private String bannerUrl = "";

    // Constructor vacío requerido por Firestore
    public ClassModel() {
    }

    public String getBannerUrl() { return bannerUrl; }
    public void setBannerUrl(String bannerUrl) { this.bannerUrl = bannerUrl; }

    // Constructor completo
    public ClassModel(String id, String name, String section, String room, String subject,
                      String code, String teacherId, String teacherName, List<String> students,
                      long createdAt, boolean archived) {
        this.id = id;
        this.name = name;
        this.section = section;
        this.room = room;
        this.subject = subject;
        this.code = code;
        this.teacherId = teacherId;
        this.teacherName = teacherName;
        this.students = students;
        this.createdAt = createdAt;
        this.archived = archived;
    }

    // Getters y Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(String teacherId) {
        this.teacherId = teacherId;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public List<String> getStudents() {
        return students;
    }

    public void setStudents(List<String> students) {
        this.students = students;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }
}