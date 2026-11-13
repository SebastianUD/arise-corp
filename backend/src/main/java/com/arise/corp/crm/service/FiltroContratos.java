package com.arise.corp.crm.service;

import java.util.UUID;

import com.arise.corp.crm.entity.EstadoContrato;

/**
 * Filtros de la lista de contratos (CRM-03, DP-API-03).
 *
 * <p>Un filtro ausente llega como {@code null} y la especificacion
 * correspondiente no anade restricciones. Un {@code clienteId} inexistente
 * produce una pagina vacia, no un 404.
 */
public record FiltroContratos(UUID clienteId, EstadoContrato estado) {
}
