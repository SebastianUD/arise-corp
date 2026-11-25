package com.arise.corp.sga.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Dron de entrega del SGA (SGA-01).
 *
 * <p>Un dron se da de baja con el estado {@code FUERA_DE_SERVICIO}, nunca se
 * elimina. No hay setters publicos: los datos se cambian con
 * {@link #actualizarDatos}, el estado manual con {@link #cambiarEstadoManual} y
 * el estado ligado al paquete con {@link #sincronizarConPaquete}, siempre con
 * el actor y el instante de la auditoria (D8, D14, DP-SEG-06).
 */
@Entity
@Table(
        name = "drones",
        uniqueConstraints = @UniqueConstraint(
                name = "drones_codigo_uk",
                columnNames = "codigo"))
public class Dron {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "codigo", nullable = false)
    private String codigo;

    @Column(name = "modelo", nullable = false)
    private String modelo;

    @Column(name = "observaciones")
    private String observaciones;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoDron estado;

    @Column(name = "estado_actualizado_en", nullable = false)
    private Instant estadoActualizadoEn;

    @Column(name = "estado_actualizado_por", nullable = false)
    private UUID estadoActualizadoPor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "actualizado_por", nullable = false)
    private UUID actualizadoPor;

    protected Dron() {
        // Requerido por JPA.
    }

    private Dron(
            String codigo,
            String modelo,
            String observaciones,
            EstadoDron estado,
            Instant estadoActualizadoEn,
            UUID estadoActualizadoPor,
            Instant creadoEn,
            Instant actualizadoEn,
            UUID actualizadoPor) {

        this.codigo = codigo;
        this.modelo = modelo;
        this.observaciones = observaciones;
        this.estado = estado;
        this.estadoActualizadoEn = estadoActualizadoEn;
        this.estadoActualizadoPor = estadoActualizadoPor;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
        this.actualizadoPor = actualizadoPor;
    }

    /**
     * Crea un dron en {@code DISPONIBLE} con la auditoria de datos y la de
     * estado iguales (Requirement 4.1).
     */
    public static Dron crear(
            String codigo,
            String modelo,
            String observaciones,
            UUID actor,
            Instant ahora) {

        return new Dron(
                codigo,
                modelo,
                observaciones,
                EstadoDron.DISPONIBLE,
                ahora,
                actor,
                ahora,
                ahora,
                actor);
    }

    /**
     * Reemplaza los datos y la auditoria de datos sin tocar el estado ni su
     * auditoria (D14).
     */
    public void actualizarDatos(
            String codigo,
            String modelo,
            String observaciones,
            UUID actor,
            Instant ahora) {

        this.codigo = codigo;
        this.modelo = modelo;
        this.observaciones = observaciones;
        this.actualizadoEn = ahora;
        this.actualizadoPor = actor;
    }

    /**
     * Cambio manual de estado; nunca fija ni abandona {@code EN_MISION} y el
     * mismo estado no es una transicion (Requirements 6.1, 6.2).
     */
    public void cambiarEstadoManual(
            EstadoDron destino,
            UUID actor,
            Instant ahora) {

        if (!estado.puedeCambiarManualmenteA(destino)) {
            throw new IllegalStateException(
                    "Transicion manual no permitida de " + estado + " a " + destino + ".");
        }

        this.estado = destino;
        this.estadoActualizadoEn = ahora;
        this.estadoActualizadoPor = actor;
    }

    /**
     * Cambio de estado ligado al paquete: solo {@code DISPONIBLE -> EN_MISION}
     * y {@code EN_MISION -> DISPONIBLE} (D8). Esta defensa solo protege el
     * modelo; el servicio comprueba antes las reglas de negocio.
     */
    public void sincronizarConPaquete(
            EstadoDron destino,
            UUID actor,
            Instant ahora) {

        boolean permitida = (estado == EstadoDron.DISPONIBLE && destino == EstadoDron.EN_MISION)
                || (estado == EstadoDron.EN_MISION && destino == EstadoDron.DISPONIBLE);

        if (!permitida) {
            throw new IllegalStateException(
                    "Sincronizacion no permitida de " + estado + " a " + destino + ".");
        }

        this.estado = destino;
        this.estadoActualizadoEn = ahora;
        this.estadoActualizadoPor = actor;
    }

    public UUID getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getModelo() {
        return modelo;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public EstadoDron getEstado() {
        return estado;
    }

    public Instant getEstadoActualizadoEn() {
        return estadoActualizadoEn;
    }

    public UUID getEstadoActualizadoPor() {
        return estadoActualizadoPor;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public UUID getActualizadoPor() {
        return actualizadoPor;
    }
}
