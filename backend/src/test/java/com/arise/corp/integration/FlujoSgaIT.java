package com.arise.corp.integration;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.arise.corp.config.PerfilJwtAuthenticationConverter;
import com.arise.corp.login.AutenticacionPerfilTestSupport;
import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.login.repository.PerfilUsuarioRepository;
import com.arise.corp.sga.entity.ArticuloInventario;
import com.arise.corp.sga.entity.CategoriaArticulo;
import com.arise.corp.sga.entity.Dron;
import com.arise.corp.sga.entity.EstadoDron;
import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.entity.HistorialEstadoPaquete;
import com.arise.corp.sga.entity.Paquete;
import com.arise.corp.sga.entity.TipoMovimientoInventario;
import com.arise.corp.sga.entity.UnidadArticulo;
import com.arise.corp.sga.repository.ArticuloInventarioRepository;
import com.arise.corp.sga.repository.DronRepository;
import com.arise.corp.sga.repository.HistorialEstadoPaqueteRepository;
import com.arise.corp.sga.repository.PaqueteRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Flujo completo del SGA por MockMvc contra PostgreSQL efimero (Requirements
 * 8.8, 26.5, 26.6 y 26.9, CA-04, CA-05, ENG-03).
 *
 * <p>Recorre el alta de un cliente y un contrato por COMERCIAL, el alta de un
 * dron y un paquete por OPERACIONES, las transiciones hasta {@code ENTREGADO}
 * con su historial encadenado y los conteos del resumen. Tambien cubre los
 * recorridos {@code EN_TRANSITO -> DEVUELTO} y {@code ASIGNADO -> CANCELADO},
 * los filtros de paquetes, drones asignables y articulos bajo stock minimo, y
 * la relectura del nombre del cliente desde el CRM.
 */
class FlujoSgaIT extends PostgresIntegracionBase {

    private static final String RUTA_CUENTAS = "/api/crm/cuentas-corporativas";
    private static final String RUTA_CLIENTES = "/api/crm/clientes";
    private static final String RUTA_CONTRATOS = "/api/crm/contratos";
    private static final String RUTA_DRONES = "/api/sga/drones";
    private static final String RUTA_PAQUETES = "/api/sga/paquetes";
    private static final String RUTA_ARTICULOS = "/api/sga/articulos";
    private static final String RUTA_RESUMEN = "/api/sga/resumen";

    private static final Instant INSTANTE = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PerfilJwtAuthenticationConverter converter;

    @Autowired
    private PerfilUsuarioRepository perfiles;

    @Autowired
    private DronRepository drones;

    @Autowired
    private PaqueteRepository paquetes;

    @Autowired
    private HistorialEstadoPaqueteRepository historial;

    @Autowired
    private ArticuloInventarioRepository articulos;

    private UUID operaciones;
    private RequestPostProcessor authComercial;
    private RequestPostProcessor authOperaciones;

    @BeforeEach
    void prepararSesion() {
        UUID comercial = crearPerfil(RolUsuario.COMERCIAL);
        operaciones = crearPerfil(RolUsuario.OPERACIONES);

        authComercial = AutenticacionPerfilTestSupport.como(
                converter, comercial.toString(), "comercial.sga@example.test");
        authOperaciones = AutenticacionPerfilTestSupport.como(
                converter, operaciones.toString(), "operaciones.sga@example.test");
    }

