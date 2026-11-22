package com.arise.corp.sga.repository;

/**
 * Proyeccion (estado, total) de una consulta agrupada del SGA (D13).
 *
 * <p>El SGA declara su propia proyeccion: no reutiliza
 * {@code crm.repository.ConteoPorClave} (Requirement 1.5).
 */
public interface ConteoPorEstado<E> {

    E getClave();

    long getTotal();
}
