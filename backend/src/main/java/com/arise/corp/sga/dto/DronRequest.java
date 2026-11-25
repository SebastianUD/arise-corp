package com.arise.corp.sga.dto;

import com.arise.corp.shared.normalizacion.NormalizadorTexto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de alta y edicion de un dron (SGA-01).
 *
 * <p>El mismo DTO se usa en {@code POST} y {@code PUT}: el identificador, el
 * estado y la auditoria los controla el servidor, asi que se ignoran las
 * propiedades desconocidas. El codigo se convierte en Codigo_Normalizado y los
 * textos se recortan en el constructor compacto; un texto vacio queda nulo y la
 * obligatoriedad la comprueba {@code @NotNull} (Requirement 3).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DronRequest(
        @NotNull(message = "es obligatorio")
        @Pattern(regexp = "[A-Z0-9-]{3,30}",
                message = "debe tener entre 3 y 30 caracteres: letras, dígitos o guiones")
        String codigo,
        @NotNull(message = "es obligatorio")
        @Size(max = 100, message = "debe tener como máximo 100 caracteres")
        String modelo,
        @Size(max = 500, message = "debe tener como máximo 500 caracteres")
        String observaciones) {

    public DronRequest {
        codigo = NormalizadorTexto.codigo(codigo);
        modelo = NormalizadorTexto.texto(modelo);
        observaciones = NormalizadorTexto.texto(observaciones);
    }

    @Override
    public String toString() {
        return "DronRequest[datos protegidos]";
    }
}
