package com.arise.corp.crm.dto;

import java.util.UUID;

import com.arise.corp.crm.dto.validacion.NumeroDocumentoValido;
import com.arise.corp.crm.entity.TipoDocumento;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;
import com.arise.corp.shared.validacion.Correo;
import com.arise.corp.shared.validacion.Telefono;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de alta y edicion de un cliente (CRM-01, CRM-04).
 *
 * <p>El identificador, el estado activo y la auditoria los controla el
 * servidor, asi que el DTO ignora las propiedades desconocidas. Los textos se
 * normalizan en el constructor compacto (D2).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@NumeroDocumentoValido
public record ClienteRequest(
        @NotNull(message = "es obligatorio") TipoDocumento tipoDocumento,
        @NotNull(message = "es obligatorio") String numeroDocumento,
        @NotNull(message = "es obligatorio")
        @Size(max = 200, message = "debe tener como máximo 200 caracteres")
        String nombre,
        @NotNull(message = "es obligatorio") @Correo String correo,
        @NotNull(message = "es obligatorio") @Telefono String telefono,
        @Size(max = 200, message = "debe tener como máximo 200 caracteres")
        String direccion,
        @Size(max = 100, message = "debe tener como máximo 100 caracteres")
        String ciudad,
        UUID cuentaCorporativaId) {

    public ClienteRequest {
        numeroDocumento = NormalizadorTexto.documento(numeroDocumento);
        nombre = NormalizadorTexto.texto(nombre);
        correo = NormalizadorTexto.texto(correo);
        telefono = NormalizadorTexto.texto(telefono);
        direccion = NormalizadorTexto.texto(direccion);
        ciudad = NormalizadorTexto.texto(ciudad);
    }

    @Override
    public String toString() {
        return "ClienteRequest[datos protegidos]";
    }
}
