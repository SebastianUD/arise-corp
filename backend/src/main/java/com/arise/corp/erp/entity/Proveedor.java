package com.arise.corp.erp.entity;

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
 * Proveedor de Arise Corp. identificado por tipo y numero de documento
 * (ERP-02).
 *
 * <p>Un proveedor se desactiva, nunca se elimina. Desactivarlo no consulta ni
 * modifica sus movimientos financieros (Requirement 12.4). Los datos de
 * contacto son opcionales y se guardan como {@code null} cuando no se
 * informan.
 */
@Entity
@Table(
        name = "proveedores",
        uniqueConstraints = @UniqueConstraint(
                name = "proveedores_documento_uk",
                columnNames = {"tipo_documento", "numero_documento"}))
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false)
    private TipoDocumentoProveedor tipoDocumento;

    @Column(name = "numero_documento", nullable = false)
    private String numeroDocumento;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "nombre_contacto")
    private String nombreContacto;

    @Column(name = "correo", nullable = false)
    private String correo;

    @Column(name = "telefono", nullable = false)
    private String telefono;

    @Column(name = "direccion")
    private String direccion;

    @Column(name = "ciudad")
    private String ciudad;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "actualizado_por", nullable = false)
    private UUID actualizadoPor;

    protected Proveedor() {
        // Requerido por JPA.
    }

    private Proveedor(
            TipoDocumentoProveedor tipoDocumento,
            String numeroDocumento,
            String nombre,
            String nombreContacto,
            String correo,
            String telefono,
            String direccion,
            String ciudad,
            boolean activo,
            Instant creadoEn,
            Instant actualizadoEn,
            UUID actualizadoPor) {

        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.nombre = nombre;
        this.nombreContacto = nombreContacto;
        this.correo = correo;
        this.telefono = telefono;
        this.direccion = direccion;
        this.ciudad = ciudad;
        this.activo = activo;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
        this.actualizadoPor = actualizadoPor;
    }

    /**
     * Crea un proveedor activo con las marcas temporales y el actor indicados.
     */
    public static Proveedor crear(
            TipoDocumentoProveedor tipoDocumento,
            String numeroDocumento,
            String nombre,
            String nombreContacto,
            String correo,
            String telefono,
            String direccion,
            String ciudad,
            UUID actor,
            Instant ahora) {

        return new Proveedor(
                tipoDocumento,
                numeroDocumento,
                nombre,
                nombreContacto,
                correo,
                telefono,
                direccion,
                ciudad,
                true,
                ahora,
                ahora,
                actor);
    }

    /**
     * Reemplaza los datos del proveedor conservando su estado y su fecha de
     * creacion. Un opcional omitido queda {@code null}.
     */
    public void actualizarDatos(
            TipoDocumentoProveedor tipoDocumento,
            String numeroDocumento,
            String nombre,
            String nombreContacto,
            String correo,
            String telefono,
            String direccion,
            String ciudad,
            UUID actor,
            Instant ahora) {

        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.nombre = nombre;
        this.nombreContacto = nombreContacto;
        this.correo = correo;
        this.telefono = telefono;
        this.direccion = direccion;
        this.ciudad = ciudad;
        this.actualizadoEn = ahora;
        this.actualizadoPor = actor;
    }

    /**
     * Activa o desactiva el proveedor sin consultar ni modificar sus
     * movimientos.
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

    public TipoDocumentoProveedor getTipoDocumento() {
        return tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public String getNombre() {
        return nombre;
    }

    public String getNombreContacto() {
        return nombreContacto;
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
