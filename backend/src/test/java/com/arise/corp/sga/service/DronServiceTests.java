package com.arise.corp.sga.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.arise.corp.sga.dto.DronRequest;
import com.arise.corp.sga.dto.DronResponse;
import com.arise.corp.sga.entity.Dron;
import com.arise.corp.sga.entity.EstadoDron;
import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.entity.Paquete;
import com.arise.corp.sga.repository.DronRepository;
import com.arise.corp.sga.repository.PaqueteRepository;
import com.arise.corp.shared.error.ConflictoNegocioException;
import com.arise.corp.shared.error.RecursoNoEncontradoException;

/**
 * Pruebas de {@link DronService} (Requirement 25.1, Property 2).
 *
 * <p>Mockito y {@code Clock.fixed}: comprueban la auditoria controlada por el
 * servidor, los duplicados de codigo y la maquina de estados manual con y sin
 * paquete en curso, sin base de datos.
 */
class DronServiceTests {

    private static final UUID ACTOR =
            UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final String ACTOR_SUB = ACTOR.toString();
    private static final UUID DRON_ID =
            UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    private static final UUID OTRO_DRON_ID =
            UUID.fromString("00000000-0000-4000-8000-0000000000b3");
    private static final UUID PAQUETE_ID =
            UUID.fromString("00000000-0000-4000-8000-0000000000b4");
    private static final Instant AHORA =
            Instant.parse("2026-11-25T14:00:00.123456Z");
    private static final Instant ANTERIOR = AHORA.minusSeconds(3600);
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    private DronRepository drones;
    private PaqueteRepository paquetes;
    private DronService servicio;

    @BeforeEach
    void setUp() {
        drones = mock(DronRepository.class);
        paquetes = mock(PaqueteRepository.class);
        servicio = new DronService(drones, paquetes, RELOJ);

        when(drones.save(any(Dron.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        when(paquetes.findFirstByDronIdAndEstadoIn(any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 2: El dron solo cambia a mano entre sus estados manuales "
            + "y sin paquete en curso")
    void crearDejaElDronDisponibleConLaAuditoriaDelActor() {
        DronResponse creado = servicio.crear(solicitud("drn-001"), ACTOR_SUB);

        assertEquals("DRN-001", creado.codigo());
        assertEquals(EstadoDron.DISPONIBLE, creado.estado());
        assertNull(creado.paqueteEnCurso());
        assertEquals(AHORA, creado.creadoEn());
        assertEquals(AHORA, creado.actualizadoEn());
        assertEquals(AHORA, creado.estadoActualizadoEn());
        assertEquals(ACTOR, creado.actualizadoPor());
        assertEquals(ACTOR, creado.estadoActualizadoPor());
        verify(drones).save(any(Dron.class));
    }

    @Test
    void crearRechazaUnCodigoYaRegistrado() {
        when(drones.findByCodigo("DRN-001")).thenReturn(Optional.of(dron(EstadoDron.DISPONIBLE)));

        ConflictoNegocioException conflicto = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.crear(solicitud("DRN-001"), ACTOR_SUB));

        assertEquals(ConflictosSga.DRON_CODIGO_DUPLICADO, conflicto.getConflicto());
        assertEquals("codigo", conflicto.getCampo());
        verify(drones, never()).save(any(Dron.class));
    }

    @Test
    void actualizarConservaElEstadoYSuAuditoria() {
        Dron existente = dron(EstadoDron.MANTENIMIENTO);
        when(drones.findParaModificarById(DRON_ID)).thenReturn(Optional.of(existente));

        DronResponse actualizado = servicio.actualizar(
                DRON_ID, solicitud("DRN-002"), ACTOR_SUB);

        assertEquals("DRN-002", actualizado.codigo());
        assertEquals(EstadoDron.MANTENIMIENTO, actualizado.estado());
        assertEquals(ANTERIOR, actualizado.estadoActualizadoEn());
        assertEquals(AHORA, actualizado.actualizadoEn());
        assertEquals(ACTOR, actualizado.actualizadoPor());
    }

    @Test
    void actualizarRechazaElCodigoDeOtroDron() {
        Dron existente = dron(EstadoDron.DISPONIBLE);
        Dron otro = dron(EstadoDron.DISPONIBLE);
        ReflectionTestUtils.setField(otro, "id", OTRO_DRON_ID);

        when(drones.findParaModificarById(DRON_ID)).thenReturn(Optional.of(existente));
        when(drones.findByCodigo("DRN-002")).thenReturn(Optional.of(otro));

        ConflictoNegocioException conflicto = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.actualizar(DRON_ID, solicitud("DRN-002"), ACTOR_SUB));

        assertEquals(ConflictosSga.DRON_CODIGO_DUPLICADO, conflicto.getConflicto());
        assertEquals("DRN-001", existente.getCodigo());
    }

    @Test
    void cambiarEstadoDevuelve404SiElDronNoExiste() {
        when(drones.findParaModificarById(DRON_ID)).thenReturn(Optional.empty());

        RecursoNoEncontradoException error = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.cambiarEstado(
                        DRON_ID, EstadoDron.MANTENIMIENTO, ACTOR_SUB));

        assertEquals("El dron no existe.", error.getMessage());
    }

    static Stream<Arguments> paresConPaqueteEnCurso() {
        return Stream.of(EstadoDron.values())
                .flatMap(origen -> Stream.of(EstadoDron.values())
                        .flatMap(destino -> Stream.of(false, true)
                                .map(enCurso -> Arguments.of(origen, destino, enCurso))));
    }

    @ParameterizedTest(name = "{0} -> {1}; paquete en curso: {2}")
    @MethodSource("paresConPaqueteEnCurso")
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 2: El dron solo cambia a mano entre sus estados manuales "
            + "y sin paquete en curso")
    void soloCambiaManualmenteEntreSusEstadosManualesYSinPaqueteEnCurso(
            EstadoDron origen,
            EstadoDron destino,
            boolean conPaqueteEnCurso) {

        Dron dron = dron(origen);
        when(drones.findParaModificarById(DRON_ID)).thenReturn(Optional.of(dron));
        when(paquetes.findFirstByDronIdAndEstadoIn(DRON_ID, EstadoPaquete.EN_CURSO))
                .thenReturn(conPaqueteEnCurso
                        ? Optional.of(paqueteEnCurso())
                        : Optional.empty());

        boolean manual = origen.puedeCambiarManualmenteA(destino);
        boolean aceptada = manual && !conPaqueteEnCurso;
        String contexto = origen + " -> " + destino + " con paquete en curso: "
                + conPaqueteEnCurso;

        if (aceptada) {
            DronResponse respuesta = servicio.cambiarEstado(DRON_ID, destino, ACTOR_SUB);

            assertEquals(destino, respuesta.estado(), contexto);
            assertEquals(destino, dron.getEstado(), contexto);
            assertEquals(AHORA, dron.getEstadoActualizadoEn(), contexto);
            assertEquals(ACTOR, dron.getEstadoActualizadoPor(), contexto);
            assertEquals(ANTERIOR, dron.getActualizadoEn(), contexto);
            assertEquals(ACTOR, dron.getActualizadoPor(), contexto);
            return;
        }

        ConflictoNegocioException conflicto = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.cambiarEstado(DRON_ID, destino, ACTOR_SUB),
                contexto);

        if (manual) {
            assertEquals(ConflictosSga.DRON_CON_PAQUETE_EN_CURSO,
                    conflicto.getConflicto(), contexto);
            assertEquals(PAQUETE_ID, conflicto.getExtensiones().get("paqueteId"), contexto);
            assertNull(conflicto.getCampo(), contexto);
        } else {
            assertEquals(ConflictosSga.TRANSICION_DRON_NO_PERMITIDA,
                    conflicto.getConflicto(), contexto);
            assertEquals("estado", conflicto.getCampo(), contexto);
            assertEquals(origen.name(), conflicto.getExtensiones().get("estadoActual"));
            assertEquals(destino.name(), conflicto.getExtensiones().get("estadoSolicitado"));
        }

        assertEquals(origen, dron.getEstado(), contexto);
        assertEquals(ANTERIOR, dron.getEstadoActualizadoEn(), contexto);
        assertEquals(ACTOR, dron.getEstadoActualizadoPor(), contexto);
    }

