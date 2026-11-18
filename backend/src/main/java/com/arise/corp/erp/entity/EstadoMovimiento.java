package com.arise.corp.erp.entity;

/**
 * Estado de un movimiento financiero (ERP-03).
 *
 * <p>Aqui solo viven las constantes. La Transicion_Permitida_Movimiento
 * ({@code PENDIENTE -> PAGADO} y {@code PENDIENTE -> ANULADO}) se anade en la
 * tarea 6.1 junto con las defensas de la entidad (D11).
 */
public enum EstadoMovimiento {
    PENDIENTE,
    PAGADO,
    ANULADO
}
