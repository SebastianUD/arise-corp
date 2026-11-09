package com.arise.corp.login.dto;

/**
 * Identidad devuelta por {@code GET /api/me}.
 *
 * <p>{@code id} es el {@code sub} del JWT, {@code email} es informativo y puede
 * ser nulo, y {@code role} es el rol empresarial del perfil activo, nunca el
 * claim tecnico de Supabase (D2).
 */
public record MeResponse(String id, String email, String role) {
}
