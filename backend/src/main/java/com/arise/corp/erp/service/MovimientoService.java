package com.arise.corp.erp.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arise.corp.crm.dto.ClienteReferencia;
import com.arise.corp.crm.service.ClienteReferenciaService;
import com.arise.corp.erp.dto.MovimientoRequest;
import com.arise.corp.erp.dto.MovimientoResponse;
import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.MovimientoFinanciero;
import com.arise.corp.erp.entity.Proveedor;
import com.arise.corp.erp.repository.EspecificacionesErp;
import com.arise.corp.erp.repository.MovimientoFinancieroRepository;
import com.arise.corp.erp.repository.ProveedorRepository;
import com.arise.corp.shared.error.RecursoNoEncontradoException;
import com.arise.corp.shared.paginacion.PaginaResponse;

/**
 * Servicio de movimientos financieros (ERP-03, ERP-05, D2, D12, D13).
 *
 * <p>Las lecturas son transacciones de solo lectura y las escrituras usan
 * {@code @Transactional}. Las referencias de clientes se resuelven a traves de
 * {@link ClienteReferenciaService}, sin acceder al repositorio del CRM. El
 * actor es el {@code sub} del perfil activo y las marcas temporales salen del
 * {@link Clock} de la aplicacion, truncadas a microsegundos para coincidir con
 * {@code timestamptz}. No se registran datos de clientes ni de proveedores en
 * logs.
 */
@Service
@Transactional(readOnly = true)
public class MovimientoService {

    private static final String MOVIMIENTO_NO_ENCONTRADO =
            "El movimiento financiero no existe.";
    private static final String CLIENTE_NO_ENCONTRADO =
            "El cliente indicado no existe.";
    private static final String PROVEEDOR_NO_ENCONTRADO =
            "El proveedor indicado no existe.";
    private static final String CAMPO_CLIENTE = "clienteId";
    private static final String CAMPO_PROVEEDOR = "proveedorId";

    private final MovimientoFinancieroRepository movimientos;
    private final ProveedorRepository proveedores;
    private final ClienteReferenciaService clientesCrm;
    private final Clock clock;

    public MovimientoService(
            MovimientoFinancieroRepository movimientos,
            ProveedorRepository proveedores,
            ClienteReferenciaService clientesCrm,
            Clock clock) {

        this.movimientos = movimientos;
        this.proveedores = proveedores;
        this.clientesCrm = clientesCrm;
        this.clock = clock;
    }

