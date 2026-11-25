package com.arise.corp.sga.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.arise.corp.sga.entity.MovimientoInventario;

/**
 * Acceso a {@code app.movimientos_inventario} (SGA-03). La lista se pagina por
 * articulo (Requirement 15.11).
 */
public interface MovimientoInventarioRepository
        extends JpaRepository<MovimientoInventario, UUID> {

    Page<MovimientoInventario> findByArticuloId(UUID articuloId, Pageable pageable);
}
