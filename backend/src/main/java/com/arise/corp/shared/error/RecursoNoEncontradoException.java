package com.arise.corp.shared.error;

/**
 * Recurso de negocio inexistente.
 *
 * <p>El manejador global la traduce a 404 Problem Details sin revelar si el
 * recurso existe para usuarios sin permiso (CA-06). Cuando la referencia llega
 * en el cuerpo de la peticion, {@code campo} indica el campo afectado para que
 * el formulario muestre el error junto a el.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    private final String campo;

    public RecursoNoEncontradoException(String mensaje) {
        this(mensaje, null);
    }

    public RecursoNoEncontradoException(String mensaje, String campo) {
        super(mensaje);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
