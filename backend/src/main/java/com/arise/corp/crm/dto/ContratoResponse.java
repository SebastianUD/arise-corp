package com.arise.corp.crm.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.Contrato;
import com.arise.corp.crm.entity.EstadoContrato;

/**
 * Representacion de un contrato, la misma para detalle y listas
 * (Requirements 12.4 y 12.5, DP-API-04).
 *
 * <p>{@code valor} viaja como texto con exactamente dos decimales para no
 * perder precision en JavaScript.
 */
public record ContratoResponse(
        UUID id,
        String codigo,
        String descripcion,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String valor,
        EstadoContrato estado,
        ClienteRef cliente,
        CuentaCorporativaRef cuentaCorporativa,
        Instant estadoActualizadoEn,
        UUID estadoActualizadoPor,
        Instant creadoEn,
        Instant actualizadoEn) {

    public static ContratoResponse desde(Contrato contrato) {
        Cliente cliente = contrato.getCliente();

        return new ContratoResponse(
                contrato.getId(),
                contrato.getCodigo(),
                contrato.getDescripcion(),
                contrato.getFechaInicio(),
                contrato.getFechaFin(),
                formatoValor(contrato.getValor()),
                contrato.getEstado(),
                ClienteRef.desde(cliente),
                cliente != null
                        ? CuentaCorporativaRef.desde(cliente.getCuentaCorporativa())
                        : null,
                contrato.getEstadoActualizadoEn(),
                contrato.getEstadoActualizadoPor(),
                contrato.getCreadoEn(),
                contrato.getActualizadoEn());
    }

    private static String formatoValor(BigDecimal valor) {
        return valor == null
                ? null
                : valor.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
