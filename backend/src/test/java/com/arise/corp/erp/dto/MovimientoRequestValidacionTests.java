package com.arise.corp.erp.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import com.arise.corp.erp.GeneradoresErp;
import com.arise.corp.erp.entity.TipoMovimiento;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Pruebas de validacion de {@link MovimientoRequest} (D4, D5, D8,
 * Requirements 25.3 y 25.7).
 *
 * <p>Cubre las Properties 2, 3, 4 y 9 sobre el DTO de entrada: variantes de
 * referencia equivalentes, reglas de formato iguales a la referencia, un error
 * por cada campo invalido y coherencia entre tipo y relacion. Los mensajes se
 * comprueban en espanol.
 */
class MovimientoRequestValidacionTests {

    private static final int CASOS = 120;

    private static final LocalDate FECHA = LocalDate.of(2026, 1, 15);
    private static final String CONCEPTO = "Compra de baterías";
    private static final BigDecimal VALOR = new BigDecimal("1250000.50");
    private static final String REFERENCIA = "FAC-2026/001";
    private static final UUID CLIENTE_ID =
            UUID.fromString("8c3e9f40-0000-4000-8000-000000000001");
    private static final UUID PROVEEDOR_ID =
            UUID.fromString("8c3e9f40-0000-4000-8000-000000000002");

    private static final Validator VALIDADOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 1: La normalización es idempotente")
    void laNormalizacionEsIdempotente() {
        Random azar = GeneradoresErp.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            MovimientoRequest primera = solicitud(
                    TipoMovimiento.INGRESO,
                    GeneradoresErp.conceptoDeCualquierForma(azar),
                    GeneradoresErp.valorDeCualquierForma(azar),
                    GeneradoresErp.referenciaDeCualquierForma(azar),
                    null,
                    null);

            MovimientoRequest segunda = solicitud(
                    primera.tipo(),
                    primera.concepto(),
                    primera.valor(),
                    primera.referencia(),
                    primera.clienteId(),
                    primera.proveedorId());

            assertEquals(primera, segunda,
                    "normalizacion no idempotente para " + primera.referencia());
        }
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 2: Las variantes de formato de un documento o de una referencia son el mismo valor")
    void lasVariantesDeReferenciaProducenElMismoValor() {
        Random azar = GeneradoresErp.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            String base = referenciaBase(azar);
            String variante = varianteDeReferencia(azar, base);

            MovimientoRequest original = solicitud(
                    TipoMovimiento.INGRESO, CONCEPTO, VALOR, base, null, null);
            MovimientoRequest conVariante = solicitud(
                    TipoMovimiento.INGRESO, CONCEPTO, VALOR, variante, null, null);

            assertEquals(original.referencia(), conVariante.referencia(),
                    "referencia <" + variante + "> frente a <" + base + ">");
        }
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void elConceptoValidaSegunLaReglaDeReferencia() {
        Random azar = GeneradoresErp.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            String concepto = GeneradoresErp.conceptoDeCualquierForma(azar);
            String normalizado = NormalizadorTexto.texto(concepto);
            boolean esperado = normalizado != null
                    && normalizado.length() >= 3
                    && normalizado.length() <= 300;

            MovimientoRequest solicitud = solicitud(
                    TipoMovimiento.INGRESO, concepto, VALOR, REFERENCIA,
                    null, null);

            assertEquals(esperado, !tieneViolacionEn(solicitud, "concepto"),
                    "concepto <" + concepto + ">");
        }
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void elValorValidaSegunLaReglaDeReferencia() {
        Random azar = GeneradoresErp.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            BigDecimal valor = GeneradoresErp.valorDeCualquierForma(azar);
            BigDecimal valido = GeneradoresErp.valorPositivoValido(azar);

            MovimientoRequest conValor = solicitud(
                    TipoMovimiento.INGRESO, CONCEPTO, valor, REFERENCIA,
                    null, null);
            MovimientoRequest conValorValido = solicitud(
                    TipoMovimiento.INGRESO, CONCEPTO, valido, REFERENCIA,
                    null, null);

            assertEquals(valorValido(valor),
                    !tieneViolacionEn(conValor, "valor"),
                    "valor " + valor);
            assertFalse(tieneViolacionEn(conValorValido, "valor"),
                    "valor valido " + valido);
        }
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void laReferenciaValidaSegunLaReglaDeReferencia() {
        Random azar = GeneradoresErp.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            String referencia = GeneradoresErp.referenciaDeCualquierForma(azar);
            String normalizada = NormalizadorTexto.codigo(referencia);
            boolean esperado = normalizada == null
                    || normalizada.matches("[A-Z0-9 ./-]{1,50}");

            MovimientoRequest solicitud = solicitud(
                    TipoMovimiento.INGRESO, CONCEPTO, VALOR, referencia,
                    null, null);

            assertEquals(esperado, !tieneViolacionEn(solicitud, "referencia"),
                    "referencia <" + referencia + "> normalizada <"
                            + normalizada + ">");
        }
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 4: Un error por cada campo inválido")
    void cadaCampoInvalidoProduceUnaSolaEntrada() {
        Random azar = GeneradoresErp.nuevoRandom();
        List<String> campos = List.of(
                "tipo", "fecha", "concepto", "valor", "referencia");

        for (int i = 0; i < CASOS; i++) {
            boolean[] invalidos = new boolean[campos.size()];
            Set<String> esperados = new LinkedHashSet<>();

            for (int j = 0; j < invalidos.length; j++) {
                invalidos[j] = azar.nextBoolean();
                if (invalidos[j]) {
                    esperados.add(campos.get(j));
                }
            }

            MovimientoRequest solicitud = new MovimientoRequest(
                    invalidos[0] ? null : TipoMovimiento.INGRESO,
                    invalidos[1] ? null : FECHA,
                    invalidos[2] ? conceptoInvalido(azar) : CONCEPTO,
                    invalidos[3] ? valorInvalido(azar) : VALOR,
                    invalidos[4] ? referenciaInvalida(azar) : REFERENCIA,
                    null,
                    null);

            assertEquals(esperados, camposConViolacion(solicitud),
                    "campos invalidos: " + esperados);
        }
    }

