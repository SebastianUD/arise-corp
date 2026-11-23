package com.arise.corp.sga.dto;

import com.arise.corp.sga.entity.EstadoDron;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;

/**
 * Solicitud del cambio manual de estado de un dron (SGA-01).
 *
 * <p>El estado destino es obligatorio; las transiciones manuales permitidas las
 * comprueba la entidad {@code Dron} (Requirements 6.1, 6.2).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EstadoDronRequest(
        @NotNull(message = "es obligatorio") EstadoDron estado) {
}
