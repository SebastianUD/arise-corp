package com.arise.corp.erp.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

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
 * Prueba de humo de los repositorios del ERP sobre H2 (tarea 5.7*).
 *
 * <p>Comprueba el mapeo de las tres entidades, las busquedas derivadas, los
 * {@code @EntityGraph}, los bloqueos y la composicion del fragmento
 * {@link TotalesMovimientos}. No sustituye las pruebas de integracion con
 * PostgreSQL.
 */
@DataJpaTest
class ErpRepositoriosTests {

    private static final UUID ACTOR =
            UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final Instant AHORA = Instant.parse("2026-11-11T12:00:00Z");
    private static final LocalDate INGRESO = LocalDate.of(2026, 1, 10);
    private static final LocalDate PAGO = LocalDate.of(2026, 2, 10);
    private static final LocalDate ANULACION = LocalDate.of(2026, 3, 10);
    private static final UUID CLIENTE_ID =
            UUID.fromString("00000000-0000-4000-8000-0000000000c1");

    @Autowired
    private EmpleadoRepository empleados;

    @Autowired
    private ProveedorRepository proveedores;

    @Autowired
    private MovimientoFinancieroRepository movimientos;

    @PersistenceContext
    private EntityManager em;

    private Empleado empleado;
    private Proveedor proveedor;
    private MovimientoFinanciero ingresoPendiente;
    private MovimientoFinanciero ingresoPagado;
    private MovimientoFinanciero egresoAnulado;

    @BeforeEach
    void prepararDatos() {
        empleado = empleados.saveAndFlush(Empleado.crear(
                TipoDocumentoEmpleado.CC,
                "10234567",
                "Empleada Uno",
                "Analista",
                "Finanzas",
                "empleada@example.test",
                "+57 300 000 0000",
                LocalDate.of(2025, 3, 1),
                ACTOR,
                AHORA));

        proveedor = proveedores.saveAndFlush(Proveedor.crear(
                TipoDocumentoProveedor.NIT,
                "900765432",
                "Proveedor Uno S.A.S.",
                "Contacto Uno",
                "proveedor@example.test",
                "601 000 0000",
                "Carrera 10 # 20-30",
                "Medellin",
                ACTOR,
                AHORA));

        ingresoPendiente = movimientos.saveAndFlush(MovimientoFinanciero.crear(
                TipoMovimiento.INGRESO,
                INGRESO,
                "Ingreso pendiente",
                new BigDecimal("100.00"),
                "FAC-001",
                CLIENTE_ID,
                null,
                ACTOR,
                AHORA));

        ingresoPagado = movimientos.saveAndFlush(MovimientoFinanciero.crear(
                TipoMovimiento.INGRESO,
                PAGO,
                "Ingreso pagado",
                new BigDecimal("50.50"),
                null,
                CLIENTE_ID,
                null,
                ACTOR,
                AHORA));
        ingresoPagado.cambiarEstado(
                EstadoMovimiento.PAGADO, ACTOR, AHORA.plusSeconds(60));
        movimientos.saveAndFlush(ingresoPagado);

        egresoAnulado = movimientos.saveAndFlush(MovimientoFinanciero.crear(
                TipoMovimiento.EGRESO,
                ANULACION,
                "Egreso anulado",
                new BigDecimal("30.00"),
                "FAC-003",
                null,
                proveedor,
                ACTOR,
                AHORA));
        egresoAnulado.cambiarEstado(
                EstadoMovimiento.ANULADO, ACTOR, AHORA.plusSeconds(120));
        movimientos.saveAndFlush(egresoAnulado);

        em.clear();
    }

