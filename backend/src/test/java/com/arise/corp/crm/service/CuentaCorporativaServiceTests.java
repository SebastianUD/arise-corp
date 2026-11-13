package com.arise.corp.crm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.arise.corp.crm.GeneradoresCrm;
import com.arise.corp.crm.dto.CuentaCorporativaRequest;
import com.arise.corp.crm.dto.CuentaCorporativaResponse;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.CuentaCorporativa;
import com.arise.corp.crm.entity.TipoDocumento;
import com.arise.corp.crm.repository.CuentaCorporativaRepository;
import com.arise.corp.shared.error.ConflictoNegocioException;
import com.arise.corp.shared.error.RecursoNoEncontradoException;
import com.arise.corp.shared.paginacion.PaginaResponse;

/**
 * Pruebas del servicio de cuentas corporativas (CRM-02, CRM-04).
 *
 * <p>Mockito y {@link Clock#fixed} permiten comprobar la auditoria sin base de
 * datos. Property 7 usa los generadores con semilla fija de
 * {@link GeneradoresCrm} y Property 8 recorre las cuatro combinaciones de
 * estado.
 */
class CuentaCorporativaServiceTests {

    private static final int CASOS = 120;

    private static final UUID CUENTA_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTRA_CUENTA_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ACTOR =
            UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID ACTOR_ANTERIOR =
            UUID.fromString("44444444-4444-4444-8444-444444444444");

    private static final Instant ANTES = Instant.parse("2026-01-02T03:04:05.000001Z");
    private static final Instant AHORA = Instant.parse("2026-11-11T15:30:45.123456Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final String NIT_BASE = "9001234567";
    private static final String RAZON_ORIGINAL = "Cuenta original";

    private CuentaCorporativaRepository cuentas;
    private CuentaCorporativaService servicio;

    @BeforeEach
    void setUp() {
        cuentas = mock(CuentaCorporativaRepository.class);
        servicio = new CuentaCorporativaService(cuentas, RELOJ);

        when(cuentas.findByNit(any())).thenReturn(Optional.empty());
        when(cuentas.findById(any())).thenReturn(Optional.empty());
        when(cuentas.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(cuentas.save(any(CuentaCorporativa.class))).thenAnswer(invocacion -> {
            CuentaCorporativa cuenta = invocacion.getArgument(0);
            ReflectionTestUtils.setField(cuenta, "id", CUENTA_ID);
            return cuenta;
        });
    }

    @Test
    void creaUnaCuentaActivaConElNitNormalizadoYLaAuditoriaDelActor() {
        CuentaCorporativaResponse respuesta = servicio.crear(
                solicitud("900.123.456-7"), ACTOR.toString());

        assertEquals(CUENTA_ID, respuesta.id());
        assertEquals(NIT_BASE, respuesta.nit());
        assertEquals("Cuenta de prueba", respuesta.razonSocial());
        assertEquals("Contacto de prueba", respuesta.nombreContacto());
        assertEquals("contacto@example.test", respuesta.correoContacto());
        assertEquals("3001234567", respuesta.telefonoContacto());
        assertTrue(respuesta.activa());
        assertEquals(AHORA, respuesta.creadoEn());
        assertEquals(AHORA, respuesta.actualizadoEn());
        assertEquals(ACTOR, respuesta.actualizadoPor());
        verify(cuentas).save(any(CuentaCorporativa.class));
    }

    @Test
    void rechazaElNitDeOtraCuentaAlCrear() {
        when(cuentas.findByNit(NIT_BASE))
                .thenReturn(Optional.of(cuenta(OTRA_CUENTA_ID, true, ANTES, NIT_BASE)));

        ConflictoNegocioException error = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.crear(solicitud(NIT_BASE), ACTOR.toString()));

        assertEquals("CUENTA_NIT_DUPLICADO", error.getConflicto());
        assertEquals("nit", error.getCampo());
        verify(cuentas, never()).save(any(CuentaCorporativa.class));
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 7: Los duplicados se detectan sobre el valor normalizado")
    void detectaElNitDuplicadoAunqueCambieElFormato() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            String base = GeneradoresCrm.documentoBase(azar);
            String variante = GeneradoresCrm.varianteDocumento(azar, base);
            when(cuentas.findByNit(base))
                    .thenReturn(Optional.of(cuenta(OTRA_CUENTA_ID, true, ANTES, base)));

            ConflictoNegocioException error = assertThrows(
                    ConflictoNegocioException.class,
                    () -> servicio.crear(solicitud(variante), ACTOR.toString()),
                    "no detecto el duplicado del NIT base <" + base
                            + "> con la variante <" + variante + ">");

            assertEquals("CUENTA_NIT_DUPLICADO", error.getConflicto(),
                    "conflicto inesperado para <" + variante + ">");
            assertEquals("nit", error.getCampo(),
                    "campo inesperado para <" + variante + ">");
        }

        verify(cuentas, never()).save(any(CuentaCorporativa.class));
    }

