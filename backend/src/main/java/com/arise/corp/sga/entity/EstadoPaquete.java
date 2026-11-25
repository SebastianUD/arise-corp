package com.arise.corp.sga.entity;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Estado de un paquete (SGA-02, SGA-05).
 *
 * <p>La Transicion_Permitida_Paquete (D7) se define en {@link #transicionesPermitidas()}.
 * {@code ENTREGADO}, {@code CANCELADO} y {@code DEVUELTO} son finales, y un
 * paquete solo se edita mientras esta {@code REGISTRADO} o {@code ASIGNADO}
 * (Requirement 9.1). {@link #efectoSobreDron(EstadoPaquete)} concentra la
 * sincronizacion dron-paquete (D8).
 */
public enum EstadoPaquete {
    REGISTRADO,
    ASIGNADO,
    EN_TRANSITO,
    ENTREGADO,
    CANCELADO,
    DEVUELTO;

    /** Estados que cuentan como Paquete_En_Curso de un dron (SGA-05). */
    public static final Set<EstadoPaquete> EN_CURSO = EnumSet.of(ASIGNADO, EN_TRANSITO);

    /**
     * Estados alcanzables desde este estado; vacio si es final.
     */
    public Set<EstadoPaquete> transicionesPermitidas() {
        return switch (this) {
            case REGISTRADO -> EnumSet.of(ASIGNADO, CANCELADO);
            case ASIGNADO -> EnumSet.of(EN_TRANSITO, CANCELADO);
            case EN_TRANSITO -> EnumSet.of(ENTREGADO, DEVUELTO);
            case ENTREGADO, CANCELADO, DEVUELTO -> EnumSet.noneOf(EstadoPaquete.class);
        };
    }

    public boolean puedeTransicionarA(EstadoPaquete destino) {
        return transicionesPermitidas().contains(destino);
    }

    public boolean esFinal() {
        return transicionesPermitidas().isEmpty();
    }

    /**
     * Solo un paquete registrado o asignado admite {@code PUT} (Requirement 9.3).
     */
    public boolean esEditable() {
        return this == REGISTRADO || this == ASIGNADO;
    }

    /**
     * Estado al que pasa el dron asignado; vacio si la transicion no lo cambia
     * o no esta permitida (D8, Requirements 11.7, 11.9, 11.10).
     */
    public Optional<EstadoDron> efectoSobreDron(EstadoPaquete destino) {
        if (this == ASIGNADO && destino == EN_TRANSITO) {
            return Optional.of(EstadoDron.EN_MISION);
        }
        if (this == EN_TRANSITO && (destino == ENTREGADO || destino == DEVUELTO)) {
            return Optional.of(EstadoDron.DISPONIBLE);
        }
        return Optional.empty();
    }
}
