package com.converter.docxjats.service.impl;

import com.converter.docxjats.dto.AuthResponse;
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
            throw new IllegalArgumentException("Usuario o email y contraseña requeridos");
        }

        String identifier = request.getUsername().trim();
        Optional<Usuario> usuarioOpt = usuarioRepository.findByUsername(identifier);
        if (usuarioOpt.isEmpty() && identifier.contains("@")) {
            usuarioOpt = usuarioRepository.findByEmail(identifier.toLowerCase());
        }

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
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String password = request.getPassword() != null ? request.getPassword().trim() : "";

        if (email.isEmpty() || !email.contains("@")) {
            throw new IllegalArgumentException("Ingresá un correo electrónico válido");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres");
        }

        if (usuarioRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("El correo electrónico ya está registrado");
        }

        // Generar username y nombre a partir del email o de los campos provistos
        String baseUsername = (request.getUsername() != null && !request.getUsername().trim().isEmpty())
                ? request.getUsername().trim()
                : email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9_.-]", "");

        if (baseUsername.length() < 3) {
            baseUsername = "user_" + baseUsername;
        }

        String finalUsername = baseUsername;
        int counter = 1;
        while (usuarioRepository.existsByUsername(finalUsername)) {
            finalUsername = baseUsername + counter++;
        }

        String nombre = (request.getNombreCompleto() != null && !request.getNombreCompleto().trim().isEmpty())
                ? request.getNombreCompleto().trim()
                : finalUsername;

        Usuario nuevo = new Usuario(
                finalUsername,
                tokenManager.hashPassword(password),
                nombre,
                email,
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

