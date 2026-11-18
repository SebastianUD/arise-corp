package com.arise.corp.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;

/**
 * Solicitud para activar o desactivar un empleado o un proveedor (DP-API-05).
 *
 * <p>DTO compartido por las dos verticales (D14).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EstadoActivoRequest(
        @NotNull(message = "es obligatorio") Boolean activo) {
}
