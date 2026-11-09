package com.arise.corp.login.entity;

/**
 * Rol empresarial de un usuario (DP-PRO-01).
 *
 * <p>Los valores coinciden con la restriccion {@code CHECK} de
 * {@code app.perfiles_usuario.rol}. No confundir con el claim tecnico
 * {@code role} de Supabase.
 */
public enum RolUsuario {

    ADMINISTRADOR,
    COMERCIAL,
    FINANZAS_RRHH,
    OPERACIONES
}
