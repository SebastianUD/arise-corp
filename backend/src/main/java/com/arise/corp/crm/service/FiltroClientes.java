package com.arise.corp.crm.service;

import java.util.UUID;

/**
 * Filtros de la lista de clientes (CRM-01, DP-API-03).
 *
 * <p>Un filtro ausente llega como {@code null} y la especificacion
 * correspondiente no anade restricciones. {@code q} busca por nombre sin
 * distinguir mayusculas o por el documento normalizado.
 */
public record FiltroClientes(String q, Boolean activo, UUID cuentaCorporativaId) {
}
