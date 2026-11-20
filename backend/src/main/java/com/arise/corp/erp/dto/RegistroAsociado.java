package com.arise.corp.erp.dto;

import java.util.UUID;

import com.arise.corp.crm.dto.ClienteReferencia;
import com.arise.corp.erp.entity.Proveedor;

/**
 * Referencia anidada del cliente o del proveedor de un movimiento (D3).
 *
 * <p>Incluye el estado activo para que la vista muestre la asociacion actual
 * aunque el registro se haya desactivado despues, sin peticiones adicionales.
 * Las dos fabricas devuelven {@code null} cuando la entrada es {@code null}.
 */
public record RegistroAsociado(UUID id, String nombre, boolean activo) {

    public static RegistroAsociado desde(Proveedor proveedor) {
        if (proveedor == null) {
            return null;
        }

        return new RegistroAsociado(
                proveedor.getId(), proveedor.getNombre(), proveedor.isActivo());
    }

    public static RegistroAsociado desde(ClienteReferencia cliente) {
        if (cliente == null) {
            return null;
        }

        return new RegistroAsociado(
                cliente.id(), cliente.nombre(), cliente.activo());
    }
}
