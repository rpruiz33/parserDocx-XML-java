package com.converter.docxjats.repository;

import com.converter.docxjats.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsername(String username);
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByAuthProviderAndProviderId(String authProvider, String providerId);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}

