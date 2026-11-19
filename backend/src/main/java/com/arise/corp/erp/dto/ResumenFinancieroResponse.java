package com.arise.corp.erp.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.TipoMovimiento;
import com.arise.corp.erp.repository.TotalMovimientos;

/**
 * Panel financiero del ERP (ERP-04, D10).
 *
 * <p>Contiene siempre los dos tipos y los tres estados, incluso con cantidad
 * cero y suma {@code "0.00"} (Requirement 18.2). La cantidad del tipo es la
 * suma de sus estados y {@code saldoPagado} es la diferencia de las sumas
 * pagadas de ingresos y egresos. Las sumas viajan como texto con dos
 * decimales (Requirements 18.6-18.8).
 */
public record ResumenFinancieroResponse(
        LocalDate desde,
        LocalDate hasta,
        Map<TipoMovimiento, ResumenTipo> porTipo,
        String saldoPagado) {

    /**
     * Cantidad y desglose por estado de un tipo de movimiento.
     */
    public record ResumenTipo(
            Map<EstadoMovimiento, ResumenEstado> porEstado,
            long cantidad) {
    }

    /**
     * Cantidad y suma de una combinacion de tipo y estado.
     */
    public record ResumenEstado(long cantidad, String suma) {
    }

    /**
     * Funcion pura que construye el resumen a partir de las filas agrupadas.
     *
     * @param desde limite inferior aplicado, o {@code null}
     * @param hasta limite superior aplicado, o {@code null}
     * @param filas una fila como maximo por combinacion de tipo y estado
     * @return el resumen con las seis combinaciones y el saldo pagado
     */
    public static ResumenFinancieroResponse desde(
            LocalDate desde,
            LocalDate hasta,
            List<TotalMovimientos> filas) {

        EnumMap<TipoMovimiento, EnumMap<EstadoMovimiento, BigDecimal>> sumas =
                new EnumMap<>(TipoMovimiento.class);
        EnumMap<TipoMovimiento, EnumMap<EstadoMovimiento, Long>> cantidades =
                new EnumMap<>(TipoMovimiento.class);

        for (TipoMovimiento tipo : TipoMovimiento.values()) {
            EnumMap<EstadoMovimiento, BigDecimal> sumasTipo =
                    new EnumMap<>(EstadoMovimiento.class);
            EnumMap<EstadoMovimiento, Long> cantidadesTipo =
                    new EnumMap<>(EstadoMovimiento.class);

            for (EstadoMovimiento estado : EstadoMovimiento.values()) {
                sumasTipo.put(estado, BigDecimal.ZERO);
                cantidadesTipo.put(estado, 0L);
            }

            sumas.put(tipo, sumasTipo);
            cantidades.put(tipo, cantidadesTipo);
        }

        for (TotalMovimientos fila : filas) {
            sumas.get(fila.tipo()).merge(
                    fila.estado(),
                    fila.suma() == null ? BigDecimal.ZERO : fila.suma(),
                    BigDecimal::add);
            cantidades.get(fila.tipo()).merge(
                    fila.estado(),
                    fila.cantidad() == null ? 0L : fila.cantidad(),
                    Long::sum);
        }

        EnumMap<TipoMovimiento, ResumenTipo> porTipo =
                new EnumMap<>(TipoMovimiento.class);

        for (TipoMovimiento tipo : TipoMovimiento.values()) {
            EnumMap<EstadoMovimiento, ResumenEstado> porEstado =
                    new EnumMap<>(EstadoMovimiento.class);
            long cantidadTipo = 0L;

            for (EstadoMovimiento estado : EstadoMovimiento.values()) {
                long cantidad = cantidades.get(tipo).get(estado);
                porEstado.put(estado, new ResumenEstado(
                        cantidad, texto(sumas.get(tipo).get(estado))));
                cantidadTipo += cantidad;
            }

            porTipo.put(tipo, new ResumenTipo(porEstado, cantidadTipo));
        }

        BigDecimal saldo = sumas.get(TipoMovimiento.INGRESO)
                .get(EstadoMovimiento.PAGADO)
                .subtract(sumas.get(TipoMovimiento.EGRESO)
                        .get(EstadoMovimiento.PAGADO));

        return new ResumenFinancieroResponse(desde, hasta, porTipo, texto(saldo));
    }

    private static String texto(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
