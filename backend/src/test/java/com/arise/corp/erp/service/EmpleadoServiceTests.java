package com.arise.corp.erp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.arise.corp.erp.dto.EmpleadoRequest;
import com.arise.corp.erp.dto.EmpleadoResponse;
import com.arise.corp.erp.entity.Empleado;
import com.arise.corp.erp.entity.TipoDocumentoEmpleado;
import com.arise.corp.erp.repository.EmpleadoRepository;
import com.arise.corp.shared.error.ConflictoNegocioException;
import com.arise.corp.shared.error.RecursoNoEncontradoException;

/**
 * Pruebas de {@link EmpleadoService} (Requirement 25.1, Properties 6, 7 y 8).
 *
 * <p>Mockito y {@code Clock.fixed}: comprueban la auditoria controlada por el
 * servidor, los duplicados sobre el documento normalizado, la idempotencia de
 * activar y desactivar y la independencia respecto de otros modulos.
 */
class EmpleadoServiceTests {

    private static final UUID ACTOR =
            UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final String ACTOR_SUB = ACTOR.toString();
    private static final Instant INSTANTE =
            Instant.parse("2026-11-20T15:04:05.123456Z");
    private static final Instant ANTERIOR = INSTANTE.minusSeconds(3600);
    private static final LocalDate FECHA_INGRESO = LocalDate.of(2025, 3, 1);

    private EmpleadoRepository empleados;
    private EmpleadoService servicio;

