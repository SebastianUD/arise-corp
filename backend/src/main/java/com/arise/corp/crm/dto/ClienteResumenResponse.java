package com.arise.corp.crm.dto;

import java.util.UUID;

import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.TipoDocumento;

/**
 * Vista_Reducida_Cliente para FINANZAS_RRHH y OPERACIONES (Requirement 6.2).
 *
 * <p>Excluye {@code correo}, {@code telefono}, {@code direccion} y la auditoria.
 */
public record ClienteResumenResponse(
        UUID id,
        TipoDocumento tipoDocumento,
        String numeroDocumento,
        String nombre,
        String ciudad,
        boolean activo,
        CuentaCorporativaRef cuentaCorporativa) implements ClienteVista {

    public static ClienteResumenResponse desde(Cliente cliente) {
        return new ClienteResumenResponse(
                cliente.getId(),
                cliente.getTipoDocumento(),
                cliente.getNumeroDocumento(),
                cliente.getNombre(),
                cliente.getCiudad(),
                cliente.isActivo(),
                CuentaCorporativaRef.desde(cliente.getCuentaCorporativa()));
    }
}
