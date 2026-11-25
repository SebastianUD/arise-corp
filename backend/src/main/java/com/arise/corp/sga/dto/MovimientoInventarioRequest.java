package com.arise.corp.sga.dto;

import java.math.BigDecimal;

import com.arise.corp.sga.dto.validacion.MovimientoInventarioValido;
import com.arise.corp.sga.entity.TipoMovimientoInventario;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de registro de un movimiento de inventario (SGA-03).
 *
 * <p>{@link MovimientoInventarioValido} comprueba el rango de la cantidad
 * segun el tipo y exige la observacion en un {@code AJUSTE} (D5,
 * Requirements 15.6-15.8).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@MovimientoInventarioValido
public record MovimientoInventarioRequest(
        @NotNull(message = "es obligatorio") TipoMovimientoInventario tipo,
        @NotNull(message = "es obligatorio") BigDecimal cantidad,
        @Size(max = 500, message = "debe tener como máximo 500 caracteres")
        String observacion) {

    public MovimientoInventarioRequest {
        observacion = NormalizadorTexto.texto(observacion);
    }

    /**
     * Cantidad ya validada como entero dentro del rango de su tipo.
     */
    public int cantidadEntera() {
        return cantidad.intValueExact();
    }

    @Override
    public String toString() {
        return "MovimientoInventarioRequest[datos protegidos]";
    }
}
