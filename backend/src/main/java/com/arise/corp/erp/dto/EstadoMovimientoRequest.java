package com.arise.corp.erp.dto;

import com.arise.corp.erp.entity.EstadoMovimiento;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;

/**
 * Solicitud del cambio de estado de un movimiento financiero (D14).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EstadoMovimientoRequest(
        @NotNull(message = "es obligatorio") EstadoMovimiento estado) {
}
