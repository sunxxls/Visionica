package com.example.visionica;

public class Student {
    private String id;
    private String name;
    private String email;
    private String photoUrl;
    private boolean faceRegistered;

    public Student() {}

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public boolean isFaceRegistered() { return faceRegistered; }
    public void setFaceRegistered(boolean faceRegistered) { this.faceRegistered = faceRegistered; }
}
