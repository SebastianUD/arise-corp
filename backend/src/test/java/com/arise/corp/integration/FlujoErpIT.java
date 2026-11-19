package com.arise.corp.integration;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import com.arise.corp.erp.entity.Proveedor;
import com.arise.corp.erp.entity.TipoDocumentoProveedor;
import com.arise.corp.erp.repository.ProveedorRepository;
import com.arise.corp.login.AutenticacionPerfilTestSupport;
import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.login.repository.PerfilUsuarioRepository;
import com.arise.corp.shared.error.GlobalExceptionHandler;

import tools.jackson.databind.ObjectMapper;

/**
 * Flujo completo del ERP por MockMvc contra PostgreSQL efimero (Requirements
 * 14.6, 14.8, 14.10, 20.7 y 26.5, CA-04, ENG-03).
 *
 * <p>Recorre un ingreso asociado a un cliente activo del CRM, el 409
 * {@code CLIENTE_INACTIVO}, un {@code PUT} que conserva el cliente ya
 * inactivo, un egreso asociado a un proveedor activo y la desactivacion de los
 * dos registros sin perder sus movimientos (Property 11). Tambien comprueba que
 * la restriccion {@code UNIQUE} de PostgreSQL rechaza un proveedor duplicado
 * con {@link DataIntegrityViolationException} y que la API responde 409 sin
 * filtrar el mensaje de la base.
 */
class FlujoErpIT extends PostgresIntegracionBase {

    private static final String RUTA_CUENTAS = "/api/crm/cuentas-corporativas";
    private static final String RUTA_CLIENTES = "/api/crm/clientes";
    private static final String RUTA_PROVEEDORES = "/api/erp/proveedores";
    private static final String RUTA_MOVIMIENTOS = "/api/erp/movimientos";

    private static final String NIT_CUENTA = "901234567";
    private static final String DOCUMENTO_CLIENTE = "10234567";
    private static final String DOCUMENTO_PROVEEDOR = "900123456";
    private static final String FECHA = "2026-05-20";
    private static final String VALOR = "1500000.00";
    private static final String REFERENCIA = "FAC-FLUJO-001";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PerfilJwtAuthenticationConverter converter;

    @Autowired
    private PerfilUsuarioRepository perfiles;

    @Autowired
    private ProveedorRepository proveedores;

    @Autowired
    private GlobalExceptionHandler manejadorErrores;

    private UUID administrador;
    private RequestPostProcessor autenticacion;

    @BeforeEach
    void prepararSesion() {
        administrador = crearUsuarioAuth();
        perfiles.saveAndFlush(PerfilUsuario.crear(
                administrador,
                RolUsuario.ADMINISTRADOR,
                administrador,
                Instant.parse("2026-11-11T12:00:00Z")));
        autenticacion = AutenticacionPerfilTestSupport.como(
                converter, administrador.toString(), "admin@example.test");
    }

