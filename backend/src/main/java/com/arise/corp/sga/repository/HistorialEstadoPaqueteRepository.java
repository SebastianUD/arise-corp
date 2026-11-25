package com.arise.corp.sga.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.arise.corp.sga.entity.HistorialEstadoPaquete;

/**
 * Acceso a {@code app.historial_estados_paquete} (SGA-04).
 *
 * <p>El historial se lee por {@code secuencia}, no por el reloj (D11), y el
 * dron se carga con {@code @EntityGraph} para la respuesta.
 */
public interface HistorialEstadoPaqueteRepository
        extends JpaRepository<HistorialEstadoPaquete, UUID> {

    @EntityGraph(attributePaths = "dron")
    List<HistorialEstadoPaquete> findByPaqueteIdOrderBySecuenciaAsc(UUID paqueteId);

    long countByPaqueteId(UUID paqueteId);
}
