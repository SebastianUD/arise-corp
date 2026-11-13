package com.arise.corp.crm.entity;

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
 * Cliente de Arise Corp. identificado por tipo y numero de documento (CRM-01).
 *
 * <p>Un cliente se desactiva, nunca se elimina. La cuenta corporativa es
 * opcional y la relacion se carga de forma perezosa (D1). No hay colecciones
 * inversas ni asociaciones hacia otros modulos (DP-ARQ-01).
 */
@Entity
@Table(
        name = "clientes",
        uniqueConstraints = @UniqueConstraint(
                name = "clientes_documento_uk",
                columnNames = {"tipo_documento", "numero_documento"}))
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false)
    private TipoDocumento tipoDocumento;

    @Column(name = "numero_documento", nullable = false)
    private String numeroDocumento;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "correo", nullable = false)
    private String correo;

    @Column(name = "telefono", nullable = false)
    private String telefono;

    @Column(name = "direccion")
    private String direccion;

    @Column(name = "ciudad")
    private String ciudad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_corporativa_id")
    private CuentaCorporativa cuentaCorporativa;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "actualizado_por", nullable = false)
    private UUID actualizadoPor;

    protected Cliente() {
        // Requerido por JPA.
    }

    private Cliente(
            TipoDocumento tipoDocumento,
            String numeroDocumento,
            String nombre,
            String correo,
            String telefono,
            String direccion,
            String ciudad,
            CuentaCorporativa cuentaCorporativa,
            boolean activo,
            Instant creadoEn,
            Instant actualizadoEn,
            UUID actualizadoPor) {

        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.nombre = nombre;
        this.correo = correo;
        this.telefono = telefono;
        this.direccion = direccion;
        this.ciudad = ciudad;
        this.cuentaCorporativa = cuentaCorporativa;
        this.activo = activo;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
        this.actualizadoPor = actualizadoPor;
    }

    /**
     * Crea un cliente activo con las marcas temporales y el actor indicados.
     */
    public static Cliente crear(
            TipoDocumento tipoDocumento,
            String numeroDocumento,
            String nombre,
            String correo,
            String telefono,
            String direccion,
            String ciudad,
            CuentaCorporativa cuentaCorporativa,
            UUID actor,
            Instant ahora) {

        return new Cliente(
                tipoDocumento,
                numeroDocumento,
                nombre,
                correo,
                telefono,
                direccion,
                ciudad,
                cuentaCorporativa,
                true,
                ahora,
                ahora,
                actor);
    }

    /**
     * Reemplaza los datos del cliente conservando su estado y su fecha de
     * creacion. La cuenta destino puede ser {@code null} para quitar la
     * asociacion.
     */
    public void actualizarDatos(
            TipoDocumento tipoDocumento,
            String numeroDocumento,
            String nombre,
            String correo,
            String telefono,
            String direccion,
            String ciudad,
            CuentaCorporativa cuentaCorporativa,
            UUID actor,
            Instant ahora) {

        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.nombre = nombre;
        this.correo = correo;
        this.telefono = telefono;
        this.direccion = direccion;
        this.ciudad = ciudad;
        this.cuentaCorporativa = cuentaCorporativa;
        this.actualizadoEn = ahora;
        this.actualizadoPor = actor;
    }

    /**
     * Activa o desactiva el cliente sin consultar ni modificar sus contratos.
     *
     * @return {@code false} si el valor no cambia; en ese caso la auditoria no
     *         se modifica
     */
    public boolean cambiarEstado(boolean activo, UUID actor, Instant ahora) {
        if (this.activo == activo) {
            return false;
        }
        this.activo = activo;
        this.actualizadoEn = ahora;
        this.actualizadoPor = actor;
        return true;
    }

    public UUID getId() {
        return id;
    }

    public TipoDocumento getTipoDocumento() {
        return tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getCiudad() {
        return ciudad;
    }

    public CuentaCorporativa getCuentaCorporativa() {
        return cuentaCorporativa;
    }

    public boolean isActivo() {
        return activo;
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
