package com.arise.corp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.arise.corp.config.PerfilJwtAuthenticationConverter;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.TipoDocumento;
import com.arise.corp.crm.repository.ClienteRepository;
import com.arise.corp.erp.GeneradoresErp;
import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.MovimientoFinanciero;
import com.arise.corp.erp.entity.Proveedor;
import com.arise.corp.erp.entity.TipoDocumentoProveedor;
import com.arise.corp.erp.entity.TipoMovimiento;
import com.arise.corp.erp.repository.MovimientoFinancieroRepository;
import com.arise.corp.erp.repository.ProveedorRepository;
import com.arise.corp.login.AutenticacionPerfilTestSupport;
import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.login.repository.PerfilUsuarioRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Property 14 sobre PostgreSQL efimero (Requirements 18.1-18.4, 18.6, 18.7 y
 * 25.4, ERP-04, ENG-03).
 *
 * <p>Siembra un conjunto determinista de movimientos repartidos en dos anos y
 * ejecuta 120 rangos generados (con y sin limites) contra la API del panel. En
 * cada rango compara las seis combinaciones de tipo y estado, la cantidad por
 * tipo y el saldo pagado con conteos y sumas calculados por JDBC, sin pasar por
 * los repositorios que usa el servicio.
 */
class ResumenFinancieroIT extends PostgresIntegracionBase {

    private static final UUID ACTOR =
            UUID.fromString("00000000-0000-4000-8000-000000000311");
    private static final Instant BASE = Instant.parse("2026-04-01T00:00:00Z");
    private static final LocalDate PRIMER_DIA = LocalDate.of(2025, 1, 1);
    private static final int ESCENARIOS = 120;
    private static final int MOVIMIENTOS = 60;
    private static final String RUTA = "/api/erp/resumen";

    private static final String CONSULTA_BASE =
            "select count(*) as cantidad, sum(valor) as suma"
                    + " from app.movimientos_financieros"
                    + " where tipo = ? and estado = ?";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PerfilJwtAuthenticationConverter converter;

    @Autowired
    private PerfilUsuarioRepository perfiles;

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private ProveedorRepository proveedores;

    @Autowired
    private MovimientoFinancieroRepository movimientos;

    private RequestPostProcessor autenticacion;

