package com.arise.corp.sga.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.entity.Paquete;

import jakarta.persistence.LockModeType;

/**
 * Acceso a {@code app.paquetes} (SGA-02, SGA-05).
 *
 * <p>La lista y el detalle cargan el dron con {@code @EntityGraph} para evitar
 * N+1. Las consultas con bloqueo no hacen fetch join (D10).
 */
public interface PaqueteRepository
        extends JpaRepository<Paquete, UUID>, JpaSpecificationExecutor<Paquete> {

    Optional<Paquete> findByCodigo(String codigo);

    @Override
    @EntityGraph(attributePaths = "dron")
    Page<Paquete> findAll(Specification<Paquete> spec, Pageable pageable);

    @EntityGraph(attributePaths = "dron")
    Optional<Paquete> findConDronById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Paquete> findParaModificarById(UUID id);

    Optional<Paquete> findFirstByDronIdAndEstadoIn(
            UUID dronId,
            Collection<EstadoPaquete> estados);

    List<Paquete> findByDronIdInAndEstadoIn(
            Collection<UUID> dronIds,
            Collection<EstadoPaquete> estados);

    @Query("select p.estado as clave, count(p) as total from Paquete p group by p.estado")
    List<ConteoPorEstado<EstadoPaquete>> contarPorEstado();
}
