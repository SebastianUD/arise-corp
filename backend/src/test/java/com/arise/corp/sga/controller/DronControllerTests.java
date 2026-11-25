package com.arise.corp.sga.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.arise.corp.crm.CrmDatosPrueba;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.sga.SgaDatosPrueba;
import com.arise.corp.sga.entity.Dron;
import com.arise.corp.sga.entity.EstadoDron;
import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.entity.Paquete;
import com.arise.corp.sga.repository.DronRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pruebas MockMvc de la API de drones (Requirements 3.2, 4, 5.7-5.9, 6 y 19).
 *
 * <p>Usa H2 y {@link SgaDatosPrueba} con JWT simulados convertidos por el
 * converter real. Cubre el alta con {@code Location}, los campos controlados por
 * el servidor, las validaciones por campo, los 404, los conflictos de código y
 * de estado, el {@code paqueteEnCurso} de la lista y del detalle y el 405 del
 * {@code DELETE}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({CrmDatosPrueba.class, SgaDatosPrueba.class})
class DronControllerTests {

    private static final String RUTA = "/api/sga/drones";
    private static final String MODELO = "Arise X4 Carga ligera";
    private static final Set<String> CAMPOS_DRON = Set.of(
            "id", "codigo", "modelo", "observaciones", "estado",
            "paqueteEnCurso", "estadoActualizadoEn", "estadoActualizadoPor",
            "creadoEn", "actualizadoEn", "actualizadoPor");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CrmDatosPrueba crm;

    @Autowired
    private SgaDatosPrueba datos;

    @Autowired
    private DronRepository drones;

    private UUID operaciones;

    @BeforeEach
    void setUp() {
        datos.limpiar();
        operaciones = crm.crearPerfil(RolUsuario.OPERACIONES);
    }

    @Test
    void crearDevuelve201ConLocationYLosCamposDelServidor() throws Exception {
        Map<String, Object> cuerpo = dronValido(" drn-100 ");
        cuerpo.put("id", UUID.randomUUID().toString());
        cuerpo.put("estado", "EN_MISION");
        cuerpo.put("creadoEn", "2000-01-01T00:00:00Z");
        cuerpo.put("actualizadoPor", UUID.randomUUID().toString());

        MvcResult resultado = mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo", is("DRN-100")))
                .andExpect(jsonPath("$.modelo", is(MODELO)))
                .andExpect(jsonPath("$.estado", is("DISPONIBLE")))
                .andExpect(jsonPath("$.paqueteEnCurso").value(nullValue()))
                .andExpect(jsonPath("$.estadoActualizadoPor",
                        is(operaciones.toString())))
                .andExpect(jsonPath("$.actualizadoPor",
                        is(operaciones.toString())))
                .andExpect(jsonPath("$.creadoEn",
                        not(is("2000-01-01T00:00:00Z"))))
                .andReturn();

        UUID creado = idDe(resultado);
        assertEquals(RUTA + "/" + creado,
                resultado.getResponse().getHeader(HttpHeaders.LOCATION));
    }

    @Test
    void obtenerYListarTraenElPaqueteEnCursoYLosFiltros() throws Exception {
        Paquete paquete = datos.crearPaquete(EstadoPaquete.ASIGNADO);
        Dron dron = drones.findById(paquete.getDron().getId()).orElseThrow();

        mvc.perform(get(RUTA + "/" + dron.getId())
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(dron.getId().toString())))
                .andExpect(jsonPath("$.paqueteEnCurso.id",
                        is(paquete.getId().toString())))
                .andExpect(jsonPath("$.paqueteEnCurso.codigo",
                        is(paquete.getCodigo())))
                .andExpect(jsonPath("$.paqueteEnCurso.estado",
                        is("ASIGNADO")));

