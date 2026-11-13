package com.arise.corp.crm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;

/**
 * Solicitud para activar o desactivar un cliente (DP-API-05).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EstadoClienteRequest(
        @NotNull(message = "es obligatorio") Boolean activo) {
}
