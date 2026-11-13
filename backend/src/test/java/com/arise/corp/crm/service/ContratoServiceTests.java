package com.arise.corp.crm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.arise.corp.crm.GeneradoresCrm;
import com.arise.corp.crm.dto.ContratoActualizarRequest;
import com.arise.corp.crm.dto.ContratoCrearRequest;
import com.arise.corp.crm.dto.ContratoResponse;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.Contrato;
import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.crm.entity.TipoDocumento;
import com.arise.corp.crm.repository.ClienteRepository;
import com.arise.corp.crm.repository.ContratoRepository;
import com.arise.corp.shared.error.ConflictoNegocioException;
import com.arise.corp.shared.error.RecursoNoEncontradoException;
import com.arise.corp.shared.paginacion.PaginaResponse;

/**
 * Pruebas del servicio de contratos (CRM-03, Requirements 11 a 14).
 *
 * <p>Mockito y {@link Clock#fixed} permiten comprobar la auditoria de datos y
 * de estado sin base de datos. Las propiedades usan generadores con semilla
 * fija o recorridos exhaustivos.
 */
class ContratoServiceTests {

    private static final int CASOS = 120;

    private static final UUID CLIENTE_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CONTRATO_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OTRO_CONTRATO_ID =
            UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID ACTOR =
            UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID ACTOR_ANTERIOR =
            UUID.fromString("55555555-5555-4555-8555-555555555555");

    private static final Instant ANTES = Instant.parse("2026-01-02T03:04:05.000001Z");
    private static final Instant AHORA = Instant.parse("2026-11-11T15:30:45.123456Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final LocalDate FECHA_INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate FECHA_FIN = LocalDate.of(2026, 12, 31);
    private static final BigDecimal VALOR = new BigDecimal("1234.50");

    private ContratoRepository contratos;
    private ClienteRepository clientes;
    private ContratoService servicio;

    @BeforeEach
    void setUp() {
        contratos = mock(ContratoRepository.class);
        clientes = mock(ClienteRepository.class);
        servicio = new ContratoService(contratos, clientes, RELOJ);

        when(contratos.findByCodigo(any())).thenReturn(Optional.empty());
        when(contratos.findParaModificarById(any())).thenReturn(Optional.empty());
        when(contratos.findConClienteById(any())).thenReturn(Optional.empty());
        when(clientes.findParaContratoById(any())).thenReturn(Optional.empty());
        when(contratos.save(any(Contrato.class))).thenAnswer(invocacion -> {
            Contrato contrato = invocacion.getArgument(0);
            ReflectionTestUtils.setField(contrato, "id", CONTRATO_ID);
            return contrato;
        });
    }

    @Test
    void creaUnContratoEnBorradorConAuditoriaDeDatosYEstado() {
        Cliente cliente = cliente(true);
        when(clientes.findParaContratoById(CLIENTE_ID))
                .thenReturn(Optional.of(cliente));

        ContratoResponse respuesta = servicio.crear(
                crearRequest(" ctr-2026-001 "), ACTOR.toString());

        assertEquals(CONTRATO_ID, respuesta.id());
        assertEquals("CTR-2026-001", respuesta.codigo());
        assertEquals("Contrato de prueba", respuesta.descripcion());
        assertEquals(FECHA_INICIO, respuesta.fechaInicio());
        assertEquals(FECHA_FIN, respuesta.fechaFin());
        assertEquals("1234.50", respuesta.valor());
        assertEquals(EstadoContrato.BORRADOR, respuesta.estado());
        assertEquals(CLIENTE_ID, respuesta.cliente().id());
        assertNull(respuesta.cuentaCorporativa());
        assertEquals(AHORA, respuesta.estadoActualizadoEn());
        assertEquals(ACTOR, respuesta.estadoActualizadoPor());
        assertEquals(AHORA, respuesta.creadoEn());
        assertEquals(AHORA, respuesta.actualizadoEn());
        verify(clientes).findParaContratoById(CLIENTE_ID);
        verify(contratos).save(any(Contrato.class));
    }

    @Test
    void rechazaUnClienteInexistenteAlCrear() {
        RecursoNoEncontradoException error = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.crear(crearRequest("CTR-2026-001"), ACTOR.toString()));

        assertEquals("El cliente no existe.", error.getMessage());
        assertEquals("clienteId", error.getCampo());
        verify(contratos, never()).save(any(Contrato.class));
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 9: Las referencias inactivas no se asignan")
    void unClienteInactivoNoAdmiteContratosNuevos() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            String codigo = "CTR-" + GeneradoresCrm.cadena(azar, 3 + azar.nextInt(20));
            Cliente inactivo = cliente(false);
            when(clientes.findParaContratoById(CLIENTE_ID))
                    .thenReturn(Optional.of(inactivo));

            ConflictoNegocioException error = assertThrows(
                    ConflictoNegocioException.class,
                    () -> servicio.crear(crearRequest(codigo), ACTOR.toString()),
                    "se creo un contrato <" + codigo + "> para un cliente inactivo");

            assertEquals("CLIENTE_INACTIVO_SIN_CONTRATOS", error.getConflicto());
            assertEquals("clienteId", error.getCampo());
        }

