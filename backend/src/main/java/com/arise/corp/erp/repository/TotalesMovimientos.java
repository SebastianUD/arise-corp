package com.arise.corp.erp.repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Fragmento del panel financiero (D10, ERP-04).
 *
 * <p>Spring Data compone el fragmento con la implementacion
 * {@link TotalesMovimientosImpl}, en el mismo paquete y con el sufijo
 * {@code Impl}.
 */
public interface TotalesMovimientos {

    /**
     * Cantidad y suma por {@code (tipo, estado)}; un limite {@code null} no
     * restringe.
     */
    List<TotalMovimientos> totalizar(LocalDate desde, LocalDate hasta);
}
