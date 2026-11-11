package com.arise.corp.crm.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.Contrato;
import com.arise.corp.crm.entity.CuentaCorporativa;
import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.crm.entity.TipoDocumento;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Prueba de humo de los repositorios del CRM sobre H2.
 *
 * <p>Comprueba el mapeo de las tres entidades, las busquedas derivadas, los
 * {@code @EntityGraph}, los bloqueos y la resolucion de las proyecciones de
 * conteo. No sustituye las pruebas de integracion con PostgreSQL.
 */
@DataJpaTest
class CrmRepositoriosTests {

    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final Instant AHORA = Instant.parse("2026-11-11T12:00:00Z");

    @Autowired
    private CuentaCorporativaRepository cuentas;

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private ContratoRepository contratos;

    @PersistenceContext
    private EntityManager em;

    private CuentaCorporativa cuenta;
    private Cliente clienteConCuenta;
    private Cliente clienteSinCuenta;
    private Contrato contrato;

    @BeforeEach
    void prepararDatos() {
        cuenta = cuentas.saveAndFlush(CuentaCorporativa.crear(
                "900123456", "Arise Corp.", null, null, null, ACTOR, AHORA));

        clienteConCuenta = clientes.saveAndFlush(Cliente.crear(
                TipoDocumento.CC,
                "10234567",
                "Ana Uno",
                "ana@example.com",
                "+57 300 000 0000",
                null,
                "Bogota",
                cuenta,
                ACTOR,
                AHORA));

        clienteSinCuenta = clientes.saveAndFlush(Cliente.crear(
                TipoDocumento.PASAPORTE,
                "AB12345",
                "Luis Dos",
                "luis@example.com",
                "+57 301 000 0000",
                null,
                null,
                null,
                ACTOR,
                AHORA));

        contrato = contratos.saveAndFlush(Contrato.crear(
                clienteConCuenta,
                "CT-001",
                "Contrato inicial",
                LocalDate.of(2026, 1, 1),
                null,
                new BigDecimal("1500000.00"),
                ACTOR,
                AHORA));

        // Desprendo el contexto para que cada lectura venga de la base.
        em.clear();
    }

    @Test
    void guardaYLeeLasTresEntidades() {
        CuentaCorporativa cuentaLeida = cuentas.findById(cuenta.getId()).orElseThrow();
        assertEquals("900123456", cuentaLeida.getNit());
        assertEquals("Arise Corp.", cuentaLeida.getRazonSocial());
        assertTrue(cuentaLeida.isActiva());
        assertEquals(ACTOR, cuentaLeida.getActualizadoPor());
        assertEquals(AHORA, cuentaLeida.getCreadoEn());

        Cliente clienteLeido = clientes.findById(clienteConCuenta.getId()).orElseThrow();
        assertEquals(TipoDocumento.CC, clienteLeido.getTipoDocumento());
        assertEquals("10234567", clienteLeido.getNumeroDocumento());
        assertEquals("Bogota", clienteLeido.getCiudad());
        assertTrue(clienteLeido.isActivo());
        assertEquals(AHORA, clienteLeido.getCreadoEn());

        Contrato contratoLeido = contratos.findById(contrato.getId()).orElseThrow();
        assertEquals("CT-001", contratoLeido.getCodigo());
        assertEquals(EstadoContrato.BORRADOR, contratoLeido.getEstado());
        assertEquals(new BigDecimal("1500000.00"), contratoLeido.getValor());
        assertEquals("1500000.00", contratoLeido.getValor().toPlainString());
        assertEquals(ACTOR, contratoLeido.getEstadoActualizadoPor());
    }

