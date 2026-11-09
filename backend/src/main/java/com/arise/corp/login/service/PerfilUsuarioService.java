package com.arise.corp.login.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.login.repository.PerfilUsuarioRepository;
import com.arise.corp.login.repository.UsuarioAuthRepository;

/**
 * Resuelve y administra el perfil empresarial de un usuario autenticado.
 *
 * <p>La identidad proviene del {@code sub} del JWT ya validado por Spring
 * Security. Un perfil inexistente o inactivo se rechaza con 403 (DP-SEG-01).
 * La creacion y actualizacion de perfiles es exclusiva del Administrador y
 * queda auditada con el {@code sub} del actor (DP-PRO-01, DP-SEG-06).
 */
@Service
public class PerfilUsuarioService {

    private final PerfilUsuarioRepository perfilUsuarioRepository;
    private final UsuarioAuthRepository usuarioAuthRepository;

    public PerfilUsuarioService(
            PerfilUsuarioRepository perfilUsuarioRepository,
            UsuarioAuthRepository usuarioAuthRepository) {

        this.perfilUsuarioRepository = perfilUsuarioRepository;
        this.usuarioAuthRepository = usuarioAuthRepository;
    }

    @Transactional(readOnly = true)
    public PerfilUsuario obtenerPerfilActivo(String sub) {
        return buscarPerfilActivo(sub)
                .orElseThrow(() -> new AccessDeniedException(
                        "El usuario no tiene un perfil activo."));
    }

    /**
     * Busca el perfil activo asociado al {@code sub}. Devuelve vacio si el
     * token no identifica un UUID, si no hay perfil o si esta inactivo.
     */
    @Transactional(readOnly = true)
    public Optional<PerfilUsuario> buscarPerfilActivo(String sub) {
        Optional<UUID> usuarioId = parsearUsuarioId(sub);

        return usuarioId
                .flatMap(perfilUsuarioRepository::findByUsuarioId)
                .filter(PerfilUsuario::isActivo);
    }

    /**
     * Crea o actualiza el rol de un perfil. Devuelve vacio cuando se intenta
     * crear un perfil para un UUID que no existe en Supabase Auth.
     */
    @Transactional
    public Optional<ResultadoPerfil> crearOActualizarPerfil(
            UUID usuarioId,
            RolUsuario rol,
            String actorSub) {

        Instant ahora = Instant.now();
        UUID actor = UUID.fromString(actorSub);

        Optional<PerfilUsuario> existente =
                perfilUsuarioRepository.findByUsuarioId(usuarioId);

        if (existente.isPresent()) {
            PerfilUsuario perfil = existente.get();
            perfil.actualizarRol(rol, actor, ahora);
            return Optional.of(new ResultadoPerfil(perfil, false));
        }

        if (!usuarioAuthRepository.existe(usuarioId)) {
            return Optional.empty();
        }

        PerfilUsuario perfil = PerfilUsuario.crear(usuarioId, rol, actor, ahora);
        perfilUsuarioRepository.save(perfil);

        return Optional.of(new ResultadoPerfil(perfil, true));
    }

    /**
     * Activa o desactiva un perfil existente. Devuelve vacio si no existe.
     */
    @Transactional
    public Optional<PerfilUsuario> cambiarEstado(
            UUID usuarioId,
            boolean activo,
            String actorSub) {

        Instant ahora = Instant.now();
        UUID actor = UUID.fromString(actorSub);

        return perfilUsuarioRepository.findByUsuarioId(usuarioId)
                .map(perfil -> {
                    perfil.actualizarEstado(activo, actor, ahora);
                    return perfil;
                });
    }

    private Optional<UUID> parsearUsuarioId(String sub) {
        try {
            return Optional.of(UUID.fromString(sub));
        } catch (IllegalArgumentException | NullPointerException ex) {
            return Optional.empty();
        }
    }
}
