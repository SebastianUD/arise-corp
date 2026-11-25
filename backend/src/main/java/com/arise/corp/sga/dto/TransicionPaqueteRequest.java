package com.arise.corp.sga.dto;

import java.util.UUID;

import com.arise.corp.sga.dto.validacion.DronSegunEstado;
import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Solicitud del cambio de estado de un paquete (SGA-02, SGA-05).
 *
 * <p>{@link DronSegunEstado} exige {@code dronId} solo al pasar a
 * {@code ASIGNADO} (Requirements 11.1, 11.2). La observacion se recorta y un
 * texto vacio queda nulo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@DronSegunEstado
public record TransicionPaqueteRequest(
        @NotNull(message = "es obligatorio") EstadoPaquete estado,
        UUID dronId,
        @Size(max = 500, message = "debe tener como máximo 500 caracteres")
        String observacion) {

    public TransicionPaqueteRequest {
        observacion = NormalizadorTexto.texto(observacion);
    }

    @Override
    public String toString() {
        return "TransicionPaqueteRequest[datos protegidos]";
    }
}
