package com.arise.corp.sga.dto.validacion;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.math.BigDecimal;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Cantidad entera dentro de un rango, recibida como {@link BigDecimal} (D5).
 *
 * <p>Acepta {@code null}: la obligatoriedad se declara con {@code @NotNull}.
 * Un valor con parte fraccionaria ({@code 1.5}) o fuera del rango no es valido;
 * {@code 5}, {@code 5.0} y {@code 5E0} son el mismo entero 5 y se aceptan. El
 * predicado estatico {@link Validador#esEnteroEnRango(BigDecimal, long, long)}
 * se reutiliza en {@code @MovimientoInventarioValido}; vive en el validador
 * porque un tipo anotacion no admite metodos estaticos.
 */
@Documented
@Constraint(validatedBy = CantidadEntera.Validador.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
public @interface CantidadEntera {

    String message() default "debe ser un número entero entre {min} y {max}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    long min() default 1;

    long max() default 1_000_000;

    /**
     * Validador de {@link CantidadEntera}.
     */
    class Validador implements ConstraintValidator<CantidadEntera, BigDecimal> {

        private long min;
        private long max;

        /**
         * {@code true} si el valor no es {@code null}, no tiene parte
         * fraccionaria y esta entre {@code min} y {@code max}, ambos incluidos.
         */
        public static boolean esEnteroEnRango(BigDecimal valor, long min, long max) {
            return valor != null
                    && valor.stripTrailingZeros().scale() <= 0
                    && valor.compareTo(BigDecimal.valueOf(min)) >= 0
                    && valor.compareTo(BigDecimal.valueOf(max)) <= 0;
        }

        @Override
        public void initialize(CantidadEntera anotacion) {
            this.min = anotacion.min();
            this.max = anotacion.max();
        }

        @Override
        public boolean isValid(
                BigDecimal valor,
                ConstraintValidatorContext contexto) {

            return valor == null || esEnteroEnRango(valor, min, max);
        }
    }
}
