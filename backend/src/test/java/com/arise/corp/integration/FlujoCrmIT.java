package com.arise.corp.integration;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.request.ServletWebRequest;

import com.arise.corp.config.PerfilJwtAuthenticationConverter;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.TipoDocumento;
import com.arise.corp.crm.repository.ClienteRepository;
import com.arise.corp.login.AutenticacionPerfilTestSupport;
import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.login.repository.PerfilUsuarioRepository;
import com.arise.corp.shared.error.GlobalExceptionHandler;

import tools.jackson.databind.ObjectMapper;

/**
 * Flujo completo del CRM por MockMvc contra PostgreSQL efimero
 * (Requirements 17.5 y 24.11, CA-04, CA-05, ENG-03).
 *
 * <p>Recorre cuenta corporativa, cliente, contrato, {@code ACTIVO} y
 * {@code FINALIZADO} con peticiones HTTP reales y JWT simulado. Ademas
 * comprueba que la restriccion {@code UNIQUE} de PostgreSQL rechaza un cliente
 * duplicado con {@link DataIntegrityViolationException} y que el manejador de
 * errores la traduce a un 409 generico sin filtrar el mensaje de la base.
 */
class FlujoCrmIT extends PostgresIntegracionBase {

    private static final String RUTA_CUENTAS = "/api/crm/cuentas-corporativas";
    private static final String RUTA_CLIENTES = "/api/crm/clientes";
    private static final String RUTA_CONTRATOS = "/api/crm/contratos";

    private static final String NIT = "901234567";
    private static final String RAZON_SOCIAL = "Cuenta del flujo S.A.S.";
    private static final String DOCUMENTO = "10234567";
    private static final String NOMBRE_CLIENTE = "Cliente del flujo";
    private static final String CODIGO_CONTRATO = "CTR-FLUJO-001";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PerfilJwtAuthenticationConverter converter;

    @Autowired
    private PerfilUsuarioRepository perfiles;

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private GlobalExceptionHandler manejadorErrores;

    private UUID comercial;
    private RequestPostProcessor autenticacion;

    @BeforeEach
    void prepararSesion() {
        comercial = crearUsuarioAuth();
        perfiles.saveAndFlush(PerfilUsuario.crear(
                comercial,
                RolUsuario.COMERCIAL,
                comercial,
                Instant.parse("2026-11-11T12:00:00Z")));
        autenticacion = AutenticacionPerfilTestSupport.como(
                converter, comercial.toString(), "comercial@example.test");
    }

