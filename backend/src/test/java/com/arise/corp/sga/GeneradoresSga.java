package com.arise.corp.sga;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.arise.corp.sga.entity.TipoMovimientoInventario;

/**
 * Generadores con semilla fija para las pruebas de propiedades del SGA (D17).
 *
 * <p>No se anade ninguna libreria de pruebas basadas en propiedades: cada
 * propiedad ejecuta al menos 100 casos generados con {@link #SEMILLA} y el
 * mensaje de la asercion incluye la entrada que fallo, de modo que un fallo sea
 * reproducible. Los generadores de texto y fecha se reutilizan de
 * {@code GeneradoresCrm}; aqui solo se anaden los del dominio del SGA.
 */
public final class GeneradoresSga {

    public static final long SEMILLA = 20261125L;

    private static final int CANTIDAD_MAXIMA = 1_000_000;

    private GeneradoresSga() {
    }

    public static Random nuevoRandom() {
        return new Random(SEMILLA);
    }

    /**
     * Secuencia de movimientos sobre un articulo con existencia inicial 0.
     * Incluye salidas que a veces superan la existencia disponible, para que la
     * prueba de Property 7 compruebe el rechazo.
     */
    public static List<MovimientoGenerado> secuenciaDeMovimientos(int cantidad) {
        return secuenciaDeMovimientos(cantidad, 0L);
    }

    /**
     * Secuencia de movimientos sobre un articulo con la existencia inicial
     * indicada. El generador simula la aritmetica para producir salidas
     * validas y salidas que superan la existencia disponible.
     */
    public static List<MovimientoGenerado> secuenciaDeMovimientos(
            int cantidad,
            long existenciaInicial) {

        Random azar = nuevoRandom();
        List<MovimientoGenerado> movimientos = new ArrayList<>(cantidad);
        long existencia = existenciaInicial;

        for (int i = 0; i < cantidad; i++) {
            MovimientoGenerado movimiento = movimiento(azar, existencia);
            movimientos.add(movimiento);
            existencia = aplicarSiEsValido(movimiento, existencia);
        }

        return movimientos;
    }

    /**
     * Movimiento generado con una cantidad valida para su tipo. Una
     * {@code SALIDA} supera a veces la existencia disponible.
     */
    public static MovimientoGenerado movimiento(Random azar, long existencia) {
        TipoMovimientoInventario tipo = TipoMovimientoInventario.values()[
                azar.nextInt(TipoMovimientoInventario.values().length)];

        int cantidad = switch (tipo) {
            case ENTRADA -> 1 + azar.nextInt(20);
            case AJUSTE -> azar.nextInt(31);
            case SALIDA -> azar.nextInt(4) == 0
                    ? (int) Math.min(
                            CANTIDAD_MAXIMA,
                            1 + azar.nextInt(10) + Math.max(0L, existencia))
                    : 1 + azar.nextInt(1 + (int) Math.min(
                            CANTIDAD_MAXIMA - 1, Math.max(0L, existencia)));
        };

        return new MovimientoGenerado(tipo, cantidad);
    }

    /**
     * Movimiento de una secuencia valida para su tipo y con resultado no
     * negativo.
     */
    public static MovimientoGenerado movimientoValido(Random azar, long existencia) {
        MovimientoGenerado movimiento;

        do {
            movimiento = movimiento(azar, existencia);
        } while (movimiento.tipo() == TipoMovimientoInventario.SALIDA
                && movimiento.cantidad() > existencia);

        return movimiento;
    }

    private static long aplicarSiEsValido(MovimientoGenerado movimiento, long existencia) {
        long resultante = movimiento.tipo()
                .existenciaResultante(existencia, movimiento.cantidad());

        return resultante < 0 ? existencia : resultante;
    }

    /**
     * Valor numerico del que se desconoce si es un entero valido: enteros,
     * decimales con parte fraccionaria, negativos, notacion exponencial y los
     * bordes 0, 1, 1000000 y 1000001.
     */
    public static BigDecimal cantidadDeCualquierForma(Random azar) {
        return switch (azar.nextInt(9)) {
            case 0 -> BigDecimal.ZERO;
            case 1 -> BigDecimal.ONE;
            case 2 -> BigDecimal.valueOf(CANTIDAD_MAXIMA);
            case 3 -> BigDecimal.valueOf(CANTIDAD_MAXIMA + 1L);
            case 4 -> BigDecimal.valueOf(1 + azar.nextInt(CANTIDAD_MAXIMA));
            case 5 -> BigDecimal.valueOf(1 + azar.nextInt(CANTIDAD_MAXIMA))
                    .add(BigDecimal.valueOf(1 + azar.nextInt(99), 2));
            case 6 -> BigDecimal.valueOf(-1 - azar.nextInt(CANTIDAD_MAXIMA));
            case 7 -> new BigDecimal("1E" + azar.nextInt(7));
            default -> BigDecimal.valueOf(1 + azar.nextInt(5))
                    .movePointLeft(1 + azar.nextInt(3));
        };
    }

    /**
     * Bordes de las reglas de cantidad: 0, 1, 1000000 y 1000001.
     */
    public static List<BigDecimal> bordesDeCantidad() {
        return List.of(
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.valueOf(CANTIDAD_MAXIMA),
                BigDecimal.valueOf(CANTIDAD_MAXIMA + 1L));
    }

    /**
     * Conteos por estado de un dominio finito con estados ausentes: cada
     * constante aparece o no en el mapa, y las presentes tienen un total no
     * negativo.
     */
    public static <E extends Enum<E>> Map<E, Long> conteosPorEstadoConAusentes(
            Random azar,
            Class<E> dominio) {

        Map<E, Long> conteos = new EnumMap<>(dominio);

        for (E estado : dominio.getEnumConstants()) {
            if (azar.nextBoolean()) {
                conteos.put(estado, (long) azar.nextInt(50));
            }
        }

        return conteos;
    }

    /**
     * Mapa completo del dominio con 0 en todos los estados, para comparar con
     * el calculo directo de los indicadores.
     */
    public static <E extends Enum<E>> Map<E, Long> conteosEnCero(Class<E> dominio) {
        Map<E, Long> conteos = new EnumMap<>(dominio);

        for (E estado : dominio.getEnumConstants()) {
            conteos.put(estado, 0L);
        }

        return conteos;
    }

    /**
     * Movimiento generado; el tipo es la enumeracion del SGA y la cantidad es
     * un entero valido para su tipo.
     */
    public record MovimientoGenerado(TipoMovimientoInventario tipo, int cantidad) {
    }
}
