package com.arise.corp.sga.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.arise.corp.sga.GeneradoresSga;
import com.arise.corp.sga.GeneradoresSga.MovimientoGenerado;
import com.arise.corp.sga.dto.MovimientoInventarioRequest;
import com.arise.corp.sga.dto.MovimientoInventarioResponse;
import com.arise.corp.sga.entity.ArticuloInventario;
import com.arise.corp.sga.entity.CategoriaArticulo;
import com.arise.corp.sga.entity.MovimientoInventario;
import com.arise.corp.sga.entity.TipoMovimientoInventario;
import com.arise.corp.sga.entity.UnidadArticulo;
import com.arise.corp.sga.repository.ArticuloInventarioRepository;
import com.arise.corp.sga.repository.MovimientoInventarioRepository;
import com.arise.corp.shared.error.ConflictoNegocioException;
import com.arise.corp.shared.error.RecursoNoEncontradoException;

/**
 * Pruebas de {@link MovimientoInventarioService} (Requirement 25.1, Property
 * 7).
 *
 * <p>Mockito y {@code Clock.fixed} sobre una entidad real: recorren secuencias
 * de movimientos generadas con semilla fija y comprueban la aritmetica, el
 * rechazo de la existencia negativa y la auditoria del actor, sin base de
 * datos.
 */
class MovimientoInventarioServiceTests {

    private static final UUID ACTOR =
            UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    private static final String ACTOR_SUB = ACTOR.toString();
    private static final UUID ARTICULO_ID =
            UUID.fromString("00000000-0000-4000-8000-0000000000d2");
    private static final Instant AHORA =
            Instant.parse("2026-11-25T15:00:00.000000Z");
    private static final Instant ANTERIOR = AHORA.minusSeconds(3600);
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final String OBSERVACION_AJUSTE = "Conteo fisico ficticio";

    private ArticuloInventarioRepository articulos;
    private MovimientoInventarioRepository movimientos;
    private MovimientoInventarioService servicio;
    private ArticuloInventario articulo;
    private final List<MovimientoInventario> guardados = new ArrayList<>();

    @BeforeEach
    void setUp() {
        articulos = mock(ArticuloInventarioRepository.class);
        movimientos = mock(MovimientoInventarioRepository.class);
        servicio = new MovimientoInventarioService(articulos, movimientos, RELOJ);

        articulo = ArticuloInventario.crear(
                "ART-FICT-0001",
                "Articulo ficticio",
                CategoriaArticulo.REPUESTO,
                UnidadArticulo.UNIDAD,
                10,
                ACTOR,
                ANTERIOR);
        ReflectionTestUtils.setField(articulo, "id", ARTICULO_ID);

        guardados.clear();

        when(articulos.findParaModificarById(ARTICULO_ID))
                .thenReturn(Optional.of(articulo));
        when(movimientos.save(any(MovimientoInventario.class)))
                .thenAnswer(invocacion -> {
                    MovimientoInventario movimiento = invocacion.getArgument(0);
                    ReflectionTestUtils.setField(movimiento, "id", UUID.randomUUID());
                    guardados.add(movimiento);
                    return movimiento;
                });
    }

