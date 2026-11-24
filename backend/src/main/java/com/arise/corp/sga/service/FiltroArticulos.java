package com.arise.corp.sga.service;

import com.arise.corp.sga.entity.CategoriaArticulo;

/**
 * Filtros de la lista de articulos de inventario (Requirement 14).
 *
 * <p>Record sin logica: todos los campos son opcionales y se combinan con
 * {@code y}.
 */
public record FiltroArticulos(
        String q,
        CategoriaArticulo categoria,
        Boolean bajoStockMinimo) {
}
