package com.converter.docxjats.dto;

public class AuthResponse {
    private String token;
    private Long id;
    private String username;
    private String nombreCompleto;
    private String email;
    private String rol;
    private String avatarUrl;
    private String authProvider;

    public AuthResponse() {}

    public AuthResponse(String token, Long id, String username, String nombreCompleto, String email, String rol) {
        this(token, id, username, nombreCompleto, email, rol, null, "LOCAL");
    }

    public AuthResponse(String token, Long id, String username, String nombreCompleto, String email, String rol, String avatarUrl, String authProvider) {
        this.token = token;
        this.id = id;
        this.username = username;
        this.nombreCompleto = nombreCompleto;
        this.email = email;
        this.rol = rol;
        this.avatarUrl = avatarUrl;
        this.authProvider = authProvider;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(String authProvider) {
        this.authProvider = authProvider;
    }
}

