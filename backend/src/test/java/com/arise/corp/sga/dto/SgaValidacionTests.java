package com.arise.corp.sga.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.arise.corp.sga.GeneradoresSga;
import com.arise.corp.sga.dto.validacion.CantidadEntera;
import com.arise.corp.sga.entity.CategoriaArticulo;
import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.entity.TipoMovimientoInventario;
import com.arise.corp.sga.entity.UnidadArticulo;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Pruebas de validacion de los DTO del SGA (D5, D6).
 *
 * <p>Cubre la Property 8 (cantidades enteras en rango), la parte DTO de la
 * Property 3 (dron solo al asignar), un error por campo y los mensajes en
 * espanol, con los generadores de {@link GeneradoresSga}.
 */
class SgaValidacionTests {

    private static final int CASOS = 120;
    private static final long MAXIMO = 1_000_000;

    private static final String CODIGO = "DRN-001";
    private static final String NOMBRE = "Batería LiPo 4S 5000 mAh";
    private static final UUID DRON_ID =
            UUID.fromString("7a1b2c3d-0000-4000-8000-000000000031");

    private static final Validator VALIDADOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 8: Las cantidades se aceptan solo como enteros dentro del rango de su tipo")
    void elStockMinimoSoloAceptaEnterosEnRango() {
        List<BigDecimal> valores = valoresDeCantidad();

        for (BigDecimal valor : valores) {
            ArticuloRequest solicitud = articulo(valor);

            assertEquals(
                    CantidadEntera.Validador.esEnteroEnRango(valor, 0, MAXIMO),
                    !tieneViolacionEn(solicitud, "stockMinimo"),
                    "stockMinimo " + valor);
        }
    }

    @Test
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 8: Las cantidades se aceptan solo como enteros dentro del rango de su tipo")
    void laCantidadDelMovimientoSoloAceptaEnterosEnElRangoDeSuTipo() {
        List<BigDecimal> valores = valoresDeCantidad();

        for (TipoMovimientoInventario tipo : TipoMovimientoInventario.values()) {
            long min = tipo.cantidadMinima();

            for (BigDecimal valor : valores) {
                MovimientoInventarioRequest solicitud =
                        new MovimientoInventarioRequest(tipo, valor, "Conteo físico");

                assertEquals(
                        CantidadEntera.Validador.esEnteroEnRango(valor, min, MAXIMO),
                        !tieneViolacionEn(solicitud, "cantidad"),
                        tipo + " cantidad " + valor);
            }
        }
    }

    @Test
    void unDecimalNoSeTruncaYLasFormasEquivalentesDeUnEnteroSeAceptan() {
        ArticuloRequest decimal = articulo(new BigDecimal("1.5"));
        ArticuloRequest entero = articulo(new BigDecimal("5"));
        ArticuloRequest conCero = articulo(new BigDecimal("5.0"));
        ArticuloRequest exponencial = articulo(new BigDecimal("5E0"));

        assertEquals(Set.of("debe ser un número entero entre 0 y 1000000"),
                mensajesEn(decimal, "stockMinimo"));
        assertFalse(tieneViolacionEn(entero, "stockMinimo"));
        assertFalse(tieneViolacionEn(conCero, "stockMinimo"));
        assertFalse(tieneViolacionEn(exponencial, "stockMinimo"));

        assertEquals(5, new MovimientoInventarioRequest(
                TipoMovimientoInventario.ENTRADA, new BigDecimal("5.0"), null)
                .cantidadEntera());
        assertEquals(5, new MovimientoInventarioRequest(
                TipoMovimientoInventario.ENTRADA, new BigDecimal("5E0"), null)
                .cantidadEntera());
    }

    @Test
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 4: Un error por cada campo inválido")
    void cadaCampoInvalidoProduceUnaSolaEntrada() {
        ArticuloRequest articulo = new ArticuloRequest(
                "x", null, null, null, new BigDecimal("1.5"));

        assertEquals(
                Set.of("codigo", "nombre", "categoria", "unidad", "stockMinimo"),
                camposConViolacion(articulo));

        MovimientoInventarioRequest movimiento = new MovimientoInventarioRequest(
                TipoMovimientoInventario.AJUSTE, new BigDecimal("1.5"), null);

        assertEquals(Set.of("cantidad", "observacion"),
                camposConViolacion(movimiento));
        assertEquals(Set.of("debe ser un número entero entre 0 y 1000000"),
                mensajesEn(movimiento, "cantidad"));
        assertEquals(Set.of("es obligatoria en un ajuste"),
                mensajesEn(movimiento, "observacion"));
    }

    @Test
    void losMensajesDeCantidadEstanEnEspanol() {
        assertEquals(Set.of("debe ser un número entero entre 1 y 1000000"),
                mensajesEn(new MovimientoInventarioRequest(
                        TipoMovimientoInventario.ENTRADA, BigDecimal.ZERO, null),
                        "cantidad"));
        assertEquals(Set.of("debe ser un número entero entre 0 y 1000000"),
                mensajesEn(new MovimientoInventarioRequest(
                        TipoMovimientoInventario.AJUSTE,
                        BigDecimal.valueOf(1_000_001),
                        "Conteo"),
                        "cantidad"));
    }

