package com.arise.corp.erp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.arise.corp.erp.dto.ProveedorRequest;
import com.arise.corp.erp.dto.ProveedorResponse;
import com.arise.corp.erp.entity.Proveedor;
import com.arise.corp.erp.entity.TipoDocumentoProveedor;
import com.arise.corp.erp.repository.MovimientoFinancieroRepository;
import com.arise.corp.erp.repository.ProveedorRepository;
import com.arise.corp.shared.error.ConflictoNegocioException;
import com.arise.corp.shared.error.RecursoNoEncontradoException;

/**
 * Pruebas de {@link ProveedorService} (Requirement 25.1, Properties 7, 8 y 11).
 *
 * <p>Mockito y {@code Clock.fixed}: duplicados sobre el documento normalizado,
 * idempotencia de activar y desactivar, opcionales omitidos y desactivacion sin
 * interaccion con los movimientos financieros.
 */
class ProveedorServiceTests {

    private static final UUID ACTOR =
            UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final String ACTOR_SUB = ACTOR.toString();
    private static final Instant INSTANTE =
            Instant.parse("2026-11-20T15:04:05.123456Z");
    private static final Instant ANTERIOR = INSTANTE.minusSeconds(3600);

    private ProveedorRepository proveedores;
    private MovimientoFinancieroRepository movimientos;
    private ProveedorService servicio;

