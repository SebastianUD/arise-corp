package com.arise.corp.erp.service;

import java.time.LocalDate;

import com.arise.corp.shared.error.ParametroInvalidoException;

/**
 * Limites de fecha de los filtros y del panel financiero (D9).
 *
 * <p>Los dos limites son opcionales. Si ambos existen y {@code desde} es
 * posterior a {@code hasta}, el error se informa sobre {@code hasta}, igual
 * que {@code fechaFin} en el CRM.
 */
public record RangoFechas(LocalDate desde, LocalDate hasta) {

    public RangoFechas {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ParametroInvalidoException(
                    "hasta", "debe ser igual o posterior a la fecha desde");
        }
    }
}
