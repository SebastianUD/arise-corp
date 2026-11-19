package com.arise.corp.erp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.arise.corp.erp.dto.ResumenFinancieroResponse;
import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.TipoMovimiento;
import com.arise.corp.erp.repository.MovimientoFinancieroRepository;
import com.arise.corp.erp.repository.TotalMovimientos;

/**
 * Pruebas de {@link ResumenFinancieroService} (D10, Requirement 18.3).
 *
 * <p>Mockito comprueba que el servicio delega en {@code totalizar} con los
 * limites recibidos, tambien nulos, y que la respuesta conserva las filas y
 * los limites.
 */
class ResumenFinancieroServiceTests {

    private static final LocalDate DESDE = LocalDate.of(2026, 1, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 12, 31);

    @Test
    void delegaEnTotalizarConLosLimitesRecibidos() {
        MovimientoFinancieroRepository movimientos =
                mock(MovimientoFinancieroRepository.class);
        when(movimientos.totalizar(DESDE, HASTA)).thenReturn(List.of(
                new TotalMovimientos(TipoMovimiento.INGRESO,
                        EstadoMovimiento.PAGADO, 2L, new BigDecimal("300.50")),
                new TotalMovimientos(TipoMovimiento.EGRESO,
                        EstadoMovimiento.PAGADO, 1L, new BigDecimal("100.25"))));

        ResumenFinancieroService servicio =
                new ResumenFinancieroService(movimientos);
        ResumenFinancieroResponse resumen =
                servicio.obtener(new RangoFechas(DESDE, HASTA));

        assertEquals(DESDE, resumen.desde());
        assertEquals(HASTA, resumen.hasta());
        assertEquals("200.25", resumen.saldoPagado());
        assertEquals(2L, resumen.porTipo().get(TipoMovimiento.INGRESO)
                .porEstado().get(EstadoMovimiento.PAGADO).cantidad());
        assertEquals("300.50", resumen.porTipo().get(TipoMovimiento.INGRESO)
                .porEstado().get(EstadoMovimiento.PAGADO).suma());
        assertEquals(1L, resumen.porTipo().get(TipoMovimiento.EGRESO)
                .porEstado().get(EstadoMovimiento.PAGADO).cantidad());

        verify(movimientos, times(1)).totalizar(DESDE, HASTA);
    }

    @Test
    void delegaEnTotalizarConLimitesNulos() {
        MovimientoFinancieroRepository movimientos =
                mock(MovimientoFinancieroRepository.class);
        when(movimientos.totalizar(null, null)).thenReturn(List.of());

        ResumenFinancieroService servicio =
                new ResumenFinancieroService(movimientos);
        ResumenFinancieroResponse resumen =
                servicio.obtener(new RangoFechas(null, null));

        assertNull(resumen.desde());
        assertNull(resumen.hasta());
        assertEquals("0.00", resumen.saldoPagado());
        assertEquals(0L, resumen.porTipo().get(TipoMovimiento.INGRESO)
                .cantidad());

        verify(movimientos, times(1)).totalizar(null, null);
    }

    @Test
    void delegaEnTotalizarConUnSoloLimite() {
        MovimientoFinancieroRepository movimientos =
                mock(MovimientoFinancieroRepository.class);
        when(movimientos.totalizar(DESDE, null)).thenReturn(List.of());

        ResumenFinancieroService servicio =
                new ResumenFinancieroService(movimientos);
        ResumenFinancieroResponse resumen =
                servicio.obtener(new RangoFechas(DESDE, null));

        assertEquals(DESDE, resumen.desde());
        assertNull(resumen.hasta());

        verify(movimientos, times(1)).totalizar(DESDE, null);
    }
}
