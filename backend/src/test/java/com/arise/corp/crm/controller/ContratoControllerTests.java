package com.arise.corp.crm.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.crm.repository.ContratoRepository;
import com.arise.corp.login.entity.RolUsuario;

import tools.jackson.databind.ObjectMapper;

/**
 * Pruebas MockMvc del API de contratos (Requirements 11 a 14, 23.7).
 *
 * <p>Cubre el alta en BORRADOR con {@code Location}, las validaciones por
 * campo, los 404 de la ruta y de {@code clienteId}, los cuatro conflictos, el
 * formato de {@code valor}, los campos del servidor ignorados (Property 6) y los
 * parametros de lista (Property 14).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CrmDatosPrueba.class)
class ContratoControllerTests {

    private static final String RUTA = "/api/crm/contratos";
    private static final String CODIGO = "CTR-2026-001";
    private static final String DESCRIPCION = "Contrato ficticio";
    private static final String INICIO = "2026-01-01";
    private static final String FIN = "2026-12-31";
    private static final String VALOR = "1234.50";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CrmDatosPrueba datos;

    @Autowired
    private ContratoRepository contratos;

    private UUID comercial;
    private UUID operaciones;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        datos.limpiar();
        comercial = datos.crearPerfil(RolUsuario.COMERCIAL);
        operaciones = datos.crearPerfil(RolUsuario.OPERACIONES);
        cliente = datos.crearCliente(
                datos.siguienteDocumento(), "Cliente ficticio", true, null);
    }

    @Test
    void crearDevuelve201ConLocationYEstadoBorrador() throws Exception {
        MvcResult resultado = mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(crearValido())))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.codigo", is(CODIGO)))
                .andExpect(jsonPath("$.descripcion", is(DESCRIPCION)))
                .andExpect(jsonPath("$.estado", is("BORRADOR")))
                .andExpect(jsonPath("$.valor", is(VALOR)))
                .andExpect(jsonPath("$.cliente.id", is(cliente.getId().toString())))
                .andExpect(jsonPath("$.estadoActualizadoPor",
                        is(comercial.toString())))
                .andExpect(jsonPath("$.creadoEn", notNullValue()))
                .andReturn();

        UUID creado = idDe(resultado);
        assertEquals(RUTA + "/" + creado,
                resultado.getResponse().getHeader(HttpHeaders.LOCATION));
        assertEquals(EstadoContrato.BORRADOR,
                contratos.findById(creado).orElseThrow().getEstado());
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 6: Los campos controlados por el servidor no se toman del cuerpo")
    void crearIgnoraElEstadoYLosCamposControladosPorElServidor() throws Exception {
        Map<String, Object> cuerpo = crearValido();
        UUID idAjeno = UUID.randomUUID();
        cuerpo.put("id", idAjeno.toString());
        cuerpo.put("estado", "FINALIZADO");
        cuerpo.put("estadoActualizadoEn", "2000-01-01T00:00:00Z");
        cuerpo.put("estadoActualizadoPor", UUID.randomUUID().toString());
        cuerpo.put("creadoEn", "2000-01-01T00:00:00Z");
        cuerpo.put("actualizadoEn", "2000-01-01T00:00:00Z");
        cuerpo.put("actualizadoPor", UUID.randomUUID().toString());

        mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(idAjeno.toString())))
                .andExpect(jsonPath("$.estado", is("BORRADOR")))
                .andExpect(jsonPath("$.estadoActualizadoPor",
                        is(comercial.toString())))
                .andExpect(jsonPath("$.creadoEn",
                        not("2000-01-01T00:00:00Z")));
    }

    @Test
    void listarDevuelveLosContratosConFiltrosYOrdenPorDefecto() throws Exception {
        Cliente otro = datos.crearCliente(
                datos.siguienteDocumento(), "Otro cliente", true, null);
        Contrato primero = datos.crearContrato(cliente, EstadoContrato.ACTIVO);
        datos.crearContrato(otro, EstadoContrato.BORRADOR);

        mvc.perform(get(RUTA)
                        .with(datos.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(2)))
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.size", is(20)));

        mvc.perform(get(RUTA)
                        .param("clienteId", cliente.getId().toString())
                        .with(datos.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(1)))
                .andExpect(jsonPath("$.content[0].id",
                        is(primero.getId().toString())));

        mvc.perform(get(RUTA).param("estado", "ACTIVO")
                        .with(datos.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(1)))
                .andExpect(jsonPath("$.content[0].id",
                        is(primero.getId().toString())));

        mvc.perform(get(RUTA)
                        .param("clienteId", UUID.randomUUID().toString())
                        .with(datos.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(0)));
    }

    @Test
    void obtenerDevuelve200Y404SiNoExiste() throws Exception {
        Contrato contrato = datos.crearContrato(cliente);

        mvc.perform(get(RUTA + "/{id}", contrato.getId())
                        .with(datos.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo", is(contrato.getCodigo())))
                .andExpect(jsonPath("$.valor", is(VALOR)));

        mvc.perform(get(RUTA + "/{id}", UUID.randomUUID())
                        .with(datos.autenticacionDe(operaciones)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title", is("Recurso no encontrado")))
                .andExpect(jsonPath("$.detail", is("El contrato no existe.")));
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
    void devuelve400ConUnEstadoDesconocidoEnElCambioDeEstado() throws Exception {
        Contrato contrato = datos.crearContrato(cliente);

        mvc.perform(patch(RUTA + "/{id}/estado", contrato.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"PAUSADO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("estado")));

        mvc.perform(patch(RUTA + "/{id}/estado", contrato.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("estado")));
    }

    @Test
    void devuelve404DeLaRutaYDelClienteConSuCampo() throws Exception {
        mvc.perform(get(RUTA + "/{id}", UUID.randomUUID())
                        .with(datos.autenticacionDe(comercial)))
                .andExpect(status().isNotFound());

        mvc.perform(put(RUTA + "/{id}", UUID.randomUUID())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(actualizarValido())))
                .andExpect(status().isNotFound());

        mvc.perform(patch(RUTA + "/{id}/estado", UUID.randomUUID())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ACTIVO\"}"))
                .andExpect(status().isNotFound());

        Map<String, Object> cuerpo = crearValido();
        cuerpo.put("clienteId", UUID.randomUUID().toString());

        mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", is("El cliente no existe.")))
                .andExpect(jsonPath("$.campo", is("clienteId")));
    }

    @Test
    void devuelve409CuandoElClienteEstaInactivo() throws Exception {
        Cliente inactivo = datos.crearCliente(
                datos.siguienteDocumento(), "Cliente inactivo", false, null);
        Map<String, Object> cuerpo = crearValido();
        cuerpo.put("clienteId", inactivo.getId().toString());

        mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("CLIENTE_INACTIVO_SIN_CONTRATOS")))
                .andExpect(jsonPath("$.campo", is("clienteId")));

        assertEquals(0, contratos.count());
    }

    @Test
    void devuelve409PorCodigoDuplicadoAlCrearYAlEditar() throws Exception {
        Contrato existente = datos.crearContrato(
                CODIGO, cliente, EstadoContrato.BORRADOR);
        Contrato editable = datos.crearContrato(cliente);

        mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(crearValido())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("CONTRATO_CODIGO_DUPLICADO")))
                .andExpect(jsonPath("$.campo", is("codigo")));

        Map<String, Object> cuerpo = actualizarValido();
        cuerpo.put("codigo", "  " + CODIGO.toLowerCase() + "  ");

        mvc.perform(put(RUTA + "/{id}", editable.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("CONTRATO_CODIGO_DUPLICADO")));

        assertEquals(CODIGO, contratos.findById(existente.getId())
                .orElseThrow().getCodigo());
    }

    @Test
    void devuelve409CuandoElContratoNoEsEditable() throws Exception {
        Contrato contrato = datos.crearContrato(
                cliente, EstadoContrato.ACTIVO);
        String codigoOriginal = contrato.getCodigo();

        mvc.perform(put(RUTA + "/{id}", contrato.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(actualizarValido())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("CONTRATO_NO_EDITABLE")))
                .andExpect(jsonPath("$.campo").doesNotExist())
                .andExpect(jsonPath("$.estadoActual", is("ACTIVO")));

        assertEquals(codigoOriginal, contratos.findById(contrato.getId())
                .orElseThrow().getCodigo());
    }

    @Test
    void cambiarEstadoAceptaSoloLasTransicionesPermitidas() throws Exception {
        Contrato contrato = datos.crearContrato(
                cliente, EstadoContrato.BORRADOR);
        String datosActualizadoEn = contrato.getActualizadoEn().toString();

        MvcResult resultado = mvc.perform(
                        patch(RUTA + "/{id}/estado", contrato.getId())
                                .with(datos.autenticacionDe(comercial))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"estado\":\"ACTIVO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("ACTIVO")))
                .andExpect(jsonPath("$.estadoActualizadoPor",
                        is(comercial.toString())))
                .andExpect(jsonPath("$.actualizadoEn",
                        is(datosActualizadoEn)))
                .andReturn();

        assertEquals("ACTIVO", objectMapper.readTree(
                resultado.getResponse().getContentAsString())
                .get("estado").asString());

        mvc.perform(patch(RUTA + "/{id}/estado", contrato.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"BORRADOR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("TRANSICION_NO_PERMITIDA")))
                .andExpect(jsonPath("$.campo", is("estado")))
                .andExpect(jsonPath("$.estadoActual", is("ACTIVO")))
                .andExpect(jsonPath("$.estadoSolicitado", is("BORRADOR")));

        assertEquals(EstadoContrato.ACTIVO,
                contratos.findById(contrato.getId()).orElseThrow().getEstado());
    }

    @Test
    void actualizarConservaElClienteYElEstadoEIgnoraElCuerpo() throws Exception {
        Cliente otro = datos.crearCliente(
                datos.siguienteDocumento(), "Otro cliente", true, null);
        Contrato contrato = datos.crearContrato(
                cliente, EstadoContrato.BORRADOR);

        Map<String, Object> cuerpo = actualizarValido();
        cuerpo.put("codigo", " ctr-nuevo-001 ");
        cuerpo.put("clienteId", otro.getId().toString());
        cuerpo.put("estado", "FINALIZADO");

        mvc.perform(put(RUTA + "/{id}", contrato.getId())
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo", is("CTR-NUEVO-001")))
                .andExpect(jsonPath("$.cliente.id",
                        is(cliente.getId().toString())))
                .andExpect(jsonPath("$.estado", is("BORRADOR")))
                .andExpect(jsonPath("$.valor", is("999.90")));

        Contrato actualizado = contratos.findById(contrato.getId())
                .orElseThrow();

        assertEquals(cliente.getId(), actualizado.getCliente().getId());
        assertEquals(EstadoContrato.BORRADOR, actualizado.getEstado());
        assertEquals("CTR-NUEVO-001", actualizado.getCodigo());
    }

    @Test
    void elValorSeSerializaConDosDecimales() throws Exception {
        Map<String, Object> cuerpo = crearValido();
        cuerpo.put("valor", "1500000.5");

        mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valor", is("1500000.50")));
    }

    @ParameterizedTest(name = "GET {0} -> {1}")
    @MethodSource("parametrosDeLista")
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 14: Los parámetros de lista se aceptan o rechazan según DP-API-03")
    void aceptaORechazaLosParametrosDeLista(
            String consulta,
            int estado,
            String campo) throws Exception {

        var peticion = get(RUTA).with(datos.autenticacionDe(operaciones));

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
            Map<String, Object> base = "POST".equals(metodo)
                    ? crearBase()
                    : actualizarBase();

            casos.add(Arguments.of(metodo, sinCampo(base, "codigo"), "codigo"));
            casos.add(Arguments.of(metodo, conCampo(base, "codigo", "ab"), "codigo"));
            casos.add(Arguments.of(metodo, conCampo(base, "codigo", "CTR_2026"), "codigo"));
            casos.add(Arguments.of(metodo, conCampo(base, "codigo", "A".repeat(31)), "codigo"));
            casos.add(Arguments.of(metodo, sinCampo(base, "descripcion"), "descripcion"));
            casos.add(Arguments.of(metodo, conCampo(base, "descripcion", "a".repeat(1001)), "descripcion"));
            casos.add(Arguments.of(metodo, sinCampo(base, "fechaInicio"), "fechaInicio"));
            casos.add(Arguments.of(metodo, conCampo(base, "fechaInicio", "31/12/2026"), "fechaInicio"));
            casos.add(Arguments.of(metodo, conCampo(base, "fechaFin", "2025-12-31"), "fechaFin"));
            casos.add(Arguments.of(metodo, sinCampo(base, "valor"), "valor"));
            casos.add(Arguments.of(metodo, conCampo(base, "valor", "-1"), "valor"));
            casos.add(Arguments.of(metodo, conCampo(base, "valor", "1.999"), "valor"));
            casos.add(Arguments.of(metodo, conCampo(base, "valor", "abc"), "valor"));
        }

        casos.add(Arguments.of("POST", sinCampo(crearBase(), "clienteId"), "clienteId"));
        casos.add(Arguments.of("POST",
                conCampo(crearBase(), "clienteId", "no-es-uuid"), "clienteId"));

        return casos.stream();
    }

    static Stream<Arguments> parametrosDeLista() {
        return Stream.of(
                Arguments.of("", 200, null),
                Arguments.of("page=0&size=20&sort=creadoEn,desc", 200, null),
                Arguments.of("size=1&sort=codigo,asc", 200, null),
                Arguments.of("size=100&sort=fechaInicio,asc", 200, null),
                Arguments.of("page=-1", 400, "page"),
                Arguments.of("size=0", 400, "size"),
                Arguments.of("size=101", 400, "size"),
                Arguments.of("sort=valor,asc", 400, "sort"),
                Arguments.of("sort=codigo", 400, "sort"),
                Arguments.of("sort=codigo,ASC", 400, "sort"),
                Arguments.of("page=abc", 400, "page"),
                Arguments.of("size=abc", 400, "size"),
                Arguments.of("clienteId=abc", 400, "clienteId"),
                Arguments.of("estado=PAUSADO", 400, "estado"),
                Arguments.of("estado=ACTIVO", 200, null));
    }

    private Map<String, Object> crearValido() {
        Map<String, Object> cuerpo = crearBase();
        cuerpo.put("clienteId", cliente.getId().toString());
        return cuerpo;
    }

    private static Map<String, Object> crearBase() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("clienteId", UUID.randomUUID().toString());
        cuerpo.put("codigo", CODIGO);
        cuerpo.put("descripcion", DESCRIPCION);
        cuerpo.put("fechaInicio", INICIO);
        cuerpo.put("fechaFin", FIN);
        cuerpo.put("valor", VALOR);
        return cuerpo;
    }

    private static Map<String, Object> actualizarBase() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("codigo", CODIGO);
        cuerpo.put("descripcion", DESCRIPCION);
        cuerpo.put("fechaInicio", INICIO);
        cuerpo.put("fechaFin", FIN);
        cuerpo.put("valor", "999.90");
        return cuerpo;
    }

    private static Map<String, Object> sinCampo(
            Map<String, Object> base, String campo) {
        Map<String, Object> cuerpo = new LinkedHashMap<>(base);
        cuerpo.remove(campo);
        return cuerpo;
    }

    private static Map<String, Object> conCampo(
            Map<String, Object> base, String campo, Object valor) {
        Map<String, Object> cuerpo = new LinkedHashMap<>(base);
        cuerpo.put(campo, valor);
        return cuerpo;
    }

    private static Map<String, Object> actualizarValido() {
        return actualizarBase();
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
