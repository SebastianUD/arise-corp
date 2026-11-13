package com.arise.corp.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL efimero de las pruebas de integracion (DP-TEC-04, D12).
 *
 * <p>El contenedor se declara como {@code @Bean} con {@code @ServiceConnection}:
 * Spring crea los detalles de conexion a partir del contenedor y liga su ciclo
 * de vida al contexto, asi que no se usan {@code @Testcontainers} ni
 * {@code @Container} y no hace falta {@code testcontainers-junit-jupiter}.
 *
 * <p>El contenedor arranca y se migra dentro del metodo de fabricacion, antes
 * de que Hibernate valide el esquema con {@code ddl-auto=validate}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContenedorConfig {

    /**
     * Version mayor alineada con {@code arise-corp-dev} (PostgreSQL 17).
     */
    static final String IMAGEN_POSTGRES = "postgres:17-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        PostgreSQLContainer<?> contenedor = new PostgreSQLContainer<>(DockerImageName.parse(IMAGEN_POSTGRES));
        contenedor.start();
        MigracionesSupabase.aplicar(contenedor);
        return contenedor;
    }
}
