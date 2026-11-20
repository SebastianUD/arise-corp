package com.arise.corp.erp.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estado de un movimiento financiero (ERP-03).
 *
 * <p>La Transicion_Permitida_Movimiento (D11) solo admite
 * {@code PENDIENTE -> PAGADO} y {@code PENDIENTE -> ANULADO}. {@code PAGADO} y
 * {@code ANULADO} son finales y un movimiento solo se edita mientras esta
 * {@code PENDIENTE} (Requirement 16.4).
 */
public enum EstadoMovimiento {
    PENDIENTE,
    PAGADO,
    ANULADO;

    /**
     * Estados alcanzables desde este estado; vacio si es final.
     */
    public Set<EstadoMovimiento> transicionesPermitidas() {
        return switch (this) {
            case PENDIENTE -> EnumSet.of(PAGADO, ANULADO);
            case PAGADO, ANULADO -> EnumSet.noneOf(EstadoMovimiento.class);
        };
    }

    public boolean puedeTransicionarA(EstadoMovimiento destino) {
        return transicionesPermitidas().contains(destino);
    }

    public boolean esFinal() {
        return transicionesPermitidas().isEmpty();
    }

    /**
     * Solo un movimiento pendiente admite {@code PUT} (Requirement 16.4).
     */
    public boolean esEditable() {
        return this == PENDIENTE;
    }
}
