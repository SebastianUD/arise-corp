package com.arise.corp.crm.entity;

/**
 * Estado de un contrato (CRM-03, DP-API-04).
 *
 * <p>Los valores coinciden con la restriccion {@code CHECK} de
 * {@code app.contratos.estado}. Las reglas de transicion entre estados se
 * definen junto a la maquina de estados del dominio.
 */
public enum EstadoContrato {

    BORRADOR,
    ACTIVO,
    FINALIZADO,
    CANCELADO
}
