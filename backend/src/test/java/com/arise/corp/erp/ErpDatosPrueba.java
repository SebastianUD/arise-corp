package com.arise.corp.erp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.arise.corp.crm.CrmDatosPrueba;
import com.arise.corp.erp.entity.Empleado;
import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.MovimientoFinanciero;
import com.arise.corp.erp.entity.Proveedor;
import com.arise.corp.erp.entity.TipoDocumentoEmpleado;
import com.arise.corp.erp.entity.TipoDocumentoProveedor;
import com.arise.corp.erp.entity.TipoMovimiento;
import com.arise.corp.erp.repository.EmpleadoRepository;
import com.arise.corp.erp.repository.MovimientoFinancieroRepository;
import com.arise.corp.erp.repository.ProveedorRepository;

/**
 * Datos ficticios para las pruebas del ERP (Requirements 25.5, 25.6).
 *
 * <p>Los tests lo importan con
 * {@code @Import({CrmDatosPrueba.class, ErpDatosPrueba.class})} y llaman a
 * {@link #limpiar()} en {@code @BeforeEach}. Los perfiles y los clientes se
 * crean con {@link CrmDatosPrueba}; la autenticacion usa
 * {@code AutenticacionPerfilTestSupport.como(...)} con el
 * {@code PerfilJwtAuthenticationConverter} real. Ningun dato es real.
 */
public class ErpDatosPrueba {

    private static final UUID ACTOR_ID = CrmDatosPrueba.ACTOR_ID;
    private static final Instant INSTANTE = CrmDatosPrueba.INSTANTE;
    private static final LocalDate FECHA_INGRESO = LocalDate.of(2025, 3, 1);
    private static final LocalDate FECHA_MOVIMIENTO = LocalDate.of(2026, 1, 15);
    private static final BigDecimal VALOR = new BigDecimal("2500000.50");

    private final CrmDatosPrueba crm;
    private final EmpleadoRepository empleados;
    private final ProveedorRepository proveedores;
    private final MovimientoFinancieroRepository movimientos;
    private final AtomicInteger secuencia = new AtomicInteger();

    public ErpDatosPrueba(
            CrmDatosPrueba crm,
            EmpleadoRepository empleados,
            ProveedorRepository proveedores,
            MovimientoFinancieroRepository movimientos) {

        this.crm = crm;
        this.empleados = empleados;
        this.proveedores = proveedores;
        this.movimientos = movimientos;
    }

    /**
     * Vacia las tablas del ERP en orden de dependencias y despues las del CRM.
     */
    public void limpiar() {
        movimientos.deleteAll();
        proveedores.deleteAll();
        empleados.deleteAll();
        crm.limpiar();
        secuencia.set(0);
    }

    public Empleado crearEmpleado() {
        return crearEmpleado(true);
    }

    public Empleado crearEmpleado(boolean activo) {
        String documento = siguienteDocumento();
        return crearEmpleado(documento, "Empleado ficticio " + documento, activo);
    }

    public Empleado crearEmpleado(
            String documento,
            String nombre,
            boolean activo) {

        Empleado empleado = Empleado.crear(
                TipoDocumentoEmpleado.CC,
                documento,
                nombre,
                "Cargo ficticio",
                "Area ficticia",
                "empleado" + documento + "@example.test",
                "+57 300 000 0000",
                FECHA_INGRESO,
                ACTOR_ID,
                INSTANTE);

        if (!activo) {
            empleado.cambiarEstado(false, ACTOR_ID, INSTANTE);
        }

        return empleados.save(empleado);
    }

    public Proveedor crearProveedor() {
        return crearProveedor(true);
    }

    public Proveedor crearProveedor(boolean activo) {
        String documento = siguienteDocumento();
        return crearProveedor(documento, "Proveedor ficticio " + documento, activo);
    }

    public Proveedor crearProveedor(
            String documento,
            String nombre,
            boolean activo) {

        Proveedor proveedor = Proveedor.crear(
                TipoDocumentoProveedor.NIT,
                documento,
                nombre,
                "Contacto ficticio",
                "proveedor" + documento + "@example.test",
                "601 000 0000",
                "Calle ficticia 123",
                "Bogota",
                ACTOR_ID,
                INSTANTE);

        if (!activo) {
            proveedor.cambiarEstado(false, ACTOR_ID, INSTANTE);
        }

        return proveedores.save(proveedor);
    }

    public MovimientoFinanciero crearMovimiento(
            TipoMovimiento tipo,
            EstadoMovimiento estado) {

        return crearMovimiento(
                tipo,
                estado,
                VALOR,
                FECHA_MOVIMIENTO,
                tipo == TipoMovimiento.INGRESO ? clienteFicticio() : null,
                tipo == TipoMovimiento.EGRESO ? crearProveedor() : null);
    }

    public MovimientoFinanciero crearMovimiento(
            TipoMovimiento tipo,
            EstadoMovimiento estado,
            BigDecimal valor,
            LocalDate fecha,
            UUID clienteId,
            Proveedor proveedor) {

        MovimientoFinanciero movimiento = MovimientoFinanciero.crear(
                tipo,
                fecha,
                "Movimiento ficticio " + secuencia.incrementAndGet(),
                valor,
                "FAC-FICT-" + String.format("%04d", secuencia.get()),
                clienteId,
                proveedor,
                ACTOR_ID,
                INSTANTE);

        if (estado != EstadoMovimiento.PENDIENTE) {
            movimiento.cambiarEstado(estado, ACTOR_ID, INSTANTE);
        }

        return movimientos.save(movimiento);
    }

    /**
     * Cliente activo ficticio del CRM para asociar ingresos.
     */
    public UUID clienteFicticio() {
        return crm.crearCliente(crm.crearCuenta()).getId();
    }

    public String siguienteDocumento() {
        return String.format("20%08d", secuencia.incrementAndGet());
    }

    public static BigDecimal valor() {
        return VALOR;
    }

    public static LocalDate fechaMovimiento() {
        return FECHA_MOVIMIENTO;
    }
}
