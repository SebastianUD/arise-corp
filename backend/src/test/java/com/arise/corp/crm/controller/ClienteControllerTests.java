package com.arise.corp.crm.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.arise.corp.crm.CrmDatosPrueba;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.Contrato;
import com.arise.corp.crm.entity.CuentaCorporativa;
import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.crm.repository.ClienteRepository;
import com.arise.corp.crm.repository.ContratoRepository;
import com.arise.corp.login.entity.RolUsuario;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pruebas MockMvc del API de clientes (Requirements 4, 5, 7, 8, 16.6 y 23.7).
 *
 * <p>Usa H2 y JWT simulados convertidos con el converter real a partir de
 * perfiles persistidos. Cubre la vista por rol (Property 10), los campos que
 * controla el servidor (Property 6) y los parametros de lista (Property 14).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CrmDatosPrueba.class)
class ClienteControllerTests {

    private static final String RUTA = "/api/crm/clientes";
    private static final String NOMBRE = "Ana Ficticia";
    private static final String CORREO = "ana.ficticia@example.test";
    private static final String TELEFONO = "3001234567";
    private static final String DIRECCION = "Calle Ficticia 123";
    private static final String CIUDAD = "Bogota";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CrmDatosPrueba datos;

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private ContratoRepository contratos;

    private UUID comercial;
    private UUID finanzas;
    private UUID operaciones;

    @BeforeEach
    void setUp() {
        datos.limpiar();
        comercial = datos.crearPerfil(RolUsuario.COMERCIAL);
        finanzas = datos.crearPerfil(RolUsuario.FINANZAS_RRHH);
        operaciones = datos.crearPerfil(RolUsuario.OPERACIONES);
    }

