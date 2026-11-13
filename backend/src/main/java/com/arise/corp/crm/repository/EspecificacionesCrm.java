package com.arise.corp.crm.repository;

import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.Contrato;
import com.arise.corp.crm.entity.CuentaCorporativa;
import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;

import jakarta.persistence.criteria.Predicate;

/**
 * Filtros y busquedas del CRM con la API de criterios y solo JPQL portable
 * (Property 15).
 *
 * <p>Los comodines {@code %}, {@code _} y {@code \} se escapan antes de
 * construir el {@code LIKE ... ESCAPE '\'}. Un filtro ausente devuelve una
 * especificacion sin restricciones, de modo que se pueda combinar con
 * {@link Specification#allOf(java.lang.Iterable)}.
 */
public final class EspecificacionesCrm {

    private static final char ESCAPE = '\\';

    private EspecificacionesCrm() {
    }

    /**
     * Busca por nombre sin distinguir mayusculas o por el Documento_Normalizado
     * de {@code q}. El segundo criterio solo se aplica cuando el documento
     * normalizado no queda vacio.
     */
    public static Specification<Cliente> nombreODocumentoContiene(String q) {
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

    public static Specification<Cliente> activoIgual(Boolean activo) {
        if (activo == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.equal(root.get("activo"), activo);
    }

    public static Specification<Cliente> cuentaIgual(UUID cuentaCorporativaId) {
        if (cuentaCorporativaId == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.equal(
                root.get("cuentaCorporativa").get("id"), cuentaCorporativaId);
    }

    /**
     * Busca por razon social sin distinguir mayusculas o por el NIT
     * normalizado de {@code q}.
     */
    public static Specification<CuentaCorporativa> razonSocialONitContiene(String q) {
        String patronRazonSocial = NormalizadorTexto.patronContiene(q);
        if (patronRazonSocial == null) {
            return Specification.unrestricted();
        }

        String patronNit = NormalizadorTexto.patronContiene(
                NormalizadorTexto.documento(q));
        String razonSocial = patronRazonSocial.toLowerCase(Locale.ROOT);

        return (root, query, cb) -> {
            Predicate porRazonSocial = cb.like(
                    cb.lower(root.<String>get("razonSocial")), razonSocial, ESCAPE);

            if (patronNit == null) {
                return porRazonSocial;
            }

            Predicate porNit = cb.like(
                    root.<String>get("nit"), patronNit, ESCAPE);

            return cb.or(porRazonSocial, porNit);
        };
    }

    public static Specification<CuentaCorporativa> activaIgual(Boolean activa) {
        if (activa == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.equal(root.get("activa"), activa);
    }

    public static Specification<Contrato> clienteIgual(UUID clienteId) {
        if (clienteId == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.equal(
                root.get("cliente").get("id"), clienteId);
    }

    public static Specification<Contrato> estadoIgual(EstadoContrato estado) {
        if (estado == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) -> cb.equal(root.get("estado"), estado);
    }
}
