package com.arise.corp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.MovimientoFinanciero;
import com.arise.corp.erp.entity.TipoMovimiento;
import com.arise.corp.erp.repository.MovimientoFinancieroRepository;
import com.arise.corp.erp.service.ConflictosErp;
import com.arise.corp.erp.service.MovimientoService;
import com.arise.corp.shared.error.ConflictoNegocioException;

/**
 * Transacciones y concurrencia del cambio de estado sobre PostgreSQL efimero
 * (Requirements 17.4, 17.5 y 26.8, DP-SEG-06, ENG-03).
 *
 * <p>Comprueba que un cambio confirmado actualiza juntas las tres columnas de
 * la auditoria de estado sin tocar la auditoria de datos, que una excepcion
 * posterior dentro de la misma transaccion revierte el cambio y que dos
 * transiciones simultaneas desde {@code PENDIENTE} dejan exactamente una
 * aplicada y la otra en {@code TRANSICION_NO_PERMITIDA} (D12).
 */
class MovimientoTransaccionIT extends PostgresIntegracionBase {

    private static final UUID ACTOR_DATOS =
            UUID.fromString("00000000-0000-4000-8000-000000000401");
    private static final UUID ACTOR_ESTADO =
            UUID.fromString("00000000-0000-4000-8000-000000000402");
    private static final Instant CREADO = Instant.parse("2026-01-15T10:00:00Z");
    private static final LocalDate FECHA = LocalDate.of(2026, 1, 15);
    private static final BigDecimal VALOR = new BigDecimal("2500000.50");
    private static final String CONCEPTO = "Movimiento de transaccion";
    private static final String REFERENCIA = "TRA-001";

    @Autowired
    private MovimientoService servicio;

    @Autowired
    private MovimientoFinancieroRepository movimientos;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Test
    @DisplayName("Un cambio confirmado actualiza estado y su auditoria sin tocar actualizado_*")
    void elCambioConfirmadoActualizaJuntasLasTresColumnasDeEstado() {
        UUID id = crearPendiente();
        Instant estadoAntes = estadoActualizadoEn(id);

        servicio.cambiarEstado(id, EstadoMovimiento.PAGADO, ACTOR_ESTADO.toString());

        assertEquals("PAGADO", estado(id));
        assertEquals(ACTOR_ESTADO, estadoActualizadoPor(id));
        assertNotEquals(estadoAntes, estadoActualizadoEn(id));
        assertTrue(estadoActualizadoEn(id).isAfter(estadoAntes),
                "estado_actualizado_en debe avanzar con el cambio");

        assertEquals(CREADO, actualizadoEn(id),
                "actualizado_en no debe cambiar al cambiar el estado");
        assertEquals(ACTOR_DATOS, actualizadoPor(id),
                "actualizado_por no debe cambiar al cambiar el estado");
    }

    @Test
    @DisplayName("Una excepcion posterior en la transaccion revierte el estado y su auditoria")
    void unaExcepcionPosteriorRevierteElCambioDeEstado() {
        UUID id = crearPendiente();
        TransactionTemplate transaccion = new TransactionTemplate(transacciones);

        assertThrows(IllegalStateException.class, () -> transaccion.execute(estado -> {
            servicio.cambiarEstado(
                    id, EstadoMovimiento.PAGADO, ACTOR_ESTADO.toString());
            throw new IllegalStateException("fallo simulado despues del cambio");
        }));

        assertEquals("PENDIENTE", estado(id));
        assertEquals(ACTOR_DATOS, estadoActualizadoPor(id));
        assertEquals(CREADO, estadoActualizadoEn(id));
        assertEquals(CREADO, actualizadoEn(id));
        assertEquals(ACTOR_DATOS, actualizadoPor(id));
    }