    /**
     * Lista los movimientos que cumplen todos los filtros. Los nombres de los
     * clientes de la pagina se resuelven con una sola consulta en lote.
     */
    public PaginaResponse<MovimientoResponse> listar(
            FiltroMovimientos filtro,
            Pageable pageable) {

        Specification<MovimientoFinanciero> especificacion = Specification.allOf(
                EspecificacionesErp.tipoIgual(filtro.tipo()),
                EspecificacionesErp.estadoIgual(filtro.estado()),
                EspecificacionesErp.clienteIgual(filtro.clienteId()),
                EspecificacionesErp.proveedorIgual(filtro.proveedorId()),
                EspecificacionesErp.fechaDesde(filtro.rango().desde()),
                EspecificacionesErp.fechaHasta(filtro.rango().hasta()),
                EspecificacionesErp.conceptoOReferenciaContiene(filtro.q()));

        Page<MovimientoFinanciero> pagina = movimientos.findAll(
                especificacion, pageable);

        Set<UUID> clienteIds = pagina.getContent().stream()
                .map(MovimientoFinanciero::getClienteId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        var referencias = clientesCrm.buscarTodos(clienteIds);

        Page<MovimientoResponse> respuesta = pagina.map(movimiento -> {
            ClienteReferencia cliente = movimiento.getClienteId() == null
                    ? null
                    : referencias.get(movimiento.getClienteId());
            return MovimientoResponse.desde(movimiento, cliente);
        });

        return PaginaResponse.desde(respuesta);
    }

    /**
     * Obtiene un movimiento por su identificador.
     *
     * @throws RecursoNoEncontradoException si el movimiento no existe
     */
    public MovimientoResponse obtener(UUID id) {
        return convertir(movimientos.findConProveedorById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        MOVIMIENTO_NO_ENCONTRADO)));
    }

    /**
     * Crea un movimiento en {@code PENDIENTE} con la auditoria del actor. El
     * cliente y el proveedor deben existir y estar activos.
     *
     * @throws RecursoNoEncontradoException si la referencia no existe
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si la
     *         referencia esta inactiva
     */
    @Transactional
    public MovimientoResponse crear(MovimientoRequest solicitud, String actorSub) {
        ClienteReferencia cliente = null;
        if (solicitud.clienteId() != null) {
            cliente = clientesCrm.buscarParaAsociar(solicitud.clienteId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            CLIENTE_NO_ENCONTRADO, CAMPO_CLIENTE));

            if (!cliente.activo()) {
                throw ConflictosErp.clienteInactivo();
            }
        }

        Proveedor proveedor = null;
        if (solicitud.proveedorId() != null) {
            proveedor = proveedores.findParaAsociarById(solicitud.proveedorId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            PROVEEDOR_NO_ENCONTRADO, CAMPO_PROVEEDOR));

            if (!proveedor.isActivo()) {
                throw ConflictosErp.proveedorInactivo();
            }
        }

        MovimientoFinanciero movimiento = MovimientoFinanciero.crear(
                solicitud.tipo(),
                solicitud.fecha(),
                solicitud.concepto(),
                solicitud.valor(),
                solicitud.referencia(),
                cliente == null ? null : cliente.id(),
                proveedor,
                UUID.fromString(actorSub),
                ahora());

        return MovimientoResponse.desde(movimientos.save(movimiento), cliente);
    }

    /**
     * Reemplaza los datos de un movimiento pendiente. La asociacion actual se
     * conserva aunque este inactiva; una asociacion nueva debe existir y estar
     * activa. El orden de los errores es 404 del movimiento, 404 de la
     * referencia, 409 de no editable y 409 de inactivo (D9 de 003).
     *
     * @throws RecursoNoEncontradoException si el movimiento o la referencia no
     *         existen
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si el
     *         movimiento no es editable o la referencia nueva esta inactiva
     */
    @Transactional
    public MovimientoResponse actualizar(
            UUID id,
            MovimientoRequest solicitud,
            String actorSub) {

        MovimientoFinanciero movimiento = buscarParaModificar(id);

        ClienteReferencia cliente = resolverCliente(
                solicitud.clienteId(), movimiento.getClienteId());
        Proveedor proveedor = resolverProveedor(
                solicitud.proveedorId(), movimiento.getProveedor());

        if (!movimiento.getEstado().esEditable()) {
            throw ConflictosErp.movimientoNoEditable(movimiento.getEstado());
        }

        if (cliente != null
                && !cliente.activo()
                && !cliente.id().equals(movimiento.getClienteId())) {
            throw ConflictosErp.clienteInactivo();
        }

        if (proveedor != null
                && !proveedor.isActivo()
                && !esProveedorActual(proveedor, movimiento.getProveedor())) {
            throw ConflictosErp.proveedorInactivo();
        }

        movimiento.actualizarDatos(
                solicitud.tipo(),
                solicitud.fecha(),
                solicitud.concepto(),
                solicitud.valor(),
                solicitud.referencia(),
                cliente == null ? null : cliente.id(),
                proveedor,
                UUID.fromString(actorSub),
                ahora());

        return MovimientoResponse.desde(movimiento, cliente);
    }

    /**
     * Aplica una transicion permitida y actualiza solo la auditoria de estado.
     * No consulta el estado activo del cliente ni del proveedor.
     *
     * @throws RecursoNoEncontradoException si el movimiento no existe
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si la
     *         transicion no esta permitida
     */
    @Transactional
    public MovimientoResponse cambiarEstado(
            UUID id,
            EstadoMovimiento destino,
            String actorSub) {

        MovimientoFinanciero movimiento = buscarParaModificar(id);

        if (!movimiento.getEstado().puedeTransicionarA(destino)) {
            throw ConflictosErp.transicionNoPermitida(
                    movimiento.getEstado(), destino);
        }

        movimiento.cambiarEstado(destino, UUID.fromString(actorSub), ahora());

        return convertir(movimiento);
    }

    private ClienteReferencia resolverCliente(UUID destinoId, UUID actualId) {
        if (destinoId == null) {
            return null;
        }

        if (destinoId.equals(actualId)) {
            return clientesCrm.buscar(destinoId).orElseThrow();
        }

        return clientesCrm.buscarParaAsociar(destinoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        CLIENTE_NO_ENCONTRADO, CAMPO_CLIENTE));
    }

    private Proveedor resolverProveedor(UUID destinoId, Proveedor actual) {
        if (destinoId == null) {
            return null;
        }

        if (actual != null && destinoId.equals(actual.getId())) {
            return actual;
        }

        return proveedores.findParaAsociarById(destinoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        PROVEEDOR_NO_ENCONTRADO, CAMPO_PROVEEDOR));
    }

    private static boolean esProveedorActual(Proveedor destino, Proveedor actual) {
        return actual != null && destino.getId().equals(actual.getId());
    }

    private MovimientoFinanciero buscarParaModificar(UUID id) {
        return movimientos.findParaModificarById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        MOVIMIENTO_NO_ENCONTRADO));
    }

    private MovimientoResponse convertir(MovimientoFinanciero movimiento) {
        ClienteReferencia cliente = movimiento.getClienteId() == null
                ? null
                : clientesCrm.buscar(movimiento.getClienteId())
                        .orElseThrow(() -> new IllegalStateException(
                                "El cliente del movimiento no existe."));

        return MovimientoResponse.desde(movimiento, cliente);
    }

    private Instant ahora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
