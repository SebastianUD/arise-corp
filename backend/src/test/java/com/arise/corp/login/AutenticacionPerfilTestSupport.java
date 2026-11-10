package com.arise.corp.login;

import java.time.Instant;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.arise.corp.config.PerfilJwtAuthenticationConverter;

/**
 * Simula un JWT valido y lo convierte con el mismo converter de produccion,
 * de modo que las pruebas usan el rol empresarial del perfil persistido.
 * Nunca usa tokens reales de Supabase.
 */
public final class AutenticacionPerfilTestSupport {

    private AutenticacionPerfilTestSupport() {
    }

    public static RequestPostProcessor como(
            PerfilJwtAuthenticationConverter converter,
            String sub,
            String email) {

        Instant ahora = Instant.now();

        Jwt jwt = Jwt.withTokenValue("token-simulado")
                .header("alg", "ES256")
                .subject(sub)
                .claim("email", email)
                .claim("role", "authenticated")
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(300))
                .build();

        return SecurityMockMvcRequestPostProcessors.authentication(
                converter.convert(jwt));
    }
}
