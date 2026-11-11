package com.arise.corp.shared.normalizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.arise.corp.crm.GeneradoresCrm;

/**
 * Pruebas de la normalizacion de identificadores (CRM-04, D2).
 */
class NormalizadorTextoTests {

    private static final int CASOS = 120;

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 1: La normalización es idempotente")
    void laNormalizacionEsIdempotente() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            String cadena = GeneradoresCrm.texto(azar);

            assertEquals(
                    NormalizadorTexto.texto(cadena),
                    NormalizadorTexto.texto(NormalizadorTexto.texto(cadena)),
                    "texto no idempotente para: <" + cadena + ">");

            assertEquals(
                    NormalizadorTexto.documento(cadena),
                    NormalizadorTexto.documento(NormalizadorTexto.documento(cadena)),
                    "documento no idempotente para: <" + cadena + ">");

            assertEquals(
                    NormalizadorTexto.codigo(cadena),
                    NormalizadorTexto.codigo(NormalizadorTexto.codigo(cadena)),
                    "codigo no idempotente para: <" + cadena + ">");

            String texto = NormalizadorTexto.texto(cadena);
            if (texto != null) {
                assertEquals(cadena.strip(), texto,
                        "texto debe ser el recorte para: <" + cadena + ">");
            } else if (cadena != null) {
                assertEquals("", cadena.strip(),
                        "texto solo puede ser null si la cadena queda vacia: <"
                                + cadena + ">");
            }
        }

        assertNull(NormalizadorTexto.texto(null));
        assertNull(NormalizadorTexto.texto(""));
        assertNull(NormalizadorTexto.texto("   \t\n"));
        assertNull(NormalizadorTexto.documento(" . - "));
        assertNull(NormalizadorTexto.codigo("  "));
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 2: Las variantes de formato de un documento son el mismo documento")
    void lasVariantesDeFormatoDeUnDocumentoSonElMismoDocumento() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            String base = GeneradoresCrm.documentoBase(azar);
            String variante = GeneradoresCrm.varianteDocumento(azar, base);

            assertEquals(
                    base.toUpperCase(Locale.ROOT),
                    NormalizadorTexto.documento(variante),
                    "variante distinta para el documento base <" + base
                            + ">: <" + variante + ">");
        }

        assertEquals("10234567", NormalizadorTexto.documento("1.023.456-7"));
        assertEquals("10234567", NormalizadorTexto.documento(" 1 0 2 3 4 5 6 7 "));
        assertEquals("AB123", NormalizadorTexto.documento("ab-1.2 3"));
        assertEquals("AB123", NormalizadorTexto.documento("AB123"));
    }

    @Test
    void normalizaElCodigoARecorteYMayusculas() {
        assertEquals("CTR-2026-001", NormalizadorTexto.codigo("  ctr-2026-001 "));
        assertEquals("AB1", NormalizadorTexto.codigo("ab1"));
        assertNull(NormalizadorTexto.codigo("   "));
    }

    @Test
    void elPatronDeBusquedaEscapaLosComodinesDeLike() {
        assertEquals("%\\%a\\_b\\\\c%", NormalizadorTexto.patronContiene("%a_b\\c"));
        assertEquals("%ana%", NormalizadorTexto.patronContiene("  ana "));
        assertNull(NormalizadorTexto.patronContiene(null));
        assertNull(NormalizadorTexto.patronContiene("   "));
    }
}
