package com.arise.corp.crm.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import com.arise.corp.crm.entity.CuentaCorporativa;

import jakarta.persistence.LockModeType;

/**
 * Repositorio de cuentas corporativas (CRM-02).
 *
 * <p>Las consultas con bloqueo no hacen fetch join: PostgreSQL rechaza
 * {@code FOR UPDATE} sobre el lado anulable de un {@code LEFT JOIN} (D9).
 */
public interface CuentaCorporativaRepository
        extends JpaRepository<CuentaCorporativa, UUID>, JpaSpecificationExecutor<CuentaCorporativa> {

    Optional<CuentaCorporativa> findByNit(String nit);

    /**
     * Carga la cuenta con bloqueo de lectura para asignarla a un cliente.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<CuentaCorporativa> findParaAsignarById(UUID id);
}
