package com.arise.corp.erp.dto.validacion;

/**
 * Contrato minimo que necesitan las solicitudes del ERP para validar su
 * {@code numeroDocumento} ya normalizado (D4).
 *
 * <p>Lo implementan {@code EmpleadoRequest} y {@code ProveedorRequest}; el
 * validador de clase {@link NumeroDocumentoErpValido} informa el error sobre el
 * nodo {@code numeroDocumento}.
 */
public interface DocumentoErp {

    /**
     * Documento ya normalizado; puede ser {@code null} cuando falta.
     */
    String numeroDocumento();

    /**
     * Indica si el tipo de documento enviado exige solo digitos; {@code false}
     * si el tipo es {@code null}.
     */
    boolean exigeSoloDigitos();

    /**
     * Mensaje de la regla de solo digitos para el tipo enviado.
     */
    String mensajeSoloDigitos();
}
