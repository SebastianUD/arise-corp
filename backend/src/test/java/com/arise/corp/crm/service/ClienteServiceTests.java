package com.arise.corp.crm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.arise.corp.crm.GeneradoresCrm;
import com.arise.corp.crm.dto.ClienteRequest;
import com.arise.corp.crm.dto.ClienteResponse;
import com.arise.corp.crm.dto.ClienteResumenResponse;
import com.arise.corp.crm.dto.ClienteVista;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.CuentaCorporativa;
import com.arise.corp.crm.entity.TipoDocumento;
import com.arise.corp.crm.repository.ClienteRepository;
import com.arise.corp.crm.repository.CuentaCorporativaRepository;
import com.arise.corp.shared.error.ConflictoNegocioException;
import com.arise.corp.shared.error.RecursoNoEncontradoException;
import com.arise.corp.shared.paginacion.PaginaResponse;

/**
 * Pruebas del servicio de clientes (CRM-01, CRM-04).
 *
 * <p>Mockito y {@link Clock#fixed} permiten comprobar la auditoria sin base de
 * datos. Las propiedades usan los generadores con semilla fija de
 * {@link GeneradoresCrm}.
 */
class ClienteServiceTests {

    private static final int CASOS = 120;

    private static final UUID CLIENTE_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTRO_CLIENTE_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CUENTA_ACTUAL_ID =
            UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID CUENTA_OTRA_ID =
            UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID ACTOR =
            UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID ACTOR_ANTERIOR =
            UUID.fromString("66666666-6666-4666-8666-666666666666");

    private static final Instant ANTES = Instant.parse("2026-01-02T03:04:05.000001Z");
    private static final Instant AHORA = Instant.parse("2026-11-11T15:30:45.123456Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final String NOMBRE_ORIGINAL = "Cliente original";

    private ClienteRepository clientes;
    private CuentaCorporativaRepository cuentas;
    private ClienteService servicio;

    @BeforeEach
    void setUp() {
        clientes = mock(ClienteRepository.class);
        cuentas = mock(CuentaCorporativaRepository.class);
        servicio = new ClienteService(clientes, cuentas, RELOJ);

        when(clientes.findByTipoDocumentoAndNumeroDocumento(any(), any()))
                .thenReturn(Optional.empty());
        when(clientes.findConCuentaById(any())).thenReturn(Optional.empty());
        when(cuentas.findParaAsignarById(any())).thenReturn(Optional.empty());
        when(clientes.save(any(Cliente.class))).thenAnswer(invocacion -> {
            Cliente cliente = invocacion.getArgument(0);
            ReflectionTestUtils.setField(cliente, "id", CLIENTE_ID);
            return cliente;
        });
    }

    @Test
    void creaUnClienteActivoConLaAuditoriaDelActorYLaCuentaIndicada() {
        CuentaCorporativa cuenta = cuenta(CUENTA_ACTUAL_ID, true);
        when(cuentas.findParaAsignarById(CUENTA_ACTUAL_ID)).thenReturn(Optional.of(cuenta));

        ClienteResponse respuesta = servicio.crear(
                solicitud("1.023.456-7", CUENTA_ACTUAL_ID), ACTOR.toString());

        assertEquals(CLIENTE_ID, respuesta.id());
        assertEquals(TipoDocumento.CC, respuesta.tipoDocumento());
        assertEquals("10234567", respuesta.numeroDocumento());
        assertEquals("Cliente de prueba", respuesta.nombre());
        assertTrue(respuesta.activo());
        assertEquals(CUENTA_ACTUAL_ID, respuesta.cuentaCorporativa().id());
        assertEquals(AHORA, respuesta.creadoEn());
        assertEquals(AHORA, respuesta.actualizadoEn());
        assertEquals(ACTOR, respuesta.actualizadoPor());
        verify(clientes).save(any(Cliente.class));
    }

    @Test
    void creaUnClienteSinCuentaCuandoNoSeIndica() {
        servicio.crear(solicitud("10234567", null), ACTOR.toString());

        assertNull(obtenerClienteGuardado().getCuentaCorporativa());
        verify(cuentas, never()).findParaAsignarById(any());
    }

    @Test
    void rechazaUnaCuentaInexistenteAlCrear() {
        RecursoNoEncontradoException error = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.crear(solicitud("10234567", CUENTA_OTRA_ID),
                        ACTOR.toString()));

        assertEquals("La cuenta corporativa no existe.", error.getMessage());
        assertEquals("cuentaCorporativaId", error.getCampo());
        verify(clientes, never()).save(any(Cliente.class));
    }

