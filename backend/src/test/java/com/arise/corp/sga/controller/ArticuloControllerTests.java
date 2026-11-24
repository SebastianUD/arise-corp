package com.arise.corp.sga.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.arise.corp.crm.CrmDatosPrueba;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.sga.SgaDatosPrueba;
import com.arise.corp.sga.entity.ArticuloInventario;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pruebas MockMvc de la API de articulos e inventario (Requirements 13, 14, 15
 * y 19).
 *
 * <p>Usa H2 y {@link SgaDatosPrueba} con JWT simulados convertidos por el
 * converter real. Cubre el alta con {@code Location}, los campos controlados
 * por el servidor, las validaciones por campo (incluida una cantidad con
 * decimales), los 404, los conflictos de codigo y de existencia con sus
 * extensiones, el alta de movimientos con {@code Location}, el 405 con
 * {@code Allow} y {@code bajoStockMinimo}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({CrmDatosPrueba.class, SgaDatosPrueba.class})
class ArticuloControllerTests {

    private static final String RUTA = "/api/sga/articulos";
    private static final String NOMBRE = "Bateria ficticia de repuesto";
    private static final Set<String> CAMPOS_ARTICULO = Set.of(
            "id", "codigo", "nombre", "categoria", "unidad", "stockMinimo",
            "existencia", "bajoStockMinimo", "creadoEn", "actualizadoEn",
            "actualizadoPor");
    private static final Set<String> CAMPOS_MOVIMIENTO = Set.of(
            "id", "articuloId", "tipo", "cantidad", "existenciaAnterior",
            "existenciaResultante", "observacion", "registradoEn",
            "registradoPor");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CrmDatosPrueba crm;

    @Autowired
    private SgaDatosPrueba datos;

    private UUID operaciones;

    @BeforeEach
    void setUp() {
        datos.limpiar();
        operaciones = crm.crearPerfil(RolUsuario.OPERACIONES);
    }

    @Test
    void crearDevuelve201ConLocationEIgnoraLaExistencia() throws Exception {
        Map<String, Object> cuerpo = articulo(" art-900 ", 10);
        cuerpo.put("id", UUID.randomUUID().toString());
        cuerpo.put("existencia", 999);
        cuerpo.put("creadoEn", "2000-01-01T00:00:00Z");
        cuerpo.put("actualizadoPor", UUID.randomUUID().toString());

        MvcResult resultado = mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo", is("ART-900")))
                .andExpect(jsonPath("$.nombre", is(NOMBRE)))
                .andExpect(jsonPath("$.categoria", is("REPUESTO")))
                .andExpect(jsonPath("$.unidad", is("UNIDAD")))
                .andExpect(jsonPath("$.stockMinimo", is(10)))
                .andExpect(jsonPath("$.existencia", is(0)))
                .andExpect(jsonPath("$.bajoStockMinimo", is(true)))
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
    void listarFiltraPorCategoriaBajoStockYTexto() throws Exception {
        ArticuloInventario bajo = datos.crearArticulo(10, 0L);
        datos.crearArticulo(0, 0L);

        mvc.perform(get(RUTA)
                        .param("bajoStockMinimo", "true")
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id",
                        is(bajo.getId().toString())));

        mvc.perform(get(RUTA)
                        .param("bajoStockMinimo", "false")
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));

