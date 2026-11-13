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

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

/**
 * Valor monetario en COP mayor o igual a 0, con hasta 13 digitos enteros y 2
 * decimales (D3).
 *
 * <p>Restriccion compuesta que acepta {@code null}; la obligatoriedad se
 * declara con {@code @NotNull}. Se comparte con ERP y SGA.
 */
@Documented
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
@ReportAsSingleViolation
@DecimalMin("0.00")
@Digits(integer = 13, fraction = 2)
public @interface ValorCop {

    String message() default
            "debe ser mayor o igual a 0, con hasta 13 dígitos enteros y 2 decimales";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
