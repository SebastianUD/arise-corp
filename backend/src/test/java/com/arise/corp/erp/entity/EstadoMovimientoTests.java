package com.arise.corp.erp.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pruebas exhaustivas de la maquina de estados del movimiento (Property 12 a
 * nivel de enumeracion, Requirement 25.2).
 */
class EstadoMovimientoTests {

    private static final String PERMITIDAS =
            "PENDIENTE->PAGADO, PENDIENTE->ANULADO";

    static Stream<Arguments> paresDeEstado() {
        return Stream.of(EstadoMovimiento.values())
                .flatMap(origen -> Stream.of(EstadoMovimiento.values())
                        .map(destino -> Arguments.of(origen, destino)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("paresDeEstado")
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 12: Solo se aceptan las dos transiciones permitidas")
    void soloSeAceptanLasDosTransicionesPermitidas(
            EstadoMovimiento origen,
            EstadoMovimiento destino) {

        boolean esperada = origen == EstadoMovimiento.PENDIENTE
                && (destino == EstadoMovimiento.PAGADO
                        || destino == EstadoMovimiento.ANULADO);

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
    @EnumSource(EstadoMovimiento.class)
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 12: Solo se aceptan las dos transiciones permitidas")
    void soloLosEstadosFinalesNoTienenTransiciones(EstadoMovimiento estado) {
        boolean esperadoFinal = estado == EstadoMovimiento.PAGADO
                || estado == EstadoMovimiento.ANULADO;

        assertEquals(esperadoFinal, estado.esFinal(),
                "esFinal incorrecto para " + estado);
    }

    @ParameterizedTest
    @EnumSource(EstadoMovimiento.class)
    void soloPendienteEsEditable(EstadoMovimiento estado) {
        assertEquals(estado == EstadoMovimiento.PENDIENTE, estado.esEditable(),
                "esEditable incorrecto para " + estado);
    }

    @Test
    void pendienteAdmitePagarOAnularYLosFinalesNinguna() {
        assertEquals(
                EnumSet.of(EstadoMovimiento.PAGADO, EstadoMovimiento.ANULADO),
                EstadoMovimiento.PENDIENTE.transicionesPermitidas());
        assertTrue(EstadoMovimiento.PAGADO.transicionesPermitidas().isEmpty());
        assertTrue(EstadoMovimiento.ANULADO.transicionesPermitidas().isEmpty());
    }

    @Test
    void elNumeroDeTransicionesPermitidasEsDos() {
        long total = Stream.of(EstadoMovimiento.values())
                .mapToLong(estado -> estado.transicionesPermitidas().size())
                .sum();

        assertEquals(2, total);
        assertFalse(EstadoMovimiento.PENDIENTE.transicionesPermitidas()
                .contains(EstadoMovimiento.PENDIENTE));
    }
}
