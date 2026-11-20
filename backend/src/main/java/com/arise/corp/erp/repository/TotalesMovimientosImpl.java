package com.arise.corp.erp.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.arise.corp.erp.entity.MovimientoFinanciero;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Implementacion del fragmento del panel con Criteria API (D10).
 *
 * <p>Se usa Criteria API en lugar de JPQL con
 * {@code (:desde is null or ...)} para no enviar parametros nulos a
 * PostgreSQL, donde su tipo depende de la inferencia de Hibernate, y para que
 * la misma consulta funcione en H2 y en PostgreSQL. Cada grupo tiene al menos
 * un movimiento con {@code valor not null}, asi que {@code sum} nunca es nulo.
 */
public class TotalesMovimientosImpl implements TotalesMovimientos {

    private final EntityManager entityManager;

    public TotalesMovimientosImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<TotalMovimientos> totalizar(LocalDate desde, LocalDate hasta) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<TotalMovimientos> consulta =
                cb.createQuery(TotalMovimientos.class);
        Root<MovimientoFinanciero> movimiento =
                consulta.from(MovimientoFinanciero.class);
        List<Predicate> condiciones = new ArrayList<>();

        if (desde != null) {
            condiciones.add(cb.greaterThanOrEqualTo(
                    movimiento.get("fecha"), desde));
        }
        if (hasta != null) {
            condiciones.add(cb.lessThanOrEqualTo(
                    movimiento.get("fecha"), hasta));
        }

        consulta.select(cb.construct(
                        TotalMovimientos.class,
                        movimiento.get("tipo"),
                        movimiento.get("estado"),
                        cb.count(movimiento),
                        cb.sum(movimiento.<BigDecimal>get("valor"))))
                .groupBy(movimiento.get("tipo"), movimiento.get("estado"));

        if (!condiciones.isEmpty()) {
            consulta.where(condiciones.toArray(Predicate[]::new));
        }

        return entityManager.createQuery(consulta).getResultList();
    }
}
