package com.arise.corp.sga;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.arise.corp.crm.CrmDatosPrueba;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.Contrato;
import com.arise.corp.sga.entity.ArticuloInventario;
import com.arise.corp.sga.entity.CategoriaArticulo;
import com.arise.corp.sga.entity.Dron;
import com.arise.corp.sga.entity.EstadoDron;
import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.entity.HistorialEstadoPaquete;
import com.arise.corp.sga.entity.MovimientoInventario;
import com.arise.corp.sga.entity.Paquete;
import com.arise.corp.sga.entity.TipoMovimientoInventario;
import com.arise.corp.sga.entity.UnidadArticulo;
import com.arise.corp.sga.repository.ArticuloInventarioRepository;
import com.arise.corp.sga.repository.DronRepository;
import com.arise.corp.sga.repository.HistorialEstadoPaqueteRepository;
import com.arise.corp.sga.repository.MovimientoInventarioRepository;
import com.arise.corp.sga.repository.PaqueteRepository;

/**
 * Datos ficticios para las pruebas del SGA (Requirements 25.4, 25.5, NF-07).
 *
 * <p>Los tests lo importan con
 * {@code @Import({CrmDatosPrueba.class, SgaDatosPrueba.class})} y llaman a
 * {@link #limpiar()} en {@code @BeforeEach}. Los perfiles, clientes y
 * contratos se crean con {@link CrmDatosPrueba}; la autenticacion usa
 * {@code AutenticacionPerfilTestSupport.como(...)} con el
 * {@code PerfilJwtAuthenticationConverter} real. Ningun dato es real.
 */
public class SgaDatosPrueba {

    private static final UUID ACTOR_ID = CrmDatosPrueba.ACTOR_ID;
    private static final Instant INSTANTE = CrmDatosPrueba.INSTANTE;
    private static final String DIRECCION = "Calle ficticia 123, Bogota";
    private static final int STOCK_MINIMO = 10;
    private static final int CANTIDAD_MAXIMA = 1_000_000;

    private final CrmDatosPrueba crm;
    private final DronRepository drones;
    private final PaqueteRepository paquetes;
    private final HistorialEstadoPaqueteRepository historial;
    private final ArticuloInventarioRepository articulos;
    private final MovimientoInventarioRepository movimientos;
    private final AtomicInteger secuencia = new AtomicInteger();

    public SgaDatosPrueba(
            CrmDatosPrueba crm,
            DronRepository drones,
            PaqueteRepository paquetes,
            HistorialEstadoPaqueteRepository historial,
            ArticuloInventarioRepository articulos,
            MovimientoInventarioRepository movimientos) {

        this.crm = crm;
        this.drones = drones;
        this.paquetes = paquetes;
        this.historial = historial;
        this.articulos = articulos;
        this.movimientos = movimientos;
    }

    /**
     * Vacia las tablas del SGA en orden de dependencias y despues las del CRM.
     */
    public void limpiar() {
        movimientos.deleteAll();
        historial.deleteAll();
        articulos.deleteAll();
        paquetes.deleteAll();
        drones.deleteAll();
        crm.limpiar();
        secuencia.set(0);
    }

    public Dron crearDron() {
        return crearDron(EstadoDron.DISPONIBLE);
    }

    public Dron crearDron(String codigo) {
        return crearDron(codigo, "Dron ficticio " + codigo, EstadoDron.DISPONIBLE);
    }

    /**
     * Crea un dron en el estado indicado aplicando el mismo mecanismo que los
     * servicios: cambio manual, o sincronizacion para {@code EN_MISION}.
     */
    public Dron crearDron(EstadoDron estado) {
        return crearDron(siguienteCodigo("DRN"), "Dron ficticio", estado);
    }

    public Dron crearDron(String codigo, String modelo, EstadoDron estado) {
        Dron dron = Dron.crear(codigo, modelo, "Observacion ficticia", ACTOR_ID, INSTANTE);

        switch (estado) {
            case DISPONIBLE -> {
                // Estado inicial de la fabrica.
            }
            case EN_MISION -> dron.sincronizarConPaquete(
                    EstadoDron.EN_MISION, ACTOR_ID, INSTANTE);
            case MANTENIMIENTO, FUERA_DE_SERVICIO ->
                    dron.cambiarEstadoManual(estado, ACTOR_ID, INSTANTE);
        }

        return drones.save(dron);
    }

    public Paquete crearPaquete() {
        return crearPaquete(EstadoPaquete.REGISTRADO);
    }