    @BeforeEach
    void setUp() {
        empleados = mock(EmpleadoRepository.class);
        servicio = new EmpleadoService(
                empleados, Clock.fixed(INSTANTE, ZoneOffset.UTC));

        when(empleados.save(any(Empleado.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 6: Los campos controlados por el servidor no se toman del cuerpo")
    void crearDejaElEmpleadoActivoConLaAuditoriaDelActor() {
        EmpleadoResponse creado = servicio.crear(solicitud("10234567"), ACTOR_SUB);

        assertEquals(TipoDocumentoEmpleado.CC, creado.tipoDocumento());
        assertEquals("10234567", creado.numeroDocumento());
        assertEquals("Ana Pérez", creado.nombre());
        assertTrue(creado.activo());
        assertEquals(INSTANTE, creado.creadoEn());
        assertEquals(INSTANTE, creado.actualizadoEn());
        assertEquals(ACTOR, creado.actualizadoPor());
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 7: Los duplicados se detectan sobre el documento normalizado")
    void crearRechazaUnEmpleadoActivoConElMismoDocumento() {
        when(empleados.findByTipoDocumentoAndNumeroDocumento(
                TipoDocumentoEmpleado.CC, "10234567"))
                .thenReturn(Optional.of(empleadoExistente(true)));

        ConflictoNegocioException conflicto = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.crear(solicitud("1.023.456-7"), ACTOR_SUB));

        assertEquals(ConflictosErp.EMPLEADO_DUPLICADO, conflicto.getConflicto());
        assertEquals("numeroDocumento", conflicto.getCampo());
        verify(empleados, never()).save(any(Empleado.class));
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 7: Los duplicados se detectan sobre el documento normalizado")
    void crearRechazaUnEmpleadoInactivoExistenteConSuIdentificador() {
        Empleado existente = empleadoExistente(false);
        when(empleados.findByTipoDocumentoAndNumeroDocumento(
                TipoDocumentoEmpleado.CC, "10234567"))
                .thenReturn(Optional.of(existente));

        ConflictoNegocioException conflicto = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.crear(solicitud("1.023.456-7"), ACTOR_SUB));

        assertEquals(ConflictosErp.EMPLEADO_INACTIVO_EXISTENTE,
                conflicto.getConflicto());
        assertEquals(existente.getId(),
                conflicto.getExtensiones().get("empleadoId"));
        verify(empleados, never()).save(any(Empleado.class));
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 6: Los campos controlados por el servidor no se toman del cuerpo")
    void actualizarConservaElEstadoYLaFechaDeCreacion() {
        Empleado existente = empleadoExistente(true);
        when(empleados.findById(existente.getId()))
                .thenReturn(Optional.of(existente));
        when(empleados.findByTipoDocumentoAndNumeroDocumento(
                TipoDocumentoEmpleado.CC, "10234567"))
                .thenReturn(Optional.of(existente));

        EmpleadoResponse respuesta = servicio.actualizar(
                existente.getId(), solicitud("10234567"), ACTOR_SUB);

        assertTrue(respuesta.activo());
        assertEquals(ANTERIOR, respuesta.creadoEn());
        assertEquals(INSTANTE, respuesta.actualizadoEn());
        assertEquals(ACTOR, respuesta.actualizadoPor());
        assertEquals("Ana Pérez", respuesta.nombre());
    }

    @Test
    void actualizarRechazaElDocumentoDeOtroEmpleado() {
        Empleado existente = empleadoExistente(true);
        Empleado otro = empleadoExistente(true);
        ReflectionTestUtils.setField(otro, "id", UUID.randomUUID());

        when(empleados.findById(existente.getId()))
                .thenReturn(Optional.of(existente));
        when(empleados.findByTipoDocumentoAndNumeroDocumento(
                TipoDocumentoEmpleado.CC, "10234567"))
                .thenReturn(Optional.of(otro));

        ConflictoNegocioException conflicto = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.actualizar(
                        existente.getId(), solicitud("10234567"), ACTOR_SUB));

        assertEquals(ConflictosErp.EMPLEADO_DUPLICADO, conflicto.getConflicto());
        assertEquals("Cargo original", existente.getCargo());
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 8: Activar y desactivar es idempotente")
    void cambiarEstadoAlMismoValorNoTocaLaAuditoria() {
        Empleado existente = empleadoExistente(true);
        when(empleados.findById(existente.getId()))
                .thenReturn(Optional.of(existente));

        EmpleadoResponse respuesta = servicio.cambiarEstado(
                existente.getId(), true, ACTOR_SUB);

        assertTrue(respuesta.activo());
        assertEquals(ANTERIOR, respuesta.actualizadoEn());
        assertEquals(ACTOR, respuesta.actualizadoPor());
    }

    @Test
    void cambiarEstadoDesactivaYActualizaLaAuditoria() {
        Empleado existente = empleadoExistente(true);
        when(empleados.findById(existente.getId()))
                .thenReturn(Optional.of(existente));

        EmpleadoResponse respuesta = servicio.cambiarEstado(
                existente.getId(), false, ACTOR_SUB);

        assertEquals(false, respuesta.activo());
        assertEquals(INSTANTE, respuesta.actualizadoEn());
        assertEquals(ACTOR, respuesta.actualizadoPor());
    }

    @Test
    void obtenerUnEmpleadoInexistenteDevuelve404() {
        UUID id = UUID.randomUUID();
        when(empleados.findById(id)).thenReturn(Optional.empty());

        RecursoNoEncontradoException error = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.obtener(id));

        assertEquals("El empleado no existe.", error.getMessage());
    }

    @Test
    void unDocumentoDeOtroModuloNoImpideRegistrarElEmpleado() {
        when(empleados.findByTipoDocumentoAndNumeroDocumento(
                TipoDocumentoEmpleado.CC, "10234567"))
                .thenReturn(Optional.empty());

        EmpleadoResponse creado = servicio.crear(solicitud("10234567"), ACTOR_SUB);

        assertEquals("10234567", creado.numeroDocumento());
        verify(empleados).save(any(Empleado.class));
    }

    private Empleado empleadoExistente(boolean activo) {
        Empleado empleado = Empleado.crear(
                TipoDocumentoEmpleado.CC,
                "10234567",
                "Ana Pérez",
                "Cargo original",
                "Finanzas",
                "ana@example.test",
                "+57 300 000 0000",
                FECHA_INGRESO,
                ACTOR,
                ANTERIOR);

        if (!activo) {
            empleado.cambiarEstado(false, ACTOR, ANTERIOR);
        }

        ReflectionTestUtils.setField(empleado, "id", UUID.randomUUID());
        return empleado;
    }

    private static EmpleadoRequest solicitud(String documento) {
        return new EmpleadoRequest(
                TipoDocumentoEmpleado.CC,
                documento,
                " Ana Pérez ",
                " Analista ",
                " Finanzas ",
                "ana@example.test",
                "+57 300 000 0000",
                FECHA_INGRESO);
    }
}
