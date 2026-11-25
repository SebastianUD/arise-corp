package com.arise.corp.sga.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.arise.corp.sga.GeneradoresSga;
import com.arise.corp.sga.entity.EstadoDron;
import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.repository.ConteoArticulos;
import com.arise.corp.sga.repository.ConteoPorEstado;

/**
 * Pruebas de la funcion pura {@link ResumenSgaResponse#desde} (D13,
 * Requirement 16).
 *
 * <p>Cubre la Property 9: todos los estados aparecen (con 0 si faltan), cada
 * total es la suma de sus cantidades y los articulos conservan sus dos valores.
 */
class ResumenSgaResponseTests {

    private static final int CASOS = 120;

    @Test
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 9: Los indicadores incluyen todos los estados y los totales correctos")
    void losIndicadoresIncluyenTodosLosEstadosYLosTotales() {
        Random azar = GeneradoresSga.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            Map<EstadoPaquete, Long> paquetes =
                    GeneradoresSga.conteosPorEstadoConAusentes(
                            azar, EstadoPaquete.class);
            Map<EstadoDron, Long> drones =
                    GeneradoresSga.conteosPorEstadoConAusentes(
                            azar, EstadoDron.class);

            long bajoStockMinimo = azar.nextInt(20);
            long totalArticulos = bajoStockMinimo + azar.nextInt(20);

            ResumenSgaResponse resumen = ResumenSgaResponse.desde(
                    filas(paquetes),
                    filas(drones),
                    new ConteoFicticio(totalArticulos, bajoStockMinimo));

            assertEquals(
                    completo(EstadoPaquete.class, paquetes),
                    resumen.paquetes().porEstado(),
                    "paquetes " + paquetes);
            assertEquals(suma(paquetes), resumen.paquetes().total(),
                    "total de paquetes " + paquetes);

            assertEquals(
                    completo(EstadoDron.class, drones),
                    resumen.drones().porEstado(),
                    "drones " + drones);
            assertEquals(suma(drones), resumen.drones().total(),
                    "total de drones " + drones);

            assertEquals(bajoStockMinimo,
                    resumen.articulos().bajoStockMinimo());
            assertEquals(totalArticulos, resumen.articulos().total());
        }
    }

    @Test
    void losEstadosAusentesSeInformanEnCero() {
        ResumenSgaResponse resumen = ResumenSgaResponse.desde(
                List.of(),
                List.of(),
                new ConteoFicticio(0, 0));

        assertEquals(6, resumen.paquetes().porEstado().size());
        assertEquals(4, resumen.drones().porEstado().size());
        assertEquals(0, resumen.paquetes().total());
        assertEquals(0, resumen.drones().total());
        resumen.paquetes().porEstado().values()
                .forEach(total -> assertEquals(0, total));
        resumen.drones().porEstado().values()
                .forEach(total -> assertEquals(0, total));
    }

    @Test
    void elMapaDeEstadosNoSePuedeModificar() {
        ResumenSgaResponse resumen = ResumenSgaResponse.desde(
                List.of(), List.of(), new ConteoFicticio(0, 0));

        assertThrows(
                UnsupportedOperationException.class,
                () -> resumen.paquetes().porEstado()
                        .put(EstadoPaquete.REGISTRADO, 1L));
    }

    private static long suma(Map<?, Long> conteos) {
        return conteos.values().stream().mapToLong(Long::longValue).sum();
    }

    private static <E extends Enum<E>> Map<E, Long> completo(
            Class<E> dominio,
            Map<E, Long> conteos) {

        Map<E, Long> completo = new EnumMap<>(dominio);
        for (E estado : dominio.getEnumConstants()) {
            completo.put(estado, conteos.getOrDefault(estado, 0L));
        }

        return completo;
    }

    private static <E extends Enum<E>> List<ConteoPorEstado<E>> filas(
            Map<E, Long> conteos) {

        List<ConteoPorEstado<E>> filas = new ArrayList<>();
        conteos.forEach((estado, total) ->
                filas.add(new Fila<>(estado, total)));

        return filas;
    }

    private record Fila<E extends Enum<E>>(E clave, long total)
            implements ConteoPorEstado<E> {

        @Override
        public E getClave() {
            return clave;
        }

        @Override
        public long getTotal() {
            return total;
        }
    }

    private record ConteoFicticio(long total, long bajoStockMinimo)
            implements ConteoArticulos {

        @Override
        public long getTotal() {
            return total;
        }

        @Override
        public long getBajoStockMinimo() {
            return bajoStockMinimo;
        }
    }
}
