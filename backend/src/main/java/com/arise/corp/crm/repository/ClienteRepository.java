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

import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.TipoDocumento;

import jakarta.persistence.LockModeType;

/**
 * Acceso a {@code app.clientes} (CRM-01, CRM-05).
 *
 * <p>Las lecturas de lista y de detalle cargan la cuenta corporativa con
 * {@code @EntityGraph} para evitar N+1. Las consultas con bloqueo no hacen
 * fetch join (D9).
 */
public interface ClienteRepository
        extends JpaRepository<Cliente, UUID>, JpaSpecificationExecutor<Cliente> {

    Optional<Cliente> findByTipoDocumentoAndNumeroDocumento(
            TipoDocumento tipoDocumento, String numeroDocumento);

    @Override
    @EntityGraph(attributePaths = "cuentaCorporativa")
    Page<Cliente> findAll(Specification<Cliente> spec, Pageable pageable);

    @EntityGraph(attributePaths = "cuentaCorporativa")
    Optional<Cliente> findConCuentaById(UUID id);

    /**
     * Carga el cliente con bloqueo de lectura para crear un contrato sobre el.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Cliente> findParaContratoById(UUID id);

    @Query("select c.activo as clave, count(c) as total from Cliente c group by c.activo")
    List<ConteoPorClave<Boolean>> contarPorActivo();
}
