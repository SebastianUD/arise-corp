package com.arise.corp.erp.entity;

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
 * Ingreso o egreso financiero del ERP (ERP-03, ERP-05).
 *
 * <p>El cliente se guarda como {@code UUID clienteId} sin asociacion JPA: el
 * CRM es el dueno de esos datos y la relacion vive solo en PostgreSQL
 * (DP-ARQ-01). El proveedor si es una asociacion dentro del ERP y se carga de
 * forma perezosa. Los datos del movimiento y la auditoria de su estado se
 * actualizan por separado (D13).
 *
 * <p>Las defensas de invariantes comprueban la coherencia entre el tipo y la
 * relacion (D8) y la maquina de estados (D11). El servicio valida cada regla
 * antes de llamar a la entidad, de modo que estas excepciones solo protegen
 * las invariantes.
 */
@Entity
@Table(name = "movimientos_financieros")
public class MovimientoFinanciero {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoMovimiento tipo;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "concepto", nullable = false)
    private String concepto;

    @Column(name = "valor", nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "referencia")
    private String referencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoMovimiento estado;

    @Column(name = "cliente_id")
    private UUID clienteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

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

    protected MovimientoFinanciero() {
        // Requerido por JPA.
    }

    private MovimientoFinanciero(
            TipoMovimiento tipo,
            LocalDate fecha,
            String concepto,
            BigDecimal valor,
            String referencia,
            UUID clienteId,
            Proveedor proveedor,
            EstadoMovimiento estado,
            Instant estadoActualizadoEn,
            UUID estadoActualizadoPor,
            Instant creadoEn,
            Instant actualizadoEn,
            UUID actualizadoPor) {

        this.tipo = tipo;
        this.fecha = fecha;
        this.concepto = concepto;
        this.valor = valor;
        this.referencia = referencia;
        this.clienteId = clienteId;
        this.proveedor = proveedor;
        this.estado = estado;
        this.estadoActualizadoEn = estadoActualizadoEn;
        this.estadoActualizadoPor = estadoActualizadoPor;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
        this.actualizadoPor = actualizadoPor;
    }

    /**
     * Crea un movimiento en {@code PENDIENTE} con la auditoria de datos y la de
     * estado iguales.
     */
    public static MovimientoFinanciero crear(
            TipoMovimiento tipo,
            LocalDate fecha,
            String concepto,
            BigDecimal valor,
            String referencia,
            UUID clienteId,
            Proveedor proveedor,
            UUID actor,
            Instant ahora) {

        validarRelacion(tipo, clienteId, proveedor);

        return new MovimientoFinanciero(
                tipo,
                fecha,
                concepto,
                valor,
                referencia,
                clienteId,
                proveedor,
                EstadoMovimiento.PENDIENTE,
                ahora,
                actor,
                ahora,
                ahora,
                actor);
    }

    /**
     * Reemplaza los siete campos editables y la auditoria de datos sin tocar el
     * estado ni su auditoria (D13).
     */
    public void actualizarDatos(
            TipoMovimiento tipo,
            LocalDate fecha,
            String concepto,
            BigDecimal valor,
            String referencia,
            UUID clienteId,
            Proveedor proveedor,
            UUID actor,
            Instant ahora) {

        if (!estado.esEditable()) {
            throw new IllegalStateException(
                    "Solo se pueden editar movimientos en estado PENDIENTE.");
        }

        validarRelacion(tipo, clienteId, proveedor);

        this.tipo = tipo;
        this.fecha = fecha;
        this.concepto = concepto;
        this.valor = valor;
        this.referencia = referencia;
        this.clienteId = clienteId;
        this.proveedor = proveedor;
        this.actualizadoEn = ahora;
        this.actualizadoPor = actor;
    }

    /**
     * Cambia el estado y solo su auditoria (D13).
     */
    public void cambiarEstado(
            EstadoMovimiento destino,
            UUID actor,
            Instant ahora) {

        if (!estado.puedeTransicionarA(destino)) {
            throw new IllegalStateException(
                    "Transicion no permitida de " + estado + " a " + destino + ".");
        }

        this.estado = destino;
        this.estadoActualizadoEn = ahora;
        this.estadoActualizadoPor = actor;
    }

    private static void validarRelacion(
            TipoMovimiento tipo,
            UUID clienteId,
            Proveedor proveedor) {

        if (tipo == TipoMovimiento.EGRESO && clienteId != null) {
            throw new IllegalArgumentException("Un EGRESO no admite clienteId.");
        }
        if (tipo == TipoMovimiento.INGRESO && proveedor != null) {
            throw new IllegalArgumentException("Un INGRESO no admite proveedor.");
        }
    }

    public UUID getId() {
        return id;
    }

    public TipoMovimiento getTipo() {
        return tipo;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getConcepto() {
        return concepto;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getReferencia() {
        return referencia;
    }

    public EstadoMovimiento getEstado() {
        return estado;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public Proveedor getProveedor() {
        return proveedor;
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
