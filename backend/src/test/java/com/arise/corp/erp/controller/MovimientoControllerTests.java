package com.arise.corp.erp.controller;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.arise.corp.erp.ErpDatosPrueba;
import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.MovimientoFinanciero;
import com.arise.corp.erp.entity.Proveedor;
import com.arise.corp.erp.entity.TipoMovimiento;
import com.arise.corp.erp.repository.MovimientoFinancieroRepository;
import com.arise.corp.login.entity.RolUsuario;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pruebas MockMvc de la API de movimientos financieros (Requirements 13, 14,
 * 15, 16, 17 y 20).
 *
 * <p>Usa H2 y JWT simulados convertidos con el converter real a partir de
 * perfiles persistidos. Cubre los campos que controla el servidor (Property
 * 6), los parametros de lista y rango (Property 15), las representaciones
 * exactas (Property 17) y la ausencia de datos personales en los errores
 * (Property 19).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({CrmDatosPrueba.class, ErpDatosPrueba.class})
class MovimientoControllerTests {

    private static final String RUTA = "/api/erp/movimientos";
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 10);
    private static final BigDecimal VALOR = new BigDecimal("2500000.50");
    private static final String CONCEPTO = "Compra de baterías para drones";
    private static final String REFERENCIA = "FAC-2026/001";

    private static final Set<String> CAMPOS_MOVIMIENTO = Set.of(
            "id", "tipo", "fecha", "concepto", "valor", "referencia", "estado",
            "cliente", "proveedor", "estadoActualizadoEn",
            "estadoActualizadoPor", "creadoEn", "actualizadoEn",
            "actualizadoPor");

    private static final Set<String> CAMPOS_ASOCIADO =
            Set.of("id", "nombre", "activo");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CrmDatosPrueba crm;

    @Autowired
    private ErpDatosPrueba datos;

    @Autowired
    private MovimientoFinancieroRepository movimientos;

    private UUID administrador;
    private UUID finanzas;

    @BeforeEach
    void setUp() {
        datos.limpiar();
        administrador = crm.crearPerfil(RolUsuario.ADMINISTRADOR);
        finanzas = crm.crearPerfil(RolUsuario.FINANZAS_RRHH);
    }

    @Test
    void crearDevuelve201ConLocationYElValorConDosDecimales() throws Exception {
        Proveedor proveedor = datos.crearProveedor();

        Map<String, Object> cuerpo = movimientoValido();
        cuerpo.put("tipo", "EGRESO");
        cuerpo.put("valor", 2500000.5);
        cuerpo.put("referencia", "  fac-2026/001  ");
        cuerpo.put("proveedorId", proveedor.getId().toString());

        MvcResult resultado = mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.estado", is("PENDIENTE")))
                .andExpect(jsonPath("$.valor", is("2500000.50")))
                .andExpect(jsonPath("$.referencia", is("FAC-2026/001")))
                .andExpect(jsonPath("$.cliente").value(nullValue()))
                .andExpect(jsonPath("$.proveedor.id",
                        is(proveedor.getId().toString())))
                .andExpect(jsonPath("$.proveedor.activo", is(true)))
                .andExpect(jsonPath("$.estadoActualizadoPor",
                        is(administrador.toString())))
                .andExpect(jsonPath("$.actualizadoPor",
                        is(administrador.toString())))
                .andReturn();

        UUID creado = idDe(resultado);
        assertEquals(RUTA + "/" + creado,
                resultado.getResponse().getHeader(HttpHeaders.LOCATION));

        MovimientoFinanciero guardado = movimientos.findById(creado).orElseThrow();
        assertEquals(EstadoMovimiento.PENDIENTE, guardado.getEstado());
        assertEquals(new BigDecimal("2500000.50"), guardado.getValor());
        assertEquals(proveedor.getId(), guardado.getProveedor().getId());
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 6: Los campos controlados por el servidor no se toman del cuerpo")
    void crearIgnoraLosCamposDelServidor() throws Exception {
        UUID idEnviado = UUID.randomUUID();
        Instant instanteEnviado = Instant.parse("2020-01-01T00:00:00Z");
        Map<String, Object> cuerpo = movimientoValido();
        cuerpo.put("id", idEnviado.toString());
        cuerpo.put("estado", "PAGADO");
        cuerpo.put("creadoEn", instanteEnviado.toString());
        cuerpo.put("actualizadoEn", instanteEnviado.toString());
        cuerpo.put("actualizadoPor", UUID.randomUUID().toString());
        cuerpo.put("estadoActualizadoEn", instanteEnviado.toString());
        cuerpo.put("estadoActualizadoPor", UUID.randomUUID().toString());
        cuerpo.put("campoDesconocido", "ignorado");

        MvcResult resultado = mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(idEnviado.toString())))
                .andExpect(jsonPath("$.estado", is("PENDIENTE")))
                .andExpect(jsonPath("$.actualizadoPor",
                        is(administrador.toString())))
                .andExpect(jsonPath("$.estadoActualizadoPor",
                        is(administrador.toString())))
                .andReturn();

        MovimientoFinanciero guardado = movimientos.findById(idDe(resultado))
                .orElseThrow();
        assertEquals(EstadoMovimiento.PENDIENTE, guardado.getEstado());
        assertFalse(guardado.getCreadoEn().equals(instanteEnviado));
    }

    @Test
    void obtenerYListarDevuelvenLaMismaRepresentacion() throws Exception {
        Proveedor proveedor = datos.crearProveedor();
        MovimientoFinanciero movimiento = datos.crearMovimiento(
                TipoMovimiento.EGRESO, EstadoMovimiento.PENDIENTE,
                ErpDatosPrueba.valor(), ErpDatosPrueba.fechaMovimiento(),
                null, proveedor);

        MvcResult detalleResultado = mvc.perform(get(RUTA + "/{id}",
                        movimiento.getId())
                        .with(crm.autenticacionDe(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo", is("EGRESO")))
                .andExpect(jsonPath("$.valor", is("2500000.50")))
                .andExpect(jsonPath("$.cliente").value(nullValue()))
                .andExpect(jsonPath("$.proveedor.id",
                        is(proveedor.getId().toString())))
                .andReturn();

        JsonNode detalle = objectMapper.readTree(
                detalleResultado.getResponse().getContentAsString());

        assertEquals(CAMPOS_MOVIMIENTO, nombres(detalle));
        assertEquals(CAMPOS_ASOCIADO, nombres(detalle.get("proveedor")));
        assertTrue(detalle.get("proveedor").get("nombre").asString()
                .startsWith("Proveedor ficticio"));

        MvcResult listaResultado = mvc.perform(get(RUTA)
                        .with(crm.autenticacionDe(finanzas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andReturn();

        JsonNode pagina = objectMapper.readTree(
                listaResultado.getResponse().getContentAsString());
        JsonNode elemento = pagina.get("content").get(0);

        assertEquals(detalle, elemento);
        assertEquals(CAMPOS_MOVIMIENTO, nombres(elemento));
    }

    @Test
    void obtenerYListarConParametrosExtraMantienenElContrato() throws Exception {
        MovimientoFinanciero movimiento = datos.crearMovimiento(
                TipoMovimiento.INGRESO, EstadoMovimiento.PENDIENTE);

        MvcResult detalle = mvc.perform(get(RUTA + "/{id}", movimiento.getId())
                        .param("extra", "ignorado")
                        .with(crm.autenticacionDe(finanzas)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode nodo = objectMapper.readTree(
                detalle.getResponse().getContentAsString());

        assertEquals(CAMPOS_MOVIMIENTO, nombres(nodo));
        assertEquals(CAMPOS_ASOCIADO, nombres(nodo.get("cliente")));
        assertEquals(movimiento.getId().toString(),
                nodo.get("id").asString());
    }

    @Test
    void listarFiltraPorTipoEstadoYRango() throws Exception {
        datos.crearMovimiento(TipoMovimiento.INGRESO, EstadoMovimiento.PENDIENTE);
        datos.crearMovimiento(TipoMovimiento.EGRESO, EstadoMovimiento.PAGADO);
        datos.crearMovimiento(TipoMovimiento.INGRESO, EstadoMovimiento.PAGADO);

        mvc.perform(get(RUTA)
                        .param("tipo", "INGRESO")
                        .param("estado", "PENDIENTE")
                        .param("desde", ErpDatosPrueba.fechaMovimiento().toString())
                        .param("hasta", ErpDatosPrueba.fechaMovimiento().toString())
                        .with(crm.autenticacionDe(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].tipo", is("INGRESO")))
                .andExpect(jsonPath("$.content[0].estado", is("PENDIENTE")));

        mvc.perform(get(RUTA)
                        .param("clienteId", UUID.randomUUID().toString())
                        .with(crm.autenticacionDe(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)))
                .andExpect(jsonPath("$.content", is(List.of())));
    }

    @ParameterizedTest(name = "POST con {0} -> 400 en {1}")
    @MethodSource("cuerposInvalidos")
    void crearDevuelve400PorCadaReglaDeValidacion(
            String caso,
            Map<String, Object> cuerpo,
            String campo) throws Exception {

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.errors[0].field", is(campo)));

        assertEquals(0, movimientos.count(), caso);
    }

    @Test
    void crearDevuelve404DeLaReferenciaConElCampo() throws Exception {
        Map<String, Object> cliente = movimientoValido();
        cliente.put("clienteId", UUID.randomUUID().toString());

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cliente)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail",
                        is("El cliente indicado no existe.")))
                .andExpect(jsonPath("$.campo", is("clienteId")));

        Map<String, Object> proveedor = movimientoValido();
        proveedor.put("tipo", "EGRESO");
        proveedor.put("proveedorId", UUID.randomUUID().toString());

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(proveedor)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail",
                        is("El proveedor indicado no existe.")))
                .andExpect(jsonPath("$.campo", is("proveedorId")));
    }

    @Test
    void crearDevuelve409CuandoLaReferenciaEstaInactiva() throws Exception {
        UUID cliente = crm.crearCliente(
                "20999999", "Cliente Secreto", false, crm.crearCuenta()).getId();

        Map<String, Object> cuerpo = movimientoValido();
        cuerpo.put("clienteId", cliente.toString());

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("CLIENTE_INACTIVO")))
                .andExpect(jsonPath("$.campo", is("clienteId")));

        Proveedor proveedor = datos.crearProveedor(false);
        Map<String, Object> egreso = movimientoValido();
        egreso.put("tipo", "EGRESO");
        egreso.put("proveedorId", proveedor.getId().toString());

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(egreso)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("PROVEEDOR_INACTIVO")))
                .andExpect(jsonPath("$.campo", is("proveedorId")));

        assertEquals(0, movimientos.count());
    }

    @Test
    void actualizarReemplazaLosDatosYConservaElEstadoYSuAuditoria()
            throws Exception {

        MovimientoFinanciero movimiento = datos.crearMovimiento(
                TipoMovimiento.INGRESO, EstadoMovimiento.PENDIENTE);
        JsonNode antes = objectMapper.readTree(mvc.perform(
                        get(RUTA + "/{id}", movimiento.getId())
                                .with(crm.autenticacionDe(administrador)))
                .andReturn().getResponse().getContentAsString());

        Map<String, Object> cuerpo = movimientoValido();
        cuerpo.put("tipo", "EGRESO");
        cuerpo.put("concepto", "  Compra nueva  ");
        cuerpo.put("valor", "99.99");
        cuerpo.put("referencia", null);
        cuerpo.put("estado", "PAGADO");
        cuerpo.put("proveedorId", datos.crearProveedor().getId().toString());

        MvcResult resultado = mvc.perform(put(RUTA + "/{id}", movimiento.getId())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo", is("EGRESO")))
                .andExpect(jsonPath("$.concepto", is("Compra nueva")))
                .andExpect(jsonPath("$.valor", is("99.99")))
                .andExpect(jsonPath("$.referencia").value(nullValue()))
                .andExpect(jsonPath("$.estado", is("PENDIENTE")))
                .andExpect(jsonPath("$.cliente").value(nullValue()))
                .andExpect(jsonPath("$.actualizadoPor",
                        is(administrador.toString())))
                .andReturn();

        JsonNode despues = objectMapper.readTree(
                resultado.getResponse().getContentAsString());

        assertEquals(antes.get("estadoActualizadoEn").asString(),
                despues.get("estadoActualizadoEn").asString());
        assertEquals(antes.get("estadoActualizadoPor").asString(),
                despues.get("estadoActualizadoPor").asString());
        assertEquals(antes.get("creadoEn").asString(),
                despues.get("creadoEn").asString());
        assertFalse(antes.get("actualizadoEn").asString()
                .equals(despues.get("actualizadoEn").asString()));
    }

    @Test
    void actualizarEliminaLaRelacionOmitida() throws Exception {
        MovimientoFinanciero movimiento = datos.crearMovimiento(
                TipoMovimiento.INGRESO, EstadoMovimiento.PENDIENTE);

        Map<String, Object> cuerpo = movimientoValido();
        cuerpo.remove("clienteId");
        cuerpo.remove("proveedorId");

        mvc.perform(put(RUTA + "/{id}", movimiento.getId())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente").value(nullValue()))
                .andExpect(jsonPath("$.proveedor").value(nullValue()));

        assertNull(movimientos.findById(movimiento.getId())
                .orElseThrow().getClienteId());
    }

    @Test
    void actualizarConservaLaAsociacionActualInactiva() throws Exception {
        UUID cliente = crm.crearCliente(
                "20999999", "Cliente Inactivo", false, crm.crearCuenta()).getId();
        MovimientoFinanciero movimiento = datos.crearMovimiento(
                TipoMovimiento.INGRESO, EstadoMovimiento.PENDIENTE,
                VALOR, FECHA, cliente, null);

        Map<String, Object> cuerpo = movimientoValido();
        cuerpo.put("clienteId", cliente.toString());
        cuerpo.put("concepto", "Concepto conservado");

        mvc.perform(put(RUTA + "/{id}", movimiento.getId())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concepto", is("Concepto conservado")))
                .andExpect(jsonPath("$.cliente.id", is(cliente.toString())))
                .andExpect(jsonPath("$.cliente.activo", is(false)));
    }

    @Test
    void actualizarDevuelve404DelMovimientoYDeLaReferencia() throws Exception {
        mvc.perform(put(RUTA + "/{id}", UUID.randomUUID())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(movimientoValido())))
                .andExpect(status().isNotFound());

        MovimientoFinanciero movimiento = datos.crearMovimiento(
                TipoMovimiento.INGRESO, EstadoMovimiento.PENDIENTE);
        Map<String, Object> cuerpo = movimientoValido();
        cuerpo.put("clienteId", UUID.randomUUID().toString());

        mvc.perform(put(RUTA + "/{id}", movimiento.getId())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.campo", is("clienteId")));
    }

    @Test
    void actualizarDevuelve409PorCadaConflicto() throws Exception {
        MovimientoFinanciero pagado = datos.crearMovimiento(
                TipoMovimiento.INGRESO, EstadoMovimiento.PAGADO);

        mvc.perform(put(RUTA + "/{id}", pagado.getId())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(movimientoValido())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("MOVIMIENTO_NO_EDITABLE")))
                .andExpect(jsonPath("$.estadoActual", is("PAGADO")));

        MovimientoFinanciero pendiente = datos.crearMovimiento(
                TipoMovimiento.INGRESO, EstadoMovimiento.PENDIENTE);
        UUID clienteInactivo = crm.crearCliente(
                "20999999", "Cliente Inactivo", false, crm.crearCuenta()).getId();
        Map<String, Object> conCliente = movimientoValido();
        conCliente.put("clienteId", clienteInactivo.toString());

        mvc.perform(put(RUTA + "/{id}", pendiente.getId())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(conCliente)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("CLIENTE_INACTIVO")))
                .andExpect(jsonPath("$.campo", is("clienteId")));

        MovimientoFinanciero egreso = datos.crearMovimiento(
                TipoMovimiento.EGRESO, EstadoMovimiento.PENDIENTE);
        Proveedor proveedorInactivo = datos.crearProveedor(false);
        Map<String, Object> conProveedor = movimientoValido();
        conProveedor.put("tipo", "EGRESO");
        conProveedor.put("proveedorId", proveedorInactivo.getId().toString());

        mvc.perform(put(RUTA + "/{id}", egreso.getId())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(conProveedor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto", is("PROVEEDOR_INACTIVO")))
                .andExpect(jsonPath("$.campo", is("proveedorId")));
    }

    @Test
    void cambiarEstadoAplicaLaTransicionYSuAuditoria() throws Exception {
        MovimientoFinanciero movimiento = datos.crearMovimiento(
                TipoMovimiento.INGRESO, EstadoMovimiento.PENDIENTE);
        JsonNode antes = objectMapper.readTree(mvc.perform(
                        get(RUTA + "/{id}", movimiento.getId())
                                .with(crm.autenticacionDe(administrador)))
                .andReturn().getResponse().getContentAsString());

        MvcResult resultado = mvc.perform(patch(
                        RUTA + "/{id}/estado", movimiento.getId())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("estado", "PAGADO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("PAGADO")))
                .andExpect(jsonPath("$.estadoActualizadoPor",
                        is(administrador.toString())))
                .andReturn();

        JsonNode despues = objectMapper.readTree(
                resultado.getResponse().getContentAsString());

        assertFalse(antes.get("estadoActualizadoEn").asString()
                .equals(despues.get("estadoActualizadoEn").asString()));
        assertEquals(antes.get("actualizadoEn").asString(),
                despues.get("actualizadoEn").asString());
        assertEquals(antes.get("actualizadoPor").asString(),
                despues.get("actualizadoPor").asString());
    }

    @Test
    void cambiarEstadoDevuelve409CuandoLaTransicionNoEsPermitida()
            throws Exception {

        MovimientoFinanciero movimiento = datos.crearMovimiento(
                TipoMovimiento.INGRESO, EstadoMovimiento.PAGADO);

        mvc.perform(patch(RUTA + "/{id}/estado", movimiento.getId())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("estado", "ANULADO"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("TRANSICION_NO_PERMITIDA")))
                .andExpect(jsonPath("$.campo", is("estado")))
                .andExpect(jsonPath("$.estadoActual", is("PAGADO")))
                .andExpect(jsonPath("$.estadoSolicitado", is("ANULADO")));

        mvc.perform(patch(RUTA + "/{id}/estado", movimiento.getId())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("estado", "PENDIENTE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicto",
                        is("TRANSICION_NO_PERMITIDA")));
    }

    @Test
    void cambiarEstadoAplicaLasMismasTransicionesConRelacionadoInactivo()
            throws Exception {

        UUID cliente = crm.crearCliente(
                "20999999", "Cliente Inactivo", false, crm.crearCuenta()).getId();
        MovimientoFinanciero movimiento = datos.crearMovimiento(
                TipoMovimiento.INGRESO, EstadoMovimiento.PENDIENTE,
                VALOR, FECHA, cliente, null);

        mvc.perform(patch(RUTA + "/{id}/estado", movimiento.getId())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("estado", "PAGADO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("PAGADO")))
                .andExpect(jsonPath("$.cliente.activo", is(false)));
    }

    @Test
    void cambiarEstadoDevuelve404Y400() throws Exception {
        mvc.perform(patch(RUTA + "/{id}/estado", UUID.randomUUID())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("estado", "PAGADO"))))
                .andExpect(status().isNotFound());

        MovimientoFinanciero movimiento = datos.crearMovimiento(
                TipoMovimiento.INGRESO, EstadoMovimiento.PENDIENTE);

        mvc.perform(patch(RUTA + "/{id}/estado", movimiento.getId())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("estado")));

        mvc.perform(patch(RUTA + "/{id}/estado", movimiento.getId())
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("estado", "PAUSADO"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("estado")));
    }

    @Test
    void deleteDevuelve405YConservaElMovimiento() throws Exception {
        MovimientoFinanciero movimiento = datos.crearMovimiento(
                TipoMovimiento.INGRESO, EstadoMovimiento.PENDIENTE);

        mvc.perform(delete(RUTA + "/{id}", movimiento.getId())
                        .with(crm.autenticacionDe(administrador)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(405)))
                .andExpect(jsonPath("$.title", is("Método no permitido")))
                .andExpect(header().string(HttpHeaders.ALLOW, allOf(
                        containsString("GET"), containsString("PUT"))));

        assertTrue(movimientos.findById(movimiento.getId()).isPresent());
    }

    @ParameterizedTest(name = "GET {0} -> {1}")
    @MethodSource("parametrosDeLista")
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 15: Los parámetros de lista y de rango se aceptan o rechazan según la regla")
    void aceptaORechazaLosParametrosDeLista(
            String consulta,
            int estado,
            String campo) throws Exception {

        var peticion = get(RUTA).with(crm.autenticacionDe(administrador));

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

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 19: Los errores del ERP no exponen información interna ni datos personales")
    void losErroresNoExponenDatosPersonalesNiDetallesInternos() throws Exception {
        UUID cliente = crm.crearCliente(
                "20999999",
                "Cliente Secreto",
                "cliente.secreto@example.test",
                "3001234567",
                "Calle Secreta 123",
                "Bogota",
                false,
                crm.crearCuenta()).getId();

        Map<String, Object> cuerpo = movimientoValido();
        cuerpo.put("clienteId", cliente.toString());

        MvcResult resultado = mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cuerpo)))
                .andExpect(status().isConflict())
                .andReturn();

        String texto = resultado.getResponse().getContentAsString();

        assertFalse(texto.contains("Cliente Secreto"), texto);
        assertFalse(texto.contains("20999999"), texto);
        assertFalse(texto.contains("cliente.secreto@example.test"), texto);
        assertFalse(texto.contains("3001234567"), texto);
        assertFalse(texto.contains("Calle Secreta 123"), texto);
        assertFalse(texto.contains("Exception"), texto);
        assertFalse(texto.contains("java."), texto);
        assertFalse(texto.contains("org.springframework"), texto);
    }

    static Stream<Arguments> cuerposInvalidos() {
        List<Arguments> casos = new ArrayList<>();

        casos.add(caso("sin tipo", sinCampo("tipo"), "tipo"));
        casos.add(caso("tipo desconocido", conCampo("tipo", "XX"), "tipo"));
        casos.add(caso("sin fecha", sinCampo("fecha"), "fecha"));
        casos.add(caso("fecha invalida",
                conCampo("fecha", "20/11/2026"), "fecha"));
        casos.add(caso("sin concepto", sinCampo("concepto"), "concepto"));
        casos.add(caso("concepto corto",
                conCampo("concepto", "ab"), "concepto"));
        casos.add(caso("concepto largo",
                conCampo("concepto", "x".repeat(301)), "concepto"));
        casos.add(caso("sin valor", sinCampo("valor"), "valor"));
        casos.add(caso("valor no numerico",
                conCampo("valor", "abc"), "valor"));
        casos.add(caso("valor cero", conCampo("valor", 0), "valor"));
        casos.add(caso("valor con tres decimales",
                conCampo("valor", new BigDecimal("1.234")), "valor"));
        casos.add(caso("referencia con ene",
                conCampo("referencia", "FAC-ñ-001"), "referencia"));
        casos.add(caso("referencia larga",
                conCampo("referencia", "X".repeat(51)), "referencia"));

        Map<String, Object> clienteEnEgreso = movimientoValido();
        clienteEnEgreso.put("tipo", "EGRESO");
        clienteEnEgreso.put("clienteId", UUID.randomUUID().toString());
        casos.add(caso("cliente en egreso", clienteEnEgreso, "clienteId"));

        Map<String, Object> proveedorEnIngreso = movimientoValido();
        proveedorEnIngreso.put("proveedorId", UUID.randomUUID().toString());
        casos.add(caso("proveedor en ingreso", proveedorEnIngreso,
                "proveedorId"));

        casos.add(caso("cliente no uuid",
                conCampo("clienteId", "abc"), "clienteId"));
        casos.add(caso("proveedor no uuid",
                conCampo("proveedorId", "abc"), "proveedorId"));

        return casos.stream();
    }

    static Stream<Arguments> parametrosDeLista() {
        return Stream.of(
                Arguments.of("tipo=OTRO", 400, "tipo"),
                Arguments.of("estado=PAUSADO", 400, "estado"),
                Arguments.of("clienteId=abc", 400, "clienteId"),
                Arguments.of("proveedorId=abc", 400, "proveedorId"),
                Arguments.of("desde=2026-13-01", 400, "desde"),
                Arguments.of("hasta=2026-13-01", 400, "hasta"),
                Arguments.of("desde=2026-12-31&hasta=2026-01-01", 400, "hasta"),
                Arguments.of("page=-1", 400, "page"),
                Arguments.of("size=0", 400, "size"),
                Arguments.of("size=101", 400, "size"),
                Arguments.of("sort=concepto,asc", 400, "sort"),
                Arguments.of("q=" + "a".repeat(101), 400, "q"),
                Arguments.of("tipo=INGRESO&estado=PENDIENTE", 200, null),
                Arguments.of("desde=2026-01-01&hasta=2026-12-31", 200, null),
                Arguments.of("page=0&size=100&sort=valor,asc", 200, null));
    }

    private static Arguments caso(
            String nombre,
            Map<String, Object> cuerpo,
            String campo) {

        return Arguments.of(nombre, cuerpo, campo);
    }

    private static Map<String, Object> movimientoValido() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipo", "INGRESO");
        cuerpo.put("fecha", FECHA.toString());
        cuerpo.put("concepto", CONCEPTO);
        cuerpo.put("valor", VALOR.toPlainString());
        cuerpo.put("referencia", REFERENCIA);
        cuerpo.put("clienteId", null);
        cuerpo.put("proveedorId", null);
        return cuerpo;
    }

    private static Map<String, Object> sinCampo(String campo) {
        Map<String, Object> cuerpo = movimientoValido();
        cuerpo.remove(campo);
        return cuerpo;
    }

    private static Map<String, Object> conCampo(String campo, Object valor) {
        Map<String, Object> cuerpo = movimientoValido();
        cuerpo.put(campo, valor);
        return cuerpo;
    }

    private String cuerpo(Map<String, Object> contenido) {
        return objectMapper.writeValueAsString(contenido);
    }

    private static UUID idDe(MvcResult resultado) throws Exception {
        return UUID.fromString(resultado.getResponse().getContentAsString()
                .split("\"id\":\"")[1].split("\"")[0]);
    }

    private static Set<String> nombres(JsonNode nodo) {
        Set<String> nombres = new LinkedHashSet<>();
        nombres.addAll(nodo.propertyNames());
        return nombres;
    }

    private static void assertNull(Object valor) {
        org.junit.jupiter.api.Assertions.assertNull(valor);
    }
}
