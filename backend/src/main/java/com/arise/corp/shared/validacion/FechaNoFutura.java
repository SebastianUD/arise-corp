package com.arise.corp.shared.validacion;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.time.ZoneId;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Fecha de calendario que no puede ser posterior a la fecha actual en
 * {@code America/Bogota} (D6, D7).
 *
 * <p>Acepta {@code null}; la obligatoriedad se declara con {@code @NotNull}. La
 * fecha actual sale del {@code Clock} que Bean Validation recibe de la
 * aplicacion (D7), de modo que la regla no depende de la zona del servidor ni
 * de la zona del reloj.
 */
@Documented
@Constraint(validatedBy = FechaNoFutura.Validador.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
public @interface FechaNoFutura {

    ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    String message() default "no puede ser posterior a la fecha actual";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Compara la fecha con el dia calendario de {@code America/Bogota} del
     * reloj del {@code ClockProvider}.
     */
    class Validador implements ConstraintValidator<FechaNoFutura, LocalDate> {

        @Override
        public boolean isValid(
                LocalDate fecha,
                ConstraintValidatorContext contexto) {

            if (fecha == null) {
                return true;
            }

            LocalDate hoy = LocalDate.now(
                    contexto.getClockProvider().getClock().withZone(ZONA_COLOMBIA));

            return !fecha.isAfter(hoy);
        }
    }
}
