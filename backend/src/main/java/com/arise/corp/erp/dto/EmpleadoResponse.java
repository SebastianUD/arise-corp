package com.arise.corp.erp.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.arise.corp.erp.entity.Empleado;
import com.arise.corp.erp.entity.TipoDocumentoEmpleado;

/**
 * Representacion de un empleado del ERP, la misma para ADMINISTRADOR y
 * FINANZAS_RRHH en detalle y en listas (Requirements 5.6, 8.1, 8.2).
 *
 * <p>No incluye salario, datos de nomina ni datos bancarios: el modelo no los
 * tiene.
 */
public record EmpleadoResponse(
        UUID id,
        TipoDocumentoEmpleado tipoDocumento,
        String numeroDocumento,
        String nombre,
        String cargo,
        String area,
        String correo,
        String telefono,
        LocalDate fechaIngreso,
        boolean activo,
        Instant creadoEn,
        Instant actualizadoEn,
        UUID actualizadoPor) {

    public static EmpleadoResponse desde(Empleado empleado) {
        return new EmpleadoResponse(
                empleado.getId(),
                empleado.getTipoDocumento(),
                empleado.getNumeroDocumento(),
                empleado.getNombre(),
                empleado.getCargo(),
                empleado.getArea(),
                empleado.getCorreo(),
                empleado.getTelefono(),
                empleado.getFechaIngreso(),
                empleado.isActivo(),
                empleado.getCreadoEn(),
                empleado.getActualizadoEn(),
                empleado.getActualizadoPor());
    }

    @Override
    public String toString() {
        return "EmpleadoResponse[datos protegidos]";
    }
}
