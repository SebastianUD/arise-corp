package com.arise.corp.login.dto;

import java.time.Instant;
import java.util.UUID;

import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.entity.RolUsuario;

/**
 * Representacion de un perfil empresarial devuelta por la administracion.
 */
public record PerfilUsuarioResponse(
        UUID usuarioId,
        RolUsuario rol,
        boolean activo,
        Instant creadoEn,
        Instant actualizadoEn,
        UUID actualizadoPor) {

    public static PerfilUsuarioResponse desde(PerfilUsuario perfil) {
        return new PerfilUsuarioResponse(
                perfil.getUsuarioId(),
                perfil.getRol(),
                perfil.isActivo(),
                perfil.getCreadoEn(),
                perfil.getActualizadoEn(),
                perfil.getActualizadoPor());
    }
}
