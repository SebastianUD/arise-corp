package com.arise.corp.sga.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.arise.corp.crm.dto.ClienteReferencia;
import com.arise.corp.crm.dto.ContratoReferencia;
import com.arise.corp.crm.service.ClienteReferenciaService;
import com.arise.corp.crm.service.ContratoReferenciaService;
import com.arise.corp.sga.dto.PaqueteActualizarRequest;
import com.arise.corp.sga.dto.PaqueteCrearRequest;
import com.arise.corp.sga.dto.PaqueteResponse;
import com.arise.corp.sga.dto.RegistroHistorialResponse;
import com.arise.corp.sga.dto.TransicionPaqueteRequest;
import com.arise.corp.sga.entity.Dron;
import com.arise.corp.sga.entity.EstadoDron;
import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.entity.HistorialEstadoPaquete;
import com.arise.corp.sga.entity.Paquete;
import com.arise.corp.sga.repository.DronRepository;
import com.arise.corp.sga.repository.HistorialEstadoPaqueteRepository;
import com.arise.corp.sga.repository.PaqueteRepository;
import com.arise.corp.shared.error.ConflictoNegocioException;
import com.arise.corp.shared.error.RecursoNoEncontradoException;

/**
 * Pruebas de {@link PaqueteService} (Requirement 25.1, Properties 1, 3, 4, 5 y
 * 6).
 *
 * <p>Mockito y {@code Clock.fixed}. El alta y la edicion comprueban el orden de
 * D9 y la asociacion con el CRM con referencias simuladas. Las transiciones se
 * aplican sobre entidades reales con repositorios simulados que guardan en
 * memoria, de modo que las Properties 1, 3, 4 y 5 se recorren completas.
 */
class PaqueteServiceTests {

    private static final UUID ACTOR =
            UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final String ACTOR_SUB = ACTOR.toString();
    private static final UUID CLIENTE_ID =
            UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    private static final UUID OTRO_CLIENTE_ID =
            UUID.fromString("00000000-0000-4000-8000-0000000000c3");
    private static final UUID CONTRATO_ACTUAL_ID =
            UUID.fromString("00000000-0000-4000-8000-0000000000c4");
    private static final UUID CONTRATO_NUEVO_ID =
            UUID.fromString("00000000-0000-4000-8000-0000000000c5");
    private static final UUID CONTRATO_OTRO_CLIENTE_ID =
            UUID.fromString("00000000-0000-4000-8000-0000000000c6");
    private static final UUID DRON_ID =
            UUID.fromString("00000000-0000-4000-8000-0000000000c7");
    private static final UUID DRON_CANDIDATO_ID =
            UUID.fromString("00000000-0000-4000-8000-0000000000c8");
    private static final UUID PAQUETE_EN_CURSO_ID =
            UUID.fromString("00000000-0000-4000-8000-0000000000c9");

    private static final Instant AHORA =
            Instant.parse("2026-11-25T14:05:00.123456Z");
    private static final Instant ANTERIOR = AHORA.minusSeconds(3600);
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final String OBSERVACION_ALTA = "Recibido en bodega";

    private PaqueteRepository paquetes;
    private DronRepository drones;
    private HistorialEstadoPaqueteRepository historial;
    private ClienteReferenciaService clientesCrm;
    private ContratoReferenciaService contratosCrm;
    private PaqueteService servicio;

    private final Map<UUID, Paquete> paquetesMemoria = new HashMap<>();
    private final Map<UUID, Dron> dronesMemoria = new HashMap<>();
    private final List<HistorialEstadoPaquete> historialMemoria = new ArrayList<>();
    private final AtomicInteger secuencia = new AtomicInteger();

