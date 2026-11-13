package com.arise.corp.crm.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pruebas exhaustivas de la maquina de estados del contrato (Property 11 a
 * nivel de enumeracion, Requirement 23.2).
 */
class EstadoContratoTests {

    private static final String PERMITIDAS =
            "BORRADOR->ACTIVO, BORRADOR->CANCELADO, "
            + "ACTIVO->FINALIZADO, ACTIVO->CANCELADO";

    static Stream<Arguments> paresDeEstado() {
        return Stream.of(EstadoContrato.values())
                .flatMap(origen -> Stream.of(EstadoContrato.values())
                        .map(destino -> Arguments.of(origen, destino)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("paresDeEstado")
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 11: Solo se aceptan las cuatro transiciones permitidas")
    void soloSeAceptanLasCuatroTransicionesPermitidas(
            EstadoContrato origen,
            EstadoContrato destino) {

        boolean esperada = (origen == EstadoContrato.BORRADOR
                && destino == EstadoContrato.ACTIVO)
                || (origen == EstadoContrato.BORRADOR
                        && destino == EstadoContrato.CANCELADO)
                || (origen == EstadoContrato.ACTIVO
                        && destino == EstadoContrato.FINALIZADO)
                || (origen == EstadoContrato.ACTIVO
                        && destino == EstadoContrato.CANCELADO);

        assertEquals(
                esperada,
                origen.puedeTransicionarA(destino),
                origen + " -> " + destino + " no coincide con: " + PERMITIDAS);
        assertEquals(
                esperada,
                origen.transicionesPermitidas().contains(destino),
                origen + " -> " + destino + " no coincide con: " + PERMITIDAS);
    }

    @ParameterizedTest
    @EnumSource(EstadoContrato.class)
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 11: Solo se aceptan las cuatro transiciones permitidas")
    void soloLosEstadosFinalesNoTienenTransiciones(EstadoContrato estado) {
        boolean esperadoFinal = estado == EstadoContrato.FINALIZADO
                || estado == EstadoContrato.CANCELADO;

        assertEquals(esperadoFinal, estado.esFinal(),
                "esFinal incorrecto para " + estado);
    }

    @Test
    void elNumeroDeTransicionesPermitidasEsCuatro() {
        long total = Stream.of(EstadoContrato.values())
                .mapToLong(estado -> estado.transicionesPermitidas().size())
                .sum();

        assertEquals(4, total);
        assertTrue(EstadoContrato.BORRADOR.transicionesPermitidas()
                .contains(EstadoContrato.ACTIVO));
        assertFalse(EstadoContrato.BORRADOR.transicionesPermitidas()
                .contains(EstadoContrato.BORRADOR));
    }
}
