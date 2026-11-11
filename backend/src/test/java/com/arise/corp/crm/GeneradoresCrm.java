package com.arise.corp.crm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Random;

/**
 * Generadores con semilla fija para las pruebas de propiedades del CRM (D13).
 *
 * <p>No se anade ninguna libreria de pruebas basadas en propiedades: cada
 * propiedad ejecuta al menos 100 casos generados con {@link #SEMILLA} y el
 * mensaje de la asercion incluye la entrada que fallo, de modo que un fallo sea
 * reproducible.
 */
public final class GeneradoresCrm {

    public static final long SEMILLA = 20261111L;

    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String LETRAS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String[] SEPARADORES_DOCUMENTO = {" ", ".", "-", "  "};
    private static final String[] DOMINIOS = {"example.test", "arise.co", "correo.com"};

    private GeneradoresCrm() {
    }

    public static Random nuevoRandom() {
        return new Random(SEMILLA);
    }

    /**
     * Cadenas variadas: {@code null}, vacias, solo espacios, texto normal y
     * caracteres Unicode.
     */
    public static String texto(Random azar) {
        return switch (azar.nextInt(6)) {
            case 0 -> null;
            case 1 -> "";
            case 2 -> " ".repeat(1 + azar.nextInt(4));
            case 3 -> cadena(azar, 1 + azar.nextInt(20));
            case 4 -> "  " + cadena(azar, 1 + azar.nextInt(10)) + "\t\n";
            default -> "áéíóú ñ Ü 中文 " + cadena(azar, 1 + azar.nextInt(8));
        };
    }

    public static String cadena(Random azar, int longitud) {
        StringBuilder texto = new StringBuilder(longitud);

        for (int i = 0; i < longitud; i++) {
            texto.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
        }

        return texto.toString();
    }

    /**
     * Documento base de 3 a 20 letras o digitos (sin separadores).
     */
    public static String documentoBase(Random azar) {
        int longitud = 3 + azar.nextInt(18);
        String alfabeto = azar.nextBoolean() ? ALFABETO : LETRAS + "0123456789";

        StringBuilder documento = new StringBuilder(longitud);
        for (int i = 0; i < longitud; i++) {
            documento.append(alfabeto.charAt(azar.nextInt(alfabeto.length())));
        }

        return documento.toString();
    }

    /**
     * Variante de un documento con separadores y mayusculas y minusculas
     * mezcladas (Property 2).
     */
    public static String varianteDocumento(Random azar, String base) {
        StringBuilder variante = new StringBuilder();

        for (int i = 0; i < base.length(); i++) {
            char caracter = base.charAt(i);
            variante.append(azar.nextBoolean()
                    ? Character.toLowerCase(caracter)
                    : caracter);

            if (i < base.length() - 1 && azar.nextInt(3) == 0) {
                variante.append(SEPARADORES_DOCUMENTO[
                        azar.nextInt(SEPARADORES_DOCUMENTO.length)]);
            }
        }

        return variante.toString();
    }

    /**
     * Documento del que se desconoce si cumple el formato: puede faltar, ser
     * demasiado corto o largo, llevar separadores o letras con CC o NIT.
     */
    public static String documentoDeCualquierForma(Random azar) {
        return switch (azar.nextInt(5)) {
            case 0 -> documentoBase(azar);
            case 1 -> varianteDocumento(azar, documentoBase(azar));
            case 2 -> cadena(azar, 1 + azar.nextInt(2));
            case 3 -> cadena(azar, 21 + azar.nextInt(10));
            default -> cadena(azar, 3 + azar.nextInt(8)) + "ñ";
        };
    }

    /**
     * Correo del que se desconoce si cumple el formato.
     */
    public static String correoDeCualquierForma(Random azar) {
        return switch (azar.nextInt(5)) {
            case 0 -> cadena(azar, 3 + azar.nextInt(10)).toLowerCase(Locale.ROOT)
                    + "@" + DOMINIOS[azar.nextInt(DOMINIOS.length)];
            case 1 -> "cliente.example.test";
            case 2 -> "cliente@example";
            case 3 -> " " + cadena(azar, 6) + "@example.test";
            default -> cadena(azar, 240 + azar.nextInt(20)).toLowerCase(Locale.ROOT)
                    + "@" + DOMINIOS[azar.nextInt(DOMINIOS.length)];
        };
    }

    /**
     * Telefono del que se desconoce si cumple el formato.
     */
    public static String telefonoDeCualquierForma(Random azar) {
        return switch (azar.nextInt(5)) {
            case 0 -> "+57 300 " + (100 + azar.nextInt(900)) + " " + (1000 + azar.nextInt(9000));
            case 1 -> "(601) 000-0000";
            case 2 -> "123456";
            case 3 -> "123456789012345678901";
            default -> "300#0000";
        };
    }

    /**
     * Texto de hasta {@code maximo} caracteres o que lo supera.
     */
    public static String textoDeLongitudVariable(Random azar, int maximo) {
        int longitud = 1 + azar.nextInt(maximo + 2);
        return cadena(azar, longitud);
    }

    /**
     * Codigo de contrato con mayusculas, digitos y guiones, posiblemente
     * invalido por longitud o por caracteres.
     */
    public static String codigoDeCualquierForma(Random azar) {
        return switch (azar.nextInt(4)) {
            case 0 -> "CTR-" + cadena(azar, 3 + azar.nextInt(10));
            case 1 -> "ct-" + cadena(azar, 2 + azar.nextInt(6)).toLowerCase(Locale.ROOT);
            case 2 -> cadena(azar, 1 + azar.nextInt(2));
            default -> cadena(azar, 31 + azar.nextInt(10));
        };
    }

    /**
     * NIT con o sin separadores y de longitud variable.
     */
    public static String nitDeCualquierForma(Random azar) {
        int digitos = 3 + azar.nextInt(15);
        StringBuilder nit = new StringBuilder();

        for (int i = 0; i < digitos; i++) {
            nit.append((char) ('0' + azar.nextInt(10)));
            if (i == 2 && azar.nextBoolean()) {
                nit.append('.');
            }
        }

        return nit.toString();
    }

    /**
     * Valor decimal valido: de 0 a 9999999999999.99 con 0, 1 o 2 decimales.
     */
    public static BigDecimal valorValido(Random azar) {
        long entero = Math.abs(azar.nextLong()) % 1_000_000_000_000L;
        int decimales = azar.nextInt(3);

        if (decimales == 0) {
            return BigDecimal.valueOf(entero);
        }

        if (decimales == 1) {
            return BigDecimal.valueOf(entero)
                    .add(BigDecimal.valueOf(azar.nextInt(10), 1));
        }

        return BigDecimal.valueOf(Math.abs(azar.nextLong()) % 100_000_000_000_000L, 2);
    }

    /**
     * Valor decimal del que se desconoce si cumple las reglas.
     */
    public static BigDecimal valorDeCualquierForma(Random azar) {
        return switch (azar.nextInt(4)) {
            case 0 -> valorValido(azar);
            case 1 -> valorValido(azar).negate();
            case 2 -> valorValido(azar).add(BigDecimal.valueOf(azar.nextInt(1000), 3));
            default -> BigDecimal.valueOf(
                    Math.abs(azar.nextLong()) % 1_000_000_000_000_000L);
        };
    }

    /**
     * Fecha de calendario entre 1950 y 2100.
     */
    public static LocalDate fecha(Random azar) {
        return LocalDate.of(1950, 1, 1).plusDays(azar.nextInt(54_000));
    }
}
