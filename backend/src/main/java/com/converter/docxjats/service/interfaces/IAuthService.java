package com.converter.docxjats.service.interfaces;

import com.converter.docxjats.dto.AuthResponse;
import com.converter.docxjats.dto.GoogleAuthRequest;
import com.converter.docxjats.dto.LoginRequest;
import com.converter.docxjats.dto.RegisterRequest;
import com.converter.docxjats.entity.Usuario;

public interface IAuthService {
    AuthResponse login(LoginRequest request);
    AuthResponse register(RegisterRequest request);
    AuthResponse loginWithGoogle(GoogleAuthRequest request);
    Usuario getCurrentUser(String tokenHeader);
    void logout(String tokenHeader);
}