    /**
     * Crea un paquete con un cliente activo ficticio del CRM sin contrato.
     */
    public Paquete crearPaquete(EstadoPaquete estado) {
        Cliente cliente = crm.crearCliente(crm.crearCuenta());
        return crearPaquete(cliente.getId(), null, estado);
    }

    public Paquete crearPaquete(Cliente cliente, Contrato contrato, EstadoPaquete estado) {
        return crearPaquete(
                cliente.getId(),
                contrato == null ? null : contrato.getId(),
                estado);
    }

    /**
     * Crea un paquete en el estado indicado aplicando las transiciones de la
     * entidad y guardando cada registro del historial con su secuencia.
     */
    public Paquete crearPaquete(UUID clienteId, UUID contratoId, EstadoPaquete estado) {
        Paquete paquete = paquetes.save(Paquete.crear(
                siguienteCodigo("PAQ"),
                "Paquete ficticio",
                DIRECCION,
                clienteId,
                contratoId,
                ACTOR_ID,
                INSTANTE));

        historial.save(HistorialEstadoPaquete.alta(
                paquete, "Recibido en bodega", ACTOR_ID, INSTANTE));

        if (estado == EstadoPaquete.REGISTRADO) {
            return paquete;
        }

        if (estado == EstadoPaquete.CANCELADO) {
            paquete.aplicarTransicion(
                    EstadoPaquete.CANCELADO, null, ACTOR_ID, INSTANTE);
            historial.save(HistorialEstadoPaquete.transicion(
                    paquete, 2, EstadoPaquete.REGISTRADO, EstadoPaquete.CANCELADO,
                    null, "Cancelacion ficticia", ACTOR_ID, INSTANTE));
            return paquetes.save(paquete);
        }

        Dron dron = crearDron();
        paquete.aplicarTransicion(EstadoPaquete.ASIGNADO, dron, ACTOR_ID, INSTANTE);
        historial.save(HistorialEstadoPaquete.transicion(
                paquete, 2, EstadoPaquete.REGISTRADO, EstadoPaquete.ASIGNADO,
                dron, null, ACTOR_ID, INSTANTE));

        if (estado == EstadoPaquete.ASIGNADO) {
            return paquetes.save(paquete);
        }

        paquete.aplicarTransicion(EstadoPaquete.EN_TRANSITO, dron, ACTOR_ID, INSTANTE);
        historial.save(HistorialEstadoPaquete.transicion(
                paquete, 3, EstadoPaquete.ASIGNADO, EstadoPaquete.EN_TRANSITO,
                null, null, ACTOR_ID, INSTANTE));

        if (estado == EstadoPaquete.EN_TRANSITO) {
            return paquetes.save(paquete);
        }

        paquete.aplicarTransicion(estado, dron, ACTOR_ID, INSTANTE);
        historial.save(HistorialEstadoPaquete.transicion(
                paquete, 4, EstadoPaquete.EN_TRANSITO, estado,
                null, null, ACTOR_ID, INSTANTE));

        return paquetes.save(paquete);
    }

    public ArticuloInventario crearArticulo() {
        return crearArticulo(STOCK_MINIMO, 0L);
    }

    /**
     * Crea un articulo con la existencia indicada aplicando una
     * {@code ENTRADA} inicial.
     */
    public ArticuloInventario crearArticulo(int stockMinimo, long existencia) {
        ArticuloInventario articulo = articulos.save(ArticuloInventario.crear(
                siguienteCodigo("ART"),
                "Articulo ficticio",
                CategoriaArticulo.REPUESTO,
                UnidadArticulo.UNIDAD,
                stockMinimo,
                ACTOR_ID,
                INSTANTE));

        if (existencia > 0) {
            crearMovimiento(
                    articulo,
                    TipoMovimientoInventario.ENTRADA,
                    (int) Math.min(existencia, CANTIDAD_MAXIMA),
                    null);
        }

        return articulo;
    }

    /**
     * Aplica un movimiento valido sobre el articulo y lo guarda.
     */
    public MovimientoInventario crearMovimiento(
            ArticuloInventario articulo,
            TipoMovimientoInventario tipo,
            int cantidad,
            String observacion) {

        long anterior = articulo.getExistencia();
        long resultante = articulo.aplicarMovimiento(tipo, cantidad);
        articulos.save(articulo);

        return movimientos.save(MovimientoInventario.registrar(
                articulo,
                tipo,
                cantidad,
                anterior,
                resultante,
                observacion,
                ACTOR_ID,
                INSTANTE));
    }

    public String siguienteCodigo(String prefijo) {
        return String.format("%s-FICT-%04d", prefijo, secuencia.incrementAndGet());
    }
}
