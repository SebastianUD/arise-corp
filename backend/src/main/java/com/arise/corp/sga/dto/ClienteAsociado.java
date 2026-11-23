package com.arise.corp.sga.dto;

import java.util.UUID;

import com.arise.corp.crm.dto.ClienteReferencia;

/**
 * Cliente asociado a un paquete (Requirement 17.2).
 *
 * <p>Solo expone identificador, nombre y estado activo, leidos del CRM a traves
 * de {@code ClienteReferenciaService} (DP-ARQ-01).
 */
public record ClienteAsociado(UUID id, String nombre, boolean activo) {

    public static ClienteAsociado desde(ClienteReferencia cliente) {
        return new ClienteAsociado(
                cliente.id(), cliente.nombre(), cliente.activo());
    }
}
