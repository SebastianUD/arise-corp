package com.arise.corp.login.service;

import com.arise.corp.login.entity.PerfilUsuario;

/**
 * Resultado de crear o actualizar un perfil.
 *
 * @param perfil perfil persistido
 * @param creado {@code true} si se creo, {@code false} si se actualizo
 */
public record ResultadoPerfil(PerfilUsuario perfil, boolean creado) {
}
