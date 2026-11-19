package com.arise.corp.erp.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.arise.corp.erp.GeneradoresErp;
import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.MovimientoFinanciero;
import com.arise.corp.erp.entity.TipoMovimiento;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pruebas de serializacion del ERP con el {@link ObjectMapper} de la
 * aplicacion (Properties 5 y 6, D13).
 *
 * <p>El valor de un movimiento viaja como texto con dos decimales, tanto al
 * serializar la respuesta como al leer una solicitud enviada como texto o como
 * numero. Las propiedades desconocidas del cuerpo se ignoran y las relaciones
 * ausentes se serializan como {@code null}.
 */
@SpringBootTest
class SerializacionErpTests {

    private static final int CASOS = 120;

    private static final UUID ACTOR =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final Instant AHORA = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDate FECHA = LocalDate.of(2026, 1, 15);
    private static final String CONCEPTO = "Movimiento de prueba";
    private static final String REFERENCIA = "FAC-2026-001";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 5: Los importes viajan como texto sin perder precisión")
    void elValorViajaComoTextoConDosDecimales() {
        Random azar = GeneradoresErp.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            BigDecimal valor = GeneradoresErp.valorPositivoValido(azar);
            MovimientoResponse respuesta = respuesta(valor);

            String json = objectMapper.writeValueAsString(respuesta);
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
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 5: Los importes viajan como texto sin perder precisión")
    void elValorDeIdaYVueltaConservaLaPrecision() {
        Random azar = GeneradoresErp.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            BigDecimal valor = GeneradoresErp.valorPositivoValido(azar);
            MovimientoResponse respuesta = respuesta(valor);

            String json = objectMapper.writeValueAsString(respuesta);
            MovimientoRequest desdeTexto = objectMapper.readValue(
                    json, MovimientoRequest.class);

            assertEquals(0, desdeTexto.valor().compareTo(valor),
                    "ida y vuelta como texto para " + valor);
            assertEquals(TipoMovimiento.INGRESO, desdeTexto.tipo());
            assertEquals(FECHA, desdeTexto.fecha());
            assertEquals(CONCEPTO, desdeTexto.concepto());
            assertEquals(REFERENCIA, desdeTexto.referencia());

            MovimientoRequest desdeNumero = objectMapper.readValue(
                    solicitudJson(valor, Map.of()), MovimientoRequest.class);

            assertEquals(0, desdeNumero.valor().compareTo(valor),
                    "ida y vuelta como numero para " + valor);
        }
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 6: Los campos controlados por el servidor no se toman del cuerpo")
    void lasPropiedadesDesconocidasSeIgnoran() {
        Random azar = GeneradoresErp.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            BigDecimal valor = GeneradoresErp.valorPositivoValido(azar);
            Map<String, Object> extras = camposDelServidor(azar);

            MovimientoRequest solicitud = objectMapper.readValue(
                    solicitudJson(valor, extras), MovimientoRequest.class);

            assertEquals(TipoMovimiento.INGRESO, solicitud.tipo());
            assertEquals(FECHA, solicitud.fecha());
            assertEquals(CONCEPTO, solicitud.concepto());
            assertEquals(REFERENCIA, solicitud.referencia());
            assertEquals(0, solicitud.valor().compareTo(valor));
            assertNull(solicitud.clienteId());
            assertNull(solicitud.proveedorId());

            EstadoMovimientoRequest estado = objectMapper.readValue(
                    "{\"estado\":\"PAGADO\",\"id\":\""
                            + UUID.randomUUID() + "\",\"creadoEn\":\""
                            + AHORA + "\",\"desconocido\":true}",
                    EstadoMovimientoRequest.class);

            assertEquals(EstadoMovimiento.PAGADO, estado.estado());
        }
    }

    @Test
    void clienteYProveedorSeSerializanComoNulos() {
        MovimientoResponse respuesta = respuesta(new BigDecimal("10.00"));

        String json = objectMapper.writeValueAsString(respuesta);
        JsonNode nodo = objectMapper.readTree(json);

        assertTrue(nodo.get("cliente").isNull(), json);
        assertTrue(nodo.get("proveedor").isNull(), json);

        MovimientoRequest solicitud = objectMapper.readValue(
                solicitudJson(new BigDecimal("10.00"), Map.of()),
                MovimientoRequest.class);

        assertNull(solicitud.clienteId());
        assertNull(solicitud.proveedorId());
    }

    @Test
    void elRegistroAsociadoDevuelveNuloConEntradaNula() {
        assertNull(RegistroAsociado.desde((com.arise.corp.erp.entity.Proveedor) null));
        assertNull(RegistroAsociado.desde(
                (com.arise.corp.crm.dto.ClienteReferencia) null));
    }

    @Test
    void elMovimientoConClienteExigeLaReferenciaDelCrm() {
        MovimientoFinanciero movimiento = MovimientoFinanciero.crear(
                TipoMovimiento.INGRESO,
                FECHA,
                CONCEPTO,
                new BigDecimal("10.00"),
                REFERENCIA,
                UUID.randomUUID(),
                null,
                ACTOR,
                AHORA);

        assertThrows(NullPointerException.class,
                () -> MovimientoResponse.desde(movimiento, null));
    }

    private MovimientoResponse respuesta(BigDecimal valor) {
        MovimientoFinanciero movimiento = MovimientoFinanciero.crear(
                TipoMovimiento.INGRESO,
                FECHA,
                CONCEPTO,
                valor,
                REFERENCIA,
                null,
                null,
                ACTOR,
                AHORA);

        return MovimientoResponse.desde(movimiento, null);
    }

    private String solicitudJson(BigDecimal valor, Map<String, Object> extras) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipo", "INGRESO");
        cuerpo.put("fecha", FECHA.toString());
        cuerpo.put("concepto", CONCEPTO);
        cuerpo.put("valor", valor);
        cuerpo.put("referencia", REFERENCIA);
        cuerpo.put("clienteId", null);
        cuerpo.put("proveedorId", null);
        cuerpo.putAll(extras);

        return objectMapper.writeValueAsString(cuerpo);
    }

    private static Map<String, Object> camposDelServidor(Random azar) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("id", UUID.randomUUID().toString());
        extras.put("estado", azar.nextBoolean() ? "PAGADO" : "ANULADO");
        extras.put("creadoEn", AHORA.toString());
        extras.put("actualizadoEn", AHORA.plusSeconds(1).toString());
        extras.put("actualizadoPor", UUID.randomUUID().toString());
        extras.put("estadoActualizadoEn", AHORA.plusSeconds(2).toString());
        extras.put("estadoActualizadoPor", UUID.randomUUID().toString());
        extras.put("activo", azar.nextBoolean());
        extras.put("campoDesconocido", "ignorado");
        return extras;
    }
}
