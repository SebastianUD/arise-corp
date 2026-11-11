package com.arise.corp.crm.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.arise.corp.crm.entity.CuentaCorporativa;
import com.arise.corp.crm.repository.ClienteRepository;
import com.arise.corp.crm.repository.CuentaCorporativaRepository;
import com.arise.corp.login.entity.RolUsuario;

import tools.jackson.databind.ObjectMapper;

/**
 * Pruebas MockMvc del API de cuentas corporativas (Requirements 9.1 a 9.13,
 * 23.7 y NF-07).
 *
 * <p>Cubre 201 con {@code Location}, 200, las reglas de validacion, 404, el
 * conflicto {@code CUENTA_NIT_DUPLICADO}, el {@code PATCH} idempotente que no
 * toca los clientes, los campos controlados por el servidor y los parametros
 * de lista (Property 14).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CrmDatosPrueba.class)
class CuentaCorporativaControllerTests {

    private static final String RUTA = "/api/crm/cuentas-corporativas";
    private static final String NIT = "900123456";
    private static final String RAZON = "Acme Ficticia";
    private static final String CONTACTO = "Contacto Ficticio";
    private static final String CORREO = "contacto@example.test";
    private static final String TELEFONO = "3001234567";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CrmDatosPrueba datos;

    @Autowired
    private CuentaCorporativaRepository cuentas;

    @Autowired
    private ClienteRepository clientes;

    private UUID comercial;
    private CuentaCorporativa existente;

    @BeforeEach
    void setUp() {
        datos.limpiar();
        comercial = datos.crearPerfil(RolUsuario.COMERCIAL);
        existente = datos.crearCuenta(NIT, true);
    }

    @Test
    void crearDevuelve201ConLocationYLaAuditoriaDelActor() throws Exception {
        Map<String, Object> cuerpo = cuentaValida(
                "900.654.321-0", "Cuenta nueva");

        MvcResult resultado = mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.nit", is("9006543210")))
                .andExpect(jsonPath("$.razonSocial", is("Cuenta nueva")))
                .andExpect(jsonPath("$.activa", is(true)))
                .andExpect(jsonPath("$.actualizadoPor", is(comercial.toString())))
                .andExpect(jsonPath("$.creadoEn", notNullValue()))
                .andReturn();

        UUID creada = idDe(resultado);
        assertEquals(RUTA + "/" + creada,
                resultado.getResponse().getHeader(HttpHeaders.LOCATION));
        assertTrue(cuentas.findById(creada).orElseThrow().isActiva());
    }

    @Test
    void listarOrdenaPorRazonSocialYFiltra() throws Exception {
        mvc.perform(put(RUTA + "/{id}", existente.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuentaValida(NIT, "Zeta Ficticia"))))
                .andExpect(status().isOk());
        crear("900000001", "Alfa Ficticia");
        crear("900000002", "Beta Ficticia");

        mvc.perform(get(RUTA).with(datos.autenticacionDe(comercial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].razonSocial", is("Alfa Ficticia")))
                .andExpect(jsonPath("$.content[1].razonSocial", is("Beta Ficticia")))
                .andExpect(jsonPath("$.content[2].razonSocial", is("Zeta Ficticia")));

        mvc.perform(get(RUTA).param("sort", "nit,desc")
                        .with(datos.autenticacionDe(comercial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nit", is(NIT)));

        mvc.perform(get(RUTA).param("activa", "false")
                        .with(datos.autenticacionDe(comercial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));

        mvc.perform(get(RUTA).param("q", "beta")
                        .with(datos.autenticacionDe(comercial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].razonSocial", is("Beta Ficticia")));

        mvc.perform(get(RUTA).param("q", "900000001")
                        .with(datos.autenticacionDe(comercial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].nit", is("900000001")));
    }

    @Test
    void obtenerDevuelve200Y404() throws Exception {
        mvc.perform(get(RUTA + "/{id}", existente.getId())
                        .with(datos.autenticacionDe(comercial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(existente.getId().toString())))
                .andExpect(jsonPath("$.nit", is(NIT)));

        mvc.perform(get(RUTA + "/{id}", UUID.randomUUID())
                        .with(datos.autenticacionDe(comercial)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail",
                        is("La cuenta corporativa no existe.")));
    }

    @Test
    void actualizarReemplazaLosDatosYConservaEstadoYCreadoEn() throws Exception {
        CuentaCorporativa inactiva = datos.crearCuenta("900000009", false);
        String creadoEn = inactiva.getCreadoEn().toString();

        mvc.perform(put(RUTA + "/{id}", inactiva.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuentaValida(
                                "800.765.432-1", "Cuenta renombrada"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nit", is("8007654321")))
                .andExpect(jsonPath("$.razonSocial", is("Cuenta renombrada")))
                .andExpect(jsonPath("$.activa", is(false)))
                .andExpect(jsonPath("$.creadoEn", is(creadoEn)))
                .andExpect(jsonPath("$.actualizadoPor", is(comercial.toString())));

        mvc.perform(put(RUTA + "/{id}", UUID.randomUUID())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuentaValida("900123457", "Otra"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void devuelve409CuandoElNitPerteneceAOtraCuenta() throws Exception {
        mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuentaValida("900.123.456", "Duplicada"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("CUENTA_NIT_DUPLICADO")))
                .andExpect(jsonPath("$.campo", is("nit")));

        CuentaCorporativa otra = datos.crearCuenta("900000002", true);

        mvc.perform(put(RUTA + "/{id}", otra.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuentaValida(NIT, "Otra cuenta"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("CUENTA_NIT_DUPLICADO")))
                .andExpect(jsonPath("$.campo", is("nit")));

        assertEquals(NIT, cuentas.findById(existente.getId())
                .orElseThrow().getNit());
        assertEquals("900000002", cuentas.findById(otra.getId())
                .orElseThrow().getNit());
    }

    @Test
    void cambiarEstadoEsIdempotenteYNoTocaLosClientes() throws Exception {
        Cliente cliente = datos.crearCliente(true, existente);

        MvcResult primera = mvc.perform(patch(RUTA + "/{id}", existente.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa", is(false)))
                .andReturn();

        String actualizadoEn = json(primera).get("actualizadoEn").asString();
        String actualizadoPor = json(primera).get("actualizadoPor").asString();

        mvc.perform(patch(RUTA + "/{id}", existente.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa", is(false)))
                .andExpect(jsonPath("$.actualizadoEn", is(actualizadoEn)))
                .andExpect(jsonPath("$.actualizadoPor", is(actualizadoPor)));

        Cliente conservado = clientes.findById(cliente.getId()).orElseThrow();
        assertTrue(conservado.isActivo());
        assertNotNull(conservado.getCuentaCorporativa());
        assertEquals(existente.getId(), conservado.getCuentaCorporativa().getId());

        mvc.perform(patch(RUTA + "/{id}", existente.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa", is(true)))
                .andExpect(jsonPath("$.actualizadoEn", not(actualizadoEn)));

        mvc.perform(patch(RUTA + "/{id}", UUID.randomUUID())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":false}"))
                .andExpect(status().isNotFound());

        mvc.perform(patch(RUTA + "/{id}", existente.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ignoraLosCamposControladosPorElServidor() throws Exception {
        UUID idAjeno = UUID.randomUUID();
        Map<String, Object> cuerpo = cuentaValida("900654322", "Cuenta controlada");
        cuerpo.put("id", idAjeno.toString());
        cuerpo.put("activa", false);
        cuerpo.put("creadoEn", "2000-01-01T00:00:00Z");
        cuerpo.put("actualizadoEn", "2000-01-01T00:00:00Z");
        cuerpo.put("actualizadoPor", UUID.randomUUID().toString());

        mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(idAjeno.toString())))
                .andExpect(jsonPath("$.activa", is(true)))
                .andExpect(jsonPath("$.actualizadoPor", is(comercial.toString())));

        Map<String, Object> edicion = cuentaValida(NIT, "Cuenta editada");
        edicion.put("activa", false);

        mvc.perform(put(RUTA + "/{id}", existente.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(edicion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razonSocial", is("Cuenta editada")))
                .andExpect(jsonPath("$.activa", is(true)));
    }

    @Test
    void noHayDeleteMapeado() throws Exception {
        mvc.perform(delete(RUTA + "/{id}", existente.getId())
                        .with(datos.autenticacionDe(comercial)))
                .andExpect(status().isMethodNotAllowed());

        assertTrue(cuentas.findById(existente.getId()).isPresent());
    }

    @ParameterizedTest(name = "{0} con {1} -> 400 en {2}")
    @MethodSource("cuerposInvalidos")
    void devuelve400PorCadaReglaDeValidacion(
            String metodo,
            Map<String, Object> cuerpo,
            String campo) throws Exception {

        var peticion = "POST".equals(metodo)
                ? post(RUTA)
                : put(RUTA + "/{id}", existente.getId());

        mvc.perform(peticion
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is(campo)));
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
            casos.add(Arguments.of(metodo, sinCampo("nit"), "nit"));
            casos.add(Arguments.of(metodo, conCampo("nit", "12"), "nit"));
            casos.add(Arguments.of(metodo, conCampo("nit", "1".repeat(16)), "nit"));
            casos.add(Arguments.of(metodo, sinCampo("razonSocial"), "razonSocial"));
            casos.add(Arguments.of(metodo,
                    conCampo("razonSocial", "a".repeat(201)), "razonSocial"));
            casos.add(Arguments.of(metodo,
                    conCampo("nombreContacto", "a".repeat(201)), "nombreContacto"));
            casos.add(Arguments.of(metodo,
                    conCampo("correoContacto", "sin-arroba"), "correoContacto"));
            casos.add(Arguments.of(metodo,
                    conCampo("telefonoContacto", "12"), "telefonoContacto"));
        }

        return casos.stream();
    }

    static Stream<Arguments> parametrosDeLista() {
        return Stream.of(
                Arguments.of("", 200, null),
                Arguments.of("page=0&size=20&sort=razonSocial,asc", 200, null),
                Arguments.of("page=2&size=1&sort=nit,desc", 200, null),
                Arguments.of("size=100&sort=creadoEn,asc", 200, null),
                Arguments.of("page=-1", 400, "page"),
                Arguments.of("size=0", 400, "size"),
                Arguments.of("size=101", 400, "size"),
                Arguments.of("sort=nombre,asc", 400, "sort"),
                Arguments.of("sort=razonSocial", 400, "sort"),
                Arguments.of("sort=razonSocial,ASC", 400, "sort"),
                Arguments.of("page=abc", 400, "page"),
                Arguments.of("size=abc", 400, "size"),
                Arguments.of("activa=quizas", 400, "activa"),
                Arguments.of("activa=true", 200, null),
                Arguments.of("activa=false", 200, null),
                Arguments.of("q=" + "a".repeat(100), 200, null),
                Arguments.of("q=" + "a".repeat(101), 400, "q"));
    }

    private void crear(String nit, String razonSocial) throws Exception {
        mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuentaValida(nit, razonSocial))))
                .andExpect(status().isCreated());
    }

    private static Map<String, Object> cuentaValida(
            String nit, String razonSocial) {

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nit", nit);
        cuerpo.put("razonSocial", razonSocial);
        cuerpo.put("nombreContacto", CONTACTO);
        cuerpo.put("correoContacto", CORREO);
        cuerpo.put("telefonoContacto", TELEFONO);
        return cuerpo;
    }

    private static Map<String, Object> sinCampo(String campo) {
        Map<String, Object> cuerpo = cuentaValida(NIT, RAZON);
        cuerpo.remove(campo);
        return cuerpo;
    }

    private static Map<String, Object> conCampo(String campo, Object valor) {
        Map<String, Object> cuerpo = cuentaValida(NIT, RAZON);
        cuerpo.put(campo, valor);
        return cuerpo;
    }

    private String cuerpo(Map<String, Object> contenido) {
        return objectMapper.writeValueAsString(contenido);
    }

    private tools.jackson.databind.JsonNode json(MvcResult resultado)
            throws Exception {

        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private static UUID idDe(MvcResult resultado) throws Exception {
        String texto = resultado.getResponse().getContentAsString();
        int inicio = texto.indexOf("\"id\":\"") + "\"id\":\"".length();
        return UUID.fromString(texto.substring(inicio, texto.indexOf('"', inicio)));
    }
}
