package com.arise.corp.sga.dto;

import java.util.UUID;

import com.arise.corp.shared.normalizacion.NormalizadorTexto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de alta de un paquete (SGA-02, SGA-05).
 *
 * <p>El identificador, el estado, el dron y la auditoria los controla el
 * servidor. El codigo se convierte en Codigo_Normalizado y los textos se
 * recortan en el constructor compacto. La {@code observacion} solo se guarda en
 * el primer registro del historial (Requirements 3, 7.2-7.4).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaqueteCrearRequest(
        @NotNull(message = "es obligatorio")
        @Pattern(regexp = "[A-Z0-9-]{3,30}",
                message = "debe tener entre 3 y 30 caracteres: letras, dígitos o guiones")
        String codigo,
        @NotNull(message = "es obligatorio")
        @Size(max = 500, message = "debe tener como máximo 500 caracteres")
        String descripcion,
        @NotNull(message = "es obligatorio")
        @Size(max = 200, message = "debe tener como máximo 200 caracteres")
        String direccionEntrega,
        @NotNull(message = "es obligatorio") UUID clienteId,
        UUID contratoId,
        @Size(max = 500, message = "debe tener como máximo 500 caracteres")
        String observacion) {

    public PaqueteCrearRequest {
        codigo = NormalizadorTexto.codigo(codigo);
        descripcion = NormalizadorTexto.texto(descripcion);
        direccionEntrega = NormalizadorTexto.texto(direccionEntrega);
        observacion = NormalizadorTexto.texto(observacion);
    }

    @Override
    public String toString() {
        return "PaqueteCrearRequest[datos protegidos]";
    }
}
