package com.arise.corp.erp.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.arise.corp.crm.GeneradoresCrm;
import com.arise.corp.erp.GeneradoresErp;
import com.arise.corp.erp.entity.TipoDocumentoEmpleado;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Pruebas de validacion de {@link EmpleadoRequest} (D4, D5, Requirements 25.3
 * y 25.7).
 *
 * <p>Cubre las Properties 1, 2, 3 y 4 sobre el DTO de entrada: normalizacion
 * idempotente, variantes de documento equivalentes, reglas de formato iguales
 * a la referencia y un error por cada campo invalido. El validador usa un
 * {@code ClockProvider} que apunta al reloj de cada caso.
 */
class EmpleadoRequestValidacionTests {

    private static final int CASOS = 120;

    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    private static final String CORREO_VALIDO = "empleada@example.test";
    private static final String TELEFONO_VALIDO = "+57 310 000 0000";
    private static final LocalDate FECHA_INGRESO = LocalDate.of(2025, 3, 1);

    private static final AtomicReference<Clock> RELOJ = new AtomicReference<>(
            Clock.fixed(Instant.parse("2026-11-21T03:30:00Z"), ZoneId.of("UTC")));

    private static final Validator VALIDADOR = Validation.byDefaultProvider()
            .configure()
            .clockProvider(RELOJ::get)
            .buildValidatorFactory()
            .getValidator();

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 1: La normalización es idempotente")
    void laNormalizacionEsIdempotente() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            EmpleadoRequest primera = empleado(
                    TipoDocumentoEmpleado.CC,
                    GeneradoresCrm.documentoDeCualquierForma(azar),
                    GeneradoresCrm.texto(azar),
                    GeneradoresCrm.texto(azar),
                    GeneradoresCrm.texto(azar),
                    CORREO_VALIDO,
                    TELEFONO_VALIDO,
                    FECHA_INGRESO);

            EmpleadoRequest segunda = empleado(
                    primera.tipoDocumento(),
                    primera.numeroDocumento(),
                    primera.nombre(),
                    primera.cargo(),
                    primera.area(),
                    primera.correo(),
                    primera.telefono(),
                    primera.fechaIngreso());

