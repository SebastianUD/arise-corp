package com.arise.corp.sga.dto;

import java.util.UUID;

import com.arise.corp.crm.dto.ContratoReferencia;

/**
 * Contrato asociado a un paquete (Requirement 8.7).
 *
 * <p>Solo expone identificador, codigo y estado, leidos del CRM a traves de
 * {@code ContratoReferenciaService} (DP-ARQ-01).
 */
public record ContratoAsociado(UUID id, String codigo, String estado) {

    /**
     * @return {@code null} si la referencia recibida es {@code null}
     */
    public static ContratoAsociado desde(ContratoReferencia contrato) {
        if (contrato == null) {
            return null;
        }

        return new ContratoAsociado(
                contrato.id(), contrato.codigo(), contrato.estado());
    }
}
