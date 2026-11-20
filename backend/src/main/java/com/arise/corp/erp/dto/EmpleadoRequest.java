package com.arise.corp.erp.dto;

import java.time.LocalDate;

import com.arise.corp.erp.dto.validacion.DocumentoErp;
import com.arise.corp.erp.dto.validacion.NumeroDocumentoErpValido;
import com.arise.corp.erp.entity.TipoDocumentoEmpleado;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;
import com.arise.corp.shared.validacion.Correo;
import com.arise.corp.shared.validacion.FechaNoFutura;
import com.arise.corp.shared.validacion.Telefono;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de alta y edicion de un empleado (ERP-01, D5, D14).
 *
 * <p>El identificador, el estado activo y la auditoria los controla el
 * servidor, asi que el DTO ignora las propiedades desconocidas. Los textos se
 * normalizan en el constructor compacto; la obligatoriedad se comprueba con
 * {@code @NotNull} y las reglas de formato aceptan {@code null}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@NumeroDocumentoErpValido
public record EmpleadoRequest(
        @NotNull(message = "es obligatorio") TipoDocumentoEmpleado tipoDocumento,
        @NotNull(message = "es obligatorio") String numeroDocumento,
        @NotNull(message = "es obligatorio")
        @Size(max = 200, message = "debe tener como máximo 200 caracteres")
        String nombre,
        @NotNull(message = "es obligatorio")
        @Size(max = 100, message = "debe tener como máximo 100 caracteres")
        String cargo,
        @NotNull(message = "es obligatorio")
        @Size(max = 100, message = "debe tener como máximo 100 caracteres")
        String area,
        @NotNull(message = "es obligatorio") @Correo String correo,
        @NotNull(message = "es obligatorio") @Telefono String telefono,
        @NotNull(message = "es obligatorio") @FechaNoFutura LocalDate fechaIngreso)
        implements DocumentoErp {

    public EmpleadoRequest {
        numeroDocumento = NormalizadorTexto.documento(numeroDocumento);
        nombre = NormalizadorTexto.texto(nombre);
        cargo = NormalizadorTexto.texto(cargo);
        area = NormalizadorTexto.texto(area);
        correo = NormalizadorTexto.texto(correo);
        telefono = NormalizadorTexto.texto(telefono);
    }

    @Override
    public boolean exigeSoloDigitos() {
        return tipoDocumento == TipoDocumentoEmpleado.CC;
    }

    @Override
    public String mensajeSoloDigitos() {
        return "solo admite dígitos para el tipo CC";
    }

    @Override
    public String toString() {
        return "EmpleadoRequest[datos protegidos]";
    }
}
