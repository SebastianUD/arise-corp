package com.arise.corp.crm.dto;

import java.util.UUID;

import com.arise.corp.crm.entity.CuentaCorporativa;

/**
 * Referencia anidada de la cuenta corporativa de un cliente o contrato.
 */
public record CuentaCorporativaRef(UUID id, String razonSocial) {

    public static CuentaCorporativaRef desde(CuentaCorporativa cuenta) {
        return cuenta == null
                ? null
                : new CuentaCorporativaRef(cuenta.getId(), cuenta.getRazonSocial());
    }
}
