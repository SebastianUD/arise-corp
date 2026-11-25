package com.arise.corp.sga.dto;

import java.time.Instant;
import java.util.UUID;

import com.arise.corp.sga.entity.Dron;
import com.arise.corp.sga.entity.EstadoDron;
import com.arise.corp.sga.entity.Paquete;

/**
 * Representacion de un dron (Requirement 5.7).
 *
 * <p>Los once campos son iguales en lista y en detalle. {@code paqueteEnCurso}
 * es {@code null} si ningun paquete del dron esta en {@code ASIGNADO} o
 * {@code EN_TRANSITO}.
 */
public record DronResponse(
        UUID id,
        String codigo,
        String modelo,
        String observaciones,
        EstadoDron estado,
        PaqueteEnCursoRef paqueteEnCurso,
        Instant estadoActualizadoEn,
        UUID estadoActualizadoPor,
        Instant creadoEn,
        Instant actualizadoEn,
        UUID actualizadoPor) {

    public static DronResponse desde(Dron dron, Paquete enCurso) {
        return new DronResponse(
                dron.getId(),
                dron.getCodigo(),
                dron.getModelo(),
                dron.getObservaciones(),
                dron.getEstado(),
                PaqueteEnCursoRef.desde(enCurso),
                dron.getEstadoActualizadoEn(),
                dron.getEstadoActualizadoPor(),
                dron.getCreadoEn(),
                dron.getActualizadoEn(),
                dron.getActualizadoPor());
    }

    @Override
    public String toString() {
        return "DronResponse[datos protegidos]";
    }
}