    @Test
    void guardaYLeeLasTresEntidades() {
        Empleado empleadoLeido = empleados.findById(empleado.getId()).orElseThrow();
        assertEquals(TipoDocumentoEmpleado.CC, empleadoLeido.getTipoDocumento());
        assertEquals("10234567", empleadoLeido.getNumeroDocumento());
        assertEquals("Finanzas", empleadoLeido.getArea());
        assertEquals(LocalDate.of(2025, 3, 1), empleadoLeido.getFechaIngreso());
        assertTrue(empleadoLeido.isActivo());
        assertEquals(ACTOR, empleadoLeido.getActualizadoPor());
        assertEquals(AHORA, empleadoLeido.getCreadoEn());

        Proveedor proveedorLeido = proveedores.findById(proveedor.getId()).orElseThrow();
        assertEquals(TipoDocumentoProveedor.NIT, proveedorLeido.getTipoDocumento());
        assertEquals("900765432", proveedorLeido.getNumeroDocumento());
        assertEquals("Medellin", proveedorLeido.getCiudad());
        assertTrue(proveedorLeido.isActivo());
        assertEquals(AHORA, proveedorLeido.getCreadoEn());

        MovimientoFinanciero movimientoLeido = movimientos
                .findById(egresoAnulado.getId())
                .orElseThrow();
        assertEquals(TipoMovimiento.EGRESO, movimientoLeido.getTipo());
        assertEquals(new BigDecimal("30.00"), movimientoLeido.getValor());
        assertEquals("30.00", movimientoLeido.getValor().toPlainString());
        assertEquals(EstadoMovimiento.ANULADO, movimientoLeido.getEstado());
        assertEquals(
                AHORA.plusSeconds(120),
                movimientoLeido.getEstadoActualizadoEn());
        assertEquals(AHORA, movimientoLeido.getActualizadoEn());
    }

    @Test
    void resuelveLasBusquedasDerivadas() {
        assertEquals(
                empleado.getId(),
                empleados.findByTipoDocumentoAndNumeroDocumento(
                                TipoDocumentoEmpleado.CC, "10234567")
                        .orElseThrow()
                        .getId());
        assertEquals(
                proveedor.getId(),
                proveedores.findByTipoDocumentoAndNumeroDocumento(
                                TipoDocumentoProveedor.NIT, "900765432")
                        .orElseThrow()
                        .getId());

        assertTrue(empleados.findByTipoDocumentoAndNumeroDocumento(
                TipoDocumentoEmpleado.CE, "99999999").isEmpty());
        assertTrue(proveedores.findByTipoDocumentoAndNumeroDocumento(
                TipoDocumentoProveedor.CC, "99999999").isEmpty());
    }

    @Test
    void cargaElProveedorConEntityGraphEnDetalleYEnLista() {
        MovimientoFinanciero detalle = movimientos
                .findConProveedorById(egresoAnulado.getId())
                .orElseThrow();
        assertTrue(Hibernate.isInitialized(detalle.getProveedor()));
        assertEquals("Proveedor Uno S.A.S.", detalle.getProveedor().getNombre());

        Page<MovimientoFinanciero> pagina = movimientos.findAll(
                EspecificacionesErp.proveedorIgual(proveedor.getId()),
                PageRequest.of(0, 10));

        assertEquals(1, pagina.getTotalElements());
        assertTrue(Hibernate.isInitialized(
                pagina.getContent().getFirst().getProveedor()));
    }

    @Test
    void resuelveLasConsultasConBloqueo() {
        assertEquals(
                proveedor.getId(),
                proveedores.findParaAsociarById(proveedor.getId())
                        .orElseThrow()
                        .getId());
        assertEquals(
                ingresoPendiente.getId(),
                movimientos.findParaModificarById(ingresoPendiente.getId())
                        .orElseThrow()
                        .getId());
    }

    @Test
    void totalizaConYSinLimites() {
        List<TotalMovimientos> todas = movimientos.totalizar(null, null);
        assertEquals(3, todas.size());

        BigDecimal sumaTotal = todas.stream()
                .map(TotalMovimientos::suma)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("180.50").compareTo(sumaTotal));

        List<TotalMovimientos> febrero = movimientos.totalizar(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        assertEquals(1, febrero.size());

        TotalMovimientos fila = febrero.getFirst();
        assertEquals(TipoMovimiento.INGRESO, fila.tipo());
        assertEquals(EstadoMovimiento.PAGADO, fila.estado());
        assertEquals(1L, fila.cantidad());
        assertEquals(0, new BigDecimal("50.50").compareTo(fila.suma()));

        List<TotalMovimientos> desdeMarzo = movimientos.totalizar(
                LocalDate.of(2026, 3, 1), null);
        assertEquals(1, desdeMarzo.size());
        assertEquals(EstadoMovimiento.ANULADO, desdeMarzo.getFirst().estado());
    }
}
