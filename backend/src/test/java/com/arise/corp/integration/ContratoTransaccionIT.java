package com.arise.corp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.arise.corp.crm.dto.ContratoResponse;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.Contrato;
import com.arise.corp.crm.entity.CuentaCorporativa;
import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.crm.entity.TipoDocumento;
import com.arise.corp.crm.repository.ClienteRepository;
import com.arise.corp.crm.repository.ContratoRepository;
import com.arise.corp.crm.repository.CuentaCorporativaRepository;
import com.arise.corp.crm.service.ContratoService;

/**
 * Transacciones del cambio de estado de un contrato sobre PostgreSQL efimero
 * (Requirements 14.4 y 24.8, DP-SEG-06).
 *
 * <p>Comprueba que un cambio confirmado actualiza juntas las tres columnas del
 * estado ({@code estado}, {@code estado_actualizado_en} y
 * {@code estado_actualizado_por}) sin tocar la auditoria de los datos, y que un
 * rollback posterior a {@link ContratoService#cambiarEstado} no deja rastro.
 * Las lecturas se hacen con SQL directo para no depender de la cache de JPA.
 */
class ContratoTransaccionIT extends PostgresIntegracionBase {

    private static final UUID CREADOR =
            UUID.fromString("00000000-0000-4000-8000-000000000101");
    private static final UUID ACTOR_ACTIVO =
            UUID.fromString("00000000-0000-4000-8000-000000000102");
    private static final UUID ACTOR_FINAL =
            UUID.fromString("00000000-0000-4000-8000-000000000103");
    private static final Instant CREADO_EN =
            Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ContratoService contratoService;

    @Autowired
    private ContratoRepository contratos;

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private CuentaCorporativaRepository cuentas;

    @Autowired
    private PlatformTransactionManager gestorTransacciones;

    private Contrato contrato;

    @BeforeEach
    void prepararContrato() {
        CuentaCorporativa cuenta = cuentas.saveAndFlush(CuentaCorporativa.crear(
                "900000101",
                "Cuenta de transacciones S.A.S.",
                null,
                null,
                null,
                CREADOR,
                CREADO_EN));

        Cliente cliente = clientes.saveAndFlush(Cliente.crear(
                TipoDocumento.CC,
                "10000101",
                "Cliente de transacciones",
                "transacciones@example.test",
                "3001234567",
                null,
                null,
                cuenta,
                CREADOR,
                CREADO_EN));

        contrato = contratos.saveAndFlush(Contrato.crear(
                cliente,
                "CTR-TX-001",
                "Contrato de transacciones",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("1000000.00"),
                CREADOR,
                CREADO_EN));
    }

    @Test
    @DisplayName("Un cambio de estado confirmado actualiza juntas las tres columnas de estado")
    void elCambioConfirmadoActualizaJuntasLasTresColumnas() {
        Map<String, Object> inicial = filaDeEstado(contrato.getId());
        Instant estadoInicialEn = instante(inicial.get("estado_actualizado_en"));
        Instant datosInicialEn = instante(inicial.get("actualizado_en"));

        ContratoResponse activo = contratoService.cambiarEstado(
                contrato.getId(), EstadoContrato.ACTIVO, ACTOR_ACTIVO.toString());

        Map<String, Object> trasActivo = filaDeEstado(contrato.getId());
        Instant estadoActivoEn = instante(trasActivo.get("estado_actualizado_en"));

        assertEquals("ACTIVO", trasActivo.get("estado"),
                "el estado debe cambiar con la transicion");
        assertEquals(ACTOR_ACTIVO, trasActivo.get("estado_actualizado_por"),
                "la auditoria del estado debe registrar al actor del cambio");
        assertNotEquals(estadoInicialEn, estadoActivoEn,
                "la marca del estado debe avanzar");
        assertEquals(estadoActivoEn, activo.estadoActualizadoEn(),
                "el DTO y la fila deben compartir la marca del estado");
        assertEquals(ACTOR_ACTIVO, activo.estadoActualizadoPor());

        assertEquals(datosInicialEn, instante(trasActivo.get("actualizado_en")),
                "la auditoria de los datos no cambia con el estado");
        assertEquals(inicial.get("actualizado_por"), trasActivo.get("actualizado_por"),
                "el actor de los datos no cambia con el estado");

        ContratoResponse finalizado = contratoService.cambiarEstado(
                contrato.getId(), EstadoContrato.FINALIZADO, ACTOR_FINAL.toString());

        Map<String, Object> trasFinal = filaDeEstado(contrato.getId());
        Instant estadoFinalEn = instante(trasFinal.get("estado_actualizado_en"));

        assertEquals("FINALIZADO", trasFinal.get("estado"));
        assertEquals(ACTOR_FINAL, trasFinal.get("estado_actualizado_por"));
        assertTrue(!estadoFinalEn.isBefore(estadoActivoEn),
                "la marca del estado no retrocede en el segundo cambio");
        assertEquals(estadoFinalEn, finalizado.estadoActualizadoEn());
        assertEquals(datosInicialEn, instante(trasFinal.get("actualizado_en")));
        assertEquals(inicial.get("actualizado_por"), trasFinal.get("actualizado_por"));
    }

    @Test
    @DisplayName("El rollback posterior a cambiarEstado conserva el estado y su auditoria")
    void elRollbackConservaElEstadoYSuAuditoria() {
        Map<String, Object> inicial = filaDeEstado(contrato.getId());
        TransactionTemplate plantilla = new TransactionTemplate(gestorTransacciones);

        IllegalStateException fallo = assertThrows(
                IllegalStateException.class,
                () -> plantilla.execute(estado -> {
                    contratoService.cambiarEstado(
                            contrato.getId(),
                            EstadoContrato.ACTIVO,
                            ACTOR_ACTIVO.toString());
                    throw new IllegalStateException(
                            "fallo simulado despues del cambio de estado");
                }));

        assertEquals("fallo simulado despues del cambio de estado", fallo.getMessage());

        Map<String, Object> despues = filaDeEstado(contrato.getId());
        assertEquals(inicial.get("estado"), despues.get("estado"),
                "el rollback no debe cambiar el estado");
        assertEquals(inicial.get("estado_actualizado_por"),
                despues.get("estado_actualizado_por"),
                "el rollback no debe cambiar la auditoria del estado");
        assertEquals(instante(inicial.get("estado_actualizado_en")),
                instante(despues.get("estado_actualizado_en")),
                "el rollback no debe mover la marca del estado");
        assertEquals(instante(inicial.get("actualizado_en")),
                instante(despues.get("actualizado_en")),
                "el rollback no debe cambiar la auditoria de los datos");
        assertEquals(inicial.get("actualizado_por"), despues.get("actualizado_por"));
    }

    private Map<String, Object> filaDeEstado(UUID id) {
        return jdbc.queryForMap("""
                select estado, estado_actualizado_en, estado_actualizado_por,
                       actualizado_en, actualizado_por
                from app.contratos
                where id = ?
                """, id);
    }

    private static Instant instante(Object valor) {
        if (valor instanceof OffsetDateTime offset) {
            return offset.toInstant();
        }
        if (valor instanceof java.sql.Timestamp marca) {
            return marca.toInstant();
        }
        throw new AssertionError("Tipo de dato inesperado para una marca temporal: " + valor);
    }
}
