package com.arise.corp.erp.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.arise.corp.erp.GeneradoresErp;
import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.TipoMovimiento;
import com.arise.corp.erp.repository.TotalMovimientos;

/**
 * Pruebas de la funcion pura del panel financiero (D10, Requirements 18.1,
 * 18.2, 18.4, 18.6-18.8 y 25.4).
 *
 * <p>Los conjuntos de movimientos generados se agrupan con un modelo en
 * memoria y se comparan con el resumen calculado. Se cubren las seis
 * combinaciones, los ceros, la cantidad total del tipo, el saldo pagado y el
 * formato de dos decimales con signo.
 */
class ResumenFinancieroResponseTests {

    private static final int CASOS = 120;

    private static final LocalDate DESDE = LocalDate.of(2026, 1, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 12, 31);

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 14: El resumen financiero es consistente con los movimientos")
    void elResumenEsConsistenteConLosMovimientosGenerados() {
        Random azar = GeneradoresErp.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            List<GeneradoresErp.MovimientoGenerado> movimientos =
                    GeneradoresErp.conjuntoMovimientos(1 + azar.nextInt(40));

            ResumenFinancieroResponse resumen = ResumenFinancieroResponse.desde(
                    DESDE, HASTA, filas(movimientos));

            comprobarContraElModelo(movimientos, resumen);
        }
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 14: El resumen financiero es consistente con los movimientos")
    void elResumenSinFilasTieneLasSeisCombinacionesEnCero() {
        ResumenFinancieroResponse resumen =
                ResumenFinancieroResponse.desde(DESDE, null, List.of());

        assertEquals(DESDE, resumen.desde());
        assertNull(resumen.hasta());
        assertEquals(2, resumen.porTipo().size());
        assertEquals("0.00", resumen.saldoPagado());

        for (TipoMovimiento tipo : TipoMovimiento.values()) {
            ResumenFinancieroResponse.ResumenTipo porTipo =
                    resumen.porTipo().get(tipo);

            assertEquals(0L, porTipo.cantidad(), tipo.name());
            assertEquals(3, porTipo.porEstado().size(), tipo.name());

            for (EstadoMovimiento estado : EstadoMovimiento.values()) {
                ResumenFinancieroResponse.ResumenEstado porEstado =
                        porTipo.porEstado().get(estado);

                assertEquals(0L, porEstado.cantidad(),
                        tipo + "/" + estado);
                assertEquals("0.00", porEstado.suma(),
                        tipo + "/" + estado);
            }
        }
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 5: Los importes viajan como texto sin perder precisión")
    void lasSumasYElSaldoTienenDosDecimalesYSigno() {
        List<GeneradoresErp.MovimientoGenerado> movimientos = List.of(
                movimiento("INGRESO", "PENDIENTE", "100.00"),
                movimiento("INGRESO", "PAGADO", "200.50"),
                movimiento("EGRESO", "PAGADO", "1200000.50"),
                movimiento("EGRESO", "ANULADO", "10.00"));

        ResumenFinancieroResponse resumen = ResumenFinancieroResponse.desde(
                DESDE, HASTA, filas(movimientos));

        assertEquals("-1199800.00", resumen.saldoPagado());
        assertTrue(resumen.saldoPagado().startsWith("-"));

        for (TipoMovimiento tipo : TipoMovimiento.values()) {
            for (EstadoMovimiento estado : EstadoMovimiento.values()) {
                String suma = resumen.porTipo().get(tipo)
                        .porEstado().get(estado).suma();

                assertTrue(suma.matches("-?[0-9]+\\.[0-9]{2}"),
                        tipo + "/" + estado + " -> " + suma);
                assertEquals(2, new BigDecimal(suma).scale(),
                        tipo + "/" + estado + " -> " + suma);
            }
        }
    }

    @Test
    void conservaLosLimitesAplicados() {
        ResumenFinancieroResponse resumen = ResumenFinancieroResponse.desde(
                DESDE, HASTA, List.of());

        assertEquals(DESDE, resumen.desde());
        assertEquals(HASTA, resumen.hasta());
    }

    private static void comprobarContraElModelo(
            List<GeneradoresErp.MovimientoGenerado> movimientos,
            ResumenFinancieroResponse resumen) {

        EnumMap<TipoMovimiento, EnumMap<EstadoMovimiento, Long>> cantidades =
                new EnumMap<>(TipoMovimiento.class);
        EnumMap<TipoMovimiento, EnumMap<EstadoMovimiento, BigDecimal>> sumas =
                new EnumMap<>(TipoMovimiento.class);

        for (TipoMovimiento tipo : TipoMovimiento.values()) {
            EnumMap<EstadoMovimiento, Long> cantidadesTipo =
                    new EnumMap<>(EstadoMovimiento.class);
            EnumMap<EstadoMovimiento, BigDecimal> sumasTipo =
                    new EnumMap<>(EstadoMovimiento.class);

            for (EstadoMovimiento estado : EstadoMovimiento.values()) {
                cantidadesTipo.put(estado, 0L);
                sumasTipo.put(estado, BigDecimal.ZERO);
            }

            cantidades.put(tipo, cantidadesTipo);
            sumas.put(tipo, sumasTipo);
        }

        for (GeneradoresErp.MovimientoGenerado movimiento : movimientos) {
            TipoMovimiento tipo = TipoMovimiento.valueOf(movimiento.tipo());
            EstadoMovimiento estado =
                    EstadoMovimiento.valueOf(movimiento.estado());

            cantidades.get(tipo).merge(estado, 1L, Long::sum);
            sumas.get(tipo).merge(estado, movimiento.valor(), BigDecimal::add);
        }

        for (TipoMovimiento tipo : TipoMovimiento.values()) {
            ResumenFinancieroResponse.ResumenTipo porTipo =
                    resumen.porTipo().get(tipo);
            long total = 0L;

            for (EstadoMovimiento estado : EstadoMovimiento.values()) {
                long cantidad = cantidades.get(tipo).get(estado);
                BigDecimal suma = sumas.get(tipo).get(estado);
                ResumenFinancieroResponse.ResumenEstado porEstado =
                        porTipo.porEstado().get(estado);

                assertEquals(cantidad, porEstado.cantidad(),
                        tipo + "/" + estado);
                assertEquals(texto(suma), porEstado.suma(),
                        tipo + "/" + estado);
                total += cantidad;
            }

            assertEquals(total, porTipo.cantidad(), tipo.name());
        }

        BigDecimal saldo = sumas.get(TipoMovimiento.INGRESO)
                .get(EstadoMovimiento.PAGADO)
                .subtract(sumas.get(TipoMovimiento.EGRESO)
                        .get(EstadoMovimiento.PAGADO));

        assertEquals(texto(saldo), resumen.saldoPagado());
        assertFalse(resumen.saldoPagado().contains("E"),
                "notacion cientifica: " + resumen.saldoPagado());
    }

    private static List<TotalMovimientos> filas(
            List<GeneradoresErp.MovimientoGenerado> movimientos) {

        EnumMap<TipoMovimiento, EnumMap<EstadoMovimiento, long[]>> agrupados =
                new EnumMap<>(TipoMovimiento.class);
        EnumMap<TipoMovimiento, EnumMap<EstadoMovimiento, BigDecimal>> sumas =
                new EnumMap<>(TipoMovimiento.class);

        for (TipoMovimiento tipo : TipoMovimiento.values()) {
            EnumMap<EstadoMovimiento, long[]> cantidadesTipo =
                    new EnumMap<>(EstadoMovimiento.class);
            EnumMap<EstadoMovimiento, BigDecimal> sumasTipo =
                    new EnumMap<>(EstadoMovimiento.class);

            for (EstadoMovimiento estado : EstadoMovimiento.values()) {
                cantidadesTipo.put(estado, new long[1]);
                sumasTipo.put(estado, BigDecimal.ZERO);
            }

            agrupados.put(tipo, cantidadesTipo);
            sumas.put(tipo, sumasTipo);
        }

        for (GeneradoresErp.MovimientoGenerado movimiento : movimientos) {
            TipoMovimiento tipo = TipoMovimiento.valueOf(movimiento.tipo());
            EstadoMovimiento estado =
                    EstadoMovimiento.valueOf(movimiento.estado());

            agrupados.get(tipo).get(estado)[0]++;
            sumas.get(tipo).merge(estado, movimiento.valor(), BigDecimal::add);
        }

        List<TotalMovimientos> filas = new ArrayList<>();

        for (TipoMovimiento tipo : TipoMovimiento.values()) {
            for (EstadoMovimiento estado : EstadoMovimiento.values()) {
                long cantidad = agrupados.get(tipo).get(estado)[0];

                if (cantidad > 0) {
                    filas.add(new TotalMovimientos(
                            tipo, estado, cantidad, sumas.get(tipo).get(estado)));
                }
            }
        }

        return filas;
    }

    private static GeneradoresErp.MovimientoGenerado movimiento(
            String tipo,
            String estado,
            String valor) {

        return new GeneradoresErp.MovimientoGenerado(
                tipo, estado, LocalDate.of(2026, 6, 15), new BigDecimal(valor));
    }

    private static String texto(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
