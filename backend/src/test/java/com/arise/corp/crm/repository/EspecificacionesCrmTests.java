package com.arise.corp.crm.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
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
 * Filtros y busquedas de {@link EspecificacionesCrm} sobre H2.
 *
 * <p>Comprueba la busqueda por nombre o documento, el NIT normalizado, los
 * comodines escapados y que los filtros ausentes no restringen. La
 * equivalencia completa con PostgreSQL se verifica en {@code ConsultasCrmIT}.
 */
@DataJpaTest
class EspecificacionesCrmTests {

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

    private CuentaCorporativa cuentaActiva;
    private CuentaCorporativa cuentaInactiva;
    private Cliente ana;
    private Cliente luis;
    private Cliente marta;
    private Cliente porcentaje;
    private Cliente guionBajo;
    private Contrato contratoBorrador;
    private Contrato contratoActivo;

    @BeforeEach
    void prepararDatos() {
        cuentaActiva = cuentas.saveAndFlush(CuentaCorporativa.crear(
                "900123456", "Empresa Uno S.A.S.", null, null, null, ACTOR, AHORA));

        cuentaInactiva = cuentas.saveAndFlush(CuentaCorporativa.crear(
                "800654321", "Comercial Dos Ltda.", null, null, null, ACTOR, AHORA));
        cuentaInactiva.cambiarEstado(false, ACTOR, AHORA.plusSeconds(1));
        cuentas.saveAndFlush(cuentaInactiva);

        ana = clientes.saveAndFlush(Cliente.crear(
                TipoDocumento.CC, "10234567", "Ana Uno", "ana@example.test",
                "+57 300 000 0000", null, "Bogota", cuentaActiva, ACTOR, AHORA));

        luis = clientes.saveAndFlush(Cliente.crear(
                TipoDocumento.PASAPORTE, "AB12345", "Luis Dos", "luis@example.test",
                "+57 301 000 0000", null, null, null, ACTOR, AHORA));
        luis.cambiarEstado(false, ACTOR, AHORA.plusSeconds(1));
        clientes.saveAndFlush(luis);

        marta = clientes.saveAndFlush(Cliente.crear(
                TipoDocumento.CE, "77888999", "Marta Cien", "marta@example.test",
                "+57 302 000 0000", null, "Cali", cuentaInactiva, ACTOR, AHORA));

        porcentaje = clientes.saveAndFlush(Cliente.crear(
                TipoDocumento.CC, "40000001", "Cien % Real", "porcentaje@example.test",
                "+57 303 000 0000", null, null, null, ACTOR, AHORA));

        guionBajo = clientes.saveAndFlush(Cliente.crear(
                TipoDocumento.CC, "40000002", "Guion_Bajo", "guion@example.test",
                "+57 304 000 0000", null, null, null, ACTOR, AHORA));

        contratoBorrador = contratos.saveAndFlush(Contrato.crear(
                ana, "CT-001", "Contrato en borrador",
                LocalDate.of(2026, 1, 1), null,
                new BigDecimal("1500000.00"), ACTOR, AHORA));

        contratoActivo = contratos.saveAndFlush(Contrato.crear(
                luis, "CT-002", "Contrato activo",
                LocalDate.of(2026, 2, 1), null,
                new BigDecimal("2000000.00"), ACTOR, AHORA));
        contratoActivo.cambiarEstado(EstadoContrato.ACTIVO, ACTOR, AHORA.plusSeconds(1));
        contratos.saveAndFlush(contratoActivo);

        em.clear();
    }

    @Test
    void buscaClientesPorNombreSinDistinguirMayusculas() {
        assertEquals(List.of(ana.getId()), idsClientes(EspecificacionesCrm.nombreODocumentoContiene("ana")));
        assertEquals(List.of(ana.getId()), idsClientes(EspecificacionesCrm.nombreODocumentoContiene("ANA")));
        assertEquals(List.of(luis.getId()), idsClientes(EspecificacionesCrm.nombreODocumentoContiene("  luis  ")));
    }

    @Test
    void buscaClientesPorDocumentoNormalizado() {
        assertEquals(
                List.of(ana.getId()),
                idsClientes(EspecificacionesCrm.nombreODocumentoContiene("1023.4567")));
        assertEquals(
                List.of(luis.getId()),
                idsClientes(EspecificacionesCrm.nombreODocumentoContiene("ab-12345")));
    }

    @Test
    void escapaLosComodinesYLaBarraInvertida() {
        assertEquals(
                List.of(porcentaje.getId()),
                idsClientes(EspecificacionesCrm.nombreODocumentoContiene("%")));
        assertEquals(
                List.of(guionBajo.getId()),
                idsClientes(EspecificacionesCrm.nombreODocumentoContiene("_")));
        assertTrue(idsClientes(EspecificacionesCrm.nombreODocumentoContiene("\\")).isEmpty());
    }

    @Test
    void ignoraLosFiltrosAusentes() {
        assertEquals(5, totalClientes(EspecificacionesCrm.nombreODocumentoContiene("   ")));
        assertEquals(5, totalClientes(EspecificacionesCrm.activoIgual(null)));
        assertEquals(5, totalClientes(EspecificacionesCrm.cuentaIgual(null)));
        assertEquals(5, totalClientes(Specification.allOf(List.of(
                EspecificacionesCrm.nombreODocumentoContiene(null),
                EspecificacionesCrm.activoIgual(null),
                EspecificacionesCrm.cuentaIgual(null)))));

        assertEquals(2, totalCuentas(EspecificacionesCrm.razonSocialONitContiene(" ")));
        assertEquals(2, totalCuentas(EspecificacionesCrm.activaIgual(null)));
        assertEquals(2, totalContratos(EspecificacionesCrm.clienteIgual(null)));
        assertEquals(2, totalContratos(EspecificacionesCrm.estadoIgual(null)));
    }

