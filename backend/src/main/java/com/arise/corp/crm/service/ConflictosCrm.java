package com.arise.corp.crm.service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.shared.error.ConflictoNegocioException;

/**
 * Fabrica de los conflictos de negocio del CRM (D5).
 *
 * <p>Una fabrica por cada fila del catalogo de "Error Handling" del diseno. Los
 * codigos son estables y estan en {@code UPPER_SNAKE_CASE}; los detalles ya
 * estan redactados para el usuario y nunca incluyen documentos, nombres ni
 * datos de contacto. El {@code campo} y las extensiones acompanan al Problem
 * Details 409.
 */
public final class ConflictosCrm {

    public static final String CLIENTE_DUPLICADO = "CLIENTE_DUPLICADO";
    public static final String CLIENTE_INACTIVO_EXISTENTE = "CLIENTE_INACTIVO_EXISTENTE";
    public static final String CUENTA_NIT_DUPLICADO = "CUENTA_NIT_DUPLICADO";
    public static final String CUENTA_INACTIVA = "CUENTA_INACTIVA";
    public static final String CLIENTE_INACTIVO_SIN_CONTRATOS = "CLIENTE_INACTIVO_SIN_CONTRATOS";
    public static final String CONTRATO_CODIGO_DUPLICADO = "CONTRATO_CODIGO_DUPLICADO";
    public static final String CONTRATO_NO_EDITABLE = "CONTRATO_NO_EDITABLE";
    public static final String TRANSICION_NO_PERMITIDA = "TRANSICION_NO_PERMITIDA";

    private static final String CAMPO_NUMERO_DOCUMENTO = "numeroDocumento";
    private static final String CAMPO_NIT = "nit";
    private static final String CAMPO_CUENTA = "cuentaCorporativaId";
    private static final String CAMPO_CLIENTE = "clienteId";
    private static final String CAMPO_CODIGO = "codigo";
    private static final String CAMPO_ESTADO = "estado";

    private ConflictosCrm() {
    }

    public static ConflictoNegocioException clienteDuplicado() {
        return new ConflictoNegocioException(
                CLIENTE_DUPLICADO,
                "Ya existe un cliente registrado con este tipo y número de documento.",
                CAMPO_NUMERO_DOCUMENTO,
                Map.of());
    }

    public static ConflictoNegocioException clienteInactivoExistente(UUID clienteId) {
        return new ConflictoNegocioException(
                CLIENTE_INACTIVO_EXISTENTE,
                "El cliente ya existe, pero está inactivo. Reactívalo en lugar de registrarlo de nuevo.",
                CAMPO_NUMERO_DOCUMENTO,
                Map.of(CAMPO_CLIENTE, Objects.requireNonNull(clienteId, CAMPO_CLIENTE)));
    }

    public static ConflictoNegocioException cuentaNitDuplicado() {
        return new ConflictoNegocioException(
                CUENTA_NIT_DUPLICADO,
                "Ya existe una cuenta corporativa con este NIT.",
                CAMPO_NIT,
                Map.of());
    }

    public static ConflictoNegocioException cuentaInactiva() {
        return new ConflictoNegocioException(
                CUENTA_INACTIVA,
                "La cuenta corporativa está inactiva y no puede asignarse a clientes.",
                CAMPO_CUENTA,
                Map.of());
    }

    public static ConflictoNegocioException clienteInactivoSinContratos() {
        return new ConflictoNegocioException(
                CLIENTE_INACTIVO_SIN_CONTRATOS,
                "El cliente está inactivo y no admite contratos nuevos.",
                CAMPO_CLIENTE,
                Map.of());
    }

    public static ConflictoNegocioException contratoCodigoDuplicado() {
        return new ConflictoNegocioException(
                CONTRATO_CODIGO_DUPLICADO,
                "Ya existe un contrato con este código.",
                CAMPO_CODIGO,
                Map.of());
    }

    public static ConflictoNegocioException contratoNoEditable(EstadoContrato estadoActual) {
        String estado = Objects.requireNonNull(estadoActual, "estadoActual").name();

        return new ConflictoNegocioException(
                CONTRATO_NO_EDITABLE,
                "Solo se pueden editar contratos en estado BORRADOR. Estado actual: " + estado + ".",
                null,
                Map.of("estadoActual", estado));
    }

    public static ConflictoNegocioException transicionNoPermitida(
            EstadoContrato estadoActual,
            EstadoContrato estadoSolicitado) {

        String actual = Objects.requireNonNull(estadoActual, "estadoActual").name();
        String solicitado = Objects.requireNonNull(estadoSolicitado, "estadoSolicitado").name();

        return new ConflictoNegocioException(
                TRANSICION_NO_PERMITIDA,
                "No se puede cambiar el contrato de " + actual + " a " + solicitado + ".",
                CAMPO_ESTADO,
                Map.of(
                        "estadoActual", actual,
                        "estadoSolicitado", solicitado));
    }
}
