package com.arise.corp.config;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.arise.corp.crm.CrmDatosPrueba;
import com.arise.corp.erp.ErpDatosPrueba;
import com.arise.corp.login.entity.RolUsuario;

import tools.jackson.databind.ObjectMapper;

/**
 * Demuestra que Spring MVC valida con el reloj de la aplicacion (D7,
 * Requirement 4.7).
 *
 * <p>Declara un {@link Clock} {@code @Primary} fijo en
 * {@code 2026-11-21T03:30:00Z}, que en {@code America/Bogota} ya es 20 de
 * noviembre. El {@code ValidationConfigurationCustomizer} de
 * {@link ClockConfig} entrega ese reloj a Bean Validation, asi que la fecha de
 * ingreso del 21 se rechaza y la del 20 se acepta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
        CrmDatosPrueba.class,
        ErpDatosPrueba.class,
        ClockConfigTests.RelojFijo.class
})
class ClockConfigTests {

    private static final String RUTA = "/api/erp/empleados";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CrmDatosPrueba crm;

    @Autowired
    private ErpDatosPrueba datos;

    private UUID administrador;

    @BeforeEach
    void setUp() {
        datos.limpiar();
        administrador = crm.crearPerfil(RolUsuario.ADMINISTRADOR);
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 3: Las validaciones de formato coinciden con la regla de referencia")
    void laFechaDeIngresoSeValidaConElRelojDeLaAplicacion() throws Exception {
        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("10234567", "2026-11-21")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field", is("fechaIngreso")))
                .andExpect(jsonPath("$.errors[0].message",
                        is("no puede ser posterior a la fecha actual")));

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("10234568", "2026-11-20")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaIngreso", is("2026-11-20")));
    }

    private String cuerpo(String documento, String fechaIngreso) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipoDocumento", "CC");
        cuerpo.put("numeroDocumento", documento);
        cuerpo.put("nombre", "Empleada de Reloj");
        cuerpo.put("cargo", "Analista");
        cuerpo.put("area", "Finanzas");
        cuerpo.put("correo", "reloj" + documento + "@example.test");
        cuerpo.put("telefono", "+57 300 000 0000");
        cuerpo.put("fechaIngreso", fechaIngreso);

        return objectMapper.writeValueAsString(cuerpo);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RelojFijo {

        @Bean
        @Primary
        Clock relojDePrueba() {
            return Clock.fixed(
                    Instant.parse("2026-11-21T03:30:00Z"), ZoneId.of("UTC"));
        }
    }
}
