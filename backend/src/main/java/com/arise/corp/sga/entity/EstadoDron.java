package com.arise.corp.sga.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estado de un dron (SGA-01).
 *
 * <p>{@code EN_MISION} solo lo fija y lo abandona la sincronizacion con el
 * paquete (D8): el cambio manual nunca lo fija ni lo abandona y el mismo estado
 * no cuenta como transicion (Requirements 6.1, 6.2).
 */
public enum EstadoDron {
    DISPONIBLE,
    EN_MISION,
    MANTENIMIENTO,
    FUERA_DE_SERVICIO;

    private static final Set<EstadoDron> MANUALES =
            EnumSet.of(DISPONIBLE, MANTENIMIENTO, FUERA_DE_SERVICIO);

    /**
     * Estados alcanzables con un cambio manual; vacio desde {@code EN_MISION}.
     */
    public Set<EstadoDron> transicionesManuales() {
        if (this == EN_MISION) {
            return EnumSet.noneOf(EstadoDron.class);
        }
        EnumSet<EstadoDron> destinos = EnumSet.copyOf(MANUALES);
        destinos.remove(this);
        return destinos;
    }

    public boolean puedeCambiarManualmenteA(EstadoDron destino) {
        return transicionesManuales().contains(destino);
    }
}