            assertEquals(primera, segunda,
                    "normalizacion no idempotente para " + primera.numeroDocumento());
        }
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 2: Las variantes de formato de un documento o de una referencia son el mismo valor")
    void lasVariantesDeDocumentoProducenElMismoValor() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            String base = GeneradoresCrm.documentoBase(azar);
            String variante = GeneradoresCrm.varianteDocumento(azar, base);

            EmpleadoRequest original = empleado(
                    TipoDocumentoEmpleado.PASAPORTE, base, "Empleada",
                    "Cargo", "Area", CORREO_VALIDO, TELEFONO_VALIDO, FECHA_INGRESO);
            EmpleadoRequest conVariante = empleado(
                    TipoDocumentoEmpleado.PASAPORTE, variante, "Empleada",
                    "Cargo", "Area", CORREO_VALIDO, TELEFONO_VALIDO, FECHA_INGRESO);

            assertEquals(original.numeroDocumento(), conVariante.numeroDocumento(),
                    "documento <" + variante + "> frente a <" + base + ">");
        }
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void elDocumentoValidaSegunLaReglaDeReferencia() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            TipoDocumentoEmpleado tipo = TipoDocumentoEmpleado.values()[
                    azar.nextInt(TipoDocumentoEmpleado.values().length)];
            String documento = GeneradoresCrm.documentoDeCualquierForma(azar);
            String normalizado = NormalizadorTexto.documento(documento);

            boolean esperado = normalizado != null
                    && normalizado.matches("[A-Z0-9]{3,20}")
                    && (tipo != TipoDocumentoEmpleado.CC
                            || normalizado.matches("[0-9]+"));

            EmpleadoRequest solicitud = empleado(
                    tipo, documento, "Empleada", "Cargo", "Area",
                    CORREO_VALIDO, TELEFONO_VALIDO, FECHA_INGRESO);

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
            String cargo = GeneradoresCrm.textoDeLongitudVariable(azar, 100);
            String area = GeneradoresCrm.textoDeLongitudVariable(azar, 100);
            String correo = GeneradoresCrm.correoDeCualquierForma(azar);
            String telefono = GeneradoresCrm.telefonoDeCualquierForma(azar);

            EmpleadoRequest solicitud = empleado(
                    TipoDocumentoEmpleado.CC, "10234567", nombre, cargo, area,
                    correo, telefono, FECHA_INGRESO);

            assertEquals(normalizado(nombre).length() <= 200,
                    !tieneViolacionEn(solicitud, "nombre"),
                    "nombre de longitud " + normalizado(nombre).length());
            assertEquals(normalizado(cargo).length() <= 100,
                    !tieneViolacionEn(solicitud, "cargo"),
                    "cargo de longitud " + normalizado(cargo).length());
            assertEquals(normalizado(area).length() <= 100,
                    !tieneViolacionEn(solicitud, "area"),
                    "area de longitud " + normalizado(area).length());
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
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void laFechaIngresoCoincideConLaFechaDeBogotaDelReloj() {
        Random azar = GeneradoresErp.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            Instant instante = GeneradoresErp.relojAleatorio(azar);
            RELOJ.set(Clock.fixed(instante, ZoneId.of("UTC")));
            LocalDate hoy = ZonedDateTime.ofInstant(instante, ZONA_COLOMBIA)
                    .toLocalDate();

            for (LocalDate fecha : GeneradoresErp.fechaAlrededorDe(hoy)) {
                EmpleadoRequest solicitud = empleado(
                        TipoDocumentoEmpleado.CC, "10234567", "Empleada",
                        "Cargo", "Area", CORREO_VALIDO, TELEFONO_VALIDO, fecha);

                assertEquals(!fecha.isAfter(hoy),
                        !tieneViolacionEn(solicitud, "fechaIngreso"),
                        "fecha " + fecha + " con el reloj " + instante);
            }
        }

        RELOJ.set(Clock.fixed(
                Instant.parse("2026-11-21T03:30:00Z"), ZoneId.of("UTC")));
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 4: Un error por cada campo inválido")
    void cadaCampoInvalidoProduceUnaSolaEntrada() {
        Random azar = GeneradoresCrm.nuevoRandom();
        List<String> campos = List.of(
                "tipoDocumento", "numeroDocumento", "nombre", "cargo", "area",
                "correo", "telefono", "fechaIngreso");

        for (int i = 0; i < CASOS; i++) {
            boolean[] invalidos = new boolean[campos.size()];
            Set<String> esperados = new LinkedHashSet<>();

            for (int j = 0; j < invalidos.length; j++) {
                invalidos[j] = azar.nextBoolean();
                if (invalidos[j]) {
                    esperados.add(campos.get(j));
                }
            }

            EmpleadoRequest solicitud = new EmpleadoRequest(
                    invalidos[0] ? null : TipoDocumentoEmpleado.CC,
                    invalidos[1] ? "!" : "10234567",
                    invalidos[2] ? null : "Empleada",
                    invalidos[3] ? null : "Cargo",
                    invalidos[4] ? "x".repeat(101) : "Area",
                    invalidos[5] ? "sin-arroba" : CORREO_VALIDO,
                    invalidos[6] ? "abc" : TELEFONO_VALIDO,
                    invalidos[7] ? null : FECHA_INGRESO);

            assertEquals(esperados, camposConViolacion(solicitud),
                    "campos invalidos: " + esperados);
        }
    }

    @Test
    void normalizaElDocumentoYLosTextosEnElConstructor() {
        EmpleadoRequest solicitud = empleado(
                TipoDocumentoEmpleado.CC,
                " 1.023.456-7 ",
                "  Ana Pérez  ",
                " Analista ",
                " Finanzas ",
                " ana@example.test ",
                " +57 310 000 0000 ",
                FECHA_INGRESO);

        assertEquals("10234567", solicitud.numeroDocumento());
        assertEquals("Ana Pérez", solicitud.nombre());
        assertEquals("Analista", solicitud.cargo());
        assertEquals("Finanzas", solicitud.area());
        assertEquals("ana@example.test", solicitud.correo());
        assertEquals("+57 310 000 0000", solicitud.telefono());
    }

    @Test
    void nitNoEsUnTipoDeDocumentoDeEmpleado() {
        assertThrows(IllegalArgumentException.class,
                () -> TipoDocumentoEmpleado.valueOf("NIT"));
    }

    @Test
    void losMensajesEstanEnEspanol() {
        EmpleadoRequest vacio = new EmpleadoRequest(
                null, null, null, null, null, null, null, null);

        long obligatorios = VALIDADOR.validate(vacio).stream()
                .filter(violacion -> "es obligatorio".equals(
                        violacion.getMessage()))
                .count();
        assertEquals(8, obligatorios);

        EmpleadoRequest letrasParaCc = empleado(
                TipoDocumentoEmpleado.CC, "ABC123", "Empleada", "Cargo",
                "Area", CORREO_VALIDO, TELEFONO_VALIDO, FECHA_INGRESO);

        assertEquals(Set.of("solo admite dígitos para el tipo CC"),
                mensajesEn(letrasParaCc, "numeroDocumento"));

        EmpleadoRequest fechaFutura = empleado(
                TipoDocumentoEmpleado.CC, "10234567", "Empleada", "Cargo",
                "Area", CORREO_VALIDO, TELEFONO_VALIDO, LocalDate.of(2026, 11, 21));

        assertEquals(Set.of("no puede ser posterior a la fecha actual"),
                mensajesEn(fechaFutura, "fechaIngreso"));
    }

    @Test
    void elToStringNoExponeDatosPersonales() {
        EmpleadoRequest solicitud = empleado(
                TipoDocumentoEmpleado.CC, "10234567", "Ana Pérez", "Cargo",
                "Area", CORREO_VALIDO, TELEFONO_VALIDO, FECHA_INGRESO);

        assertEquals("EmpleadoRequest[datos protegidos]", solicitud.toString());
        assertFalse(solicitud.toString().contains("Ana"));
        assertFalse(solicitud.toString().contains("10234567"));
        assertTrue(solicitud.toString().contains("protegidos"));
    }

    private static EmpleadoRequest empleado(
            TipoDocumentoEmpleado tipo,
            String documento,
            String nombre,
            String cargo,
            String area,
            String correo,
            String telefono,
            LocalDate fechaIngreso) {

        return new EmpleadoRequest(
                tipo, documento, nombre, cargo, area, correo, telefono,
                fechaIngreso);
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

    private static Set<String> camposConViolacion(EmpleadoRequest solicitud) {
        return VALIDADOR.validate(solicitud).stream()
                .map(violacion -> violacion.getPropertyPath().toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean tieneViolacionEn(
            EmpleadoRequest solicitud,
            String campo) {

        return !mensajesEn(solicitud, campo).isEmpty();
    }

    private static Set<String> mensajesEn(
            EmpleadoRequest solicitud,
            String campo) {

        return VALIDADOR.validate(solicitud).stream()
                .filter(violacion -> violacion.getPropertyPath().toString()
                        .equals(campo))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
