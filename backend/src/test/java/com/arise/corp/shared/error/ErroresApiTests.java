package com.arise.corp.shared.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.arise.corp.crm.entity.EstadoContrato;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

    private static final String UUID_TEXTO =
            "123e4567-e89b-12d3-a456-426614174000";

    private static final String[] INFORMACION_INTERNA = {
            "Exception", "java.", "tools.jackson", "org.springframework",
            "_uk", "_ck", "select ", "insert "
    };

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void conflictoDeNegocioDevuelve409ConCodigoCampoYExtensiones()
            throws Exception {

        mvc.perform(get("/api/test/errores/conflicto-negocio").with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", is("Conflicto")))
                .andExpect(jsonPath("$.detail", is("El cliente está inactivo.")))
                .andExpect(jsonPath("$.conflicto",
                        is("CLIENTE_INACTIVO_EXISTENTE")))
                .andExpect(jsonPath("$.campo", is("cuentaCorporativaId")))
                .andExpect(jsonPath("$.clienteId", is(UUID_TEXTO)));
    }

    @Test
    void recursoNoEncontradoDevuelve404ConCampo() throws Exception {
        mvc.perform(get("/api/test/errores/no-encontrado").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", is("Recurso no encontrado")))
                .andExpect(jsonPath("$.detail", is("El cliente no existe.")))
                .andExpect(jsonPath("$.campo", is("clienteId")));
    }

    @Test
    void errorDeTipoEnElCuerpoInformaLaFecha() throws Exception {
        mvc.perform(post("/api/test/errores/tipo-cuerpo")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConFecha("31/12/2026")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail",
                        is("La solicitud contiene campos inválidos.")))
                .andExpect(jsonPath("$.errors[0].field", is("fechaInicio")))
                .andExpect(jsonPath("$.errors[0].message",
                        is("debe ser una fecha válida con formato AAAA-MM-DD")));
    }

    @Test
    void errorDeTipoEnElCuerpoInformaLaEnumeracion() throws Exception {
        mvc.perform(post("/api/test/errores/tipo-cuerpo")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConEstado("PAUSADO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("estado")))
                .andExpect(jsonPath("$.errors[0].message",
                        is("debe ser uno de: BORRADOR, ACTIVO, FINALIZADO, CANCELADO")));
    }

    @Test
    void errorDeTipoEnElCuerpoInformaElDecimal() throws Exception {
        mvc.perform(post("/api/test/errores/tipo-cuerpo")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConValor("\"abc\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("valor")))
                .andExpect(jsonPath("$.errors[0].message",
                        is("debe ser un número válido")));
    }

    @Test
    void errorDeTipoEnElCuerpoInformaElUuid() throws Exception {
        mvc.perform(post("/api/test/errores/tipo-cuerpo")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConId("no-es-uuid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("id")))
                .andExpect(jsonPath("$.errors[0].message",
                        is("debe ser un identificador UUID válido")));
    }

    @Test
    void errorDeTipoEnElCuerpoInformaElBooleano() throws Exception {
        mvc.perform(post("/api/test/errores/tipo-cuerpo")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConActivo("\"quizas\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("activo")))
                .andExpect(jsonPath("$.errors[0].message",
                        is("debe ser verdadero o falso")));
    }

    @Test
    void errorDeParametroInformaElNombreDelParametro() throws Exception {
        mvc.perform(get("/api/test/errores/parametro")
                        .param("limite", "abc")
                        .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail",
                        is("Los parámetros de la solicitud no son válidos.")))
                .andExpect(jsonPath("$.errors[0].field", is("limite")))
                .andExpect(jsonPath("$.errors[0].message",
                        is("debe ser un número válido")));
    }

    @Test
    void metodoNoSoportadoDevuelve405ConAllowYEnEspanol() throws Exception {
        mvc.perform(delete("/api/test/errores/validacion").with(jwt()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")))
                .andExpect(jsonPath("$.status", is(405)))
                .andExpect(jsonPath("$.title", is("Método no permitido")))
                .andExpect(jsonPath("$.detail",
                        is("La operación no está disponible para este recurso.")));
    }

    @Test
    void tipoDeContenidoNoAdmitidoDevuelve415EnEspanol() throws Exception {
        mvc.perform(post("/api/test/errores/validacion")
                        .with(jwt())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("texto"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(415)))
                .andExpect(jsonPath("$.title", is("Tipo de contenido no admitido")))
                .andExpect(jsonPath("$.detail",
                        is("Envía el cuerpo como application/json.")));
    }

    @Test
    void losErroresDeValidacionSeDeduplicanPorCampo() throws Exception {
        mvc.perform(post("/api/test/errores/duplicado")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()", is(1)))
                .andExpect(jsonPath("$.errors[0].field", is("nombre")));
    }

    @Test
    void conflictoDeConcurrenciaDevuelve409Generico() throws Exception {
        mvc.perform(get("/api/test/errores/concurrencia").with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", is("Conflicto")))
                .andExpect(jsonPath("$.detail",
                        is("El recurso está siendo modificado. Intenta nuevamente.")))
                .andExpect(content().string(not(containsString("detalle-interno"))));
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 17: Los errores no exponen información interna")
    void losErroresNoExponenInformacionInterna() throws Exception {
        List<RequestBuilder> solicitudes = solicitudesDeError();

        assertTrue(solicitudes.size() >= 14,
                "Property 17 debe cubrir todas las respuestas de error");

        for (RequestBuilder solicitud : solicitudes) {
            MvcResult resultado = mvc.perform(solicitud).andReturn();
            int estado = resultado.getResponse().getStatus();

            assertTrue(estado >= 400,
                    "toda solicitud de la propiedad debe producir un error, fue "
                            + estado);

            String tipo = resultado.getResponse().getContentType();
            assertNotNull(tipo, "respuesta sin Content-Type para " + estado);
            assertTrue(tipo.startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE),
                    "Content-Type inesperado para " + estado + ": " + tipo);

            String cuerpo = resultado.getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);

            for (String prohibido : INFORMACION_INTERNA) {
                assertFalse(cuerpo.contains(prohibido),
                        "el error " + estado + " expone <" + prohibido + ">: "
                                + cuerpo);
            }

            JsonNode problema = objectMapper.readTree(cuerpo);
            assertTrue(problema.get("title") != null
                            && problema.get("title").isTextual()
                            && !problema.get("title").asString().isBlank(),
                    "falta title en el error " + estado + ": " + cuerpo);
            assertTrue(problema.get("detail") != null
                            && problema.get("detail").isTextual()
                            && !problema.get("detail").asString().isBlank(),
                    "falta detail en el error " + estado + ": " + cuerpo);
        }
    }

    private List<RequestBuilder> solicitudesDeError() {
        return List.of(
                get("/api/me"),
                post("/api/test/errores/validacion")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"),
                post("/api/test/errores/validacion")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"),
                get("/api/test/errores/acceso").with(jwt()),
                get("/api/test/errores/inesperado").with(jwt()),
                get("/api/test/errores/conflicto").with(jwt()),
                delete("/api/test/errores/validacion").with(jwt()),
                post("/api/test/errores/validacion")
                        .with(jwt())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("texto"),
                get("/api/test/errores/conflicto-negocio").with(jwt()),
                get("/api/test/errores/no-encontrado").with(jwt()),
                post("/api/test/errores/tipo-cuerpo")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConFecha("31/12/2026")),
                get("/api/test/errores/parametro")
                        .param("limite", "abc")
                        .with(jwt()),
                post("/api/test/errores/duplicado")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"ab\"}"),
                get("/api/test/errores/concurrencia").with(jwt()));
    }

    private String cuerpoConFecha(String fecha) {
        return cuerpoCon("fechaInicio", texto(fecha));
    }

    private String cuerpoConEstado(String estado) {
        return cuerpoCon("estado", texto(estado));
    }

    private String cuerpoConValor(String valorJson) {
        return cuerpoCon("valor", valorJson);
    }

    private String cuerpoConId(String id) {
        return cuerpoCon("id", texto(id));
    }

    private String cuerpoConActivo(String activoJson) {
        return cuerpoCon("activo", activoJson);
    }

    private String cuerpoCon(String campo, String valorJson) {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("fechaInicio", "\"2026-01-01\"");
        campos.put("valor", "\"10.00\"");
        campos.put("estado", "\"ACTIVO\"");
        campos.put("id", "\"" + UUID_TEXTO + "\"");
        campos.put("activo", "true");
        campos.put(campo, valorJson);

        return campos.entrySet().stream()
                .map(entrada -> "\"" + entrada.getKey() + "\":"
                        + entrada.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String texto(String valor) {
        return "\"" + valor + "\"";
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

        @PostMapping("/api/test/errores/duplicado")
        String duplicado(@Valid @RequestBody SolicitudDuplicada solicitud) {
            return solicitud.nombre();
        }

        @PostMapping("/api/test/errores/tipo-cuerpo")
        String tipoCuerpo(@RequestBody SolicitudTipos solicitud) {
            return "ok";
        }

        @GetMapping("/api/test/errores/parametro")
        String parametro(@RequestParam int limite) {
            return "ok";
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

        @GetMapping("/api/test/errores/conflicto-negocio")
        String conflictoNegocio() {
            throw new ConflictoNegocioException(
                    "CLIENTE_INACTIVO_EXISTENTE",
                    "El cliente está inactivo.",
                    "cuentaCorporativaId",
                    Map.of("clienteId", UUID.fromString(UUID_TEXTO)));
        }

        @GetMapping("/api/test/errores/no-encontrado")
        String noEncontrado() {
            throw new RecursoNoEncontradoException(
                    "El cliente no existe.", "clienteId");
        }

        @GetMapping("/api/test/errores/concurrencia")
        String concurrencia() {
            throw new CannotAcquireLockException("detalle-interno");
        }
    }

    record SolicitudPrueba(@NotBlank String nombre) {
    }

    record SolicitudDuplicada(
            @Size(min = 5, message = "debe tener al menos 5 caracteres")
            @Pattern(regexp = "[0-9]+", message = "solo admite dígitos")
            String nombre) {
    }

    record SolicitudTipos(
            LocalDate fechaInicio,
            BigDecimal valor,
            EstadoContrato estado,
            UUID id,
            Boolean activo) {
    }
}