    @Test
    @DisplayName("Dos transiciones simultaneas dejan una aplicada y la otra en TRANSICION_NO_PERMITIDA")
    void dosTransicionesSimultaneasSoloAplicanUna() throws Exception {
        UUID id = crearPendiente();
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch arranque = new CountDownLatch(1);

        try {
            Future<Intento> pago = ejecutor.submit(
                    () -> intentar(id, EstadoMovimiento.PAGADO, listos, arranque));
            Future<Intento> anulacion = ejecutor.submit(
                    () -> intentar(id, EstadoMovimiento.ANULADO, listos, arranque));

            assertTrue(listos.await(10, TimeUnit.SECONDS),
                    "los dos hilos deben estar listos antes de arrancar");
            arranque.countDown();

            List<Intento> intentos = List.of(
                    pago.get(30, TimeUnit.SECONDS),
                    anulacion.get(30, TimeUnit.SECONDS));

            assertEquals(1, intentos.stream().filter(Intento::exitoso).count(),
                    "exactamente una transicion debe aplicarse");

            Intento ganador = intentos.stream()
                    .filter(Intento::exitoso)
                    .findFirst()
                    .orElseThrow();
            Intento perdedor = intentos.stream()
                    .filter(intento -> !intento.exitoso())
                    .findFirst()
                    .orElseThrow();

            assertEquals(ConflictosErp.TRANSICION_NO_PERMITIDA,
                    perdedor.conflicto());
            assertEquals(ganador.destino().name(), perdedor.estadoActual(),
                    "el perdedor debe ver el estado ya confirmado por el ganador");
            assertEquals(perdedor.destino().name(),
                    perdedor.estadoSolicitado());

            assertEquals(ganador.destino().name(), estado(id));
            assertEquals(ACTOR_ESTADO, estadoActualizadoPor(id));
            assertEquals(CREADO, actualizadoEn(id));
            assertEquals(ACTOR_DATOS, actualizadoPor(id));
        } finally {
            ejecutor.shutdownNow();
        }
    }

    private UUID crearPendiente() {
        MovimientoFinanciero movimiento = MovimientoFinanciero.crear(
                TipoMovimiento.INGRESO,
                FECHA,
                CONCEPTO,
                VALOR,
                REFERENCIA,
                null,
                null,
                ACTOR_DATOS,
                CREADO);

        return movimientos.saveAndFlush(movimiento).getId();
    }

    private Intento intentar(
            UUID id,
            EstadoMovimiento destino,
            CountDownLatch listos,
            CountDownLatch arranque) throws InterruptedException {

        listos.countDown();
        arranque.await();

        try {
            servicio.cambiarEstado(id, destino, ACTOR_ESTADO.toString());
            return new Intento(destino, true, null, null, null);
        } catch (ConflictoNegocioException excepcion) {
            return new Intento(
                    destino,
                    false,
                    excepcion.getConflicto(),
                    (String) excepcion.getExtensiones().get("estadoActual"),
                    (String) excepcion.getExtensiones().get("estadoSolicitado"));
        }
    }

    private String estado(UUID id) {
        return jdbc.queryForObject(
                "select estado from app.movimientos_financieros where id = ?",
                String.class, id);
    }

    private Instant estadoActualizadoEn(UUID id) {
        return jdbc.queryForObject(
                "select estado_actualizado_en from app.movimientos_financieros where id = ?",
                OffsetDateTime.class, id).toInstant();
    }

    private UUID estadoActualizadoPor(UUID id) {
        return jdbc.queryForObject(
                "select estado_actualizado_por from app.movimientos_financieros where id = ?",
                UUID.class, id);
    }

    private Instant actualizadoEn(UUID id) {
        return jdbc.queryForObject(
                "select actualizado_en from app.movimientos_financieros where id = ?",
                OffsetDateTime.class, id).toInstant();
    }

    private UUID actualizadoPor(UUID id) {
        return jdbc.queryForObject(
                "select actualizado_por from app.movimientos_financieros where id = ?",
                UUID.class, id);
    }

    private record Intento(
            EstadoMovimiento destino,
            boolean exitoso,
            String conflicto,
            String estadoActual,
            String estadoSolicitado) {
    }
}
