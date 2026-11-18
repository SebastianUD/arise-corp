package com.arise.corp.erp.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.arise.corp.erp.entity.Empleado;
import com.arise.corp.erp.entity.TipoDocumentoEmpleado;

/**
 * Acceso a {@code app.empleados} (ERP-01).
 *
 * <p>La lista y el detalle usan las especificaciones de
 * {@link EspecificacionesErp}; los duplicados se buscan por tipo y numero de
 * documento ya normalizado.
 */
public interface EmpleadoRepository
        extends JpaRepository<Empleado, UUID>, JpaSpecificationExecutor<Empleado> {

    Optional<Empleado> findByTipoDocumentoAndNumeroDocumento(
            TipoDocumentoEmpleado tipoDocumento, String numeroDocumento);
}