    @Test
    void rechazaUnaCuentaInactivaAlCrear() {
        CuentaCorporativa inactiva = cuenta(CUENTA_OTRA_ID, false);
        when(cuentas.findParaAsignarById(CUENTA_OTRA_ID))
                .thenReturn(Optional.of(inactiva));

        ConflictoNegocioException error = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.crear(solicitud("10234567", CUENTA_OTRA_ID),
                        ACTOR.toString()));

        assertEquals("CUENTA_INACTIVA", error.getConflicto());
        assertEquals("cuentaCorporativaId", error.getCampo());
        verify(clientes, never()).save(any(Cliente.class));
    }

    @Test
    void distingueElDocumentoDuplicadoActivoDelInactivo() {
        Cliente activo = cliente("10234567", true, null);
        when(clientes.findByTipoDocumentoAndNumeroDocumento(
                TipoDocumento.CC, "10234567")).thenReturn(Optional.of(activo));

        ConflictoNegocioException duplicado = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.crear(solicitud("1.023.456-7", null), ACTOR.toString()));
        assertEquals("CLIENTE_DUPLICADO", duplicado.getConflicto());
        assertEquals("numeroDocumento", duplicado.getCampo());

        Cliente inactivo = cliente("10234567", false, null);
        when(clientes.findByTipoDocumentoAndNumeroDocumento(
                TipoDocumento.CC, "10234567")).thenReturn(Optional.of(inactivo));

        ConflictoNegocioException existente = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.crear(solicitud("10234567", null), ACTOR.toString()));
        assertEquals("CLIENTE_INACTIVO_EXISTENTE", existente.getConflicto());
        assertEquals(CLIENTE_ID, existente.getExtensiones().get("clienteId"));

        verify(clientes, never()).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 7: Los duplicados se detectan sobre el valor normalizado")
    void detectaElDocumentoDuplicadoAunqueCambieElFormato() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            String base = GeneradoresCrm.documentoBase(azar);
            String variante = GeneradoresCrm.varianteDocumento(azar, base);
            Cliente existente = cliente(base, true, null);
            when(clientes.findByTipoDocumentoAndNumeroDocumento(
                    TipoDocumento.CC, base)).thenReturn(Optional.of(existente));

            ConflictoNegocioException error = assertThrows(
                    ConflictoNegocioException.class,
                    () -> servicio.crear(solicitud(variante, null), ACTOR.toString()),
                    "no detecto el duplicado del documento base <" + base
                            + "> con la variante <" + variante + ">");

            assertEquals("CLIENTE_DUPLICADO", error.getConflicto(),
                    "conflicto inesperado para <" + variante + ">");
        }
    }

    @Test
    void actualizaLosDatosYConservaActivoYCreadoEn() {
        Cliente cliente = cliente("10234567", true, null, ANTES);
        when(clientes.findConCuentaById(CLIENTE_ID)).thenReturn(Optional.of(cliente));

        ClienteResponse respuesta = servicio.actualizar(
                CLIENTE_ID,
                new ClienteRequest(TipoDocumento.CE, "AB-123", "Nombre nuevo",
                        "nuevo@example.test", "3000000000", "Calle 2", "Medellin", null),
                ACTOR.toString());

        assertEquals("AB123", respuesta.numeroDocumento());
        assertEquals("Nombre nuevo", respuesta.nombre());
        assertEquals("Medellin", respuesta.ciudad());
        assertTrue(respuesta.activo());
        assertEquals(ANTES, respuesta.creadoEn());
        assertEquals(AHORA, respuesta.actualizadoEn());
        assertEquals(ACTOR, respuesta.actualizadoPor());
    }

    @Test
    void conservaUnClienteInactivoEnLaEdicion() {
        Cliente cliente = cliente("10234567", false, null, ANTES);
        when(clientes.findConCuentaById(CLIENTE_ID)).thenReturn(Optional.of(cliente));

        ClienteResponse respuesta = servicio.actualizar(
                CLIENTE_ID, solicitud("10234567", null), ACTOR.toString());

        assertFalse(respuesta.activo());
        assertEquals(ANTES, respuesta.creadoEn());
        assertEquals(AHORA, respuesta.actualizadoEn());
    }

    @Test
    void rechazaElDocumentoDeOtroClienteAlEditar() {
        Cliente cliente = cliente("10234567", true, null, ANTES);
        Cliente otro = cliente("98765432", true, null, ANTES);
        ReflectionTestUtils.setField(otro, "id", OTRO_CLIENTE_ID);
        when(clientes.findConCuentaById(CLIENTE_ID)).thenReturn(Optional.of(cliente));
        when(clientes.findByTipoDocumentoAndNumeroDocumento(
                TipoDocumento.CC, "98765432")).thenReturn(Optional.of(otro));

        ConflictoNegocioException error = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.actualizar(
                        CLIENTE_ID, solicitud("98765432", null), ACTOR.toString()));

        assertEquals("CLIENTE_DUPLICADO", error.getConflicto());
        assertEquals("10234567", cliente.getNumeroDocumento());
    }

    @Test
    void rechazaLaEdicionDeUnClienteInexistente() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.actualizar(CLIENTE_ID, solicitud("10234567", null),
                        ACTOR.toString()));
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 8: Activar y desactivar es idempotente")
    void cambiarEstadoEsIdempotenteYConservaLaAuditoriaSiCoincide() {
        for (boolean actual : new boolean[] {true, false}) {
            for (boolean solicitado : new boolean[] {true, false}) {
                Cliente cliente = cliente("10234567", actual, null, ANTES);
                when(clientes.findConCuentaById(CLIENTE_ID))
                        .thenReturn(Optional.of(cliente));

                ClienteResponse respuesta = servicio.cambiarEstado(
                        CLIENTE_ID, solicitado, ACTOR.toString());

                assertEquals(solicitado, respuesta.activo(),
                        "estado incorrecto para actual=" + actual
                                + " solicitado=" + solicitado);
                if (actual == solicitado) {
                    assertEquals(ANTES, respuesta.actualizadoEn(),
                            "la auditoria cambio sin cambio de estado");
                    assertEquals(ACTOR_ANTERIOR, respuesta.actualizadoPor(),
                            "el actor cambio sin cambio de estado");
                } else {
                    assertEquals(AHORA, respuesta.actualizadoEn(),
                            "la auditoria no cambio al cambiar de estado");
                    assertEquals(ACTOR, respuesta.actualizadoPor(),
                            "el actor no se actualizo al cambiar de estado");
                }
            }
        }

        // El servicio no depende de ContratoRepository: desactivar un cliente no
        // consulta ni modifica sus contratos.
        verify(clientes, never()).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 9: Las referencias inactivas no se asignan")
    void actualizarSoloAsignaCuentasActivasOSuCuentaActual() {
        for (CasoCuenta caso : casosDeCuentaParaActualizar()) {
            CuentaCorporativa actual = caso.cuentaActual();
            Cliente cliente = cliente("10234567", true, actual, ANTES);
            ReflectionTestUtils.setField(cliente, "id", CLIENTE_ID);
            when(clientes.findConCuentaById(CLIENTE_ID))
                    .thenReturn(Optional.of(cliente));

            CuentaCorporativa destino = caso.destino() == Destino.MISMA
                    ? actual
                    : prepararDestino(caso.destino());

            if (caso.aceptada()) {
                ClienteResponse respuesta = servicio.actualizar(
                        CLIENTE_ID, solicitud("10234567", idDe(caso.destino())),
                        ACTOR.toString());

                assertEquals(idDe(caso.destino()),
                        respuesta.cuentaCorporativa() == null
                                ? null
                                : respuesta.cuentaCorporativa().id(),
                        "cuenta inesperada para " + caso);
                assertSame(destino, cliente.getCuentaCorporativa(),
                        "la entidad no conservo la cuenta esperada para " + caso);
            } else {
                ConflictoNegocioException error = assertThrows(
                        ConflictoNegocioException.class,
                        () -> servicio.actualizar(
                                CLIENTE_ID, solicitud("10234567", idDe(caso.destino())),
                                ACTOR.toString()),
                        "se acepto una cuenta inactiva distinta para " + caso);

                assertEquals("CUENTA_INACTIVA", error.getConflicto());
                assertEquals(NOMBRE_ORIGINAL, cliente.getNombre(),
                        "el cliente cambio pese al conflicto para " + caso);
            }
        }
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 9: Las referencias inactivas no se asignan")
    void crearSoloAsignaCuentasActivas() {
        for (CasoCuenta caso : casosDeCuentaParaCrear()) {
            prepararDestino(caso.destino());

            if (caso.aceptada()) {
                ClienteResponse respuesta = servicio.crear(
                        solicitud("10234567", idDe(caso.destino())),
                        ACTOR.toString());
                assertEquals(idDe(caso.destino()),
                        respuesta.cuentaCorporativa() == null
                                ? null
                                : respuesta.cuentaCorporativa().id());
            } else {
                ConflictoNegocioException error = assertThrows(
                        ConflictoNegocioException.class,
                        () -> servicio.crear(
                                solicitud("10234567", idDe(caso.destino())),
                                ACTOR.toString()));
                assertEquals("CUENTA_INACTIVA", error.getConflicto());
            }
        }
    }

    @Test
    void quitaLaAsociacionCuandoLaCuentaDestinoEsNula() {
        Cliente cliente = cliente("10234567", true,
                cuenta(CUENTA_ACTUAL_ID, true), ANTES);
        when(clientes.findConCuentaById(CLIENTE_ID)).thenReturn(Optional.of(cliente));

        ClienteResponse respuesta = servicio.actualizar(
                CLIENTE_ID, solicitud("10234567", null), ACTOR.toString());

        assertNull(respuesta.cuentaCorporativa());
        assertNull(cliente.getCuentaCorporativa());
        verify(cuentas, never()).findParaAsignarById(any());
    }

    @Test
    void obtenerDevuelveLaVistaDelActorYFallaSiNoExiste() {
        Cliente cliente = cliente("10234567", true, null);
        when(clientes.findConCuentaById(CLIENTE_ID)).thenReturn(Optional.of(cliente));

        assertInstanceOf(ClienteResponse.class,
                servicio.obtener(CLIENTE_ID, VistaCliente.COMPLETA));
        assertInstanceOf(ClienteResumenResponse.class,
                servicio.obtener(CLIENTE_ID, VistaCliente.REDUCIDA));

        when(clientes.findConCuentaById(CLIENTE_ID)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.obtener(CLIENTE_ID, VistaCliente.COMPLETA));
    }

    @Test
    void listarConvierteCadaElementoALaVistaSolicitada() {
        Cliente cliente = cliente("10234567", true, null);
        when(clientes.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cliente)));
        FiltroClientes filtro = new FiltroClientes("ana", true, null);
        Pageable pagina = PageRequest.of(0, 20);

        PaginaResponse<ClienteVista> completa = servicio.listar(
                filtro, pagina, VistaCliente.COMPLETA);
        PaginaResponse<ClienteVista> reducida = servicio.listar(
                filtro, pagina, VistaCliente.REDUCIDA);

        assertInstanceOf(ClienteResponse.class, completa.content().get(0));
        assertInstanceOf(ClienteResumenResponse.class, reducida.content().get(0));
    }

    private Cliente obtenerClienteGuardado() {
        org.mockito.ArgumentCaptor<Cliente> captor =
                org.mockito.ArgumentCaptor.forClass(Cliente.class);
        verify(clientes).save(captor.capture());
        return captor.getValue();
    }

    private CuentaCorporativa prepararDestino(Destino destino) {
        return switch (destino) {
            case NINGUNA -> null;
            case MISMA -> null;
            case OTRA_ACTIVA -> {
                CuentaCorporativa otra = cuenta(CUENTA_OTRA_ID, true);
                when(cuentas.findParaAsignarById(CUENTA_OTRA_ID))
                        .thenReturn(Optional.of(otra));
                yield otra;
            }
            case OTRA_INACTIVA -> {
                CuentaCorporativa otra = cuenta(CUENTA_OTRA_ID, false);
                when(cuentas.findParaAsignarById(CUENTA_OTRA_ID))
                        .thenReturn(Optional.of(otra));
                yield otra;
            }
        };
    }

    private static UUID idDe(Destino destino) {
        return switch (destino) {
            case NINGUNA -> null;
            case MISMA -> CUENTA_ACTUAL_ID;
            case OTRA_ACTIVA, OTRA_INACTIVA -> CUENTA_OTRA_ID;
        };
    }

    private static List<CasoCuenta> casosDeCuentaParaActualizar() {
        List<CasoCuenta> casos = new ArrayList<>();

        for (EstadoCuenta actual : EstadoCuenta.values()) {
            for (Destino destino : Destino.values()) {
                if (destino == Destino.MISMA && actual == EstadoCuenta.NINGUNA) {
                    continue;
                }
                boolean aceptada = destino == Destino.NINGUNA
                        || destino == Destino.MISMA
                        || destino == Destino.OTRA_ACTIVA;
                casos.add(new CasoCuenta(actual, destino, aceptada));
            }
        }

        return casos;
    }

    private static List<CasoCuenta> casosDeCuentaParaCrear() {
        return List.of(
                new CasoCuenta(EstadoCuenta.NINGUNA, Destino.NINGUNA, true),
                new CasoCuenta(EstadoCuenta.NINGUNA, Destino.OTRA_ACTIVA, true),
                new CasoCuenta(EstadoCuenta.NINGUNA, Destino.OTRA_INACTIVA, false));
    }

    private static ClienteRequest solicitud(String documento, UUID cuentaId) {
        return new ClienteRequest(
                TipoDocumento.CC,
                documento,
                "Cliente de prueba",
                "cliente@example.test",
                "3001234567",
                "Calle 1",
                "Bogota",
                cuentaId);
    }

    private static Cliente cliente(
            String documento, boolean activo, CuentaCorporativa cuenta) {

        return cliente(documento, activo, cuenta, ANTES);
    }

    private static Cliente cliente(
            String documento,
            boolean activo,
            CuentaCorporativa cuenta,
            Instant creadoEn) {

        Cliente cliente = Cliente.crear(
                TipoDocumento.CC,
                documento,
                NOMBRE_ORIGINAL,
                "cliente@example.test",
                "3001234567",
                "Calle 1",
                "Bogota",
                cuenta,
                ACTOR_ANTERIOR,
                creadoEn);
        ReflectionTestUtils.setField(cliente, "id", CLIENTE_ID);

        if (!activo) {
            cliente.cambiarEstado(false, ACTOR_ANTERIOR, creadoEn);
        }

        return cliente;
    }

    private static CuentaCorporativa cuenta(UUID id, boolean activa) {
        CuentaCorporativa cuenta = CuentaCorporativa.crear(
                "900123456",
                "Cuenta de prueba",
                null,
                null,
                null,
                ACTOR_ANTERIOR,
                ANTES);
        ReflectionTestUtils.setField(cuenta, "id", id);

        if (!activa) {
            cuenta.cambiarEstado(false, ACTOR_ANTERIOR, ANTES);
        }

        return cuenta;
    }

    private enum EstadoCuenta {
        NINGUNA, ACTIVA, INACTIVA
    }

    private enum Destino {
        NINGUNA, MISMA, OTRA_ACTIVA, OTRA_INACTIVA
    }

    private record CasoCuenta(EstadoCuenta actual, Destino destino, boolean aceptada) {

        CuentaCorporativa cuentaActual() {
            return switch (actual) {
                case NINGUNA -> null;
                case ACTIVA -> cuenta(CUENTA_ACTUAL_ID, true);
                case INACTIVA -> cuenta(CUENTA_ACTUAL_ID, false);
            };
        }

        @Override
        public String toString() {
            return "actual=" + actual + ", destino=" + destino
                    + ", aceptada=" + aceptada;
        }
    }
}
