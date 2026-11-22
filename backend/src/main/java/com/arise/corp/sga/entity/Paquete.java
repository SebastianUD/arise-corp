package com.arise.corp.sga.entity;

import java.time.Instant;
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
import jakarta.persistence.UniqueConstraint;

/**
 * Paquete del SGA con historial de estados (SGA-02, SGA-04, SGA-05).
 *
 * <p>El cliente y el contrato se guardan como {@code UUID} sin asociacion JPA:
 * el CRM es el dueno de esos datos y la relacion vive solo en PostgreSQL
 * (DP-ARQ-01). El dron si es una asociacion dentro del SGA y se carga de forma
 * perezosa. {@link #aplicarTransicion} concentra las invariantes de D8 y
 * sincroniza el dron en la misma transaccion.
 */
@Entity
@Table(
        name = "paquetes",
        uniqueConstraints = @UniqueConstraint(
                name = "paquetes_codigo_uk",
                columnNames = "codigo"))
public class Paquete {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "codigo", nullable = false, updatable = false)
    private String codigo;

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    @Column(name = "direccion_entrega", nullable = false)
    private String direccionEntrega;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoPaquete estado;

    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    @Column(name = "contrato_id")
    private UUID contratoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dron_id")
    private Dron dron;

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

    protected Paquete() {
        // Requerido por JPA.
    }

    private Paquete(
            String codigo,
            String descripcion,
            String direccionEntrega,
            EstadoPaquete estado,
            UUID clienteId,
            UUID contratoId,
            Dron dron,
            Instant estadoActualizadoEn,
            UUID estadoActualizadoPor,
            Instant creadoEn,
            Instant actualizadoEn,
            UUID actualizadoPor) {

        this.codigo = codigo;
        this.descripcion = descripcion;
        this.direccionEntrega = direccionEntrega;
        this.estado = estado;
        this.clienteId = clienteId;
        this.contratoId = contratoId;
        this.dron = dron;
        this.estadoActualizadoEn = estadoActualizadoEn;
        this.estadoActualizadoPor = estadoActualizadoPor;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
        this.actualizadoPor = actualizadoPor;
    }

    /**
     * Crea un paquete en {@code REGISTRADO} sin dron, con la auditoria de datos
     * y la de estado iguales (Requirement 7.1).
     */
    public static Paquete crear(
            String codigo,
            String descripcion,
            String direccionEntrega,
            UUID clienteId,
            UUID contratoId,
            UUID actor,
            Instant ahora) {

        return new Paquete(
                codigo,
                descripcion,
                direccionEntrega,
                EstadoPaquete.REGISTRADO,
                clienteId,
                contratoId,
                null,
                ahora,
                actor,
                ahora,
                ahora,
                actor);
    }

    /**
     * Reemplaza los datos editables y la auditoria de datos sin tocar el
     * estado, su auditoria, el dron ni el historial (D14). Solo un paquete
     * editable admite {@code PUT} (Requirement 9.3).
     */
    public void actualizarDatos(
            String descripcion,
            String direccionEntrega,
            UUID contratoId,
            UUID actor,
            Instant ahora) {

        if (!estado.esEditable()) {
            throw new IllegalStateException(
                    "Solo se pueden editar paquetes en estado REGISTRADO o ASIGNADO.");
        }

        this.descripcion = descripcion;
        this.direccionEntrega = direccionEntrega;
        this.contratoId = contratoId;
        this.actualizadoEn = ahora;
        this.actualizadoPor = actor;
    }

    /**
     * Aplica una transicion permitida y sincroniza el dron en el mismo paso y
     * con el mismo instante y actor (D8).
     *
     * <p>Los cinco pasos del diseno: comprobar la transicion; exigir y asignar
     * el dron al pasar a {@code ASIGNADO}; exigir el dron asignado en otro
     * destino; actualizar solo el estado y su auditoria; y sincronizar el dron
     * cuando la transicion lo cambia.
     */
    public void aplicarTransicion(
            EstadoPaquete destino,
            Dron dron,
            UUID actor,
            Instant ahora) {

        EstadoPaquete origen = this.estado;

        if (!origen.puedeTransicionarA(destino)) {
            throw new IllegalStateException(
                    "Transicion no permitida de " + origen + " a " + destino + ".");
        }

        if (destino == EstadoPaquete.ASIGNADO) {
            if (dron == null) {
                throw new IllegalArgumentException(
                        "La transicion a ASIGNADO exige un dron.");
            }
            this.dron = dron;
        } else if (dron != null && !esElDronAsignado(dron)) {
            throw new IllegalArgumentException(
                    "El dron indicado no es el asignado al paquete.");
        }

        this.estado = destino;
        this.estadoActualizadoEn = ahora;
        this.estadoActualizadoPor = actor;

        Dron asignado = this.dron;
        origen.efectoSobreDron(destino)
                .ifPresent(efecto -> asignado.sincronizarConPaquete(efecto, actor, ahora));
    }

    private boolean esElDronAsignado(Dron otro) {
        if (this.dron == null) {
            return false;
        }
        if (this.dron == otro) {
            return true;
        }
        return this.dron.getId() != null && this.dron.getId().equals(otro.getId());
    }

    public UUID getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getDireccionEntrega() {
        return direccionEntrega;
    }

    public EstadoPaquete getEstado() {
        return estado;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public UUID getContratoId() {
        return contratoId;
    }

    public Dron getDron() {
        return dron;
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
