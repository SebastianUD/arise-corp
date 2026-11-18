package com.arise.corp.erp.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.arise.corp.crm.GeneradoresCrm;
import com.arise.corp.erp.entity.TipoDocumentoProveedor;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Pruebas de validacion de {@link ProveedorRequest} (D4, D5, Requirements 25.3
 * y 25.7).
 *
 * <p>Cubre las Properties 2, 3 y 4 sobre el DTO de entrada: variantes de
 * documento equivalentes, reglas de formato iguales a la referencia (con los
 * opcionales convertidos en {@code null}) y un error por cada campo invalido.
 */
class ProveedorRequestValidacionTests {

    private static final int CASOS = 120;

    private static final Validator VALIDADOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static final String CORREO_VALIDO = "proveedor@example.test";
    private static final String TELEFONO_VALIDO = "601 000 0000";

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 2: Las variantes de formato de un documento o de una referencia son el mismo valor")
    void lasVariantesDeDocumentoProducenElMismoValor() {
        Random azar = GeneradoresCrm.nuevoRandom();
        TipoDocumentoProveedor[] tipos = TipoDocumentoProveedor.values();

        for (int i = 0; i < CASOS; i++) {
            TipoDocumentoProveedor tipo = tipos[azar.nextInt(tipos.length)];
            String base = GeneradoresCrm.documentoBase(azar);
            String variante = GeneradoresCrm.varianteDocumento(azar, base);

            ProveedorRequest original = proveedor(
                    tipo, base, "Proveedor", null, CORREO_VALIDO,
                    TELEFONO_VALIDO, null, null);
            ProveedorRequest conVariante = proveedor(
                    tipo, variante, "Proveedor", null, CORREO_VALIDO,
                    TELEFONO_VALIDO, null, null);

            assertEquals(original.numeroDocumento(), conVariante.numeroDocumento(),
                    "documento <" + variante + "> frente a <" + base + ">");
        }
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void elDocumentoValidaSegunLaReglaDeReferencia() {
        Random azar = GeneradoresCrm.nuevoRandom();
        TipoDocumentoProveedor[] tipos = TipoDocumentoProveedor.values();

        for (int i = 0; i < CASOS; i++) {
            TipoDocumentoProveedor tipo = tipos[azar.nextInt(tipos.length)];
            String documento = GeneradoresCrm.documentoDeCualquierForma(azar);
            String normalizado = NormalizadorTexto.documento(documento);

            boolean soloDigitos = tipo == TipoDocumentoProveedor.CC
                    || tipo == TipoDocumentoProveedor.NIT;
            boolean esperado = normalizado != null
                    && normalizado.matches("[A-Z0-9]{3,20}")
                    && (!soloDigitos || normalizado.matches("[0-9]+"));

            ProveedorRequest solicitud = proveedor(
                    tipo, documento, "Proveedor", null, CORREO_VALIDO,
                    TELEFONO_VALIDO, null, null);

            assertEquals(esperado, !tieneViolacionEn(solicitud, "numeroDocumento"),
                    "documento <" + documento + "> para el tipo " + tipo);
        }
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void lasLongitudesCorreoYTelefonoCoincidenConLaReglaDeReferencia() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            String nombre = GeneradoresCrm.textoDeLongitudVariable(azar, 200);
            String contacto = GeneradoresCrm.textoDeLongitudVariable(azar, 200);
            String direccion = GeneradoresCrm.textoDeLongitudVariable(azar, 200);
            String ciudad = GeneradoresCrm.textoDeLongitudVariable(azar, 100);
            String correo = GeneradoresCrm.correoDeCualquierForma(azar);
            String telefono = GeneradoresCrm.telefonoDeCualquierForma(azar);

            ProveedorRequest solicitud = proveedor(
                    TipoDocumentoProveedor.NIT, "900765432", nombre, contacto,
                    correo, telefono, direccion, ciudad);

            assertEquals(normalizado(nombre).length() <= 200,
                    !tieneViolacionEn(solicitud, "nombre"),
                    "nombre de longitud " + normalizado(nombre).length());
            assertEquals(normalizado(contacto).length() <= 200,
                    !tieneViolacionEn(solicitud, "nombreContacto"),
                    "contacto de longitud " + normalizado(contacto).length());
            assertEquals(normalizado(direccion).length() <= 200,
                    !tieneViolacionEn(solicitud, "direccion"),
                    "direccion de longitud " + normalizado(direccion).length());
            assertEquals(normalizado(ciudad).length() <= 100,
                    !tieneViolacionEn(solicitud, "ciudad"),
                    "ciudad de longitud " + normalizado(ciudad).length());
            assertEquals(correoValido(normalizado(correo)),
                    !tieneViolacionEn(solicitud, "correo"),
                    "correo <" + correo + ">");
            assertEquals(normalizado(telefono).matches("[0-9 +()\\-]{7,20}"),
                    !tieneViolacionEn(solicitud, "telefono"),
                    "telefono <" + telefono + ">");
        }
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 4: Un error por cada campo inválido")
    void cadaCampoInvalidoProduceUnaSolaEntrada() {
        Random azar = GeneradoresCrm.nuevoRandom();
        List<String> campos = List.of(
                "tipoDocumento", "numeroDocumento", "nombre", "nombreContacto",
                "correo", "telefono", "direccion", "ciudad");

        for (int i = 0; i < CASOS; i++) {
            boolean[] invalidos = new boolean[campos.size()];
            Set<String> esperados = new LinkedHashSet<>();

            for (int j = 0; j < invalidos.length; j++) {
                invalidos[j] = azar.nextBoolean();
                if (invalidos[j]) {
                    esperados.add(campos.get(j));
                }
            }

            ProveedorRequest solicitud = new ProveedorRequest(
                    invalidos[0] ? null : TipoDocumentoProveedor.NIT,
                    invalidos[1] ? "!" : "900765432",
                    invalidos[2] ? null : "Proveedor",
                    invalidos[3] ? "x".repeat(201) : null,
                    invalidos[4] ? "sin-arroba" : CORREO_VALIDO,
                    invalidos[5] ? "abc" : TELEFONO_VALIDO,
                    invalidos[6] ? "x".repeat(201) : null,
                    invalidos[7] ? "y".repeat(101) : null);

            assertEquals(esperados, camposConViolacion(solicitud),
                    "campos invalidos: " + esperados);
        }
    }

    @Test
    void losOpcionalesVaciosQuedanNulos() {
        ProveedorRequest solicitud = proveedor(
                TipoDocumentoProveedor.NIT,
                " 900.765.432 ",
                "  Proveedor Ejemplo S.A.S.  ",
                "   ",
                " proveedor@example.test ",
                " 601 000 0000 ",
                "",
                "   ");

        assertEquals("900765432", solicitud.numeroDocumento());
        assertEquals("Proveedor Ejemplo S.A.S.", solicitud.nombre());
        assertNull(solicitud.nombreContacto());
        assertNull(solicitud.direccion());
        assertNull(solicitud.ciudad());
    }

    @Test
    void nitYCedulaExigenSoloDigitos() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            TipoDocumentoProveedor tipo = TipoDocumentoProveedor.values()[
                    azar.nextInt(TipoDocumentoProveedor.values().length)];
            String documento = GeneradoresCrm.documentoBase(azar);

            ProveedorRequest solicitud = proveedor(
                    tipo, documento, "Proveedor", null, CORREO_VALIDO,
                    TELEFONO_VALIDO, null, null);

            boolean exigeDigitos = tipo == TipoDocumentoProveedor.CC
                    || tipo == TipoDocumentoProveedor.NIT;
            boolean esperado = !exigeDigitos
                    || solicitud.numeroDocumento().matches("[0-9]+");

            assertEquals(esperado,
                    !tieneViolacionEn(solicitud, "numeroDocumento"),
                    "documento <" + documento + "> para el tipo " + tipo);
        }
    }

    @Test
    void losMensajesEstanEnEspanol() {
        ProveedorRequest vacio = new ProveedorRequest(
                null, null, null, null, null, null, null, null);

        long obligatorios = VALIDADOR.validate(vacio).stream()
                .filter(violacion -> "es obligatorio".equals(
                        violacion.getMessage()))
                .count();
        assertEquals(5, obligatorios);

        ProveedorRequest letrasParaNit = proveedor(
                TipoDocumentoProveedor.NIT, "ABC123", "Proveedor", null,
                CORREO_VALIDO, TELEFONO_VALIDO, null, null);

        assertEquals(
                Set.of("solo admite dígitos para el tipo CC o NIT"),
                mensajesEn(letrasParaNit, "numeroDocumento"));
    }

    @Test
    void elToStringNoExponeDatosPersonales() {
        ProveedorRequest solicitud = proveedor(
                TipoDocumentoProveedor.NIT, "900765432", "Proveedor Ejemplo",
                "Contacto Ejemplo", CORREO_VALIDO, TELEFONO_VALIDO,
                "Calle 1", "Bogotá");

        assertEquals("ProveedorRequest[datos protegidos]", solicitud.toString());
        assertFalse(solicitud.toString().contains("Proveedor Ejemplo"));
        assertFalse(solicitud.toString().contains("900765432"));
        assertTrue(solicitud.toString().contains("protegidos"));
    }

    private static ProveedorRequest proveedor(
            TipoDocumentoProveedor tipo,
            String documento,
            String nombre,
            String nombreContacto,
            String correo,
            String telefono,
            String direccion,
            String ciudad) {

        return new ProveedorRequest(
                tipo, documento, nombre, nombreContacto, correo, telefono,
                direccion, ciudad);
    }

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

    private static Set<String> camposConViolacion(ProveedorRequest solicitud) {
        return VALIDADOR.validate(solicitud).stream()
                .map(violacion -> violacion.getPropertyPath().toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean tieneViolacionEn(
            ProveedorRequest solicitud,
            String campo) {

        return !mensajesEn(solicitud, campo).isEmpty();
    }

    private static Set<String> mensajesEn(
            ProveedorRequest solicitud,
            String campo) {

        return VALIDADOR.validate(solicitud).stream()
                .filter(violacion -> violacion.getPropertyPath().toString()
                        .equals(campo))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
