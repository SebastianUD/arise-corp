package com.arise.corp.erp.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.arise.corp.crm.CrmDatosPrueba;
import com.arise.corp.erp.ErpDatosPrueba;
import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.MovimientoFinanciero;
import com.arise.corp.erp.entity.TipoMovimiento;
import com.arise.corp.login.entity.RolUsuario;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pruebas MockMvc del panel financiero (ERP-04, Requirement 18).
 *
 * <p>Usa H2 y perfiles persistidos con el converter real. Comprueba las seis
 * combinaciones en cero, las cantidades y sumas, el saldo pagado con signo, el
 * filtro inclusivo de fechas, los limites repetidos y los 400 de las fechas
 * invalidas y del rango invertido.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({CrmDatosPrueba.class, ErpDatosPrueba.class})
class ResumenFinancieroControllerTests {

    private static final String RUTA = "/api/erp/resumen";
    private static final LocalDate FECHA = LocalDate.of(2026, 6, 15);

    private static final Set<String> CAMPOS_RESUMEN =
            Set.of("desde", "hasta", "porTipo", "saldoPagado");
    private static final Set<String> CAMPOS_TIPO =
            Set.of("porEstado", "cantidad");
    private static final Set<String> CAMPOS_ESTADO =
            Set.of("cantidad", "suma");

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
    void sinMovimientosDevuelveLasSeisCombinacionesEnCero() throws Exception {
        MvcResult resultado = mvc.perform(get(RUTA)
                        .with(crm.autenticacionDe(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value(nullValue()))
                .andExpect(jsonPath("$.hasta").value(nullValue()))
                .andExpect(jsonPath("$.saldoPagado", is("0.00")))
                .andExpect(jsonPath("$.porTipo.INGRESO.cantidad", is(0)))
                .andExpect(jsonPath("$.porTipo.EGRESO.cantidad", is(0)))
                .andReturn();

        JsonNode resumen = objectMapper.readTree(
                resultado.getResponse().getContentAsString());

        assertEquals(CAMPOS_RESUMEN, nombres(resumen));
        assertEquals(Set.of("INGRESO", "EGRESO"),
                nombres(resumen.get("porTipo")));

        for (TipoMovimiento tipo : TipoMovimiento.values()) {
            JsonNode porTipo = resumen.get("porTipo").get(tipo.name());

            assertEquals(CAMPOS_TIPO, nombres(porTipo), tipo.name());
            assertEquals(Set.of("PENDIENTE", "PAGADO", "ANULADO"),
                    nombres(porTipo.get("porEstado")), tipo.name());

            for (EstadoMovimiento estado : EstadoMovimiento.values()) {
                JsonNode porEstado =
                        porTipo.get("porEstado").get(estado.name());

                assertEquals(CAMPOS_ESTADO, nombres(porEstado),
                        tipo + "/" + estado);
                assertEquals(0, porEstado.get("cantidad").asInt(),
                        tipo + "/" + estado);
                assertEquals("0.00", porEstado.get("suma").asString(),
                        tipo + "/" + estado);
            }
        }

        mvc.perform(get(RUTA).with(crm.autenticacionDe(finanzas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoPagado", is("0.00")));
    }

    @Test
    void calculaLasCantidadesLasSumasYElSaldo() throws Exception {
        crear(TipoMovimiento.INGRESO, EstadoMovimiento.PENDIENTE, "100.00");
        crear(TipoMovimiento.INGRESO, EstadoMovimiento.PAGADO, "200.50");
        crear(TipoMovimiento.INGRESO, EstadoMovimiento.PAGADO, "50.00");
        crear(TipoMovimiento.EGRESO, EstadoMovimiento.PENDIENTE, "5.00");
        crear(TipoMovimiento.EGRESO, EstadoMovimiento.PAGADO, "30.25");
        crear(TipoMovimiento.EGRESO, EstadoMovimiento.ANULADO, "10.00");

        mvc.perform(get(RUTA)
                        .with(crm.autenticacionDe(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porTipo.INGRESO.cantidad", is(3)))
                .andExpect(jsonPath("$.porTipo.INGRESO.porEstado.PENDIENTE.cantidad",
                        is(1)))
                .andExpect(jsonPath("$.porTipo.INGRESO.porEstado.PENDIENTE.suma",
                        is("100.00")))
                .andExpect(jsonPath("$.porTipo.INGRESO.porEstado.PAGADO.cantidad",
                        is(2)))
                .andExpect(jsonPath("$.porTipo.INGRESO.porEstado.PAGADO.suma",
                        is("250.50")))
                .andExpect(jsonPath("$.porTipo.INGRESO.porEstado.ANULADO.cantidad",
                        is(0)))
                .andExpect(jsonPath("$.porTipo.INGRESO.porEstado.ANULADO.suma",
                        is("0.00")))
                .andExpect(jsonPath("$.porTipo.EGRESO.cantidad", is(3)))
                .andExpect(jsonPath("$.porTipo.EGRESO.porEstado.PENDIENTE.cantidad",
                        is(1)))
                .andExpect(jsonPath("$.porTipo.EGRESO.porEstado.PENDIENTE.suma",
                        is("5.00")))
                .andExpect(jsonPath("$.porTipo.EGRESO.porEstado.PAGADO.cantidad",
                        is(1)))
                .andExpect(jsonPath("$.porTipo.EGRESO.porEstado.PAGADO.suma",
                        is("30.25")))
                .andExpect(jsonPath("$.porTipo.EGRESO.porEstado.ANULADO.cantidad",
                        is(1)))
                .andExpect(jsonPath("$.porTipo.EGRESO.porEstado.ANULADO.suma",
                        is("10.00")))
                .andExpect(jsonPath("$.saldoPagado", is("220.25")));
    }

    @Test
    void elSaldoNegativoConservaElSigno() throws Exception {
        crear(TipoMovimiento.INGRESO, EstadoMovimiento.PAGADO, "100.00");
        crear(TipoMovimiento.EGRESO, EstadoMovimiento.PAGADO, "1200000.50");

        mvc.perform(get(RUTA)
                        .with(crm.autenticacionDe(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoPagado", is("-1199900.50")));
    }

    @Test
    void filtraPorRangoInclusivoYRepiteLosLimites() throws Exception {
        crear(TipoMovimiento.INGRESO, EstadoMovimiento.PAGADO, "100.00",
                LocalDate.of(2026, 1, 10));
        crear(TipoMovimiento.INGRESO, EstadoMovimiento.PAGADO, "200.00",
                LocalDate.of(2026, 2, 10));
        crear(TipoMovimiento.EGRESO, EstadoMovimiento.PAGADO, "50.00",
                LocalDate.of(2026, 3, 10));

        mvc.perform(get(RUTA)
                        .param("desde", "2026-02-10")
                        .param("hasta", "2026-02-10")
                        .with(crm.autenticacionDe(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde", is("2026-02-10")))
                .andExpect(jsonPath("$.hasta", is("2026-02-10")))
                .andExpect(jsonPath("$.porTipo.INGRESO.porEstado.PAGADO.cantidad",
                        is(1)))
                .andExpect(jsonPath("$.porTipo.INGRESO.porEstado.PAGADO.suma",
                        is("200.00")))
                .andExpect(jsonPath("$.porTipo.EGRESO.porEstado.PAGADO.cantidad",
                        is(0)))
                .andExpect(jsonPath("$.saldoPagado", is("200.00")));

        mvc.perform(get(RUTA)
                        .param("desde", "2026-02-01")
                        .with(crm.autenticacionDe(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde", is("2026-02-01")))
                .andExpect(jsonPath("$.hasta").value(nullValue()))
                .andExpect(jsonPath("$.porTipo.INGRESO.porEstado.PAGADO.suma",
                        is("200.00")))
                .andExpect(jsonPath("$.porTipo.EGRESO.porEstado.PAGADO.suma",
                        is("50.00")))
                .andExpect(jsonPath("$.saldoPagado", is("150.00")));

        mvc.perform(get(RUTA)
                        .param("hasta", "2026-01-31")
                        .with(crm.autenticacionDe(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value(nullValue()))
                .andExpect(jsonPath("$.hasta", is("2026-01-31")))
                .andExpect(jsonPath("$.porTipo.INGRESO.porEstado.PAGADO.suma",
                        is("100.00")))
                .andExpect(jsonPath("$.saldoPagado", is("100.00")));
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 15: Los parámetros de lista y de rango se aceptan o "
            + "rechazan según la regla")
    void rechazaLasFechasInvalidasYElRangoInvertido() throws Exception {
        mvc.perform(get(RUTA)
                        .param("desde", "2026-13-01")
                        .with(crm.autenticacionDe(administrador)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field", is("desde")));

        mvc.perform(get(RUTA)
                        .param("hasta", "2026-13-01")
                        .with(crm.autenticacionDe(administrador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("hasta")));

        mvc.perform(get(RUTA)
                        .param("desde", "2026-12-31")
                        .param("hasta", "2026-01-01")
                        .with(crm.autenticacionDe(administrador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("hasta")))
                .andExpect(jsonPath("$.errors[0].message",
                        is("debe ser igual o posterior a la fecha desde")));
    }

    private MovimientoFinanciero crear(
            TipoMovimiento tipo,
            EstadoMovimiento estado,
            String valor) {

        return crear(tipo, estado, valor, FECHA);
    }

    private MovimientoFinanciero crear(
            TipoMovimiento tipo,
            EstadoMovimiento estado,
            String valor,
            LocalDate fecha) {

        return datos.crearMovimiento(
                tipo, estado, new BigDecimal(valor), fecha, null, null);
    }

    private static Set<String> nombres(JsonNode nodo) {
        Set<String> nombres = new LinkedHashSet<>();
        nombres.addAll(nodo.propertyNames());
        return nombres;
    }
}