    @Test
    @DisplayName("El flujo cliente, contrato, dron y paquete con historial se persiste en PostgreSQL")
    void recorreElFlujoCompletoDelSgaPorMockMvc() throws Exception {
        UUID cuentaId = crearCuenta("900300001");
        UUID clienteId = crearCliente(cuentaId, "10203040", "Cliente del flujo SGA");
        UUID contratoId = crearContratoActivo(clienteId, "CTR-SGA-001");
        UUID dronId = crearDronApi("DRN-SGA-001");
        UUID paqueteId = crearPaqueteApi("PAQ-SGA-001", clienteId, contratoId);

        mvc.perform(get(RUTA_PAQUETES + "/{id}", paqueteId).with(authOperaciones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("REGISTRADO")))
                .andExpect(jsonPath("$.cliente.nombre", is("Cliente del flujo SGA")))
                .andExpect(jsonPath("$.contrato.codigo", is("CTR-SGA-001")))
                .andExpect(jsonPath("$.dron").value(nullValue()));

        assertEquals("DISPONIBLE", estadoDron(dronId),
                "un paquete REGISTRADO no cambia el estado del dron");

        transicion(paqueteId, dronId, "ASIGNADO");
        assertEquals("DISPONIBLE", estadoDron(dronId),
                "la asignacion conserva el dron disponible hasta el transito");

        transicion(paqueteId, null, "EN_TRANSITO");
        assertEquals("EN_MISION", estadoDron(dronId));

        transicion(paqueteId, null, "ENTREGADO");
        assertEquals("DISPONIBLE", estadoDron(dronId));

        mvc.perform(get(RUTA_PAQUETES + "/{id}/estados", paqueteId).with(authOperaciones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].estadoAnterior").value(nullValue()))
                .andExpect(jsonPath("$[0].estadoNuevo", is("REGISTRADO")))
                .andExpect(jsonPath("$[1].estadoAnterior", is("REGISTRADO")))
                .andExpect(jsonPath("$[1].estadoNuevo", is("ASIGNADO")))
                .andExpect(jsonPath("$[1].dron.id", is(dronId.toString())))
                .andExpect(jsonPath("$[2].estadoAnterior", is("ASIGNADO")))
                .andExpect(jsonPath("$[2].estadoNuevo", is("EN_TRANSITO")))
                .andExpect(jsonPath("$[2].dron").value(nullValue()))
                .andExpect(jsonPath("$[3].estadoAnterior", is("EN_TRANSITO")))
                .andExpect(jsonPath("$[3].estadoNuevo", is("ENTREGADO")))
                .andExpect(jsonPath("$[0].registradoPor", is(operaciones.toString())))
                .andExpect(jsonPath("$[1].registradoPor", is(operaciones.toString())))
                .andExpect(jsonPath("$[2].registradoPor", is(operaciones.toString())))
                .andExpect(jsonPath("$[3].registradoPor", is(operaciones.toString())));

        mvc.perform(get(RUTA_RESUMEN).with(authOperaciones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paquetes.porEstado.ENTREGADO", is(1)))
                .andExpect(jsonPath("$.paquetes.total", is(1)))
                .andExpect(jsonPath("$.drones.porEstado.DISPONIBLE", is(1)))
                .andExpect(jsonPath("$.drones.total", is(1)))
                .andExpect(jsonPath("$.articulos.bajoStockMinimo", is(0)))
                .andExpect(jsonPath("$.articulos.total", is(0)));
    }

    @Test
    @DisplayName("Los recorridos DEVUELTO y CANCELADO liberan el dron para una nueva asignacion")
    void losRecorridosDevueltoYCanceladoLiberanElDron() throws Exception {
        UUID clienteId = crearCliente(
                crearCuenta("900300002"), "10203041", "Cliente de recorridos SGA");

        UUID dronDevuelto = crearDronApi("DRN-SGA-DEV");
        UUID paqueteDevuelto = crearPaqueteApi("PAQ-SGA-DEV", clienteId, null);
        transicion(paqueteDevuelto, dronDevuelto, "ASIGNADO");
        transicion(paqueteDevuelto, null, "EN_TRANSITO");
        transicion(paqueteDevuelto, null, "DEVUELTO");

        assertEquals("DEVUELTO", estadoPaquete(paqueteDevuelto));
        assertEquals("DISPONIBLE", estadoDron(dronDevuelto));
        mvc.perform(get(RUTA_DRONES)
                        .param("asignable", "true")
                        .param("size", "50")
                        .with(authOperaciones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].codigo", hasItem("DRN-SGA-DEV")));

        UUID dronCancelado = crearDronApi("DRN-SGA-CAN");
        UUID paqueteCancelado = crearPaqueteApi("PAQ-SGA-CAN", clienteId, null);
        transicion(paqueteCancelado, dronCancelado, "ASIGNADO");
        transicion(paqueteCancelado, null, "CANCELADO");

        assertEquals("CANCELADO", estadoPaquete(paqueteCancelado));
        assertEquals("DISPONIBLE", estadoDron(dronCancelado));
        mvc.perform(get(RUTA_DRONES)
                        .param("asignable", "true")
                        .param("size", "50")
                        .with(authOperaciones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].codigo", hasItem("DRN-SGA-CAN")));
    }

    @Test
    @DisplayName("Los filtros de paquetes, drones asignables y articulos usan PostgreSQL")
    void losFiltrosDelSgaFuncionanSobrePostgreSql() throws Exception {
        UUID clienteId = crearCliente(
                crearCuenta("900300003"), "10203042", "Cliente de filtros SGA");
        UUID otroClienteId = crearCliente(
                crearCuenta("900300004"), "10203043", "Otro cliente de filtros");

        Dron dronA = crearDronEntidad("DRN-FIL-A", EstadoDron.DISPONIBLE);
        Dron dronB = crearDronEntidad("DRN-FIL-B", EstadoDron.DISPONIBLE);
        crearPaqueteEntidad("PAQ-FIL-ASIGNADO", clienteId, dronA, EstadoPaquete.ASIGNADO);
        crearPaqueteEntidad("PAQ-FIL-REGISTRADO", clienteId, null, EstadoPaquete.REGISTRADO);
        crearPaqueteEntidad("PAQ-FIL-CANCELADO", clienteId, null, EstadoPaquete.CANCELADO);
        crearPaqueteEntidad("PAQ-FIL-AJENO", otroClienteId, dronB, EstadoPaquete.ASIGNADO);

        mvc.perform(get(RUTA_PAQUETES)
                        .param("estado", "ASIGNADO")
                        .param("size", "50")
                        .with(authOperaciones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content[*].codigo", hasItem("PAQ-FIL-ASIGNADO")))
                .andExpect(jsonPath("$.content[*].codigo", hasItem("PAQ-FIL-AJENO")));

        mvc.perform(get(RUTA_PAQUETES)
                        .param("clienteId", clienteId.toString())
                        .param("size", "50")
                        .with(authOperaciones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(3)));

        mvc.perform(get(RUTA_PAQUETES)
                        .param("dronId", dronA.getId().toString())
                        .param("size", "50")
                        .with(authOperaciones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].codigo", is("PAQ-FIL-ASIGNADO")));

        // Cuatro estados de dron con y sin paquete en curso.
        for (EstadoDron estado : EstadoDron.values()) {
            Dron sin = crearDronEntidad("DRN-E" + estado.ordinal() + "-SIN", estado);
            Dron con = crearDronEntidad("DRN-E" + estado.ordinal() + "-CON", estado);
            crearPaqueteEntidad(
                    "PAQ-E" + estado.ordinal() + "-CON",
                    clienteId,
                    con,
                    EstadoPaquete.ASIGNADO);
            assertNotNull(sin.getId());
        }

        mvc.perform(get(RUTA_DRONES)
                        .param("asignable", "true")
                        .param("size", "50")
                        .with(authOperaciones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].codigo", is("DRN-E0-SIN")));

        mvc.perform(get(RUTA_DRONES)
                        .param("asignable", "false")
                        .param("size", "50")
                        .with(authOperaciones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(9)));

        mvc.perform(get(RUTA_DRONES + "/{id}", dronIdPorCodigo("DRN-E1-CON"))
                        .with(authOperaciones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("EN_MISION")))
                .andExpect(jsonPath("$.paqueteEnCurso.codigo", is("PAQ-E1-CON")));

        crearArticuloEntidad("ART-FIL-BAJO", 10, 5);
        crearArticuloEntidad("ART-FIL-IGUAL", 10, 10);
        crearArticuloEntidad("ART-FIL-MAYOR", 10, 15);
        crearArticuloEntidad("ART-FIL-CERO", 0, 0);

        mvc.perform(get(RUTA_ARTICULOS)
                        .param("bajoStockMinimo", "true")
                        .param("size", "50")
                        .with(authOperaciones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].codigo", is("ART-FIL-BAJO")));

        mvc.perform(get(RUTA_ARTICULOS)
                        .param("bajoStockMinimo", "false")
                        .param("size", "50")
                        .with(authOperaciones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.content[*].codigo", hasItem("ART-FIL-CERO")));
    }

    @Test
    @DisplayName("El nombre del cliente actualizado en el CRM se refleja en el detalle del paquete")
    void elNombreDelClienteDelCrmSeReflejaEnElPaquete() throws Exception {
        UUID cuentaId = crearCuenta("900300005");
        UUID clienteId = crearCliente(cuentaId, "10203044", "Nombre original");
        UUID paqueteId = crearPaqueteApi("PAQ-SGA-NOMBRE", clienteId, null);

        mvc.perform(get(RUTA_PAQUETES + "/{id}", paqueteId).with(authOperaciones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.nombre", is("Nombre original")));

        mvc.perform(put(RUTA_CLIENTES + "/{id}", clienteId)
                        .with(authComercial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cliente(
                                cuentaId, "10203044", "Nombre actualizado"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre", is("Nombre actualizado")));

        mvc.perform(get(RUTA_PAQUETES + "/{id}", paqueteId).with(authOperaciones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.id", is(clienteId.toString())))
                .andExpect(jsonPath("$.cliente.nombre", is("Nombre actualizado")));
    }

    private UUID crearPerfil(RolUsuario rol) {
        UUID usuarioId = crearUsuarioAuth();
        perfiles.saveAndFlush(PerfilUsuario.crear(
                usuarioId, rol, usuarioId, INSTANTE));
        return usuarioId;
    }

    private UUID crearCuenta(String nit) throws Exception {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("nit", nit);
        datos.put("razonSocial", "Cuenta del flujo SGA " + nit);
        datos.put("nombreContacto", "Contacto ficticio");
        datos.put("correoContacto", "contacto" + nit + "@example.test");
        datos.put("telefonoContacto", "601 000 0000");

        MvcResult resultado = mvc.perform(post(RUTA_CUENTAS)
                        .with(authComercial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(datos)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        return idDe(resultado);
    }

    private UUID crearCliente(
            UUID cuentaId,
            String documento,
            String nombre) throws Exception {

        MvcResult resultado = mvc.perform(post(RUTA_CLIENTES)
                        .with(authComercial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cliente(cuentaId, documento, nombre))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activo", is(true)))
                .andReturn();

        return idDe(resultado);
    }

    private Map<String, Object> cliente(
            UUID cuentaId,
            String documento,
            String nombre) {

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipoDocumento", "CC");
        datos.put("numeroDocumento", documento);
        datos.put("nombre", nombre);
        datos.put("correo", "cliente" + documento + "@example.test");
        datos.put("telefono", "3001234567");
        datos.put("direccion", "Calle Ficticia 123");
        datos.put("ciudad", "Bogota");
        datos.put("cuentaCorporativaId", cuentaId.toString());
        return datos;
    }

    private UUID crearContratoActivo(UUID clienteId, String codigo) throws Exception {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("clienteId", clienteId.toString());
        datos.put("codigo", codigo);
        datos.put("descripcion", "Contrato del flujo SGA");
        datos.put("fechaInicio", "2026-01-01");
        datos.put("fechaFin", "2026-12-31");
        datos.put("valor", "1500000.00");

        MvcResult resultado = mvc.perform(post(RUTA_CONTRATOS)
                        .with(authComercial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(datos)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado", is("BORRADOR")))
                .andReturn();

        UUID contratoId = idDe(resultado);

        mvc.perform(patch(RUTA_CONTRATOS + "/{id}/estado", contratoId)
                        .with(authComercial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("estado", "ACTIVO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("ACTIVO")));

        return contratoId;
    }

    private UUID crearDronApi(String codigo) throws Exception {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("codigo", codigo);
        datos.put("modelo", "Dron ficticio " + codigo);
        datos.put("observaciones", "Observacion ficticia");

        MvcResult resultado = mvc.perform(post(RUTA_DRONES)
                        .with(authOperaciones)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(datos)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.estado", is("DISPONIBLE")))
                .andReturn();

        return idDe(resultado);
    }

    private UUID crearPaqueteApi(
            String codigo,
            UUID clienteId,
            UUID contratoId) throws Exception {

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("codigo", codigo);
        datos.put("descripcion", "Paquete ficticio " + codigo);
        datos.put("direccionEntrega", "Calle Ficticia 456");
        datos.put("clienteId", clienteId.toString());
        if (contratoId != null) {
            datos.put("contratoId", contratoId.toString());
        }
        datos.put("observacion", "Recibido en bodega");

        MvcResult resultado = mvc.perform(post(RUTA_PAQUETES)
                        .with(authOperaciones)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(datos)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.estado", is("REGISTRADO")))
                .andReturn();

        return idDe(resultado);
    }

    private void transicion(UUID paqueteId, UUID dronId, String estado)
            throws Exception {

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("estado", estado);
        if (dronId != null) {
            datos.put("dronId", dronId.toString());
        }

        mvc.perform(post(RUTA_PAQUETES + "/{id}/estados", paqueteId)
                        .with(authOperaciones)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(datos)))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        RUTA_PAQUETES + "/" + paqueteId + "/estados"))
                .andExpect(jsonPath("$.estadoNuevo", is(estado)));
    }

    private Dron crearDronEntidad(String codigo, EstadoDron estado) {
        Dron dron = Dron.crear(
                codigo, "Dron ficticio " + codigo, null, operaciones, INSTANTE);

        switch (estado) {
            case EN_MISION -> dron.sincronizarConPaquete(
                    EstadoDron.EN_MISION, operaciones, INSTANTE);
            case MANTENIMIENTO, FUERA_DE_SERVICIO -> dron.cambiarEstadoManual(
                    estado, operaciones, INSTANTE);
            case DISPONIBLE -> {
                // Estado inicial de la fabrica.
            }
        }

        return drones.saveAndFlush(dron);
    }

    private Paquete crearPaqueteEntidad(
            String codigo,
            UUID clienteId,
            Dron dron,
            EstadoPaquete estado) {

        Paquete paquete = paquetes.save(Paquete.crear(
                codigo,
                "Paquete ficticio " + codigo,
                "Calle ficticia 123",
                clienteId,
                null,
                operaciones,
                INSTANTE));

        historial.save(HistorialEstadoPaquete.alta(
                paquete, null, operaciones, INSTANTE));

        if (estado == EstadoPaquete.ASIGNADO) {
            paquete.aplicarTransicion(
                    EstadoPaquete.ASIGNADO, dron, operaciones, INSTANTE);
            historial.save(HistorialEstadoPaquete.transicion(
                    paquete,
                    2,
                    EstadoPaquete.REGISTRADO,
                    EstadoPaquete.ASIGNADO,
                    dron,
                    null,
                    operaciones,
                    INSTANTE));
        } else if (estado == EstadoPaquete.CANCELADO) {
            paquete.aplicarTransicion(
                    EstadoPaquete.CANCELADO, null, operaciones, INSTANTE);
            historial.save(HistorialEstadoPaquete.transicion(
                    paquete,
                    2,
                    EstadoPaquete.REGISTRADO,
                    EstadoPaquete.CANCELADO,
                    null,
                    null,
                    operaciones,
                    INSTANTE));
        }

        return paquetes.saveAndFlush(paquete);
    }

    private ArticuloInventario crearArticuloEntidad(
            String codigo,
            int stockMinimo,
            long existencia) {

        ArticuloInventario articulo = articulos.save(ArticuloInventario.crear(
                codigo,
                "Articulo ficticio " + codigo,
                CategoriaArticulo.REPUESTO,
                UnidadArticulo.UNIDAD,
                stockMinimo,
                operaciones,
                INSTANTE));

        if (existencia > 0) {
            articulo.aplicarMovimiento(
                    TipoMovimientoInventario.ENTRADA, (int) existencia);
        }

        return articulos.saveAndFlush(articulo);
    }

    private UUID dronIdPorCodigo(String codigo) {
        return jdbc.queryForObject(
                "select id from app.drones where codigo = ?", UUID.class, codigo);
    }

    private String estadoDron(UUID id) {
        return jdbc.queryForObject(
                "select estado from app.drones where id = ?", String.class, id);
    }

    private String estadoPaquete(UUID id) {
        return jdbc.queryForObject(
                "select estado from app.paquetes where id = ?", String.class, id);
    }

    private String cuerpo(Map<String, Object> contenido) {
        return objectMapper.writeValueAsString(contenido);
    }

    private UUID idDe(MvcResult resultado) throws Exception {
        return UUID.fromString(objectMapper
                .readTree(resultado.getResponse().getContentAsString())
                .get("id")
                .asText());
    }
}
