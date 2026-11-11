package com.arise.corp.crm.repository;

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

import com.arise.corp.crm.entity.Contrato;
import com.arise.corp.crm.entity.EstadoContrato;

import jakarta.persistence.LockModeType;

/**
 * Acceso a {@code app.contratos} (CRM-03, CRM-05).
 *
 * <p>Las lecturas cargan el cliente y su cuenta corporativa con
 * {@code @EntityGraph}. Las consultas con bloqueo no hacen fetch join (D9).
 */
public interface ContratoRepository
        extends JpaRepository<Contrato, UUID>, JpaSpecificationExecutor<Contrato> {

    Optional<Contrato> findByCodigo(String codigo);

    @Override
    @EntityGraph(attributePaths = {"cliente", "cliente.cuentaCorporativa"})
    Page<Contrato> findAll(Specification<Contrato> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"cliente", "cliente.cuentaCorporativa"})
    Optional<Contrato> findConClienteById(UUID id);

    /**
     * Carga el contrato con bloqueo de escritura para editarlo o cambiar su
     * estado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Contrato> findParaModificarById(UUID id);

    @Query("select c.estado as clave, count(c) as total from Contrato c group by c.estado")
    List<ConteoPorClave<EstadoContrato>> contarPorEstado();
}
