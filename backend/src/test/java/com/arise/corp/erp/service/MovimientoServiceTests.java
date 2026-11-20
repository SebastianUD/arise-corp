package com.arise.corp.erp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.arise.corp.crm.dto.ClienteReferencia;
import com.arise.corp.crm.service.ClienteReferenciaService;
import com.arise.corp.erp.dto.MovimientoRequest;
import com.arise.corp.erp.dto.MovimientoResponse;
import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.MovimientoFinanciero;
import com.arise.corp.erp.entity.Proveedor;
import com.arise.corp.erp.entity.TipoDocumentoProveedor;
import com.arise.corp.erp.entity.TipoMovimiento;
import com.arise.corp.erp.repository.MovimientoFinancieroRepository;
import com.arise.corp.erp.repository.ProveedorRepository;
import com.arise.corp.shared.error.ConflictoNegocioException;
import com.arise.corp.shared.error.RecursoNoEncontradoException;
import com.arise.corp.shared.paginacion.PaginaResponse;

/**
 * Pruebas del servicio de movimientos financieros (ERP-03, ERP-05).
 *
 * <p>Mockito y {@link Clock#fixed} permiten comprobar la auditoria, el orden
 * de los errores y las llamadas al CRM sin base de datos. Las Properties 10,
 * 12 y 13 se prueban de forma exhaustiva.
 */
class MovimientoServiceTests {

    private static final UUID MOVIMIENTO_ID =
            UUID.fromString("9d4f0a51-0000-4000-8000-000000000001");
    private static final UUID CLIENTE_ID =
            UUID.fromString("9d4f0a51-0000-4000-8000-000000000002");
    private static final UUID OTRO_CLIENTE_ID =
            UUID.fromString("9d4f0a51-0000-4000-8000-000000000003");
    private static final UUID PROVEEDOR_ID =
            UUID.fromString("9d4f0a51-0000-4000-8000-000000000004");
    private static final UUID OTRO_PROVEEDOR_ID =
            UUID.fromString("9d4f0a51-0000-4000-8000-000000000005");
    private static final UUID ACTOR =
            UUID.fromString("9d4f0a51-0000-4000-8000-000000000006");

    private static final Instant ANTES = Instant.parse("2026-01-02T03:04:05.000001Z");
    private static final Instant AHORA = Instant.parse("2026-11-11T15:30:45.123456Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 10);
    private static final BigDecimal VALOR = new BigDecimal("1250000.50");
    private static final String CONCEPTO = "Movimiento ficticio";
    private static final String REFERENCIA = "FAC-2026-001";

    private MovimientoFinancieroRepository movimientos;
    private ProveedorRepository proveedores;
    private ClienteReferenciaService clientesCrm;
    private MovimientoService servicio;

    @BeforeEach
    void setUp() {
        movimientos = mock(MovimientoFinancieroRepository.class);
        proveedores = mock(ProveedorRepository.class);
        clientesCrm = mock(ClienteReferenciaService.class);
        servicio = new MovimientoService(
                movimientos, proveedores, clientesCrm, RELOJ);

        when(movimientos.save(any(MovimientoFinanciero.class)))
                .thenAnswer(invocacion -> {
                    MovimientoFinanciero movimiento = invocacion.getArgument(0);
                    ReflectionTestUtils.setField(
                            movimiento, "id", MOVIMIENTO_ID);
                    return movimiento;
                });
        when(movimientos.findConProveedorById(any()))
                .thenReturn(Optional.empty());
        when(movimientos.findParaModificarById(any()))
                .thenReturn(Optional.empty());
        when(clientesCrm.buscar(any())).thenReturn(Optional.empty());
        when(clientesCrm.buscarParaAsociar(any())).thenReturn(Optional.empty());
        when(clientesCrm.buscarTodos(any())).thenReturn(Map.of());
        when(proveedores.findParaAsociarById(any())).thenReturn(Optional.empty());
    }

    @Test
    void creaUnIngresoPendienteConAuditoriasIgualesYElActor() {
        when(clientesCrm.buscarParaAsociar(CLIENTE_ID))
                .thenReturn(Optional.of(cliente(CLIENTE_ID, true)));

        MovimientoResponse respuesta = servicio.crear(
                solicitud(TipoMovimiento.INGRESO, CLIENTE_ID, null),
                ACTOR.toString());

        assertEquals(MOVIMIENTO_ID, respuesta.id());
        assertEquals(TipoMovimiento.INGRESO, respuesta.tipo());
        assertEquals(EstadoMovimiento.PENDIENTE, respuesta.estado());
        assertEquals("1250000.50", respuesta.valor());
        assertEquals(CLIENTE_ID, respuesta.cliente().id());
        assertTrue(respuesta.cliente().activo());
        assertNull(respuesta.proveedor());
        assertEquals(AHORA, respuesta.estadoActualizadoEn());
        assertEquals(ACTOR, respuesta.estadoActualizadoPor());
        assertEquals(AHORA, respuesta.creadoEn());
        assertEquals(AHORA, respuesta.actualizadoEn());
        assertEquals(ACTOR, respuesta.actualizadoPor());
        verify(clientesCrm).buscarParaAsociar(CLIENTE_ID);
        verify(proveedores, never()).findParaAsociarById(any());
        verify(movimientos).save(any(MovimientoFinanciero.class));
    }

    @Test
    void creaUnEgresoConProveedorActivo() {
        when(proveedores.findParaAsociarById(PROVEEDOR_ID))
                .thenReturn(Optional.of(proveedor(PROVEEDOR_ID, true)));

        MovimientoResponse respuesta = servicio.crear(
                solicitud(TipoMovimiento.EGRESO, null, PROVEEDOR_ID),
                ACTOR.toString());

        assertNull(respuesta.cliente());
        assertEquals(PROVEEDOR_ID, respuesta.proveedor().id());
        assertTrue(respuesta.proveedor().activo());
        verify(proveedores).findParaAsociarById(PROVEEDOR_ID);
        verify(clientesCrm, never()).buscarParaAsociar(any());
    }

    @Test
    void crearRechazaUnClienteInexistenteConElCampo() {
        RecursoNoEncontradoException error = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.crear(
                        solicitud(TipoMovimiento.INGRESO, CLIENTE_ID, null),
                        ACTOR.toString()));

        assertEquals("El cliente indicado no existe.", error.getMessage());
        assertEquals("clienteId", error.getCampo());
        verify(movimientos, never()).save(any(MovimientoFinanciero.class));
    }

