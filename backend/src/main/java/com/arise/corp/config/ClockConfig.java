package com.arise.corp.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicacion (D10).
 *
 * <p>Los servicios obtienen el instante UTC del bean {@link Clock}; las pruebas
 * unitarias construyen los servicios con {@link Clock#fixed}. Esto mantiene la
 * auditoria reproducible sin acoplar el codigo a {@code Instant.now()}.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