        verify(contratos, never()).findByCodigo(any());
        verify(contratos, never()).save(any(Contrato.class));
    }

    @Test
    void rechazaElCodigoDuplicadoAlCrear() {
        Cliente cliente = cliente(true);
        when(clientes.findParaContratoById(CLIENTE_ID))
                .thenReturn(Optional.of(cliente));
        when(contratos.findByCodigo("CTR-2026-001"))
                .thenReturn(Optional.of(contrato(cliente, "CTR-2026-001",
                        EstadoContrato.BORRADOR)));

        ConflictoNegocioException error = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.crear(crearRequest("CTR-2026-001"), ACTOR.toString()));

        assertEquals("CONTRATO_CODIGO_DUPLICADO", error.getConflicto());
        assertEquals("codigo", error.getCampo());
        verify(contratos, never()).save(any(Contrato.class));
    }

    @Test
    void detectaElCodigoDuplicadoAunqueCambienMayusculasYEspacios() {
        Random azar = GeneradoresCrm.nuevoRandom();
        Cliente cliente = cliente(true);
        when(clientes.findParaContratoById(CLIENTE_ID))
                .thenReturn(Optional.of(cliente));

        for (int i = 0; i < CASOS; i++) {
            String base = "CTR-" + GeneradoresCrm.cadena(azar, 3 + azar.nextInt(20));
            String variante = "  " + base.toLowerCase(Locale.ROOT) + "  ";
            when(contratos.findByCodigo(base))
                    .thenReturn(Optional.of(contrato(cliente, base,
                            EstadoContrato.BORRADOR)));

            ConflictoNegocioException error = assertThrows(
                    ConflictoNegocioException.class,
                    () -> servicio.crear(crearRequest(variante), ACTOR.toString()),
                    "no detecto el codigo duplicado base <" + base
                            + "> con la variante <" + variante + ">");

            assertEquals("CONTRATO_CODIGO_DUPLICADO", error.getConflicto(),
                    "conflicto inesperado para <" + variante + ">");
        }
    }

    @Test
    void obtenerDevuelveElContratoYFallaSiNoExiste() {
        Contrato contrato = contrato(cliente(true), "CTR-2026-001",
                EstadoContrato.ACTIVO);
        when(contratos.findConClienteById(CONTRATO_ID))
                .thenReturn(Optional.of(contrato));

        ContratoResponse respuesta = servicio.obtener(CONTRATO_ID);

        assertEquals("CTR-2026-001", respuesta.codigo());
        assertEquals(EstadoContrato.ACTIVO, respuesta.estado());

        when(contratos.findConClienteById(CONTRATO_ID)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.obtener(CONTRATO_ID));
    }

    @ParameterizedTest
    @EnumSource(EstadoContrato.class)
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 12: Solo se editan contratos en borrador")
    void soloSeEditanContratosEnBorrador(EstadoContrato estado) {
        Contrato contrato = contrato(cliente(true), "CTR-ORIGINAL", estado);
        when(contratos.findParaModificarById(CONTRATO_ID))
                .thenReturn(Optional.of(contrato));

        if (estado == EstadoContrato.BORRADOR) {
            ContratoResponse respuesta = servicio.actualizar(
                    CONTRATO_ID, actualizarRequest(" ctr-nuevo-001 "),
                    ACTOR.toString());

            assertEquals("CTR-NUEVO-001", respuesta.codigo());
            assertEquals("Contrato actualizado", respuesta.descripcion());
            assertEquals(new BigDecimal("999.99"),
                    new BigDecimal(respuesta.valor()));
            assertEquals(EstadoContrato.BORRADOR, respuesta.estado());
            assertEquals(AHORA, respuesta.actualizadoEn());
            assertEquals(AHORA, contrato.getActualizadoEn());
            assertEquals(ACTOR, contrato.getActualizadoPor());
            assertEquals(ANTES, respuesta.creadoEn());
            assertEquals(ANTES, respuesta.estadoActualizadoEn());
            assertEquals(ACTOR_ANTERIOR, respuesta.estadoActualizadoPor());
        } else {
            ConflictoNegocioException error = assertThrows(
                    ConflictoNegocioException.class,
                    () -> servicio.actualizar(
                            CONTRATO_ID, actualizarRequest("CTR-NUEVO-001"),
                            ACTOR.toString()),
                    "se edito un contrato en estado " + estado);

            assertEquals("CONTRATO_NO_EDITABLE", error.getConflicto());
            assertNull(error.getCampo());
            assertEquals(estado.name(), error.getExtensiones().get("estadoActual"));
            assertEquals("CTR-ORIGINAL", contrato.getCodigo());
            assertEquals(estado, contrato.getEstado());
            assertEquals(ANTES, contrato.getActualizadoEn());
            assertEquals(ANTES, contrato.getEstadoActualizadoEn());
        }
    }

    @Test
    void rechazaElCodigoDeOtroContratoAlEditar() {
        Cliente cliente = cliente(true);
        Contrato contrato = contrato(cliente, "CTR-ORIGINAL", EstadoContrato.BORRADOR);
        Contrato otro = contrato(cliente, "CTR-OCUPADO", EstadoContrato.BORRADOR);
        ReflectionTestUtils.setField(otro, "id", OTRO_CONTRATO_ID);
        when(contratos.findParaModificarById(CONTRATO_ID))
                .thenReturn(Optional.of(contrato));
        when(contratos.findByCodigo("CTR-OCUPADO")).thenReturn(Optional.of(otro));

        ConflictoNegocioException error = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.actualizar(
                        CONTRATO_ID, actualizarRequest("  ctr-ocupado  "),
                        ACTOR.toString()));

        assertEquals("CONTRATO_CODIGO_DUPLICADO", error.getConflicto());
        assertEquals("CTR-ORIGINAL", contrato.getCodigo());
        assertEquals(ANTES, contrato.getActualizadoEn());
    }

    @Test
    void rechazaLaEdicionDeUnContratoInexistente() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.actualizar(
                        CONTRATO_ID, actualizarRequest("CTR-NUEVO-001"),
                        ACTOR.toString()));
    }

    @ParameterizedTest(name = "{0} -> {1} con cliente activo={2}")
    @MethodSource("transicionesConCliente")
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 11: Solo se aceptan las cuatro transiciones permitidas")
    void soloSeAceptanLasCuatroTransicionesPermitidas(
            EstadoContrato origen,
            EstadoContrato destino,
            boolean clienteActivo) {

        Cliente cliente = cliente(clienteActivo);
        Contrato contrato = contrato(cliente, "CTR-2026-001", origen);
        when(contratos.findParaModificarById(CONTRATO_ID))
                .thenReturn(Optional.of(contrato));

        boolean permitida = origen.puedeTransicionarA(destino);

        if (permitida) {
            ContratoResponse respuesta = servicio.cambiarEstado(
                    CONTRATO_ID, destino, ACTOR.toString());

            assertEquals(destino, respuesta.estado(),
                    "estado inesperado para " + origen + " -> " + destino);
            assertEquals(AHORA, respuesta.estadoActualizadoEn(),
                    "no se actualizo la auditoria de estado");
            assertEquals(ACTOR, respuesta.estadoActualizadoPor(),
                    "el actor del estado no es el sub");
            assertEquals(ANTES, respuesta.actualizadoEn(),
                    "la auditoria de datos no debe cambiar");
            assertEquals(ANTES, contrato.getActualizadoEn());
            assertEquals(ACTOR_ANTERIOR, contrato.getActualizadoPor());
        } else {
            ConflictoNegocioException error = assertThrows(
                    ConflictoNegocioException.class,
                    () -> servicio.cambiarEstado(
                            CONTRATO_ID, destino, ACTOR.toString()),
                    "se acepto la transicion " + origen + " -> " + destino);

            assertEquals("TRANSICION_NO_PERMITIDA", error.getConflicto());
            assertEquals("estado", error.getCampo());
            assertEquals(origen.name(), error.getExtensiones().get("estadoActual"));
            assertEquals(destino.name(), error.getExtensiones().get("estadoSolicitado"));
            assertEquals(origen, contrato.getEstado());
            assertEquals(ANTES, contrato.getEstadoActualizadoEn());
            assertEquals(ACTOR_ANTERIOR, contrato.getEstadoActualizadoPor());
        }

        // Requirement 14.5: cambiar el estado no consulta al cliente.
        verifyNoInteractions(clientes);
    }

    @Test
    void rechazaElCambioDeEstadoDeUnContratoInexistente() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.cambiarEstado(
                        CONTRATO_ID, EstadoContrato.ACTIVO, ACTOR.toString()));
    }

    @Test
    void listarConvierteLosContratosYConservaLaPagina() {
        Contrato contrato = contrato(cliente(true), "CTR-2026-001",
                EstadoContrato.BORRADOR);
        when(contratos.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(contrato)));

        PaginaResponse<ContratoResponse> pagina = servicio.listar(
                new FiltroContratos(null, null), PageRequest.of(0, 20));

        assertEquals(1, pagina.content().size());
        assertEquals("CTR-2026-001", pagina.content().get(0).codigo());
        assertEquals(0, pagina.page());
        assertTrue(pagina.totalPages() > 0);
    }

    static Stream<Arguments> transicionesConCliente() {
        return Stream.of(EstadoContrato.values())
                .flatMap(origen -> Stream.of(EstadoContrato.values())
                        .flatMap(destino -> Stream.of(true, false)
                                .map(activo -> Arguments.of(origen, destino, activo))));
    }

    private static ContratoCrearRequest crearRequest(String codigo) {
        return new ContratoCrearRequest(
                CLIENTE_ID,
                codigo,
                "Contrato de prueba",
                FECHA_INICIO,
                FECHA_FIN,
                VALOR);
    }

    private static ContratoActualizarRequest actualizarRequest(String codigo) {
        return new ContratoActualizarRequest(
                codigo,
                "Contrato actualizado",
                FECHA_INICIO,
                FECHA_FIN,
                new BigDecimal("999.99"));
    }

    private static Cliente cliente(boolean activo) {
        Cliente cliente = Cliente.crear(
                TipoDocumento.CC,
                "10234567",
                "Cliente de prueba",
                "cliente@example.test",
                "3001234567",
                null,
                "Bogota",
                null,
                ACTOR_ANTERIOR,
                ANTES);
        ReflectionTestUtils.setField(cliente, "id", CLIENTE_ID);

        if (!activo) {
            cliente.cambiarEstado(false, ACTOR_ANTERIOR, ANTES);
        }

        return cliente;
    }

    private static Contrato contrato(
            Cliente cliente, String codigo, EstadoContrato estado) {

        Contrato contrato = Contrato.crear(
                cliente,
                codigo,
                "Contrato de prueba",
                FECHA_INICIO,
                FECHA_FIN,
                VALOR,
                ACTOR_ANTERIOR,
                ANTES);
        ReflectionTestUtils.setField(contrato, "id", CONTRATO_ID);

        switch (estado) {
            case BORRADOR -> {
                // Estado inicial de la fabrica.
            }
            case ACTIVO -> contrato.cambiarEstado(
                    EstadoContrato.ACTIVO, ACTOR_ANTERIOR, ANTES);
            case FINALIZADO -> {
                contrato.cambiarEstado(EstadoContrato.ACTIVO, ACTOR_ANTERIOR, ANTES);
                contrato.cambiarEstado(
                        EstadoContrato.FINALIZADO, ACTOR_ANTERIOR, ANTES);
            }
            case CANCELADO -> contrato.cambiarEstado(
                    EstadoContrato.CANCELADO, ACTOR_ANTERIOR, ANTES);
        }

        return contrato;
    }
}
