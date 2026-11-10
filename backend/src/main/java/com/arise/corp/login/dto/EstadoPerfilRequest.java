package com.arise.corp.login.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Solicitud para activar o desactivar un perfil existente.
 */
public record EstadoPerfilRequest(@NotNull Boolean activo) {
}
