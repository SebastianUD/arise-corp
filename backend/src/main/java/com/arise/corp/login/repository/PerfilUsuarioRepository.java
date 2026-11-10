package com.arise.corp.login.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.arise.corp.login.entity.PerfilUsuario;

/**
 * Acceso a {@code app.perfiles_usuario}. La busqueda se hace por el
 * {@code sub} UUID del JWT validado (DP-SEG-01).
 */
public interface PerfilUsuarioRepository
        extends JpaRepository<PerfilUsuario, UUID> {

    Optional<PerfilUsuario> findByUsuarioId(UUID usuarioId);
}
