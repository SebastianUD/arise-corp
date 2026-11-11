package com.arise.corp.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.arise.corp.crm.dto.validacion.RangoFechasContrato;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;
import com.arise.corp.shared.validacion.ValorCop;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de alta de un contrato (CRM-03, CRM-04).
 *
 * <p>El estado, el identificador y la auditoria los controla el servidor. El
 * codigo y la descripcion se normalizan en el constructor compacto (D2).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@RangoFechasContrato
public record ContratoCrearRequest(
        @NotNull(message = "es obligatorio") UUID clienteId,
        @NotNull(message = "es obligatorio")
        @Pattern(regexp = "[A-Z0-9-]{3,30}",
                message = "debe tener entre 3 y 30 caracteres: letras, dígitos o guiones")
        String codigo,
        @NotNull(message = "es obligatorio")
        @Size(max = 1000, message = "debe tener como máximo 1000 caracteres")
        String descripcion,
        @NotNull(message = "es obligatorio") LocalDate fechaInicio,
        LocalDate fechaFin,
        @NotNull(message = "es obligatorio") @ValorCop BigDecimal valor)
        implements RangoFechasContrato.Fechas {

    public ContratoCrearRequest {
        codigo = NormalizadorTexto.codigo(codigo);
        descripcion = NormalizadorTexto.texto(descripcion);
    }
}
