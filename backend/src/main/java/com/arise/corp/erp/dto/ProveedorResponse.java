package com.arise.corp.erp.dto;

import java.time.Instant;
import java.util.UUID;

import com.arise.corp.erp.entity.Proveedor;
import com.arise.corp.erp.entity.TipoDocumentoProveedor;

/**
 * Representacion de un proveedor del ERP, la misma para ADMINISTRADOR y
 * FINANZAS_RRHH en detalle y en listas (Requirement 10.6).
 *
 * <p>{@code nombreContacto}, {@code direccion} y {@code ciudad} son
 * {@code null} cuando no se informan.
 */
public record ProveedorResponse(
        UUID id,
        TipoDocumentoProveedor tipoDocumento,
        String numeroDocumento,
        String nombre,
        String nombreContacto,
        String correo,
        String telefono,
        String direccion,
        String ciudad,
        boolean activo,
        Instant creadoEn,
        Instant actualizadoEn,
        UUID actualizadoPor) {

    public static ProveedorResponse desde(Proveedor proveedor) {
        return new ProveedorResponse(
                proveedor.getId(),
                proveedor.getTipoDocumento(),
                proveedor.getNumeroDocumento(),
                proveedor.getNombre(),
                proveedor.getNombreContacto(),
                proveedor.getCorreo(),
                proveedor.getTelefono(),
                proveedor.getDireccion(),
                proveedor.getCiudad(),
                proveedor.isActivo(),
                proveedor.getCreadoEn(),
                proveedor.getActualizadoEn(),
                proveedor.getActualizadoPor());
    }

    @Override
    public String toString() {
        return "ProveedorResponse[datos protegidos]";
    }
}