    @ParameterizedTest(name = "{0} con cliente={1} y proveedor={2}")
    @MethodSource("relaciones")
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 9: La relación es coherente con el tipo")
    void laRelacionEsCoherenteConElTipo(
            TipoMovimiento tipo,
            UUID clienteId,
            UUID proveedorId) {

        MovimientoRequest solicitud = solicitud(
                tipo, CONCEPTO, VALOR, REFERENCIA, clienteId, proveedorId);

        Set<String> esperados = new LinkedHashSet<>();
        if (tipo == TipoMovimiento.EGRESO && clienteId != null) {
            esperados.add("clienteId");
        }
        if (tipo == TipoMovimiento.INGRESO && proveedorId != null) {
            esperados.add("proveedorId");
        }

        assertEquals(esperados, camposConViolacion(solicitud));
    }

    @Test
    void sinTipoNoSeInformaLaCoherencia() {
        MovimientoRequest solicitud = new MovimientoRequest(
                null, FECHA, CONCEPTO, VALOR, REFERENCIA,
                CLIENTE_ID, PROVEEDOR_ID);

        assertEquals(Set.of("tipo"), camposConViolacion(solicitud));
    }

    @Test
    void unaReferenciaConEneSeRechaza() {
        MovimientoRequest solicitud = solicitud(
                TipoMovimiento.INGRESO, CONCEPTO, VALOR, "FAC-ñ-001", null, null);

        assertEquals(Set.of(
                "debe tener como máximo 50 caracteres: letras A-Z, "
                        + "dígitos, espacios, puntos, barras o guiones"),
                mensajesEn(solicitud, "referencia"));
    }

    @Test
    void normalizaElConceptoYLaReferenciaEnElConstructor() {
        MovimientoRequest solicitud = solicitud(
                TipoMovimiento.INGRESO,
                "  Compra de baterías  ",
                VALOR,
                "  fac-2026/001  ",
                null,
                null);

        assertEquals("Compra de baterías", solicitud.concepto());
        assertEquals("FAC-2026/001", solicitud.referencia());

        MovimientoRequest vacios = solicitud(
                TipoMovimiento.INGRESO, "   ", VALOR, "   ", null, null);

        assertNull(vacios.concepto());
        assertNull(vacios.referencia());
    }

