package com.arise.corp.sga.dto;

import java.util.UUID;

import com.arise.corp.shared.normalizacion.NormalizadorTexto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de edicion de un paquete (SGA-02).
 *
 * <p>No incluye {@code codigo}, {@code clienteId}, {@code estado} ni
 * {@code dronId}: el cliente los ignora (Requirement 9.4). Los textos se
 * recortan en el constructor compacto (Requirement 3).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaqueteActualizarRequest(
        @NotNull(message = "es obligatorio")
        @Size(max = 500, message = "debe tener como máximo 500 caracteres")
        String descripcion,
        @NotNull(message = "es obligatorio")
        @Size(max = 200, message = "debe tener como máximo 200 caracteres")
        String direccionEntrega,
        UUID contratoId) {

    public PaqueteActualizarRequest {
        descripcion = NormalizadorTexto.texto(descripcion);
        direccionEntrega = NormalizadorTexto.texto(direccionEntrega);
    }

    @Override
    public String toString() {
        return "PaqueteActualizarRequest[datos protegidos]";
    }
}
