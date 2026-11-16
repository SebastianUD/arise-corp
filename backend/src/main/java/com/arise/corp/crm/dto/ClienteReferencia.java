package com.arise.corp.crm.dto;

import java.util.UUID;

import com.arise.corp.crm.entity.Cliente;

/**
 * Lectura de un cliente para otros modulos (DP-ARQ-01, D2): identificador,
 * nombre y estado.
 *
 * <p>Expone solo los datos de la Vista_Reducida_Cliente y no conoce el ERP; el
 * modulo consumidor la recibe sin acceso a la entidad ni al repositorio del
 * CRM.
 */
public record ClienteReferencia(UUID id, String nombre, boolean activo) {

    /**
     * Convierte la entidad en la referencia de solo lectura.
     *
     * @return {@code null} si el cliente recibido es {@code null}
     */
    public static ClienteReferencia desde(Cliente cliente) {
        if (cliente == null) {
            return null;
        }

        return new ClienteReferencia(
                cliente.getId(), cliente.getNombre(), cliente.isActivo());
    }
}
