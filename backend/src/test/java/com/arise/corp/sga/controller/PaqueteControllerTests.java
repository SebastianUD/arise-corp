package com.arise.corp.sga.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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

import java.time.Instant;
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
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.Contrato;
import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.crm.repository.ClienteRepository;
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
 * Pruebas MockMvc de la API de paquetes y su historial (Requirements 7, 8, 9,
 * 10, 11, 12, 17 y 19).
 *
 * <p>Usa H2 y {@link SgaDatosPrueba} con JWT simulados convertidos por el
 * converter real. Cubre el alta con {@code Location}, los campos controlados
 * por el servidor, las validaciones por campo, los 404 con {@code campo}, los
 * ocho conflictos del catalogo con sus extensiones, el historial como arreglo
 * ordenado, la lectura del cliente del CRM en cada consulta, el 405 con
 * {@code Allow} y la ausencia de datos sensibles en los {@code detail}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({CrmDatosPrueba.class, SgaDatosPrueba.class})
class PaqueteControllerTests {

    private static final String RUTA = "/api/sga/paquetes";
    private static final String DIRECCION = "Calle ficticia 456, Bogota";
    private static final String DESCRIPCION = "Paquete ficticio de prueba";
    private static final String OBSERVACION = "Observacion ficticia de bodega";
    private static final Set<String> CAMPOS_PAQUETE = Set.of(
            "id", "codigo", "descripcion", "direccionEntrega", "estado",
            "cliente", "contrato", "dron", "estadoActualizadoEn",
            "estadoActualizadoPor", "creadoEn", "actualizadoEn",
            "actualizadoPor");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CrmDatosPrueba crm;

    @Autowired
    private SgaDatosPrueba datos;

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private DronRepository drones;

    private UUID operaciones;

    @BeforeEach
    void setUp() {
        datos.limpiar();
        operaciones = crm.crearPerfil(RolUsuario.OPERACIONES);
    }

    @Test
    void crearDevuelve201ConLocationEIgnoraElEstadoYElDron() throws Exception {
        Cliente cliente = crm.crearCliente(crm.crearCuenta());
        Dron dron = datos.crearDron("DRN-900");

        Map<String, Object> cuerpo = paquete(" paq-900 ", cliente.getId());
        cuerpo.put("id", UUID.randomUUID().toString());
        cuerpo.put("estado", "EN_TRANSITO");
        cuerpo.put("dronId", dron.getId().toString());
        cuerpo.put("creadoEn", "2000-01-01T00:00:00Z");
        cuerpo.put("actualizadoPor", UUID.randomUUID().toString());
        cuerpo.put("observacion", OBSERVACION);

        MvcResult resultado = mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo", is("PAQ-900")))
                .andExpect(jsonPath("$.descripcion", is(DESCRIPCION)))
                .andExpect(jsonPath("$.direccionEntrega", is(DIRECCION)))
                .andExpect(jsonPath("$.estado", is("REGISTRADO")))
                .andExpect(jsonPath("$.dron").value(nullValue()))
                .andExpect(jsonPath("$.contrato").value(nullValue()))
                .andExpect(jsonPath("$.cliente.id",
                        is(cliente.getId().toString())))
                .andExpect(jsonPath("$.cliente.nombre", is(cliente.getNombre())))
                .andExpect(jsonPath("$.cliente.activo", is(true)))
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

        mvc.perform(get(RUTA + "/" + creado + "/estados")
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].estadoAnterior").value(nullValue()))
                .andExpect(jsonPath("$[0].estadoNuevo", is("REGISTRADO")))
                .andExpect(jsonPath("$[0].dron").value(nullValue()))
                .andExpect(jsonPath("$[0].observacion", is(OBSERVACION)))
                .andExpect(jsonPath("$[0].registradoPor",
                        is(operaciones.toString())));
    }

