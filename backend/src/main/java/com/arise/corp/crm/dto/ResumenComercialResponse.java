package com.arise.corp.crm.dto;

import java.util.EnumMap;
import java.util.Map;

import com.arise.corp.crm.entity.EstadoContrato;

/**
 * Resumen comercial del CRM (CRM-05).
 *
 * <p>Los cuatro estados aparecen siempre, con 0 cuando no hay contratos. Los
 * totales son la suma de sus componentes (Requirement 15.3).
 */
public record ResumenComercialResponse(
        ConteoClientes clientes,
        ConteoContratos contratos) {

    public record ConteoClientes(long activos, long inactivos, long total) {

        public static ConteoClientes desde(long activos, long inactivos) {
            return new ConteoClientes(activos, inactivos, activos + inactivos);
        }
    }

    public record ConteoContratos(Map<EstadoContrato, Long> porEstado, long total) {

        public static ConteoContratos desde(Map<EstadoContrato, Long> conteos) {
            Map<EstadoContrato, Long> porEstado =
                    new EnumMap<>(EstadoContrato.class);
            long total = 0;

            for (EstadoContrato estado : EstadoContrato.values()) {
                long cantidad = conteos.getOrDefault(estado, 0L);
                porEstado.put(estado, cantidad);
                total += cantidad;
            }

            return new ConteoContratos(porEstado, total);
        }
    }
}
