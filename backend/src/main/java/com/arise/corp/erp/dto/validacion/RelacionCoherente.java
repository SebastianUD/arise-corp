package com.arise.corp.erp.dto.validacion;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.arise.corp.erp.dto.MovimientoRequest;
import com.arise.corp.erp.entity.TipoMovimiento;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Comprueba que la relacion del movimiento sea coherente con su tipo (D8).
 *
 * <p>Si {@code tipo} es {@code null} no informa nada: la obligatoriedad ya la
 * cubre {@code @NotNull}. Con {@code EGRESO} y {@code clienteId} informa sobre
 * {@code clienteId}; con {@code INGRESO} y {@code proveedorId} informa sobre
 * {@code proveedorId}. La entidad repite la comprobacion como defensa y el
 * esquema la refuerza con un {@code CHECK}.
 */
@Documented
@Constraint(validatedBy = RelacionCoherente.Validador.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface RelacionCoherente {

    String message() default "la relación no es coherente con el tipo de movimiento";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Validador de clase de {@link MovimientoRequest}.
     */
    class Validador implements
            ConstraintValidator<RelacionCoherente, MovimientoRequest> {

        @Override
        public boolean isValid(
                MovimientoRequest solicitud,
                ConstraintValidatorContext contexto) {

            if (solicitud == null || solicitud.tipo() == null) {
                return true;
            }

            boolean valido = true;

            if (solicitud.tipo() == TipoMovimiento.EGRESO
                    && solicitud.clienteId() != null) {

                informar(contexto, "clienteId",
                        "solo se admite en movimientos de tipo INGRESO");
                valido = false;
            }

            if (solicitud.tipo() == TipoMovimiento.INGRESO
                    && solicitud.proveedorId() != null) {

                informar(contexto, "proveedorId",
                        "solo se admite en movimientos de tipo EGRESO");
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