    @BeforeEach
    void setUp() {
        proveedores = mock(ProveedorRepository.class);
        movimientos = mock(MovimientoFinancieroRepository.class);
        servicio = new ProveedorService(
                proveedores, Clock.fixed(INSTANTE, ZoneOffset.UTC));

        when(proveedores.save(any(Proveedor.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 6: Los campos controlados por el servidor no se toman del cuerpo")
    void crearDejaElProveedorActivoConLaAuditoriaDelActor() {
        ProveedorResponse creado = servicio.crear(
                solicitud("900765432", "Contacto", "Calle 1"), ACTOR_SUB);

        assertEquals(TipoDocumentoProveedor.NIT, creado.tipoDocumento());
        assertEquals("900765432", creado.numeroDocumento());
        assertEquals("Contacto", creado.nombreContacto());
        assertEquals("Calle 1", creado.direccion());
        assertTrue(creado.activo());
        assertEquals(INSTANTE, creado.creadoEn());
        assertEquals(INSTANTE, creado.actualizadoEn());
        assertEquals(ACTOR, creado.actualizadoPor());
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 7: Los duplicados se detectan sobre el documento normalizado")
    void crearRechazaUnProveedorActivoConElMismoDocumento() {
        when(proveedores.findByTipoDocumentoAndNumeroDocumento(
                TipoDocumentoProveedor.NIT, "900765432"))
                .thenReturn(Optional.of(proveedorExistente(true)));

        ConflictoNegocioException conflicto = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.crear(
                        solicitud("900.765.432", null, null), ACTOR_SUB));

        assertEquals(ConflictosErp.PROVEEDOR_DUPLICADO, conflicto.getConflicto());
        assertEquals("numeroDocumento", conflicto.getCampo());
        verify(proveedores, never()).save(any(Proveedor.class));
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 7: Los duplicados se detectan sobre el documento normalizado")
    void crearRechazaUnProveedorInactivoExistenteConSuIdentificador() {
        Proveedor existente = proveedorExistente(false);
        when(proveedores.findByTipoDocumentoAndNumeroDocumento(
                TipoDocumentoProveedor.NIT, "900765432"))
                .thenReturn(Optional.of(existente));

        ConflictoNegocioException conflicto = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.crear(
                        solicitud("900.765.432", null, null), ACTOR_SUB));

        assertEquals(ConflictosErp.PROVEEDOR_INACTIVO_EXISTENTE,
                conflicto.getConflicto());
        assertEquals(existente.getId(),
                conflicto.getExtensiones().get("proveedorId"));
        verify(proveedores, never()).save(any(Proveedor.class));
    }

    @Test
    void actualizarDejaNulosLosOpcionalesOmitidosYConservaElEstado() {
        Proveedor existente = proveedorExistente(true);
        when(proveedores.findById(existente.getId()))
                .thenReturn(Optional.of(existente));
        when(proveedores.findByTipoDocumentoAndNumeroDocumento(
                TipoDocumentoProveedor.NIT, "900765432"))
                .thenReturn(Optional.of(existente));

        ProveedorResponse respuesta = servicio.actualizar(
                existente.getId(),
                solicitud("900765432", "   ", ""),
                ACTOR_SUB);

        assertNull(respuesta.nombreContacto());
        assertNull(respuesta.direccion());
        assertEquals("Bogotá", respuesta.ciudad());
        assertTrue(respuesta.activo());
        assertEquals(ANTERIOR, respuesta.creadoEn());
        assertEquals(INSTANTE, respuesta.actualizadoEn());
        assertEquals(ACTOR, respuesta.actualizadoPor());
    }

    @Test
    void actualizarRechazaElDocumentoDeOtroProveedor() {
        Proveedor existente = proveedorExistente(true);
        Proveedor otro = proveedorExistente(true);
        ReflectionTestUtils.setField(otro, "id", UUID.randomUUID());

        when(proveedores.findById(existente.getId()))
                .thenReturn(Optional.of(existente));
        when(proveedores.findByTipoDocumentoAndNumeroDocumento(
                TipoDocumentoProveedor.NIT, "900765432"))
                .thenReturn(Optional.of(otro));

        ConflictoNegocioException conflicto = assertThrows(
                ConflictoNegocioException.class,
                () -> servicio.actualizar(
                        existente.getId(), solicitud("900765432", null, null),
                        ACTOR_SUB));

        assertEquals(ConflictosErp.PROVEEDOR_DUPLICADO, conflicto.getConflicto());
        assertEquals("Proveedor original", existente.getNombre());
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 8: Activar y desactivar es idempotente")
    void cambiarEstadoAlMismoValorNoTocaLaAuditoria() {
        Proveedor existente = proveedorExistente(true);
        when(proveedores.findById(existente.getId()))
                .thenReturn(Optional.of(existente));

        ProveedorResponse respuesta = servicio.cambiarEstado(
                existente.getId(), true, ACTOR_SUB);

        assertTrue(respuesta.activo());
        assertEquals(ANTERIOR, respuesta.actualizadoEn());
        assertEquals(ACTOR, respuesta.actualizadoPor());
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 11: Desactivar un cliente o un proveedor conserva sus movimientos")
    void desactivarNoInteractuaConLosMovimientos() {
        Proveedor existente = proveedorExistente(true);
        when(proveedores.findById(existente.getId()))
                .thenReturn(Optional.of(existente));

        ProveedorResponse respuesta = servicio.cambiarEstado(
                existente.getId(), false, ACTOR_SUB);

        assertFalse(respuesta.activo());
        assertEquals(INSTANTE, respuesta.actualizadoEn());
        verifyNoInteractions(movimientos);
    }

    @Test
    void obtenerUnProveedorInexistenteDevuelve404() {
        UUID id = UUID.randomUUID();
        when(proveedores.findById(id)).thenReturn(Optional.empty());

        RecursoNoEncontradoException error = assertThrows(
                RecursoNoEncontradoException.class,
                () -> servicio.obtener(id));

        assertEquals("El proveedor no existe.", error.getMessage());
    }

    private Proveedor proveedorExistente(boolean activo) {
        Proveedor proveedor = Proveedor.crear(
                TipoDocumentoProveedor.NIT,
                "900765432",
                "Proveedor original",
                "Contacto original",
                "proveedor@example.test",
                "601 000 0000",
                "Calle original 1",
                "Bogotá",
                ACTOR,
                ANTERIOR);

        if (!activo) {
            proveedor.cambiarEstado(false, ACTOR, ANTERIOR);
        }

        ReflectionTestUtils.setField(proveedor, "id", UUID.randomUUID());
        return proveedor;
    }

    private static ProveedorRequest solicitud(
            String documento,
            String nombreContacto,
            String direccion) {

        return new ProveedorRequest(
                TipoDocumentoProveedor.NIT,
                documento,
                " Proveedor Ejemplo S.A.S. ",
                nombreContacto,
                "proveedor@example.test",
                "601 000 0000",
                direccion,
                " Bogotá ");
    }
}
