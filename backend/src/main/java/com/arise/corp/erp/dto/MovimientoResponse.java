package com.arise.corp.erp.dto;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import com.arise.corp.crm.dto.ClienteReferencia;
import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.MovimientoFinanciero;
import com.arise.corp.erp.entity.TipoMovimiento;

/**
 * Representacion de un movimiento financiero, la misma en detalle y en listas
 * (Requirements 14.12, 15.8, 15.9).
 *
 * <p>{@code valor} viaja como texto con dos decimales para no perder
 * precision (DP-API-04). El cliente y el proveedor son {@code null} cuando no
 * existe la relacion.
 */
public record MovimientoResponse(
        UUID id,
        TipoMovimiento tipo,
        LocalDate fecha,
        String concepto,
        String valor,
        String referencia,
        EstadoMovimiento estado,
        RegistroAsociado cliente,
        RegistroAsociado proveedor,
        Instant estadoActualizadoEn,
        UUID estadoActualizadoPor,
        Instant creadoEn,
        Instant actualizadoEn,
        UUID actualizadoPor) {

    /**
     * Convierte la entidad en la representacion del contrato.
     *
     * @param cliente referencia del CRM; obligatoria si el movimiento tiene
     *        {@code clienteId}, porque la FK garantiza que el cliente existe
     * @throws NullPointerException si el movimiento tiene cliente y la
     *         referencia recibida es {@code null}
     */
    public static MovimientoResponse desde(
            MovimientoFinanciero movimiento,
            ClienteReferencia cliente) {

        if (movimiento.getClienteId() != null) {
            Objects.requireNonNull(cliente, "cliente");
        }

        return new MovimientoResponse(
                movimiento.getId(),
                movimiento.getTipo(),
                movimiento.getFecha(),
                movimiento.getConcepto(),
                movimiento.getValor()
                        .setScale(2, RoundingMode.UNNECESSARY)
                        .toPlainString(),
                movimiento.getReferencia(),
                movimiento.getEstado(),
                RegistroAsociado.desde(cliente),
                RegistroAsociado.desde(movimiento.getProveedor()),
                movimiento.getEstadoActualizadoEn(),
                movimiento.getEstadoActualizadoPor(),
                movimiento.getCreadoEn(),
                movimiento.getActualizadoEn(),
                movimiento.getActualizadoPor());
    }
}
