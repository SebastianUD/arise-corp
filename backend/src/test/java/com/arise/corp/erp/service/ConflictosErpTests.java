package com.arise.corp.erp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.shared.error.ConflictoNegocioException;

/**
 * Catalogo de conflictos de negocio del ERP (D5).
 */
class ConflictosErpTests {

    private static final UUID EMPLEADO_ID =
            UUID.fromString("7b2d8e3f-0000-4000-8000-000000000001");
    private static final UUID PROVEEDOR_ID =
            UUID.fromString("7b2d8e3f-0000-4000-8000-000000000002");

    @Test
    void describeCadaFilaDelCatalogo() {
        verificar(
                ConflictosErp.empleadoDuplicado(),
                "EMPLEADO_DUPLICADO",
                "numeroDocumento",
                "Ya existe un empleado registrado con este tipo y número de documento.",
                Map.of());

        verificar(
                ConflictosErp.empleadoInactivoExistente(EMPLEADO_ID),
                "EMPLEADO_INACTIVO_EXISTENTE",
                "numeroDocumento",
                "El empleado ya existe, pero está inactivo. "
                        + "Reactívalo en lugar de registrarlo de nuevo.",
                Map.of("empleadoId", EMPLEADO_ID));

        verificar(
                ConflictosErp.proveedorDuplicado(),
                "PROVEEDOR_DUPLICADO",
                "numeroDocumento",
                "Ya existe un proveedor registrado con este tipo y número de documento.",
                Map.of());

        verificar(
                ConflictosErp.proveedorInactivoExistente(PROVEEDOR_ID),
                "PROVEEDOR_INACTIVO_EXISTENTE",
                "numeroDocumento",
                "El proveedor ya existe, pero está inactivo. "
                        + "Reactívalo en lugar de registrarlo de nuevo.",
                Map.of("proveedorId", PROVEEDOR_ID));

        verificar(
                ConflictosErp.clienteInactivo(),
                "CLIENTE_INACTIVO",
                "clienteId",
                "El cliente está inactivo y no puede asociarse a un movimiento.",
                Map.of());

        verificar(
                ConflictosErp.proveedorInactivo(),
                "PROVEEDOR_INACTIVO",
                "proveedorId",
                "El proveedor está inactivo y no puede asociarse a un movimiento.",
                Map.of());

        verificar(
                ConflictosErp.movimientoNoEditable(EstadoMovimiento.PAGADO),
                "MOVIMIENTO_NO_EDITABLE",
                null,
                "Solo se pueden editar movimientos en estado PENDIENTE. "
                        + "Estado actual: PAGADO.",
                Map.of("estadoActual", "PAGADO"));

        verificar(
                ConflictosErp.transicionNoPermitida(
                        EstadoMovimiento.PAGADO, EstadoMovimiento.ANULADO),
                "TRANSICION_NO_PERMITIDA",
                "estado",
                "No se puede cambiar el movimiento de PAGADO a ANULADO.",
                Map.of(
                        "estadoActual", "PAGADO",
                        "estadoSolicitado", "ANULADO"));
    }

    @Test
    void usaCodigosEstablesEnMayusculasYSinDatosPersonales() {
        List<ConflictoNegocioException> conflictos = List.of(
                ConflictosErp.empleadoDuplicado(),
                ConflictosErp.empleadoInactivoExistente(EMPLEADO_ID),
                ConflictosErp.proveedorDuplicado(),
                ConflictosErp.proveedorInactivoExistente(PROVEEDOR_ID),
                ConflictosErp.clienteInactivo(),
                ConflictosErp.proveedorInactivo(),
                ConflictosErp.movimientoNoEditable(EstadoMovimiento.ANULADO),
                ConflictosErp.transicionNoPermitida(
                        EstadoMovimiento.PENDIENTE, EstadoMovimiento.PENDIENTE));

        assertEquals(
                8,
                conflictos.stream()
                        .map(ConflictoNegocioException::getConflicto)
                        .distinct()
                        .count());

        for (ConflictoNegocioException conflicto : conflictos) {
            String codigo = conflicto.getConflicto();
            assertTrue(codigo.matches("[A-Z][A-Z_]*"), codigo);
            assertFalse(conflicto.getMessage().isBlank(), codigo);
            assertFalse(conflicto.getMessage().contains("@"), codigo);
            assertFalse(conflicto.getMessage().contains("10234567"), codigo);
            assertFalse(conflicto.getMessage().contains("Proveedor ficticio"), codigo);
        }
    }

    @Test
    void exigeElIdentificadorYLosEstadosDeLasExtensiones() {
        assertThrows(NullPointerException.class,
                () -> ConflictosErp.empleadoInactivoExistente(null));
        assertThrows(NullPointerException.class,
                () -> ConflictosErp.proveedorInactivoExistente(null));
        assertThrows(NullPointerException.class,
                () -> ConflictosErp.movimientoNoEditable(null));
        assertThrows(NullPointerException.class,
                () -> ConflictosErp.transicionNoPermitida(null, EstadoMovimiento.PAGADO));
        assertThrows(NullPointerException.class,
                () -> ConflictosErp.transicionNoPermitida(EstadoMovimiento.PENDIENTE, null));
    }

    private static void verificar(
            ConflictoNegocioException conflicto,
            String codigo,
            String campo,
            String detalle,
            Map<String, Object> extensiones) {

        assertEquals(codigo, conflicto.getConflicto());
        assertEquals(campo, conflicto.getCampo());
        assertEquals(detalle, conflicto.getMessage());
        assertEquals(extensiones, conflicto.getExtensiones());
    }
}
