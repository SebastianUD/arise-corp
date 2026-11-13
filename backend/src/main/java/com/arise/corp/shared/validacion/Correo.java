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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Correo electronico de maximo 254 caracteres (D3).
 *
 * <p>Restriccion compuesta que acepta {@code null}; la obligatoriedad se
 * declara con {@code @NotNull}. Se comparte con ERP y SGA.
 */
@Documented
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
@ReportAsSingleViolation
@Email(regexp = ".+@.+\\..+")
@Size(max = 254)
public @interface Correo {

    String message() default "debe ser un correo válido de máximo 254 caracteres";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
