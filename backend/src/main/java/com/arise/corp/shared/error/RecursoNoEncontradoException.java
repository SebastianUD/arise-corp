package com.arise.corp.shared.error;

/**
 * Recurso de negocio inexistente.
 *
 * <p>El manejador global la traduce a 404 Problem Details sin revelar si el
 * recurso existe para usuarios sin permiso (CA-06).
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
