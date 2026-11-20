package com.arise.corp.erp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.arise.corp.erp.dto.validacion.RelacionCoherente;
import com.arise.corp.erp.entity.TipoMovimiento;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;
import com.arise.corp.shared.validacion.ValorCopPositivo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de alta y edicion de un movimiento financiero (ERP-03, D14).
 *
 * <p>El mismo DTO se usa en {@code POST} y {@code PUT}: el identificador, el
 * estado y la auditoria los controla el servidor, asi que se ignoran las
 * propiedades desconocidas. El concepto se recorta y la referencia se
 * convierte en la Referencia_Normalizada en el constructor compacto; un texto
 * vacio queda nulo y la obligatoriedad la comprueba {@code @NotNull}.
 *
 * <p>{@link RelacionCoherente} informa sobre {@code clienteId} o
 * {@code proveedorId} cuando la relacion no corresponde al tipo (D8).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@RelacionCoherente
public record MovimientoRequest(
        @NotNull(message = "es obligatorio") TipoMovimiento tipo,
        @NotNull(message = "es obligatorio") LocalDate fecha,
        @NotNull(message = "es obligatorio")
        @Size(min = 3, max = 300, message = "debe tener entre 3 y 300 caracteres")
        String concepto,
        @NotNull(message = "es obligatorio") @ValorCopPositivo BigDecimal valor,
        @Pattern(regexp = "[A-Z0-9 ./-]{1,50}",
                message = "debe tener como máximo 50 caracteres: letras A-Z, "
                        + "dígitos, espacios, puntos, barras o guiones")
        String referencia,
        UUID clienteId,
        UUID proveedorId) {

    public MovimientoRequest {
        concepto = NormalizadorTexto.texto(concepto);
        referencia = NormalizadorTexto.codigo(referencia);
    }
}