    @Test
    @DisplayName("El flujo cuenta, cliente, contrato, ACTIVO y FINALIZADO se persiste en PostgreSQL")
    void recorreElFlujoCompletoDelCrmPorMockMvc() throws Exception {
        UUID cuentaId = crearCuenta();
        UUID clienteId = crearCliente(cuentaId);
        UUID contratoId = crearContrato(clienteId);

        assertEquals(1, contar("select count(*) from app.cuentas_corporativas where id = ? and nit = ?",
                cuentaId, NIT));
        assertEquals(1, contar(
                "select count(*) from app.clientes where id = ? and cuenta_corporativa_id = ?",
                clienteId, cuentaId));
        assertEquals(1, contar(
                "select count(*) from app.contratos where id = ? and codigo = ? and estado = 'BORRADOR'",
                contratoId, CODIGO_CONTRATO));

        mvc.perform(patch(RUTA_CONTRATOS + "/{id}/estado", contratoId)
                        .with(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("estado", "ACTIVO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("ACTIVO")))
                .andExpect(jsonPath("$.estadoActualizadoPor", is(comercial.toString())));

        mvc.perform(patch(RUTA_CONTRATOS + "/{id}/estado", contratoId)
                        .with(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("estado", "FINALIZADO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("FINALIZADO")))
                .andExpect(jsonPath("$.cliente.id", is(clienteId.toString())));

        assertEquals("FINALIZADO", jdbc.queryForObject(
                "select estado from app.contratos where id = ?", String.class, contratoId));
        assertEquals(comercial, jdbc.queryForObject(
                "select estado_actualizado_por from app.contratos where id = ?",
                UUID.class, contratoId));
        assertNotNull(jdbc.queryForObject(
                "select estado_actualizado_en from app.contratos where id = ?",
                OffsetDateTime.class, contratoId));
    }

    @Test
    @DisplayName("Un cliente duplicado viola UNIQUE y la API responde 409 sin filtrar la base")
    void elClienteDuplicadoSeRechazaConDataIntegrityViolationY409() throws Exception {
        UUID cuentaId = crearCuenta();
        crearCliente(cuentaId);

        Cliente duplicado = Cliente.crear(
                TipoDocumento.CC,
                DOCUMENTO,
                "Duplicado ficticio",
                "duplicado@example.test",
                "3001234567",
                null,
                null,
                null,
                comercial,
                Instant.parse("2026-11-11T12:00:00Z"));

        assertThrows(DataIntegrityViolationException.class,
                () -> clientes.saveAndFlush(duplicado),
                "la restriccion UNIQUE de PostgreSQL debe rechazar el duplicado");

        mvc.perform(post(RUTA_CLIENTES)
                        .with(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cliente(cuentaId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.title", is("Conflicto")))
                .andExpect(jsonPath("$.conflicto", is("CLIENTE_DUPLICADO")));

        ResponseEntity<Object> respuesta = manejadorErrores.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicado"),
                new ServletWebRequest(new MockHttpServletRequest()));
        ProblemDetail problema = (ProblemDetail) respuesta.getBody();

        assertNotNull(problema);
        assertEquals(409, respuesta.getStatusCode().value());
        assertEquals(409, problema.getStatus());
        assertEquals("Conflicto", problema.getTitle());
        assertEquals("La operación entra en conflicto con el estado actual del recurso.",
                problema.getDetail());
        assertTrue(problema.getProperties() == null
                        || !problema.getProperties().containsKey("conflicto"),
                "el 409 generico no debe exponer el catalogo de conflictos de negocio");
    }

    private UUID crearCuenta() throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nit", NIT);
        cuerpo.put("razonSocial", RAZON_SOCIAL);
        cuerpo.put("nombreContacto", "Contacto ficticio");
        cuerpo.put("correoContacto", "contacto@example.test");
        cuerpo.put("telefonoContacto", "+57 300 123 4567");

        MvcResult resultado = mvc.perform(post(RUTA_CUENTAS)
                        .with(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.nit", is(NIT)))
                .andExpect(jsonPath("$.activa", is(true)))
                .andReturn();

        return idDe(resultado);
    }

    private UUID crearCliente(UUID cuentaId) throws Exception {
        MvcResult resultado = mvc.perform(post(RUTA_CLIENTES)
                        .with(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cliente(cuentaId))))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.numeroDocumento", is(DOCUMENTO)))
                .andExpect(jsonPath("$.activo", is(true)))
                .andExpect(jsonPath("$.cuentaCorporativa.id", is(cuentaId.toString())))
                .andReturn();

        return idDe(resultado);
    }

    private UUID crearContrato(UUID clienteId) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("clienteId", clienteId.toString());
        cuerpo.put("codigo", CODIGO_CONTRATO);
        cuerpo.put("descripcion", "Contrato del flujo");
        cuerpo.put("fechaInicio", "2026-01-01");
        cuerpo.put("fechaFin", "2026-12-31");
        cuerpo.put("valor", "1500000.00");

        MvcResult resultado = mvc.perform(post(RUTA_CONTRATOS)
                        .with(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.codigo", is(CODIGO_CONTRATO)))
                .andExpect(jsonPath("$.estado", is("BORRADOR")))
                .andExpect(jsonPath("$.cliente.id", is(clienteId.toString())))
                .andReturn();

        return idDe(resultado);
    }

    private Map<String, Object> cliente(UUID cuentaId) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipoDocumento", "CC");
        cuerpo.put("numeroDocumento", DOCUMENTO);
        cuerpo.put("nombre", NOMBRE_CLIENTE);
        cuerpo.put("correo", "cliente.flujo@example.test");
        cuerpo.put("telefono", "3001234567");
        cuerpo.put("direccion", "Calle Ficticia 123");
        cuerpo.put("ciudad", "Bogotá");
        cuerpo.put("cuentaCorporativaId", cuentaId.toString());
        return cuerpo;
    }

    private long contar(String sql, Object... argumentos) {
        Long total = jdbc.queryForObject(sql, Long.class, argumentos);
        return total == null ? 0L : total;
    }

    private String cuerpo(Map<String, Object> contenido) {
        return objectMapper.writeValueAsString(contenido);
    }

    private static UUID idDe(MvcResult resultado) throws Exception {
        String texto = resultado.getResponse().getContentAsString();
        int inicio = texto.indexOf("\"id\":\"") + "\"id\":\"".length();
        return UUID.fromString(texto.substring(inicio, texto.indexOf('"', inicio)));
    }
}
