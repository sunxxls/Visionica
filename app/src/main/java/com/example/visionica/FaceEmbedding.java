package com.example.visionica;

import java.util.List;

public class FaceEmbedding {
    private String id;
    private String studentId;
    private String studentName;
    private String classId;
    private List<Float> embedding;      // frente
    private List<Float> embeddingLeft;  // izquierda
    private List<Float> embeddingRight; // derecha
    private long registeredAt;

    public FaceEmbedding() {}

    public FaceEmbedding(String studentId, String studentName, String classId, List<Float> embedding) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.classId = classId;
        this.embedding = embedding;
        this.registeredAt = System.currentTimeMillis();
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public List<Float> getEmbedding() { return embedding; }
    public void setEmbedding(List<Float> embedding) { this.embedding = embedding; }

    public List<Float> getEmbeddingLeft() { return embeddingLeft; }
    public void setEmbeddingLeft(List<Float> embeddingLeft) { this.embeddingLeft = embeddingLeft; }

    public List<Float> getEmbeddingRight() { return embeddingRight; }
    public void setEmbeddingRight(List<Float> embeddingRight) { this.embeddingRight = embeddingRight; }

    public long getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(long registeredAt) { this.registeredAt = registeredAt; }
}