package com.arise.corp.shared.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Pruebas del formato uniforme de errores (DP-API-02).
 *
 * <p>Verifica que la cadena real de Spring Security y el manejador global
 * respondan RFC 9457 Problem Details, que las validaciones por campo incluyan
 * la extension {@code errors} y que ningun detalle interno se filtre
 * (AUTH-05, CA-06). No se usan tokens reales de Supabase.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ErroresApiTests.ConfiguracionErrores.class)
class ErroresApiTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void sinTokenDevuelve401ProblemDetailsConWwwAuthenticate() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.title", is("No autenticado")))
                .andExpect(jsonPath("$.instance", is("/api/me")));
    }

    @Test
    void validacionPorCampoDevuelve400ConExtensionErrors() throws Exception {
        mvc.perform(post("/api/test/errores/validacion")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.title", is("Solicitud inválida")))
                .andExpect(jsonPath("$.detail",
                        is("La solicitud contiene campos inválidos.")))
                .andExpect(jsonPath("$.errors[0].field", is("nombre")))
                .andExpect(jsonPath("$.errors[0].message",
                        not(containsString("Exception"))));
    }

    @Test
    void jsonMalFormadoDevuelve400SinDetallesInternos() throws Exception {
        mvc.perform(post("/api/test/errores/validacion")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail",
                        is("El cuerpo de la solicitud no es válido.")))
                .andExpect(content().string(not(containsString("Json"))));
    }

    @Test
    void accesoDenegadoEnServicioDevuelve403ProblemDetails() throws Exception {
        mvc.perform(get("/api/test/errores/acceso").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.title", is("Acceso denegado")))
                .andExpect(content().string(not(containsString("detalle-interno"))));
    }

    @Test
    void errorInesperadoDevuelve500Generico() throws Exception {
        mvc.perform(get("/api/test/errores/inesperado").with(jwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.detail",
                        is("Ocurrió un error inesperado. Intenta nuevamente.")))
                .andExpect(content().string(not(containsString("detalle-interno"))));
    }

    @Test
    void conflictoDeIntegridadDevuelve409SinMensajeDeBase() throws Exception {
        mvc.perform(get("/api/test/errores/conflicto").with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(content().string(not(containsString("detalle-sql"))));
    }

    @Test
    void metodoNoSoportadoDevuelve405ProblemDetails() throws Exception {
        mvc.perform(delete("/api/test/errores/validacion").with(jwt()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(405)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionErrores {

        @Bean
        EndpointsErrores endpointsErrores() {
            return new EndpointsErrores();
        }
    }

    @RestController
    static class EndpointsErrores {

        @PostMapping("/api/test/errores/validacion")
        String validar(@Valid @RequestBody SolicitudPrueba solicitud) {
            return solicitud.nombre();
        }

        @GetMapping("/api/test/errores/acceso")
        String accesoDenegado() {
            throw new AccessDeniedException("detalle-interno");
        }

        @GetMapping("/api/test/errores/inesperado")
        String inesperado() {
            throw new IllegalStateException("detalle-interno");
        }

        @GetMapping("/api/test/errores/conflicto")
        String conflicto() {
            throw new DataIntegrityViolationException("detalle-sql");
        }
    }

    record SolicitudPrueba(@NotBlank String nombre) {
    }
}
