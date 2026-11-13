package com.arise.corp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.arise.corp.config.PerfilJwtAuthenticationConverter;
import com.arise.corp.crm.GeneradoresCrm;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.Contrato;
import com.arise.corp.crm.entity.CuentaCorporativa;
import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.crm.entity.TipoDocumento;
import com.arise.corp.crm.repository.ClienteRepository;
import com.arise.corp.crm.repository.ContratoRepository;
import com.arise.corp.crm.repository.CuentaCorporativaRepository;
import com.arise.corp.login.AutenticacionPerfilTestSupport;
import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.login.repository.PerfilUsuarioRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Property 13 sobre PostgreSQL efimero (Requirements 15.1, 15.2, 15.3 y 24.7,
 * CRM-05).
 *
 * <p>Siembra cien escenarios generados con semilla fija y compara la respuesta
 * del resumen comercial con conteos obtenidos por JDBC, sin pasar por los
 * repositorios que usa el servicio. En cada escenario verifica que los cuatro
 * estados aparecen, que cada conteo coincide con los registros persistidos, que
 * {@code clientes.total = activos + inactivos} y que {@code contratos.total} es
 * la suma de {@code porEstado}.
 */
class ResumenComercialIT extends PostgresIntegracionBase {

    private static final UUID ACTOR =
            UUID.fromString("00000000-0000-4000-8000-000000000301");
    private static final Instant BASE = Instant.parse("2026-04-01T00:00:00Z");
    private static final LocalDate FECHA_INICIO = LocalDate.of(2026, 1, 1);
    private static final int ESCENARIOS = 100;
    private static final String RUTA = "/api/crm/resumen";
    private static final String TRUNCAR_CRM = "truncate table app.contratos,"
            + " app.clientes, app.cuentas_corporativas cascade";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PerfilJwtAuthenticationConverter converter;

    @Autowired
    private PerfilUsuarioRepository perfiles;

    @Autowired
    private CuentaCorporativaRepository cuentas;

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private ContratoRepository contratos;

    private RequestPostProcessor autenticacion;

    @BeforeEach
    void prepararAutenticacion() {
        UUID comercial = crearUsuarioAuth();
        perfiles.saveAndFlush(PerfilUsuario.crear(
                comercial, RolUsuario.COMERCIAL, comercial, BASE));
        autenticacion = AutenticacionPerfilTestSupport.como(
                converter, comercial.toString(), "comercial@example.test");
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 13: El resumen comercial es consistente")
    void elResumenCoincideConLosConteosPersistidos() throws Exception {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int escenario = 0; escenario < ESCENARIOS; escenario++) {
            vaciarEscenario();
            sembrarEscenario(azar, escenario);
            verificarEscenario(escenario);
        }
    }

    @Test
    @DisplayName("El resumen sin datos devuelve los cuatro estados en cero")
    void elResumenSinDatosDevuelveLosCuatroEstadosEnCero() throws Exception {
        JsonNode resumen = consultarResumen();

        assertEquals(0L, resumen.get("clientes").get("activos").asLong());
        assertEquals(0L, resumen.get("clientes").get("inactivos").asLong());
        assertEquals(0L, resumen.get("clientes").get("total").asLong());

        JsonNode porEstado = resumen.get("contratos").get("porEstado");
        assertEquals(EstadoContrato.values().length, porEstado.size());
        for (EstadoContrato estado : EstadoContrato.values()) {
            assertEquals(0L, porEstado.get(estado.name()).asLong(),
                    "sin contratos " + estado + " debe ser 0");
        }
        assertEquals(0L, resumen.get("contratos").get("total").asLong());
    }

    private void vaciarEscenario() {
        jdbc.execute(TRUNCAR_CRM);
    }

    private void sembrarEscenario(Random azar, int escenario) {
        List<CuentaCorporativa> entidadesCuentas = sembrarCuentas(azar, escenario);
        List<Cliente> entidadesClientes = sembrarClientes(
                azar, escenario, entidadesCuentas);
        sembrarContratos(azar, escenario, entidadesClientes);
    }

    private List<CuentaCorporativa> sembrarCuentas(Random azar, int escenario) {
        int total = azar.nextInt(4);
        List<CuentaCorporativa> entidades = new ArrayList<>(total);

        for (int i = 0; i < total; i++) {
            String nit = String.format("9%02d%06d", escenario, i);
            CuentaCorporativa cuenta = CuentaCorporativa.crear(
                    nit,
                    "Cuenta " + escenario + "-" + i,
                    "Contacto " + i,
                    "cuenta" + escenario + "-" + i + "@example.test",
                    "3001234567",
                    ACTOR,
                    BASE.plusSeconds(i));
            entidades.add(cuentas.saveAndFlush(cuenta));
        }

        return entidades;
    }

