package com.arise.corp.login.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Perfil empresarial del usuario autenticado (DP-SEG-01).
 *
 * <p>El identificador es el {@code sub} UUID de Supabase Auth y la tabla vive
 * en el esquema privado {@code app} configurado por Hibernate. Un usuario tiene
 * un unico rol y un estado activo o inactivo.
 */
@Entity
@Table(name = "perfiles_usuario")
public class PerfilUsuario {

    @Id
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false)
    private RolUsuario rol;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "actualizado_por")
    private UUID actualizadoPor;

    protected PerfilUsuario() {
        // Requerido por JPA.
    }

    public PerfilUsuario(
            UUID usuarioId,
            RolUsuario rol,
            boolean activo,
            Instant creadoEn,
            Instant actualizadoEn,
            UUID actualizadoPor) {

        this.usuarioId = usuarioId;
        this.rol = rol;
        this.activo = activo;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
        this.actualizadoPor = actualizadoPor;
    }

    /**
     * Crea un perfil activo con las marcas temporales y el actor indicados.
     */
    public static PerfilUsuario crear(
            UUID usuarioId,
            RolUsuario rol,
            UUID actualizadoPor,
            Instant ahora) {

        return new PerfilUsuario(
                usuarioId, rol, true, ahora, ahora, actualizadoPor);
    }

    /**
     * Actualiza el rol conservando el estado y la fecha de creacion.
     */
    public void actualizarRol(
            RolUsuario rol,
            UUID actualizadoPor,
            Instant ahora) {

        this.rol = rol;
        this.actualizadoEn = ahora;
        this.actualizadoPor = actualizadoPor;
    }

    /**
     * Activa o desactiva el perfil y registra la auditoria del cambio.
     */
    public void actualizarEstado(
            boolean activo,
            UUID actualizadoPor,
            Instant ahora) {

        this.activo = activo;
        this.actualizadoEn = ahora;
        this.actualizadoPor = actualizadoPor;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public RolUsuario getRol() {
        return rol;
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
