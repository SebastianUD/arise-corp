package com.arise.corp.shared.error;

/**
 * Parametro de consulta invalido detectado en el servicio (D9).
 *
 * <p>El manejador global la traduce a 400 Problem Details con el nombre del
 * parametro afectado en {@code errors[0].field} y el mensaje recibido en
 * {@code errors[0].message}. No se registra nada en el log porque el mensaje
 * describe un error de la solicitud, no del servidor.
 */
public class ParametroInvalidoException extends RuntimeException {

    private final String parametro;

    public ParametroInvalidoException(String parametro, String mensaje) {
        super(mensaje);
        this.parametro = parametro;
    }

    public String getParametro() {
        return parametro;
    }
}
