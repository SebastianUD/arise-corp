package com.arise.corp.crm.service;

/**
 * Filtros de la lista de cuentas corporativas (CRM-02, DP-API-03).
 *
 * <p>Un filtro ausente llega como {@code null} y la especificacion
 * correspondiente no anade restricciones. La razon social se compara sin
 * distinguir mayusculas y el NIT con su valor normalizado.
 */
public record FiltroCuentas(String q, Boolean activa) {
}
