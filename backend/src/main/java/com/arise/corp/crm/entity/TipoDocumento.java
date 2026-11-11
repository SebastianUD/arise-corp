package com.arise.corp.crm.entity;

/**
 * Tipo de documento de identidad de un cliente (DP-API-04).
 *
 * <p>Los valores coinciden con la restriccion {@code CHECK} de
 * {@code app.clientes.tipo_documento}. Se persisten como texto con
 * {@code @Enumerated(EnumType.STRING)}.
 */
public enum TipoDocumento {

    CC,
    CE,
    NIT,
    PASAPORTE
}
