package com.arise.corp.sga.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.arise.corp.crm.dto.ClienteReferencia;
import com.arise.corp.crm.dto.ContratoReferencia;
import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.entity.Paquete;

/**
 * Representacion de un paquete (Requirements 8.7, 17.2).
 *
 * <p>El cliente y el contrato se leen del CRM en cada consulta y solo exponen
 * los campos de sus referencias (DP-ARQ-01). Si el paquete tiene
 * {@code clienteId} o {@code contratoId}, la referencia correspondiente es
 * obligatoria.
 */
public record PaqueteResponse(
        UUID id,
        String codigo,
        String descripcion,
        String direccionEntrega,
        EstadoPaquete estado,
        ClienteAsociado cliente,
        ContratoAsociado contrato,
        DronAsociado dron,
        Instant estadoActualizadoEn,
        UUID estadoActualizadoPor,
        Instant creadoEn,
        Instant actualizadoEn,
        UUID actualizadoPor) {

    public static PaqueteResponse desde(
            Paquete paquete,
            ClienteReferencia cliente,
            ContratoReferencia contrato) {

        Objects.requireNonNull(paquete, "paquete");

        ClienteAsociado clienteAsociado = null;
        if (paquete.getClienteId() != null) {
            clienteAsociado = ClienteAsociado.desde(
                    Objects.requireNonNull(cliente, "cliente"));
        }

        ContratoAsociado contratoAsociado = null;
        if (paquete.getContratoId() != null) {
            contratoAsociado = ContratoAsociado.desde(
                    Objects.requireNonNull(contrato, "contrato"));
        }

        return new PaqueteResponse(
                paquete.getId(),
                paquete.getCodigo(),
                paquete.getDescripcion(),
                paquete.getDireccionEntrega(),
                paquete.getEstado(),
                clienteAsociado,
                contratoAsociado,
                DronAsociado.desde(paquete.getDron()),
                paquete.getEstadoActualizadoEn(),
                paquete.getEstadoActualizadoPor(),
                paquete.getCreadoEn(),
                paquete.getActualizadoEn(),
                paquete.getActualizadoPor());
    }

    @Override
    public String toString() {
        return "PaqueteResponse[datos protegidos]";
    }
}