    @BeforeEach
    void sembrarDatos() {
        UUID gestor = crearUsuarioAuth();
        perfiles.saveAndFlush(PerfilUsuario.crear(
                gestor, RolUsuario.ADMINISTRADOR, gestor, BASE));
        autenticacion = AutenticacionPerfilTestSupport.como(
                converter, gestor.toString(), "admin@example.test");

        List<Cliente> entidadesClientes = sembrarClientes();
        List<Proveedor> entidadesProveedores = sembrarProveedores();
        sembrarMovimientos(entidadesClientes, entidadesProveedores);
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 14: El resumen financiero es consistente con los movimientos")
    void elResumenCoincideConLosConteosYLasSumasPersistidas() throws Exception {
        Random azar = GeneradoresErp.nuevoRandom();
        int escenarios = 0;

        for (int i = 0; i < ESCENARIOS; i++) {
            GeneradoresErp.RangoGenerado rango = GeneradoresErp.rango(azar);
            verificarResumen(rango.desde(), rango.hasta(), "escenario " + i);
            escenarios++;
        }

        assertTrue(escenarios >= 100,
                "la propiedad debe ejecutar al menos 100 rangos generados");
    }

    @Test
    @DisplayName("El resumen sin movimientos en el rango devuelve las seis combinaciones en cero")
    void elResumenSinDatosDevuelveLasSeisCombinacionesEnCero() throws Exception {
        JsonNode resumen = consultar(LocalDate.of(2030, 1, 1), null);

        for (TipoMovimiento tipo : TipoMovimiento.values()) {
            JsonNode porTipo = resumen.get("porTipo").get(tipo.name());
            assertEquals(0L, porTipo.get("cantidad").asLong(),
                    "sin movimientos la cantidad de " + tipo + " debe ser 0");

            for (EstadoMovimiento estado : EstadoMovimiento.values()) {
                JsonNode celda = porTipo.get("porEstado").get(estado.name());
                assertEquals(0L, celda.get("cantidad").asLong(),
                        tipo + " " + estado + " debe ser 0");
                assertEquals("0.00", celda.get("suma").asString(),
                        tipo + " " + estado + " debe sumar 0.00");
            }
        }

        assertEquals("0.00", resumen.get("saldoPagado").asString());
    }

    @Test
    @DisplayName("El saldo pagado es negativo cuando los egresos pagados superan a los ingresos")
    void elSaldoPagadoEsNegativoCuandoLosEgresosSuperanALosIngresos() throws Exception {
        JsonNode resumen = consultar(null, null);

        String saldo = resumen.get("saldoPagado").asString();
        BigDecimal esperado = sumaDb(TipoMovimiento.INGRESO, EstadoMovimiento.PAGADO,
                null, null)
                .subtract(sumaDb(TipoMovimiento.EGRESO, EstadoMovimiento.PAGADO,
                        null, null));

        assertTrue(esperado.signum() < 0,
                "el conjunto sembrado debe dejar un saldo pagado negativo");
        assertTrue(saldo.startsWith("-"),
                "el saldo negativo debe conservar el signo");
        assertEquals(texto(esperado), saldo);
    }

    private void verificarResumen(
            LocalDate desde, LocalDate hasta, String contexto) throws Exception {

        JsonNode resumen = consultar(desde, hasta);
        JsonNode porTipo = resumen.get("porTipo");

        assertEquals(TipoMovimiento.values().length, porTipo.size(),
                contexto + ": deben aparecer los dos tipos");
        assertEquals(desde == null ? null : desde.toString(),
                resumen.get("desde").isNull() ? null : resumen.get("desde").asString(),
                contexto + ": desde");
        assertEquals(hasta == null ? null : hasta.toString(),
                resumen.get("hasta").isNull() ? null : resumen.get("hasta").asString(),
                contexto + ": hasta");

        for (TipoMovimiento tipo : TipoMovimiento.values()) {
            JsonNode nodoTipo = porTipo.get(tipo.name());
            JsonNode porEstado = nodoTipo.get("porEstado");
            long cantidadTipo = 0;

            assertEquals(EstadoMovimiento.values().length, porEstado.size(),
                    contexto + ": deben aparecer los tres estados de " + tipo);

            for (EstadoMovimiento estado : EstadoMovimiento.values()) {
                JsonNode celda = porEstado.get(estado.name());
                long cantidad = cantidadDb(tipo, estado, desde, hasta);
                BigDecimal suma = sumaDb(tipo, estado, desde, hasta);

                cantidadTipo += cantidad;
                assertEquals(cantidad, celda.get("cantidad").asLong(),
                        contexto + ": cantidad de " + tipo + " " + estado);
                assertEquals(texto(suma), celda.get("suma").asString(),
                        contexto + ": suma de " + tipo + " " + estado);
            }

            assertEquals(cantidadTipo, nodoTipo.get("cantidad").asLong(),
                    contexto + ": cantidad total de " + tipo);
        }

        BigDecimal saldo = sumaDb(TipoMovimiento.INGRESO, EstadoMovimiento.PAGADO,
                desde, hasta)
                .subtract(sumaDb(TipoMovimiento.EGRESO, EstadoMovimiento.PAGADO,
                        desde, hasta));

        assertEquals(texto(saldo), resumen.get("saldoPagado").asString(),
                contexto + ": saldo pagado");
    }

    private List<Cliente> sembrarClientes() {
        List<Cliente> entidades = new ArrayList<>(3);

        for (int i = 0; i < 3; i++) {
            Cliente cliente = Cliente.crear(
                    TipoDocumento.CC,
                    String.format("%08d", 40_000_000 + i),
                    "Cliente resumen " + i,
                    "cliente.resumen" + i + "@example.test",
                    "3001234567",
                    null,
                    null,
                    null,
                    ACTOR,
                    BASE.plusSeconds(100 + i));

            entidades.add(clientes.saveAndFlush(cliente));
        }

        return entidades;
    }

    private List<Proveedor> sembrarProveedores() {
        List<Proveedor> entidades = new ArrayList<>(3);

        for (int i = 0; i < 3; i++) {
            Proveedor proveedor = Proveedor.crear(
                    TipoDocumentoProveedor.NIT,
                    String.format("9%08d", i + 1),
                    "Proveedor resumen " + i,
                    null,
                    "proveedor.resumen" + i + "@example.test",
                    "601 000 0000",
                    null,
                    null,
                    ACTOR,
                    BASE.plusSeconds(200 + i));

            entidades.add(proveedores.saveAndFlush(proveedor));
        }

        return entidades;
    }

    private void sembrarMovimientos(
            List<Cliente> entidadesClientes, List<Proveedor> entidadesProveedores) {

        for (int i = 0; i < MOVIMIENTOS; i++) {
            TipoMovimiento tipo = i % 2 == 0
                    ? TipoMovimiento.INGRESO
                    : TipoMovimiento.EGRESO;
            EstadoMovimiento estado = switch ((i / 2) % 3) {
                case 0 -> EstadoMovimiento.PENDIENTE;
                case 1 -> EstadoMovimiento.PAGADO;
                default -> EstadoMovimiento.ANULADO;
            };
            int entero = (tipo == TipoMovimiento.INGRESO ? 100_000 : 250_000)
                    + i * 1_000;
            BigDecimal valor = new BigDecimal(String.format(
                    "%d.%02d", entero, (i * 13) % 100));
            LocalDate fecha = PRIMER_DIA.plusDays((i * 23L) % 730);
            Instant creadoEn = BASE.plusSeconds(1_000 + i * 60L);

            MovimientoFinanciero movimiento = MovimientoFinanciero.crear(
                    tipo,
                    fecha,
                    "Movimiento del resumen " + i,
                    valor,
                    String.format("RES-%04d", i),
                    tipo == TipoMovimiento.INGRESO
                            ? entidadesClientes.get(i % entidadesClientes.size()).getId()
                            : null,
                    tipo == TipoMovimiento.EGRESO
                            ? entidadesProveedores.get(i % entidadesProveedores.size())
                            : null,
                    ACTOR,
                    creadoEn);

            if (estado != EstadoMovimiento.PENDIENTE) {
                movimiento.cambiarEstado(estado, ACTOR, creadoEn.plusSeconds(1));
            }

            movimientos.saveAndFlush(movimiento);
        }
    }

    private JsonNode consultar(LocalDate desde, LocalDate hasta) throws Exception {
        MockHttpServletRequestBuilder peticion = get(RUTA).with(autenticacion);

        if (desde != null) {
            peticion.param("desde", desde.toString());
        }
        if (hasta != null) {
            peticion.param("hasta", hasta.toString());
        }

        MvcResult resultado = mvc.perform(peticion)
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(
                resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private long cantidadDb(
            TipoMovimiento tipo,
            EstadoMovimiento estado,
            LocalDate desde,
            LocalDate hasta) {

        return ((Number) fila(tipo, estado, desde, hasta).get("cantidad")).longValue();
    }

    private BigDecimal sumaDb(
            TipoMovimiento tipo,
            EstadoMovimiento estado,
            LocalDate desde,
            LocalDate hasta) {

        Object suma = fila(tipo, estado, desde, hasta).get("suma");
        return suma == null ? BigDecimal.ZERO : (BigDecimal) suma;
    }

    private java.util.Map<String, Object> fila(
            TipoMovimiento tipo,
            EstadoMovimiento estado,
            LocalDate desde,
            LocalDate hasta) {

        StringBuilder sql = new StringBuilder(CONSULTA_BASE);
        List<Object> argumentos = new ArrayList<>(List.of(tipo.name(), estado.name()));

        if (desde != null) {
            sql.append(" and fecha >= ?");
            argumentos.add(desde);
        }
        if (hasta != null) {
            sql.append(" and fecha <= ?");
            argumentos.add(hasta);
        }

        return jdbc.queryForMap(sql.toString(), argumentos.toArray());
    }

    private static String texto(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
