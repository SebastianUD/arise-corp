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
import jakarta.persistence.UniqueConstraint;

/**
 * Registro inmutable del historial de estados de un paquete (SGA-04).
 *
 * <p>{@code @Immutable} hace que Hibernate no emita {@code UPDATE} sobre el
 * historial. La {@code secuencia} ordena los registros dentro del paquete sin
 * depender del reloj (D11). El dron solo se registra en la transicion a
 * {@code ASIGNADO}.
 */
@Entity
@Immutable
@Table(
        name = "historial_estados_paquete",
        uniqueConstraints = @UniqueConstraint(
                name = "historial_estados_paquete_secuencia_uk",
                columnNames = {"paquete_id", "secuencia"}))
public class HistorialEstadoPaquete {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paquete_id", nullable = false, updatable = false)
    private Paquete paquete;

    @Column(name = "secuencia", nullable = false, updatable = false)
    private short secuencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", updatable = false)
    private EstadoPaquete estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, updatable = false)
    private EstadoPaquete estadoNuevo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dron_id", updatable = false)
    private Dron dron;

    @Column(name = "observacion", updatable = false)
    private String observacion;

    @Column(name = "registrado_en", nullable = false, updatable = false)
    private Instant registradoEn;

    @Column(name = "registrado_por", nullable = false, updatable = false)
    private UUID registradoPor;

    protected HistorialEstadoPaquete() {
        // Requerido por JPA.
    }

    private HistorialEstadoPaquete(
            Paquete paquete,
            short secuencia,
            EstadoPaquete estadoAnterior,
            EstadoPaquete estadoNuevo,
            Dron dron,
            String observacion,
            Instant registradoEn,
            UUID registradoPor) {

        this.paquete = paquete;
        this.secuencia = secuencia;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.dron = dron;
        this.observacion = observacion;
        this.registradoEn = registradoEn;
        this.registradoPor = registradoPor;
    }

    /**
     * Primer registro del historial: secuencia 1, sin estado anterior y en
     * {@code REGISTRADO}. La observacion del alta se guarda aqui y no en el
     * paquete (interpretacion 10 de Requirement 7.1).
     */
    public static HistorialEstadoPaquete alta(
            Paquete paquete,
            String observacion,
            UUID actor,
            Instant ahora) {

        return new HistorialEstadoPaquete(
                paquete,
                (short) 1,
                null,
                EstadoPaquete.REGISTRADO,
                null,
                observacion,
                ahora,
                actor);
    }

    /**
     * Registro de una transicion aplicada. Solo la transicion a
     * {@code ASIGNADO} lleva dron.
     */
    public static HistorialEstadoPaquete transicion(
            Paquete paquete,
            int secuencia,
            EstadoPaquete anterior,
            EstadoPaquete nuevo,
            Dron dron,
            String observacion,
            UUID actor,
            Instant ahora) {

        return new HistorialEstadoPaquete(
                paquete,
                (short) secuencia,
                anterior,
                nuevo,
                dron,
                observacion,
                ahora,
                actor);
    }

    public UUID getId() {
        return id;
    }

    public Paquete getPaquete() {
        return paquete;
    }

    public short getSecuencia() {
        return secuencia;
    }

    public EstadoPaquete getEstadoAnterior() {
        return estadoAnterior;
    }

    public EstadoPaquete getEstadoNuevo() {
        return estadoNuevo;
    }

    public Dron getDron() {
        return dron;
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
