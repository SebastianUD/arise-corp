package com.arise.corp.crm.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.arise.corp.crm.GeneradoresCrm;
import com.arise.corp.crm.entity.TipoDocumento;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Pruebas de validacion de {@link ClienteRequest} (D3, D13).
 *
 * <p>Cubre las Properties 3 y 4 sobre el DTO de entrada: el validador acepta
 * exactamente lo que acepta la regla de referencia y cada campo invalido
 * produce una sola entrada.
 */
class ClienteRequestValidacionTests {

    private static final int CASOS = 120;

    private static final Validator VALIDADOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static final String CORREO_VALIDO = "cliente@example.test";
    private static final String TELEFONO_VALIDO = "+57 300 123 4567";

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void elDocumentoValidaSegunLaReglaDeReferencia() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            TipoDocumento tipo = TipoDocumento.values()[
                    azar.nextInt(TipoDocumento.values().length)];
            String documento = GeneradoresCrm.documentoDeCualquierForma(azar);
            String normalizado = NormalizadorTexto.documento(documento);

            boolean esperado = normalizado != null
                    && normalizado.matches("[A-Z0-9]{3,20}")
                    && (!exigeSoloDigitos(tipo)
                            || normalizado.matches("[0-9]+"));

            ClienteRequest solicitud = cliente(
                    tipo, documento, "Cliente", CORREO_VALIDO,
                    TELEFONO_VALIDO, null, null);