    @Test
    void crearConContratoActivoYListarConLosFiltros() throws Exception {
        Cliente cliente = crm.crearCliente(crm.crearCuenta());
        Contrato contrato = crm.crearContrato(cliente, EstadoContrato.ACTIVO);

        Map<String, Object> cuerpo = paquete("PAQ-901", cliente.getId());
        cuerpo.put("contratoId", contrato.getId().toString());

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contrato.id",
                        is(contrato.getId().toString())))
                .andExpect(jsonPath("$.contrato.codigo",
                        is(contrato.getCodigo())))
                .andExpect(jsonPath("$.contrato.estado", is("ACTIVO")));

        mvc.perform(get(RUTA)
                        .param("estado", "REGISTRADO")
                        .param("clienteId", cliente.getId().toString())
                        .param("contratoId", contrato.getId().toString())
                        .param("q", "PAQ-901")
                        .param("size", "10")
                        .param("sort", "codigo,asc")
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].codigo", is("PAQ-901")))
                .andExpect(jsonPath("$.content[0].contrato.estado",
                        is("ACTIVO")));

        mvc.perform(get(RUTA)
                        .param("estado", "ENTREGADO")
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    void losCuerposYParametrosInvalidosDevuelven400ConElCampo()
            throws Exception {

        Cliente cliente = crm.crearCliente(crm.crearCuenta());
        Paquete paquete = datos.crearPaquete(cliente, null,
                EstadoPaquete.REGISTRADO);

        assertErrorDeCuerpo(post(RUTA), paquete("PAQ-902", cliente.getId()),
                "codigo", cuerpo -> cuerpo.remove("codigo"));
        assertErrorDeCuerpo(post(RUTA), paquete("ab", cliente.getId()),
                "codigo", cuerpo -> { });
        assertErrorDeCuerpo(post(RUTA), paquete("PAQ-903", cliente.getId()),
                "descripcion", cuerpo -> cuerpo.put("descripcion", "d".repeat(501)));
        assertErrorDeCuerpo(post(RUTA), paquete("PAQ-904", cliente.getId()),
                "direccionEntrega",
                cuerpo -> cuerpo.put("direccionEntrega", "d".repeat(201)));
        assertErrorDeCuerpo(post(RUTA), paquete("PAQ-905", cliente.getId()),
                "clienteId", cuerpo -> cuerpo.remove("clienteId"));
        assertErrorDeCuerpo(post(RUTA), paquete("PAQ-906", cliente.getId()),
                "clienteId", cuerpo -> cuerpo.put("clienteId", "no-es-uuid"));

        assertErrorDeCuerpo(put(RUTA + "/" + paquete.getId()),
                edicion(DIRECCION), "descripcion",
                cuerpo -> cuerpo.remove("descripcion"));
        assertErrorDeCuerpo(put(RUTA + "/" + paquete.getId()),
                edicion(DIRECCION), "direccionEntrega",
                cuerpo -> cuerpo.put("direccionEntrega", "d".repeat(201)));

        assertErrorDeCuerpo(post(RUTA + "/" + paquete.getId() + "/estados"),
                transicion("ASIGNADO", null), "dronId", cuerpo -> { });
        assertErrorDeCuerpo(post(RUTA + "/" + paquete.getId() + "/estados"),
                transicion("ENTREGADO", UUID.randomUUID()), "dronId",
                cuerpo -> { });
        assertErrorDeCuerpo(post(RUTA + "/" + paquete.getId() + "/estados"),
                transicion("PERDIDO", null), "estado", cuerpo -> { });
        assertErrorDeCuerpo(post(RUTA + "/" + paquete.getId() + "/estados"),
                transicion("CANCELADO", null), "observacion",
                cuerpo -> cuerpo.put("observacion", "o".repeat(501)));

        mvc.perform(get(RUTA + "/no-es-uuid")
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("id")));

        assertErrorDeParametro(get(RUTA).param("page", "-1"), "page");
        assertErrorDeParametro(get(RUTA).param("size", "101"), "size");
        assertErrorDeParametro(get(RUTA).param("sort", "correo,asc"), "sort");
        assertErrorDeParametro(get(RUTA).param("q", "x".repeat(101)), "q");
        assertErrorDeParametro(get(RUTA).param("estado", "PERDIDO"), "estado");
        assertErrorDeParametro(get(RUTA).param("clienteId", "no-es-uuid"),
                "clienteId");
    }

    @Test
    void losRecursosInexistentesDevuelven404ConElCampo() throws Exception {
        UUID inexistente = UUID.randomUUID();
        Cliente cliente = crm.crearCliente(crm.crearCuenta());
        Paquete paquete = datos.crearPaquete(cliente, null,
                EstadoPaquete.REGISTRADO);

        mvc.perform(get(RUTA + "/" + inexistente)
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(
                        containsString("El paquete no existe.")));

        mvc.perform(get(RUTA + "/" + inexistente + "/estados")
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(
                        containsString("El paquete no existe.")));

        mvc.perform(put(RUTA + "/" + inexistente)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(edicion(DIRECCION))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(
                        containsString("El paquete no existe.")));

        mvc.perform(post(RUTA + "/" + inexistente + "/estados")
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(transicion("CANCELADO", null))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(
                        containsString("El paquete no existe.")));

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(paquete("PAQ-907", inexistente))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.campo", is("clienteId")))
                .andExpect(jsonPath("$.detail", is(
                        "El cliente indicado no existe.")));

        Map<String, Object> conContrato = paquete("PAQ-908", cliente.getId());
        conContrato.put("contratoId", inexistente.toString());

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(conContrato)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.campo", is("contratoId")))
                .andExpect(jsonPath("$.detail", is(
                        "El contrato indicado no existe.")));

        mvc.perform(post(RUTA + "/" + paquete.getId() + "/estados")
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(transicion("ASIGNADO", inexistente))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.campo", is("dronId")))
                .andExpect(jsonPath("$.detail", is(
                        "El dron indicado no existe.")));
    }

    @Test
    void cadaConflictoDelCatalogoDevuelveSuCodigoCampoYExtensiones()
            throws Exception {

        Cliente cliente = crm.crearCliente(crm.crearCuenta());
        Cliente otroCliente = crm.crearCliente(crm.crearCuenta());
        Paquete existente = datos.crearPaquete(cliente, null,
                EstadoPaquete.REGISTRADO);

        Map<String, Object> duplicado =
                paquete(" " + existente.getCodigo().toLowerCase() + " ",
                        cliente.getId());
        duplicado.put("direccionEntrega", DIRECCION);
        duplicado.put("observacion", OBSERVACION);

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(duplicado)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("PAQUETE_CODIGO_DUPLICADO")))
                .andExpect(jsonPath("$.campo", is("codigo")))
                .andExpect(jsonPath("$.detail", is(
                        "Ya existe un paquete registrado con este código.")))
                .andExpect(content().string(not(containsString(DIRECCION))))
                .andExpect(content().string(not(containsString(OBSERVACION))))
                .andExpect(content().string(
                        not(containsString(cliente.getNombre()))));

        Cliente inactivo = crm.crearCliente(false, crm.crearCuenta());

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(paquete("PAQ-910", inactivo.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("CLIENTE_INACTIVO")))
                .andExpect(jsonPath("$.campo", is("clienteId")));

        Contrato ajeno = crm.crearContrato(otroCliente, EstadoContrato.ACTIVO);
        Map<String, Object> deOtro = paquete("PAQ-911", cliente.getId());
        deOtro.put("contratoId", ajeno.getId().toString());

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(deOtro)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("CONTRATO_DE_OTRO_CLIENTE")))
                .andExpect(jsonPath("$.campo", is("contratoId")));

        Contrato borrador = crm.crearContrato(cliente, EstadoContrato.BORRADOR);
        Map<String, Object> noActivo = paquete("PAQ-912", cliente.getId());
        noActivo.put("contratoId", borrador.getId().toString());

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(noActivo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("CONTRATO_NO_ACTIVO")))
                .andExpect(jsonPath("$.campo", is("contratoId")))
                .andExpect(jsonPath("$.estadoActual", is("BORRADOR")));

        Paquete entregado = datos.crearPaquete(
                cliente, null, EstadoPaquete.ENTREGADO);

        mvc.perform(put(RUTA + "/" + entregado.getId())
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(edicion(DIRECCION))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("PAQUETE_NO_EDITABLE")))
                .andExpect(jsonPath("$.campo").doesNotExist())
                .andExpect(jsonPath("$.estadoActual", is("ENTREGADO")))
                .andExpect(content().string(not(containsString(DIRECCION))));

        mvc.perform(post(RUTA + "/" + existente.getId() + "/estados")
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(transicion("ENTREGADO", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("TRANSICION_NO_PERMITIDA")))
                .andExpect(jsonPath("$.campo", is("estado")))
                .andExpect(jsonPath("$.estadoActual", is("REGISTRADO")))
                .andExpect(jsonPath("$.estadoSolicitado", is("ENTREGADO")));

        Dron enMantenimiento = datos.crearDron(EstadoDron.MANTENIMIENTO);

        mvc.perform(post(RUTA + "/" + existente.getId() + "/estados")
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(transicion(
                                "ASIGNADO", enMantenimiento.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("DRON_NO_DISPONIBLE")))
                .andExpect(jsonPath("$.campo", is("dronId")))
                .andExpect(jsonPath("$.estadoActual", is("MANTENIMIENTO")));

        Paquete ocupado = datos.crearPaquete(
                cliente, null, EstadoPaquete.ASIGNADO);
        Paquete libre = datos.crearPaquete(
                cliente, null, EstadoPaquete.REGISTRADO);

        mvc.perform(post(RUTA + "/" + libre.getId() + "/estados")
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(transicion(
                                "ASIGNADO", ocupado.getDron().getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("DRON_CON_PAQUETE_EN_CURSO")))
                .andExpect(jsonPath("$.campo", is("dronId")))
                .andExpect(jsonPath("$.paqueteId",
                        is(ocupado.getId().toString())));
    }

    @Test
    void elCambioDeEstadoDevuelve201ConLocationYElRegistroDelHistorial()
            throws Exception {

        Cliente cliente = crm.crearCliente(crm.crearCuenta());
        Paquete paquete = datos.crearPaquete(cliente, null,
                EstadoPaquete.REGISTRADO);
        Dron dron = datos.crearDron("DRN-901");

        Map<String, Object> cuerpo = transicion("ASIGNADO", dron.getId());
        cuerpo.put("observacion", OBSERVACION);

        mvc.perform(post(RUTA + "/" + paquete.getId() + "/estados")
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        is(RUTA + "/" + paquete.getId() + "/estados")))
                .andExpect(jsonPath("$.estadoAnterior", is("REGISTRADO")))
                .andExpect(jsonPath("$.estadoNuevo", is("ASIGNADO")))
                .andExpect(jsonPath("$.dron.id", is(dron.getId().toString())))
                .andExpect(jsonPath("$.dron.codigo", is("DRN-901")))
                .andExpect(jsonPath("$.observacion", is(OBSERVACION)))
                .andExpect(jsonPath("$.registradoPor",
                        is(operaciones.toString())));

        mvc.perform(get(RUTA + "/" + paquete.getId())
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("ASIGNADO")))
                .andExpect(jsonPath("$.dron.id", is(dron.getId().toString())));
    }

    @Test
    void elHistorialEsUnArregloOrdenadoPorSecuencia() throws Exception {
        Paquete paquete = datos.crearPaquete(EstadoPaquete.EN_TRANSITO);
        Dron dron = drones.findById(paquete.getDron().getId()).orElseThrow();

        mvc.perform(get(RUTA + "/" + paquete.getId() + "/estados")
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].estadoAnterior").value(nullValue()))
                .andExpect(jsonPath("$[0].estadoNuevo", is("REGISTRADO")))
                .andExpect(jsonPath("$[1].estadoAnterior", is("REGISTRADO")))
                .andExpect(jsonPath("$[1].estadoNuevo", is("ASIGNADO")))
                .andExpect(jsonPath("$[1].dron.codigo",
                        is(dron.getCodigo())))
                .andExpect(jsonPath("$[2].estadoAnterior", is("ASIGNADO")))
                .andExpect(jsonPath("$[2].estadoNuevo", is("EN_TRANSITO")))
                .andExpect(jsonPath("$[2].dron").value(nullValue()));
    }

    @Test
    void elClienteSeLeeDelCrmEnCadaConsulta() throws Exception {
        Cliente cliente = crm.crearCliente(crm.crearCuenta());
        Paquete paquete = datos.crearPaquete(cliente, null,
                EstadoPaquete.REGISTRADO);

        mvc.perform(get(RUTA + "/" + paquete.getId())
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.nombre",
                        is(cliente.getNombre())))
                .andExpect(jsonPath("$.cliente", is(Map.of(
                        "id", cliente.getId().toString(),
                        "nombre", cliente.getNombre(),
                        "activo", true))));

        String nombreNuevo = "Cliente Renombrado " + cliente.getNumeroDocumento();
        cliente.actualizarDatos(
                cliente.getTipoDocumento(),
                cliente.getNumeroDocumento(),
                nombreNuevo,
                cliente.getCorreo(),
                cliente.getTelefono(),
                cliente.getDireccion(),
                cliente.getCiudad(),
                cliente.getCuentaCorporativa(),
                CrmDatosPrueba.ACTOR_ID,
                Instant.now());
        clientes.save(cliente);

        mvc.perform(get(RUTA + "/" + paquete.getId())
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.nombre", is(nombreNuevo)))
                .andExpect(jsonPath("$.cliente", is(Map.of(
                        "id", cliente.getId().toString(),
                        "nombre", nombreNuevo,
                        "activo", true))));

        mvc.perform(get(RUTA)
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].cliente.nombre",
                        is(nombreNuevo)));
    }

    @Test
    void losMetodosNoAdmitidosDevuelven405ConAllow() throws Exception {
        Paquete paquete = datos.crearPaquete();

        mvc.perform(delete(RUTA + "/" + paquete.getId())
                        .with(crm.autenticacionDe(operaciones)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("GET")))
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("PUT")))
                .andExpect(jsonPath("$.detail", is(
                        "La operación no está disponible para este recurso.")));

        String estados = RUTA + "/" + paquete.getId() + "/estados";

        mvc.perform(put(estados)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(transicion("CANCELADO", null))))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("GET")))
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("POST")));

        mvc.perform(patch(estados)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(transicion("CANCELADO", null))))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("GET")))
                .andExpect(header().string(HttpHeaders.ALLOW,
                        containsString("POST")));

        mvc.perform(delete(estados)
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
    void laRepresentacionTieneLosCamposDelContratoEnListaYEnDetalle()
            throws Exception {

        Paquete paquete = datos.crearPaquete(EstadoPaquete.ASIGNADO);

        JsonNode detalle = leer(get(RUTA + "/" + paquete.getId()));
        JsonNode elemento = leer(get(RUTA)).get("content").get(0);

        assertEquals(CAMPOS_PAQUETE, nombres(detalle));
        assertEquals(CAMPOS_PAQUETE, nombres(elemento));
        assertEquals(Set.of("id", "nombre", "activo"),
                nombres(detalle.get("cliente")));
        assertEquals(Set.of("id", "codigo", "estado"),
                nombres(detalle.get("dron")));
        assertEquals(detalle, elemento);
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

    private Map<String, Object> paquete(String codigo, UUID clienteId) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("codigo", codigo);
        cuerpo.put("descripcion", DESCRIPCION);
        cuerpo.put("direccionEntrega", DIRECCION);
        cuerpo.put("clienteId", clienteId.toString());
        return cuerpo;
    }

    private Map<String, Object> edicion(String direccionEntrega) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("descripcion", DESCRIPCION);
        cuerpo.put("direccionEntrega", direccionEntrega);
        return cuerpo;
    }

    private Map<String, Object> transicion(String estado, UUID dronId) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("estado", estado);

        if (dronId != null) {
            cuerpo.put("dronId", dronId.toString());
        }

        return cuerpo;
    }

    private String cuerpo(Map<String, Object> valores) {
        return objectMapper.writeValueAsString(valores);
    }
}
