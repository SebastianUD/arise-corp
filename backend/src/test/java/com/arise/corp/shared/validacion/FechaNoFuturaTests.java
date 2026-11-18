package com.arise.corp.shared.validacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.arise.corp.erp.GeneradoresErp;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Pruebas de {@link FechaNoFutura} (Requirements 4.7, 25.1).
 *
 * <p>El validador se construye con un {@code ClockProvider} fijo, igual que las
 * pruebas de DTO del ERP. El instante de referencia
 * {@code 2026-11-21T03:30:00Z} ya es 20 de noviembre en {@code America/Bogota},
 * asi que demuestra que la regla usa la zona de negocio y no la de UTC.
 */
class FechaNoFuturaTests {

    private static final int CASOS = 120;

    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    private static final Clock RELOJ_FIJO =
            Clock.fixed(Instant.parse("2026-11-21T03:30:00Z"), ZoneId.of("UTC"));

    @Test
    void aceptaLaFechaDeHoyEnBogotaYRechazaLaDeManana() {
        Validator validador = validadorCon(RELOJ_FIJO);

        assertTrue(violaciones(validador, LocalDate.of(2026, 11, 20)).isEmpty());
        assertEquals(
                Set.of("no puede ser posterior a la fecha actual"),
                mensajes(violaciones(validador, LocalDate.of(2026, 11, 21))));
    }

    @Test
    void aceptaUnaFechaNula() {
        Validator validador = validadorCon(RELOJ_FIJO);

        assertTrue(violaciones(validador, null).isEmpty());
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void laFechaDeIngresoCoincideConLaFechaDeBogotaDelReloj() {
        Random azar = GeneradoresErp.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            Instant instante = GeneradoresErp.relojAleatorio(azar);
            Clock reloj = Clock.fixed(instante, ZoneId.of("UTC"));
            LocalDate hoy = ZonedDateTime.ofInstant(instante, ZONA_COLOMBIA)
                    .toLocalDate();
            Validator validador = validadorCon(reloj);

            for (LocalDate fecha : GeneradoresErp.fechaAlrededorDe(hoy)) {
                boolean esperado = !fecha.isAfter(hoy);

                assertEquals(
                        esperado,
                        violaciones(validador, fecha).isEmpty(),
                        "fecha " + fecha + " con el reloj " + instante);
            }
        }
    }

    private static Validator validadorCon(Clock reloj) {
        return Validation.byDefaultProvider()
                .configure()
                .clockProvider(() -> reloj)
                .buildValidatorFactory()
                .getValidator();
    }

    private static Set<ConstraintViolation<FechaIngreso>> violaciones(
            Validator validador,
            LocalDate fecha) {

        return validador.validate(new FechaIngreso(fecha));
    }

    private static Set<String> mensajes(
            Set<ConstraintViolation<FechaIngreso>> violaciones) {

        return violaciones.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    /**
     * DTO minimo para ejercitar la restriccion sobre una fecha de ingreso.
     */
    private record FechaIngreso(@FechaNoFutura LocalDate fecha) {
    }
}
