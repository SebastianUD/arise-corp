package com.arise.corp.erp.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import com.arise.corp.erp.entity.Proveedor;
import com.arise.corp.erp.entity.TipoDocumentoProveedor;

import jakarta.persistence.LockModeType;

/**
 * Acceso a {@code app.proveedores} (ERP-02).
 *
 * <p>{@link #findParaAsociarById} carga el proveedor con bloqueo de lectura
 * para asociarlo a un movimiento financiero: una desactivacion simultanea
 * espera a que termine la asociacion, o la asociacion ve el proveedor ya
 * inactivo (D12).
 */
public interface ProveedorRepository
        extends JpaRepository<Proveedor, UUID>, JpaSpecificationExecutor<Proveedor> {

    Optional<Proveedor> findByTipoDocumentoAndNumeroDocumento(
            TipoDocumentoProveedor tipoDocumento, String numeroDocumento);

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Proveedor> findParaAsociarById(UUID id);
}