        mvc.perform(get(RUTA)
                        .param("q", dron.getCodigo())
                        .param("estado", "DISPONIBLE")
                        .param("asignable", "false")
                        .param("size", "10")
                        .param("sort", "codigo,asc")
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id",
                        is(dron.getId().toString())))
                .andExpect(jsonPath("$.content[0].paqueteEnCurso.id",
                        is(paquete.getId().toString())));

        mvc.perform(get(RUTA)
                        .param("asignable", "true")
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    void losCuerposYParametrosInvalidosDevuelven400ConElCampo()
            throws Exception {

        assertErrorDeCuerpo(dronValido("DRN-101"), "codigo",
                cuerpo -> cuerpo.remove("codigo"));
        assertErrorDeCuerpo(dronValido("ab"), "codigo", cuerpo -> { });
        assertErrorDeCuerpo(dronValido("DRN-102"), "modelo",
                cuerpo -> cuerpo.put("modelo", "m".repeat(101)));
        assertErrorDeCuerpo(dronValido("DRN-103"), "observaciones",
                cuerpo -> cuerpo.put("observaciones", "o".repeat(501)));

        Dron dron = datos.crearDron("DRN-104");

        mvc.perform(patch(RUTA + "/" + dron.getId() + "/estado")
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"PERDIDO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("estado")))
                .andExpect(jsonPath("$.errors[0].message",
                        is("debe ser uno de: DISPONIBLE, EN_MISION, "
                                + "MANTENIMIENTO, FUERA_DE_SERVICIO")));

        mvc.perform(get(RUTA + "/no-es-uuid")
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("id")));

        assertErrorDeParametro(get(RUTA).param("page", "-1"), "page");
        assertErrorDeParametro(get(RUTA).param("size", "101"), "size");
        assertErrorDeParametro(get(RUTA).param("sort", "correo,asc"), "sort");
        assertErrorDeParametro(get(RUTA).param("q", "x".repeat(101)), "q");
        assertErrorDeParametro(get(RUTA).param("estado", "PERDIDO"), "estado");
        assertErrorDeParametro(get(RUTA).param("asignable", "quizas"),
                "asignable");
    }

    @Test
    void losRecursosInexistentesDevuelven404() throws Exception {
        UUID inexistente = UUID.randomUUID();

        mvc.perform(get(RUTA + "/" + inexistente)
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(
                        containsString("El dron no existe.")));

        mvc.perform(put(RUTA + "/" + inexistente)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(dronValido("DRN-105"))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(
                        containsString("El dron no existe.")));

        mvc.perform(patch(RUTA + "/" + inexistente + "/estado")
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"MANTENIMIENTO\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(
                        containsString("El dron no existe.")));
    }

    @Test
    void elCodigoDuplicadoSeComparaNormalizado() throws Exception {
        Dron existente = datos.crearDron("DRN-106");
        Dron otro = datos.crearDron("DRN-107");

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(dronValido(" drn-106 "))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("DRON_CODIGO_DUPLICADO")))
                .andExpect(jsonPath("$.campo", is("codigo")))
                .andExpect(jsonPath("$.detail", is(
                        "Ya existe un dron registrado con este código.")))
                .andExpect(content().string(not(containsString(
                        existente.getCodigo()))));

        mvc.perform(put(RUTA + "/" + otro.getId())
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(dronValido("drn-106"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("DRON_CODIGO_DUPLICADO")))
                .andExpect(jsonPath("$.campo", is("codigo")));
    }

    @Test
    void lasTransicionesManualesInvalidasYElPaqueteEnCursoDevuelven409()
            throws Exception {

        Dron enMision = datos.crearDron(EstadoDron.EN_MISION);
        Dron disponible = datos.crearDron("DRN-108");

        mvc.perform(patch(RUTA + "/" + enMision.getId() + "/estado")
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"MANTENIMIENTO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("TRANSICION_DRON_NO_PERMITIDA")))
                .andExpect(jsonPath("$.campo", is("estado")))
                .andExpect(jsonPath("$.estadoActual", is("EN_MISION")))
                .andExpect(jsonPath("$.estadoSolicitado",
                        is("MANTENIMIENTO")));

        mvc.perform(patch(RUTA + "/" + disponible.getId() + "/estado")
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"DISPONIBLE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("TRANSICION_DRON_NO_PERMITIDA")))
                .andExpect(jsonPath("$.estadoActual", is("DISPONIBLE")))
                .andExpect(jsonPath("$.estadoSolicitado",
                        is("DISPONIBLE")));

        Paquete paquete = datos.crearPaquete(EstadoPaquete.ASIGNADO);
        Dron ocupado = paquete.getDron();

        mvc.perform(patch(RUTA + "/" + ocupado.getId() + "/estado")
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"MANTENIMIENTO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("DRON_CON_PAQUETE_EN_CURSO")))
                .andExpect(jsonPath("$.campo").doesNotExist())
                .andExpect(jsonPath("$.paqueteId",
                        is(paquete.getId().toString())));
    }

    @Test
    void elCambioManualValidoDevuelve200ConLaAuditoriaDelEstado()
            throws Exception {

        Dron dron = datos.crearDron("DRN-109");
        String actualizadoEn = leer(get(RUTA + "/" + dron.getId()))
                .get("actualizadoEn").asString();

        mvc.perform(patch(RUTA + "/" + dron.getId() + "/estado")
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"MANTENIMIENTO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("MANTENIMIENTO")))
                .andExpect(jsonPath("$.estadoActualizadoPor",
                        is(operaciones.toString())))
                .andExpect(jsonPath("$.actualizadoEn", is(actualizadoEn)))
                .andExpect(jsonPath("$.actualizadoPor",
                        is(dron.getActualizadoPor().toString())));
    }

    @Test
    void actualizarConservaElEstadoYLaFechaDeCreacion() throws Exception {
        Dron dron = datos.crearDron("DRN-110");
        JsonNode original = leer(get(RUTA + "/" + dron.getId()));

        Map<String, Object> cuerpo = dronValido("DRN-110");
        cuerpo.put("modelo", "Modelo editado");

        mvc.perform(put(RUTA + "/" + dron.getId())
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo", is("Modelo editado")))
                .andExpect(jsonPath("$.observaciones").value(nullValue()))
                .andExpect(jsonPath("$.estado", is("DISPONIBLE")))
                .andExpect(jsonPath("$.creadoEn",
                        is(original.get("creadoEn").asString())))
                .andExpect(jsonPath("$.actualizadoPor",
                        is(operaciones.toString())));
    }

    @Test
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 17: Las representaciones del SGA contienen exactamente los campos del contrato")
    void laRepresentacionTieneLosOnceCamposEnListaYEnDetalle()
            throws Exception {

        Dron dron = datos.crearDron("DRN-111");

        JsonNode detalle = leer(get(RUTA + "/" + dron.getId()));
        JsonNode elemento = leer(get(RUTA)).get("content").get(0);

        assertEquals(CAMPOS_DRON, nombres(detalle));
        assertEquals(CAMPOS_DRON, nombres(elemento));
        assertEquals(detalle, elemento);
    }

    @Test
    void elDeleteResponde405ConAllow() throws Exception {
        Dron dron = datos.crearDron("DRN-112");

        mvc.perform(delete(RUTA + "/" + dron.getId())
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("GET")))
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("PUT")))
                .andExpect(jsonPath("$.detail", is(
                        "La operación no está disponible para este recurso.")));
    }

    private void assertErrorDeCuerpo(
            Map<String, Object> base,
            String campo,
            java.util.function.Consumer<Map<String, Object>> ajuste)
            throws Exception {

        Map<String, Object> cuerpo = new LinkedHashMap<>(base);
        ajuste.accept(cuerpo);

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is(campo)));
    }

    private void assertErrorDeParametro(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
            String campo) throws Exception {

        mvc.perform(peticion.with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is(campo)));
    }

    private JsonNode leer(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion)
            throws Exception {

        MvcResult resultado = mvc.perform(peticion
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(
                resultado.getResponse().getContentAsString());
    }

    private Set<String> nombres(JsonNode nodo) {
        Set<String> nombres = new LinkedHashSet<>();
        nodo.propertyNames().forEach(nombres::add);
        return nombres;
    }

    private UUID idDe(MvcResult resultado) throws Exception {
        return UUID.fromString(objectMapper.readTree(
                        resultado.getResponse().getContentAsString())
                .get("id").asString());
    }

    private Map<String, Object> dronValido(String codigo) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("codigo", codigo);
        cuerpo.put("modelo", MODELO);
        return cuerpo;
    }

    private String cuerpo(Map<String, Object> valores) {
        return objectMapper.writeValueAsString(valores);
    }
}
