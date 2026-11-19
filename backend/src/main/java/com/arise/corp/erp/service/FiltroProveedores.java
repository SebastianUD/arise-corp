package com.arise.corp.erp.service;

/**
 * Filtros de la lista de proveedores (ERP-02, DP-API-03).
 *
 * <p>Un filtro ausente llega como {@code null} y la especificacion
 * correspondiente no anade restricciones.
 */
public record FiltroProveedores(String q, Boolean activo) {
}
