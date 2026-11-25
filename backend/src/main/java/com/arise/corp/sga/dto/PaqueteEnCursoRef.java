package com.arise.corp.sga.dto;

import java.util.UUID;

import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.entity.Paquete;

/**
 * Paquete en curso de un dron (Requirement 5.7).
 *
 * <p>Expone solo identificador, codigo y estado del paquete {@code ASIGNADO} o
 * {@code EN_TRANSITO} asociado al dron.
 */
public record PaqueteEnCursoRef(UUID id, String codigo, EstadoPaquete estado) {

    /**
     * @return {@code null} si el paquete recibido es {@code null}
     */
    public static PaqueteEnCursoRef desde(Paquete paquete) {
        if (paquete == null) {
            return null;
        }

        return new PaqueteEnCursoRef(
                paquete.getId(), paquete.getCodigo(), paquete.getEstado());
    }
}
