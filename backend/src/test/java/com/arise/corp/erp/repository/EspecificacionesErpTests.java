package com.arise.corp.erp.repository;

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

import com.arise.corp.erp.entity.Empleado;
import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.MovimientoFinanciero;
import com.arise.corp.erp.entity.Proveedor;
import com.arise.corp.erp.entity.TipoDocumentoEmpleado;
import com.arise.corp.erp.entity.TipoDocumentoProveedor;
import com.arise.corp.erp.entity.TipoMovimiento;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Prueba de las especificaciones del ERP sobre H2 (tarea 5.8*).
 *
 * <p>Cubre cada especificacion por separado y combinada, el escape de
 * {@code %}, {@code _} y {@code \}, la referencia nula que no coincide y los
 * filtros ausentes sin restriccion.
 */
@DataJpaTest
class EspecificacionesErpTests {

    private static final UUID ACTOR =
            UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final Instant AHORA = Instant.parse("2026-11-11T12:00:00Z");
    private static final UUID CLIENTE_A =
            UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID CLIENTE_B =
            UUID.fromString("00000000-0000-4000-8000-0000000000b1");

    @Autowired
    private EmpleadoRepository empleados;

    @Autowired
    private ProveedorRepository proveedores;

    @Autowired
    private MovimientoFinancieroRepository movimientos;

    @PersistenceContext
    private EntityManager em;

    private Empleado empleadoActivo;
    private Empleado empleadoInactivo;
    private Proveedor proveedorActivo;
    private MovimientoFinanciero ingresoPendiente;
    private MovimientoFinanciero ingresoPagado;
    private MovimientoFinanciero egresoAnulado;

    @BeforeEach
    void prepararDatos() {
        empleadoActivo = empleados.saveAndFlush(Empleado.crear(
                TipoDocumentoEmpleado.CC,
                "10234567",
                "Ana Perez",
                "Analista",
                "Finanzas",
                "ana@example.test",
                "+57 300 000 0000",
                LocalDate.of(2025, 3, 1),
                ACTOR,
                AHORA));

        empleadoInactivo = empleados.saveAndFlush(Empleado.crear(
                TipoDocumentoEmpleado.CE,
                "AB1234",
                "500% Cobertura",
                "Gestor",
                "Comercial",
                "cobertura@example.test",
                "+57 301 000 0000",
                LocalDate.of(2024, 1, 15),
                ACTOR,
                AHORA));
        empleadoInactivo.cambiarEstado(false, ACTOR, AHORA.plusSeconds(1));
        empleados.saveAndFlush(empleadoInactivo);

        proveedorActivo = proveedores.saveAndFlush(Proveedor.crear(
                TipoDocumentoProveedor.NIT,
                "900765432",
                "Suministros\\Andinos",
                null,
                "proveedor@example.test",
                "601 000 0000",
                null,
                "Bogota",
                ACTOR,
                AHORA));

        ingresoPendiente = movimientos.saveAndFlush(MovimientoFinanciero.crear(
                TipoMovimiento.INGRESO,
                LocalDate.of(2026, 1, 10),
                "Servicio mensual",
                new BigDecimal("100.00"),
                "FAC-001",
                CLIENTE_A,
                null,
                ACTOR,
                AHORA));

        ingresoPagado = movimientos.saveAndFlush(MovimientoFinanciero.crear(
                TipoMovimiento.INGRESO,
                LocalDate.of(2026, 2, 10),
                "Ajuste 100%",
                new BigDecimal("50.50"),
                null,
                CLIENTE_B,
                null,
                ACTOR,
                AHORA));
        ingresoPagado.cambiarEstado(EstadoMovimiento.PAGADO, ACTOR, AHORA);
        movimientos.saveAndFlush(ingresoPagado);

        egresoAnulado = movimientos.saveAndFlush(MovimientoFinanciero.crear(
                TipoMovimiento.EGRESO,
                LocalDate.of(2026, 3, 10),
                "Compra de insumos",
                new BigDecimal("30.00"),
                "FAC_003",
                null,
                proveedorActivo,
                ACTOR,
                AHORA));
        egresoAnulado.cambiarEstado(EstadoMovimiento.ANULADO, ACTOR, AHORA);
        movimientos.saveAndFlush(egresoAnulado);

        em.clear();
    }

    @Test
    void buscaEmpleadosPorNombreODocumentoNormalizado() {
        assertEquals(
                List.of(empleadoActivo.getId()),
                idsEmpleados(EspecificacionesErp.nombreODocumentoContiene("ana")));
        assertEquals(
                List.of(empleadoActivo.getId()),
                idsEmpleados(EspecificacionesErp.nombreODocumentoContiene("1.023.456-7")));
        assertEquals(
                List.of(empleadoInactivo.getId()),
                idsEmpleados(EspecificacionesErp.nombreODocumentoContiene("AB12")));
    }

    @Test
    void escapaLosComodinesEnEmpleados() {
        assertEquals(
                List.of(empleadoInactivo.getId()),
                idsEmpleados(EspecificacionesErp.nombreODocumentoContiene("%")));
        assertTrue(idsEmpleados(
                EspecificacionesErp.nombreODocumentoContiene("_")).isEmpty());
        assertTrue(idsEmpleados(
                EspecificacionesErp.nombreODocumentoContiene("\\")).isEmpty());

        List<Empleado> literales = empleados.findAll(
                EspecificacionesErp.nombreODocumentoContiene("500%"));
        assertEquals(1, literales.size());
        assertEquals(empleadoInactivo.getId(), literales.getFirst().getId());
    }

