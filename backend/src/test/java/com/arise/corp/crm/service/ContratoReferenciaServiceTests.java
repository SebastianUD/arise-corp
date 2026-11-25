package com.arise.corp.crm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.arise.corp.crm.CrmDatosPrueba;
import com.arise.corp.crm.dto.ContratoReferencia;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.Contrato;
import com.arise.corp.crm.entity.CuentaCorporativa;
import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.crm.repository.ContratoRepository;

/**
 * Pruebas de la lectura de contratos para otros modulos (D2, DP-ARQ-01).
 *
 * <p>Usa H2 y {@link CrmDatosPrueba}. Comprueba el cliente y el codigo en
 * {@code buscar}, {@code activo()} en los cuatro estados, la consulta unica y
 * el corto circuito de {@code buscarTodos}, y que {@code buscarParaAsociar}
 * exige la transaccion del llamador (Requirements 1.6, 7.12, NF-07).
 */
@SpringBootTest
@Import(CrmDatosPrueba.class)
class ContratoReferenciaServiceTests {

    private static final UUID INEXISTENTE =
            UUID.fromString("00000000-0000-4000-8000-000000000000");

    @Autowired
    private ContratoReferenciaService servicio;

    @Autowired
    private CrmDatosPrueba datos;

    @Autowired
    private TransactionTemplate transacciones;

    @MockitoSpyBean
    private ContratoRepository contratos;

    @BeforeEach
    void limpiar() {
        datos.limpiar();
    }

    @Test
    void buscarDevuelveElContratoConSuClienteYCodigo() {
        CuentaCorporativa cuenta = datos.crearCuenta();
        Cliente cliente = datos.crearCliente(cuenta);
        Contrato contrato = datos.crearContrato(
                "CTR-LECTURA-01", cliente, EstadoContrato.ACTIVO);

        Optional<ContratoReferencia> referencia = servicio.buscar(contrato.getId());

        assertTrue(referencia.isPresent());
        assertEquals(contrato.getId(), referencia.get().id());
        assertEquals(cliente.getId(), referencia.get().clienteId());
        assertEquals("CTR-LECTURA-01", referencia.get().codigo());
        assertEquals("ACTIVO", referencia.get().estado());
        assertTrue(referencia.get().activo());
    }

    @Test
    void buscarDevuelveVacioCuandoElContratoNoExiste() {
        assertTrue(servicio.buscar(INEXISTENTE).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(EstadoContrato.class)
    @DisplayName("activo() solo es true para ACTIVO")
    void activoSoloEsTrueParaActivo(EstadoContrato estado) {
        CuentaCorporativa cuenta = datos.crearCuenta();
        Cliente cliente = datos.crearCliente(cuenta);
        Contrato contrato = datos.crearContrato(cliente, estado);

        ContratoReferencia referencia = servicio.buscar(contrato.getId())
                .orElseThrow();

        assertEquals(estado.name(), referencia.estado());
        assertEquals(estado == EstadoContrato.ACTIVO, referencia.activo());
    }

    @Test
    void buscarTodosConColeccionVaciaNoConsultaElRepositorio() {
        Map<UUID, ContratoReferencia> referencias = servicio.buscarTodos(List.of());

        assertTrue(referencias.isEmpty());
        verify(contratos, never()).findAllById(any());
    }

    @Test
    @DisplayName("buscarTodos resuelve ids mezclados, repetidos e inexistentes "
            + "en una sola consulta")
    void buscarTodosResuelveIdsMezcladosYRepetidos() {
        CuentaCorporativa cuenta = datos.crearCuenta();
        Cliente cliente = datos.crearCliente(cuenta);
        Contrato activo = datos.crearContrato(
                "CTR-LOTE-01", cliente, EstadoContrato.ACTIVO);
        Contrato borrador = datos.crearContrato(
                "CTR-LOTE-02", cliente, EstadoContrato.BORRADOR);

        Map<UUID, ContratoReferencia> referencias = servicio.buscarTodos(List.of(
                activo.getId(),
                borrador.getId(),
                activo.getId(),
                INEXISTENTE));

        assertEquals(2, referencias.size());
        assertTrue(referencias.get(activo.getId()).activo());
        assertEquals("CTR-LOTE-01", referencias.get(activo.getId()).codigo());
        assertFalse(referencias.get(borrador.getId()).activo());
        assertEquals(cliente.getId(), referencias.get(borrador.getId()).clienteId());
        assertFalse(referencias.containsKey(INEXISTENTE));
        verify(contratos).findAllById(any());
    }

    @Test
    void buscarParaAsociarDevuelveElContratoDentroDeUnaTransaccion() {
        CuentaCorporativa cuenta = datos.crearCuenta();
        Cliente cliente = datos.crearCliente(cuenta);
        Contrato contrato = datos.crearContrato(
                "CTR-ASOCIAR-01", cliente, EstadoContrato.ACTIVO);

        Optional<ContratoReferencia> referencia = transacciones.execute(estado ->
                servicio.buscarParaAsociar(contrato.getId()));

        assertTrue(referencia.isPresent());
        assertEquals(contrato.getId(), referencia.get().id());
        assertEquals(cliente.getId(), referencia.get().clienteId());
        assertEquals("CTR-ASOCIAR-01", referencia.get().codigo());
    }

    @Test
    void buscarParaAsociarFueraDeTransaccionLanzaIllegalTransactionState() {
        CuentaCorporativa cuenta = datos.crearCuenta();
        Cliente cliente = datos.crearCliente(cuenta);
        Contrato contrato = datos.crearContrato(cliente);

        assertThrows(
                IllegalTransactionStateException.class,
                () -> servicio.buscarParaAsociar(contrato.getId()));
    }
}
