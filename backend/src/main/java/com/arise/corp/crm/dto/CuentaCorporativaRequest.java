package com.arise.corp.crm.dto;

import com.arise.corp.shared.normalizacion.NormalizadorTexto;
import com.arise.corp.shared.validacion.Correo;
import com.arise.corp.shared.validacion.Telefono;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de alta y edicion de una cuenta corporativa (CRM-02).
 *
 * <p>El estado activo y la auditoria los controla el servidor. El NIT y los
 * textos se normalizan en el constructor compacto (D2).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CuentaCorporativaRequest(
        @NotNull(message = "es obligatorio")
        @Pattern(regexp = "[0-9]{5,15}",
                message = "debe tener entre 5 y 15 dígitos")
        String nit,
        @NotNull(message = "es obligatorio")
        @Size(max = 200, message = "debe tener como máximo 200 caracteres")
        String razonSocial,
        @Size(max = 200, message = "debe tener como máximo 200 caracteres")
        String nombreContacto,
        @Correo String correoContacto,
        @Telefono String telefonoContacto) {

    public CuentaCorporativaRequest {
        nit = NormalizadorTexto.documento(nit);
        razonSocial = NormalizadorTexto.texto(razonSocial);
        nombreContacto = NormalizadorTexto.texto(nombreContacto);
        correoContacto = NormalizadorTexto.texto(correoContacto);
        telefonoContacto = NormalizadorTexto.texto(telefonoContacto);
    }

    @Override
    public String toString() {
        return "CuentaCorporativaRequest[datos protegidos]";
    }
}
