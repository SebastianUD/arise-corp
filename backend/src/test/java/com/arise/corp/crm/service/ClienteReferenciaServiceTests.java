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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.arise.corp.crm.CrmDatosPrueba;
import com.arise.corp.crm.dto.ClienteReferencia;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.CuentaCorporativa;
import com.arise.corp.crm.repository.ClienteRepository;

/**
 * Pruebas de la lectura de clientes para otros modulos (D2, DP-ARQ-01).
 *
 * <p>Usa H2 y {@link CrmDatosPrueba}. Comprueba la existencia y el estado en
 * {@code buscar}, la consulta unica y el corto circuito de {@code buscarTodos},
 * y que {@code buscarParaAsociar} exige la transaccion del llamador
 * (Requirements 1.6, 14.11, 25.1).
 */
@SpringBootTest
@Import(CrmDatosPrueba.class)
class ClienteReferenciaServiceTests {

    private static final UUID INEXISTENTE =
            UUID.fromString("00000000-0000-4000-8000-000000000000");

    @Autowired
    private ClienteReferenciaService servicio;

    @Autowired
    private CrmDatosPrueba datos;

    @Autowired
    private TransactionTemplate transacciones;

    @MockitoSpyBean
    private ClienteRepository clientes;

    @BeforeEach
    void limpiar() {
        datos.limpiar();
    }

    @Test
    void buscarDevuelveElClienteActivo() {
        CuentaCorporativa cuenta = datos.crearCuenta();
        Cliente cliente = datos.crearCliente("900123456", "Cliente Activo", true, cuenta);

        Optional<ClienteReferencia> referencia = servicio.buscar(cliente.getId());

        assertTrue(referencia.isPresent());
        assertEquals(cliente.getId(), referencia.get().id());
        assertEquals("Cliente Activo", referencia.get().nombre());
        assertTrue(referencia.get().activo());
    }

    @Test
    void buscarDevuelveElClienteInactivoConSuEstado() {
        CuentaCorporativa cuenta = datos.crearCuenta();
        Cliente cliente = datos.crearCliente("900123457", "Cliente Inactivo", false, cuenta);

        Optional<ClienteReferencia> referencia = servicio.buscar(cliente.getId());

        assertTrue(referencia.isPresent());
        assertEquals(cliente.getId(), referencia.get().id());
        assertFalse(referencia.get().activo());
    }

    @Test
    void buscarDevuelveVacioCuandoElClienteNoExiste() {
        assertTrue(servicio.buscar(INEXISTENTE).isEmpty());
    }

    @Test
    void buscarTodosConColeccionVaciaNoConsultaElRepositorio() {
        Map<UUID, ClienteReferencia> referencias = servicio.buscarTodos(List.of());

        assertTrue(referencias.isEmpty());
        verify(clientes, never()).findAllById(any());
    }

    @Test
    @DisplayName("buscarTodos resuelve ids mezclados, repetidos e inexistentes "
            + "en una sola consulta")
    void buscarTodosResuelveIdsMezcladosYRepetidos() {
        CuentaCorporativa cuenta = datos.crearCuenta();
        Cliente activo = datos.crearCliente("900123458", "Activo", true, cuenta);
        Cliente inactivo = datos.crearCliente("900123459", "Inactivo", false, cuenta);

        Map<UUID, ClienteReferencia> referencias = servicio.buscarTodos(List.of(
                activo.getId(),
                inactivo.getId(),
                activo.getId(),
                INEXISTENTE));

        assertEquals(2, referencias.size());
        assertTrue(referencias.get(activo.getId()).activo());
        assertFalse(referencias.get(inactivo.getId()).activo());
        assertFalse(referencias.containsKey(INEXISTENTE));
        verify(clientes).findAllById(any());
    }

    @Test
    void buscarParaAsociarDevuelveElClienteDentroDeUnaTransaccion() {
        CuentaCorporativa cuenta = datos.crearCuenta();
        Cliente cliente = datos.crearCliente("900123460", "Para Asociar", true, cuenta);

        Optional<ClienteReferencia> referencia = transacciones.execute(estado ->
                servicio.buscarParaAsociar(cliente.getId()));

        assertTrue(referencia.isPresent());
        assertEquals(cliente.getId(), referencia.get().id());
        assertEquals("Para Asociar", referencia.get().nombre());
    }

    @Test
    void buscarParaAsociarFueraDeTransaccionLanzaIllegalTransactionState() {
        CuentaCorporativa cuenta = datos.crearCuenta();
        Cliente cliente = datos.crearCliente("900123461", "Sin Transaccion", true, cuenta);

        assertThrows(
                IllegalTransactionStateException.class,
                () -> servicio.buscarParaAsociar(cliente.getId()));
    }
}