    private static DronRequest solicitud(String codigo) {
        return new DronRequest(codigo, "Arise X4 Carga ligera", "Observacion ficticia");
    }

    /**
     * Dron ficticio en el estado indicado con la auditoria de datos anterior.
     */
    static Dron dron(EstadoDron estado) {
        Dron dron = Dron.crear(
                "DRN-001", "Arise X4", "Observacion ficticia", ACTOR, ANTERIOR);

        switch (estado) {
            case DISPONIBLE -> {
                // Estado inicial de la fabrica.
            }
            case EN_MISION -> dron.sincronizarConPaquete(EstadoDron.EN_MISION, ACTOR, ANTERIOR);
            case MANTENIMIENTO, FUERA_DE_SERVICIO ->
                    dron.cambiarEstadoManual(estado, ACTOR, ANTERIOR);
        }

        ReflectionTestUtils.setField(dron, "id", DRON_ID);
        return dron;
    }

    static Paquete paqueteEnCurso() {
        Paquete paquete = Paquete.crear(
                "PAQ-0001",
                "Descripcion ficticia",
                "Calle ficticia 123",
                UUID.fromString("00000000-0000-4000-8000-0000000000b5"),
                null,
                ACTOR,
                ANTERIOR);
        ReflectionTestUtils.setField(paquete, "id", PAQUETE_ID);
        return paquete;
    }

    @Test
    void unDronEnMisionNoAdmiteCambioManual() {
        Dron dron = dron(EstadoDron.EN_MISION);
        when(drones.findParaModificarById(DRON_ID)).thenReturn(Optional.of(dron));

        assertTrue(dron.getEstado().transicionesManuales().isEmpty());
        assertThrows(ConflictoNegocioException.class,
                () -> servicio.cambiarEstado(DRON_ID, EstadoDron.DISPONIBLE, ACTOR_SUB));
    }
}
