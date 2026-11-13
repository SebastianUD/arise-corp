package com.arise.corp.shared.error;

import java.util.Map;

/**
 * Conflicto de negocio detectado en el servicio (409, D5).
 *
 * <p>El codigo {@code conflicto} es estable y esta en
 * {@code UPPER_SNAKE_CASE}; el {@code detalle} ya esta redactado para el
 * usuario y nunca contiene datos personales. Las extensiones acompanan al
 * Problem Details (por ejemplo {@code clienteId} o {@code estadoActual}).
 */
public class ConflictoNegocioException extends RuntimeException {

    private final String conflicto;
    private final String campo;
    private final Map<String, Object> extensiones;

    public ConflictoNegocioException(
            String conflicto,
            String detalle,
            String campo,
            Map<String, Object> extensiones) {

        super(detalle);
        this.conflicto = conflicto;
        this.campo = campo;
        this.extensiones = extensiones != null ? Map.copyOf(extensiones) : Map.of();
    }

    public String getConflicto() {
        return conflicto;
    }

    public String getCampo() {
        return campo;
    }

    public Map<String, Object> getExtensiones() {
        return extensiones;
    }
}