    @Test
    void crearRechazaUnClienteInactivo() {
        when(clientesCrm.buscarParaAsociar(CLIENTE_ID))
                .thenReturn(Optional.of(cliente(CLIENTE_ID, false)));

        ConflictoNegocioException error = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.crear(
                        solicitud(TipoMovimiento.INGRESO, CLIENTE_ID, null),
                        ACTOR.toString()));

        assertEquals("CLIENTE_INACTIVO", error.getConflicto());
        assertEquals("clienteId", error.getCampo());
        verify(movimientos, never()).save(any(MovimientoFinanciero.class));
    }

    @Test
    void crearRechazaUnProveedorInexistenteConElCampo() {
        RecursoNoEncontradoException error = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.crear(
                        solicitud(TipoMovimiento.EGRESO, null, PROVEEDOR_ID),
                        ACTOR.toString()));

        assertEquals("El proveedor indicado no existe.", error.getMessage());
        assertEquals("proveedorId", error.getCampo());
        verify(movimientos, never()).save(any(MovimientoFinanciero.class));
    }

    @Test
    void crearRechazaUnProveedorInactivo() {
        when(proveedores.findParaAsociarById(PROVEEDOR_ID))
                .thenReturn(Optional.of(proveedor(PROVEEDOR_ID, false)));

        ConflictoNegocioException error = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.crear(
                        solicitud(TipoMovimiento.EGRESO, null, PROVEEDOR_ID),
                        ACTOR.toString()));

        assertEquals("PROVEEDOR_INACTIVO", error.getConflicto());
        assertEquals("proveedorId", error.getCampo());
        verify(movimientos, never()).save(any(MovimientoFinanciero.class));
    }

    @Test
    void listarResuelveLosClientesDeLaPaginaConUnaSolaConsulta() {
        MovimientoFinanciero primero = movimientoPendiente(
                TipoMovimiento.INGRESO, CLIENTE_ID, null, MOVIMIENTO_ID);
        MovimientoFinanciero segundo = movimientoPendiente(
                TipoMovimiento.INGRESO, OTRO_CLIENTE_ID, null, OTRO_CLIENTE_ID);
        MovimientoFinanciero sinCliente = movimientoPendiente(
                TipoMovimiento.INGRESO, null, null, PROVEEDOR_ID);

        when(movimientos.findAll(
                ArgumentMatchers.<Specification<MovimientoFinanciero>>any(),
                ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(primero, segundo, sinCliente),
                        PageRequest.of(0, 20), 3));
        when(clientesCrm.buscarTodos(any())).thenReturn(Map.of(
                CLIENTE_ID, cliente(CLIENTE_ID, true),
                OTRO_CLIENTE_ID, cliente(OTRO_CLIENTE_ID, false)));

        PaginaResponse<MovimientoResponse> pagina = servicio.listar(
                filtro(), PageRequest.of(0, 20));

        assertEquals(3, pagina.content().size());
        assertEquals("Cliente " + CLIENTE_ID,
                pagina.content().get(0).cliente().nombre());
        assertEquals("Cliente " + OTRO_CLIENTE_ID,
                pagina.content().get(1).cliente().nombre());
        assertNull(pagina.content().get(2).cliente());
        assertEquals(3, pagina.totalElements());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> captor =
                ArgumentCaptor.forClass(Collection.class);

        verify(clientesCrm, times(1)).buscarTodos(captor.capture());
        assertEquals(Set.of(CLIENTE_ID, OTRO_CLIENTE_ID),
                Set.copyOf(captor.getValue()));
    }

    @Test
    void listarSinMovimientosConsultaUnaVezConLaListaVacia() {
        when(movimientos.findAll(
                ArgumentMatchers.<Specification<MovimientoFinanciero>>any(),
                ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(), PageRequest.of(0, 20), 0));

        PaginaResponse<MovimientoResponse> pagina = servicio.listar(
                filtro(), PageRequest.of(0, 20));

        assertTrue(pagina.content().isEmpty());
        verify(clientesCrm).buscarTodos(Set.of());
    }

    @Test
    void obtenerDevuelve404SiElMovimientoNoExiste() {
        RecursoNoEncontradoException error = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.obtener(MOVIMIENTO_ID));

        assertEquals("El movimiento financiero no existe.", error.getMessage());
    }

    @Test
    void obtenerResuelveElNombreDelCliente() {
        MovimientoFinanciero movimiento = movimientoPendiente(
                TipoMovimiento.INGRESO, CLIENTE_ID, null, MOVIMIENTO_ID);
        when(movimientos.findConProveedorById(MOVIMIENTO_ID))
                .thenReturn(Optional.of(movimiento));
        when(clientesCrm.buscar(CLIENTE_ID))
                .thenReturn(Optional.of(cliente(CLIENTE_ID, true)));

        MovimientoResponse respuesta = servicio.obtener(MOVIMIENTO_ID);

        assertEquals(CLIENTE_ID, respuesta.cliente().id());
        assertEquals("1250000.50", respuesta.valor());
    }

    @Test
    void actualizarReemplazaLosDatosYConservaElEstadoYSuAuditoria() {
        MovimientoFinanciero movimiento = movimientoPendiente(
                TipoMovimiento.INGRESO, CLIENTE_ID, null, MOVIMIENTO_ID);
        when(movimientos.findParaModificarById(MOVIMIENTO_ID))
                .thenReturn(Optional.of(movimiento));
        when(clientesCrm.buscar(CLIENTE_ID))
                .thenReturn(Optional.of(cliente(CLIENTE_ID, false)));

        MovimientoRequest solicitud = new MovimientoRequest(
                TipoMovimiento.INGRESO,
                FECHA.plusDays(2),
                "Concepto nuevo",
                new BigDecimal("99.99"),
                "FAC-2026-002",
                CLIENTE_ID,
                null);

        MovimientoResponse respuesta = servicio.actualizar(
                MOVIMIENTO_ID, solicitud, ACTOR.toString());

        assertEquals("Concepto nuevo", respuesta.concepto());
        assertEquals("99.99", respuesta.valor());
        assertEquals("FAC-2026-002", respuesta.referencia());
        assertEquals(EstadoMovimiento.PENDIENTE, respuesta.estado());
        assertEquals(AHORA, respuesta.actualizadoEn());
        assertEquals(ACTOR, respuesta.actualizadoPor());
        assertEquals(ANTES, respuesta.estadoActualizadoEn());
        assertEquals(ACTOR, respuesta.estadoActualizadoPor());
        assertFalse(respuesta.cliente().activo());
    }

    @Test
    void actualizarConRelacionNulaEliminaLaAsociacion() {
        MovimientoFinanciero movimiento = movimientoPendiente(
                TipoMovimiento.INGRESO, CLIENTE_ID, null, MOVIMIENTO_ID);
        when(movimientos.findParaModificarById(MOVIMIENTO_ID))
                .thenReturn(Optional.of(movimiento));

        MovimientoResponse respuesta = servicio.actualizar(
                MOVIMIENTO_ID,
                solicitud(TipoMovimiento.INGRESO, null, null),
                ACTOR.toString());

        assertNull(respuesta.cliente());
        assertNull(movimiento.getClienteId());
        verify(clientesCrm, never()).buscarParaAsociar(any());
        verify(clientesCrm, never()).buscar(any());
    }

    @Test
    void actualizarConvierteUnIngresoEnEgresoConProveedor() {
        MovimientoFinanciero movimiento = movimientoPendiente(
                TipoMovimiento.INGRESO, CLIENTE_ID, null, MOVIMIENTO_ID);
        when(movimientos.findParaModificarById(MOVIMIENTO_ID))
                .thenReturn(Optional.of(movimiento));
        when(proveedores.findParaAsociarById(PROVEEDOR_ID))
                .thenReturn(Optional.of(proveedor(PROVEEDOR_ID, true)));

        MovimientoResponse respuesta = servicio.actualizar(
                MOVIMIENTO_ID,
                solicitud(TipoMovimiento.EGRESO, null, PROVEEDOR_ID),
                ACTOR.toString());

        assertEquals(TipoMovimiento.EGRESO, respuesta.tipo());
        assertNull(respuesta.cliente());
        assertEquals(PROVEEDOR_ID, respuesta.proveedor().id());
        assertNull(movimiento.getClienteId());
    }

    @Test
    void actualizarDevuelve404DelMovimientoAntesDeConsultarLaReferencia() {
        RecursoNoEncontradoException error = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.actualizar(
                        MOVIMIENTO_ID,
                        solicitud(TipoMovimiento.INGRESO, OTRO_CLIENTE_ID, null),
                        ACTOR.toString()));

        assertEquals("El movimiento financiero no existe.", error.getMessage());
        verify(clientesCrm, never()).buscarParaAsociar(any());
        verify(clientesCrm, never()).buscar(any());
    }

    @Test
    void actualizarDevuelve404DeLaReferenciaAntesDelConflictoDeEdicion() {
        MovimientoFinanciero movimiento = movimientoEnEstado(
                EstadoMovimiento.PAGADO,
                TipoMovimiento.INGRESO,
                CLIENTE_ID,
                null);
        when(movimientos.findParaModificarById(MOVIMIENTO_ID))
                .thenReturn(Optional.of(movimiento));

        RecursoNoEncontradoException error = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.actualizar(
                        MOVIMIENTO_ID,
                        solicitud(TipoMovimiento.INGRESO, OTRO_CLIENTE_ID, null),
                        ACTOR.toString()));

        assertEquals("El cliente indicado no existe.", error.getMessage());
        assertEquals("clienteId", error.getCampo());
        assertEquals(EstadoMovimiento.PAGADO, movimiento.getEstado());
    }

    @Test
    void actualizarDevuelveNoEditableAntesDeLaInactividadDeLaReferencia() {
        MovimientoFinanciero movimiento = movimientoEnEstado(
                EstadoMovimiento.PAGADO,
                TipoMovimiento.INGRESO,
                null,
                null);
        when(movimientos.findParaModificarById(MOVIMIENTO_ID))
                .thenReturn(Optional.of(movimiento));
        when(clientesCrm.buscarParaAsociar(OTRO_CLIENTE_ID))
                .thenReturn(Optional.of(cliente(OTRO_CLIENTE_ID, false)));

        ConflictoNegocioException error = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.actualizar(
                        MOVIMIENTO_ID,
                        solicitud(TipoMovimiento.INGRESO, OTRO_CLIENTE_ID, null),
                        ACTOR.toString()));

        assertEquals("MOVIMIENTO_NO_EDITABLE", error.getConflicto());
        assertEquals(Map.of("estadoActual", "PAGADO"), error.getExtensiones());
        assertEquals(ANTES, movimiento.getActualizadoEn());
    }

    @Test
    void actualizarRechazaUnClienteNuevoInactivo() {
        MovimientoFinanciero movimiento = movimientoPendiente(
                TipoMovimiento.INGRESO, null, null, MOVIMIENTO_ID);
        when(movimientos.findParaModificarById(MOVIMIENTO_ID))
                .thenReturn(Optional.of(movimiento));
        when(clientesCrm.buscarParaAsociar(OTRO_CLIENTE_ID))
                .thenReturn(Optional.of(cliente(OTRO_CLIENTE_ID, false)));

        ConflictoNegocioException error = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.actualizar(
                        MOVIMIENTO_ID,
                        solicitud(TipoMovimiento.INGRESO, OTRO_CLIENTE_ID, null),
                        ACTOR.toString()));

        assertEquals("CLIENTE_INACTIVO", error.getConflicto());
        assertNull(movimiento.getClienteId());
    }

    @Test
    void actualizarRechazaUnProveedorNuevoInactivo() {
        MovimientoFinanciero movimiento = movimientoPendiente(
                TipoMovimiento.EGRESO, null, null, MOVIMIENTO_ID);
        when(movimientos.findParaModificarById(MOVIMIENTO_ID))
                .thenReturn(Optional.of(movimiento));
        when(proveedores.findParaAsociarById(OTRO_PROVEEDOR_ID))
                .thenReturn(Optional.of(proveedor(OTRO_PROVEEDOR_ID, false)));

        ConflictoNegocioException error = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.actualizar(
                        MOVIMIENTO_ID,
                        solicitud(TipoMovimiento.EGRESO, null, OTRO_PROVEEDOR_ID),
                        ACTOR.toString()));

        assertEquals("PROVEEDOR_INACTIVO", error.getConflicto());
        assertNull(movimiento.getProveedor());
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(EstadoMovimiento.class)
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 13: Solo se editan movimientos pendientes")
    void soloSeEditanMovimientosPendientes(EstadoMovimiento estado) {
        MovimientoFinanciero movimiento = movimientoEnEstado(
                estado, TipoMovimiento.INGRESO, null, null);
        when(movimientos.findParaModificarById(MOVIMIENTO_ID))
                .thenReturn(Optional.of(movimiento));

        MovimientoRequest solicitud = new MovimientoRequest(
                TipoMovimiento.INGRESO,
                FECHA.plusDays(1),
                "Concepto editado",
                new BigDecimal("10.00"),
                null,
                null,
                null);

        if (estado.esEditable()) {
            MovimientoResponse respuesta = servicio.actualizar(
                    MOVIMIENTO_ID, solicitud, ACTOR.toString());

            assertEquals("Concepto editado", respuesta.concepto());
            assertEquals(estado, respuesta.estado());
            assertEquals(AHORA, respuesta.actualizadoEn());
            return;
        }

        ConflictoNegocioException error = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.actualizar(
                        MOVIMIENTO_ID, solicitud, ACTOR.toString()));

        assertEquals("MOVIMIENTO_NO_EDITABLE", error.getConflicto());
        assertEquals(Map.of("estadoActual", estado.name()),
                error.getExtensiones());
        assertEquals(CONCEPTO, movimiento.getConcepto());
        assertEquals(ANTES, movimiento.getActualizadoEn());
    }

    @ParameterizedTest(name = "cliente actual {0}, destino {1}")
    @MethodSource("combinacionesCliente")
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 10: Solo se asocian registros activos o la asociación actual")
    void soloSeAsociaUnClienteActivoOLaAsociacionActual(
            RelacionActual actual,
            DestinoRelacion destino,
            Resultado resultado) {

        MovimientoFinanciero movimiento = movimientoPendiente(
                TipoMovimiento.INGRESO,
                actual == RelacionActual.SIN_RELACION ? null : CLIENTE_ID,
                null,
                MOVIMIENTO_ID);
        when(movimientos.findParaModificarById(MOVIMIENTO_ID))
                .thenReturn(Optional.of(movimiento));

        prepararCliente(actual, destino);

        UUID destinoId = destinoIdCliente(destino);
        MovimientoRequest solicitud = solicitud(
                TipoMovimiento.INGRESO, destinoId, null);

        if (resultado == Resultado.OK) {
            MovimientoResponse respuesta = servicio.actualizar(
                    MOVIMIENTO_ID, solicitud, ACTOR.toString());

            assertEquals(destinoId,
                    respuesta.cliente() == null ? null : respuesta.cliente().id());
            return;
        }

        if (resultado == Resultado.CONFLICTO) {
            ConflictoNegocioException error = assertThrows(
                    ConflictoNegocioException.class,
                    () -> servicio.actualizar(
                            MOVIMIENTO_ID, solicitud, ACTOR.toString()));
            assertEquals("CLIENTE_INACTIVO", error.getConflicto());
            assertEquals(actual == RelacionActual.SIN_RELACION
                    ? null : CLIENTE_ID, movimiento.getClienteId());
            return;
        }

        RecursoNoEncontradoException error = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.actualizar(
                        MOVIMIENTO_ID, solicitud, ACTOR.toString()));
        assertEquals("clienteId", error.getCampo());
    }

    @ParameterizedTest(name = "proveedor actual {0}, destino {1}")
    @MethodSource("combinacionesProveedor")
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 10: Solo se asocian registros activos o la asociación actual")
    void soloSeAsociaUnProveedorActivoOLaAsociacionActual(
            RelacionActual actual,
            DestinoRelacion destino,
            Resultado resultado) {

        Proveedor proveedorActual = actual == RelacionActual.SIN_RELACION
                ? null
                : proveedor(PROVEEDOR_ID, actual == RelacionActual.ACTIVO);
        MovimientoFinanciero movimiento = movimientoPendiente(
                TipoMovimiento.EGRESO, null, proveedorActual, MOVIMIENTO_ID);
        when(movimientos.findParaModificarById(MOVIMIENTO_ID))
                .thenReturn(Optional.of(movimiento));

        prepararProveedor(actual, destino);

        UUID destinoId = destinoIdProveedor(destino);
        MovimientoRequest solicitud = solicitud(
                TipoMovimiento.EGRESO, null, destinoId);

        if (resultado == Resultado.OK) {
            MovimientoResponse respuesta = servicio.actualizar(
                    MOVIMIENTO_ID, solicitud, ACTOR.toString());

            assertEquals(destinoId,
                    respuesta.proveedor() == null
                            ? null
                            : respuesta.proveedor().id());
            return;
        }

        if (resultado == Resultado.CONFLICTO) {
            ConflictoNegocioException error = assertThrows(
                    ConflictoNegocioException.class,
                    () -> servicio.actualizar(
                            MOVIMIENTO_ID, solicitud, ACTOR.toString()));
            assertEquals("PROVEEDOR_INACTIVO", error.getConflicto());
            return;
        }

        RecursoNoEncontradoException error = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.actualizar(
                        MOVIMIENTO_ID, solicitud, ACTOR.toString()));
        assertEquals("proveedorId", error.getCampo());
    }

    @ParameterizedTest(name = "{0} -> {1} con relacionado {2}")
    @MethodSource("transiciones")
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 12: Solo se aceptan las dos transiciones permitidas")
    void soloSeAceptanLasDosTransicionesPermitidas(
            EstadoMovimiento origen,
            EstadoMovimiento destino,
            Relacionado relacionado) {

        MovimientoFinanciero movimiento = movimientoEnEstado(
                origen,
                TipoMovimiento.INGRESO,
                relacionado == Relacionado.AUSENTE ? null : CLIENTE_ID,
                null);

        if (relacionado != Relacionado.AUSENTE) {
            when(clientesCrm.buscar(CLIENTE_ID)).thenReturn(Optional.of(
                    cliente(CLIENTE_ID, relacionado == Relacionado.ACTIVO)));
        }

        when(movimientos.findParaModificarById(MOVIMIENTO_ID))
                .thenReturn(Optional.of(movimiento));

        boolean permitida = origen == EstadoMovimiento.PENDIENTE
                && (destino == EstadoMovimiento.PAGADO
                        || destino == EstadoMovimiento.ANULADO);

        if (permitida) {
            MovimientoResponse respuesta = servicio.cambiarEstado(
                    MOVIMIENTO_ID, destino, ACTOR.toString());

            assertEquals(destino, respuesta.estado());
            assertEquals(AHORA, respuesta.estadoActualizadoEn());
            assertEquals(ACTOR, respuesta.estadoActualizadoPor());
            assertEquals(ANTES, respuesta.actualizadoEn());
            assertEquals(ACTOR, respuesta.actualizadoPor());
        } else {
            ConflictoNegocioException error = assertThrows(
                    ConflictoNegocioException.class,
                    () -> servicio.cambiarEstado(
                            MOVIMIENTO_ID, destino, ACTOR.toString()));

            assertEquals("TRANSICION_NO_PERMITIDA", error.getConflicto());
            assertEquals(Map.of(
                    "estadoActual", origen.name(),
                    "estadoSolicitado", destino.name()),
                    error.getExtensiones());
            assertEquals(origen, movimiento.getEstado());
            assertEquals(ANTES, movimiento.getEstadoActualizadoEn());
        }

        verify(clientesCrm, never()).buscarParaAsociar(any());
        verify(proveedores, never()).findParaAsociarById(any());
    }

    @Test
    void cambiarEstadoDevuelve404SiElMovimientoNoExiste() {
        RecursoNoEncontradoException error = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.cambiarEstado(
                        MOVIMIENTO_ID, EstadoMovimiento.PAGADO,
                        ACTOR.toString()));

        assertEquals("El movimiento financiero no existe.", error.getMessage());
    }

    @Test
    void cambiarEstadoConservaLosDatosYLaAuditoriaDeDatos() {
        MovimientoFinanciero movimiento = movimientoPendiente(
                TipoMovimiento.INGRESO, CLIENTE_ID, null, MOVIMIENTO_ID);
        when(movimientos.findParaModificarById(MOVIMIENTO_ID))
                .thenReturn(Optional.of(movimiento));
        when(clientesCrm.buscar(CLIENTE_ID))
                .thenReturn(Optional.of(cliente(CLIENTE_ID, true)));

        servicio.cambiarEstado(
                MOVIMIENTO_ID, EstadoMovimiento.PAGADO, ACTOR.toString());

        assertEquals(TipoMovimiento.INGRESO, movimiento.getTipo());
        assertEquals(FECHA, movimiento.getFecha());
        assertEquals(CONCEPTO, movimiento.getConcepto());
        assertEquals(VALOR, movimiento.getValor());
        assertEquals(REFERENCIA, movimiento.getReferencia());
        assertEquals(CLIENTE_ID, movimiento.getClienteId());
        assertEquals(ANTES, movimiento.getActualizadoEn());
        assertEquals(ACTOR, movimiento.getActualizadoPor());
    }

    static Stream<Arguments> combinacionesCliente() {
        return combinaciones();
    }

    static Stream<Arguments> combinacionesProveedor() {
        return combinaciones();
    }

    static Stream<Arguments> transiciones() {
        return Stream.of(EstadoMovimiento.values())
                .flatMap(origen -> Stream.of(EstadoMovimiento.values())
                        .flatMap(destino -> Stream.of(Relacionado.values())
                                .map(relacionado -> Arguments.of(
                                        origen, destino, relacionado))));
    }

    private static Stream<Arguments> combinaciones() {
        return Stream.of(RelacionActual.values())
                .flatMap(actual -> Stream.of(DestinoRelacion.values())
                        .map(destino -> Arguments.of(
                                actual, destino, resultado(destino))));
    }

    private static Resultado resultado(DestinoRelacion destino) {
        return switch (destino) {
            case NULO, MISMO, OTRO_ACTIVO -> Resultado.OK;
            case OTRO_INACTIVO -> Resultado.CONFLICTO;
            case INEXISTENTE -> Resultado.NO_ENCONTRADO;
        };
    }

    private void prepararCliente(
            RelacionActual actual,
            DestinoRelacion destino) {

        if (actual != RelacionActual.SIN_RELACION) {
            when(clientesCrm.buscar(CLIENTE_ID)).thenReturn(Optional.of(
                    cliente(CLIENTE_ID, actual == RelacionActual.ACTIVO)));
        }

        switch (destino) {
            case MISMO -> {
                if (actual == RelacionActual.SIN_RELACION) {
                    when(clientesCrm.buscarParaAsociar(CLIENTE_ID))
                            .thenReturn(Optional.of(cliente(CLIENTE_ID, true)));
                }
            }
            case OTRO_ACTIVO -> when(clientesCrm.buscarParaAsociar(
                    OTRO_CLIENTE_ID))
                    .thenReturn(Optional.of(cliente(OTRO_CLIENTE_ID, true)));
            case OTRO_INACTIVO -> when(clientesCrm.buscarParaAsociar(
                    OTRO_CLIENTE_ID))
                    .thenReturn(Optional.of(cliente(OTRO_CLIENTE_ID, false)));
            case INEXISTENTE -> when(clientesCrm.buscarParaAsociar(
                    OTRO_CLIENTE_ID))
                    .thenReturn(Optional.empty());
            default -> {
            }
        }
    }

    private void prepararProveedor(
            RelacionActual actual,
            DestinoRelacion destino) {

        if (actual == RelacionActual.SIN_RELACION
                && destino == DestinoRelacion.MISMO) {
            when(proveedores.findParaAsociarById(PROVEEDOR_ID))
                    .thenReturn(Optional.of(proveedor(PROVEEDOR_ID, true)));
        }

        switch (destino) {
            case OTRO_ACTIVO -> when(proveedores.findParaAsociarById(
                    OTRO_PROVEEDOR_ID))
                    .thenReturn(Optional.of(proveedor(OTRO_PROVEEDOR_ID, true)));
            case OTRO_INACTIVO -> when(proveedores.findParaAsociarById(
                    OTRO_PROVEEDOR_ID))
                    .thenReturn(Optional.of(proveedor(OTRO_PROVEEDOR_ID, false)));
            case INEXISTENTE -> when(proveedores.findParaAsociarById(
                    OTRO_PROVEEDOR_ID))
                    .thenReturn(Optional.empty());
            default -> {
            }
        }
    }

    private static UUID destinoIdCliente(DestinoRelacion destino) {
        return switch (destino) {
            case NULO -> null;
            case MISMO -> CLIENTE_ID;
            case OTRO_ACTIVO, OTRO_INACTIVO, INEXISTENTE -> OTRO_CLIENTE_ID;
        };
    }

    private static UUID destinoIdProveedor(DestinoRelacion destino) {
        return switch (destino) {
            case NULO -> null;
            case MISMO -> PROVEEDOR_ID;
            case OTRO_ACTIVO, OTRO_INACTIVO, INEXISTENTE -> OTRO_PROVEEDOR_ID;
        };
    }

    private static MovimientoRequest solicitud(
            TipoMovimiento tipo,
            UUID clienteId,
            UUID proveedorId) {

        return new MovimientoRequest(
                tipo, FECHA, CONCEPTO, VALOR, REFERENCIA, clienteId, proveedorId);
    }

    private static MovimientoFinanciero movimientoPendiente(
            TipoMovimiento tipo,
            UUID clienteId,
            Proveedor proveedor,
            UUID id) {

        MovimientoFinanciero movimiento = MovimientoFinanciero.crear(
                tipo, FECHA, CONCEPTO, VALOR, REFERENCIA, clienteId, proveedor,
                ACTOR, ANTES);
        ReflectionTestUtils.setField(movimiento, "id", id);
        return movimiento;
    }

    private static MovimientoFinanciero movimientoEnEstado(
            EstadoMovimiento estado,
            TipoMovimiento tipo,
            UUID clienteId,
            Proveedor proveedor) {

        MovimientoFinanciero movimiento = movimientoPendiente(
                tipo, clienteId, proveedor, MOVIMIENTO_ID);

        if (estado != EstadoMovimiento.PENDIENTE) {
            movimiento.cambiarEstado(estado, ACTOR, ANTES);
        }

        return movimiento;
    }

    private static ClienteReferencia cliente(UUID id, boolean activo) {
        return new ClienteReferencia(id, "Cliente " + id, activo);
    }

    private static Proveedor proveedor(UUID id, boolean activo) {
        Proveedor proveedor = Proveedor.crear(
                TipoDocumentoProveedor.NIT,
                "900123456",
                "Proveedor " + id,
                null,
                "proveedor@example.test",
                "601 000 0000",
                null,
                null,
                ACTOR,
                ANTES);
        ReflectionTestUtils.setField(proveedor, "id", id);

        if (!activo) {
            proveedor.cambiarEstado(false, ACTOR, ANTES);
        }

        return proveedor;
    }

    private static FiltroMovimientos filtro() {
        return new FiltroMovimientos(
                null, null, null, null, new RangoFechas(null, null), null);
    }

    private enum RelacionActual {
        SIN_RELACION, ACTIVO, INACTIVO
    }

    private enum DestinoRelacion {
        NULO, MISMO, OTRO_ACTIVO, OTRO_INACTIVO, INEXISTENTE
    }

    private enum Resultado {
        OK, CONFLICTO, NO_ENCONTRADO
    }

    private enum Relacionado {
        AUSENTE, ACTIVO, INACTIVO
    }
}
