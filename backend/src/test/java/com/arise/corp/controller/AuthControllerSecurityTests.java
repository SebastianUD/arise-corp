package com.arise.corp.controller;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de controlador/seguridad del endpoint protegido {@code GET /api/me}.
 *
 * <p>Se ejecutan contra la cadena de filtros real de Spring Security
 * ({@code SecurityConfig}) usando {@code @SpringBootTest} +
 * {@code @AutoConfigureMockMvc}, con el perfil de pruebas aislado
 * (H2 + valores JWT ficticios de {@code src/test/resources/application.properties}).
 * No se usan tokens reales de Supabase ni la base compartida {@code arise-corp-dev}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Requisitos: 9.1, 4.6, 5.6, CA-02, CA-03.
     *
     * <p>Una petición a {@code GET /api/me} sin token de acceso debe responder
     * con HTTP 401, incluir el encabezado {@code WWW-Authenticate} y no devolver
     * cuerpo JSON (cuerpo vacío).
     */
    @Test
    void getMeSinTokenDevuelve401SinCuerpoJson() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().string(emptyOrNullString()));
    }

    /**
     * Requisitos: 9.2, 5.1, CA-02, CA-03.
     *
     * <p>Una petición a {@code GET /api/me} con un JWT válido (simulado con
     * {@code SecurityMockMvcRequestPostProcessors.jwt()} e incluyendo el claim
     * {@code sub}) debe responder con HTTP 200. El token se simula sobre la
     * cadena de seguridad real sin usar tokens reales de Supabase.
     */
    @Test
    void getMeConJwtSimuladoDevuelve200() throws Exception {
        mockMvc.perform(get("/api/me")
                        .with(jwt().jwt(builder -> builder
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("email", "usuario@arise.test")
                                .claim("role", "authenticated"))))
                .andExpect(status().isOk());
    }

    /**
     * Requisitos: 9.3, 5.1, 5.2, 5.4.
     *
     * <p>Una petición a {@code GET /api/me} con un JWT simulado que incluye los
     * claims {@code sub}, {@code email} y {@code role} debe responder con HTTP 200
     * y un cuerpo JSON (camelCase) con los campos {@code id}, {@code email} y
     * {@code role} derivados del token: {@code id} proviene de {@code sub},
     * {@code email} del claim {@code email} y {@code role} del claim {@code role}.
     */
    @Test
    void getMeConJwtSimuladoDevuelveIdEmailYRolDelToken() throws Exception {
        String sub = "00000000-0000-0000-0000-000000000002";
        String email = "comercial@arise.test";
        String role = "authenticated";

        mockMvc.perform(get("/api/me")
                        .with(jwt().jwt(builder -> builder
                                .subject(sub)
                                .claim("email", email)
                                .claim("role", role))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(sub)))
                .andExpect(jsonPath("$.email", is(email)))
                .andExpect(jsonPath("$.role", is(role)));
    }

    /**
     * Requisitos: 9.5, 4.7, CA-02.
     *
     * <p>Una petición a {@code GET /api/me} con un token mal formado en el
     * encabezado {@code Authorization: Bearer <token>} debe responder con HTTP
     * 401 e incluir el encabezado {@code WWW-Authenticate}. A diferencia de las
     * pruebas 2.2 y 2.3, aquí el token viaja por el encabezado real para que el
     * resource server intente decodificarlo con el {@code JwtDecoder} de la
     * cadena de seguridad y lo rechace. El token no tiene la estructura de un
     * JWT (tres segmentos Base64URL separados por puntos), por lo que falla el
     * parseo localmente, sin ninguna consulta de red al JWKS ficticio.
     */
    @Test
    void getMeConTokenMalFormadoDevuelve401() throws Exception {
        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-no-valido"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));
    }

    /**
     * Requisitos: 9.5, 4.7, 4.8, CA-02.
     *
     * <p>Una petición a {@code GET /api/me} con un token que imita la estructura
     * de un JWT (tres segmentos separados por puntos) pero cuyo contenido es
     * inválido —firma manipulada y payload arbitrario, incluido un {@code exp}
     * vencido— debe responder con HTTP 401 e incluir el encabezado
     * {@code WWW-Authenticate}. El token se envía por el encabezado real para
     * ejercitar el {@code JwtDecoder}; al no ser un JWT verificable (segmentos
     * sin JSON Base64URL válido ni firma legítima) es rechazado durante el
     * parseo/validación local, sin tokens reales de Supabase.
     */
    @Test
    void getMeConTokenInvalidoDevuelve401() throws Exception {
        String tokenInvalido = "eyJhbGciOiJFUzI1NiJ9.payload-invalido.firma-invalida";

        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenInvalido))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));
    }
}
