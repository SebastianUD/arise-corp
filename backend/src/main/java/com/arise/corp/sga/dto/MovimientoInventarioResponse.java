package com.arise.corp.sga.dto;

import java.time.Instant;
import java.util.UUID;

import com.arise.corp.sga.entity.MovimientoInventario;
import com.arise.corp.sga.entity.TipoMovimientoInventario;

/**
 * Representacion de un movimiento de inventario (Requirement 15.1).
 *
 * <p>Expone el articulo por identificador y las existencias anterior y
 * resultante del movimiento.
 */
public record MovimientoInventarioResponse(
        UUID id,
        UUID articuloId,
        TipoMovimientoInventario tipo,
        int cantidad,
        long existenciaAnterior,
        long existenciaResultante,
        String observacion,
        Instant registradoEn,
        UUID registradoPor) {

    public static MovimientoInventarioResponse desde(
            MovimientoInventario movimiento) {

        return new MovimientoInventarioResponse(
                movimiento.getId(),
                movimiento.getArticulo().getId(),
                movimiento.getTipo(),
                movimiento.getCantidad(),
                movimiento.getExistenciaAnterior(),
                movimiento.getExistenciaResultante(),
                movimiento.getObservacion(),
                movimiento.getRegistradoEn(),
                movimiento.getRegistradoPor());
    }

    @Override
    public String toString() {
        return "MovimientoInventarioResponse[datos protegidos]";
    }
}