    @ParameterizedTest(name = "{0} con dronId={1}")
    @MethodSource("transiciones")
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 3: Solo se asigna un dron asignable")
    void elDronSoloSeAdmiteAlAsignar(
            EstadoPaquete estado,
            UUID dronId) {

        TransicionPaqueteRequest solicitud =
                new TransicionPaqueteRequest(estado, dronId, null);

        Set<String> esperados = new LinkedHashSet<>();
        if (estado == EstadoPaquete.ASIGNADO && dronId == null) {
            esperados.add("dronId");
        }
        if (estado != EstadoPaquete.ASIGNADO && dronId != null) {
            esperados.add("dronId");
        }

        assertEquals(esperados, camposConViolacion(solicitud));
    }

    @Test
    void sinEstadoNoSeInformaLaCoherenciaDelDron() {
        TransicionPaqueteRequest solicitud =
                new TransicionPaqueteRequest(null, DRON_ID, null);

        assertEquals(Set.of("estado"), camposConViolacion(solicitud));
    }

    @Test
    void losMensajesDelDronEstanEnEspanol() {
        assertEquals(Set.of("es obligatorio para asignar el paquete"),
                mensajesEn(new TransicionPaqueteRequest(
                        EstadoPaquete.ASIGNADO, null, null),
                        "dronId"));
        assertEquals(Set.of("solo se admite en la transición a ASIGNADO"),
                mensajesEn(new TransicionPaqueteRequest(
                        EstadoPaquete.EN_TRANSITO, DRON_ID, null),
                        "dronId"));
    }

    @Test
    void normalizaElCodigoYLosTextosEnElConstructor() {
        DronRequest solicitud = new DronRequest(
                "  drn-001  ", "  Arise X4  ", "   ");

        assertEquals(CODIGO, solicitud.codigo());
        assertEquals("Arise X4", solicitud.modelo());
        assertNull(solicitud.observaciones());

        PaqueteCrearRequest paquete = new PaqueteCrearRequest(
                " paq-0001 ", "  Repuestos  ", "  Calle 10 # 20-30  ",
                UUID.randomUUID(), null, "   ");

        assertEquals("PAQ-0001", paquete.codigo());
        assertEquals("Repuestos", paquete.descripcion());
        assertEquals("Calle 10 # 20-30", paquete.direccionEntrega());
        assertNull(paquete.observacion());

        MovimientoInventarioRequest movimiento = new MovimientoInventarioRequest(
                TipoMovimientoInventario.ENTRADA, BigDecimal.ONE, "   ");

        assertNull(movimiento.observacion());
    }

    @Test
    void elCodigoInvalidoInformaElMensajeComun() {
        DronRequest solicitud = new DronRequest("x", "Arise X4", null);

        assertEquals(Set.of(
                "debe tener entre 3 y 30 caracteres: letras, dígitos o guiones"),
                mensajesEn(solicitud, "codigo"));
    }

    @Test
    void elToStringNoExponeDatosProtegidos() {
        assertEquals("DronRequest[datos protegidos]",
                new DronRequest(CODIGO, "Arise X4", "Bodega norte").toString());
        assertEquals("PaqueteCrearRequest[datos protegidos]",
                new PaqueteCrearRequest(
                        "PAQ-0001", "Repuestos", "Calle 10", UUID.randomUUID(),
                        null, "Fragil").toString());
        assertEquals("TransicionPaqueteRequest[datos protegidos]",
                new TransicionPaqueteRequest(
                        EstadoPaquete.ASIGNADO, DRON_ID, "Fragil").toString());
        assertEquals("MovimientoInventarioRequest[datos protegidos]",
                new MovimientoInventarioRequest(
                        TipoMovimientoInventario.AJUSTE, BigDecimal.ONE,
                        "Conteo").toString());
    }

    static Stream<Arguments> transiciones() {
        return Stream.of(EstadoPaquete.values())
                .flatMap(estado -> Stream.of(
                        Arguments.of(estado, null),
                        Arguments.of(estado, DRON_ID)));
    }

    private static List<BigDecimal> valoresDeCantidad() {
        Random azar = GeneradoresSga.nuevoRandom();
        List<BigDecimal> valores =
                new ArrayList<>(GeneradoresSga.bordesDeCantidad());

        for (int i = 0; i < CASOS; i++) {
            valores.add(GeneradoresSga.cantidadDeCualquierForma(azar));
        }

        return valores;
    }

    private static ArticuloRequest articulo(BigDecimal stockMinimo) {
        return new ArticuloRequest(
                CODIGO,
                NOMBRE,
                CategoriaArticulo.BATERIA,
                UnidadArticulo.UNIDAD,
                stockMinimo);
    }

    private static Set<String> camposConViolacion(Object solicitud) {
        return VALIDADOR.validate(solicitud).stream()
                .map(violacion -> violacion.getPropertyPath().toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean tieneViolacionEn(Object solicitud, String campo) {
        return !mensajesEn(solicitud, campo).isEmpty();
    }

    private static Set<String> mensajesEn(Object solicitud, String campo) {
        return VALIDADOR.validate(solicitud).stream()
                .filter(violacion -> violacion.getPropertyPath().toString()
                        .equals(campo))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
