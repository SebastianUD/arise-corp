package com.arise.corp.erp.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import com.arise.corp.erp.entity.MovimientoFinanciero;

import jakarta.persistence.LockModeType;

/**
 * Acceso a {@code app.movimientos_financieros} (ERP-03, ERP-05).
 *
 * <p>La lista y el detalle cargan el proveedor con {@code @EntityGraph} para
 * evitar N+1. Las consultas con bloqueo no hacen fetch join de la relacion
 * opcional, porque PostgreSQL rechaza {@code FOR UPDATE} sobre el lado anulable
 * de un {@code LEFT JOIN}; el proveedor se carga despues de forma perezosa
 * (D12).
 */
public interface MovimientoFinancieroRepository
        extends JpaRepository<MovimientoFinanciero, UUID>,
                JpaSpecificationExecutor<MovimientoFinanciero>,
                TotalesMovimientos {

    @Override
    @EntityGraph(attributePaths = "proveedor")
    Page<MovimientoFinanciero> findAll(
            Specification<MovimientoFinanciero> spec,
            Pageable pageable);

    @EntityGraph(attributePaths = "proveedor")
    Optional<MovimientoFinanciero> findConProveedorById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MovimientoFinanciero> findParaModificarById(UUID id);
}