    @Test
    void actualizaLosDatosYConservaActivaYCreadoEn() {
        CuentaCorporativa cuenta = cuenta(CUENTA_ID, true, ANTES, NIT_BASE);
        when(cuentas.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        CuentaCorporativaResponse respuesta = servicio.actualizar(
                CUENTA_ID,
                new CuentaCorporativaRequest(
                        "800.765.432-1",
                        "Razon nueva",
                        "Contacto nuevo",
                        "nuevo@example.test",
                        "3000000000"),
                ACTOR.toString());

        assertEquals("8007654321", respuesta.nit());
        assertEquals("Razon nueva", respuesta.razonSocial());
        assertTrue(respuesta.activa());
        assertEquals(ANTES, respuesta.creadoEn());
        assertEquals(AHORA, respuesta.actualizadoEn());
        assertEquals(ACTOR, respuesta.actualizadoPor());
    }

    @Test
    void conservaUnaCuentaInactivaEnLaEdicion() {
        CuentaCorporativa cuenta = cuenta(CUENTA_ID, false, ANTES, NIT_BASE);
        when(cuentas.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        CuentaCorporativaResponse respuesta = servicio.actualizar(
                CUENTA_ID, solicitud(NIT_BASE), ACTOR.toString());

        assertFalse(respuesta.activa());
        assertEquals(ANTES, respuesta.creadoEn());
        assertEquals(AHORA, respuesta.actualizadoEn());
    }

    @Test
    void rechazaElNitDeOtraCuentaAlEditar() {
        CuentaCorporativa cuenta = cuenta(CUENTA_ID, true, ANTES, NIT_BASE);
        when(cuentas.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(cuentas.findByNit("8007654321"))
                .thenReturn(Optional.of(cuenta(
                        OTRA_CUENTA_ID, true, ANTES, "8007654321")));

        ConflictoNegocioException error = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.actualizar(
                        CUENTA_ID, solicitud("800.765.432-1"), ACTOR.toString()));

        assertEquals("CUENTA_NIT_DUPLICADO", error.getConflicto());
        assertEquals(NIT_BASE, cuenta.getNit());
    }

    @Test
    void permiteConservarElPropioNitAlEditar() {
        CuentaCorporativa cuenta = cuenta(CUENTA_ID, true, ANTES, NIT_BASE);
        when(cuentas.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(cuentas.findByNit(NIT_BASE)).thenReturn(Optional.of(cuenta));

        CuentaCorporativaResponse respuesta = servicio.actualizar(
                CUENTA_ID, solicitud("900.123.456-7"), ACTOR.toString());

        assertEquals(NIT_BASE, respuesta.nit());
        assertEquals(AHORA, respuesta.actualizadoEn());
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 8: Activar y desactivar es idempotente")
    void cambiarEstadoEsIdempotenteYConservaLaAuditoriaSiCoincide() {
        for (boolean actual : new boolean[] {true, false}) {
            for (boolean solicitado : new boolean[] {true, false}) {
                CuentaCorporativa cuenta = cuenta(CUENTA_ID, actual, ANTES, NIT_BASE);
                when(cuentas.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));

                CuentaCorporativaResponse respuesta = servicio.cambiarEstado(
                        CUENTA_ID, solicitado, ACTOR.toString());

                assertEquals(solicitado, respuesta.activa(),
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

        verify(cuentas, never()).save(any(CuentaCorporativa.class));
    }

    @Test
    void desactivarUnaCuentaNoModificaSusClientes() {
        CuentaCorporativa cuenta = cuenta(CUENTA_ID, true, ANTES, NIT_BASE);
        Cliente cliente = Cliente.crear(
                TipoDocumento.CC,
                "10234567",
                "Cliente de la cuenta",
                "cliente@example.test",
                "3001234567",
                "Calle 1",
                "Bogota",
                cuenta,
                ACTOR_ANTERIOR,
                ANTES);
        when(cuentas.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        CuentaCorporativaResponse respuesta = servicio.cambiarEstado(
                CUENTA_ID, false, ACTOR.toString());

        assertFalse(respuesta.activa());
        assertTrue(cliente.isActivo(), "la desactivacion cambio el cliente");
        assertSame(cuenta, cliente.getCuentaCorporativa(),
                "la desactivacion quito la cuenta del cliente");
        assertEquals("10234567", cliente.getNumeroDocumento(),
                "la desactivacion cambio los datos del cliente");
        verify(cuentas, never()).save(any(CuentaCorporativa.class));
    }

    @Test
    void fallaSiLaCuentaNoExiste() {
        RecursoNoEncontradoException obtener = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.obtener(CUENTA_ID));
        assertEquals("La cuenta corporativa no existe.", obtener.getMessage());

        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.actualizar(
                        CUENTA_ID, solicitud(NIT_BASE), ACTOR.toString()));
        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.cambiarEstado(CUENTA_ID, false, ACTOR.toString()));
    }

    @Test
    void listarConvierteCadaCuentaYDevuelveLaPagina() {
        CuentaCorporativa cuenta = cuenta(CUENTA_ID, true, ANTES, NIT_BASE);
        CuentaCorporativa otra = cuenta(OTRA_CUENTA_ID, false, ANTES, "8007654321");
        when(cuentas.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cuenta, otra)));

        PaginaResponse<CuentaCorporativaResponse> pagina = servicio.listar(
                new FiltroCuentas("acme", true), PageRequest.of(0, 20));

        assertEquals(2, pagina.content().size());
        assertEquals(CUENTA_ID, pagina.content().get(0).id());
        assertEquals(OTRA_CUENTA_ID, pagina.content().get(1).id());
        assertEquals(2L, pagina.totalElements());
    }

    @Test
    void obtenerDevuelveLaCuentaExistente() {
        CuentaCorporativa cuenta = cuenta(CUENTA_ID, true, ANTES, NIT_BASE);
        when(cuentas.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        CuentaCorporativaResponse respuesta = servicio.obtener(CUENTA_ID);

        assertEquals(CUENTA_ID, respuesta.id());
        assertEquals(NIT_BASE, respuesta.nit());
    }

    private static CuentaCorporativaRequest solicitud(String nit) {
        return new CuentaCorporativaRequest(
                nit,
                "Cuenta de prueba",
                "Contacto de prueba",
                "contacto@example.test",
                "3001234567");
    }

    private static CuentaCorporativa cuenta(
            UUID id, boolean activa, Instant creadoEn, String nit) {

        CuentaCorporativa cuenta = CuentaCorporativa.crear(
                nit,
                RAZON_ORIGINAL,
                "Contacto original",
                "contacto@example.test",
                "3001234567",
                ACTOR_ANTERIOR,
                creadoEn);
        ReflectionTestUtils.setField(cuenta, "id", id);

        if (!activa) {
            cuenta.cambiarEstado(false, ACTOR_ANTERIOR, creadoEn);
        }

        return cuenta;
    }
}
