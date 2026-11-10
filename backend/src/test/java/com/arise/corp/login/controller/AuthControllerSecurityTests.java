package com.arise.corp.login.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
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
import org.springframework.test.web.servlet.MockMvc;

import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.login.repository.PerfilUsuarioRepository;

/**
 * Pruebas de controlador/seguridad del endpoint protegido {@code GET /api/me}.
 *
 * <p>Se ejecutan contra la cadena de filtros real de Spring Security
 * ({@code SecurityConfig}) usando {@code @SpringBootTest} +
 * {@code @AutoConfigureMockMvc}, con el perfil de pruebas aislado
 * (H2 + valores JWT ficticios de {@code src/test/resources/application.properties}).
 * El rol se resuelve desde un perfil persistido en H2, no desde el claim
 * tecnico {@code role} del token. No se usan tokens reales de Supabase ni la
 * base compartida {@code arise-corp-dev}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerSecurityTests {

    private static final UUID USUARIO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String EMAIL = "comercial@arise.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;

    @BeforeEach
    void limpiarPerfiles() {
        perfilUsuarioRepository.deleteAll();
    }

    /**
     * Requisitos: 9.1, 4.6, 5.6, CA-02, CA-03.
     */
    @Test
    void getMeSinTokenDevuelve401ProblemDetails() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.instance", is("/api/me")));
    }

    /**
     * Requisitos: 9.2, 9.3, 5.1, 5.2, 5.4, AUTH-04, CA-02, CA-03.
     *
     * <p>El JWT incluye el claim tecnico {@code role=authenticated}, que no
     * debe devolverse: la respuesta usa el rol empresarial del perfil activo.
     */
    @Test
    void getMeConJwtYPerfilActivoDevuelveRolEmpresarial() throws Exception {
        guardarPerfil(USUARIO_ID, RolUsuario.COMERCIAL, true);

        mockMvc.perform(get("/api/me")
                        .with(jwt().jwt(builder -> builder
                                .subject(USUARIO_ID.toString())
                                .claim("email", EMAIL)
                                .claim("role", "authenticated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(USUARIO_ID.toString())))
                .andExpect(jsonPath("$.email", is(EMAIL)))
                .andExpect(jsonPath("$.role", is(RolUsuario.COMERCIAL.name())));
    }

    /**
     * Requisitos: 5.5, 9.3.
     */
    @Test
    void getMeSinClaimEmailDevuelveEmailNulo() throws Exception {
        guardarPerfil(USUARIO_ID, RolUsuario.OPERACIONES, true);

        mockMvc.perform(get("/api/me")
                        .with(jwt().jwt(builder -> builder
                                .subject(USUARIO_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(USUARIO_ID.toString())))
                .andExpect(jsonPath("$.email", nullValue()))
                .andExpect(jsonPath("$.role", is(RolUsuario.OPERACIONES.name())));
    }

    /**
     * Requisitos: 5.4, 6.2, AUTH-04, CA-03.
     */
    @Test
    void getMeConPerfilInexistenteDevuelve403() throws Exception {
        mockMvc.perform(get("/api/me")
                        .with(jwt().jwt(builder -> builder
                                .subject(USUARIO_ID.toString())
                                .claim("email", EMAIL))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.title", is("Acceso denegado")));
    }

    /**
     * Requisitos: 5.4, 6.2, AUTH-04, CA-03.
     */
    @Test
    void getMeConPerfilInactivoDevuelve403() throws Exception {
        guardarPerfil(USUARIO_ID, RolUsuario.COMERCIAL, false);

        mockMvc.perform(get("/api/me")
                        .with(jwt().jwt(builder -> builder
                                .subject(USUARIO_ID.toString())
                                .claim("email", EMAIL))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(403)));
    }

    /**
     * Requisitos: 9.5, 4.7, CA-02.
     */
    @Test
    void getMeConTokenMalFormadoDevuelve401() throws Exception {
        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-no-valido"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(401)));
    }

    /**
     * Requisitos: 9.5, 4.7, 4.8, CA-02.
     */
    @Test
    void getMeConTokenInvalidoDevuelve401() throws Exception {
        String tokenInvalido = "eyJhbGciOiJFUzI1NiJ9.payload-invalido.firma-invalida";

        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenInvalido))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(401)));
    }

    /**
     * DP-SEG-04: el origen local configurado por {@code CORS_ALLOWED_ORIGINS}
     * puede realizar peticiones preflight.
     */
    @Test
    void corsPermiteElOrigenConfigurado() throws Exception {
        mockMvc.perform(options("/api/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"));
    }

    /**
     * DP-SEG-04: los origenes no enumerados se rechazan sin comodines.
     */
    @Test
    void corsRechazaUnOrigenNoConfigurado() throws Exception {
        mockMvc.perform(options("/api/me")
                        .header(HttpHeaders.ORIGIN, "http://origen-no-permitido.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    private void guardarPerfil(UUID usuarioId, RolUsuario rol, boolean activo) {
        Instant ahora = Instant.now();

        perfilUsuarioRepository.save(new PerfilUsuario(
                usuarioId, rol, activo, ahora, ahora, null));
    }
}