    @Test
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 7: La existencia sigue la aritmética de los movimientos "
            + "y nunca es negativa")
    void laExistenciaSigueLaAritmeticaYNuncaEsNegativa() {
        List<MovimientoGenerado> secuencia = GeneradoresSga.secuenciaDeMovimientos(120);
        long referencia = 0;

        for (MovimientoGenerado paso : secuencia) {
            int cantidad = paso.cantidad();
            long esperado = paso.tipo().existenciaResultante(referencia, cantidad);
            MovimientoInventarioRequest solicitud = solicitud(
                    paso.tipo(), cantidad, observacionDe(paso.tipo()));
            String contexto = paso.tipo() + " de " + cantidad + " sobre " + referencia;

            if (esperado < 0) {
                int movimientosAntes = guardados.size();

                ConflictoNegocioException conflicto = assertThrows(
                        ConflictoNegocioException.class,
                        () -> servicio.registrar(ARTICULO_ID, solicitud, ACTOR_SUB),
                        contexto);

                assertEquals(ConflictosSga.EXISTENCIA_INSUFICIENTE,
                        conflicto.getConflicto(), contexto);
                assertEquals("cantidad", conflicto.getCampo(), contexto);
                assertEquals(referencia,
                        conflicto.getExtensiones().get("existenciaActual"), contexto);
                assertEquals(cantidad,
                        conflicto.getExtensiones().get("cantidadSolicitada"), contexto);
                assertEquals(referencia, articulo.getExistencia(), contexto);
                assertEquals(movimientosAntes, guardados.size(), contexto);
                continue;
            }

            MovimientoInventarioResponse respuesta = servicio.registrar(
                    ARTICULO_ID, solicitud, ACTOR_SUB);

            assertEquals(paso.tipo(), respuesta.tipo(), contexto);
            assertEquals(cantidad, respuesta.cantidad(), contexto);
            assertEquals(referencia, respuesta.existenciaAnterior(), contexto);
            assertEquals(esperado, respuesta.existenciaResultante(), contexto);
            assertEquals(ACTOR, respuesta.registradoPor(), contexto);
            assertEquals(AHORA, respuesta.registradoEn(), contexto);

            referencia = esperado;
            assertEquals(referencia, articulo.getExistencia(), contexto);
            assertTrue(articulo.getExistencia() >= 0, contexto);
        }

        assertEquals(referencia, articulo.getExistencia());
        assertFalse(guardados.isEmpty());

        for (MovimientoInventario guardado : guardados) {
            assertEquals(articulo, guardado.getArticulo());
            assertEquals(
                    guardado.getExistenciaResultante(),
                    guardado.getTipo().existenciaResultante(
                            guardado.getExistenciaAnterior(), guardado.getCantidad()));
        }

        assertEquals(ANTERIOR, articulo.getActualizadoEn());
        assertEquals(ACTOR, articulo.getActualizadoPor());
    }

    @Test
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 7: La existencia sigue la aritmética de los movimientos "
            + "y nunca es negativa")
    void unAjusteConLaMismaCantidadQueLaExistenciaSeRegistra() {
        servicio.registrar(ARTICULO_ID, solicitud(
                TipoMovimientoInventario.ENTRADA, 5, null), ACTOR_SUB);

        MovimientoInventarioResponse ajuste = servicio.registrar(
                ARTICULO_ID,
                solicitud(TipoMovimientoInventario.AJUSTE, 5, OBSERVACION_AJUSTE),
                ACTOR_SUB);

        assertEquals(5, ajuste.existenciaAnterior());
        assertEquals(5, ajuste.existenciaResultante());
        assertEquals(5, articulo.getExistencia());
        assertEquals(OBSERVACION_AJUSTE, ajuste.observacion());
    }

    @Test
    void registrarDevuelve404SiElArticuloNoExiste() {
        UUID inexistente = UUID.randomUUID();
        when(articulos.findParaModificarById(inexistente)).thenReturn(Optional.empty());

        RecursoNoEncontradoException error = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.registrar(
                        inexistente,
                        solicitud(TipoMovimientoInventario.ENTRADA, 1, null),
                        ACTOR_SUB));

        assertEquals("El artículo no existe.", error.getMessage());
        verify(movimientos, never()).save(any(MovimientoInventario.class));
    }

    private static MovimientoInventarioRequest solicitud(
            TipoMovimientoInventario tipo,
            int cantidad,
            String observacion) {

        return new MovimientoInventarioRequest(
                tipo, BigDecimal.valueOf(cantidad), observacion);
    }

    private static String observacionDe(TipoMovimientoInventario tipo) {
        return tipo == TipoMovimientoInventario.AJUSTE ? OBSERVACION_AJUSTE : null;
    }
}
