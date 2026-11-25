package com.arise.corp.sga.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pruebas exhaustivas de la maquina de estados del paquete (Property 1 y
 * Property 4 a nivel de enumeracion, Requirements 10.2, 11.7, 11.9, 11.10,
 * 25.2).
 */
class EstadoPaqueteTests {

    private static final Set<EstadoPaquete> EN_CURSO =
            EnumSet.of(EstadoPaquete.ASIGNADO, EstadoPaquete.EN_TRANSITO);

    static Stream<Arguments> paresDeEstado() {
        return Stream.of(EstadoPaquete.values())
                .flatMap(origen -> Stream.of(EstadoPaquete.values())
                        .map(destino -> Arguments.of(origen, destino)));
    }

    private static Set<EstadoPaquete> transicionesEsperadas(EstadoPaquete origen) {
        return switch (origen) {
            case REGISTRADO -> EnumSet.of(EstadoPaquete.ASIGNADO, EstadoPaquete.CANCELADO);
            case ASIGNADO -> EnumSet.of(EstadoPaquete.EN_TRANSITO, EstadoPaquete.CANCELADO);
            case EN_TRANSITO -> EnumSet.of(EstadoPaquete.ENTREGADO, EstadoPaquete.DEVUELTO);
            case ENTREGADO, CANCELADO, DEVUELTO ->
                    EnumSet.noneOf(EstadoPaquete.class);
        };
    }

    private static Optional<EstadoDron> efectoEsperado(
            EstadoPaquete origen,
            EstadoPaquete destino) {

        if (origen == EstadoPaquete.ASIGNADO && destino == EstadoPaquete.EN_TRANSITO) {
            return Optional.of(EstadoDron.EN_MISION);
        }
        if (origen == EstadoPaquete.EN_TRANSITO
                && (destino == EstadoPaquete.ENTREGADO
                        || destino == EstadoPaquete.DEVUELTO)) {
            return Optional.of(EstadoDron.DISPONIBLE);
        }
        return Optional.empty();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("paresDeEstado")
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 1: Solo se aplican las transiciones permitidas del paquete")
    void soloSeAplicanLasTransicionesPermitidas(
            EstadoPaquete origen,
            EstadoPaquete destino) {

        Set<EstadoPaquete> esperadas = transicionesEsperadas(origen);
        boolean permitida = esperadas.contains(destino);
        String contexto = origen + " -> " + destino + "; permitidas desde " + origen
                + ": " + esperadas;

        assertEquals(permitida, origen.puedeTransicionarA(destino), contexto);
        assertEquals(esperadas, origen.transicionesPermitidas(), contexto);
    }

    @ParameterizedTest
    @EnumSource(EstadoPaquete.class)
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 1: Solo se aplican las transiciones permitidas del paquete")
    void soloLosEstadosFinalesNoTienenTransiciones(EstadoPaquete estado) {
        boolean esperadoFinal = estado == EstadoPaquete.ENTREGADO
                || estado == EstadoPaquete.CANCELADO
                || estado == EstadoPaquete.DEVUELTO;

        assertEquals(esperadoFinal, estado.esFinal(),
                "esFinal incorrecto para " + estado);
    }

    @ParameterizedTest
    @EnumSource(EstadoPaquete.class)
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 1: Solo se aplican las transiciones permitidas del paquete")
    void soloRegistradoYAsignadoSonEditables(EstadoPaquete estado) {
        boolean esperadoEditable = estado == EstadoPaquete.REGISTRADO
                || estado == EstadoPaquete.ASIGNADO;

        assertEquals(esperadoEditable, estado.esEditable(),
                "esEditable incorrecto para " + estado);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("paresDeEstado")
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 4: El estado del dron sigue al de su paquete")
    void elEfectoSobreElDronSoloExisteEnLasTransicionesDeSincronizacion(
            EstadoPaquete origen,
            EstadoPaquete destino) {

        assertEquals(
                efectoEsperado(origen, destino),
                origen.efectoSobreDron(destino),
                "efectoSobreDron incorrecto para " + origen + " -> " + destino);
    }

    @Test
    void elConjuntoEnCursoEsAsignadoYEnTransito() {
        assertEquals(EN_CURSO, EstadoPaquete.EN_CURSO);
    }

    @Test
    void elMismoEstadoNoEsUnaTransicion() {
        for (EstadoPaquete estado : EstadoPaquete.values()) {
            assertFalse(estado.puedeTransicionarA(estado),
                    "El mismo estado " + estado + " no deberia ser transicion");
        }
    }

    @Test
    void registradoNoAsignaDronSinPasarPorAsignado() {
        assertTrue(EstadoPaquete.REGISTRADO.puedeTransicionarA(EstadoPaquete.ASIGNADO));
        assertTrue(EstadoPaquete.REGISTRADO.transicionesPermitidas()
                .contains(EstadoPaquete.CANCELADO));
        assertFalse(EstadoPaquete.REGISTRADO.puedeTransicionarA(EstadoPaquete.EN_TRANSITO));
        assertFalse(EstadoPaquete.REGISTRADO.puedeTransicionarA(EstadoPaquete.ENTREGADO));
    }
}
