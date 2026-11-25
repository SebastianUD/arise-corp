package com.arise.corp.sga.repository;

import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.arise.corp.sga.entity.ArticuloInventario;
import com.arise.corp.sga.entity.CategoriaArticulo;
import com.arise.corp.sga.entity.Dron;
import com.arise.corp.sga.entity.EstadoDron;
import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.entity.Paquete;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Filtros y busquedas del SGA con la API de criterios y solo JPQL portable.
 *
 * <p>Los comodines {@code %}, {@code _} y {@code \} se escapan antes de
 * construir el {@code LIKE ... ESCAPE '\'}. Un filtro ausente devuelve una
 * especificacion sin restricciones, de modo que se pueda combinar con
 * {@link Specification#allOf(java.lang.Iterable)}.
 */
public final class EspecificacionesSga {

    private static final char ESCAPE = '\\';

    private EspecificacionesSga() {
    }

    /**
     * Busca por el Codigo_Normalizado de {@code q} o por el texto indicado sin
     * distinguir mayusculas. Vale para {@code modelo}, {@code descripcion} y
     * {@code nombre}.
     */
    public static <T> Specification<T> codigoOTextoContiene(String q, String campoTexto) {
        String patronCodigo = NormalizadorTexto.patronContiene(NormalizadorTexto.codigo(q));
        if (patronCodigo == null) {
            return Specification.unrestricted();
        }

        String patronTexto = NormalizadorTexto.patronContiene(q).toLowerCase(Locale.ROOT);

        return (root, query, cb) -> {
            Predicate porCodigo = cb.like(
                    root.<String>get("codigo"), patronCodigo, ESCAPE);
            Predicate porTexto = cb.like(
                    cb.lower(root.<String>get(campoTexto)), patronTexto, ESCAPE);

            return cb.or(porCodigo, porTexto);
        };
    }

    public static Specification<Dron> estadoIgual(EstadoDron estado) {
        if (estado == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.equal(root.get("estado"), estado);
    }

    public static Specification<Paquete> estadoIgual(EstadoPaquete estado) {
        if (estado == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.equal(root.get("estado"), estado);
    }

    public static Specification<ArticuloInventario> categoriaIgual(
            CategoriaArticulo categoria) {

        if (categoria == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.equal(root.get("categoria"), categoria);
    }

    /**
     * Filtra por la columna {@code cliente_id}, sin comprobar que el cliente
     * exista y sin asociacion JPA (DP-ARQ-01).
     */
    public static Specification<Paquete> clienteIgual(UUID clienteId) {
        if (clienteId == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.equal(root.get("clienteId"), clienteId);
    }

    public static Specification<Paquete> contratoIgual(UUID contratoId) {
        if (contratoId == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.equal(root.get("contratoId"), contratoId);
    }

    /**
     * Filtra por la FK del dron sin hacer un join explicito.
     */
    public static Specification<Paquete> dronIgual(UUID dronId) {
        if (dronId == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.equal(root.get("dron").get("id"), dronId);
    }

    /**
     * Dron_Asignable: esta {@code DISPONIBLE} y no tiene un Paquete_En_Curso
     * (Requirement 5.4). Con {@code false} devuelve la negacion.
     */
    public static Specification<Dron> asignable(Boolean asignable) {
        if (asignable == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> {
            Predicate disponible = cb.equal(root.get("estado"), EstadoDron.DISPONIBLE);

            Subquery<Long> enCurso = query.subquery(Long.class);
            Root<Paquete> paquete = enCurso.from(Paquete.class);
            enCurso.select(cb.literal(1L)).where(
                    cb.equal(paquete.get("dron"), root),
                    paquete.get("estado").in(EstadoPaquete.EN_CURSO));

            Predicate sinPaqueteEnCurso = cb.not(cb.exists(enCurso));
            Predicate esAsignable = cb.and(disponible, sinPaqueteEnCurso);

            return asignable ? esAsignable : cb.not(esAsignable);
        };
    }

    /**
     * {@code existencia < stockMinimo} estricto; con {@code false} devuelve la
     * negacion (Requirement 14.4).
     */
    public static Specification<ArticuloInventario> bajoStockMinimo(Boolean bajoStockMinimo) {
        if (bajoStockMinimo == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> {
            Expression<Long> existencia = root.get("existencia");
            Expression<Long> minimo = root.get("stockMinimo").as(Long.class);

            Predicate bajo = cb.lt(existencia, minimo);

            return bajoStockMinimo ? bajo : cb.not(bajo);
        };
    }
}
