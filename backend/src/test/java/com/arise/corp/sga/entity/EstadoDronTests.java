package com.arise.corp.sga.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pruebas exhaustivas de la maquina de estados manual del dron (Property 2 a
 * nivel de enumeracion, Requirements 6.1, 6.2, 25.2).
 */
class EstadoDronTests {

    private static final Set<EstadoDron> MANUALES =
            EnumSet.of(EstadoDron.DISPONIBLE, EstadoDron.MANTENIMIENTO,
                    EstadoDron.FUERA_DE_SERVICIO);

    static Stream<Arguments> paresDeEstado() {
        return Stream.of(EstadoDron.values())
                .flatMap(origen -> Stream.of(EstadoDron.values())
                        .map(destino -> Arguments.of(origen, destino)));
    }

    private static Set<EstadoDron> transicionesEsperadas(EstadoDron origen) {
        if (origen == EstadoDron.EN_MISION) {
            return EnumSet.noneOf(EstadoDron.class);
        }
        EnumSet<EstadoDron> destinos = EnumSet.copyOf(MANUALES);
        destinos.remove(origen);
        return destinos;
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("paresDeEstado")
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 2: El dron solo cambia a mano entre sus estados manuales "
            + "y sin paquete en curso")
    void soloSeAceptanLasTransicionesManuales(
            EstadoDron origen,
            EstadoDron destino) {

        Set<EstadoDron> esperadas = transicionesEsperadas(origen);
        boolean permitida = esperadas.contains(destino);
        String contexto = origen + " -> " + destino + "; manuales desde " + origen
                + ": " + esperadas;

        assertEquals(permitida, origen.puedeCambiarManualmenteA(destino), contexto);
        assertEquals(esperadas, origen.transicionesManuales(), contexto);
    }

    @ParameterizedTest
    @EnumSource(EstadoDron.class)
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 2: El dron solo cambia a mano entre sus estados manuales "
            + "y sin paquete en curso")
    void enMisionNoTieneTransicionesManuales(EstadoDron estado) {
        if (estado == EstadoDron.EN_MISION) {
            assertTrue(estado.transicionesManuales().isEmpty());
        } else {
            assertFalse(estado.transicionesManuales().isEmpty());
        }
    }

    @Test
    void enMisionNoEsDestinoManual() {
        for (EstadoDron estado : EstadoDron.values()) {
            assertFalse(estado.puedeCambiarManualmenteA(EstadoDron.EN_MISION),
                    estado + " no deberia poder pasar a EN_MISION a mano");
        }
    }

    @Test
    void elMismoEstadoNoEsUnaTransicionManual() {
        for (EstadoDron estado : EstadoDron.values()) {
            assertFalse(estado.puedeCambiarManualmenteA(estado),
                    "El mismo estado " + estado + " no deberia ser transicion manual");
        }
    }

    @Test
    void losTresEstadosManualesSeConectanEntreSiYConNingunoMas() {
        assertEquals(
                EnumSet.of(EstadoDron.MANTENIMIENTO, EstadoDron.FUERA_DE_SERVICIO),
                EstadoDron.DISPONIBLE.transicionesManuales());
        assertEquals(
                EnumSet.of(EstadoDron.DISPONIBLE, EstadoDron.FUERA_DE_SERVICIO),
                EstadoDron.MANTENIMIENTO.transicionesManuales());
        assertEquals(
                EnumSet.of(EstadoDron.DISPONIBLE, EstadoDron.MANTENIMIENTO),
                EstadoDron.FUERA_DE_SERVICIO.transicionesManuales());
        assertEquals(6, Stream.of(EstadoDron.values())
                .mapToLong(estado -> estado.transicionesManuales().size())
                .sum());
    }
}
