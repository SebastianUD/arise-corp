package com.arise.corp.sga.service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.arise.corp.sga.entity.EstadoDron;
import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.shared.error.ConflictoNegocioException;

/**
 * Fabrica de los conflictos de negocio del SGA (D5, "Error Handling").
 *
 * <p>Una fabrica por cada fila del catalogo de conflictos. Los codigos son
 * estables y estan en {@code UPPER_SNAKE_CASE}; los detalles ya estan
 * redactados para el usuario y solo contienen estados, codigos y cantidades:
 * nunca direcciones, observaciones ni nombres (Requirement 17.4). El
 * {@code campo} y las extensiones acompanan al Problem Details 409.
 *
 * <p>El SGA declara sus propias constantes, tambien
 * {@code TRANSICION_NO_PERMITIDA}, sin importar las del ERP ni del CRM
 * (Requirement 1.5).
 */
public final class ConflictosSga {

    public static final String DRON_CODIGO_DUPLICADO = "DRON_CODIGO_DUPLICADO";
    public static final String PAQUETE_CODIGO_DUPLICADO = "PAQUETE_CODIGO_DUPLICADO";
    public static final String ARTICULO_CODIGO_DUPLICADO = "ARTICULO_CODIGO_DUPLICADO";
    public static final String CLIENTE_INACTIVO = "CLIENTE_INACTIVO";
    public static final String CONTRATO_DE_OTRO_CLIENTE = "CONTRATO_DE_OTRO_CLIENTE";
    public static final String CONTRATO_NO_ACTIVO = "CONTRATO_NO_ACTIVO";
    public static final String PAQUETE_NO_EDITABLE = "PAQUETE_NO_EDITABLE";
    public static final String TRANSICION_NO_PERMITIDA = "TRANSICION_NO_PERMITIDA";
    public static final String DRON_NO_DISPONIBLE = "DRON_NO_DISPONIBLE";
    public static final String DRON_CON_PAQUETE_EN_CURSO = "DRON_CON_PAQUETE_EN_CURSO";
    public static final String TRANSICION_DRON_NO_PERMITIDA =
            "TRANSICION_DRON_NO_PERMITIDA";
    public static final String EXISTENCIA_INSUFICIENTE = "EXISTENCIA_INSUFICIENTE";

    private static final String CAMPO_CODIGO = "codigo";
    private static final String CAMPO_CLIENTE = "clienteId";
    private static final String CAMPO_CONTRATO = "contratoId";
    private static final String CAMPO_ESTADO = "estado";
    private static final String CAMPO_DRON = "dronId";
    private static final String CAMPO_CANTIDAD = "cantidad";

    private ConflictosSga() {
    }

    public static ConflictoNegocioException dronCodigoDuplicado() {
        return new ConflictoNegocioException(
                DRON_CODIGO_DUPLICADO,
                "Ya existe un dron registrado con este código.",
                CAMPO_CODIGO,
                Map.of());
    }

    public static ConflictoNegocioException paqueteCodigoDuplicado() {
        return new ConflictoNegocioException(
                PAQUETE_CODIGO_DUPLICADO,
                "Ya existe un paquete registrado con este código.",
                CAMPO_CODIGO,
                Map.of());
    }

    public static ConflictoNegocioException articuloCodigoDuplicado() {
        return new ConflictoNegocioException(
                ARTICULO_CODIGO_DUPLICADO,
                "Ya existe un artículo registrado con este código.",
                CAMPO_CODIGO,
                Map.of());
    }

    public static ConflictoNegocioException clienteInactivo() {
        return new ConflictoNegocioException(
                CLIENTE_INACTIVO,
                "El cliente está inactivo y no puede asociarse a un paquete.",
                CAMPO_CLIENTE,
                Map.of());
    }

    public static ConflictoNegocioException contratoDeOtroCliente() {
        return new ConflictoNegocioException(
                CONTRATO_DE_OTRO_CLIENTE,
                "El contrato indicado no pertenece al cliente del paquete.",
                CAMPO_CONTRATO,
                Map.of());
    }

    public static ConflictoNegocioException contratoNoActivo(String estado) {
        String actual = Objects.requireNonNull(estado, "estado");

        return new ConflictoNegocioException(
                CONTRATO_NO_ACTIVO,
                "Solo se pueden asociar contratos en estado ACTIVO. "
                        + "Estado actual: " + actual + ".",
                CAMPO_CONTRATO,
                Map.of("estadoActual", actual));
    }

    public static ConflictoNegocioException paqueteNoEditable(
            EstadoPaquete estadoActual) {

        String estado = Objects.requireNonNull(estadoActual, "estadoActual").name();

        return new ConflictoNegocioException(
                PAQUETE_NO_EDITABLE,
                "Solo se pueden editar paquetes en estado REGISTRADO o ASIGNADO. "
                        + "Estado actual: " + estado + ".",
                null,
                Map.of("estadoActual", estado));
    }

    public static ConflictoNegocioException transicionNoPermitida(
            EstadoPaquete estadoActual,
            EstadoPaquete estadoSolicitado) {

        String actual = Objects.requireNonNull(estadoActual, "estadoActual").name();
        String solicitado = Objects.requireNonNull(
                estadoSolicitado, "estadoSolicitado").name();

        return new ConflictoNegocioException(
                TRANSICION_NO_PERMITIDA,
                "No se puede cambiar el paquete de " + actual + " a " + solicitado + ".",
                CAMPO_ESTADO,
                Map.of(
                        "estadoActual", actual,
                        "estadoSolicitado", solicitado));
    }

    public static ConflictoNegocioException dronNoDisponible(
            EstadoDron estadoActual,
            String campo) {

        String estado = Objects.requireNonNull(estadoActual, "estadoActual").name();

        return new ConflictoNegocioException(
                DRON_NO_DISPONIBLE,
                "El dron no está disponible. Estado actual: " + estado + ".",
                campo,
                Map.of("estadoActual", estado));
    }

    public static ConflictoNegocioException dronConPaqueteEnCurso(
            UUID paqueteId,
            String campo) {

        return new ConflictoNegocioException(
                DRON_CON_PAQUETE_EN_CURSO,
                "El dron tiene un paquete en curso.",
                campo,
                Map.of("paqueteId", Objects.requireNonNull(paqueteId, "paqueteId")));
    }

    public static ConflictoNegocioException transicionDronNoPermitida(
            EstadoDron estadoActual,
            EstadoDron estadoSolicitado) {

        String actual = Objects.requireNonNull(estadoActual, "estadoActual").name();
        String solicitado = Objects.requireNonNull(
                estadoSolicitado, "estadoSolicitado").name();

        return new ConflictoNegocioException(
                TRANSICION_DRON_NO_PERMITIDA,
                "No se puede cambiar manualmente el dron de " + actual
                        + " a " + solicitado + ".",
                CAMPO_ESTADO,
                Map.of(
                        "estadoActual", actual,
                        "estadoSolicitado", solicitado));
    }

    public static ConflictoNegocioException existenciaInsuficiente(
            long existenciaActual,
            int cantidadSolicitada) {

        return new ConflictoNegocioException(
                EXISTENCIA_INSUFICIENTE,
                "La existencia disponible es " + existenciaActual
                        + "; no alcanza para la salida solicitada.",
                CAMPO_CANTIDAD,
                Map.of(
                        "existenciaActual", existenciaActual,
                        "cantidadSolicitada", cantidadSolicitada));
    }
}
