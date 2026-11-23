package com.arise.corp.sga.dto;

import java.math.BigDecimal;

import com.arise.corp.sga.dto.validacion.CantidadEntera;
import com.arise.corp.sga.entity.CategoriaArticulo;
import com.arise.corp.sga.entity.UnidadArticulo;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de alta y edicion de un articulo de inventario (SGA-03).
 *
 * <p>El mismo DTO se usa en {@code POST} y {@code PUT}: la existencia y la
 * auditoria las controla el servidor, asi que se ignoran las propiedades
 * desconocidas. {@code stockMinimo} se recibe como {@link BigDecimal} y se
 * valida como entero entre 0 y 1 000 000 para no truncar decimales (D5,
 * Requirement 13.5).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArticuloRequest(
        @NotNull(message = "es obligatorio")
        @Pattern(regexp = "[A-Z0-9-]{3,30}",
                message = "debe tener entre 3 y 30 caracteres: letras, dígitos o guiones")
        String codigo,
        @NotNull(message = "es obligatorio")
        @Size(max = 200, message = "debe tener como máximo 200 caracteres")
        String nombre,
        @NotNull(message = "es obligatorio") CategoriaArticulo categoria,
        @NotNull(message = "es obligatorio") UnidadArticulo unidad,
        @NotNull(message = "es obligatorio")
        @CantidadEntera(min = 0) BigDecimal stockMinimo) {

    public ArticuloRequest {
        codigo = NormalizadorTexto.codigo(codigo);
        nombre = NormalizadorTexto.texto(nombre);
    }
}
