package com.arise.corp.crm.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.arise.corp.crm.CrmDatosPrueba;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.CuentaCorporativa;
import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.login.entity.RolUsuario;

/**
 * Pruebas MockMvc del resumen comercial (Requirements 15.1 a 15.3, CRM-05).
 *
 * <p>Comprueba que los cuatro estados aparecen siempre, con 0 cuando no hay
 * contratos, que los totales son la suma de sus componentes y que los conteos
 * se leen en el momento de la solicitud.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CrmDatosPrueba.class)
class ResumenComercialControllerTests {

    private static final String RUTA = "/api/crm/resumen";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CrmDatosPrueba datos;

    private UUID comercial;

    @BeforeEach
    void setUp() {
        datos.limpiar();
        comercial = datos.crearPerfil(RolUsuario.COMERCIAL);
    }

    @Test
    void devuelveLosCuatroEstadosEnCeroCuandoNoHayDatos() throws Exception {
        mvc.perform(get(RUTA).with(datos.autenticacionDe(comercial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientes.activos", is(0)))
                .andExpect(jsonPath("$.clientes.inactivos", is(0)))
                .andExpect(jsonPath("$.clientes.total", is(0)))
                .andExpect(jsonPath("$.contratos.porEstado", aMapWithSize(4)))
                .andExpect(jsonPath("$.contratos.porEstado.BORRADOR", is(0)))
                .andExpect(jsonPath("$.contratos.porEstado.ACTIVO", is(0)))
                .andExpect(jsonPath("$.contratos.porEstado.FINALIZADO", is(0)))
                .andExpect(jsonPath("$.contratos.porEstado.CANCELADO", is(0)))
                .andExpect(jsonPath("$.contratos.total", is(0)));
    }

    @Test
    void cuentaClientesYContratosPorEstadoConTotalesCoherentes() throws Exception {
        CuentaCorporativa cuenta = datos.crearCuenta();
        Cliente activo = datos.crearCliente(
                "10234567", "Cliente activo", true, cuenta);
        datos.crearCliente("10234568", "Cliente inactivo", false, cuenta);

        for (EstadoContrato estado : EstadoContrato.values()) {
            datos.crearContrato(activo, estado);
        }

        mvc.perform(get(RUTA).with(datos.autenticacionDe(comercial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientes.activos", is(1)))
                .andExpect(jsonPath("$.clientes.inactivos", is(1)))
                .andExpect(jsonPath("$.clientes.total", is(2)))
                .andExpect(jsonPath("$.contratos.porEstado", aMapWithSize(4)))
                .andExpect(jsonPath("$.contratos.porEstado.BORRADOR", is(1)))
                .andExpect(jsonPath("$.contratos.porEstado.ACTIVO", is(1)))
                .andExpect(jsonPath("$.contratos.porEstado.FINALIZADO", is(1)))
                .andExpect(jsonPath("$.contratos.porEstado.CANCELADO", is(1)))
                .andExpect(jsonPath("$.contratos.total", is(4)));
    }

    @Test
    void leeLosConteosEnElMomentoDeLaSolicitud() throws Exception {
        mvc.perform(get(RUTA).with(datos.autenticacionDe(comercial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientes.total", is(0)))
                .andExpect(jsonPath("$.contratos.total", is(0)));

        CuentaCorporativa cuenta = datos.crearCuenta();
        Cliente cliente = datos.crearCliente(
                "10234567", "Cliente nuevo", true, cuenta);
        datos.crearContrato(cliente, EstadoContrato.BORRADOR);

        mvc.perform(get(RUTA).with(datos.autenticacionDe(comercial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientes.activos", is(1)))
                .andExpect(jsonPath("$.clientes.total", is(1)))
                .andExpect(jsonPath("$.contratos.porEstado.BORRADOR", is(1)))
                .andExpect(jsonPath("$.contratos.total", is(1)));
    }
}