    @BeforeEach
    void setUp() {
        paquetes = mock(PaqueteRepository.class);
        drones = mock(DronRepository.class);
        historial = mock(HistorialEstadoPaqueteRepository.class);
        clientesCrm = mock(ClienteReferenciaService.class);
        contratosCrm = mock(ContratoReferenciaService.class);
        servicio = new PaqueteService(
                paquetes, drones, historial, clientesCrm, contratosCrm, RELOJ);

        paquetesMemoria.clear();
        dronesMemoria.clear();
        historialMemoria.clear();
        secuencia.set(0);

        when(paquetes.save(any(Paquete.class))).thenAnswer(invocacion -> {
            Paquete paquete = invocacion.getArgument(0);
            if (paquete.getId() == null) {
                ReflectionTestUtils.setField(paquete, "id", UUID.randomUUID());
            }
            paquetesMemoria.put(paquete.getId(), paquete);
            return paquete;
        });
        when(paquetes.findParaModificarById(any())).thenAnswer(invocacion ->
                Optional.ofNullable(paquetesMemoria.get(invocacion.getArgument(0))));
        when(paquetes.findConDronById(any())).thenAnswer(invocacion ->
                Optional.ofNullable(paquetesMemoria.get(invocacion.getArgument(0))));
        when(paquetes.findByCodigo(any())).thenReturn(Optional.empty());
        when(paquetes.findFirstByDronIdAndEstadoIn(any(), any())).thenAnswer(invocacion -> {
            UUID dronId = invocacion.getArgument(0);
            Collection<EstadoPaquete> estados = invocacion.getArgument(1);

            return paquetesMemoria.values().stream()
                    .filter(paquete -> paquete.getDron() != null
                            && dronId.equals(paquete.getDron().getId()))
                    .filter(paquete -> estados.contains(paquete.getEstado()))
                    .findFirst();
        });
        when(drones.findParaModificarById(any())).thenAnswer(invocacion ->
                Optional.ofNullable(dronesMemoria.get(invocacion.getArgument(0))));
        when(historial.countByPaqueteId(any())).thenAnswer(invocacion ->
                historialMemoria.stream()
                        .filter(registro -> registro.getPaquete().getId()
                                .equals(invocacion.getArgument(0)))
                        .count());
        when(historial.save(any(HistorialEstadoPaquete.class))).thenAnswer(invocacion -> {
            HistorialEstadoPaquete registro = invocacion.getArgument(0);
            ReflectionTestUtils.setField(registro, "id", UUID.randomUUID());
            historialMemoria.add(registro);
            return registro;
        });
        when(clientesCrm.buscar(CLIENTE_ID))
                .thenReturn(Optional.of(cliente(CLIENTE_ID, true)));
        when(contratosCrm.buscar(CONTRATO_ACTUAL_ID))
                .thenReturn(Optional.of(contrato(CONTRATO_ACTUAL_ID, CLIENTE_ID, "BORRADOR")));
    }

