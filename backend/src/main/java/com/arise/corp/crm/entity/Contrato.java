package com.arise.corp.crm.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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
 * Contrato comercial vinculado a un unico cliente (CRM-03).
 *
 * <p>Nace en {@link EstadoContrato#BORRADOR} y mantiene una auditoria propia del
 * estado, separada de la auditoria de los datos (D10). La cuenta corporativa no
 * se almacena aqui: se obtiene a traves del cliente (Requirement 2.6).
 */
@Entity
@Table(name = "contratos")
public class Contrato {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false, updatable = false)
    private Cliente cliente;

    @Column(name = "codigo", nullable = false, unique = true)
    private String codigo;

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "valor", nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoContrato estado;

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

    protected Contrato() {
        // Requerido por JPA.
    }

    private Contrato(
            Cliente cliente,
            String codigo,
            String descripcion,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            BigDecimal valor,
            EstadoContrato estado,
            Instant estadoActualizadoEn,
            UUID estadoActualizadoPor,
            Instant creadoEn,
            Instant actualizadoEn,
            UUID actualizadoPor) {

        this.cliente = cliente;
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.valor = valor;
        this.estado = estado;
        this.estadoActualizadoEn = estadoActualizadoEn;
        this.estadoActualizadoPor = estadoActualizadoPor;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
        this.actualizadoPor = actualizadoPor;
    }

    /**
     * Crea un contrato en {@link EstadoContrato#BORRADOR}; la auditoria del
     * estado coincide con la de los datos.
     */
    public static Contrato crear(
            Cliente cliente,
            String codigo,
            String descripcion,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            BigDecimal valor,
            UUID actor,
            Instant ahora) {

        return new Contrato(
                cliente,
                codigo,
                descripcion,
                fechaInicio,
                fechaFin,
                valor,
                EstadoContrato.BORRADOR,
                ahora,
                actor,
                ahora,
                ahora,
                actor);
    }

    /**
     * Reemplaza los datos del contrato y su auditoria; no toca el estado ni la
     * auditoria del estado.
     */
    public void actualizarDatos(
            String codigo,
            String descripcion,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            BigDecimal valor,
            UUID actor,
            Instant ahora) {

        this.codigo = codigo;
        this.descripcion = descripcion;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.valor = valor;
        this.actualizadoEn = ahora;
        this.actualizadoPor = actor;
    }

    /**
     * Cambia el estado y solo su auditoria; los datos y su auditoria no se
     * modifican.
     */
    public void cambiarEstado(EstadoContrato destino, UUID actor, Instant ahora) {
        this.estado = destino;
        this.estadoActualizadoEn = ahora;
        this.estadoActualizadoPor = actor;
    }

    public UUID getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public EstadoContrato getEstado() {
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
