package com.arise.corp.sga.dto;

import java.util.UUID;

import com.arise.corp.sga.entity.Dron;

/**
 * Referencia minima a un dron dentro del historial de estados (Requirement
 * 12.1).
 */
public record DronRef(UUID id, String codigo) {

    /**
     * @return {@code null} si el dron recibido es {@code null}
     */
    public static DronRef desde(Dron dron) {
        if (dron == null) {
            return null;
        }

        return new DronRef(dron.getId(), dron.getCodigo());
    }
}
