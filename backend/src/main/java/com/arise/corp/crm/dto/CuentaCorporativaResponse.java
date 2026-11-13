package com.arise.corp.crm.dto;

import java.time.Instant;
import java.util.UUID;

import com.arise.corp.crm.entity.CuentaCorporativa;

/**
 * Representacion completa de una cuenta corporativa (CRM-02).
 */
public record CuentaCorporativaResponse(
        UUID id,
        String nit,
        String razonSocial,
        String nombreContacto,
        String correoContacto,
        String telefonoContacto,
        boolean activa,
        Instant creadoEn,
        Instant actualizadoEn,
        UUID actualizadoPor) {

    public static CuentaCorporativaResponse desde(CuentaCorporativa cuenta) {
        return new CuentaCorporativaResponse(
                cuenta.getId(),
                cuenta.getNit(),
                cuenta.getRazonSocial(),
                cuenta.getNombreContacto(),
                cuenta.getCorreoContacto(),
                cuenta.getTelefonoContacto(),
                cuenta.isActiva(),
                cuenta.getCreadoEn(),
                cuenta.getActualizadoEn(),
                cuenta.getActualizadoPor());
    }

    @Override
    public String toString() {
        return "CuentaCorporativaResponse[datos protegidos]";
    }
}
