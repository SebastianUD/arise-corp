package com.arise.corp.sga;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.arise.corp.crm.CrmDatosPrueba;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.service.ContratoReferenciaService;
import com.arise.corp.login.entity.RolUsuario;

import tools.jackson.databind.ObjectMapper;

/**
 * Comprueba que la salida del SGA no contiene la direccion de entrega, la
 * observacion ni el nombre del cliente (Requirements 17.3, 25.7, CA-06).
 *
 * <p>Se crea y se edita un paquete sin contrato, se provoca un 409 de codigo
 * duplicado y un 500 con un alta que lleva {@code contratoId} hasta un doble de
 * {@link ContratoReferenciaService} cuyo {@code buscarParaAsociar} lanza una
 * excepcion sin datos. Los textos ficticios son conocidos y no deben aparecer
 * en la salida capturada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({CrmDatosPrueba.class, SgaDatosPrueba.class})
@ExtendWith(OutputCaptureExtension.class)
class SgaLogsTests {

    private static final String RUTA = "/api/sga/paquetes";
    private static final String DIRECCION = "Carrera Secreta 741, Bogota";
    private static final String OBSERVACION = "Observacion secreta del paquete";
    private static final String NOMBRE_CLIENTE = "Cliente Secreto S.A.S.";
    private static final String DESCRIPCION = "Descripcion ficticia del paquete";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CrmDatosPrueba crm;

    @Autowired
    private SgaDatosPrueba datos;

    @MockitoBean
    private ContratoReferenciaService contratosCrm;

    private UUID operaciones;

    @BeforeEach
    void setUp() {
        datos.limpiar();
        operaciones = crm.crearPerfil(RolUsuario.OPERACIONES);
    }

    @Test
    void laSalidaNoContieneDatosDelPaqueteNiDelCliente(CapturedOutput salida)
            throws Exception {

        Cliente cliente = crm.crearCliente(
                "70012345", NOMBRE_CLIENTE, true, crm.crearCuenta());

        MvcResult creado = mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(paquete("PAQ-900", cliente.getId(),
                                null, OBSERVACION))))
                .andExpect(status().isCreated())
                .andReturn();

        UUID id = UUID.fromString(objectMapper.readTree(
                        creado.getResponse().getContentAsString())
                .get("id").asString());

        Map<String, Object> edicion = new LinkedHashMap<>();
        edicion.put("descripcion", DESCRIPCION + " editada");
        edicion.put("direccionEntrega", DIRECCION + " editada");

        mvc.perform(put(RUTA + "/" + id)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(edicion)))
                .andExpect(status().isOk());

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(paquete("PAQ-900", cliente.getId(),
                                null, OBSERVACION))))
                .andExpect(status().isConflict());

        UUID contratoId = UUID.randomUUID();
        when(contratosCrm.buscarParaAsociar(contratoId)).thenThrow(
                new IllegalStateException("fallo simulado sin datos"));

        mvc.perform(post(RUTA)
                        .with(crm.autenticacionDe(operaciones))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(paquete("PAQ-901", cliente.getId(),
                                contratoId, OBSERVACION))))
                .andExpect(status().isInternalServerError());

        String registrado = salida.getAll();

        assertSinDato(registrado, DIRECCION, "la direccion de entrega");
        assertSinDato(registrado, DIRECCION + " editada",
                "la direccion de entrega editada");
        assertSinDato(registrado, OBSERVACION, "la observacion");
        assertSinDato(registrado, NOMBRE_CLIENTE, "el nombre del cliente");
    }

    private static void assertSinDato(
            String registrado, String dato, String descripcion) {

        assertFalse(registrado.contains(dato), "el log contiene " + descripcion);
    }

    private Map<String, Object> paquete(
            String codigo,
            UUID clienteId,
            UUID contratoId,
            String observacion) {

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("codigo", codigo);
        cuerpo.put("descripcion", DESCRIPCION);
        cuerpo.put("direccionEntrega", DIRECCION);
        cuerpo.put("clienteId", clienteId.toString());
        cuerpo.put("observacion", observacion);

        if (contratoId != null) {
            cuerpo.put("contratoId", contratoId.toString());
        }

        return cuerpo;
    }

    private String cuerpo(Map<String, Object> valores) {
        return objectMapper.writeValueAsString(valores);
    }
}
