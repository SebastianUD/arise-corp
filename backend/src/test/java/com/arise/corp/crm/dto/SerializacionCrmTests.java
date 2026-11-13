package com.arise.corp.crm.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.arise.corp.crm.GeneradoresCrm;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.Contrato;
import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.crm.entity.TipoDocumento;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pruebas de serializacion con el {@link ObjectMapper} de la aplicacion
 * (Properties 5 y 6, D13).
 */
@SpringBootTest
class SerializacionCrmTests {

    private static final int CASOS = 120;

    private static final UUID ACTOR =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final Instant AHORA = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final String UUID_TEXTO =
            "123e4567-e89b-12d3-a456-426614174000";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 5: El valor del contrato viaja como texto sin perder precisión")
    void elValorDelContratoViajaComoTextoSinPerderPrecision() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            BigDecimal valor = GeneradoresCrm.valorValido(azar);
            Cliente cliente = Cliente.crear(
                    TipoDocumento.CC,
                    "10234567",
                    "Cliente de prueba",
                    "cliente@example.test",
                    "+57 300 123 4567",
                    null,
                    "Bogotá",
                    null,
                    ACTOR,
                    AHORA);
            Contrato contrato = Contrato.crear(
                    cliente,
                    "CTR-2026-001",
                    "Contrato de prueba",
                    INICIO,
                    null,
                    valor,
                    ACTOR,
                    AHORA);

            String json = objectMapper.writeValueAsString(
                    ContratoResponse.desde(contrato));
            JsonNode nodo = objectMapper.readTree(json).get("valor");

            assertNotNull(nodo, "falta valor en: " + json);
            assertTrue(nodo.isTextual(), "valor debe ser texto en: " + json);

            String texto = nodo.asString();
            assertTrue(texto.matches("[0-9]+\\.[0-9]{2}"),
                    "valor sin dos decimales: " + texto);
            assertEquals(2, new BigDecimal(texto).scale(),
                    "escala incorrecta para " + valor);
            assertEquals(0, new BigDecimal(texto).compareTo(valor),
                    "precision perdida para " + valor + " -> " + texto);
        }
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 6: Los campos controlados por el servidor no se toman del cuerpo")
    void losCamposControladosPorElServidorSeIgnoran() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            ClienteRequest cliente = objectMapper.readValue(
                    conCamposDelServidor(cuerpoCliente(), azar, Set.of("activo")),
                    ClienteRequest.class);

            assertEquals("10234567", cliente.numeroDocumento());
            assertEquals("Cliente de prueba", cliente.nombre());
            assertNull(cliente.direccion());
            assertEquals("Bogotá", cliente.ciudad());

            CuentaCorporativaRequest cuenta = objectMapper.readValue(
                    conCamposDelServidor(cuerpoCuenta(), azar, Set.of("activa")),
                    CuentaCorporativaRequest.class);

            assertEquals("9001234567", cuenta.nit());
            assertEquals("Arise Corp", cuenta.razonSocial());
            assertNull(cuenta.nombreContacto());

            ContratoCrearRequest crear = objectMapper.readValue(
                    conCamposDelServidor(cuerpoContrato(false), azar,
                            Set.of("clienteId")),
                    ContratoCrearRequest.class);

            assertEquals(UUID.fromString(UUID_TEXTO), crear.clienteId());
            assertEquals("CTR-2026-001", crear.codigo());
            assertEquals(0, new BigDecimal("1500000.50")
                    .compareTo(crear.valor()));

            ContratoActualizarRequest actualizar = objectMapper.readValue(
                    conCamposDelServidor(cuerpoContrato(true), azar, Set.of()),
                    ContratoActualizarRequest.class);

            assertEquals("CTR-2026-001", actualizar.codigo());
            assertEquals("Contrato de prueba", actualizar.descripcion());
            assertEquals(0, new BigDecimal("1500000.50")
                    .compareTo(actualizar.valor()));

            EstadoClienteRequest estadoCliente = objectMapper.readValue(
                    conCamposDelServidor("{\"activo\":false}", azar,
                            Set.of("activo")),
                    EstadoClienteRequest.class);

            assertFalse(estadoCliente.activo());

            EstadoCuentaRequest estadoCuenta = objectMapper.readValue(
                    conCamposDelServidor("{\"activa\":false}", azar,
                            Set.of("activa")),
                    EstadoCuentaRequest.class);

            assertFalse(estadoCuenta.activa());

            EstadoContratoRequest estadoContrato = objectMapper.readValue(
                    conCamposDelServidor("{\"estado\":\"BORRADOR\"}", azar,
                            Set.of("estado")),
                    EstadoContratoRequest.class);

            assertEquals(EstadoContrato.BORRADOR, estadoContrato.estado());
        }
    }

    private String cuerpoCliente() {
        return "{"
                + "\"tipoDocumento\":\"CC\","
                + "\"numeroDocumento\":\"1.023.456-7\","
                + "\"nombre\":\"  Cliente de prueba  \","
                + "\"correo\":\"cliente@example.test\","
                + "\"telefono\":\"+57 300 123 4567\","
                + "\"direccion\":\"   \","
                + "\"ciudad\":\" Bogotá \""
                + "}";
    }

    private String cuerpoCuenta() {
        return "{"
                + "\"nit\":\"900.123.456-7\","
                + "\"razonSocial\":\"  Arise Corp  \","
                + "\"nombreContacto\":\"   \","
                + "\"correoContacto\":\"   \","
                + "\"telefonoContacto\":\"   \""
                + "}";
    }

    private String cuerpoContrato(boolean edicion) {
        return "{"
                + (edicion ? "" : "\"clienteId\":\"" + UUID_TEXTO + "\",")
                + "\"codigo\":\" ctr-2026-001 \","
                + "\"descripcion\":\" Contrato de prueba \","
                + "\"fechaInicio\":\"2026-01-01\","
                + "\"fechaFin\":\"2026-12-31\","
                + "\"valor\":\"1500000.50\""
                + "}";
    }

    private String conCamposDelServidor(
            String cuerpo,
            Random azar,
            Set<String> excluir) {

        List<String> extras = new ArrayList<>();

        if (!excluir.contains("id")) {
            extras.add("\"id\":\"" + UUID.randomUUID() + "\"");
        }
        if (!excluir.contains("activo")) {
            extras.add("\"activo\":" + azar.nextBoolean());
        }
        if (!excluir.contains("activa")) {
            extras.add("\"activa\":" + azar.nextBoolean());
        }
        if (!excluir.contains("estado")) {
            extras.add("\"estado\":\""
                    + EstadoContrato.values()[
                            azar.nextInt(EstadoContrato.values().length)]
                    + "\"");
        }
        if (!excluir.contains("clienteId")) {
            extras.add("\"clienteId\":\"" + UUID.randomUUID() + "\"");
        }
        extras.add("\"creadoEn\":\"2026-02-01T00:00:00Z\"");
        extras.add("\"actualizadoEn\":\"2026-03-01T00:00:00Z\"");
        extras.add("\"actualizadoPor\":\"" + UUID.randomUUID() + "\"");
        extras.add("\"estadoActualizadoEn\":\"2026-04-01T00:00:00Z\"");
        extras.add("\"estadoActualizadoPor\":\"" + UUID.randomUUID() + "\"");

        return cuerpo.substring(0, cuerpo.length() - 1)
                + "," + String.join(",", extras) + "}";
    }
}
