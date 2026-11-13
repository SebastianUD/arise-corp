package com.arise.corp.crm.dto.validacion;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.LocalDate;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Exige que {@code fechaFin} sea igual o posterior a {@code fechaInicio} en los
 * DTO de contrato (D3).
 *
 * <p>Si falta alguna de las dos fechas no informa nada; esas ausencias las
 * cubren las restricciones de cada campo. Informa la violacion sobre
 * {@code fechaFin}.
 */
@Documented
@Constraint(validatedBy = RangoFechasContrato.Validador.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface RangoFechasContrato {

    String message() default "debe ser igual o posterior a la fecha de inicio";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Contrato comun que exponen los dos DTO de contrato.
     */
    interface Fechas {

        LocalDate fechaInicio();

        LocalDate fechaFin();
    }

    /**
     * Validador del rango de fechas del contrato.
     */
    class Validador implements ConstraintValidator<RangoFechasContrato, Fechas> {

        @Override
        public boolean isValid(Fechas fechas, ConstraintValidatorContext contexto) {
            if (fechas == null
                    || fechas.fechaInicio() == null
                    || fechas.fechaFin() == null) {
                return true;
            }

            if (!fechas.fechaFin().isBefore(fechas.fechaInicio())) {
                return true;
            }

            contexto.disableDefaultConstraintViolation();
            contexto.buildConstraintViolationWithTemplate(
                            contexto.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("fechaFin")
                    .addConstraintViolation();

            return false;
        }
    }
}
