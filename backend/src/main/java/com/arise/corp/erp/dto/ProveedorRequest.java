package com.arise.corp.erp.dto;

import com.arise.corp.erp.dto.validacion.DocumentoErp;
import com.arise.corp.erp.dto.validacion.NumeroDocumentoErpValido;
import com.arise.corp.erp.entity.TipoDocumentoProveedor;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;
import com.arise.corp.shared.validacion.Correo;
import com.arise.corp.shared.validacion.Telefono;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de alta y edicion de un proveedor (ERP-02, D5, D14).
 *
 * <p>Misma forma que {@code EmpleadoRequest}. Los datos de contacto son
 * opcionales: un texto vacio se normaliza a {@code null}. El NIT y la cedula
 * exigen solo digitos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@NumeroDocumentoErpValido
public record ProveedorRequest(
        @NotNull(message = "es obligatorio") TipoDocumentoProveedor tipoDocumento,
        @NotNull(message = "es obligatorio") String numeroDocumento,
        @NotNull(message = "es obligatorio")
        @Size(max = 200, message = "debe tener como máximo 200 caracteres")
        String nombre,
        @Size(max = 200, message = "debe tener como máximo 200 caracteres")
        String nombreContacto,
        @NotNull(message = "es obligatorio") @Correo String correo,
        @NotNull(message = "es obligatorio") @Telefono String telefono,
        @Size(max = 200, message = "debe tener como máximo 200 caracteres")
        String direccion,
        @Size(max = 100, message = "debe tener como máximo 100 caracteres")
        String ciudad)
        implements DocumentoErp {

    public ProveedorRequest {
        numeroDocumento = NormalizadorTexto.documento(numeroDocumento);
        nombre = NormalizadorTexto.texto(nombre);
        nombreContacto = NormalizadorTexto.texto(nombreContacto);
        correo = NormalizadorTexto.texto(correo);
        telefono = NormalizadorTexto.texto(telefono);
        direccion = NormalizadorTexto.texto(direccion);
        ciudad = NormalizadorTexto.texto(ciudad);
    }

    @Override
    public boolean exigeSoloDigitos() {
        return tipoDocumento == TipoDocumentoProveedor.CC
                || tipoDocumento == TipoDocumentoProveedor.NIT;
    }

    @Override
    public String mensajeSoloDigitos() {
        return "solo admite dígitos para el tipo CC o NIT";
    }

    @Override
    public String toString() {
        return "ProveedorRequest[datos protegidos]";
    }
}
