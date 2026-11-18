package com.arise.corp.erp.dto.validacion;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Valida el {@code numeroDocumento} ya normalizado de una solicitud del ERP
 * (D4).
 *
 * <p>Si el documento es {@code null} no informa nada: la obligatoriedad la
 * cubre {@code @NotNull}. Informa la violacion sobre el nodo
 * {@code numeroDocumento}, de modo que Spring la convierte en un error de
 * campo. La regla es la misma para empleados y proveedores; solo cambia el
 * mensaje y que tipos exigen solo digitos. {@code NumeroDocumentoValido} del
 * CRM no cambia.
 */
@Documented
@Constraint(validatedBy = NumeroDocumentoErpValido.Validador.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface NumeroDocumentoErpValido {

    String message() default "debe tener entre 3 y 20 letras o dígitos";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Validador de clase del {@code numeroDocumento}.
     */
    class Validador implements
            ConstraintValidator<NumeroDocumentoErpValido, DocumentoErp> {

        private static final Pattern FORMATO = Pattern.compile("[A-Z0-9]{3,20}");
        private static final Pattern SOLO_DIGITOS = Pattern.compile("[0-9]+");

        @Override
        public boolean isValid(
                DocumentoErp solicitud,
                ConstraintValidatorContext contexto) {

            if (solicitud == null || solicitud.numeroDocumento() == null) {
                return true;
            }

            String documento = solicitud.numeroDocumento();
            String mensaje = null;

            if (!FORMATO.matcher(documento).matches()) {
                mensaje = "debe tener entre 3 y 20 letras o dígitos";
            } else if (solicitud.exigeSoloDigitos()
                    && !SOLO_DIGITOS.matcher(documento).matches()) {
                mensaje = solicitud.mensajeSoloDigitos();
            }

            if (mensaje == null) {
                return true;
            }

            contexto.disableDefaultConstraintViolation();
            contexto.buildConstraintViolationWithTemplate(mensaje)
                    .addPropertyNode("numeroDocumento")
                    .addConstraintViolation();

            return false;
        }
    }
}
