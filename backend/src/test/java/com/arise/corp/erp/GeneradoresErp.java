package com.arise.corp.erp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.arise.corp.crm.GeneradoresCrm;

/**
 * Generadores con semilla fija para las pruebas de propiedades del ERP (D15).
 *
 * <p>No se anade ninguna libreria de pruebas basadas en propiedades: cada
 * propiedad ejecuta al menos 100 casos generados con {@link #SEMILLA} y el
 * mensaje de la asercion incluye la entrada que fallo, de modo que un fallo sea
 * reproducible. Los generadores de texto, documentos, correo, telefono y fecha
 * se reutilizan de {@link GeneradoresCrm}; aqui solo se anaden los del dominio
 * del ERP.
 *
 * <p>{@link MovimientoGenerado} usa los nombres de las constantes
 * {@code TipoMovimiento} y {@code EstadoMovimiento} como texto para no
 * depender de las enumeraciones del ERP; el test que lo consuma los convierte
 * con {@code valueOf}. El generador del frontend usa la misma representacion.
 */
public final class GeneradoresErp {

    public static final long SEMILLA = 20261113L;

    private static final String[] TIPOS_MOVIMIENTO = {"INGRESO", "EGRESO"};
    private static final String[] ESTADOS_MOVIMIENTO = {
            "PENDIENTE", "PAGADO", "ANULADO"
    };
    private static final String LETRAS_REFERENCIA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String ALFABETO_REFERENCIA =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ./-";
    private static final String[] SEPARADORES_REFERENCIA = {" ", ".", "/", "-"};

    private GeneradoresErp() {
    }

    public static Random nuevoRandom() {
        return new Random(SEMILLA);
    }

    /**
     * Referencia del que se desconoce si cumple el formato: puede faltar, ser
     * vacia, valida, con separadores y minusculas, demasiado larga o con un
     * caracter no permitido.
     */
    public static String referenciaDeCualquierForma(Random azar) {
        return switch (azar.nextInt(7)) {
            case 0 -> null;
            case 1 -> "";
            case 2 -> " ".repeat(1 + azar.nextInt(4));
            case 3 -> referenciaBase(azar);
            case 4 -> varianteReferencia(azar, referenciaBase(azar));
            case 5 -> GeneradoresCrm.cadena(azar, 51 + azar.nextInt(10));
            default -> referenciaBase(azar) + "ñ";
        };
    }

    /**
     * Variante de una referencia con separadores, espacios alrededor y
     * mayusculas y minusculas mezcladas. La Referencia_Normalizada no cambia.
     */
    public static String varianteReferencia(Random azar, String base) {
        StringBuilder variante = new StringBuilder();

        for (int i = 0; i < base.length(); i++) {
            char caracter = base.charAt(i);
            variante.append(azar.nextBoolean()
                    ? Character.toLowerCase(caracter)
                    : caracter);

            if (i < base.length() - 1 && azar.nextInt(4) == 0) {
                variante.append(SEPARADORES_REFERENCIA[
                        azar.nextInt(SEPARADORES_REFERENCIA.length)]);
            }
        }

        return azar.nextBoolean()
                ? "  " + variante + "  "
                : variante.toString();
    }

    /**
     * Concepto del que se desconoce si cumple el formato: puede faltar, ser
     * vacio, tener menos de 3 o mas de 300 caracteres, o ser valido con
     * espacios alrededor y caracteres Unicode.
     */
    public static String conceptoDeCualquierForma(Random azar) {
        return switch (azar.nextInt(7)) {
            case 0 -> null;
            case 1 -> "";
            case 2 -> " ".repeat(1 + azar.nextInt(4));
            case 3 -> GeneradoresCrm.cadena(azar, 1 + azar.nextInt(2));
            case 4 -> "  " + GeneradoresCrm.cadena(azar, 3 + azar.nextInt(298)) + "  ";
            case 5 -> "áéíóú ñ 中文 " + GeneradoresCrm.cadena(azar, 1 + azar.nextInt(20));
            default -> GeneradoresCrm.cadena(azar, 301 + azar.nextInt(20));
        };
    }

    /**
     * Valor mayor que 0, con hasta 13 digitos enteros y 0, 1 o 2 decimales.
     */
    public static BigDecimal valorPositivoValido(Random azar) {
        long entero = 1 + Math.floorMod(azar.nextLong(), 9_999_999_999_999L);

        return switch (azar.nextInt(3)) {
            case 0 -> BigDecimal.valueOf(entero);
            case 1 -> BigDecimal.valueOf(entero)
                    .add(BigDecimal.valueOf(1 + azar.nextInt(9), 1));
            default -> BigDecimal.valueOf(entero)
                    .add(BigDecimal.valueOf(1 + azar.nextInt(99), 2));
        };
    }

