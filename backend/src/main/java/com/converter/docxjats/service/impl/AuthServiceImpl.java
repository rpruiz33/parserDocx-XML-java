package com.converter.docxjats.service.impl;

import com.converter.docxjats.dto.AuthResponse;
import com.converter.docxjats.dto.GoogleAuthRequest;
import com.converter.docxjats.dto.LoginRequest;
import com.converter.docxjats.dto.RegisterRequest;
import com.converter.docxjats.entity.Usuario;
import com.converter.docxjats.repository.UsuarioRepository;
import com.converter.docxjats.security.AuthTokenManager;
import com.converter.docxjats.service.interfaces.IAuthService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthServiceImpl implements IAuthService {

    private final UsuarioRepository usuarioRepository;
    private final AuthTokenManager tokenManager;

    public AuthServiceImpl(UsuarioRepository usuarioRepository, AuthTokenManager tokenManager) {
        this.usuarioRepository = usuarioRepository;
        this.tokenManager = tokenManager;
    }

    /**
     * Inicializa usuarios por defecto para pruebas y producción si la base está vacía.
     */
    @PostConstruct
    public void initDefaultUsers() {
        if (!usuarioRepository.existsByUsername("editor")) {
            Usuario editor = new Usuario(
                    "editor",
                    tokenManager.hashPassword("editor123"),
                    "Editor Científico UNLa",
                    "editor@unla.edu.ar",
                    "ROLE_EDITOR"
            );
            usuarioRepository.save(editor);
        }

        if (!usuarioRepository.existsByUsername("admin")) {
            Usuario admin = new Usuario(
                    "admin",
                    tokenManager.hashPassword("admin123"),
                    "Administrador Editorial",
                    "admin@unla.edu.ar",
                    "ROLE_ADMIN"
            );
            usuarioRepository.save(admin);
        }
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            throw new IllegalArgumentException("Usuario y contraseña requeridos");
        }

        Optional<Usuario> usuarioOpt = usuarioRepository.findByUsername(request.getUsername().trim());
        if (usuarioOpt.isEmpty()) {
            throw new IllegalArgumentException("Credenciales incorrectas");
        }

        Usuario usuario = usuarioOpt.get();
        if (!usuario.isActivo()) {
            throw new IllegalStateException("La cuenta de usuario está desactivada");
        }

        if (!tokenManager.verifyPassword(request.getPassword(), usuario.getPasswordHash())) {
            throw new IllegalArgumentException("Credenciales incorrectas");
        }

        String token = tokenManager.createSessionToken(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombreCompleto(),
                usuario.getRol()
        );

        return new AuthResponse(
                token,
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombreCompleto(),
                usuario.getEmail(),
                usuario.getRol(),
                usuario.getAvatarUrl(),
                usuario.getAuthProvider()
        );
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Datos de registro requeridos");
        }
        String username = request.getUsername() != null ? request.getUsername().trim() : "";
        String password = request.getPassword() != null ? request.getPassword().trim() : "";
        String nombre = request.getNombreCompleto() != null ? request.getNombreCompleto().trim() : "";
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";

        if (username.length() < 3) {
            throw new IllegalArgumentException("El nombre de usuario debe tener al menos 3 caracteres");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres");
        }
        if (nombre.isEmpty()) {
            throw new IllegalArgumentException("El nombre completo es obligatorio");
        }
        if (usuarioRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("El nombre de usuario ya está registrado");
        }
        if (!email.isEmpty() && usuarioRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("El correo electrónico ya está registrado");
        }

        Usuario nuevo = new Usuario(
                username,
                tokenManager.hashPassword(password),
                nombre,
                email.isEmpty() ? null : email,
                "ROLE_EDITOR"
        );
        nuevo.setAuthProvider("LOCAL");
        Usuario guardado = usuarioRepository.save(nuevo);

        String token = tokenManager.createSessionToken(
                guardado.getId(),
                guardado.getUsername(),
                guardado.getNombreCompleto(),
                guardado.getRol()
        );

        return new AuthResponse(
                token,
                guardado.getId(),
                guardado.getUsername(),
                guardado.getNombreCompleto(),
                guardado.getEmail(),
                guardado.getRol(),
                guardado.getAvatarUrl(),
                guardado.getAuthProvider()
        );
    }

    @Override
    public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Datos de autenticación con Google requeridos");
        }

        String email = request.getEmail();
        String name = request.getName();
        String sub = request.getSub();
        String picture = request.getPicture();

        // Si viene un token JWT (credential de Google), extraer el payload si es posible
        if (request.getCredential() != null && !request.getCredential().isBlank()) {
            try {
                String[] parts = request.getCredential().split("\\.");
                if (parts.length >= 2) {
                    byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
                    String payloadJson = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
                    
                    // Parseo simple de campos comunes del JWT de Google
                    email = extractJsonField(payloadJson, "email", email);
                    name = extractJsonField(payloadJson, "name", name);
                    sub = extractJsonField(payloadJson, "sub", sub);
                    picture = extractJsonField(payloadJson, "picture", picture);
                }
            } catch (Exception e) {
                // Si falla el parseo manual del token, se usan los datos directos del request
            }
        }

        if (sub == null || sub.isBlank()) {
            if (email != null && !email.isBlank()) {
                sub = "google_" + email;
            } else {
                throw new IllegalArgumentException("No se pudo identificar la cuenta de Google");
            }
        }

        // 1. Buscar si ya existe por Google provider y sub
        Optional<Usuario> usuarioOpt = usuarioRepository.findByAuthProviderAndProviderId("GOOGLE", sub);

        // 2. Si no, buscar por email
        if (usuarioOpt.isEmpty() && email != null && !email.isBlank()) {
            usuarioOpt = usuarioRepository.findByEmail(email.toLowerCase());
        }

        Usuario usuario;
        if (usuarioOpt.isPresent()) {
            usuario = usuarioOpt.get();
            // Actualizar avatar o provider si es necesario
            if (picture != null && !picture.isBlank()) {
                usuario.setAvatarUrl(picture);
            }
            if (usuario.getProviderId() == null) {
                usuario.setProviderId(sub);
            }
            usuarioRepository.save(usuario);
        } else {
            // Registrar nuevo usuario federado con Google
            String baseUsername = (email != null && email.contains("@")) 
                    ? email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9_.-]", "") 
                    : ("google_" + sub.substring(0, Math.min(8, sub.length())));
            
            String finalUsername = baseUsername;
            int counter = 1;
            while (usuarioRepository.existsByUsername(finalUsername)) {
                finalUsername = baseUsername + counter++;
            }

            usuario = new Usuario();
            usuario.setUsername(finalUsername);
            usuario.setNombreCompleto(name != null && !name.isBlank() ? name : finalUsername);
            usuario.setEmail(email != null ? email.toLowerCase() : null);
            usuario.setRol("ROLE_EDITOR");
            usuario.setAuthProvider("GOOGLE");
            usuario.setProviderId(sub);
            usuario.setAvatarUrl(picture);
            usuario.setPasswordHash(""); // En MySQL la columna existente puede tener NOT NULL; usamos string vacío para federados
            usuario = usuarioRepository.save(usuario);
        }

        String token = tokenManager.createSessionToken(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombreCompleto(),
                usuario.getRol()
        );

        return new AuthResponse(
                token,
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombreCompleto(),
                usuario.getEmail(),
                usuario.getRol(),
                usuario.getAvatarUrl(),
                usuario.getAuthProvider()
        );
    }

    private String extractJsonField(String json, String field, String defaultValue) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx == -1) return defaultValue;
        int colon = json.indexOf(':', idx + key.length());
        if (colon == -1) return defaultValue;
        int quoteStart = json.indexOf('"', colon);
        if (quoteStart == -1) return defaultValue;
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd == -1) return defaultValue;
        return json.substring(quoteStart + 1, quoteEnd);
    }

    @Override
    public Usuario getCurrentUser(String tokenHeader) {
        AuthTokenManager.UserSession session = tokenManager.getSession(tokenHeader);
        if (session == null) {
            return null;
        }
        return usuarioRepository.findById(session.userId()).orElse(null);
    }

    @Override
    public void logout(String tokenHeader) {
        tokenManager.invalidateToken(tokenHeader);
    }
}

