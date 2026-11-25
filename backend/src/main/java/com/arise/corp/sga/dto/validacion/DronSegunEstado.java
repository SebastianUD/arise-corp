package com.arise.corp.sga.dto.validacion;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.arise.corp.sga.dto.TransicionPaqueteRequest;
import com.arise.corp.sga.entity.EstadoPaquete;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Comprueba la coherencia entre el estado destino y el dron (D6).
 *
 * <p>Si {@code estado} es {@code null} no informa nada: la obligatoriedad ya la
 * cubre {@code @NotNull}. Con {@code ASIGNADO} y {@code dronId == null} informa
 * que el dron es obligatorio; con otro estado y {@code dronId != null} informa
 * que solo se admite al asignar. Ambos errores se asocian al nodo
 * {@code dronId} (Requirements 11.1, 11.2).
 */
@Documented
@Constraint(validatedBy = DronSegunEstado.Validador.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface DronSegunEstado {

    String message() default "la relación entre el estado y el dron no es válida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Validador de clase de {@link TransicionPaqueteRequest}.
     */
    class Validador implements
            ConstraintValidator<DronSegunEstado, TransicionPaqueteRequest> {

        @Override
        public boolean isValid(
                TransicionPaqueteRequest solicitud,
                ConstraintValidatorContext contexto) {

            if (solicitud == null || solicitud.estado() == null) {
                return true;
            }

            if (solicitud.estado() == EstadoPaquete.ASIGNADO
                    && solicitud.dronId() == null) {

                informar(contexto, "es obligatorio para asignar el paquete");
                return false;
            }

            if (solicitud.estado() != EstadoPaquete.ASIGNADO
                    && solicitud.dronId() != null) {

                informar(contexto, "solo se admite en la transición a ASIGNADO");
                return false;
            }

            return true;
        }

        private static void informar(
                ConstraintValidatorContext contexto,
                String mensaje) {

            contexto.disableDefaultConstraintViolation();
            contexto.buildConstraintViolationWithTemplate(mensaje)
                    .addPropertyNode("dronId")
                    .addConstraintViolation();
        }
    }
}