    /**
     * Valor decimal del que se desconoce si cumple las reglas: incluye 0,
     * negativos, 3 decimales y 14 digitos enteros.
     */
    public static BigDecimal valorDeCualquierForma(Random azar) {
        return switch (azar.nextInt(7)) {
            case 0 -> BigDecimal.ZERO;
            case 1 -> valorPositivoValido(azar).negate();
            case 2 -> valorPositivoValido(azar)
                    .add(BigDecimal.valueOf(1 + azar.nextInt(999), 3));
            case 3 -> BigDecimal.valueOf(
                    10_000_000_000_000L
                            + Math.floorMod(azar.nextLong(), 90_000_000_000_000L));
            case 4 -> BigDecimal.valueOf(
                    Math.floorMod(azar.nextLong(), 100_000_000_000_000L));
            case 5 -> valorPositivoValido(azar);
            default -> BigDecimal.valueOf(
                    Math.floorMod(azar.nextLong(), 100_000_000_000_000L), 2);
        };
    }

    /**
     * Instante que cubre las horas en que UTC y Bogota tienen fechas distintas
     * (00:00 a 04:59 UTC) y los bordes del cambio de fecha.
     */
    public static Instant relojAleatorio(Random azar) {
        LocalDate dia = GeneradoresCrm.fecha(azar);

        return switch (azar.nextInt(4)) {
            case 0 -> dia.atStartOfDay().toInstant(ZoneOffset.UTC);
            case 1 -> dia.atTime(4, 59, 59, 999_999_999)
                    .toInstant(ZoneOffset.UTC);
            case 2 -> dia.atTime(5, 0).toInstant(ZoneOffset.UTC);
            default -> {
                int hora = azar.nextBoolean()
                        ? azar.nextInt(5)
                        : 5 + azar.nextInt(19);
                yield dia.atTime(hora, azar.nextInt(60), azar.nextInt(60))
                        .toInstant(ZoneOffset.UTC);
            }
        };
    }

    /**
     * Fechas alrededor de {@code fecha}: ayer, hoy y manana, para cubrir los
     * bordes de una regla que depende de la fecha actual.
     */
    public static List<LocalDate> fechaAlrededorDe(LocalDate fecha) {
        return List.of(fecha.minusDays(1), fecha, fecha.plusDays(1));
    }

    /**
     * Movimiento con tipo, estado, fecha y valor; el valor siempre es positivo.
     */
    public static MovimientoGenerado movimiento(Random azar) {
        return new MovimientoGenerado(
                TIPOS_MOVIMIENTO[azar.nextInt(TIPOS_MOVIMIENTO.length)],
                ESTADOS_MOVIMIENTO[azar.nextInt(ESTADOS_MOVIMIENTO.length)],
                GeneradoresCrm.fecha(azar),
                valorPositivoValido(azar));
    }

    /**
     * Conjunto de {@code cantidad} movimientos generados con la semilla fija.
     */
    public static List<MovimientoGenerado> conjuntoMovimientos(int cantidad) {
        Random azar = nuevoRandom();
        List<MovimientoGenerado> movimientos = new ArrayList<>(cantidad);

        for (int i = 0; i < cantidad; i++) {
            movimientos.add(movimiento(azar));
        }

        return movimientos;
    }

    /**
     * Rango con limites presentes o ausentes y, si ambos existen,
     * {@code desde <= hasta}.
     */
    public static RangoGenerado rango(Random azar) {
        LocalDate primero = GeneradoresCrm.fecha(azar);
        LocalDate segundo = GeneradoresCrm.fecha(azar);
        LocalDate desde = primero.isBefore(segundo) ? primero : segundo;
        LocalDate hasta = primero.isBefore(segundo) ? segundo : primero;

        return switch (azar.nextInt(4)) {
            case 0 -> new RangoGenerado(null, null);
            case 1 -> new RangoGenerado(desde, null);
            case 2 -> new RangoGenerado(null, hasta);
            default -> azar.nextBoolean()
                    ? new RangoGenerado(desde, hasta)
                    : new RangoGenerado(desde, desde);
        };
    }

    private static String referenciaBase(Random azar) {
        int longitud = 1 + azar.nextInt(50);
        StringBuilder referencia = new StringBuilder(longitud);
        referencia.append(LETRAS_REFERENCIA.charAt(
                azar.nextInt(LETRAS_REFERENCIA.length())));

        for (int i = 1; i < longitud; i++) {
            referencia.append(ALFABETO_REFERENCIA.charAt(
                    azar.nextInt(ALFABETO_REFERENCIA.length())));
        }

        return referencia.toString();
    }

    /**
     * Movimiento generado; {@code tipo} y {@code estado} son los nombres de las
     * constantes del ERP.
     */
    public record MovimientoGenerado(
            String tipo,
            String estado,
            LocalDate fecha,
            BigDecimal valor) {
    }

    /**
     * Limites de un rango; cada uno puede ser {@code null}.
     */
    public record RangoGenerado(LocalDate desde, LocalDate hasta) {
    }
}
