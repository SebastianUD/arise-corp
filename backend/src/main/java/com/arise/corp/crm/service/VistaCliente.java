package com.arise.corp.crm.service;

/**
 * Vista del cliente que corresponde al actor (D6).
 *
 * <p>La proteccion real es la del backend: un error de configuracion devuelve
 * {@link #REDUCIDA}, de modo que nunca expone datos personales.
 */
public enum VistaCliente {

    COMPLETA,
    REDUCIDA
}