    @Test
    @DisplayName("El flujo ingreso, cliente inactivo, egreso y desactivaciones se persiste en PostgreSQL")
    void recorreElFlujoCompletoDelErpPorMockMvc() throws Exception {
        UUID clienteId = crearCliente();
        UUID ingresoId = crearIngreso(clienteId);

        assertEquals(1, contar(
                "select count(*) from app.movimientos_financieros"
                        + " where id = ? and cliente_id = ? and estado = 'PENDIENTE'",
                ingresoId, clienteId));

        mvc.perform(patch(RUTA_CLIENTES + "/{id}", clienteId)
                        .with(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("activo", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo", is(false)));

        // Un ingreso nuevo con el cliente inactivo es un conflicto de negocio.
        mvc.perform(post(RUTA_MOVIMIENTOS)
                        .with(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(ingreso(clienteId, "Ingreso rechazado"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.conflicto", is("CLIENTE_INACTIVO")))
                .andExpect(jsonPath("$.campo", is("clienteId")));

        // El PUT que conserva el cliente ya inactivo se aplica.
        mvc.perform(put(RUTA_MOVIMIENTOS + "/{id}", ingresoId)
                        .with(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(ingreso(clienteId, "Ingreso editado"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concepto", is("Ingreso editado")))
                .andExpect(jsonPath("$.cliente.id", is(clienteId.toString())))
                .andExpect(jsonPath("$.cliente.activo", is(false)))
                .andExpect(jsonPath("$.actualizadoPor", is(administrador.toString())));

        UUID proveedorId = crearProveedor();
        UUID egresoId = crearEgreso(proveedorId);

        assertEquals(1, contar(
                "select count(*) from app.movimientos_financieros"
                        + " where id = ? and proveedor_id = ?",
                egresoId, proveedorId));

        mvc.perform(patch(RUTA_PROVEEDORES + "/{id}", proveedorId)
                        .with(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("activo", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo", is(false)));

        // Property 11: desactivar conserva los movimientos asociados.
        assertEquals(2, contar("select count(*) from app.movimientos_financieros"));

        mvc.perform(get(RUTA_MOVIMIENTOS + "/{id}", ingresoId).with(autenticacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.id", is(clienteId.toString())))
                .andExpect(jsonPath("$.cliente.activo", is(false)))
                .andExpect(jsonPath("$.proveedor").value(org.hamcrest.Matchers.nullValue()));

        mvc.perform(get(RUTA_MOVIMIENTOS + "/{id}", egresoId).with(autenticacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proveedor.id", is(proveedorId.toString())))
                .andExpect(jsonPath("$.proveedor.activo", is(false)))
                .andExpect(jsonPath("$.cliente").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("Un proveedor duplicado viola UNIQUE y la API responde 409 sin filtrar la base")
    void elProveedorDuplicadoSeRechazaConDataIntegrityViolationY409() throws Exception {
        crearProveedor();

        Proveedor duplicado = Proveedor.crear(
                TipoDocumentoProveedor.NIT,
                DOCUMENTO_PROVEEDOR,
                "Duplicado ficticio",
                null,
                "duplicado@example.test",
                "601 000 0000",
                null,
                null,
                administrador,
                Instant.parse("2026-11-11T12:00:00Z"));

        assertThrows(DataIntegrityViolationException.class,
                () -> proveedores.saveAndFlush(duplicado),
                "la restriccion UNIQUE de PostgreSQL debe rechazar el duplicado");

        mvc.perform(post(RUTA_PROVEEDORES)
                        .with(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(proveedor())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.title", is("Conflicto")))
                .andExpect(jsonPath("$.conflicto", is("PROVEEDOR_DUPLICADO")));

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
        cuerpo.put("nit", NIT_CUENTA);
        cuerpo.put("razonSocial", "Cuenta del flujo ERP S.A.S.");
        cuerpo.put("nombreContacto", "Contacto ficticio");
        cuerpo.put("correoContacto", "contacto@example.test");
        cuerpo.put("telefonoContacto", "+57 300 123 4567");

        MvcResult resultado = mvc.perform(post(RUTA_CUENTAS)
                        .with(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn();

        return idDe(resultado);
    }

    private UUID crearCliente() throws Exception {
        UUID cuentaId = crearCuenta();
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipoDocumento", "CC");
        cuerpo.put("numeroDocumento", DOCUMENTO_CLIENTE);
        cuerpo.put("nombre", "Cliente del flujo ERP");
        cuerpo.put("correo", "cliente.erp@example.test");
        cuerpo.put("telefono", "3001234567");
        cuerpo.put("ciudad", "Bogotá");
        cuerpo.put("cuentaCorporativaId", cuentaId.toString());

        MvcResult resultado = mvc.perform(post(RUTA_CLIENTES)
                        .with(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.activo", is(true)))
                .andReturn();

        return idDe(resultado);
    }

    private Map<String, Object> proveedor() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipoDocumento", "NIT");
        cuerpo.put("numeroDocumento", DOCUMENTO_PROVEEDOR);
        cuerpo.put("nombre", "Proveedor del flujo ERP");
        cuerpo.put("nombreContacto", "Contacto ficticio");
        cuerpo.put("correo", "proveedor.erp@example.test");
        cuerpo.put("telefono", "601 000 0000");
        cuerpo.put("direccion", "Calle Ficticia 123");
        cuerpo.put("ciudad", "Bogotá");
        return cuerpo;
    }

    private UUID crearProveedor() throws Exception {
        MvcResult resultado = mvc.perform(post(RUTA_PROVEEDORES)
                        .with(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(proveedor())))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.numeroDocumento", is(DOCUMENTO_PROVEEDOR)))
                .andExpect(jsonPath("$.activo", is(true)))
                .andReturn();

        return idDe(resultado);
    }

    private Map<String, Object> ingreso(UUID clienteId, String concepto) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipo", "INGRESO");
        cuerpo.put("fecha", FECHA);
        cuerpo.put("concepto", concepto);
        cuerpo.put("valor", VALOR);
        cuerpo.put("referencia", REFERENCIA);
        cuerpo.put("clienteId", clienteId.toString());
        return cuerpo;
    }

    private UUID crearIngreso(UUID clienteId) throws Exception {
        MvcResult resultado = mvc.perform(post(RUTA_MOVIMIENTOS)
                        .with(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(ingreso(clienteId, "Ingreso del flujo"))))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.tipo", is("INGRESO")))
                .andExpect(jsonPath("$.estado", is("PENDIENTE")))
                .andExpect(jsonPath("$.valor", is(VALOR)))
                .andExpect(jsonPath("$.cliente.id", is(clienteId.toString())))
                .andExpect(jsonPath("$.cliente.activo", is(true)))
                .andExpect(jsonPath("$.proveedor").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.actualizadoPor", is(administrador.toString())))
                .andReturn();

        return idDe(resultado);
    }

    private UUID crearEgreso(UUID proveedorId) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipo", "EGRESO");
        cuerpo.put("fecha", FECHA);
        cuerpo.put("concepto", "Egreso del flujo");
        cuerpo.put("valor", "980000.25");
        cuerpo.put("referencia", "FAC-FLUJO-002");
        cuerpo.put("proveedorId", proveedorId.toString());

        MvcResult resultado = mvc.perform(post(RUTA_MOVIMIENTOS)
                        .with(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.tipo", is("EGRESO")))
                .andExpect(jsonPath("$.estado", is("PENDIENTE")))
                .andExpect(jsonPath("$.proveedor.id", is(proveedorId.toString())))
                .andExpect(jsonPath("$.proveedor.activo", is(true)))
                .andExpect(jsonPath("$.cliente").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();

        return idDe(resultado);
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
