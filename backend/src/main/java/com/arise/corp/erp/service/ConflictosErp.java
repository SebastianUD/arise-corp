package com.arise.corp.erp.service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.shared.error.ConflictoNegocioException;

/**
 * Fabrica de los conflictos de negocio del ERP (D5).
 *
 * <p>Una fabrica por cada fila del catalogo de "Error Handling" del diseno. Los
 * codigos son estables y estan en {@code UPPER_SNAKE_CASE}; los detalles ya
 * estan redactados para el usuario y nunca incluyen documentos, nombres ni
 * datos de contacto (Requirement 8.4). El {@code campo} y las extensiones
 * acompanan al Problem Details 409.
 */
public final class ConflictosErp {

    public static final String EMPLEADO_DUPLICADO = "EMPLEADO_DUPLICADO";
    public static final String EMPLEADO_INACTIVO_EXISTENTE = "EMPLEADO_INACTIVO_EXISTENTE";
    public static final String PROVEEDOR_DUPLICADO = "PROVEEDOR_DUPLICADO";
    public static final String PROVEEDOR_INACTIVO_EXISTENTE = "PROVEEDOR_INACTIVO_EXISTENTE";
    public static final String CLIENTE_INACTIVO = "CLIENTE_INACTIVO";
    public static final String PROVEEDOR_INACTIVO = "PROVEEDOR_INACTIVO";
    public static final String MOVIMIENTO_NO_EDITABLE = "MOVIMIENTO_NO_EDITABLE";
    public static final String TRANSICION_NO_PERMITIDA = "TRANSICION_NO_PERMITIDA";

    private static final String CAMPO_NUMERO_DOCUMENTO = "numeroDocumento";
    private static final String CAMPO_CLIENTE = "clienteId";
    private static final String CAMPO_PROVEEDOR = "proveedorId";
    private static final String CAMPO_ESTADO = "estado";

    private ConflictosErp() {
    }

    public static ConflictoNegocioException empleadoDuplicado() {
        return new ConflictoNegocioException(
                EMPLEADO_DUPLICADO,
                "Ya existe un empleado registrado con este tipo y número de documento.",
                CAMPO_NUMERO_DOCUMENTO,
                Map.of());
    }

    public static ConflictoNegocioException empleadoInactivoExistente(UUID empleadoId) {
        return new ConflictoNegocioException(
                EMPLEADO_INACTIVO_EXISTENTE,
                "El empleado ya existe, pero está inactivo. "
                        + "Reactívalo en lugar de registrarlo de nuevo.",
                CAMPO_NUMERO_DOCUMENTO,
                Map.of("empleadoId", Objects.requireNonNull(empleadoId, "empleadoId")));
    }

    public static ConflictoNegocioException proveedorDuplicado() {
        return new ConflictoNegocioException(
                PROVEEDOR_DUPLICADO,
                "Ya existe un proveedor registrado con este tipo y número de documento.",
                CAMPO_NUMERO_DOCUMENTO,
                Map.of());
    }

    public static ConflictoNegocioException proveedorInactivoExistente(UUID proveedorId) {
        return new ConflictoNegocioException(
                PROVEEDOR_INACTIVO_EXISTENTE,
                "El proveedor ya existe, pero está inactivo. "
                        + "Reactívalo en lugar de registrarlo de nuevo.",
                CAMPO_NUMERO_DOCUMENTO,
                Map.of(CAMPO_PROVEEDOR, Objects.requireNonNull(proveedorId, CAMPO_PROVEEDOR)));
    }

    public static ConflictoNegocioException clienteInactivo() {
        return new ConflictoNegocioException(
                CLIENTE_INACTIVO,
                "El cliente está inactivo y no puede asociarse a un movimiento.",
                CAMPO_CLIENTE,
                Map.of());
    }

    public static ConflictoNegocioException proveedorInactivo() {
        return new ConflictoNegocioException(
                PROVEEDOR_INACTIVO,
                "El proveedor está inactivo y no puede asociarse a un movimiento.",
                CAMPO_PROVEEDOR,
                Map.of());
    }

    public static ConflictoNegocioException movimientoNoEditable(
            EstadoMovimiento estadoActual) {

        String estado = Objects.requireNonNull(estadoActual, "estadoActual").name();

        return new ConflictoNegocioException(
                MOVIMIENTO_NO_EDITABLE,
                "Solo se pueden editar movimientos en estado PENDIENTE. "
                        + "Estado actual: " + estado + ".",
                null,
                Map.of("estadoActual", estado));
    }

    public static ConflictoNegocioException transicionNoPermitida(
            EstadoMovimiento estadoActual,
            EstadoMovimiento estadoSolicitado) {

        String actual = Objects.requireNonNull(estadoActual, "estadoActual").name();
        String solicitado = Objects.requireNonNull(
                estadoSolicitado, "estadoSolicitado").name();

        return new ConflictoNegocioException(
                TRANSICION_NO_PERMITIDA,
                "No se puede cambiar el movimiento de " + actual + " a " + solicitado + ".",
                CAMPO_ESTADO,
                Map.of(
                        "estadoActual", actual,
                        "estadoSolicitado", solicitado));
    }
}
