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
import com.arise.corp.shared.normalizacion.NormalizadorTexto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Pruebas de validacion de {@link CuentaCorporativaRequest} (D3, D13).
 */
class CuentaCorporativaRequestValidacionTests {

    private static final int CASOS = 120;

    private static final Validator VALIDADOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static final String CORREO_VALIDO = "contacto@example.test";
    private static final String TELEFONO_VALIDO = "+57 300 123 4567";

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void elNitValidaSegunLaReglaDeReferencia() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            String nit = GeneradoresCrm.nitDeCualquierForma(azar);
            String normalizado = NormalizadorTexto.documento(nit);

            boolean esperado = normalizado != null
                    && normalizado.matches("[0-9]{5,15}");

            CuentaCorporativaRequest solicitud = cuenta(
                    nit, "Arise", null, null, null);

            assertEquals(esperado, !tieneViolacionEn(solicitud, "nit"),
                    "NIT <" + nit + ">");
        }
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void lasLongitudesMaximasCoincidenConLaReglaDeReferencia() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            String razonSocial = GeneradoresCrm.textoDeLongitudVariable(azar, 200);
            String contacto = GeneradoresCrm.textoDeLongitudVariable(azar, 200);

            CuentaCorporativaRequest solicitud = cuenta(
                    "900123456", razonSocial, contacto, null, null);

            assertEquals(normalizado(razonSocial).length() <= 200,
                    !tieneViolacionEn(solicitud, "razonSocial"),
                    "razonSocial de longitud " + razonSocial.length());
            assertEquals(normalizado(contacto).length() <= 200,
                    !tieneViolacionEn(solicitud, "nombreContacto"),
                    "nombreContacto de longitud " + contacto.length());
        }
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void elCorreoYElTelefonoOpcionalesCoincidenConLaReglaDeReferencia() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            String correo = GeneradoresCrm.correoDeCualquierForma(azar);
            String telefono = GeneradoresCrm.telefonoDeCualquierForma(azar);
            String correoNormalizado = NormalizadorTexto.texto(correo);
            String telefonoNormalizado = NormalizadorTexto.texto(telefono);

            boolean correoEsperado = correoValido(correoNormalizado);
            boolean telefonoEsperado = telefonoNormalizado == null
                    || telefonoNormalizado.matches("[0-9 +()\\-]{7,20}");

            CuentaCorporativaRequest solicitud = cuenta(
                    "900123456", "Arise", null, correo, telefono);

            assertEquals(correoEsperado,
                    !tieneViolacionEn(solicitud, "correoContacto"),
                    "correo de contacto <" + correo + ">");
            assertEquals(telefonoEsperado,
                    !tieneViolacionEn(solicitud, "telefonoContacto"),
                    "telefono de contacto <" + telefono + ">");
        }
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 4: Un error por cada campo inválido")
    void cadaCampoInvalidoProduceUnaSolaEntrada() {
        Random azar = GeneradoresCrm.nuevoRandom();
        List<String> campos = List.of(
                "nit", "razonSocial", "nombreContacto", "correoContacto",
                "telefonoContacto");

        for (int i = 0; i < CASOS; i++) {
            boolean[] invalidos = new boolean[campos.size()];
            Set<String> esperados = new LinkedHashSet<>();

            for (int j = 0; j < invalidos.length; j++) {
                invalidos[j] = azar.nextBoolean();
                if (invalidos[j]) {
                    esperados.add(campos.get(j));
                }
            }

            CuentaCorporativaRequest solicitud = new CuentaCorporativaRequest(
                    invalidos[0] ? "12" : "900123456",
                    invalidos[1] ? null : "Arise",
                    invalidos[2] ? "x".repeat(201) : null,
                    invalidos[3] ? "sin-arroba" : null,
                    invalidos[4] ? "abc" : null);

            assertEquals(esperados, camposConViolacion(solicitud),
                    "campos invalidos: " + esperados);
        }
    }

    @Test
    void normalizaElNitYLosTextosEnElConstructor() {
        CuentaCorporativaRequest solicitud = cuenta(
                " 900.123.456-7 ",
                "  Arise Corp  ",
                "   ",
                " contacto@example.test ",
                " +57 300 123 4567 ");

        assertEquals("9001234567", solicitud.nit());
        assertEquals("Arise Corp", solicitud.razonSocial());
        assertNull(solicitud.nombreContacto());
        assertEquals("contacto@example.test", solicitud.correoContacto());
        assertEquals("+57 300 123 4567", solicitud.telefonoContacto());
    }

    @Test
    void losMensajesObligatoriosYDeFormatoEstanEnEspanol() {
        CuentaCorporativaRequest vacia = new CuentaCorporativaRequest(
                null, null, null, null, null);

        assertEquals(Set.of("es obligatorio"), mensajesEn(vacia, "nit"));
        assertEquals(Set.of("es obligatorio"),
                mensajesEn(vacia, "razonSocial"));

        CuentaCorporativaRequest nitCorto = cuenta(
                "1234", "Arise", null, null, null);

        assertEquals(Set.of("debe tener entre 5 y 15 dígitos"),
                mensajesEn(nitCorto, "nit"));

        CuentaCorporativaRequest contactoInvalido = cuenta(
                "900123456", "Arise", null, "sin-arroba", "abc");

        assertEquals(Set.of(
                        "debe ser un correo válido de máximo 254 caracteres"),
                mensajesEn(contactoInvalido, "correoContacto"));
        assertEquals(Set.of(
                        "debe tener entre 7 y 20 caracteres: dígitos, espacios, +, - o paréntesis"),
                mensajesEn(contactoInvalido, "telefonoContacto"));
    }

    @Test
    void losCamposOpcionalesAceptanAusencia() {
        CuentaCorporativaRequest solicitud = cuenta(
                "900123456", "Arise", null, null, null);

        assertTrue(camposConViolacion(solicitud).isEmpty());
    }

    @Test
    void elToStringNoExponeDatosPersonales() {
        CuentaCorporativaRequest solicitud = cuenta(
                "900123456", "Arise Corp", "Ana Pérez", CORREO_VALIDO,
                TELEFONO_VALIDO);

        assertEquals("CuentaCorporativaRequest[datos protegidos]",
                solicitud.toString());
        assertFalse(solicitud.toString().contains("900123456"));
        assertFalse(solicitud.toString().contains("Ana"));
        assertTrue(solicitud.toString().contains("protegidos"));
    }

    private static CuentaCorporativaRequest cuenta(
            String nit,
            String razonSocial,
            String nombreContacto,
            String correoContacto,
            String telefonoContacto) {

        return new CuentaCorporativaRequest(
                nit, razonSocial, nombreContacto, correoContacto,
                telefonoContacto);
    }

    private static String normalizado(String valor) {
        String texto = NormalizadorTexto.texto(valor);
        return texto == null ? "" : texto;
    }

    /**
     * Regla de referencia de {@code @Correo} para un campo opcional: acepta
     * {@code null}, formato {@code local@dominio.tld}, maximo 254 caracteres y
     * parte local de hasta 64.
     */
    private static boolean correoValido(String normalizado) {
        if (normalizado == null) {
            return true;
        }

        if (normalizado.length() > 254
                || !normalizado.matches(".+@.+\\..+")) {
            return false;
        }

        int arroba = normalizado.lastIndexOf('@');
        return arroba > 0 && arroba < normalizado.length() - 1
                && arroba <= 64;
    }

    private static Set<String> camposConViolacion(
            CuentaCorporativaRequest solicitud) {

        return VALIDADOR.validate(solicitud).stream()
                .map(violacion -> violacion.getPropertyPath().toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean tieneViolacionEn(
            CuentaCorporativaRequest solicitud,
            String campo) {

        return !mensajesEn(solicitud, campo).isEmpty();
    }

    private static Set<String> mensajesEn(
            CuentaCorporativaRequest solicitud,
            String campo) {

        return VALIDADOR.validate(solicitud).stream()
                .filter(violacion -> violacion.getPropertyPath().toString()
                        .equals(campo))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
