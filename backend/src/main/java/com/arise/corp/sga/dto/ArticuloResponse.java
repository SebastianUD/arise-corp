package com.arise.corp.sga.dto;

import java.time.Instant;
import java.util.UUID;

import com.arise.corp.sga.entity.ArticuloInventario;
import com.arise.corp.sga.entity.CategoriaArticulo;
import com.arise.corp.sga.entity.UnidadArticulo;

/**
 * Representacion de un articulo de inventario (Requirement 14.6).
 *
 * <p>{@code bajoStockMinimo = existencia < stockMinimo}, calculado al construir
 * la respuesta.
 */
public record ArticuloResponse(
        UUID id,
        String codigo,
        String nombre,
        CategoriaArticulo categoria,
        UnidadArticulo unidad,
        int stockMinimo,
        long existencia,
        boolean bajoStockMinimo,
        Instant creadoEn,
        Instant actualizadoEn,
        UUID actualizadoPor) {

    public static ArticuloResponse desde(ArticuloInventario articulo) {
        return new ArticuloResponse(
                articulo.getId(),
                articulo.getCodigo(),
                articulo.getNombre(),
                articulo.getCategoria(),
                articulo.getUnidad(),
                articulo.getStockMinimo(),
                articulo.getExistencia(),
                articulo.bajoStockMinimo(),
                articulo.getCreadoEn(),
                articulo.getActualizadoEn(),
                articulo.getActualizadoPor());
    }
}
