package com.arise.corp.crm;

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

import com.arise.corp.login.entity.RolUsuario;

import tools.jackson.databind.ObjectMapper;

/**
 * Los logs del CRM no contienen datos personales (Requirement 6.4, 17.6,
 * CA-06).
 *
 * <p>Crea, edita, provoca un 409 y un 500 con datos ficticios conocidos y
 * comprueba que la salida capturada no contiene el correo, el telefono, la
 * direccion ni el numero de documento. El endpoint de error simula un fallo
 * inesperado sin incluir el cuerpo en la excepcion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({CrmDatosPrueba.class, CrmLogsTests.ConfiguracionError.class})
@ExtendWith(OutputCaptureExtension.class)
class CrmLogsTests {

    private static final String CORREO = "persona.secreta@example.test";
    private static final String TELEFONO = "3109876543";
    private static final String DIRECCION = "Carrera Secreta 987";
    private static final String DOCUMENTO = "55512345";
    private static final String RUTA = "/api/crm/clientes";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CrmDatosPrueba datos;

    private UUID comercial;

    @BeforeEach
    void setUp() {
        datos.limpiar();
        comercial = datos.crearPerfil(RolUsuario.COMERCIAL);
    }

    @Test
    void laSalidaNoContieneDatosPersonales(CapturedOutput salida)
            throws Exception {

        MvcResult creado = mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Ana Secreta")))
                .andExpect(status().isCreated())
                .andReturn();

        UUID id = UUID.fromString(objectMapper.readTree(
                creado.getResponse().getContentAsString())
                .get("id").asString());

        mvc.perform(put(RUTA + "/{id}", id)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Ana Secreta Editada")))
                .andExpect(status().isOk());

        mvc.perform(post(RUTA)
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Duplicada")))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/test/crm-logs/inesperado")
                        .with(datos.autenticacionDe(comercial))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Fallo")))
                .andExpect(status().isInternalServerError());

        String registrado = salida.getAll();

        assertFalse(registrado.contains(CORREO), "el log contiene el correo");
        assertFalse(registrado.contains(TELEFONO), "el log contiene el telefono");
        assertFalse(registrado.contains(DIRECCION), "el log contiene la direccion");
        assertFalse(registrado.contains(DOCUMENTO), "el log contiene el documento");
    }

    private String cuerpo(String nombre) {
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("tipoDocumento", "CC");
        contenido.put("numeroDocumento", DOCUMENTO);
        contenido.put("nombre", nombre);
        contenido.put("correo", CORREO);
        contenido.put("telefono", TELEFONO);
        contenido.put("direccion", DIRECCION);
        contenido.put("ciudad", "Medellin");
        contenido.put("cuentaCorporativaId", null);
        return objectMapper.writeValueAsString(contenido);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionError {

        @Bean
        EndpointError endpointError() {
            return new EndpointError();
        }
    }

    @RestController
    static class EndpointError {

        @PostMapping("/api/test/crm-logs/inesperado")
        String inesperado(@RequestBody Map<String, Object> cuerpo) {
            throw new IllegalStateException("Fallo simulado");
        }
    }
}
