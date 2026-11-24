package com.converter.docxjats.dto;

public class GoogleAuthRequest {
    private String credential; // Google ID Token (JWT) enviado por Google Sign-In SDK
    private String email;
    private String name;
    private String sub;        // Google Subject ID
    private String picture;

    public GoogleAuthRequest() {}

    public GoogleAuthRequest(String credential, String email, String name, String sub, String picture) {
        this.credential = credential;
        this.email = email;
        this.name = name;
        this.sub = sub;
        this.picture = picture;
    }

    public String getCredential() {
        return credential;
    }

    public void setCredential(String credential) {
        this.credential = credential;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSub() {
        return sub;
    }

    public void setSub(String sub) {
        this.sub = sub;
    }

    public String getPicture() {
        return picture;
    }

    public void setPicture(String picture) {
        this.picture = picture;
    }
}

