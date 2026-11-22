package com.arise.corp.sga.repository;

/**
 * Proyeccion con el total de articulos y cuantos estan bajo stock minimo, en
 * una sola consulta (D13, Requirement 16.3).
 */
public interface ConteoArticulos {

    long getTotal();

    long getBajoStockMinimo();
}
