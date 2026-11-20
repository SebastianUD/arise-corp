package com.arise.corp.erp.entity;

/**
 * Tipos de documento de un empleado del ERP (ERP-01, D4).
 *
 * <p>El ERP no reutiliza {@code crm.entity.TipoDocumento}: es una entidad de
 * otro modulo y su conjunto de valores es distinto (Requirement 1.5).
 */
public enum TipoDocumentoEmpleado {
    CC,
    CE,
    PASAPORTE
}
