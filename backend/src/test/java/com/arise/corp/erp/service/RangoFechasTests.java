package com.arise.corp.erp.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.arise.corp.crm.GeneradoresCrm;
import com.arise.corp.erp.GeneradoresErp;
import com.arise.corp.shared.error.ParametroInvalidoException;

/**
 * Pruebas de {@link RangoFechas} (D9, Property 15).
 *
 * <p>El record lanza {@link ParametroInvalidoException} sobre {@code hasta} si
 * y solo si ambos limites existen y {@code desde} es posterior a
 * {@code hasta}.
 */
class RangoFechasTests {

    private static final int CASOS = 120;

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 15: Los parámetros de lista y de rango se aceptan o rechazan según la regla")
    void aceptaLosRangosGenerados() {
        Random azar = GeneradoresErp.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            GeneradoresErp.RangoGenerado rango = GeneradoresErp.rango(azar);

            assertDoesNotThrow(
                    () -> new RangoFechas(rango.desde(), rango.hasta()),
                    "rango " + rango.desde() + ".." + rango.hasta());
        }
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 15: Los parámetros de lista y de rango se aceptan o rechazan según la regla")
    void soloRechazaElRangoInvertidoConAmbosLimites() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            LocalDate primero = GeneradoresCrm.fecha(azar);
            LocalDate segundo = GeneradoresCrm.fecha(azar);
            boolean invertido = primero.isAfter(segundo);

            if (invertido) {
                ParametroInvalidoException error = assertThrows(
                        ParametroInvalidoException.class,
                        () -> new RangoFechas(primero, segundo),
                        "rango " + primero + ".." + segundo);

                assertEquals("hasta", error.getParametro());
                assertEquals(
                        "debe ser igual o posterior a la fecha desde",
                        error.getMessage());
            } else {
                assertDoesNotThrow(
                        () -> new RangoFechas(primero, segundo),
                        "rango " + primero + ".." + segundo);
            }
        }
    }

    @Test
    void aceptaCadaLimitePorSeparadoYElMismoDia() {
        LocalDate fecha = LocalDate.of(2026, 11, 18);

        assertDoesNotThrow(() -> new RangoFechas(fecha, null));
        assertDoesNotThrow(() -> new RangoFechas(null, fecha));
        assertDoesNotThrow(() -> new RangoFechas(null, null));
        assertDoesNotThrow(() -> new RangoFechas(fecha, fecha));
    }

    @Test
    void conservaLosLimitesRecibidos() {
        LocalDate desde = LocalDate.of(2026, 1, 1);
        LocalDate hasta = LocalDate.of(2026, 12, 31);

        RangoFechas rango = new RangoFechas(desde, hasta);

        assertEquals(desde, rango.desde());
        assertEquals(hasta, rango.hasta());
        assertTrue(rango.desde().isBefore(rango.hasta()));
    }
}
