package com.arise.corp.sga.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.arise.corp.sga.entity.Dron;
import com.arise.corp.sga.entity.EstadoDron;

import jakarta.persistence.LockModeType;

/**
 * Acceso a {@code app.drones} (SGA-01).
 *
 * <p>Las consultas con bloqueo no hacen fetch join (D10): un dron no tiene
 * relaciones perezosas dentro del SGA.
 */
public interface DronRepository
        extends JpaRepository<Dron, UUID>, JpaSpecificationExecutor<Dron> {

    Optional<Dron> findByCodigo(String codigo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Dron> findParaModificarById(UUID id);

    @Query("select d.estado as clave, count(d) as total from Dron d group by d.estado")
    List<ConteoPorEstado<EstadoDron>> contarPorEstado();
}
