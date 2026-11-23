package com.arise.corp.sga.dto.validacion;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.math.BigDecimal;

import com.arise.corp.sga.dto.MovimientoInventarioRequest;
import com.arise.corp.sga.entity.TipoMovimientoInventario;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Valida la cantidad y la observacion de un movimiento de inventario (D5).
 *
 * <p>Si {@code cantidad} es {@code null} no informa nada sobre ella: la
 * obligatoriedad la cubre {@code @NotNull}. Si no, aplica
 * {@link CantidadEntera.Validador#esEnteroEnRango(BigDecimal, long, long)} con
 * el minimo del tipo ({@code 0} en {@code AJUSTE} y {@code 1} en los demas;
 * {@code 0} si {@code tipo} es {@code null}) y el maximo de 1 000 000. Un
 * {@code AJUSTE} sin observacion informa sobre {@code observacion}. Cada campo
 * produce como maximo un error (Requirements 15.6-15.8).
 */
@Documented
@Constraint(validatedBy = MovimientoInventarioValido.Validador.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface MovimientoInventarioValido {

    String message() default "el movimiento de inventario no es válido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Validador de clase de {@link MovimientoInventarioRequest}.
     */
    class Validador implements
            ConstraintValidator<MovimientoInventarioValido, MovimientoInventarioRequest> {

        private static final long MAXIMO = 1_000_000;

        @Override
        public boolean isValid(
                MovimientoInventarioRequest solicitud,
                ConstraintValidatorContext contexto) {

            if (solicitud == null) {
                return true;
            }

            boolean valido = true;

            BigDecimal cantidad = solicitud.cantidad();
            if (cantidad != null) {
                long min = solicitud.tipo() == null
                        ? 0
                        : solicitud.tipo().cantidadMinima();

                if (!CantidadEntera.Validador.esEnteroEnRango(cantidad, min, MAXIMO)) {
                    informar(contexto, "cantidad",
                            "debe ser un número entero entre " + min
                                    + " y " + MAXIMO);
                    valido = false;
                }
            }

            if (solicitud.tipo() == TipoMovimientoInventario.AJUSTE
                    && solicitud.observacion() == null) {

                informar(contexto, "observacion", "es obligatoria en un ajuste");
                valido = false;
            }

            return valido;
        }

        private static void informar(
                ConstraintValidatorContext contexto,
                String campo,
                String mensaje) {

            contexto.disableDefaultConstraintViolation();
            contexto.buildConstraintViolationWithTemplate(mensaje)
                    .addPropertyNode(campo)
                    .addConstraintViolation();
        }
    }
}