    private List<Cliente> sembrarClientes(
            Random azar, int escenario, List<CuentaCorporativa> entidadesCuentas) {

        int total = azar.nextInt(9);
        List<Cliente> entidades = new ArrayList<>(total);

        for (int i = 0; i < total; i++) {
            boolean activo = azar.nextBoolean();
            String documento = String.format("8%02d%06d", escenario, i);
            CuentaCorporativa cuenta = entidadesCuentas.isEmpty()
                    || azar.nextBoolean()
                    ? null
                    : entidadesCuentas.get(azar.nextInt(entidadesCuentas.size()));

            Cliente cliente = Cliente.crear(
                    TipoDocumento.CC,
                    documento,
                    "Cliente " + escenario + "-" + i,
                    "cliente" + escenario + "-" + i + "@example.test",
                    "3001234567",
                    "Calle 1 # 2-3",
                    "Bogota",
                    cuenta,
                    ACTOR,
                    BASE.plusSeconds(100 + i));

            if (!activo) {
                cliente.cambiarEstado(false, ACTOR, BASE.plusSeconds(200 + i));
            }

            entidades.add(clientes.saveAndFlush(cliente));
        }

        return entidades;
    }

    private void sembrarContratos(
            Random azar, int escenario, List<Cliente> entidadesClientes) {

        if (entidadesClientes.isEmpty()) {
            return;
        }

        int total = azar.nextInt(11);

        for (int i = 0; i < total; i++) {
            Cliente cliente = entidadesClientes.get(
                    azar.nextInt(entidadesClientes.size()));
            String codigo = String.format("IT-%03d-%03d", escenario, i);
            LocalDate fechaInicio = FECHA_INICIO.plusDays(i * 7L);
            EstadoContrato estado = EstadoContrato.values()[
                    azar.nextInt(EstadoContrato.values().length)];

            Contrato contrato = Contrato.crear(
                    cliente,
                    codigo,
                    "Contrato " + codigo,
                    fechaInicio,
                    fechaInicio.plusMonths(6),
                    new BigDecimal("1000000.00").add(
                            BigDecimal.valueOf(i * 1000L)),
                    ACTOR,
                    BASE.plusSeconds(1000 + i));

            switch (estado) {
                case BORRADOR -> {
                    // Estado inicial de la fabrica.
                }
                case ACTIVO -> contrato.cambiarEstado(
                        EstadoContrato.ACTIVO, ACTOR, BASE.plusSeconds(1001 + i));
                case FINALIZADO -> {
                    contrato.cambiarEstado(
                            EstadoContrato.ACTIVO, ACTOR, BASE.plusSeconds(1001 + i));
                    contrato.cambiarEstado(
                            EstadoContrato.FINALIZADO, ACTOR,
                            BASE.plusSeconds(1002 + i));
                }
                case CANCELADO -> contrato.cambiarEstado(
                        EstadoContrato.CANCELADO, ACTOR, BASE.plusSeconds(1001 + i));
            }

            contratos.saveAndFlush(contrato);
        }
    }

    private void verificarEscenario(int escenario) throws Exception {
        JsonNode resumen = consultarResumen();
        String contexto = "escenario " + escenario;

        long activosDb = contarClientes(true);
        long inactivosDb = contarClientes(false);
        Map<EstadoContrato, Long> porEstadoDb = contarContratosPorEstado();

        JsonNode jsonClientes = resumen.get("clientes");
        assertEquals(activosDb, jsonClientes.get("activos").asLong(),
                contexto + ": clientes activos");
        assertEquals(inactivosDb, jsonClientes.get("inactivos").asLong(),
                contexto + ": clientes inactivos");
        assertEquals(activosDb + inactivosDb,
                jsonClientes.get("total").asLong(),
                contexto + ": clientes.total debe ser la suma");

        JsonNode jsonPorEstado = resumen.get("contratos").get("porEstado");
        assertEquals(EstadoContrato.values().length, jsonPorEstado.size(),
                contexto + ": deben aparecer los cuatro estados");

        long sumaDb = 0;

        for (EstadoContrato estado : EstadoContrato.values()) {
            long esperados = porEstadoDb.getOrDefault(estado, 0L);
            sumaDb += esperados;
            assertEquals(esperados, jsonPorEstado.get(estado.name()).asLong(),
                    contexto + ": contratos en " + estado);
        }

        assertEquals(sumaDb, resumen.get("contratos").get("total").asLong(),
                contexto + ": contratos.total debe ser la suma de porEstado");
    }

    private JsonNode consultarResumen() throws Exception {
        MvcResult resultado = mvc.perform(get(RUTA).with(autenticacion))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(
                resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private long contarClientes(boolean activo) {
        Long total = jdbc.queryForObject(
                "select count(*) from app.clientes where activo = ?",
                Long.class,
                activo);

        return total == null ? 0L : total;
    }

    private Map<EstadoContrato, Long> contarContratosPorEstado() {
        Map<EstadoContrato, Long> conteos = new EnumMap<>(EstadoContrato.class);

        for (Map<String, Object> fila : jdbc.queryForList(
                "select estado, count(*) as total from app.contratos group by estado")) {
            EstadoContrato estado = EstadoContrato.valueOf(
                    String.valueOf(fila.get("estado")));
            conteos.put(estado, ((Number) fila.get("total")).longValue());
        }

        return conteos;
    }
}
