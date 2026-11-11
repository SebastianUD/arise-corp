package com.arise.corp.crm.dto;

import java.util.UUID;

import com.arise.corp.crm.entity.Cliente;

/**
 * Referencia anidada del cliente de un contrato.
 */
public record ClienteRef(UUID id, String nombre) {

    public static ClienteRef desde(Cliente cliente) {
        return cliente == null
                ? null
                : new ClienteRef(cliente.getId(), cliente.getNombre());
    }
}
