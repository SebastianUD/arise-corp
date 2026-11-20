package com.arise.corp.erp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.arise.corp.crm.CrmDatosPrueba;
import com.arise.corp.login.entity.RolUsuario;

import tools.jackson.databind.ObjectMapper;

/**
 * Los logs del ERP no contienen datos personales (Requirement 8.3, 25.8,
 * CA-06).
 *
 * <p>Crea y edita un empleado y un proveedor, provoca un 409 de duplicado y un
 * 500 con datos ficticios conocidos y comprueba que la salida capturada no
 * contiene el nombre, el numero de documento, el correo, el telefono ni la
 * direccion. El endpoint de error simula un fallo inesperado sin incluir el
 * cuerpo en la excepcion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({CrmDatosPrueba.class, ErpDatosPrueba.class,
        ErpLogsTests.ConfiguracionError.class})
@ExtendWith(OutputCaptureExtension.class)
class ErpLogsTests {

    private static final String DOCUMENTO_EMPLEADO = "55512345";
    private static final String NOMBRE_EMPLEADO = "Empleado Secreto";
    private static final String CORREO_EMPLEADO = "empleado.secreto@example.test";
    private static final String TELEFONO_EMPLEADO = "3109876543";

    private static final String DOCUMENTO_PROVEEDOR = "900555123";
    private static final String NOMBRE_PROVEEDOR = "Proveedor Secreto S.A.S.";
    private static final String CORREO_PROVEEDOR = "proveedor.secreto@example.test";
    private static final String TELEFONO_PROVEEDOR = "6015551234";
    private static final String DIRECCION_PROVEEDOR = "Carrera Secreta 987";

    private static final String EMPLEADOS = "/api/erp/empleados";
    private static final String PROVEEDORES = "/api/erp/proveedores";

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
    void laSalidaNoContieneDatosPersonales(CapturedOutput salida)
            throws Exception {

        MvcResult creado = mvc.perform(post(EMPLEADOS)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(empleado("Empleado Secreto"))))
                .andExpect(status().isCreated())
                .andReturn();

        UUID idEmpleado = UUID.fromString(objectMapper.readTree(
                creado.getResponse().getContentAsString())
                .get("id").asString());

        mvc.perform(put(EMPLEADOS + "/{id}", idEmpleado)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(empleado("Empleado Secreto Editado"))))
                .andExpect(status().isOk());

        MvcResult proveedor = mvc.perform(post(PROVEEDORES)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(proveedor())))
                .andExpect(status().isCreated())
                .andReturn();

        UUID idProveedor = UUID.fromString(objectMapper.readTree(
                proveedor.getResponse().getContentAsString())
                .get("id").asString());

        mvc.perform(put(PROVEEDORES + "/{id}", idProveedor)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(proveedor())))
                .andExpect(status().isOk());

        mvc.perform(post(EMPLEADOS)
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(empleado("Empleado Duplicado"))))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/test/erp-logs/inesperado")
                        .with(crm.autenticacionDe(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(empleado("Fallo"))))
                .andExpect(status().isInternalServerError());

        String registrado = salida.getAll();

        assertSinDato(registrado, NOMBRE_EMPLEADO, "el nombre del empleado");
        assertSinDato(registrado, "Empleado Secreto Editado",
                "el nombre editado del empleado");
        assertSinDato(registrado, DOCUMENTO_EMPLEADO,
                "el documento del empleado");
        assertSinDato(registrado, CORREO_EMPLEADO, "el correo del empleado");
        assertSinDato(registrado, TELEFONO_EMPLEADO, "el telefono del empleado");
        assertSinDato(registrado, NOMBRE_PROVEEDOR, "el nombre del proveedor");
        assertSinDato(registrado, DOCUMENTO_PROVEEDOR,
                "el documento del proveedor");
        assertSinDato(registrado, CORREO_PROVEEDOR, "el correo del proveedor");
        assertSinDato(registrado, TELEFONO_PROVEEDOR, "el telefono del proveedor");
        assertSinDato(registrado, DIRECCION_PROVEEDOR,
                "la direccion del proveedor");
    }

    private static void assertSinDato(
            String registrado, String dato, String descripcion) {

        assertFalse(registrado.contains(dato), "el log contiene " + descripcion);
    }

    private Map<String, Object> empleado(String nombre) {
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("tipoDocumento", "CC");
        contenido.put("numeroDocumento", DOCUMENTO_EMPLEADO);
        contenido.put("nombre", nombre);
        contenido.put("cargo", "Analista");
        contenido.put("area", "Finanzas");
        contenido.put("correo", CORREO_EMPLEADO);
        contenido.put("telefono", TELEFONO_EMPLEADO);
        contenido.put("fechaIngreso", "2025-03-01");
        return contenido;
    }

    private Map<String, Object> proveedor() {
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("tipoDocumento", "NIT");
        contenido.put("numeroDocumento", DOCUMENTO_PROVEEDOR);
        contenido.put("nombre", NOMBRE_PROVEEDOR);
        contenido.put("nombreContacto", "Contacto Secreto");
        contenido.put("correo", CORREO_PROVEEDOR);
        contenido.put("telefono", TELEFONO_PROVEEDOR);
        contenido.put("direccion", DIRECCION_PROVEEDOR);
        contenido.put("ciudad", "Medellin");
        return contenido;
    }

    private String cuerpo(Map<String, Object> contenido) {
        return objectMapper.writeValueAsString(contenido);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionError {

        @Bean
        EndpointErrorErp endpointErrorErp() {
            return new EndpointErrorErp();
        }
    }

    @RestController
    static class EndpointErrorErp {

        @PostMapping("/api/test/erp-logs/inesperado")
        String inesperado(@RequestBody Map<String, Object> cuerpo) {
            throw new IllegalStateException("Fallo simulado");
        }
    }
}
