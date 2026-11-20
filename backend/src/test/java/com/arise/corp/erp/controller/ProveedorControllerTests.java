package com.arise.corp.erp.controller;

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
import com.arise.corp.erp.ErpDatosPrueba;
import com.arise.corp.erp.entity.Proveedor;
import com.arise.corp.login.entity.RolUsuario;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pruebas MockMvc de la API de proveedores (Requirements 9, 10, 11, 12 y 20).
 *
 * <p>Usa H2 y JWT simulados convertidos con el converter real. Cubre los campos
 * controlados por el servidor (Property 6), los parametros de lista (Property
 * 15), la representacion exacta para los dos roles gestores (Property 17) y la
 * ausencia de datos personales en los errores (Property 19).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({CrmDatosPrueba.class, ErpDatosPrueba.class})
class ProveedorControllerTests {

    private static final String RUTA = "/api/erp/proveedores";
    private static final Set<String> CAMPOS_PROVEEDOR = Set.of(
            "id", "tipoDocumento", "numeroDocumento", "nombre", "nombreContacto",
            "correo", "telefono", "direccion", "ciudad", "activo", "creadoEn",
            "actualizadoEn", "actualizadoPor");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CrmDatosPrueba crm;

    @Autowired
    private ErpDatosPrueba datos;

    private UUID administrador;
    private UUID finanzas;

