package com.arise.corp.sga.entity;

/**
 * Tipo de movimiento de inventario (SGA-03).
 *
 * <p>La aritmetica de la existencia es una funcion pura (D12): {@code ENTRADA}
 * suma, {@code SALIDA} resta y {@code AJUSTE} fija la existencia contada. El
 * servicio rechaza un resultado negativo antes de tocar el articulo
 * (Requirements 15.2-15.5).
 */
public enum TipoMovimientoInventario {
    ENTRADA,
    SALIDA,
    AJUSTE;

    public long existenciaResultante(long anterior, int cantidad) {
        return switch (this) {
            case ENTRADA -> anterior + cantidad;
            case SALIDA -> anterior - cantidad;
            case AJUSTE -> cantidad;
        };
    }

    /**
     * Cantidad minima admitida: 0 en un conteo ({@code AJUSTE}) y 1 en los
     * movimientos que suman o restan (Requirement 15.7).
     */
    public int cantidadMinima() {
        return this == AJUSTE ? 0 : 1;
    }
}
