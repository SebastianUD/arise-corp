package com.arise.corp.login.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.login.repository.PerfilUsuarioRepository;

/**
 * Pruebas de {@code GET /api/me} centradas en el mapeo de identidad y en el
 * rechazo de tokens por el resource server (decoder simulado).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

    private static final UUID USUARIO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final String EMAIL = "paula@example.test";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;

    @MockitoBean
    private JwtDecoder decoder;

    @BeforeEach
    void limpiarPerfiles() {
        perfilUsuarioRepository.deleteAll();
    }

    @Test
    void withoutTokenReturnsProblemDetailsUnauthorized() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(content().string(not(containsString("Bearer"))));
    }

    @Test
    void subjectOnlyIsAcceptedAndOptionalClaimsAreNull() throws Exception {
        guardarPerfil(RolUsuario.COMERCIAL, true);

        mvc.perform(get("/api/me").with(jwt().jwt(token -> token
                        .subject(USUARIO_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USUARIO_ID.toString()))
                .andExpect(jsonPath("$.email").value(nullValue()))
                .andExpect(jsonPath("$.role").value(RolUsuario.COMERCIAL.name()));
    }

    @Test
    void returnsBusinessRoleFromActiveProfileNotTechnicalClaim() throws Exception {
        guardarPerfil(RolUsuario.FINANZAS_RRHH, true);

        mvc.perform(get("/api/me").with(jwt().jwt(token -> token
                        .subject(USUARIO_ID.toString())
                        .claim("email", EMAIL)
                        .claim("role", "authenticated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USUARIO_ID.toString()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value(RolUsuario.FINANZAS_RRHH.name()));
    }

    @Test
    void missingProfileReturnsForbidden() throws Exception {
        mvc.perform(get("/api/me").with(jwt().jwt(token -> token
                        .subject(USUARIO_ID.toString())
                        .claim("email", EMAIL))))
                .andExpect(status().isForbidden());
    }

    @Test
    void inactiveProfileReturnsForbidden() throws Exception {
        guardarPerfil(RolUsuario.COMERCIAL, false);

        mvc.perform(get("/api/me").with(jwt().jwt(token -> token
                        .subject(USUARIO_ID.toString())
                        .claim("email", EMAIL))))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidTokenIsRejectedBySecurityFilter() throws Exception {
        assertRejectedToken("test-invalid-token", "Invalid signature");
    }

    @Test
    void expiredTokenIsRejectedBySecurityFilter() throws Exception {
        assertRejectedToken("test-expired-token", "Expired token");
    }

    private void guardarPerfil(RolUsuario rol, boolean activo) {
        Instant ahora = Instant.now();

        perfilUsuarioRepository.save(new PerfilUsuario(
                USUARIO_ID, rol, activo, ahora, ahora, null));
    }

    private void assertRejectedToken(String token, String reason) throws Exception {
        // Simula el rechazo del decoder; no consulta JWKS ni usa JWT reales.
        when(decoder.decode(token)).thenThrow(new BadJwtException(reason));
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(content().string(not(containsString(reason))));
        verify(decoder).decode(token);
    }
}