    @BeforeEach
    void setUp() {
        datos.limpiar();
        administrador = crm.crearPerfil(RolUsuario.ADMINISTRADOR);
        finanzas = crm.crearPerfil(RolUsuario.FINANZAS_RRHH);
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 6: Los campos controlados por el servidor no se toman del cuerpo")
    void crearDevuelve201ConLocationYLaAuditoriaDelActor() throws Exception {
        Map<String, Object> cuerpo = proveedorValido("900765432");
        cuerpo.put("id", UUID.randomUUID().toString());
        cuerpo.put("activo", false);
        cuerpo.put("creadoEn", "2000-01-01T00:00:00Z");
        cuerpo.put("actualizadoPor", UUID.randomUUID().toString());

        MvcResult resultado = mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.activo", is(true)))
                .andExpect(jsonPath("$.actualizadoPor",
                        is(administrador.toString())))
                .andExpect(jsonPath("$.creadoEn", not(is("2000-01-01T00:00:00Z"))))
                .andReturn();

        UUID creado = idDe(resultado);
        assertEquals(RUTA + "/" + creado,
                resultado.getResponse().getHeader(HttpHeaders.LOCATION));
    }

    @Test
    void obtenerYListarDevuelvenElProveedorConLosFiltrosDeLista() throws Exception {
        Proveedor primero = datos.crearProveedor(
                "900765432", "Suministros Andinos", true);
        Proveedor segundo = datos.crearProveedor(
                "AB1234", "Proveedor Inactivo", false);

        mvc.perform(get(RUTA + "/" + primero.getId())
                        .with(crm.autenticacionDe(finanzas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre", is("Suministros Andinos")))
                .andExpect(jsonPath("$.tipoDocumento", is("NIT")))
                .andExpect(jsonPath("$.activo", is(true)));

        mvc.perform(get(RUTA)
                        .param("q", "andinos")
                        .param("activo", "true")
                        .param("size", "10")
                        .param("sort", "nombre,asc")
                        .with(crm.autenticacionDe(finanzas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id",
                        is(primero.getId().toString())));

        mvc.perform(get(RUTA)
                        .param("activo", "false")
                        .with(crm.autenticacionDe(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id",
                        is(segundo.getId().toString())));
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 15: Los parámetros de lista y de rango se aceptan o rechazan según la regla")
    void losParametrosDeListaInvalidosDevuelven400ConElCampo() throws Exception {
        assertErrorDeParametro(get(RUTA).param("page", "-1"), "page");
        assertErrorDeParametro(get(RUTA).param("size", "101"), "size");
        assertErrorDeParametro(get(RUTA).param("sort", "correo,asc"), "sort");
        assertErrorDeParametro(get(RUTA).param("q", "x".repeat(101)), "q");
    }

    @Test
    void lasReglasDelCuerpoYElIdentificadorDevuelven400() throws Exception {
        Map<String, Object> conLetras = proveedorValido("ABC123");
        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(conLetras)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("numeroDocumento")));

        Map<String, Object> sinNombre = proveedorValido("900765432");
        sinNombre.remove("nombre");
        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(sinNombre)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("nombre")));

        Map<String, Object> conNitInvalido = proveedorValido("900765432");
        conNitInvalido.put("tipoDocumento", "XX");
        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(conNitInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("tipoDocumento")));

        Map<String, Object> conCiudadLarga = proveedorValido("900765432");
        conCiudadLarga.put("ciudad", "y".repeat(101));
        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(conCiudadLarga)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("ciudad")));

        mvc.perform(get(RUTA + "/no-es-uuid")
                        .with(crm.autenticacionDe(administrador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("id")));
    }

    @Test
    void elDocumentoDuplicadoYElInactivoExistenteDevuelven409() throws Exception {
        datos.crearProveedor("900765432", "Proveedor Activo", true);

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(proveedorValido("900.765.432"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("PROVEEDOR_DUPLICADO")))
                .andExpect(jsonPath("$.campo", is("numeroDocumento")))
                .andExpect(content().string(not(containsString("900765432"))))
                .andExpect(content().string(not(containsString("Proveedor Activo"))));

        datos.limpiar();
        administrador = crm.crearPerfil(RolUsuario.ADMINISTRADOR);
        Proveedor inactivo = datos.crearProveedor(
                "900765432", "Proveedor Inactivo", false);

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(proveedorValido("900.765.432"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("PROVEEDOR_INACTIVO_EXISTENTE")))
                .andExpect(jsonPath("$.proveedorId",
                        is(inactivo.getId().toString())))
                .andExpect(content().string(not(containsString("900765432"))));
    }

    @Test
    void actualizarDejaNulosLosOpcionalesOmiteYConservaElEstado() throws Exception {
        Proveedor proveedor = datos.crearProveedor(
                "900765432", "Proveedor Original", true);

        String creadoEn = objectMapper.readTree(
                        mvc.perform(get(RUTA + "/" + proveedor.getId())
                                        .with(crm.autenticacionDe(administrador)))
                                .andReturn().getResponse().getContentAsString())
                .get("creadoEn").asString();

        Map<String, Object> cuerpo = proveedorValido("900765432");
        cuerpo.put("nombre", "Proveedor Editado");

        mvc.perform(put(RUTA + "/" + proveedor.getId())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre", is("Proveedor Editado")))
                .andExpect(jsonPath("$.nombreContacto").value(nullValue()))
                .andExpect(jsonPath("$.direccion").value(nullValue()))
                .andExpect(jsonPath("$.ciudad").value(nullValue()))
                .andExpect(jsonPath("$.activo", is(true)))
                .andExpect(jsonPath("$.creadoEn", is(creadoEn)))
                .andExpect(jsonPath("$.actualizadoPor",
                        is(administrador.toString())));

        mvc.perform(put(RUTA + "/" + UUID.randomUUID())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(
                        containsString("El proveedor no existe.")));
    }

    @Test
    void cambiarEstadoEsIdempotenteYValidaElCuerpo() throws Exception {
        Proveedor proveedor = datos.crearProveedor(
                "900765432", "Proveedor Activo", true);

        String actualizadoEn = objectMapper.readTree(
                        mvc.perform(get(RUTA + "/" + proveedor.getId())
                                        .with(crm.autenticacionDe(finanzas)))
                                .andReturn().getResponse().getContentAsString())
                .get("actualizadoEn").asString();

        mvc.perform(patch(RUTA + "/" + proveedor.getId())
                        .with(crm.autenticacionDe(finanzas))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo", is(true)))
                .andExpect(jsonPath("$.actualizadoEn", is(actualizadoEn)));

        mvc.perform(patch(RUTA + "/" + proveedor.getId())
                        .with(crm.autenticacionDe(finanzas))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\": \"quizas\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("activo")));
    }

    @Test
    void elDeleteResponde405ConAllow() throws Exception {
        Proveedor proveedor = datos.crearProveedor(
                "900765432", "Proveedor", true);

        mvc.perform(delete(RUTA + "/" + proveedor.getId())
                        .with(crm.autenticacionDe(administrador)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("GET")))
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("PUT")))
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("PATCH")));
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 17: Las representaciones del ERP contienen exactamente los campos del contrato")
    void laRepresentacionEsLaMismaParaLosDosRolesYTraeLosTreceCampos()
            throws Exception {

        datos.crearProveedor("900765432", "Proveedor Ficticio", true);

        JsonNode comoAdministrador = leer(get(RUTA)
                .with(crm.autenticacionDe(administrador)));
        JsonNode comoFinanzas = leer(get(RUTA)
                .with(crm.autenticacionDe(finanzas)));

        assertEquals(comoAdministrador, comoFinanzas);

        JsonNode elemento = comoAdministrador.get("content").get(0);
        assertEquals(CAMPOS_PROVEEDOR, nombres(elemento));

        JsonNode detalle = leer(get(RUTA + "/" + elemento.get("id").asString())
                .with(crm.autenticacionDe(finanzas)));
        assertEquals(CAMPOS_PROVEEDOR, nombres(detalle));
    }

    private void assertErrorDeParametro(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
            String campo) throws Exception {

        mvc.perform(peticion.with(crm.autenticacionDe(administrador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is(campo)));
    }

    private JsonNode leer(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion)
            throws Exception {

        MvcResult resultado = mvc.perform(peticion)
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

    private Map<String, Object> proveedorValido(String documento) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipoDocumento", "NIT");
        cuerpo.put("numeroDocumento", documento);
        cuerpo.put("nombre", "Proveedor Ejemplo S.A.S.");
        cuerpo.put("correo", "proveedor@example.test");
        cuerpo.put("telefono", "601 000 0000");
        return cuerpo;
    }

    private String cuerpo(Map<String, Object> valores) {
        return objectMapper.writeValueAsString(valores);
    }
}
