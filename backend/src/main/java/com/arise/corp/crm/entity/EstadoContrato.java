package com.arise.corp.crm.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estado de un contrato (CRM-03, DP-API-04, D10).
 *
 * <p>Los valores coinciden con la restriccion {@code CHECK} de
 * {@code app.contratos.estado}. Solo se aceptan las cuatro transiciones
 * permitidas: {@code BORRADOR -> ACTIVO}, {@code BORRADOR -> CANCELADO},
 * {@code ACTIVO -> FINALIZADO} y {@code ACTIVO -> CANCELADO}. {@code FINALIZADO}
 * y {@code CANCELADO} son estados finales.
 */
public enum EstadoContrato {

    BORRADOR,
    ACTIVO,
    FINALIZADO,
    CANCELADO;

    public Set<EstadoContrato> transicionesPermitidas() {
        return switch (this) {
            case BORRADOR -> EnumSet.of(ACTIVO, CANCELADO);
            case ACTIVO -> EnumSet.of(FINALIZADO, CANCELADO);
            case FINALIZADO, CANCELADO -> EnumSet.noneOf(EstadoContrato.class);
        };
    }

    public boolean puedeTransicionarA(EstadoContrato destino) {
        return transicionesPermitidas().contains(destino);
    }

    public boolean esFinal() {
        return transicionesPermitidas().isEmpty();
    }
}
