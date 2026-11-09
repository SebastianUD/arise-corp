package com.arise.corp.login.controller;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arise.corp.login.dto.EstadoPerfilRequest;
import com.arise.corp.login.dto.PerfilRequest;
import com.arise.corp.login.dto.PerfilUsuarioResponse;
import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.service.PerfilUsuarioService;
import com.arise.corp.login.service.ResultadoPerfil;
import com.arise.corp.shared.error.RecursoNoEncontradoException;

import jakarta.validation.Valid;

/**
 * Administracion de perfiles empresariales. Solo el Administrador puede
 * invocar estas rutas; la restriccion se aplica en {@code SecurityConfig}
 * (DP-PRO-01).
 */
@RestController
@RequestMapping("/api/admin/usuarios")
public class AdminPerfilController {

    private final PerfilUsuarioService perfilUsuarioService;

    public AdminPerfilController(
            PerfilUsuarioService perfilUsuarioService) {

        this.perfilUsuarioService = perfilUsuarioService;
    }

    /**
     * Crea un perfil (201) o actualiza el rol de uno existente (200).
     *
     * <p>El estado activo y los campos de auditoria los controla el servidor.
     * Un UUID inexistente en Supabase Auth responde 404.
     */
    @PutMapping("/{id}/perfil")
    public ResponseEntity<PerfilUsuarioResponse> crearOActualizarPerfil(
            @PathVariable UUID id,
            @Valid @RequestBody PerfilRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        Optional<ResultadoPerfil> resultado =
                perfilUsuarioService.crearOActualizarPerfil(
                        id, request.rol(), jwt.getSubject());

        if (resultado.isEmpty()) {
            throw new RecursoNoEncontradoException(
                    "El perfil o la identidad del usuario no existe.");
        }

        ResultadoPerfil perfil = resultado.get();
        PerfilUsuarioResponse cuerpo =
                PerfilUsuarioResponse.desde(perfil.perfil());

        if (perfil.creado()) {
            URI location = URI.create(
                    "/api/admin/usuarios/" + id + "/perfil");
            return ResponseEntity.created(location).body(cuerpo);
        }

        return ResponseEntity.ok(cuerpo);
    }

    /**
     * Activa o desactiva un perfil existente (200). Un perfil inexistente
     * responde 404.
     */
    @PatchMapping("/{id}/estado")
    public ResponseEntity<PerfilUsuarioResponse> cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody EstadoPerfilRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        Optional<PerfilUsuario> perfil = perfilUsuarioService.cambiarEstado(
                id, request.activo(), jwt.getSubject());

        return perfil
                .map(actualizado -> ResponseEntity.ok(
                        PerfilUsuarioResponse.desde(actualizado)))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El perfil del usuario no existe."));
    }
}
