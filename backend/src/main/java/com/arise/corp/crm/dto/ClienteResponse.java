package com.arise.corp.crm.dto;

import java.time.Instant;
import java.util.UUID;

import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.TipoDocumento;

/**
 * Vista_Completa_Cliente para ADMINISTRADOR y COMERCIAL (Requirement 6.1).
 */
public record ClienteResponse(
        UUID id,
        TipoDocumento tipoDocumento,
        String numeroDocumento,
        String nombre,
        String correo,
        String telefono,
        String direccion,
        String ciudad,
        boolean activo,
        CuentaCorporativaRef cuentaCorporativa,
        Instant creadoEn,
        Instant actualizadoEn,
        UUID actualizadoPor) implements ClienteVista {

    public static ClienteResponse desde(Cliente cliente) {
        return new ClienteResponse(
                cliente.getId(),
                cliente.getTipoDocumento(),
                cliente.getNumeroDocumento(),
                cliente.getNombre(),
                cliente.getCorreo(),
                cliente.getTelefono(),
                cliente.getDireccion(),
                cliente.getCiudad(),
                cliente.isActivo(),
                CuentaCorporativaRef.desde(cliente.getCuentaCorporativa()),
                cliente.getCreadoEn(),
                cliente.getActualizadoEn(),
                cliente.getActualizadoPor());
    }

    @Override
    public String toString() {
        return "ClienteResponse[datos protegidos]";
    }
}
