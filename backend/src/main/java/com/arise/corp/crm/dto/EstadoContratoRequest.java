package com.arise.corp.crm.dto;

import com.arise.corp.crm.entity.EstadoContrato;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;

/**
 * Solicitud de cambio de estado de un contrato (DP-API-05).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EstadoContratoRequest(
        @NotNull(message = "es obligatorio") EstadoContrato estado) {
}
