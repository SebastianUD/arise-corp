package com.arise.corp.crm.dto;

import java.util.UUID;

import com.arise.corp.crm.entity.Contrato;
import com.arise.corp.crm.entity.EstadoContrato;

/**
 * Lectura de un contrato para otros modulos (DP-ARQ-01, D2): identificador,
 * cliente, codigo y estado.
 *
 * <p>El estado viaja como texto para que el consumidor no dependa de
 * {@code crm.entity}. El modulo consumidor la recibe sin acceso a la entidad ni
 * al repositorio del CRM, y este DTO no conoce el SGA.
 */
public record ContratoReferencia(UUID id, UUID clienteId, String codigo, String estado) {

    /**
     * Convierte la entidad en la referencia de solo lectura.
     *
     * <p>{@code clienteId} se toma del proxy {@code LAZY} sin inicializarlo.
     *
     * @return {@code null} si el contrato recibido es {@code null}
     */
    public static ContratoReferencia desde(Contrato contrato) {
        if (contrato == null) {
            return null;
        }

        return new ContratoReferencia(
                contrato.getId(),
                contrato.getCliente().getId(),
                contrato.getCodigo(),
                contrato.getEstado().name());
    }

    /**
     * {@code true} si el contrato esta en {@link EstadoContrato#ACTIVO}.
     */
    public boolean activo() {
        return EstadoContrato.ACTIVO.name().equals(estado);
    }
}
