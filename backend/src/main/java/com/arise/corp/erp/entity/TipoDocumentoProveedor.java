package com.arise.corp.erp.entity;

/**
 * Tipos de documento de un proveedor del ERP (ERP-02, D4).
 *
 * <p>El NIT solo existe para proveedores; el ERP no reutiliza
 * {@code crm.entity.TipoDocumento} (Requirement 1.5).
 */
public enum TipoDocumentoProveedor {
    NIT,
    CC,
    CE,
    PASAPORTE
}