    // ---------------------------------------------------------------
    // Alta y edicion (tarea 9.3)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 6: Solo se asocian clientes activos y contratos activos "
            + "del mismo cliente")
    void elAltaCreaElPaqueteRegistradoConSuPrimerRegistro() {
        when(clientesCrm.buscarParaAsociar(CLIENTE_ID))
                .thenReturn(Optional.of(cliente(CLIENTE_ID, true)));
        when(contratosCrm.buscarParaAsociar(CONTRATO_NUEVO_ID))
                .thenReturn(Optional.of(contrato(CONTRATO_NUEVO_ID, CLIENTE_ID, "ACTIVO")));

        PaqueteResponse creado = servicio.crear(
                solicitudCrear(CONTRATO_NUEVO_ID), ACTOR_SUB);

        assertEquals(EstadoPaquete.REGISTRADO, creado.estado());
        assertNull(creado.dron());
        assertEquals(CLIENTE_ID, creado.cliente().id());
        assertEquals(CONTRATO_NUEVO_ID, creado.contrato().id());
        assertEquals(AHORA, creado.creadoEn());
        assertEquals(AHORA, creado.actualizadoEn());
        assertEquals(AHORA, creado.estadoActualizadoEn());
        assertEquals(ACTOR, creado.actualizadoPor());
        assertEquals(ACTOR, creado.estadoActualizadoPor());

        HistorialEstadoPaquete primerRegistro =
                historialMemoria.get(historialMemoria.size() - 1);
        assertEquals((short) 1, primerRegistro.getSecuencia());
        assertNull(primerRegistro.getEstadoAnterior());
        assertEquals(EstadoPaquete.REGISTRADO, primerRegistro.getEstadoNuevo());
        assertNull(primerRegistro.getDron());
        assertEquals(OBSERVACION_ALTA, primerRegistro.getObservacion());
        assertEquals(ACTOR, primerRegistro.getRegistradoPor());
        assertEquals(AHORA, primerRegistro.getRegistradoEn());
    }

    @Test
    void elAltaResuelveElClienteAntesDeLosConflictos() {
        when(clientesCrm.buscarParaAsociar(CLIENTE_ID))
                .thenReturn(Optional.of(cliente(CLIENTE_ID, false)));
        when(paquetes.findByCodigo("PAQ-FICT-0001"))
                .thenReturn(Optional.of(paqueteEn(EstadoPaquete.REGISTRADO, null)));

        ConflictoNegocioException conflicto = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.crear(solicitudCrear(null), ACTOR_SUB));

        assertEquals(ConflictosSga.CLIENTE_INACTIVO, conflicto.getConflicto());
        assertEquals("clienteId", conflicto.getCampo());
        verify(paquetes, never()).save(any(Paquete.class));
        verify(historial, never()).save(any(HistorialEstadoPaquete.class));
    }

    @Test
    void elAltaDevuelve404CuandoElClienteNoExiste() {
        when(clientesCrm.buscarParaAsociar(CLIENTE_ID)).thenReturn(Optional.empty());

        RecursoNoEncontradoException error = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.crear(solicitudCrear(null), ACTOR_SUB));

        assertEquals("clienteId", error.getCampo());
        verify(paquetes, never()).save(any(Paquete.class));
    }

    static Stream<Arguments> combinacionesDeAlta() {
        return Stream.of(CasoCliente.values())
                .flatMap(cliente -> Stream.of(CasoContrato.values())
                        .map(contrato -> Arguments.of(cliente, contrato)));
    }

    @ParameterizedTest(name = "cliente {0}, contrato {1}")
    @MethodSource("combinacionesDeAlta")
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 6: Solo se asocian clientes activos y contratos activos "
            + "del mismo cliente")
    void elAltaSoloAceptaClienteActivoYContratoActivoDelMismoCliente(
            CasoCliente casoCliente,
            CasoContrato casoContrato) {

        UUID contratoId = prepararAlta(casoCliente, casoContrato);

        boolean aceptada = casoCliente == CasoCliente.ACTIVO
                && (casoContrato == CasoContrato.NINGUNO
                        || casoContrato == CasoContrato.MISMO_ACTIVO);

        String contexto = "cliente " + casoCliente + ", contrato " + casoContrato;

        if (aceptada) {
            PaqueteResponse creado = servicio.crear(
                    solicitudCrear(contratoId), ACTOR_SUB);

            assertEquals(EstadoPaquete.REGISTRADO, creado.estado(), contexto);
            assertEquals(contratoId,
                    creado.contrato() == null ? null : creado.contrato().id(),
                    contexto);
            return;
        }

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> servicio.crear(solicitudCrear(contratoId), ACTOR_SUB), contexto);

        if (casoCliente == CasoCliente.INEXISTENTE) {
            assertEquals("clienteId",
                    ((RecursoNoEncontradoException) error).getCampo(), contexto);
        } else if (casoContrato == CasoContrato.INEXISTENTE) {
            assertEquals("contratoId",
                    ((RecursoNoEncontradoException) error).getCampo(), contexto);
        } else if (casoCliente == CasoCliente.INACTIVO) {
            assertEquals(ConflictosSga.CLIENTE_INACTIVO,
                    ((ConflictoNegocioException) error).getConflicto(), contexto);
        } else if (casoContrato == CasoContrato.OTRO_ACTIVO
                || casoContrato == CasoContrato.OTRO_BORRADOR) {
            assertEquals(ConflictosSga.CONTRATO_DE_OTRO_CLIENTE,
                    ((ConflictoNegocioException) error).getConflicto(), contexto);
        } else {
            ConflictoNegocioException conflicto = (ConflictoNegocioException) error;
            assertEquals(ConflictosSga.CONTRATO_NO_ACTIVO,
                    conflicto.getConflicto(), contexto);
            assertEquals("contratoId", conflicto.getCampo(), contexto);
            assertTrue(conflicto.getExtensiones().containsKey("estadoActual"), contexto);
        }

        verify(paquetes, never()).save(any(Paquete.class));
        verify(historial, never()).save(any(HistorialEstadoPaquete.class));
    }

    @Test
    void laEdicionResuelveElContratoAntesDeComprobarSiEsEditable() {
        Paquete paquete = paqueteEn(EstadoPaquete.EN_TRANSITO, CONTRATO_ACTUAL_ID);
        when(contratosCrm.buscarParaAsociar(CONTRATO_NUEVO_ID))
                .thenReturn(Optional.empty());

        RecursoNoEncontradoException error = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.actualizar(
                        paquete.getId(),
                        new PaqueteActualizarRequest("Nueva", "Nueva direccion", CONTRATO_NUEVO_ID),
                        ACTOR_SUB));

        assertEquals("contratoId", error.getCampo());
        assertEquals("Descripcion ficticia", paquete.getDescripcion());
    }

    static Stream<Arguments> combinacionesDeEdicion() {
        return Stream.of(EstadoPaquete.values())
                .flatMap(estado -> Stream.of(CasoEdicion.values())
                        .map(caso -> Arguments.of(estado, caso)));
    }

    @ParameterizedTest(name = "{0} con contrato destino {1}")
    @MethodSource("combinacionesDeEdicion")
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 6: Solo se asocian clientes activos y contratos activos "
            + "del mismo cliente")
    void laEdicionSoloAceptaNingunContratoElActualUOtroActivoDelCliente(
            EstadoPaquete estadoActual,
            CasoEdicion caso) {

        Paquete paquete = paqueteEn(estadoActual, CONTRATO_ACTUAL_ID);
        UUID contratoId = prepararEdicion(caso);

        boolean editable = estadoActual.esEditable();
        boolean aceptada = caso != CasoEdicion.INEXISTENTE
                && editable
                && (caso == CasoEdicion.NINGUNO
                        || caso == CasoEdicion.ACTUAL_BORRADOR
                        || caso == CasoEdicion.OTRO_MISMO_ACTIVO);

        String contexto = estadoActual + " con contrato destino " + caso;

        if (aceptada) {
            PaqueteResponse respuesta = servicio.actualizar(
                    paquete.getId(),
                    new PaqueteActualizarRequest("Nueva descripcion", "Nueva direccion", contratoId),
                    ACTOR_SUB);

            assertEquals("Nueva descripcion", respuesta.descripcion(), contexto);
            assertEquals(contratoId, paquete.getContratoId(), contexto);
            assertEquals(AHORA, paquete.getActualizadoEn(), contexto);
            assertEquals(ACTOR, paquete.getActualizadoPor(), contexto);
            assertEquals(ANTERIOR, paquete.getEstadoActualizadoEn(), contexto);
            return;
        }

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> servicio.actualizar(
                        paquete.getId(),
                        new PaqueteActualizarRequest("Nueva descripcion", "Nueva direccion", contratoId),
                        ACTOR_SUB),
                contexto);

        if (caso == CasoEdicion.INEXISTENTE) {
            assertEquals("contratoId",
                    ((RecursoNoEncontradoException) error).getCampo(), contexto);
        } else if (!editable) {
            assertEquals(ConflictosSga.PAQUETE_NO_EDITABLE,
                    ((ConflictoNegocioException) error).getConflicto(), contexto);
        } else if (caso == CasoEdicion.OTRO_CLIENTE_ACTIVO) {
            assertEquals(ConflictosSga.CONTRATO_DE_OTRO_CLIENTE,
                    ((ConflictoNegocioException) error).getConflicto(), contexto);
        } else {
            assertEquals(ConflictosSga.CONTRATO_NO_ACTIVO,
                    ((ConflictoNegocioException) error).getConflicto(), contexto);
        }

        assertEquals("Descripcion ficticia", paquete.getDescripcion(), contexto);
        assertEquals(CONTRATO_ACTUAL_ID, paquete.getContratoId(), contexto);
    }

    @ParameterizedTest
    @EnumSource(EstadoPaquete.class)
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 1: Solo se aplican las transiciones permitidas del paquete")
    void laEdicionSoloSeAplicaEnLosEstadosEditables(EstadoPaquete estadoActual) {
        Paquete paquete = paqueteEn(estadoActual, null);

        if (estadoActual.esEditable()) {
            PaqueteResponse respuesta = servicio.actualizar(
                    paquete.getId(),
                    new PaqueteActualizarRequest("Nueva", "Nueva direccion", null),
                    ACTOR_SUB);

            assertEquals("Nueva", respuesta.descripcion());
            return;
        }

        ConflictoNegocioException conflicto = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.actualizar(
                        paquete.getId(),
                        new PaqueteActualizarRequest("Nueva", "Nueva direccion", null),
                        ACTOR_SUB));

        assertEquals(ConflictosSga.PAQUETE_NO_EDITABLE, conflicto.getConflicto());
        assertEquals(estadoActual.name(), conflicto.getExtensiones().get("estadoActual"));
        assertEquals("Descripcion ficticia", paquete.getDescripcion());
    }

    // ---------------------------------------------------------------
    // Transiciones (tarea 9.4)
    // ---------------------------------------------------------------

    static Stream<Arguments> paresDeEstado() {
        return Stream.of(EstadoPaquete.values())
                .flatMap(origen -> Stream.of(EstadoPaquete.values())
                        .map(destino -> Arguments.of(origen, destino)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("paresDeEstado")
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 1: Solo se aplican las transiciones permitidas del paquete")
    void soloSeAplicanLasTransicionesPermitidasDelPaquete(
            EstadoPaquete origen,
            EstadoPaquete destino) {

        Paquete paquete = paqueteEn(origen, null);
        Dron candidato = nuevoDron(EstadoDron.DISPONIBLE, DRON_CANDIDATO_ID);
        int registrosAntes = historialMemoria.size();

        Dron asignado = paquete.getDron();
        EstadoDron estadoAsignadoAntes = asignado == null ? null : asignado.getEstado();
        UUID dronId = destino == EstadoPaquete.ASIGNADO ? candidato.getId() : null;

        TransicionPaqueteRequest solicitud = new TransicionPaqueteRequest(
                destino, dronId, "Observacion ficticia");

        boolean permitida = origen.puedeTransicionarA(destino);
        String contexto = origen + " -> " + destino;

        if (permitida) {
            RegistroHistorialResponse registro = servicio.cambiarEstado(
                    paquete.getId(), solicitud, ACTOR_SUB);

            assertEquals(destino, paquete.getEstado(), contexto);
            assertEquals(destino, registro.estadoNuevo(), contexto);
            assertEquals(origen, registro.estadoAnterior(), contexto);
            assertEquals(registrosAntes + 1, historialMemoria.size(), contexto);

            HistorialEstadoPaquete ultimo =
                    historialMemoria.get(historialMemoria.size() - 1);
            assertEquals((short) (registrosAntes + 1), ultimo.getSecuencia(), contexto);
            if (destino == EstadoPaquete.ASIGNADO) {
                assertSame(candidato, paquete.getDron(), contexto);
                assertSame(candidato, ultimo.getDron(), contexto);
            } else {
                assertNull(ultimo.getDron(), contexto);
            }
            assertEquals(ACTOR, ultimo.getRegistradoPor(), contexto);
            assertEquals(AHORA, paquete.getEstadoActualizadoEn(), contexto);
            assertEquals(ACTOR, paquete.getEstadoActualizadoPor(), contexto);
            assertEquals(ANTERIOR, paquete.getActualizadoEn(), contexto);
            assertEquals(ACTOR, paquete.getActualizadoPor(), contexto);
            return;
        }

        ConflictoNegocioException conflicto = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.cambiarEstado(paquete.getId(), solicitud, ACTOR_SUB),
                contexto);

        assertEquals(ConflictosSga.TRANSICION_NO_PERMITIDA,
                conflicto.getConflicto(), contexto);
        assertEquals("estado", conflicto.getCampo(), contexto);
        assertEquals(origen.name(), conflicto.getExtensiones().get("estadoActual"));
        assertEquals(destino.name(), conflicto.getExtensiones().get("estadoSolicitado"));
        assertEquals(origen, paquete.getEstado(), contexto);
        assertEquals(registrosAntes, historialMemoria.size(), contexto);
        if (asignado != null) {
            assertEquals(estadoAsignadoAntes, asignado.getEstado(), contexto);
        }
    }

    static Stream<Arguments> estadosDronYPaqueteEnCurso() {
        return Stream.of(EstadoDron.values())
                .flatMap(estado -> Stream.of(false, true)
                        .map(enCurso -> Arguments.of(estado, enCurso)));
    }

    @ParameterizedTest(name = "dron {0}, con otro paquete en curso: {1}")
    @MethodSource("estadosDronYPaqueteEnCurso")
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 3: Solo se asigna un dron asignable")
    void soloSeAsignaUnDronDisponibleYSinPaqueteEnCurso(
            EstadoDron estadoDron,
            boolean conPaqueteEnCurso) {

        Paquete paquete = paqueteEn(EstadoPaquete.REGISTRADO, null);
        if (conPaqueteEnCurso) {
            paqueteAsignadoA(DRON_CANDIDATO_ID, PAQUETE_EN_CURSO_ID);
        }

        Dron candidato = nuevoDron(estadoDron, DRON_CANDIDATO_ID);
        Paquete otro = conPaqueteEnCurso
                ? paquetesMemoria.get(PAQUETE_EN_CURSO_ID)
                : null;

        int registrosAntes = historialMemoria.size();
        TransicionPaqueteRequest solicitud = new TransicionPaqueteRequest(
                EstadoPaquete.ASIGNADO, candidato.getId(), null);

        boolean aceptada = estadoDron == EstadoDron.DISPONIBLE && !conPaqueteEnCurso;
        String contexto = "dron " + estadoDron + " con paquete en curso: "
                + conPaqueteEnCurso;

        if (aceptada) {
            servicio.cambiarEstado(paquete.getId(), solicitud, ACTOR_SUB);

            assertSame(candidato, paquete.getDron(), contexto);
            assertEquals(registrosAntes + 1, historialMemoria.size(), contexto);
            return;
        }

        ConflictoNegocioException conflicto = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.cambiarEstado(paquete.getId(), solicitud, ACTOR_SUB),
                contexto);

        if (estadoDron != EstadoDron.DISPONIBLE) {
            assertEquals(ConflictosSga.DRON_NO_DISPONIBLE,
                    conflicto.getConflicto(), contexto);
            assertEquals("dronId", conflicto.getCampo(), contexto);
            assertEquals(estadoDron.name(),
                    conflicto.getExtensiones().get("estadoActual"), contexto);
        } else {
            assertEquals(ConflictosSga.DRON_CON_PAQUETE_EN_CURSO,
                    conflicto.getConflicto(), contexto);
            assertEquals("dronId", conflicto.getCampo(), contexto);
            assertEquals(otro.getId(),
                    conflicto.getExtensiones().get("paqueteId"), contexto);
        }

        assertEquals(EstadoPaquete.REGISTRADO, paquete.getEstado(), contexto);
        assertNull(paquete.getDron(), contexto);
        assertEquals(registrosAntes, historialMemoria.size(), contexto);
    }

    static Stream<Arguments> recorridos() {
        return Stream.of(
                Arguments.of("R->C", List.of(EstadoPaquete.CANCELADO)),
                Arguments.of("R->A->C", List.of(
                        EstadoPaquete.ASIGNADO, EstadoPaquete.CANCELADO)),
                Arguments.of("R->A->T->E", List.of(
                        EstadoPaquete.ASIGNADO,
                        EstadoPaquete.EN_TRANSITO,
                        EstadoPaquete.ENTREGADO)),
                Arguments.of("R->A->T->D", List.of(
                        EstadoPaquete.ASIGNADO,
                        EstadoPaquete.EN_TRANSITO,
                        EstadoPaquete.DEVUELTO)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recorridos")
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 4: El estado del dron sigue al de su paquete")
    void elEstadoDelDronSigueAlDeSuPaquete(
            String nombre,
            List<EstadoPaquete> pasos) {

        Paquete paquete = paqueteEn(EstadoPaquete.REGISTRADO, null);
        nuevoDron(EstadoDron.DISPONIBLE, DRON_CANDIDATO_ID);

        for (EstadoPaquete paso : pasos) {
            UUID dronId = paso == EstadoPaquete.ASIGNADO ? DRON_CANDIDATO_ID : null;

            servicio.cambiarEstado(paquete.getId(),
                    new TransicionPaqueteRequest(paso, dronId, null), ACTOR_SUB);

            Dron asignado = paquete.getDron();
            if (asignado != null) {
                EstadoDron esperado = paquete.getEstado() == EstadoPaquete.EN_TRANSITO
                        ? EstadoDron.EN_MISION
                        : EstadoDron.DISPONIBLE;
                assertEquals(esperado, asignado.getEstado(), nombre + " en " + paso);

                if (paso == EstadoPaquete.EN_TRANSITO
                        || paso == EstadoPaquete.ENTREGADO
                        || paso == EstadoPaquete.DEVUELTO) {
                    assertEquals(AHORA, asignado.getEstadoActualizadoEn(), nombre);
                    assertEquals(ACTOR, asignado.getEstadoActualizadoPor(), nombre);
                }
            }

            long enCurso = paquetesMemoria.values().stream()
                    .filter(otro -> otro.getDron() != null)
                    .filter(otro -> otro.getDron().getId().equals(DRON_CANDIDATO_ID))
                    .filter(otro -> EstadoPaquete.EN_CURSO.contains(otro.getEstado()))
                    .count();
            assertTrue(enCurso <= 1, nombre + ": " + enCurso + " paquetes en curso");
        }

        if (paquete.getDron() != null) {
            EstadoDron esperado = paquete.getEstado() == EstadoPaquete.EN_TRANSITO
                    ? EstadoDron.EN_MISION
                    : EstadoDron.DISPONIBLE;
            assertEquals(esperado, paquete.getDron().getEstado(), nombre);
        }

        if (pasos.contains(EstadoPaquete.ASIGNADO)) {
            assertNotNull(paquete.getDron(),
                    nombre + ": un paquete final asignado conserva su dron");
        }

        long enCursoFinal = paquetesMemoria.values().stream()
                .filter(otro -> otro.getDron() != null)
                .filter(otro -> otro.getDron().getId().equals(DRON_CANDIDATO_ID))
                .filter(otro -> EstadoPaquete.EN_CURSO.contains(otro.getEstado()))
                .count();

        if (pasos.contains(EstadoPaquete.CANCELADO)
                || pasos.contains(EstadoPaquete.ENTREGADO)
                || pasos.contains(EstadoPaquete.DEVUELTO)) {
            assertEquals(0, enCursoFinal, nombre + ": el dron debe quedar asignable");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recorridos")
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 5: El historial está encadenado")
    void elHistorialEstaEncadenadoConLasSolicitudesRechazadas(
            String nombre,
            List<EstadoPaquete> pasos) {

        Paquete paquete = paqueteEn(EstadoPaquete.REGISTRADO, null);
        nuevoDron(EstadoDron.DISPONIBLE, DRON_CANDIDATO_ID);
        int aplicadas = 0;

        for (EstadoPaquete paso : pasos) {
            UUID dronId = paso == EstadoPaquete.ASIGNADO ? DRON_CANDIDATO_ID : null;

            servicio.cambiarEstado(paquete.getId(),
                    new TransicionPaqueteRequest(paso, dronId, null), ACTOR_SUB);
            aplicadas++;

            int registrosTrasPaso = historialMemoria.size();

            assertThrows(ConflictoNegocioException.class,
                    () -> servicio.cambiarEstado(paquete.getId(),
                            new TransicionPaqueteRequest(paso, dronId, null), ACTOR_SUB),
                    nombre + ": la misma transicion debe rechazarse");

            assertThrows(RecursoNoEncontradoException.class,
                    () -> servicio.cambiarEstado(paquete.getId(),
                            new TransicionPaqueteRequest(
                                    EstadoPaquete.ASIGNADO, UUID.randomUUID(), null),
                            ACTOR_SUB),
                    nombre + ": un dron inexistente debe responder 404");

            assertEquals(registrosTrasPaso, historialMemoria.size(), nombre);
        }

        assertEquals(1 + aplicadas, historialMemoria.size(), nombre);

        HistorialEstadoPaquete primero = historialMemoria.get(0);
        assertNull(primero.getEstadoAnterior(), nombre);
        assertEquals(EstadoPaquete.REGISTRADO, primero.getEstadoNuevo(), nombre);
        assertEquals(OBSERVACION_ALTA, primero.getObservacion(), nombre);
        assertNull(primero.getDron(), nombre);

        for (int i = 0; i < historialMemoria.size(); i++) {
            HistorialEstadoPaquete registro = historialMemoria.get(i);

            assertEquals((short) (i + 1), registro.getSecuencia(), nombre);
            assertEquals(ACTOR, registro.getRegistradoPor(), nombre);
            assertEquals(i == 0 ? ANTERIOR : AHORA, registro.getRegistradoEn(), nombre);

            if (i > 0) {
                assertEquals(historialMemoria.get(i - 1).getEstadoNuevo(),
                        registro.getEstadoAnterior(), nombre);

                if (registro.getEstadoNuevo() == EstadoPaquete.ASIGNADO) {
                    assertEquals(DRON_CANDIDATO_ID, registro.getDron().getId(), nombre);
                } else {
                    assertNull(registro.getDron(), nombre);
                }
            }
        }

        assertEquals(paquete.getEstado(),
                historialMemoria.get(historialMemoria.size() - 1).getEstadoNuevo(),
                nombre);
    }

    @Test
    void elDronIndicadoSeResuelveAntesDeComprobarLaTransicion() {
        Paquete paquete = paqueteEn(EstadoPaquete.ENTREGADO, null);
        int registrosAntes = historialMemoria.size();

        UUID dronInexistente = UUID.randomUUID();

        RecursoNoEncontradoException error = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.cambiarEstado(
                        paquete.getId(),
                        new TransicionPaqueteRequest(
                                EstadoPaquete.ASIGNADO, dronInexistente, null),
                        ACTOR_SUB));

        assertEquals("dronId", error.getCampo());
        assertEquals(EstadoPaquete.ENTREGADO, paquete.getEstado());
        assertEquals(registrosAntes, historialMemoria.size());
    }

    // ---------------------------------------------------------------
    // Datos de apoyo
    // ---------------------------------------------------------------

    private UUID prepararAlta(CasoCliente casoCliente, CasoContrato casoContrato) {
        switch (casoCliente) {
            case INEXISTENTE -> when(clientesCrm.buscarParaAsociar(CLIENTE_ID))
                    .thenReturn(Optional.empty());
            case INACTIVO -> when(clientesCrm.buscarParaAsociar(CLIENTE_ID))
                    .thenReturn(Optional.of(cliente(CLIENTE_ID, false)));
            case ACTIVO -> when(clientesCrm.buscarParaAsociar(CLIENTE_ID))
                    .thenReturn(Optional.of(cliente(CLIENTE_ID, true)));
        }

        return switch (casoContrato) {
            case NINGUNO -> null;
            case INEXISTENTE -> {
                UUID id = CONTRATO_NUEVO_ID;
                when(contratosCrm.buscarParaAsociar(id)).thenReturn(Optional.empty());
                yield id;
            }
            case MISMO_ACTIVO -> registrarContratoParaAsociar(
                    CONTRATO_NUEVO_ID, CLIENTE_ID, "ACTIVO");
            case MISMO_BORRADOR -> registrarContratoParaAsociar(
                    CONTRATO_NUEVO_ID, CLIENTE_ID, "BORRADOR");
            case MISMO_FINALIZADO -> registrarContratoParaAsociar(
                    CONTRATO_NUEVO_ID, CLIENTE_ID, "FINALIZADO");
            case MISMO_CANCELADO -> registrarContratoParaAsociar(
                    CONTRATO_NUEVO_ID, CLIENTE_ID, "CANCELADO");
            case OTRO_ACTIVO -> registrarContratoParaAsociar(
                    CONTRATO_OTRO_CLIENTE_ID, OTRO_CLIENTE_ID, "ACTIVO");
            case OTRO_BORRADOR -> registrarContratoParaAsociar(
                    CONTRATO_OTRO_CLIENTE_ID, OTRO_CLIENTE_ID, "BORRADOR");
        };
    }

    private UUID registrarContratoParaAsociar(
            UUID contratoId,
            UUID clienteId,
            String estado) {

        when(contratosCrm.buscarParaAsociar(contratoId))
                .thenReturn(Optional.of(contrato(contratoId, clienteId, estado)));
        return contratoId;
    }

    private UUID prepararEdicion(CasoEdicion caso) {
        return switch (caso) {
            case NINGUNO -> null;
            case ACTUAL_BORRADOR -> CONTRATO_ACTUAL_ID;
            case OTRO_MISMO_ACTIVO -> registrarContratoParaAsociar(
                    CONTRATO_NUEVO_ID, CLIENTE_ID, "ACTIVO");
            case OTRO_MISMO_BORRADOR -> registrarContratoParaAsociar(
                    CONTRATO_NUEVO_ID, CLIENTE_ID, "BORRADOR");
            case OTRO_CLIENTE_ACTIVO -> registrarContratoParaAsociar(
                    CONTRATO_OTRO_CLIENTE_ID, OTRO_CLIENTE_ID, "ACTIVO");
            case INEXISTENTE -> {
                when(contratosCrm.buscarParaAsociar(CONTRATO_NUEVO_ID))
                        .thenReturn(Optional.empty());
                yield CONTRATO_NUEVO_ID;
            }
        };
    }

    private static PaqueteCrearRequest solicitudCrear(UUID contratoId) {
        return new PaqueteCrearRequest(
                "PAQ-FICT-0001",
                "Descripcion ficticia",
                "Calle ficticia 123",
                CLIENTE_ID,
                contratoId,
                OBSERVACION_ALTA);
    }

    private static ClienteReferencia cliente(UUID id, boolean activo) {
        return new ClienteReferencia(id, "Cliente ficticio", activo);
    }

    private static ContratoReferencia contrato(UUID id, UUID clienteId, String estado) {
        return new ContratoReferencia(id, clienteId, "CTR-FICT-" + id, estado);
    }

    private Dron nuevoDron(EstadoDron estado, UUID id) {
        Dron dron = Dron.crear(
                "DRN-FICT-" + id, "Dron ficticio", "Observacion ficticia", ACTOR, ANTERIOR);

        switch (estado) {
            case DISPONIBLE -> {
                // Estado inicial de la fabrica.
            }
            case EN_MISION -> dron.sincronizarConPaquete(EstadoDron.EN_MISION, ACTOR, ANTERIOR);
            case MANTENIMIENTO, FUERA_DE_SERVICIO ->
                    dron.cambiarEstadoManual(estado, ACTOR, ANTERIOR);
        }

        ReflectionTestUtils.setField(dron, "id", id);
        dronesMemoria.put(id, dron);
        return dron;
    }

    /**
     * Paquete ficticio en el estado indicado, con su historial encadenado y
     * registrado en la memoria de los repositorios simulados.
     */
    private Paquete paqueteEn(EstadoPaquete estado, UUID contratoId) {
        Paquete paquete = Paquete.crear(
                "PAQ-FICT-" + secuencia.incrementAndGet(),
                "Descripcion ficticia",
                "Calle ficticia 123",
                CLIENTE_ID,
                contratoId,
                ACTOR,
                ANTERIOR);
        ReflectionTestUtils.setField(paquete, "id", UUID.randomUUID());
        paquetesMemoria.put(paquete.getId(), paquete);
        registrarAlta(paquete);

        if (estado == EstadoPaquete.REGISTRADO) {
            return paquete;
        }

        if (estado == EstadoPaquete.CANCELADO) {
            paquete.aplicarTransicion(EstadoPaquete.CANCELADO, null, ACTOR, ANTERIOR);
            registrarTransicion(paquete, EstadoPaquete.REGISTRADO,
                    EstadoPaquete.CANCELADO, null);
            return paquete;
        }

        Dron dron = nuevoDron(EstadoDron.DISPONIBLE, DRON_ID);
        paquete.aplicarTransicion(EstadoPaquete.ASIGNADO, dron, ACTOR, ANTERIOR);
        registrarTransicion(paquete, EstadoPaquete.REGISTRADO,
                EstadoPaquete.ASIGNADO, dron);

        if (estado == EstadoPaquete.ASIGNADO) {
            return paquete;
        }

        paquete.aplicarTransicion(EstadoPaquete.EN_TRANSITO, dron, ACTOR, ANTERIOR);
        registrarTransicion(paquete, EstadoPaquete.ASIGNADO,
                EstadoPaquete.EN_TRANSITO, null);

        if (estado == EstadoPaquete.EN_TRANSITO) {
            return paquete;
        }

        paquete.aplicarTransicion(estado, dron, ACTOR, ANTERIOR);
        registrarTransicion(paquete, EstadoPaquete.EN_TRANSITO, estado, null);

        return paquete;
    }

    /**
     * Paquete en {@code ASIGNADO} a un dron con identificador concreto, sin
     * tocar el estado del dron que se devuelve en las busquedas.
     */
    private Paquete paqueteAsignadoA(UUID dronId, UUID paqueteId) {
        Dron dronAsignado = Dron.crear(
                "DRN-ASIGNADO", "Dron ficticio", null, ACTOR, ANTERIOR);
        ReflectionTestUtils.setField(dronAsignado, "id", dronId);

        Paquete paquete = Paquete.crear(
                "PAQ-FICT-" + secuencia.incrementAndGet(),
                "Descripcion ficticia",
                "Calle ficticia 123",
                CLIENTE_ID,
                null,
                ACTOR,
                ANTERIOR);
        ReflectionTestUtils.setField(paquete, "id", paqueteId);
        paquete.aplicarTransicion(EstadoPaquete.ASIGNADO, dronAsignado, ACTOR, ANTERIOR);
        paquetesMemoria.put(paqueteId, paquete);
        return paquete;
    }

    private void registrarAlta(Paquete paquete) {
        HistorialEstadoPaquete alta = HistorialEstadoPaquete.alta(
                paquete, OBSERVACION_ALTA, ACTOR, ANTERIOR);
        ReflectionTestUtils.setField(alta, "id", UUID.randomUUID());
        historialMemoria.add(alta);
    }

    private void registrarTransicion(
            Paquete paquete,
            EstadoPaquete anterior,
            EstadoPaquete nuevo,
            Dron dron) {

        HistorialEstadoPaquete registro = HistorialEstadoPaquete.transicion(
                paquete,
                historialMemoria.size() + 1,
                anterior,
                nuevo,
                dron,
                null,
                ACTOR,
                ANTERIOR);
        ReflectionTestUtils.setField(registro, "id", UUID.randomUUID());
        historialMemoria.add(registro);
    }

    enum CasoCliente {
        INEXISTENTE, INACTIVO, ACTIVO
    }

    enum CasoContrato {
        NINGUNO, INEXISTENTE,
        MISMO_ACTIVO, MISMO_BORRADOR, MISMO_FINALIZADO, MISMO_CANCELADO,
        OTRO_ACTIVO, OTRO_BORRADOR
    }

    enum CasoEdicion {
        NINGUNO, ACTUAL_BORRADOR, OTRO_MISMO_ACTIVO, OTRO_MISMO_BORRADOR,
        OTRO_CLIENTE_ACTIVO, INEXISTENTE
    }
}
