package com.arise.corp.sga.dto;

import java.util.UUID;

import com.arise.corp.sga.entity.Dron;
import com.arise.corp.sga.entity.EstadoDron;

/**
 * Dron asociado a un paquete (Requirement 8.7).
 */
public record DronAsociado(UUID id, String codigo, EstadoDron estado) {

    /**
     * @return {@code null} si el dron recibido es {@code null}
     */
    public static DronAsociado desde(Dron dron) {
        if (dron == null) {
            return null;
        }

        return new DronAsociado(dron.getId(), dron.getCodigo(), dron.getEstado());
    }
}
