package com.arise.corp.integration;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base comun de las pruebas de integracion contra PostgreSQL efimero
 * (DP-TEC-04, Requirement 24).
 *
 * <p>Todas las IT comparten esta configuracion, de modo que la cache de
 * contextos de Spring reutiliza un unico contenedor para toda la suite. El
 * perfil {@code it} activa {@code ddl-auto=validate}, asi que un desajuste
 * entre las entidades y las migraciones aplicadas hace fallar el arranque.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(PostgresContenedorConfig.class)
public abstract class PostgresIntegracionBase {

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void vaciarTablas() {
        jdbc.execute("truncate table app.contratos, app.clientes, app.cuentas_corporativas,"
                + " app.perfiles_usuario, auth.users cascade");
    }

    /**
     * Inserta una identidad ficticia en {@code auth.users} para poder crear un
     * perfil, que la referencia por clave foranea.
     *
     * @return el identificador insertado
     */
    protected UUID crearUsuarioAuth() {
        UUID usuarioId = UUID.randomUUID();
        jdbc.update("insert into auth.users (id) values (?)", usuarioId);
        return usuarioId;
    }
}
