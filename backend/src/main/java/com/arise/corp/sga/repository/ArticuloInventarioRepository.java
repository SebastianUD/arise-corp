package com.arise.corp.sga.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.arise.corp.sga.entity.ArticuloInventario;

import jakarta.persistence.LockModeType;

/**
 * Acceso a {@code app.articulos_inventario} (SGA-03).
 *
 * <p>{@link #contarArticulos()} devuelve el total y cuantos estan bajo stock
 * minimo en una sola consulta (D13). Las consultas con bloqueo no hacen fetch
 * join (D10).
 */
public interface ArticuloInventarioRepository
        extends JpaRepository<ArticuloInventario, UUID>,
                JpaSpecificationExecutor<ArticuloInventario> {

    Optional<ArticuloInventario> findByCodigo(String codigo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ArticuloInventario> findParaModificarById(UUID id);

    @Query("select count(a) as total,"
            + " coalesce(sum(case when a.existencia < a.stockMinimo then 1 else 0 end), 0)"
            + " as bajoStockMinimo"
            + " from ArticuloInventario a")
    ConteoArticulos contarArticulos();
}
