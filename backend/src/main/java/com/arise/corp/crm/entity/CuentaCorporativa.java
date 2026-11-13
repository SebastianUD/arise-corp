package com.arise.corp.crm.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cuenta corporativa identificada por NIT que agrupa clientes (CRM-02).
 *
 * <p>La tabla vive en el esquema privado {@code app} configurado por Hibernate.
 * La entidad no expone setters: el estado se cambia con metodos de dominio que
 * reciben el actor y el instante, para conservar la auditoria (DP-SEG-06).
 */
@Entity
@Table(name = "cuentas_corporativas")
public class CuentaCorporativa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nit", nullable = false, unique = true)
    private String nit;

    @Column(name = "razon_social", nullable = false)
    private String razonSocial;

    @Column(name = "nombre_contacto")
    private String nombreContacto;

    @Column(name = "correo_contacto")
    private String correoContacto;

    @Column(name = "telefono_contacto")
    private String telefonoContacto;

    @Column(name = "activa", nullable = false)
    private boolean activa;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "actualizado_por", nullable = false)
    private UUID actualizadoPor;

    protected CuentaCorporativa() {
        // Requerido por JPA.
    }

    private CuentaCorporativa(
            String nit,
            String razonSocial,
            String nombreContacto,
            String correoContacto,
            String telefonoContacto,
            boolean activa,
            Instant creadoEn,
            Instant actualizadoEn,
            UUID actualizadoPor) {

        this.nit = nit;
        this.razonSocial = razonSocial;
        this.nombreContacto = nombreContacto;
        this.correoContacto = correoContacto;
        this.telefonoContacto = telefonoContacto;
        this.activa = activa;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
        this.actualizadoPor = actualizadoPor;
    }

    /**
     * Crea una cuenta corporativa activa con las marcas temporales y el actor
     * indicados.
     */
    public static CuentaCorporativa crear(
            String nit,
            String razonSocial,
            String nombreContacto,
            String correoContacto,
            String telefonoContacto,
            UUID actor,
            Instant ahora) {

        return new CuentaCorporativa(
                nit,
                razonSocial,
                nombreContacto,
                correoContacto,
                telefonoContacto,
                true,
                ahora,
                ahora,
                actor);
    }

    /**
     * Reemplaza los datos de la cuenta conservando su estado y su fecha de
     * creacion.
     */
    public void actualizarDatos(
            String nit,
            String razonSocial,
            String nombreContacto,
            String correoContacto,
            String telefonoContacto,
            UUID actor,
            Instant ahora) {

        this.nit = nit;
        this.razonSocial = razonSocial;
        this.nombreContacto = nombreContacto;
        this.correoContacto = correoContacto;
        this.telefonoContacto = telefonoContacto;
        this.actualizadoEn = ahora;
        this.actualizadoPor = actor;
    }

    /**
     * Activa o desactiva la cuenta sin tocar sus clientes.
     *
     * @return {@code false} si el valor no cambia; en ese caso la auditoria no
     *         se modifica
     */
    public boolean cambiarEstado(boolean activa, UUID actor, Instant ahora) {
        if (this.activa == activa) {
            return false;
        }
        this.activa = activa;
        this.actualizadoEn = ahora;
        this.actualizadoPor = actor;
        return true;
    }

    public UUID getId() {
        return id;
    }

    public String getNit() {
        return nit;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public String getNombreContacto() {
        return nombreContacto;
    }

    public String getCorreoContacto() {
        return correoContacto;
    }

    public String getTelefonoContacto() {
        return telefonoContacto;
    }

    public boolean isActiva() {
        return activa;
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
