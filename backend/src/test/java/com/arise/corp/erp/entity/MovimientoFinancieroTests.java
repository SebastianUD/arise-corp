package com.arise.corp.erp.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Defensas de invariantes y auditorias separadas de
 * {@link MovimientoFinanciero} (D8, D11, D13).
 */
class MovimientoFinancieroTests {

    private static final UUID ACTOR =
            UUID.fromString("6a1f7c2e-0000-4000-8000-000000000001");
    private static final UUID OTRO_ACTOR =
            UUID.fromString("6a1f7c2e-0000-4000-8000-000000000002");
    private static final UUID CLIENTE_ID =
            UUID.fromString("6a1f7c2e-0000-4000-8000-000000000003");
    private static final Instant ANTES = Instant.parse("2026-11-18T15:00:00Z");
    private static final Instant DESPUES = Instant.parse("2026-11-19T18:30:00Z");
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 10);
    private static final BigDecimal VALOR = new BigDecimal("1250000.50");
    private static final String CONCEPTO = "Servicio ficticio";
    private static final String REFERENCIA = "FAC-2026-001";

    static Stream<Arguments> paresDeEstado() {
        return Stream.of(EstadoMovimiento.values())
                .flatMap(origen -> Stream.of(EstadoMovimiento.values())
                        .map(destino -> Arguments.of(origen, destino)));
    }

    @Test
    void crearDejaElEstadoPendienteYLaAuditoriaDeEstadoIgualALaDeDatos() {
        MovimientoFinanciero movimiento = ingresoPendiente();

        assertEquals(EstadoMovimiento.PENDIENTE, movimiento.getEstado());
        assertEquals(ANTES, movimiento.getEstadoActualizadoEn());
        assertEquals(ACTOR, movimiento.getEstadoActualizadoPor());
        assertEquals(movimiento.getCreadoEn(), movimiento.getEstadoActualizadoEn());
        assertEquals(movimiento.getActualizadoEn(), movimiento.getEstadoActualizadoEn());
        assertEquals(movimiento.getActualizadoPor(), movimiento.getEstadoActualizadoPor());
    }

    @Test
    void crearAceptaLaRelacionCoherenteConElTipo() {
        MovimientoFinanciero ingreso = ingresoPendiente();
        MovimientoFinanciero egreso = MovimientoFinanciero.crear(
                TipoMovimiento.EGRESO,
                FECHA,
                CONCEPTO,
                VALOR,
                REFERENCIA,
                null,
                proveedor(),
                ACTOR,
                ANTES);
        MovimientoFinanciero sinRelacion = MovimientoFinanciero.crear(
                TipoMovimiento.INGRESO,
                FECHA,
                CONCEPTO,
                VALOR,
                null,
                null,
                null,
                ACTOR,
                ANTES);

        assertEquals(CLIENTE_ID, ingreso.getClienteId());
        assertNull(ingreso.getProveedor());
        assertNull(egreso.getClienteId());
        assertNotNull(egreso.getProveedor());
        assertNull(sinRelacion.getClienteId());
        assertNull(sinRelacion.getProveedor());
    }

    @Test
    void crearRechazaLaRelacionIncoherenteConElTipo() {
        assertThrows(IllegalArgumentException.class, () -> MovimientoFinanciero.crear(
                TipoMovimiento.EGRESO,
                FECHA,
                CONCEPTO,
                VALOR,
                REFERENCIA,
                CLIENTE_ID,
                null,
                ACTOR,
                ANTES));

        assertThrows(IllegalArgumentException.class, () -> MovimientoFinanciero.crear(
                TipoMovimiento.INGRESO,
                FECHA,
                CONCEPTO,
                VALOR,
                REFERENCIA,
                null,
                proveedor(),
                ACTOR,
                ANTES));
    }

    @Test
    void actualizarDatosReemplazaCamposYAuditoriaDeDatosSinTocarElEstado() {
        MovimientoFinanciero movimiento = ingresoPendiente();
        LocalDate nuevaFecha = FECHA.plusDays(3);
        BigDecimal nuevoValor = new BigDecimal("999.99");

        movimiento.actualizarDatos(
                TipoMovimiento.EGRESO,
                nuevaFecha,
                "Compra ficticia",
                nuevoValor,
                "FAC-2026-002",
                null,
                proveedor(),
                OTRO_ACTOR,
                DESPUES);

        assertEquals(TipoMovimiento.EGRESO, movimiento.getTipo());
        assertEquals(nuevaFecha, movimiento.getFecha());
        assertEquals("Compra ficticia", movimiento.getConcepto());
        assertEquals(nuevoValor, movimiento.getValor());
        assertEquals("FAC-2026-002", movimiento.getReferencia());
        assertNull(movimiento.getClienteId());
        assertNotNull(movimiento.getProveedor());
        assertEquals(DESPUES, movimiento.getActualizadoEn());
        assertEquals(OTRO_ACTOR, movimiento.getActualizadoPor());
        assertEquals(ANTES, movimiento.getCreadoEn());

        assertEquals(EstadoMovimiento.PENDIENTE, movimiento.getEstado());
        assertEquals(ANTES, movimiento.getEstadoActualizadoEn());
        assertEquals(ACTOR, movimiento.getEstadoActualizadoPor());
    }

    @Test
    void actualizarDatosRechazaLaRelacionIncoherenteSinModificarElMovimiento() {
        MovimientoFinanciero movimiento = ingresoPendiente();

        assertThrows(IllegalArgumentException.class, () -> movimiento.actualizarDatos(
                TipoMovimiento.EGRESO,
                FECHA,
                CONCEPTO,
                VALOR,
                REFERENCIA,
                CLIENTE_ID,
                null,
                OTRO_ACTOR,
                DESPUES));

        assertThrows(IllegalArgumentException.class, () -> movimiento.actualizarDatos(
                TipoMovimiento.INGRESO,
                FECHA,
                CONCEPTO,
                VALOR,
                REFERENCIA,
                null,
                proveedor(),
                OTRO_ACTOR,
                DESPUES));

        assertEquals(TipoMovimiento.INGRESO, movimiento.getTipo());
        assertEquals(CLIENTE_ID, movimiento.getClienteId());
        assertNull(movimiento.getProveedor());
        assertEquals(ANTES, movimiento.getActualizadoEn());
        assertEquals(ACTOR, movimiento.getActualizadoPor());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoMovimiento.class, names = {"PAGADO", "ANULADO"})
    void actualizarDatosRechazaUnMovimientoNoEditable(EstadoMovimiento estadoFinal) {
        MovimientoFinanciero movimiento = ingresoPendiente();
        movimiento.cambiarEstado(estadoFinal, OTRO_ACTOR, DESPUES);

        assertThrows(IllegalStateException.class, () -> movimiento.actualizarDatos(
                TipoMovimiento.INGRESO,
                FECHA.plusDays(1),
                "Otro concepto",
                VALOR,
                null,
                null,
                null,
                OTRO_ACTOR,
                DESPUES.plusSeconds(60)));

        assertEquals(estadoFinal, movimiento.getEstado());
        assertEquals(DESPUES, movimiento.getEstadoActualizadoEn());
        assertEquals(OTRO_ACTOR, movimiento.getEstadoActualizadoPor());
        assertEquals(CONCEPTO, movimiento.getConcepto());
        assertEquals(ANTES, movimiento.getActualizadoEn());
    }

    @Test
    void cambiarEstadoSoloActualizaSuAuditoria() {
        MovimientoFinanciero movimiento = ingresoPendiente();

        movimiento.cambiarEstado(EstadoMovimiento.PAGADO, OTRO_ACTOR, DESPUES);

        assertEquals(EstadoMovimiento.PAGADO, movimiento.getEstado());
        assertEquals(DESPUES, movimiento.getEstadoActualizadoEn());
        assertEquals(OTRO_ACTOR, movimiento.getEstadoActualizadoPor());

        assertEquals(TipoMovimiento.INGRESO, movimiento.getTipo());
        assertEquals(FECHA, movimiento.getFecha());
        assertEquals(CONCEPTO, movimiento.getConcepto());
        assertEquals(VALOR, movimiento.getValor());
        assertEquals(REFERENCIA, movimiento.getReferencia());
        assertEquals(CLIENTE_ID, movimiento.getClienteId());
        assertNull(movimiento.getProveedor());
        assertEquals(ANTES, movimiento.getCreadoEn());
        assertEquals(ANTES, movimiento.getActualizadoEn());
        assertEquals(ACTOR, movimiento.getActualizadoPor());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("paresDeEstado")
    void cambiarEstadoSoloAceptaLasTransicionesPermitidas(
            EstadoMovimiento origen,
            EstadoMovimiento destino) {

        MovimientoFinanciero movimiento = enEstado(origen);
        boolean permitida = origen == EstadoMovimiento.PENDIENTE
                && (destino == EstadoMovimiento.PAGADO
                        || destino == EstadoMovimiento.ANULADO);

        if (permitida) {
            movimiento.cambiarEstado(destino, OTRO_ACTOR, DESPUES);

            assertEquals(destino, movimiento.getEstado());
            assertEquals(DESPUES, movimiento.getEstadoActualizadoEn());
            assertEquals(OTRO_ACTOR, movimiento.getEstadoActualizadoPor());
            return;
        }

        assertThrows(IllegalStateException.class,
                () -> movimiento.cambiarEstado(destino, OTRO_ACTOR, DESPUES));
        assertEquals(origen, movimiento.getEstado());
        assertEquals(ANTES, movimiento.getEstadoActualizadoEn());
        assertEquals(ACTOR, movimiento.getEstadoActualizadoPor());
    }

    private static MovimientoFinanciero ingresoPendiente() {
        return MovimientoFinanciero.crear(
                TipoMovimiento.INGRESO,
                FECHA,
                CONCEPTO,
                VALOR,
                REFERENCIA,
                CLIENTE_ID,
                null,
                ACTOR,
                ANTES);
    }

    private static MovimientoFinanciero enEstado(EstadoMovimiento estado) {
        MovimientoFinanciero movimiento = ingresoPendiente();
        if (estado != EstadoMovimiento.PENDIENTE) {
            movimiento.cambiarEstado(estado, ACTOR, ANTES);
        }
        return movimiento;
    }

    private static Proveedor proveedor() {
        return Proveedor.crear(
                TipoDocumentoProveedor.NIT,
                "900123456",
                "Proveedor ficticio",
                null,
                "proveedor@example.test",
                "601 000 0000",
                null,
                null,
                ACTOR,
                ANTES);
    }
}