    @Test
    void losMensajesEstanEnEspanol() {
        MovimientoRequest vacio = new MovimientoRequest(
                null, null, null, null, null, null, null);

        long obligatorios = VALIDADOR.validate(vacio).stream()
                .filter(violacion -> "es obligatorio".equals(
                        violacion.getMessage()))
                .count();
        assertEquals(4, obligatorios);

        MovimientoRequest relacionIncoherente = solicitud(
                TipoMovimiento.EGRESO, CONCEPTO, VALOR, REFERENCIA,
                CLIENTE_ID, null);

        assertEquals(Set.of("solo se admite en movimientos de tipo INGRESO"),
                mensajesEn(relacionIncoherente, "clienteId"));

        MovimientoRequest proveedorEnIngreso = solicitud(
                TipoMovimiento.INGRESO, CONCEPTO, VALOR, REFERENCIA,
                null, PROVEEDOR_ID);

        assertEquals(Set.of("solo se admite en movimientos de tipo EGRESO"),
                mensajesEn(proveedorEnIngreso, "proveedorId"));
    }

    static Stream<Arguments> relaciones() {
        return Stream.of(TipoMovimiento.values())
                .flatMap(tipo -> Stream.of(null, CLIENTE_ID)
                        .flatMap(cliente -> Stream.of(null, PROVEEDOR_ID)
                                .map(proveedor -> Arguments.of(
                                        tipo, cliente, proveedor))));
    }

    private static MovimientoRequest solicitud(
            TipoMovimiento tipo,
            String concepto,
            BigDecimal valor,
            String referencia,
            UUID clienteId,
            UUID proveedorId) {

        return new MovimientoRequest(
                tipo, FECHA, concepto, valor, referencia, clienteId, proveedorId);
    }

    private static String referenciaBase(Random azar) {
        int longitud = 1 + azar.nextInt(50);
        StringBuilder referencia = new StringBuilder(longitud);
        referencia.append('A');

        for (int i = 1; i < longitud; i++) {
            referencia.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ./-"
                    .charAt(azar.nextInt(40)));
        }

        return referencia.toString();
    }

    private static String varianteDeReferencia(Random azar, String base) {
        StringBuilder variante = new StringBuilder();

        for (int i = 0; i < base.length(); i++) {
            char caracter = base.charAt(i);
            variante.append(azar.nextBoolean()
                    ? Character.toLowerCase(caracter)
                    : caracter);
        }

        String espacios = " ".repeat(1 + azar.nextInt(3));
        return azar.nextBoolean()
                ? espacios + variante + espacios
                : variante.toString();
    }

    private static String conceptoInvalido(Random azar) {
        return switch (azar.nextInt(4)) {
            case 0 -> null;
            case 1 -> "";
            case 2 -> "ab";
            default -> "x".repeat(301 + azar.nextInt(10));
        };
    }

    private static BigDecimal valorInvalido(Random azar) {
        return switch (azar.nextInt(5)) {
            case 0 -> null;
            case 1 -> BigDecimal.ZERO;
            case 2 -> new BigDecimal("-1.00");
            case 3 -> new BigDecimal("1.234");
            default -> new BigDecimal("10000000000000.00");
        };
    }

    private static String referenciaInvalida(Random azar) {
        return azar.nextBoolean() ? "FAC-ñ-001" : "x".repeat(51);
    }

    private static boolean valorValido(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        BigDecimal numero = valor.scale() < 0 ? valor.setScale(0) : valor;
        int enteros = numero.precision() - numero.scale();
        int decimales = Math.max(numero.scale(), 0);

        return enteros <= 13 && decimales <= 2;
    }

    private static Set<String> camposConViolacion(MovimientoRequest solicitud) {
        return VALIDADOR.validate(solicitud).stream()
                .map(violacion -> violacion.getPropertyPath().toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean tieneViolacionEn(
            MovimientoRequest solicitud,
            String campo) {

        return !mensajesEn(solicitud, campo).isEmpty();
    }

    private static Set<String> mensajesEn(
            MovimientoRequest solicitud,
            String campo) {

        return VALIDADOR.validate(solicitud).stream()
                .filter(violacion -> violacion.getPropertyPath().toString()
                        .equals(campo))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
