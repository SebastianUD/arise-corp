package com.arise.corp.crm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;

/**
 * Solicitud para activar o desactivar una cuenta corporativa (DP-API-05).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EstadoCuentaRequest(
        @NotNull(message = "es obligatorio") Boolean activa) {
}
