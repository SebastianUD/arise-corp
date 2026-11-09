package com.arise.corp.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.arise.corp.shared.error.RestAccessDeniedHandler;
import com.arise.corp.shared.error.RestAuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final List<String> allowedOrigins;

    public SecurityConfig(
            @Value("${cors.allowed-origins}") String allowedOrigins) {

        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            PerfilJwtAuthenticationConverter perfilAuthenticationConverter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler)
            throws Exception {

        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())

                // 401 y 403 usan el mismo formato Problem Details que la API
                // (DP-API-02); el entry point conserva WWW-Authenticate.
                .exceptionHandling(excepciones -> excepciones
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/admin/**")
                                .hasRole("ADMINISTRADOR")

                        // Lecturas cruzadas minimas de ERP-05 y SGA-05:
                        // Finanzas y Operaciones solo consultan clientes del
                        // CRM; Operaciones tambien consulta contratos.
                        // Cualquier escritura en CRM sigue siendo del rol
                        // COMERCIAL o Administrador.
                        .requestMatchers(HttpMethod.GET,
                                "/api/crm/clientes",
                                "/api/crm/clientes/*")
                                .hasAnyRole("ADMINISTRADOR", "COMERCIAL",
                                        "FINANZAS_RRHH", "OPERACIONES")
                        .requestMatchers(HttpMethod.GET,
                                "/api/crm/contratos",
                                "/api/crm/contratos/*")
                                .hasAnyRole("ADMINISTRADOR", "COMERCIAL",
                                        "OPERACIONES")

                        .requestMatchers("/api/crm/**")
                                .hasAnyRole("ADMINISTRADOR", "COMERCIAL")
                        .requestMatchers("/api/erp/**")
                                .hasAnyRole("ADMINISTRADOR", "FINANZAS_RRHH")
                        .requestMatchers("/api/sga/**")
                                .hasAnyRole("ADMINISTRADOR", "OPERACIONES")

                        // Denegacion por defecto: toda ruta restante exige
                        // autenticacion y las rutas de modulo exigen su rol.
                        .anyRequest().authenticated())

                .oauth2ResourceServer(oauth2 -> oauth2
                        // El filtro Bearer responde los tokens invalidos con
                        // su propio entry point; se reemplaza para conservar
                        // el formato Problem Details.
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                perfilAuthenticationConverter)))

                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        // Origenes enumerados y configurables por CORS_ALLOWED_ORIGINS
        // (DP-SEG-04). Nunca se usan comodines.
        configuration.setAllowedOrigins(allowedOrigins);

        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT",
                        "PATCH", "DELETE", "OPTIONS"));

        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type"));

        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
