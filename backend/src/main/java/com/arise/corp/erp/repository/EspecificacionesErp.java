package com.arise.corp.erp.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.MovimientoFinanciero;
import com.arise.corp.erp.entity.TipoMovimiento;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;

import jakarta.persistence.criteria.Predicate;

/**
 * Filtros y busquedas del ERP con la API de criterios y solo JPQL portable
 * (Property 15).
 *
 * <p>Los comodines {@code %}, {@code _} y {@code \} se escapan antes de
 * construir el {@code LIKE ... ESCAPE '\'}. Un filtro ausente devuelve una
 * especificacion sin restricciones, de modo que se pueda combinar con
 * {@link Specification#allOf(java.lang.Iterable)}.
 */
public final class EspecificacionesErp {

    private static final char ESCAPE = '\\';

    private EspecificacionesErp() {
    }

    /**
     * Busca por nombre sin distinguir mayusculas o por el Documento_Normalizado
     * de {@code q}. El segundo criterio solo se aplica cuando el documento
     * normalizado no queda vacio. Vale para {@code Empleado} y
     * {@code Proveedor}, que comparten los nombres de esas propiedades.
     */
    public static <T> Specification<T> nombreODocumentoContiene(String q) {
        String patronNombre = NormalizadorTexto.patronContiene(q);
        if (patronNombre == null) {
            return Specification.unrestricted();
        }

        String patronDocumento = NormalizadorTexto.patronContiene(
                NormalizadorTexto.documento(q));
        String nombre = patronNombre.toLowerCase(Locale.ROOT);

        return (root, query, cb) -> {
            Predicate porNombre = cb.like(
                    cb.lower(root.<String>get("nombre")), nombre, ESCAPE);

            if (patronDocumento == null) {
                return porNombre;
            }

            Predicate porDocumento = cb.like(
                    root.<String>get("numeroDocumento"), patronDocumento, ESCAPE);

            return cb.or(porNombre, porDocumento);
        };
    }

    /**
     * Filtra por estado activo. Vale para {@code Empleado} y {@code Proveedor}.
     */
    public static <T> Specification<T> activoIgual(Boolean activo) {
        if (activo == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.equal(root.get("activo"), activo);
    }

    public static Specification<MovimientoFinanciero> tipoIgual(
            TipoMovimiento tipo) {

        if (tipo == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.equal(root.get("tipo"), tipo);
    }

    public static Specification<MovimientoFinanciero> estadoIgual(
            EstadoMovimiento estado) {

        if (estado == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.equal(root.get("estado"), estado);
    }

    /**
     * Filtra por la columna {@code cliente_id}, sin comprobar que el cliente
     * exista y sin asociacion JPA (DP-ARQ-01).
     */
    public static Specification<MovimientoFinanciero> clienteIgual(
            UUID clienteId) {

        if (clienteId == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.equal(root.get("clienteId"), clienteId);
    }

    /**
     * Filtra por la FK del proveedor sin hacer un join explicito.
     */
    public static Specification<MovimientoFinanciero> proveedorIgual(
            UUID proveedorId) {

        if (proveedorId == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.equal(
                root.get("proveedor").get("id"), proveedorId);
    }

    public static Specification<MovimientoFinanciero> fechaDesde(
            LocalDate desde) {

        if (desde == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.greaterThanOrEqualTo(
                root.<LocalDate>get("fecha"), desde);
    }

    public static Specification<MovimientoFinanciero> fechaHasta(
            LocalDate hasta) {

        if (hasta == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.lessThanOrEqualTo(
                root.<LocalDate>get("fecha"), hasta);
    }

    /**
     * Busca por concepto sin distinguir mayusculas o por la
     * Referencia_Normalizada de {@code q}. Una referencia nula no coincide con
     * un patron.
     */
    public static Specification<MovimientoFinanciero> conceptoOReferenciaContiene(
            String q) {

        String patronConcepto = NormalizadorTexto.patronContiene(q);
        if (patronConcepto == null) {
            return Specification.unrestricted();
        }

        String patronReferencia = NormalizadorTexto.patronContiene(
                NormalizadorTexto.codigo(q));
        String concepto = patronConcepto.toLowerCase(Locale.ROOT);

        return (root, query, cb) -> {
            Predicate porConcepto = cb.like(
                    cb.lower(root.<String>get("concepto")), concepto, ESCAPE);

            if (patronReferencia == null) {
                return porConcepto;
            }

            Predicate porReferencia = cb.like(
                    root.<String>get("referencia"), patronReferencia, ESCAPE);

            return cb.or(porConcepto, porReferencia);
        };
    }
}
