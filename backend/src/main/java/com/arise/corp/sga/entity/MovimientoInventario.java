package com.arise.corp.sga.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Movimiento de inventario inmutable (SGA-03).
 *
 * <p>{@code @Immutable} hace que Hibernate no emita {@code UPDATE}: un error se
 * corrige con un movimiento nuevo (Requirement 15.13).
 */
@Entity
@Immutable
@Table(name = "movimientos_inventario")
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "articulo_id", nullable = false, updatable = false)
    private ArticuloInventario articulo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false)
    private TipoMovimientoInventario tipo;

    @Column(name = "cantidad", nullable = false, updatable = false)
    private int cantidad;

    @Column(name = "existencia_anterior", nullable = false, updatable = false)
    private long existenciaAnterior;

    @Column(name = "existencia_resultante", nullable = false, updatable = false)
    private long existenciaResultante;

    @Column(name = "observacion", updatable = false)
    private String observacion;

    @Column(name = "registrado_en", nullable = false, updatable = false)
    private Instant registradoEn;

    @Column(name = "registrado_por", nullable = false, updatable = false)
    private UUID registradoPor;

    protected MovimientoInventario() {
        // Requerido por JPA.
    }

    private MovimientoInventario(
            ArticuloInventario articulo,
            TipoMovimientoInventario tipo,
            int cantidad,
            long existenciaAnterior,
            long existenciaResultante,
            String observacion,
            Instant registradoEn,
            UUID registradoPor) {

        this.articulo = articulo;
        this.tipo = tipo;
        this.cantidad = cantidad;
        this.existenciaAnterior = existenciaAnterior;
        this.existenciaResultante = existenciaResultante;
        this.observacion = observacion;
        this.registradoEn = registradoEn;
        this.registradoPor = registradoPor;
    }

    /**
     * Registra un movimiento ya aplicado sobre la existencia del articulo
     * (Requirement 15.1).
     */
    public static MovimientoInventario registrar(
            ArticuloInventario articulo,
            TipoMovimientoInventario tipo,
            int cantidad,
            long existenciaAnterior,
            long existenciaResultante,
            String observacion,
            UUID actor,
            Instant ahora) {

        return new MovimientoInventario(
                articulo,
                tipo,
                cantidad,
                existenciaAnterior,
                existenciaResultante,
                observacion,
                ahora,
                actor);
    }

    public UUID getId() {
        return id;
    }

    public ArticuloInventario getArticulo() {
        return articulo;
    }

    public TipoMovimientoInventario getTipo() {
        return tipo;
    }

    public int getCantidad() {
        return cantidad;
    }

    public long getExistenciaAnterior() {
        return existenciaAnterior;
    }

    public long getExistenciaResultante() {
        return existenciaResultante;
    }

    public String getObservacion() {
        return observacion;
    }

    public Instant getRegistradoEn() {
        return registradoEn;
    }

    public UUID getRegistradoPor() {
        return registradoPor;
    }
}
