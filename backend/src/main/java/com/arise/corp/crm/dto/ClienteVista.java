package com.arise.corp.crm.dto;

/**
 * Vista del cliente que se devuelve segun el rol del actor (D6).
 *
 * <p>Jackson serializa el tipo real de cada elemento. La eleccion se hace en el
 * controlador a partir de las authorities, nunca a partir de un parametro de la
 * peticion (Requirement 6.3).
 */
public sealed interface ClienteVista permits ClienteResponse, ClienteResumenResponse {
}