    @Test
    void resuelveLasBusquedasDerivadas() {
        assertEquals(cuenta.getId(), cuentas.findByNit("900123456").orElseThrow().getId());
        assertEquals(
                clienteConCuenta.getId(),
                clientes.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.CC, "10234567")
                        .orElseThrow()
                        .getId());
        assertEquals(
                clienteSinCuenta.getId(),
                clientes.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.PASAPORTE, "AB12345")
                        .orElseThrow()
                        .getId());
        assertEquals(contrato.getId(), contratos.findByCodigo("CT-001").orElseThrow().getId());

        assertTrue(cuentas.findByNit("000000000").isEmpty());
        assertTrue(clientes
                .findByTipoDocumentoAndNumeroDocumento(TipoDocumento.CE, "99999999")
                .isEmpty());
        assertTrue(contratos.findByCodigo("CT-999").isEmpty());
    }

    @Test
    void cargaLaCuentaDelClienteConEntityGraph() {
        Cliente leido = clientes.findConCuentaById(clienteConCuenta.getId()).orElseThrow();

        assertTrue(Hibernate.isInitialized(leido.getCuentaCorporativa()));
        assertEquals("Arise Corp.", leido.getCuentaCorporativa().getRazonSocial());
    }

    @Test
    void cargaElClienteYSuCuentaDesdeElContratoConEntityGraph() {
        Contrato leido = contratos.findConClienteById(contrato.getId()).orElseThrow();

        assertTrue(Hibernate.isInitialized(leido.getCliente()));
        assertEquals("Ana Uno", leido.getCliente().getNombre());
        assertTrue(Hibernate.isInitialized(leido.getCliente().getCuentaCorporativa()));
        assertEquals("900123456", leido.getCliente().getCuentaCorporativa().getNit());
    }

    @Test
    void resuelveLasConsultasConBloqueo() {
        assertEquals(
                cuenta.getId(),
                cuentas.findParaAsignarById(cuenta.getId()).orElseThrow().getId());
        assertEquals(
                clienteConCuenta.getId(),
                clientes.findParaContratoById(clienteConCuenta.getId()).orElseThrow().getId());
        assertEquals(
                contrato.getId(),
                contratos.findParaModificarById(contrato.getId()).orElseThrow().getId());
    }

    @Test
    void filtraConSpecificationsYOrdena() {
        Specification<Cliente> activos = (root, query, cb) -> cb.isTrue(root.get("activo"));
        Page<Cliente> paginaActivos = clientes.findAll(activos, PageRequest.of(0, 10));
        assertEquals(2, paginaActivos.getTotalElements());

        Specification<Cliente> conCuenta = (root, query, cb) -> cb.equal(
                root.get("cuentaCorporativa").get("id"), cuenta.getId());
        Page<Cliente> paginaConCuenta = clientes.findAll(conCuenta, PageRequest.of(0, 10));
        assertEquals(1, paginaConCuenta.getTotalElements());
        assertEquals(clienteConCuenta.getId(), paginaConCuenta.getContent().getFirst().getId());

        Specification<Contrato> enBorrador = (root, query, cb) -> cb.equal(
                root.get("estado"), EstadoContrato.BORRADOR);
        Page<Contrato> paginaBorradores = contratos.findAll(enBorrador, PageRequest.of(0, 10));
        assertEquals(1, paginaBorradores.getTotalElements());
        assertEquals(contrato.getId(), paginaBorradores.getContent().getFirst().getId());
    }

    @Test
    void resuelveLasProyeccionesDeConteo() {
        Map<Boolean, Long> porActivo = indexar(clientes.contarPorActivo(), ConteoPorClave::getClave);
        assertEquals(2L, porActivo.get(Boolean.TRUE));
        assertFalse(porActivo.containsKey(Boolean.FALSE));

        Map<EstadoContrato, Long> porEstado = indexar(contratos.contarPorEstado(), ConteoPorClave::getClave);
        assertEquals(1L, porEstado.get(EstadoContrato.BORRADOR));
        assertNull(porEstado.get(EstadoContrato.ACTIVO));
        assertEquals(1L, porEstado.values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void exponeElEstadoDeAuditoriaDeLosDatosYDelEstado() {
        contrato.cambiarEstado(EstadoContrato.ACTIVO, ACTOR, AHORA.plusSeconds(60));
        contratos.saveAndFlush(contrato);
        em.clear();

        Contrato leido = contratos.findById(contrato.getId()).orElseThrow();
        assertEquals(EstadoContrato.ACTIVO, leido.getEstado());
        assertEquals(AHORA.plusSeconds(60), leido.getEstadoActualizadoEn());
        assertEquals(AHORA, leido.getActualizadoEn());
        assertEquals(ACTOR, leido.getEstadoActualizadoPor());

        Contrato actualizado = contratos.findById(contrato.getId()).orElseThrow();
        actualizado.actualizarDatos(
                "CT-002",
                "Contrato renombrado",
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("2000000.50"),
                ACTOR,
                AHORA.plusSeconds(120));
        contratos.saveAndFlush(actualizado);
        em.clear();

        Contrato releido = contratos.findById(contrato.getId()).orElseThrow();
        assertEquals("CT-002", releido.getCodigo());
        assertEquals(AHORA.plusSeconds(120), releido.getActualizadoEn());
        assertEquals(AHORA.plusSeconds(60), releido.getEstadoActualizadoEn());
        assertEquals(EstadoContrato.ACTIVO, releido.getEstado());
    }

    private static <K> Map<K, Long> indexar(List<ConteoPorClave<K>> conteos, Function<ConteoPorClave<K>, K> clave) {
        return conteos.stream().collect(Collectors.toMap(clave, ConteoPorClave::getTotal));
    }
}