    @Test
    void filtraClientesPorActivoYPorCuenta() {
        assertEquals(4, totalClientes(EspecificacionesCrm.activoIgual(true)));
        assertEquals(List.of(luis.getId()), idsClientes(EspecificacionesCrm.activoIgual(false)));
        assertEquals(
                List.of(ana.getId()),
                idsClientes(EspecificacionesCrm.cuentaIgual(cuentaActiva.getId())));
        assertEquals(
                List.of(marta.getId()),
                idsClientes(EspecificacionesCrm.cuentaIgual(cuentaInactiva.getId())));
        assertTrue(idsClientes(EspecificacionesCrm.cuentaIgual(UUID.randomUUID())).isEmpty());
    }

    @Test
    void combinaLosFiltrosDeCliente() {
        assertEquals(
                List.of(ana.getId()),
                idsClientes(Specification.allOf(List.of(
                        EspecificacionesCrm.nombreODocumentoContiene("ana"),
                        EspecificacionesCrm.activoIgual(true),
                        EspecificacionesCrm.cuentaIgual(cuentaActiva.getId())))));

        assertTrue(idsClientes(Specification.allOf(List.of(
                EspecificacionesCrm.nombreODocumentoContiene("ana"),
                EspecificacionesCrm.cuentaIgual(cuentaInactiva.getId())))).isEmpty());
    }

    @Test
    void buscaCuentasPorRazonSocialSinDistinguirMayusculasOPorNitNormalizado() {
        assertEquals(
                List.of(cuentaActiva.getId()),
                idsCuentas(EspecificacionesCrm.razonSocialONitContiene("empresa")));
        assertEquals(
                List.of(cuentaInactiva.getId()),
                idsCuentas(EspecificacionesCrm.razonSocialONitContiene("COMERCIAL")));
        assertEquals(
                List.of(cuentaActiva.getId()),
                idsCuentas(EspecificacionesCrm.razonSocialONitContiene("900.123.456")));
    }

    @Test
    void filtraCuentasPorActiva() {
        assertEquals(
                List.of(cuentaActiva.getId()),
                idsCuentas(EspecificacionesCrm.activaIgual(true)));
        assertEquals(
                List.of(cuentaInactiva.getId()),
                idsCuentas(EspecificacionesCrm.activaIgual(false)));
    }

    @Test
    void filtraContratosPorClienteYPorEstado() {
        assertEquals(
                List.of(contratoBorrador.getId()),
                idsContratos(EspecificacionesCrm.clienteIgual(ana.getId())));
        assertEquals(
                List.of(contratoActivo.getId()),
                idsContratos(EspecificacionesCrm.clienteIgual(luis.getId())));
        assertTrue(idsContratos(
                EspecificacionesCrm.clienteIgual(UUID.randomUUID())).isEmpty());

        assertEquals(
                List.of(contratoBorrador.getId()),
                idsContratos(EspecificacionesCrm.estadoIgual(EstadoContrato.BORRADOR)));
        assertEquals(
                List.of(contratoActivo.getId()),
                idsContratos(EspecificacionesCrm.estadoIgual(EstadoContrato.ACTIVO)));
        assertTrue(idsContratos(
                EspecificacionesCrm.estadoIgual(EstadoContrato.FINALIZADO)).isEmpty());
    }

    @Test
    void combinaLosFiltrosDeContrato() {
        assertEquals(
                List.of(contratoBorrador.getId()),
                idsContratos(Specification.allOf(List.of(
                        EspecificacionesCrm.clienteIgual(ana.getId()),
                        EspecificacionesCrm.estadoIgual(EstadoContrato.BORRADOR)))));

        assertTrue(idsContratos(Specification.allOf(List.of(
                EspecificacionesCrm.clienteIgual(ana.getId()),
                EspecificacionesCrm.estadoIgual(EstadoContrato.ACTIVO)))).isEmpty());
    }

    private List<UUID> idsClientes(Specification<Cliente> especificacion) {
        return clientes.findAll(especificacion, PageRequest.of(0, 100))
                .getContent().stream().map(Cliente::getId).toList();
    }

    private List<UUID> idsCuentas(Specification<CuentaCorporativa> especificacion) {
        return cuentas.findAll(especificacion, PageRequest.of(0, 100))
                .getContent().stream().map(CuentaCorporativa::getId).toList();
    }

    private List<UUID> idsContratos(Specification<Contrato> especificacion) {
        return contratos.findAll(especificacion, PageRequest.of(0, 100))
                .getContent().stream().map(Contrato::getId).toList();
    }

    private long totalClientes(Specification<Cliente> especificacion) {
        return clientes.findAll(especificacion, PageRequest.of(0, 100)).getTotalElements();
    }

    private long totalCuentas(Specification<CuentaCorporativa> especificacion) {
        return cuentas.findAll(especificacion, PageRequest.of(0, 100)).getTotalElements();
    }

    private long totalContratos(Specification<Contrato> especificacion) {
        return contratos.findAll(especificacion, PageRequest.of(0, 100)).getTotalElements();
    }
}
