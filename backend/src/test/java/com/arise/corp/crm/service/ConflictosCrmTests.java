package com.arise.corp.crm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.shared.error.ConflictoNegocioException;

/**
 * Catalogo de conflictos de negocio del CRM (D5).
 */
class ConflictosCrmTests {

    private static final UUID CLIENTE_ID =
            UUID.fromString("3f2c9a4e-0000-4000-8000-000000000001");

    @Test
    void describeCadaFilaDelCatalogo() {
        verificar(
                ConflictosCrm.clienteDuplicado(),
                "CLIENTE_DUPLICADO",
                "numeroDocumento",
                "Ya existe un cliente registrado con este tipo y número de documento.",
                Map.of());

        verificar(
                ConflictosCrm.clienteInactivoExistente(CLIENTE_ID),
                "CLIENTE_INACTIVO_EXISTENTE",
                "numeroDocumento",
                "El cliente ya existe, pero está inactivo. "
                        + "Reactívalo en lugar de registrarlo de nuevo.",
                Map.of("clienteId", CLIENTE_ID));

        verificar(
                ConflictosCrm.cuentaNitDuplicado(),
                "CUENTA_NIT_DUPLICADO",
                "nit",
                "Ya existe una cuenta corporativa con este NIT.",
                Map.of());

        verificar(
                ConflictosCrm.cuentaInactiva(),
                "CUENTA_INACTIVA",
                "cuentaCorporativaId",
                "La cuenta corporativa está inactiva y no puede asignarse a clientes.",
                Map.of());

        verificar(
                ConflictosCrm.clienteInactivoSinContratos(),
                "CLIENTE_INACTIVO_SIN_CONTRATOS",
                "clienteId",
                "El cliente está inactivo y no admite contratos nuevos.",
                Map.of());

        verificar(
                ConflictosCrm.contratoCodigoDuplicado(),
                "CONTRATO_CODIGO_DUPLICADO",
                "codigo",
                "Ya existe un contrato con este código.",
                Map.of());

        verificar(
                ConflictosCrm.contratoNoEditable(EstadoContrato.ACTIVO),
                "CONTRATO_NO_EDITABLE",
                null,
                "Solo se pueden editar contratos en estado BORRADOR. Estado actual: ACTIVO.",
                Map.of("estadoActual", "ACTIVO"));

        verificar(
                ConflictosCrm.transicionNoPermitida(
                        EstadoContrato.BORRADOR, EstadoContrato.FINALIZADO),
                "TRANSICION_NO_PERMITIDA",
                "estado",
                "No se puede cambiar el contrato de BORRADOR a FINALIZADO.",
                Map.of(
                        "estadoActual", "BORRADOR",
                        "estadoSolicitado", "FINALIZADO"));
    }

    @Test
    void usaCodigosEstablesEnMayusculasYSinDatosPersonales() {
        List<ConflictoNegocioException> conflictos = List.of(
                ConflictosCrm.clienteDuplicado(),
                ConflictosCrm.clienteInactivoExistente(CLIENTE_ID),
                ConflictosCrm.cuentaNitDuplicado(),
                ConflictosCrm.cuentaInactiva(),
                ConflictosCrm.clienteInactivoSinContratos(),
                ConflictosCrm.contratoCodigoDuplicado(),
                ConflictosCrm.contratoNoEditable(EstadoContrato.CANCELADO),
                ConflictosCrm.transicionNoPermitida(
                        EstadoContrato.ACTIVO, EstadoContrato.BORRADOR));

        assertEquals(8, conflictos.stream().map(ConflictoNegocioException::getConflicto).distinct().count());

        for (ConflictoNegocioException conflicto : conflictos) {
            String codigo = conflicto.getConflicto();
            assertTrue(codigo.matches("[A-Z][A-Z_]*"), codigo);
            assertFalse(conflicto.getMessage().isBlank(), codigo);
            assertFalse(conflicto.getMessage().contains("@"), codigo);
            assertFalse(conflicto.getMessage().contains("10234567"), codigo);
            assertFalse(conflicto.getMessage().contains("Ana Uno"), codigo);
        }
    }

    @Test
    void exigeElIdentificadorYLosEstadosDeLasExtensiones() {
        assertThrowsNullPointer(() -> ConflictosCrm.clienteInactivoExistente(null));
        assertThrowsNullPointer(() -> ConflictosCrm.contratoNoEditable(null));
        assertThrowsNullPointer(() -> ConflictosCrm.transicionNoPermitida(null, EstadoContrato.ACTIVO));
        assertThrowsNullPointer(() -> ConflictosCrm.transicionNoPermitida(EstadoContrato.ACTIVO, null));
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

    private static void assertThrowsNullPointer(Runnable accion) {
        assertThrows(NullPointerException.class, accion::run);
    }
}
