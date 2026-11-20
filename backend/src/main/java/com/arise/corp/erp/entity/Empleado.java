package com.arise.corp.erp.entity;

import java.time.Instant;
import java.time.LocalDate;
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
 * Empleado de Arise Corp. identificado por tipo y numero de documento
 * (ERP-01).
 *
 * <p>Un empleado se desactiva, nunca se elimina. No hay setters publicos: los
 * cambios se hacen con {@link #actualizarDatos} y {@link #cambiarEstado}, que
 * reciben el actor y el instante de la auditoria (DP-SEG-06).
 */
@Entity
@Table(
        name = "empleados",
        uniqueConstraints = @UniqueConstraint(
                name = "empleados_documento_uk",
                columnNames = {"tipo_documento", "numero_documento"}))
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false)
    private TipoDocumentoEmpleado tipoDocumento;

    @Column(name = "numero_documento", nullable = false)
    private String numeroDocumento;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "cargo", nullable = false)
    private String cargo;

    @Column(name = "area", nullable = false)
    private String area;

    @Column(name = "correo", nullable = false)
    private String correo;

    @Column(name = "telefono", nullable = false)
    private String telefono;

    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "actualizado_por", nullable = false)
    private UUID actualizadoPor;

    protected Empleado() {
        // Requerido por JPA.
    }

    private Empleado(
            TipoDocumentoEmpleado tipoDocumento,
            String numeroDocumento,
            String nombre,
            String cargo,
            String area,
            String correo,
            String telefono,
            LocalDate fechaIngreso,
            boolean activo,
            Instant creadoEn,
            Instant actualizadoEn,
            UUID actualizadoPor) {

        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.nombre = nombre;
        this.cargo = cargo;
        this.area = area;
        this.correo = correo;
        this.telefono = telefono;
        this.fechaIngreso = fechaIngreso;
        this.activo = activo;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
        this.actualizadoPor = actualizadoPor;
    }

    /**
     * Crea un empleado activo con las marcas temporales y el actor indicados.
     */
    public static Empleado crear(
            TipoDocumentoEmpleado tipoDocumento,
            String numeroDocumento,
            String nombre,
            String cargo,
            String area,
            String correo,
            String telefono,
            LocalDate fechaIngreso,
            UUID actor,
            Instant ahora) {

        return new Empleado(
                tipoDocumento,
                numeroDocumento,
                nombre,
                cargo,
                area,
                correo,
                telefono,
                fechaIngreso,
                true,
                ahora,
                ahora,
                actor);
    }

    /**
     * Reemplaza los datos del empleado conservando su estado y su fecha de
     * creacion.
     */
    public void actualizarDatos(
            TipoDocumentoEmpleado tipoDocumento,
            String numeroDocumento,
            String nombre,
            String cargo,
            String area,
            String correo,
            String telefono,
            LocalDate fechaIngreso,
            UUID actor,
            Instant ahora) {

        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.nombre = nombre;
        this.cargo = cargo;
        this.area = area;
        this.correo = correo;
        this.telefono = telefono;
        this.fechaIngreso = fechaIngreso;
        this.actualizadoEn = ahora;
        this.actualizadoPor = actor;
    }

    /**
     * Activa o desactiva el empleado.
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

    public TipoDocumentoEmpleado getTipoDocumento() {
        return tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCargo() {
        return cargo;
    }

    public String getArea() {
        return area;
    }

    public String getCorreo() {
        return correo;
    }

    public String getTelefono() {
        return telefono;
    }

    public LocalDate getFechaIngreso() {
        return fechaIngreso;
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