            assertEquals(esperado, !tieneViolacionEn(solicitud, "numeroDocumento"),
                    "documento <" + documento + "> para el tipo " + tipo);
        }
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void elCorreoValidaSegunLaReglaDeReferencia() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            String correo = GeneradoresCrm.correoDeCualquierForma(azar);
            String normalizado = NormalizadorTexto.texto(correo);

            boolean esperado = correoValido(normalizado);

            ClienteRequest solicitud = cliente(
                    TipoDocumento.CC, "10234567", "Cliente", correo,
                    TELEFONO_VALIDO, null, null);

            assertEquals(esperado, !tieneViolacionEn(solicitud, "correo"),
                    "correo <" + correo + ">");
        }
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void elTelefonoValidaSegunLaReglaDeReferencia() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            String telefono = GeneradoresCrm.telefonoDeCualquierForma(azar);
            String normalizado = NormalizadorTexto.texto(telefono);

            boolean esperado = normalizado != null
                    && normalizado.matches("[0-9 +()\\-]{7,20}");

            ClienteRequest solicitud = cliente(
                    TipoDocumento.CC, "10234567", "Cliente", CORREO_VALIDO,
                    telefono, null, null);

            assertEquals(esperado, !tieneViolacionEn(solicitud, "telefono"),
                    "telefono <" + telefono + ">");
        }
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void lasLongitudesMaximasCoincidenConLaReglaDeReferencia() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            String nombre = GeneradoresCrm.textoDeLongitudVariable(azar, 200);
            String direccion = GeneradoresCrm.textoDeLongitudVariable(azar, 200);
            String ciudad = GeneradoresCrm.textoDeLongitudVariable(azar, 100);

            ClienteRequest solicitud = cliente(
                    TipoDocumento.CC, "10234567", nombre, CORREO_VALIDO,
                    TELEFONO_VALIDO, direccion, ciudad);

            assertEquals(normalizado(nombre).length() <= 200,
                    !tieneViolacionEn(solicitud, "nombre"),
                    "nombre de longitud " + nombre.length());
            assertEquals(normalizado(direccion).length() <= 200,
                    !tieneViolacionEn(solicitud, "direccion"),
                    "direccion de longitud " + direccion.length());
            assertEquals(normalizado(ciudad).length() <= 100,
                    !tieneViolacionEn(solicitud, "ciudad"),
                    "ciudad de longitud " + ciudad.length());
        }
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 4: Un error por cada campo inválido")
    void cadaCampoInvalidoProduceUnaSolaEntrada() {
        Random azar = GeneradoresCrm.nuevoRandom();
        List<String> campos = List.of(
                "tipoDocumento", "numeroDocumento", "nombre", "correo",
                "telefono", "direccion", "ciudad");

        for (int i = 0; i < CASOS; i++) {
            boolean[] invalidos = new boolean[campos.size()];
            Set<String> esperados = new LinkedHashSet<>();

            for (int j = 0; j < invalidos.length; j++) {
                invalidos[j] = azar.nextBoolean();
                if (invalidos[j]) {
                    esperados.add(campos.get(j));
                }
            }

            ClienteRequest solicitud = new ClienteRequest(
                    invalidos[0] ? null : TipoDocumento.CC,
                    invalidos[1] ? "!" : "10234567",
                    invalidos[2] ? null : "Cliente",
                    invalidos[3] ? "sin-arroba" : CORREO_VALIDO,
                    invalidos[4] ? "abc" : TELEFONO_VALIDO,
                    invalidos[5] ? "x".repeat(201) : null,
                    invalidos[6] ? "y".repeat(101) : null,
                    null);

            assertEquals(esperados, camposConViolacion(solicitud),
                    "campos invalidos: " + esperados);
        }
    }

    @Test
    void normalizaElDocumentoYLosTextosEnElConstructor() {
        ClienteRequest solicitud = cliente(
                TipoDocumento.CC,
                " 1.023.456-7 ",
                "  Ana Pérez  ",
                " ana@example.test ",
                " +57 300 123 4567 ",
                "   ",
                " Bogotá ");

        assertEquals("10234567", solicitud.numeroDocumento());
        assertEquals("Ana Pérez", solicitud.nombre());
        assertEquals("ana@example.test", solicitud.correo());
        assertEquals("+57 300 123 4567", solicitud.telefono());
        assertNull(solicitud.direccion());
        assertEquals("Bogotá", solicitud.ciudad());
    }

    @Test
    void losMensajesObligatoriosYDeFormatoEstanEnEspanol() {
        ClienteRequest vacio = new ClienteRequest(
                null, null, null, null, null, null, null, null);

        long obligatorios = VALIDADOR.validate(vacio).stream()
                .filter(violacion -> "es obligatorio".equals(
                        violacion.getMessage()))
                .count();
        assertEquals(5, obligatorios);

        ClienteRequest letrasParaCc = cliente(
                TipoDocumento.CC, "ABC123", "Cliente", CORREO_VALIDO,
                TELEFONO_VALIDO, null, null);

        assertEquals(Set.of("solo admite dígitos para el tipo CC o NIT"),
                mensajesEn(letrasParaCc, "numeroDocumento"));

        ClienteRequest demasiadoCorto = cliente(
                TipoDocumento.CE, "AB", "Cliente", CORREO_VALIDO,
                TELEFONO_VALIDO, null, null);

        assertEquals(Set.of("debe tener entre 3 y 20 letras o dígitos"),
                mensajesEn(demasiadoCorto, "numeroDocumento"));
    }

    @Test
    void elToStringNoExponeDatosPersonales() {
        ClienteRequest solicitud = cliente(
                TipoDocumento.CC, "10234567", "Ana Pérez", CORREO_VALIDO,
                TELEFONO_VALIDO, "Calle 1", "Bogotá");

        assertEquals("ClienteRequest[datos protegidos]",
                solicitud.toString());
        assertFalse(solicitud.toString().contains("Ana"));
        assertFalse(solicitud.toString().contains("10234567"));
        assertTrue(solicitud.toString().contains("protegidos"));
    }

    private static ClienteRequest cliente(
            TipoDocumento tipo,
            String documento,
            String nombre,
            String correo,
            String telefono,
            String direccion,
            String ciudad) {

        return new ClienteRequest(
                tipo, documento, nombre, correo, telefono, direccion, ciudad,
                null);
    }

    private static boolean exigeSoloDigitos(TipoDocumento tipo) {
        return tipo == TipoDocumento.CC || tipo == TipoDocumento.NIT;
    }

    /**
     * Regla de referencia de {@code @Correo}: formato {@code local@dominio.tld},
     * maximo 254 caracteres y parte local de hasta 64 (limite de Hibernate
     * Validator para correos).
     */
    private static boolean correoValido(String normalizado) {
        if (normalizado == null
                || normalizado.length() > 254
                || !normalizado.matches(".+@.+\\..+")) {
            return false;
        }

        int arroba = normalizado.lastIndexOf('@');
        return arroba > 0 && arroba < normalizado.length() - 1
                && arroba <= 64;
    }

    private static String normalizado(String valor) {
        String texto = NormalizadorTexto.texto(valor);
        return texto == null ? "" : texto;
    }

    private static Set<String> camposConViolacion(ClienteRequest solicitud) {
        return VALIDADOR.validate(solicitud).stream()
                .map(violacion -> violacion.getPropertyPath().toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean tieneViolacionEn(
            ClienteRequest solicitud,
            String campo) {

        return !mensajesEn(solicitud, campo).isEmpty();
    }

    private static Set<String> mensajesEn(
            ClienteRequest solicitud,
            String campo) {

        return VALIDADOR.validate(solicitud).stream()
                .filter(violacion -> violacion.getPropertyPath().toString()
                        .equals(campo))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
