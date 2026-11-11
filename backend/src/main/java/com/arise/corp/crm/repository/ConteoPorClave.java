package com.arise.corp.crm.repository;

/**
 * Proyeccion generica de las consultas agrupadas del CRM (CRM-05).
 *
 * <p>Cada fila contiene la clave agrupada y su total. Se usa en el resumen
 * comercial para no depender de consultas adicionales de totales.
 *
 * @param <K> tipo de la clave agrupada
 */
public interface ConteoPorClave<K> {

    K getClave();

    long getTotal();
}