        mvc.perform(get(RUTA)
                        .param("categoria", "REPUESTO")
                        .param("sort", "existencia,asc")
                        .param("size", "10")
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)));

        mvc.perform(get(RUTA)
                        .param("q", bajo.getCodigo())
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].codigo",
                        is(bajo.getCodigo())));

        mvc.perform(get(RUTA)
                        .param("categoria", "BATERIA")
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    void losCuerposYParametrosInvalidosDevuelven400ConElCampo()
            throws Exception {

        ArticuloInventario articulo = datos.crearArticulo();

        assertErrorDeCuerpo(post(RUTA), articulo("ART-901", 10), "codigo",
                cuerpo -> cuerpo.remove("codigo"));
        assertErrorDeCuerpo(post(RUTA), articulo("ab", 10), "codigo",
                cuerpo -> { });
        assertErrorDeCuerpo(post(RUTA), articulo("ART-902", 10), "nombre",
                cuerpo -> cuerpo.put("nombre", "n".repeat(201)));
        assertErrorDeCuerpo(post(RUTA), articulo("ART-903", 10), "categoria",
                cuerpo -> cuerpo.remove("categoria"));
        assertErrorDeCuerpo(post(RUTA), articulo("ART-904", 10), "unidad",
                cuerpo -> cuerpo.remove("unidad"));
        assertErrorDeCuerpo(post(RUTA), articulo("ART-905", 10), "stockMinimo",
                cuerpo -> cuerpo.put("stockMinimo", 1.5));
        assertErrorDeCuerpo(post(RUTA), articulo("ART-906", 10), "stockMinimo",
                cuerpo -> cuerpo.put("stockMinimo", -1));
        assertErrorDeCuerpo(post(RUTA), articulo("ART-907", 10), "stockMinimo",
                cuerpo -> cuerpo.put("stockMinimo", 1_000_001));

        String movimientos = RUTA + "/" + articulo.getId() + "/movimientos";

        assertErrorDeCuerpo(post(movimientos), movimiento("ENTRADA", 5),
                "cantidad", cuerpo -> cuerpo.put("cantidad", 1.5));
        assertErrorDeCuerpo(post(movimientos), movimiento("ENTRADA", 5),
                "tipo", cuerpo -> cuerpo.remove("tipo"));
        assertErrorDeCuerpo(post(movimientos), movimiento("SALIDA", 0),
                "cantidad", cuerpo -> { });
        assertErrorDeCuerpo(post(movimientos), movimiento("AJUSTE", 5),
                "observacion", cuerpo -> cuerpo.remove("observacion"));
        assertErrorDeCuerpo(post(movimientos), movimiento("ENTRADA", 5),
                "observacion",
                cuerpo -> cuerpo.put("observacion", "o".repeat(501)));

        mvc.perform(get(RUTA + "/no-es-uuid")
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("id")));

        assertErrorDeParametro(get(RUTA).param("page", "-1"), "page");
        assertErrorDeParametro(get(RUTA).param("size", "101"), "size");
        assertErrorDeParametro(get(RUTA).param("sort", "descripcion,asc"),
                "sort");
        assertErrorDeParametro(get(RUTA).param("q", "x".repeat(101)), "q");
        assertErrorDeParametro(get(RUTA).param("categoria", "PERDIDO"),
                "categoria");
        assertErrorDeParametro(get(RUTA).param("bajoStockMinimo", "quizas"),
                "bajoStockMinimo");
        assertErrorDeParametro(
                get(movimientos).param("sort", "nombre,asc"), "sort");
    }

    @Test
    void losRecursosInexistentesDevuelven404() throws Exception {
        UUID inexistente = UUID.randomUUID();

        mvc.perform(get(RUTA + "/" + inexistente)
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(
                        containsString("El artículo no existe.")));

        mvc.perform(put(RUTA + "/" + inexistente)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(articulo("ART-908", 10))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(
                        containsString("El artículo no existe.")));

        mvc.perform(get(RUTA + "/" + inexistente + "/movimientos")
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(
                        containsString("El artículo no existe.")));

        mvc.perform(post(RUTA + "/" + inexistente + "/movimientos")
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(movimiento("ENTRADA", 5))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(
                        containsString("El artículo no existe.")));
    }

    @Test
    void elCodigoDuplicadoYLaExistenciaInsuficienteDevuelven409()
            throws Exception {

        ArticuloInventario existente = datos.crearArticulo(10, 3L);

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(articulo(
                                " " + existente.getCodigo().toLowerCase() + " ",
                                10))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("ARTICULO_CODIGO_DUPLICADO")))
                .andExpect(jsonPath("$.campo", is("codigo")))
                .andExpect(jsonPath("$.detail", is(
                        "Ya existe un artículo registrado con este código.")));

        mvc.perform(post(RUTA + "/" + existente.getId() + "/movimientos")
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(movimiento("SALIDA", 5))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("EXISTENCIA_INSUFICIENTE")))
                .andExpect(jsonPath("$.campo", is("cantidad")))
                .andExpect(jsonPath("$.existenciaActual", is(3)))
                .andExpect(jsonPath("$.cantidadSolicitada", is(5)))
                .andExpect(content().string(not(containsString(
                        existente.getNombre()))));

        mvc.perform(get(RUTA + "/" + existente.getId())
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existencia", is(3)));
    }

    @Test
    void registrarMovimientoDevuelve201ConLocationYActualizaLaExistencia()
            throws Exception {

        ArticuloInventario articulo = datos.crearArticulo(10, 3L);

        Map<String, Object> cuerpo = movimiento("ENTRADA", 7);
        cuerpo.put("observacion", "Compra ficticia");

        mvc.perform(post(RUTA + "/" + articulo.getId() + "/movimientos")
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        is(RUTA + "/" + articulo.getId() + "/movimientos")))
                .andExpect(jsonPath("$.articuloId",
                        is(articulo.getId().toString())))
                .andExpect(jsonPath("$.tipo", is("ENTRADA")))
                .andExpect(jsonPath("$.cantidad", is(7)))
                .andExpect(jsonPath("$.existenciaAnterior", is(3)))
                .andExpect(jsonPath("$.existenciaResultante", is(10)))
                .andExpect(jsonPath("$.observacion", is("Compra ficticia")))
                .andExpect(jsonPath("$.registradoPor",
                        is(operaciones.toString())));

        mvc.perform(get(RUTA + "/" + articulo.getId())
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existencia", is(10)))
                .andExpect(jsonPath("$.bajoStockMinimo", is(false)));

        mvc.perform(get(RUTA + "/" + articulo.getId() + "/movimientos")
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content[0].tipo", is("ENTRADA")))
                .andExpect(jsonPath("$.content[0].existenciaResultante",
                        is(10)));
    }

    @Test
    void losMetodosNoAdmitidosDevuelven405ConAllow() throws Exception {
        ArticuloInventario articulo = datos.crearArticulo();

        mvc.perform(delete(RUTA + "/" + articulo.getId())
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("GET")))
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("PUT")))
                .andExpect(jsonPath("$.detail", is(
                        "La operación no está disponible para este recurso.")));

        String movimientos = RUTA + "/" + articulo.getId() + "/movimientos";

        mvc.perform(put(movimientos)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(movimiento("ENTRADA", 5))))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("GET")))
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("POST")));

        mvc.perform(patch(movimientos)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(movimiento("ENTRADA", 5))))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("GET")))
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("POST")));

        mvc.perform(delete(movimientos)
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("GET")))
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("POST")));
    }

    @Test
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 17: Las representaciones del SGA contienen exactamente los campos del contrato")
    void laRepresentacionTieneLosCamposDelContrato() throws Exception {
        ArticuloInventario articulo = datos.crearArticulo(10, 5L);

        JsonNode detalle = leer(get(RUTA + "/" + articulo.getId()));
        JsonNode elemento = leer(get(RUTA)).get("content").get(0);
        JsonNode movimientos = leer(
                get(RUTA + "/" + articulo.getId() + "/movimientos"));

        assertEquals(CAMPOS_ARTICULO, nombres(detalle));
        assertEquals(CAMPOS_ARTICULO, nombres(elemento));
        assertEquals(detalle, elemento);
        assertEquals(CAMPOS_MOVIMIENTO,
                nombres(movimientos.get("content").get(0)));
        assertEquals(5, detalle.get("existencia").asInt());
        assertEquals(true, detalle.get("bajoStockMinimo").asBoolean());
    }

    private void assertErrorDeCuerpo(
            MockHttpServletRequestBuilder peticion,
            Map<String, Object> base,
            String campo,
            java.util.function.Consumer<Map<String, Object>> ajuste)
            throws Exception {

        Map<String, Object> cuerpo = new LinkedHashMap<>(base);
        ajuste.accept(cuerpo);

        mvc.perform(peticion
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is(campo)));
    }

    private void assertErrorDeParametro(
            MockHttpServletRequestBuilder peticion,
            String campo) throws Exception {

        mvc.perform(peticion.with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is(campo)));
    }

    private JsonNode leer(MockHttpServletRequestBuilder peticion)
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

    private Map<String, Object> articulo(String codigo, int stockMinimo) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("codigo", codigo);
        cuerpo.put("nombre", NOMBRE);
        cuerpo.put("categoria", "REPUESTO");
        cuerpo.put("unidad", "UNIDAD");
        cuerpo.put("stockMinimo", stockMinimo);
        return cuerpo;
    }

    private Map<String, Object> movimiento(String tipo, int cantidad) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipo", tipo);
        cuerpo.put("cantidad", cantidad);
        return cuerpo;
    }

    private String cuerpo(Map<String, Object> valores) {
        return objectMapper.writeValueAsString(valores);
    }
}
