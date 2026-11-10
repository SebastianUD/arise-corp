package com.arise.corp.login.dto;

import com.arise.corp.login.entity.RolUsuario;

import jakarta.validation.constraints.NotNull;

/**
 * Solicitud para crear o actualizar el rol de un perfil.
 *
 * <p>El identificador, el estado activo y los campos de auditoria los controla
 * el servidor; el cliente solo puede fijar el rol.
 */
public record PerfilRequest(@NotNull RolUsuario rol) {
}
