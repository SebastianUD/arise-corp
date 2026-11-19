package com.arise.corp.erp.controller;

import static org.hamcrest.Matchers.containsString;
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

import java.time.LocalDate;
import java.time.ZoneId;
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
import com.arise.corp.erp.entity.Empleado;
import com.arise.corp.login.entity.RolUsuario;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pruebas MockMvc de la API de empleados (Requirements 4, 5, 6, 7, 8 y 20).
 *
 * <p>Usa H2 y JWT simulados convertidos con el converter real a partir de
 * perfiles persistidos. Cubre los campos controlados por el servidor (Property
 * 6), los parametros de lista (Property 15), la representacion exacta para los
 * dos roles gestores (Property 17) y la ausencia de datos personales en los
 * errores (Property 19).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({CrmDatosPrueba.class, ErpDatosPrueba.class})
class EmpleadoControllerTests {

    private static final String RUTA = "/api/erp/empleados";
    private static final LocalDate FECHA_INGRESO = LocalDate.of(2025, 3, 1);
    private static final Set<String> CAMPOS_EMPLEADO = Set.of(
            "id", "tipoDocumento", "numeroDocumento", "nombre", "cargo", "area",
            "correo", "telefono", "fechaIngreso", "activo", "creadoEn",
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
        Map<String, Object> cuerpo = empleadoValido("10234567");
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
    void obtenerYListarDevuelvenElEmpleadoConLosFiltrosDeLista() throws Exception {
        Empleado primero = datos.crearEmpleado("10234567", "Ana Pérez", true);
        Empleado segundo = datos.crearEmpleado("AB1234", "Luis Dos", false);

        mvc.perform(get(RUTA + "/" + primero.getId())
                        .with(crm.autenticacionDe(finanzas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre", is("Ana Pérez")))
                .andExpect(jsonPath("$.numeroDocumento", is("10234567")))
                .andExpect(jsonPath("$.activo", is(true)));

        mvc.perform(get(RUTA)
                        .param("q", "ana")
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
        Map<String, Object> conLetras = empleadoValido("ABC123");
        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(conLetras)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("numeroDocumento")));

        Map<String, Object> sinNombre = empleadoValido("10234567");
        sinNombre.remove("nombre");
        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(sinNombre)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("nombre")));

        Map<String, Object> conNit = empleadoValido("10234567");
        conNit.put("tipoDocumento", "NIT");
        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(conNit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("tipoDocumento")));

        Map<String, Object> futuro = empleadoValido("10234567");
        futuro.put("fechaIngreso",
                LocalDate.now(ZoneId.of("America/Bogota")).plusDays(2).toString());
        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(futuro)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("fechaIngreso")));

        mvc.perform(get(RUTA + "/no-es-uuid")
                        .with(crm.autenticacionDe(administrador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("id")));
    }

    @Test
    void elDocumentoDuplicadoYElInactivoExistenteDevuelven409() throws Exception {
        datos.crearEmpleado("10234567", "Existente Activo", true);

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(empleadoValido("1.023.456-7"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("EMPLEADO_DUPLICADO")))
                .andExpect(jsonPath("$.campo", is("numeroDocumento")))
                .andExpect(content().string(not(containsString("10234567"))))
                .andExpect(content().string(not(containsString("Existente"))));

        datos.limpiar();
        administrador = crm.crearPerfil(RolUsuario.ADMINISTRADOR);
        Empleado inactivo = datos.crearEmpleado(
                "10234567", "Existente Inactivo", false);

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(empleadoValido("1.023.456-7"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("EMPLEADO_INACTIVO_EXISTENTE")))
                .andExpect(jsonPath("$.empleadoId",
                        is(inactivo.getId().toString())))
                .andExpect(content().string(not(containsString("10234567"))));
    }

    @Test
    void actualizarConservaElEstadoYLaFechaDeCreacionY404SiNoExiste() throws Exception {
        Empleado inactivo = datos.crearEmpleado(
                "10234567", "Empleado Inactivo", false);

        String creadoEn = objectMapper.readTree(
                        mvc.perform(get(RUTA + "/" + inactivo.getId())
                                        .with(crm.autenticacionDe(administrador)))
                                .andReturn().getResponse().getContentAsString())
                .get("creadoEn").asString();

        Map<String, Object> cuerpo = empleadoValido("10234567");
        cuerpo.put("cargo", "Cargo nuevo");

        mvc.perform(put(RUTA + "/" + inactivo.getId())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo", is(false)))
                .andExpect(jsonPath("$.cargo", is("Cargo nuevo")))
                .andExpect(jsonPath("$.creadoEn", is(creadoEn)))
                .andExpect(jsonPath("$.actualizadoPor",
                        is(administrador.toString())));

        mvc.perform(put(RUTA + "/" + UUID.randomUUID())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(
                        containsString("El empleado no existe.")));
    }

    @Test
    void cambiarEstadoEsIdempotenteYValidaElCuerpo() throws Exception {
        Empleado activo = datos.crearEmpleado("10234567", "Empleado Activo", true);

        String actualizadoEn = objectMapper.readTree(
                        mvc.perform(get(RUTA + "/" + activo.getId())
                                        .with(crm.autenticacionDe(finanzas)))
                                .andReturn().getResponse().getContentAsString())
                .get("actualizadoEn").asString();

        mvc.perform(patch(RUTA + "/" + activo.getId())
                        .with(crm.autenticacionDe(finanzas))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo", is(true)))
                .andExpect(jsonPath("$.actualizadoEn", is(actualizadoEn)));

        mvc.perform(patch(RUTA + "/" + activo.getId())
                        .with(crm.autenticacionDe(finanzas))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("activo")));
    }

    @Test
    void elDeleteResponde405ConAllow() throws Exception {
        Empleado empleado = datos.crearEmpleado("10234567", "Empleado", true);

        mvc.perform(delete(RUTA + "/" + empleado.getId())
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

        datos.crearEmpleado("10234567", "Ana Pérez", true);

        JsonNode comoAdministrador = leer(get(RUTA)
                .with(crm.autenticacionDe(administrador)));
        JsonNode comoFinanzas = leer(get(RUTA)
                .with(crm.autenticacionDe(finanzas)));

        assertEquals(comoAdministrador, comoFinanzas);

        JsonNode elemento = comoAdministrador.get("content").get(0);
        assertEquals(CAMPOS_EMPLEADO, nombres(elemento));

        JsonNode detalle = leer(get(RUTA + "/" + elemento.get("id").asString())
                .with(crm.autenticacionDe(finanzas)));
        assertEquals(CAMPOS_EMPLEADO, nombres(detalle));
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

    private Map<String, Object> empleadoValido(String documento) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipoDocumento", "CC");
        cuerpo.put("numeroDocumento", documento);
        cuerpo.put("nombre", "Ana Pérez");
        cuerpo.put("cargo", "Analista");
        cuerpo.put("area", "Finanzas");
        cuerpo.put("correo", "ana@example.test");
        cuerpo.put("telefono", "+57 300 000 0000");
        cuerpo.put("fechaIngreso", FECHA_INGRESO.toString());
        return cuerpo;
    }

    private String cuerpo(Map<String, Object> valores) {
        return objectMapper.writeValueAsString(valores);
    }
}
