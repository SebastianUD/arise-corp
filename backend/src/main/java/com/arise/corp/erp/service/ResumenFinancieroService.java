package com.arise.corp.erp.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arise.corp.erp.dto.ResumenFinancieroResponse;
import com.arise.corp.erp.repository.MovimientoFinancieroRepository;

/**
 * Panel financiero del ERP (ERP-04, D10).
 *
 * <p>Los totales salen de una unica consulta agrupada en una transaccion de
 * solo lectura, de modo que las cantidades por estado, la cantidad del tipo y
 * el saldo son coherentes entre si (Requirement 18.6).
 */
@Service
@Transactional(readOnly = true)
public class ResumenFinancieroService {

    private final MovimientoFinancieroRepository movimientos;

    public ResumenFinancieroService(MovimientoFinancieroRepository movimientos) {
        this.movimientos = movimientos;
    }

    /**
     * Calcula el resumen de los movimientos dentro del rango recibido.
     *
     * @param rango limites opcionales; nunca es {@code null}
     * @return el resumen con las seis combinaciones y el saldo pagado
     */
    public ResumenFinancieroResponse obtener(RangoFechas rango) {
        return ResumenFinancieroResponse.desde(
                rango.desde(),
                rango.hasta(),
                movimientos.totalizar(rango.desde(), rango.hasta()));
    }
}
