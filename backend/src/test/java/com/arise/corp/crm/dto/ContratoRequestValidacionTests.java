package com.arise.corp.crm.dto;

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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.arise.corp.crm.GeneradoresCrm;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Pruebas de validacion de los DTO de contrato (D3, D13).
 */
class ContratoRequestValidacionTests {

    private static final int CASOS = 120;

    private static final Validator VALIDADOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final BigDecimal VALOR = new BigDecimal("1500000.50");

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void elCodigoValidaSegunLaReglaDeReferencia() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            String codigo = GeneradoresCrm.codigoDeCualquierForma(azar);
            String normalizado = NormalizadorTexto.codigo(codigo);

            boolean esperado = normalizado != null
                    && normalizado.matches("[A-Z0-9-]{3,30}");

            ContratoCrearRequest crear = crear(codigo, INICIO, null, VALOR);
            ContratoActualizarRequest actualizar =
                    actualizar(codigo, INICIO, null, VALOR);

            assertEquals(esperado, !tieneViolacionEn(crear, "codigo"),
                    "codigo de creacion <" + codigo + ">");
            assertEquals(esperado, !tieneViolacionEn(actualizar, "codigo"),
                    "codigo de edicion <" + codigo + ">");
        }
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void elRangoDeFechasCoincideConLaReglaDeReferencia() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            LocalDate inicio = GeneradoresCrm.fecha(azar);
            LocalDate fin = azar.nextBoolean()
                    ? null
                    : GeneradoresCrm.fecha(azar);

            boolean esperado = fin == null || !fin.isBefore(inicio);

            ContratoCrearRequest solicitud = crear(
                    "CTR-2026-001", inicio, fin, VALOR);

            assertEquals(esperado, !tieneViolacionEn(solicitud, "fechaFin"),
                    "inicio=" + inicio + ", fin=" + fin);
        }
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void elValorValidaSegunLaReglaDeReferencia() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            BigDecimal valor = GeneradoresCrm.valorDeCualquierForma(azar);

            ContratoCrearRequest solicitud = crear(
                    "CTR-2026-001", INICIO, null, valor);

            assertEquals(valorValido(valor),
                    !tieneViolacionEn(solicitud, "valor"),
                    "valor <" + valor + ">");
        }
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 4: Un error por cada campo inválido")
    void cadaCampoInvalidoDeCreacionProduceUnaSolaEntrada() {
        Random azar = GeneradoresCrm.nuevoRandom();
        List<String> campos = List.of(
                "clienteId", "codigo", "descripcion", "fechaInicio", "valor");

        for (int i = 0; i < CASOS; i++) {
            boolean[] invalidos = new boolean[campos.size()];
            Set<String> esperados = new LinkedHashSet<>();

            for (int j = 0; j < invalidos.length; j++) {
                invalidos[j] = azar.nextBoolean();
                if (invalidos[j]) {
                    esperados.add(campos.get(j));
                }
            }

            ContratoCrearRequest solicitud = new ContratoCrearRequest(
                    invalidos[0] ? null : UUID.randomUUID(),
                    invalidos[1] ? "!" : "CTR-2026-001",
                    invalidos[2] ? null : "Contrato de prueba",
                    invalidos[3] ? null : INICIO,
                    null,
                    invalidos[4] ? null : VALOR);

            assertEquals(esperados, camposConViolacion(solicitud),
                    "campos invalidos: " + esperados);
        }
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 4: Un error por cada campo inválido")
    void cadaCampoInvalidoDeEdicionProduceUnaSolaEntrada() {
        Random azar = GeneradoresCrm.nuevoRandom();
        List<String> campos = List.of(
                "codigo", "descripcion", "fechaInicio", "valor");

        for (int i = 0; i < CASOS; i++) {
            boolean[] invalidos = new boolean[campos.size()];
            Set<String> esperados = new LinkedHashSet<>();

            for (int j = 0; j < invalidos.length; j++) {
                invalidos[j] = azar.nextBoolean();
                if (invalidos[j]) {
                    esperados.add(campos.get(j));
                }
            }

            ContratoActualizarRequest solicitud =
                    new ContratoActualizarRequest(
                            invalidos[0] ? "!" : "CTR-2026-001",
                            invalidos[1] ? null : "Contrato de prueba",
                            invalidos[2] ? null : INICIO,
                            null,
                            invalidos[3] ? null : VALOR);

            assertEquals(esperados, camposConViolacion(solicitud),
                    "campos invalidos: " + esperados);
        }
    }

    @Test
    void laFechaFinAnteriorInformaSobreFechaFin() {
        ContratoCrearRequest solicitud = crear(
                "CTR-2026-001",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 5, 31),
                VALOR);

        assertEquals(Set.of("debe ser igual o posterior a la fecha de inicio"),
                mensajesEn(solicitud, "fechaFin"));
    }

    @Test
    void laFechaFinIgualOAusenteEsValida() {
        assertTrue(camposConViolacion(crear(
                "CTR-2026-001", INICIO, INICIO, VALOR)).isEmpty());
        assertTrue(camposConViolacion(crear(
                "CTR-2026-001", INICIO, null, VALOR)).isEmpty());
    }

    @Test
    void normalizaElCodigoYLaDescripcion() {
        ContratoCrearRequest solicitud = crear(
                "  ctr-2026-001 ", INICIO, null, VALOR);

        assertEquals("CTR-2026-001", solicitud.codigo());
        assertEquals("Contrato de prueba", solicitud.descripcion());
        assertEquals("CTR-2026-001",
                actualizar(" ctr-2026-001 ", INICIO, null, VALOR).codigo());
    }

    @Test
    void losMensajesDeFormatoEstanEnEspanol() {
        ContratoCrearRequest codigoInvalido = crear(
                "!", INICIO, null, VALOR);
        assertEquals(Set.of(
                        "debe tener entre 3 y 30 caracteres: letras, dígitos o guiones"),
                mensajesEn(codigoInvalido, "codigo"));

        ContratoCrearRequest descripcionLarga = crear(
                "CTR-2026-001", INICIO, null, VALOR,
                "x".repeat(1001));
        assertEquals(Set.of("debe tener como máximo 1000 caracteres"),
                mensajesEn(descripcionLarga, "descripcion"));

        ContratoCrearRequest valorNegativo = crear(
                "CTR-2026-001", INICIO, null, new BigDecimal("-1.00"));
        assertEquals(Set.of(
                        "debe ser mayor o igual a 0, con hasta 13 dígitos enteros y 2 decimales"),
                mensajesEn(valorNegativo, "valor"));

        ContratoCrearRequest valorConTresDecimales = crear(
                "CTR-2026-001", INICIO, null, new BigDecimal("1.234"));
        assertEquals(Set.of(
                        "debe ser mayor o igual a 0, con hasta 13 dígitos enteros y 2 decimales"),
                mensajesEn(valorConTresDecimales, "valor"));
    }

    @Test
    void elDtoDeEdicionNoAceptaClienteNiEstado() {
        Set<String> componentes = java.util.Arrays.stream(
                        ContratoActualizarRequest.class.getRecordComponents())
                .map(componente -> componente.getName())
                .collect(Collectors.toSet());

        assertFalse(componentes.contains("clienteId"));
        assertFalse(componentes.contains("estado"));
        assertEquals(Set.of("codigo", "descripcion", "fechaInicio", "fechaFin",
                "valor"), componentes);
    }

    @Test
    void elValorAceptaCeroYLosLimites() {
        assertTrue(camposConViolacion(crear(
                "CTR-2026-001", INICIO, null, BigDecimal.ZERO)).isEmpty());
        assertTrue(camposConViolacion(crear(
                "CTR-2026-001", INICIO, null,
                new BigDecimal("9999999999999.99"))).isEmpty());

        ContratoCrearRequest fueraDeRango = crear(
                "CTR-2026-001", INICIO, null,
                new BigDecimal("10000000000000.00"));
        assertFalse(camposConViolacion(fueraDeRango).isEmpty());
    }

    @Test
    void elValorNuloEsObligatorioYNoSeConfundeConCero() {
        ContratoCrearRequest solicitud = crear(
                "CTR-2026-001", INICIO, null, null);

        assertEquals(Set.of("es obligatorio"), mensajesEn(solicitud, "valor"));
        assertNull(solicitud.valor());
    }

    private static ContratoCrearRequest crear(
            String codigo,
            LocalDate inicio,
            LocalDate fin,
            BigDecimal valor) {

        return crear(codigo, inicio, fin, valor, "Contrato de prueba");
    }

    private static ContratoCrearRequest crear(
            String codigo,
            LocalDate inicio,
            LocalDate fin,
            BigDecimal valor,
            String descripcion) {

        return new ContratoCrearRequest(
                UUID.randomUUID(), codigo, descripcion, inicio, fin, valor);
    }

    private static ContratoActualizarRequest actualizar(
            String codigo,
            LocalDate inicio,
            LocalDate fin,
            BigDecimal valor) {

        return new ContratoActualizarRequest(
                codigo, "Contrato de prueba", inicio, fin, valor);
    }

    private static boolean valorValido(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }

        if (valor.compareTo(BigDecimal.ZERO) == 0) {
            return true;
        }

        int enteros = valor.precision() - valor.scale();
        int decimales = valor.scale() < 0 ? 0 : valor.scale();

        return enteros <= 13 && decimales <= 2;
    }

    private static Set<String> camposConViolacion(
            ContratoCrearRequest solicitud) {

        return VALIDADOR.validate(solicitud).stream()
                .map(violacion -> violacion.getPropertyPath().toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> camposConViolacion(
            ContratoActualizarRequest solicitud) {

        return VALIDADOR.validate(solicitud).stream()
                .map(violacion -> violacion.getPropertyPath().toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean tieneViolacionEn(
            ContratoCrearRequest solicitud,
            String campo) {

        return !mensajesEn(solicitud, campo).isEmpty();
    }

    private static boolean tieneViolacionEn(
            ContratoActualizarRequest solicitud,
            String campo) {

        return VALIDADOR.validate(solicitud).stream()
                .anyMatch(violacion -> violacion.getPropertyPath().toString()
                        .equals(campo));
    }

    private static Set<String> mensajesEn(
            ContratoCrearRequest solicitud,
            String campo) {

        return VALIDADOR.validate(solicitud).stream()
                .filter(violacion -> violacion.getPropertyPath().toString()
                        .equals(campo))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
