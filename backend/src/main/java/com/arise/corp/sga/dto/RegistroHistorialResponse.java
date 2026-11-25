package com.arise.corp.sga.dto;

import java.time.Instant;
import java.util.UUID;

import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.entity.HistorialEstadoPaquete;

/**
 * Registro del historial de estados de un paquete (Requirement 12.1).
 *
 * <p>El dron solo aparece en el registro de la transicion a {@code ASIGNADO};
 * en los demas es {@code null}.
 */
public record RegistroHistorialResponse(
        UUID id,
        EstadoPaquete estadoAnterior,
        EstadoPaquete estadoNuevo,
        DronRef dron,
        String observacion,
        Instant registradoEn,
        UUID registradoPor) {

    public static RegistroHistorialResponse desde(
            HistorialEstadoPaquete registro) {

        return new RegistroHistorialResponse(
                registro.getId(),
                registro.getEstadoAnterior(),
                registro.getEstadoNuevo(),
                DronRef.desde(registro.getDron()),
                registro.getObservacion(),
                registro.getRegistradoEn(),
                registro.getRegistradoPor());
    }

    @Override
    public String toString() {
        return "RegistroHistorialResponse[datos protegidos]";
    }
}
