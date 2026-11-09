package com.arise.corp.login.repository;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consulta la existencia de una identidad en Supabase Auth.
 *
 * <p>Los perfiles solo pueden crearse para UUID existentes en
 * {@code auth.users}; el backend usa la conexion de base de datos ya
 * configurada, sin claves de servicio de Supabase.
 */
@Repository
public class UsuarioAuthRepository {

    private static final String CONTAR_USUARIO =
            "select count(*) from auth.users where id = ?";

    private final JdbcTemplate jdbcTemplate;

    public UsuarioAuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existe(UUID usuarioId) {
        Long cantidad = jdbcTemplate.queryForObject(
                CONTAR_USUARIO, Long.class, usuarioId);

        return cantidad != null && cantidad > 0;
    }
}
