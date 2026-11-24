package com.converter.docxjats.controller;

import com.converter.docxjats.dto.AuthResponse;
import com.converter.docxjats.dto.LoginRequest;
import com.converter.docxjats.entity.Usuario;
import com.converter.docxjats.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class AuthControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/auth";
    }

    @Test
    public void testLoginSuccessWithEditor() {
        LoginRequest req = new LoginRequest("editor", "editor123");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                getBaseUrl() + "/login",
                req,
                AuthResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isNotEmpty();
        assertThat(response.getBody().getUsername()).isEqualTo("editor");
        assertThat(response.getBody().getRol()).isEqualTo("ROLE_EDITOR");

        // Test /me con token
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + response.getBody().getToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<AuthResponse> meResponse = restTemplate.exchange(
                getBaseUrl() + "/me",
                HttpMethod.GET,
                entity,
                AuthResponse.class
        );

        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResponse.getBody()).isNotNull();
        assertThat(meResponse.getBody().getUsername()).isEqualTo("editor");
    }

    @Test
    public void testLoginFailureWithWrongPassword() {
        LoginRequest req = new LoginRequest("editor", "wrongpassword");
        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/login",
                req,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void testRegisterNewUserSuccess() {
        String testUser = "nuevo_editor_" + System.currentTimeMillis();
        com.converter.docxjats.dto.RegisterRequest req = new com.converter.docxjats.dto.RegisterRequest(
                testUser,
                "password123",
                "Nuevo Editor Test",
                testUser + "@unla.edu.ar"
        );

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                getBaseUrl() + "/register",
                req,
                AuthResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isNotEmpty();
        assertThat(response.getBody().getUsername()).isEqualTo(testUser);
        assertThat(response.getBody().getAuthProvider()).isEqualTo("LOCAL");
    }

    @Test
    public void testGoogleFederatedLogin() {
        com.converter.docxjats.dto.GoogleAuthRequest req = new com.converter.docxjats.dto.GoogleAuthRequest(
                null,
                "investigador.fed@gmail.com",
                "Investigador Federado Google",
                "google_sub_123456789",
                "https://lh3.googleusercontent.com/a/test-avatar"
        );

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                getBaseUrl() + "/google",
                req,
                AuthResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isNotEmpty();
        assertThat(response.getBody().getNombreCompleto()).isEqualTo("Investigador Federado Google");
        assertThat(response.getBody().getAuthProvider()).isEqualTo("GOOGLE");
    }
}