    @Test
    void crearDevuelve201ConLocationYLaAuditoriaDelActor() throws Exception {
        CuentaCorporativa cuenta = datos.crearCuenta();

        MvcResult resultado = mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(clienteConCuenta(cuenta.getId()))))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.activo", is(true)))
                .andExpect(jsonPath("$.numeroDocumento", is("10234567")))
                .andExpect(jsonPath("$.cuentaCorporativa.id",
                        is(cuenta.getId().toString())))
                .andExpect(jsonPath("$.actualizadoPor",
                        is(comercial.toString())))
                .andReturn();

        UUID creado = idDe(resultado);

        assertEquals(RUTA + "/" + creado,
                resultado.getResponse().getHeader(HttpHeaders.LOCATION));
        assertTrue(clientes.findById(creado).orElseThrow().isActivo());
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 6: Los campos controlados por el servidor no se toman del cuerpo")
    void crearIgnoraLosCamposControladosPorElServidor() throws Exception {
        Map<String, Object> cuerpo = clienteValido();
        UUID idAjeno = UUID.randomUUID();
        UUID actorAjeno = UUID.randomUUID();
        cuerpo.put("id", idAjeno.toString());
        cuerpo.put("activo", false);
        cuerpo.put("creadoEn", "2000-01-01T00:00:00Z");
        cuerpo.put("actualizadoEn", "2000-01-01T00:00:00Z");
        cuerpo.put("actualizadoPor", actorAjeno.toString());

        mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(idAjeno.toString())))
                .andExpect(jsonPath("$.activo", is(true)))
                .andExpect(jsonPath("$.actualizadoPor", is(comercial.toString())))
                .andExpect(jsonPath("$.creadoEn",
                        not("2000-01-01T00:00:00Z")));
    }

    @Test
    void listarDevuelveLaVistaCompletaParaLosGestores() throws Exception {
        Cliente cliente = datos.crearCliente(
                datos.siguienteDocumento(), NOMBRE, CORREO, TELEFONO,
                DIRECCION, CIUDAD, true, null);

        mvc.perform(get(RUTA)
                        .with(datos.autenticacionDe(comercial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id",
                        is(cliente.getId().toString())))
                .andExpect(jsonPath("$.content[0].correo", is(CORREO)))
                .andExpect(jsonPath("$.content[0].telefono", is(TELEFONO)))
                .andExpect(jsonPath("$.content[0].direccion", is(DIRECCION)))
                .andExpect(jsonPath("$.content[0].actualizadoEn", notNullValue()))
                .andExpect(jsonPath("$.size", is(20)));
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 10: La vista del cliente depende solo del rol")
    void listarDevuelveLaVistaReducidaParaLecturaCruzada() throws Exception {
        Cliente cliente = datos.crearCliente(
                datos.siguienteDocumento(), NOMBRE, CORREO, TELEFONO,
                DIRECCION, CIUDAD, true, null);

        for (UUID lector : new UUID[] {finanzas, operaciones}) {
            mvc.perform(get(RUTA)
                            .with(datos.autenticacionDe(lector)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id",
                            is(cliente.getId().toString())))
                    .andExpect(jsonPath("$.content[0].nombre", is(NOMBRE)))
                    .andExpect(jsonPath("$.content[0].ciudad", is(CIUDAD)))
                    .andExpect(jsonPath("$.content[0].correo").doesNotExist())
                    .andExpect(jsonPath("$.content[0].telefono").doesNotExist())
                    .andExpect(jsonPath("$.content[0].direccion").doesNotExist())
                    .andExpect(jsonPath("$.content[0].actualizadoEn").doesNotExist());
        }
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 10: La vista del cliente depende solo del rol")
    void obtenerAplicaLaVistaDelRolYDevuelve404SiNoExiste() throws Exception {
        Cliente cliente = datos.crearCliente(
                datos.siguienteDocumento(), NOMBRE, CORREO, TELEFONO,
                DIRECCION, CIUDAD, true, null);

        mvc.perform(get(RUTA + "/{id}", cliente.getId())
                        .with(datos.autenticacionDe(comercial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo", is(CORREO)))
                .andExpect(jsonPath("$.direccion", is(DIRECCION)));

        for (UUID lector : new UUID[] {finanzas, operaciones}) {
            mvc.perform(get(RUTA + "/{id}", cliente.getId())
                            .with(datos.autenticacionDe(lector)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correo").doesNotExist())
                    .andExpect(jsonPath("$.telefono").doesNotExist())
                    .andExpect(jsonPath("$.direccion").doesNotExist());
        }

        mvc.perform(get(RUTA + "/{id}", UUID.randomUUID())
                        .with(datos.autenticacionDe(comercial)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.title", is("Recurso no encontrado")));
    }

    @Test
    void listarAplicaLosFiltrosDeNombreDocumentoEstadoYCuenta() throws Exception {
        CuentaCorporativa cuenta = datos.crearCuenta();
        Cliente ana = datos.crearCliente(
                "10234567", "Ana Ficticia", true, cuenta);
        Cliente luis = datos.crearCliente(
                "98765432", "Luis Ficticio", false, null);

        mvc.perform(get(RUTA).param("q", "ana")
                        .with(datos.autenticacionDe(comercial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(ana.getId().toString())))
                .andExpect(jsonPath("$.totalElements", is(1)));

        mvc.perform(get(RUTA).param("q", "102.345-67")
                        .with(datos.autenticacionDe(comercial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(ana.getId().toString())));

        mvc.perform(get(RUTA).param("activo", "false")
                        .with(datos.autenticacionDe(comercial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(luis.getId().toString())))
                .andExpect(jsonPath("$.totalElements", is(1)));

        mvc.perform(get(RUTA)
                        .param("cuentaCorporativaId", cuenta.getId().toString())
                        .with(datos.autenticacionDe(comercial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(ana.getId().toString())))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @ParameterizedTest(name = "{0} con {1} -> 400 en {2}")
    @MethodSource("cuerposInvalidos")
    void devuelve400PorCadaReglaDeValidacion(
            String metodo,
            Map<String, Object> cuerpo,
            String campo) throws Exception {

        var peticion = "POST".equals(metodo)
                ? post(RUTA)
                : put(RUTA + "/{id}", UUID.randomUUID());

        mvc.perform(peticion
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.errors[0].field", is(campo)));
    }

    @Test
    void actualizarReemplazaLosDatosYQuitaLaCuentaOmitida() throws Exception {
        CuentaCorporativa cuenta = datos.crearCuenta();
        Cliente cliente = datos.crearCliente(
                "10234567", NOMBRE, CORREO, TELEFONO,
                DIRECCION, CIUDAD, true, cuenta);
        var creadoEn = cliente.getCreadoEn();

        Map<String, Object> cuerpo = clienteValido();
        cuerpo.put("tipoDocumento", "CE");
        cuerpo.put("numeroDocumento", "AB-123");
        cuerpo.put("nombre", "Nombre Nuevo");
        cuerpo.remove("direccion");
        cuerpo.remove("ciudad");
        cuerpo.remove("cuentaCorporativaId");

        MvcResult resultado = mvc.perform(put(RUTA + "/{id}", cliente.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroDocumento", is("AB123")))
                .andExpect(jsonPath("$.nombre", is("Nombre Nuevo")))
                .andExpect(jsonPath("$.direccion").value(nullValue()))
                .andExpect(jsonPath("$.ciudad").value(nullValue()))
                .andExpect(jsonPath("$.cuentaCorporativa").value(nullValue()))
                .andExpect(jsonPath("$.activo", is(true)))
                .andExpect(jsonPath("$.actualizadoPor",
                        is(comercial.toString())))
                .andReturn();

        JsonNode respuesta = objectMapper.readTree(
                resultado.getResponse().getContentAsString());

        assertEquals(creadoEn.toString(), respuesta.get("creadoEn").asString());
        Cliente actualizado = clientes.findById(cliente.getId()).orElseThrow();
        assertEquals("AB123", actualizado.getNumeroDocumento());
        assertNotNull(actualizado.getCreadoEn());
    }

    @Test
    void devuelve404AlEditarUnClienteInexistente() throws Exception {
        mvc.perform(put(RUTA + "/{id}", UUID.randomUUID())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(clienteValido())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void devuelve404DeLaCuentaConElCampoDeLaReferencia() throws Exception {
        Map<String, Object> cuerpo = clienteValido();
        cuerpo.put("cuentaCorporativaId", UUID.randomUUID().toString());

        mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail",
                        is("La cuenta corporativa no existe.")))
                .andExpect(jsonPath("$.campo", is("cuentaCorporativaId")));
    }

    @Test
    void devuelve409CuandoElDocumentoPerteneceAUnClienteActivo() throws Exception {
        Cliente existente = datos.crearCliente(
                "10234567", NOMBRE, true, null);

        mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(clienteValido())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("CLIENTE_DUPLICADO")))
                .andExpect(jsonPath("$.campo", is("numeroDocumento")))
                .andExpect(jsonPath("$.detail",
                        is("Ya existe un cliente registrado con este tipo y número de documento.")));

        assertEquals(1, clientes.count());
        assertNotNull(existente.getId());
    }

    @Test
    void devuelve409CuandoElDocumentoPerteneceAUnClienteInactivo() throws Exception {
        Cliente existente = datos.crearCliente(
                "10234567", NOMBRE, false, null);

        mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(clienteValido())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("CLIENTE_INACTIVO_EXISTENTE")))
                .andExpect(jsonPath("$.campo", is("numeroDocumento")))
                .andExpect(jsonPath("$.clienteId",
                        is(existente.getId().toString())));

        assertFalse(clientes.findById(existente.getId()).orElseThrow().isActivo());
    }

    @Test
    void devuelve409CuandoLaCuentaEstaInactiva() throws Exception {
        CuentaCorporativa inactiva = datos.crearCuenta(false);

        mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(clienteConCuenta(inactiva.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("CUENTA_INACTIVA")))
                .andExpect(jsonPath("$.campo", is("cuentaCorporativaId")));

        assertEquals(0, clientes.count());
    }

    @Test
    void devuelve409AlAsignarElDocumentoDeOtroClienteEnLaEdicion() throws Exception {
        Cliente cliente = datos.crearCliente(
                "10234567", NOMBRE, true, null);
        datos.crearCliente("98765432", "Otro cliente", true, null);

        Map<String, Object> cuerpo = clienteValido();
        cuerpo.put("numeroDocumento", "98765432");

        mvc.perform(put(RUTA + "/{id}", cliente.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("CLIENTE_DUPLICADO")))
                .andExpect(jsonPath("$.campo", is("numeroDocumento")));

        assertEquals("10234567", clientes.findById(cliente.getId())
                .orElseThrow().getNumeroDocumento());
    }

    @Test
    void devuelve409AlAsignarUnaCuentaInactivaDistintaEnLaEdicion() throws Exception {
        Cliente cliente = datos.crearCliente(
                "10234567", NOMBRE, true, datos.crearCuenta());
        CuentaCorporativa inactiva = datos.crearCuenta(false);

        mvc.perform(put(RUTA + "/{id}", cliente.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(clienteConCuenta(inactiva.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("CUENTA_INACTIVA")))
                .andExpect(jsonPath("$.campo", is("cuentaCorporativaId")));
    }

    @Test
    void cambiarEstadoDesactivaSinTocarLosContratosYEsIdempotente() throws Exception {
        Cliente cliente = datos.crearCliente(
                "10234567", NOMBRE, true, null);
        Contrato contrato = datos.crearContrato(
                cliente, EstadoContrato.ACTIVO);

        MvcResult primera = mvc.perform(patch(RUTA + "/{id}", cliente.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo", is(false)))
                .andExpect(jsonPath("$.actualizadoPor",
                        is(comercial.toString())))
                .andReturn();

        String actualizadoEn = objectMapper.readTree(
                primera.getResponse().getContentAsString())
                .get("actualizadoEn").asString();

        mvc.perform(patch(RUTA + "/{id}", cliente.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo", is(false)))
                .andExpect(jsonPath("$.actualizadoEn", is(actualizadoEn)));

        Contrato sinCambios = contratos.findById(contrato.getId()).orElseThrow();
        assertEquals(EstadoContrato.ACTIVO, sinCambios.getEstado());
        assertFalse(clientes.findById(cliente.getId()).orElseThrow().isActivo());
    }

    @Test
    void cambiarEstadoDevuelve400SinActivoOConUnTipoIncorrecto() throws Exception {
        Cliente cliente = datos.crearCliente(
                "10234567", NOMBRE, true, null);

        mvc.perform(patch(RUTA + "/{id}", cliente.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("activo")));

        mvc.perform(patch(RUTA + "/{id}", cliente.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":\"quizas\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("activo")));

        mvc.perform(patch(RUTA + "/{id}", UUID.randomUUID())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteDevuelve405YConservaElCliente() throws Exception {
        Cliente cliente = datos.crearCliente(
                "10234567", NOMBRE, true, null);

        mvc.perform(delete(RUTA + "/{id}", cliente.getId())
                        .with(datos.autenticacionDe(comercial)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(405)))
                .andExpect(jsonPath("$.title", is("Método no permitido")))
                .andExpect(header().string(HttpHeaders.ALLOW, allOf(
                        containsString("GET"),
                        containsString("PUT"),
                        containsString("PATCH"))));

        assertTrue(clientes.findById(cliente.getId()).isPresent());
    }

    @ParameterizedTest(name = "GET {0} -> {1}")
    @MethodSource("parametrosDeLista")
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 14: Los parámetros de lista se aceptan o rechazan según DP-API-03")
    void aceptaORechazaLosParametrosDeLista(
            String consulta,
            int estado,
            String campo) throws Exception {

        var peticion = get(RUTA).with(datos.autenticacionDe(comercial));

        if (!consulta.isEmpty()) {
            for (String par : consulta.split("&")) {
                String[] partes = par.split("=", 2);
                peticion.param(partes[0], partes[1]);
            }
        }

        var esperado = mvc.perform(peticion)
                .andExpect(status().is(estado));

        if (estado == 400 && campo != null) {
            esperado.andExpect(jsonPath("$.errors[0].field", is(campo)));
        }
    }

    static Stream<Arguments> cuerposInvalidos() {
        List<Arguments> casos = new ArrayList<>();

        for (String metodo : new String[] {"POST", "PUT"}) {
            casos.add(Arguments.of(metodo, sinCampo("tipoDocumento"), "tipoDocumento"));
            casos.add(Arguments.of(metodo, conCampo("tipoDocumento", "XX"), "tipoDocumento"));
            casos.add(Arguments.of(metodo, sinCampo("numeroDocumento"), "numeroDocumento"));
            casos.add(Arguments.of(metodo, conCampo("numeroDocumento", "AB"), "numeroDocumento"));
            casos.add(Arguments.of(metodo, conCampo("numeroDocumento", "ABC123"), "numeroDocumento"));
            casos.add(Arguments.of(metodo, sinCampo("nombre"), "nombre"));
            casos.add(Arguments.of(metodo, conCampo("nombre", "a".repeat(201)), "nombre"));
            casos.add(Arguments.of(metodo, sinCampo("correo"), "correo"));
            casos.add(Arguments.of(metodo, conCampo("correo", "sin-arroba"), "correo"));
            casos.add(Arguments.of(metodo, sinCampo("telefono"), "telefono"));
            casos.add(Arguments.of(metodo, conCampo("telefono", "12"), "telefono"));
            casos.add(Arguments.of(metodo, conCampo("direccion", "a".repeat(201)), "direccion"));
            casos.add(Arguments.of(metodo, conCampo("ciudad", "a".repeat(101)), "ciudad"));
            casos.add(Arguments.of(metodo, conCampo("cuentaCorporativaId", "no-es-uuid"), "cuentaCorporativaId"));
        }

        return casos.stream();
    }

    static Stream<Arguments> parametrosDeLista() {
        return Stream.of(
                Arguments.of("", 200, null),
                Arguments.of("page=0&size=20&sort=nombre,asc", 200, null),
                Arguments.of("page=2&size=1&sort=numeroDocumento,desc", 200, null),
                Arguments.of("size=100&sort=creadoEn,asc", 200, null),
                Arguments.of("page=-1", 400, "page"),
                Arguments.of("size=0", 400, "size"),
                Arguments.of("size=101", 400, "size"),
                Arguments.of("sort=correo,asc", 400, "sort"),
                Arguments.of("sort=nombre", 400, "sort"),
                Arguments.of("sort=nombre,ASC", 400, "sort"),
                Arguments.of("page=abc", 400, "page"),
                Arguments.of("size=abc", 400, "size"),
                Arguments.of("activo=quizas", 400, "activo"),
                Arguments.of("cuentaCorporativaId=abc", 400, "cuentaCorporativaId"),
                Arguments.of("activo=true", 200, null),
                Arguments.of("q=" + "a".repeat(100), 200, null),
                Arguments.of("q=" + "a".repeat(101), 400, "q"));
    }

    private static Map<String, Object> clienteValido() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipoDocumento", "CC");
        cuerpo.put("numeroDocumento", "10234567");
        cuerpo.put("nombre", NOMBRE);
        cuerpo.put("correo", CORREO);
        cuerpo.put("telefono", TELEFONO);
        cuerpo.put("direccion", DIRECCION);
        cuerpo.put("ciudad", CIUDAD);
        cuerpo.put("cuentaCorporativaId", null);
        return cuerpo;
    }

    private static Map<String, Object> clienteConCuenta(UUID cuentaId) {
        Map<String, Object> cuerpo = clienteValido();
        cuerpo.put("cuentaCorporativaId", cuentaId.toString());
        return cuerpo;
    }

    private static Map<String, Object> sinCampo(String campo) {
        Map<String, Object> cuerpo = clienteValido();
        cuerpo.remove(campo);
        return cuerpo;
    }

    private static Map<String, Object> conCampo(String campo, Object valor) {
        Map<String, Object> cuerpo = clienteValido();
        cuerpo.put(campo, valor);
        return cuerpo;
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