    @Test
    void filtraProveedoresPorEstadoYEscapaLaBarraInvertida() {
        assertEquals(
                List.of(proveedorActivo.getId()),
                idsProveedores(EspecificacionesErp.activoIgual(true)));
        assertTrue(idsProveedores(
                EspecificacionesErp.activoIgual(false)).isEmpty());

        List<Proveedor> barra = proveedores.findAll(
                EspecificacionesErp.nombreODocumentoContiene("\\"));
        assertEquals(1, barra.size());
        assertEquals(proveedorActivo.getId(), barra.getFirst().getId());
    }

    @Test
    void filtraMovimientosPorTipoEstadoClienteYProveedor() {
        assertEquals(
                java.util.Set.of(ingresoPendiente.getId(), ingresoPagado.getId()),
                java.util.Set.copyOf(idsMovimientos(
                        EspecificacionesErp.tipoIgual(TipoMovimiento.INGRESO))));
        assertEquals(
                List.of(ingresoPagado.getId()),
                idsMovimientos(EspecificacionesErp.estadoIgual(EstadoMovimiento.PAGADO)));
        assertEquals(
                List.of(ingresoPagado.getId()),
                idsMovimientos(EspecificacionesErp.clienteIgual(CLIENTE_B)));
        assertEquals(
                List.of(egresoAnulado.getId()),
                idsMovimientos(EspecificacionesErp.proveedorIgual(proveedorActivo.getId())));
    }

    @Test
    void filtraMovimientosPorFechaInclusiva() {
        assertEquals(
                java.util.Set.of(ingresoPagado.getId(), egresoAnulado.getId()),
                java.util.Set.copyOf(idsMovimientos(
                        EspecificacionesErp.fechaDesde(LocalDate.of(2026, 2, 1)))));
        assertEquals(
                java.util.Set.of(ingresoPendiente.getId(), ingresoPagado.getId()),
                java.util.Set.copyOf(idsMovimientos(
                        EspecificacionesErp.fechaHasta(LocalDate.of(2026, 2, 10)))));
    }

    @Test
    void buscaPorConceptoOReferenciaNormalizada() {
        assertEquals(
                List.of(egresoAnulado.getId()),
                idsMovimientos(EspecificacionesErp.conceptoOReferenciaContiene("insumos")));
        assertEquals(
                List.of(egresoAnulado.getId()),
                idsMovimientos(EspecificacionesErp.conceptoOReferenciaContiene("fac_003")));
        assertEquals(
                List.of(ingresoPendiente.getId()),
                idsMovimientos(EspecificacionesErp.conceptoOReferenciaContiene("FAC-001")));
    }

    @Test
    void losComodinesDelConceptoSeEscapanYLaReferenciaNulaNoCoincide() {
        List<MovimientoFinanciero> porcentaje = movimientos.findAll(
                EspecificacionesErp.conceptoOReferenciaContiene("100%"));
        assertEquals(1, porcentaje.size());
        assertEquals(ingresoPagado.getId(), porcentaje.getFirst().getId());

        assertTrue(idsMovimientos(
                EspecificacionesErp.conceptoOReferenciaContiene("FAC%")).isEmpty());
        assertEquals(
                List.of(egresoAnulado.getId()),
                idsMovimientos(
                        EspecificacionesErp.conceptoOReferenciaContiene("FAC_")));
    }

    @Test
    void combinaTodasLasEspecificacionesYLosFiltrosAusentesNoRestringen() {
        Specification<MovimientoFinanciero> combinada = Specification.allOf(
                EspecificacionesErp.tipoIgual(TipoMovimiento.INGRESO),
                EspecificacionesErp.estadoIgual(EstadoMovimiento.PENDIENTE),
                EspecificacionesErp.clienteIgual(CLIENTE_A),
                EspecificacionesErp.tipoIgual(null),
                EspecificacionesErp.estadoIgual(null),
                EspecificacionesErp.clienteIgual(null),
                EspecificacionesErp.proveedorIgual(null),
                EspecificacionesErp.fechaDesde(null),
                EspecificacionesErp.fechaHasta(null),
                EspecificacionesErp.conceptoOReferenciaContiene(null));

        assertEquals(
                List.of(ingresoPendiente.getId()),
                idsMovimientos(combinada));

        assertEquals(3, movimientos.findAll(
                Specification.allOf(
                        EspecificacionesErp.tipoIgual(null),
                        EspecificacionesErp.estadoIgual(null),
                        EspecificacionesErp.clienteIgual(null),
                        EspecificacionesErp.proveedorIgual(null),
                        EspecificacionesErp.fechaDesde(null),
                        EspecificacionesErp.fechaHasta(null),
                        EspecificacionesErp.conceptoOReferenciaContiene(null)),
                PageRequest.of(0, 10)).getTotalElements());
    }

    private List<UUID> idsEmpleados(Specification<Empleado> especificacion) {
        return empleados.findAll(especificacion).stream()
                .map(Empleado::getId)
                .toList();
    }

    private List<UUID> idsProveedores(Specification<Proveedor> especificacion) {
        return proveedores.findAll(especificacion).stream()
                .map(Proveedor::getId)
                .toList();
    }

    private List<UUID> idsMovimientos(
            Specification<MovimientoFinanciero> especificacion) {

        return movimientos.findAll(especificacion).stream()
                .map(MovimientoFinanciero::getId)
                .toList();
    }
}
