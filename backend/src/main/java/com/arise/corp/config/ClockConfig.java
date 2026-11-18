package com.arise.corp.config;

import java.time.Clock;

import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicacion (D10) y su registro en Bean Validation (D7).
 *
 * <p>Los servicios obtienen el instante UTC del bean {@link Clock}; las pruebas
 * unitarias construyen los servicios con {@link Clock#fixed}. Esto mantiene la
 * auditoria reproducible sin acoplar el codigo a {@code Instant.now()}. El
 * mismo reloj alimenta las restricciones que dependen de la fecha actual, como
 * {@code @FechaNoFutura}.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Entrega el reloj de la aplicacion a Bean Validation: Spring Boot aplica
     * los {@link ValidationConfigurationCustomizer} al
     * {@code LocalValidatorFactoryBean} por defecto, que es el que usan
     * {@code @Valid} en Spring MVC y la validacion de metodos.
     */
    @Bean
    public ValidationConfigurationCustomizer relojDeValidacion(Clock clock) {
        return configuracion -> configuracion.clockProvider(() -> clock);
    }
}
