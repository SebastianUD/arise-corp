package com.arise.corp.shared.normalizacion;

import java.util.Locale;

/**
 * Normalizacion de identificadores y textos de entrada (CRM-04, D2).
 *
 * <p>Clase final con metodos estaticos puros: no lanza excepciones ni modifica
 * su entrada. {@link #texto(String)}, {@link #documento(String)} y
 * {@link #codigo(String)} son idempotentes (Property 1).
 */
public final class NormalizadorTexto {

    private NormalizadorTexto() {
    }

    /**
     * Recorta el texto con {@link String#strip()}. Un texto vacio se convierte
     * en {@code null}.
     */
    public static String texto(String valor) {
        if (valor == null) {
            return null;
        }

        String recortado = valor.strip();
        return recortado.isEmpty() ? null : recortado;
    }

    /**
     * Documento normalizado: sin espacios Unicode, puntos ni guiones y en
     * mayusculas. Las variantes de formato de un documento producen el mismo
     * valor (Property 2).
     */
    public static String documento(String valor) {
        String recortado = texto(valor);
        if (recortado == null) {
            return null;
        }

        StringBuilder normalizado = new StringBuilder(recortado.length());
        recortado.codePoints()
                .filter(codigo -> !Character.isWhitespace(codigo)
                        && !Character.isSpaceChar(codigo))
                .filter(codigo -> codigo != '.' && codigo != '-')
                .forEach(normalizado::appendCodePoint);

        if (normalizado.isEmpty()) {
            return null;
        }

        return normalizado.toString().toUpperCase(Locale.ROOT);
    }

    /**
     * Codigo normalizado: recortado y en mayusculas.
     */
    public static String codigo(String valor) {
        String recortado = texto(valor);
        return recortado == null ? null : recortado.toUpperCase(Locale.ROOT);
    }

    /**
     * Patron {@code %q%} para {@code LIKE ... ESCAPE '\'}, escapando la barra
     * invertida, el porcentaje y el guion bajo.
     */
    public static String patronContiene(String q) {
        String base = texto(q);
        if (base == null) {
            return null;
        }

        String escapado = base
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");

        return "%" + escapado + "%";
    }
}
