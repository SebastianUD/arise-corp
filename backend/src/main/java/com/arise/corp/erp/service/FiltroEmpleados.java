package com.arise.corp.erp.service;

/**
 * Filtros de la lista de empleados (ERP-01, DP-API-03).
 *
 * <p>Un filtro ausente llega como {@code null} y la especificacion
 * correspondiente no anade restricciones. {@code q} busca por nombre sin
 * distinguir mayusculas o por el documento normalizado.
 */
public record FiltroEmpleados(String q, Boolean activo) {
}
