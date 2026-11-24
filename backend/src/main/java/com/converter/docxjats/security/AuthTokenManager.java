package com.converter.docxjats.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de autenticación y tokens de sesión para el entorno editorial.
 * Utiliza SHA-256 con salt para el hashing seguro de contraseñas y genera tokens criptográficamente seguros.
 */
@Component
public class AuthTokenManager {

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, UserSession> activeSessions = new ConcurrentHashMap<>();

    public record UserSession(Long userId, String username, String nombreCompleto, String rol, long timestamp) {}

    /**
     * Genera un hash SHA-256 de una contraseña dada una sal o genera una con formato sha256$salt$hash.
     */
    public String hashPassword(String rawPassword) {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        String saltHex = bytesToHex(salt);
        String hashHex = sha256(saltHex + rawPassword);
        return "sha256$" + saltHex + "$" + hashHex;
    }

    /**
     * Valida una contraseña contra su hash almacenado.
     */
    public boolean verifyPassword(String rawPassword, String storedHash) {
        if (storedHash == null || !storedHash.startsWith("sha256$")) {
            return false;
        }
        String[] parts = storedHash.split("\\$");
        if (parts.length != 3) {
            return false;
        }
        String saltHex = parts[1];
        String expectedHash = parts[2];
        String computedHash = sha256(saltHex + rawPassword);
        return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8),
                                    computedHash.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Crea un token de sesión para un usuario autenticado.
     */
    public String createSessionToken(Long userId, String username, String nombreCompleto, String rol) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        activeSessions.put(token, new UserSession(userId, username, nombreCompleto, rol, System.currentTimeMillis()));
        return token;
    }

    /**
     * Obtiene la sesión activa correspondiente al token.
     */
    public UserSession getSession(String token) {
        if (token == null) return null;
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        return activeSessions.get(token);
    }

    /**
     * Invalida la sesión.
     */
    public void invalidateToken(String token) {
        if (token == null) return;
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        activeSessions.remove(token);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error inicializando SHA-256", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

