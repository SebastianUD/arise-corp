package com.arise.corp.sga.dto;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.arise.corp.sga.entity.EstadoDron;
import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.repository.ConteoArticulos;
import com.arise.corp.sga.repository.ConteoPorEstado;

/**
 * Indicadores del SGA (Requirement 16, D13).
 *
 * <p>{@link #desde(List, List, ConteoArticulos)} es una funcion pura: crea un
 * {@link EnumMap} con todos los estados en 0, copia las filas agrupadas y
 * calcula cada total como la suma de sus cantidades. {@code porEstado} siempre
 * contiene los seis {@link EstadoPaquete} y los cuatro {@link EstadoDron}.
 */
public record ResumenSgaResponse(
        ConteoEstados<EstadoPaquete> paquetes,
        ConteoEstados<EstadoDron> drones,
        ResumenArticulos articulos) {

    /**
     * Conteo de un dominio finito con todos sus estados presentes.
     */
    public record ConteoEstados<E extends Enum<E>>(Map<E, Long> porEstado, long total) {

        public ConteoEstados {
            porEstado = Collections.unmodifiableMap(new EnumMap<>(porEstado));
        }
    }

    /**
     * Articulos bajo stock minimo y total, en una sola consulta.
     */
    public record ResumenArticulos(long bajoStockMinimo, long total) {
    }

    public static ResumenSgaResponse desde(
            List<ConteoPorEstado<EstadoPaquete>> paquetes,
            List<ConteoPorEstado<EstadoDron>> drones,
            ConteoArticulos articulos) {

        return new ResumenSgaResponse(
                contar(EstadoPaquete.class, paquetes),
                contar(EstadoDron.class, drones),
                new ResumenArticulos(
                        articulos.getBajoStockMinimo(), articulos.getTotal()));
    }

    private static <E extends Enum<E>> ConteoEstados<E> contar(
            Class<E> dominio,
            List<ConteoPorEstado<E>> filas) {

        EnumMap<E, Long> porEstado = new EnumMap<>(dominio);
        for (E estado : dominio.getEnumConstants()) {
            porEstado.put(estado, 0L);
        }

        for (ConteoPorEstado<E> fila : filas) {
            porEstado.put(fila.getClave(), fila.getTotal());
        }

        long total = porEstado.values().stream()
                .mapToLong(Long::longValue)
                .sum();

        return new ConteoEstados<>(porEstado, total);
    }
}
