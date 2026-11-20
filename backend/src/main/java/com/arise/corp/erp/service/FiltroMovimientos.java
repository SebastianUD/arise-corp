package com.arise.corp.erp.service;

import java.util.UUID;

import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.TipoMovimiento;

/**
 * Filtros de la lista de movimientos financieros (Requirement 15).
 *
 * <p>Record sin logica: todos los campos son opcionales y se combinan con
 * {@code y}. El rango nunca es {@code null}; el controlador lo construye con
 * los dos parametros de fecha.
 */
public record FiltroMovimientos(
        TipoMovimiento tipo,
        EstadoMovimiento estado,
        UUID clienteId,
        UUID proveedorId,
        RangoFechas rango,
        String q) {
}
