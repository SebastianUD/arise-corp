package com.arise.corp.erp.repository;

import java.math.BigDecimal;

import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.TipoMovimiento;

/**
 * Fila del panel financiero: cantidad y suma de una combinacion de tipo y
 * estado (D10).
 */
public record TotalMovimientos(
        TipoMovimiento tipo,
        EstadoMovimiento estado,
        Long cantidad,
        BigDecimal suma) {
}
