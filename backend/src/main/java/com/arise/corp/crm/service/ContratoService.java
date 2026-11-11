package com.arise.corp.crm.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arise.corp.crm.dto.ContratoActualizarRequest;
import com.arise.corp.crm.dto.ContratoCrearRequest;
import com.arise.corp.crm.dto.ContratoResponse;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.Contrato;
import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.crm.repository.ClienteRepository;
import com.arise.corp.crm.repository.ContratoRepository;
import com.arise.corp.crm.repository.EspecificacionesCrm;
import com.arise.corp.shared.error.RecursoNoEncontradoException;
import com.arise.corp.shared.paginacion.PaginaResponse;

/**
 * Servicio de contratos (CRM-03, Requirements 11 a 14).
 *
 * <p>Las lecturas son transacciones de solo lectura y las escrituras usan
 * {@code @Transactional}. Las transiciones de estado se validan contra
 * {@link EstadoContrato} y se aplican con bloqueo pesimista de escritura (D9).
 * La entidad se convierte en DTO dentro de la transaccion porque
 * {@code open-in-view} esta desactivado. El actor se recibe como el {@code sub}
 * del perfil activo y las marcas temporales salen del {@link Clock} de la
 * aplicacion, truncadas a microsegundos para coincidir con {@code timestamptz}
 * (D10, DP-SEG-06). No se registran datos de clientes en logs.
 */
@Service
@Transactional(readOnly = true)
public class ContratoService {

    private static final String CONTRATO_NO_ENCONTRADO = "El contrato no existe.";
    private static final String CLIENTE_NO_ENCONTRADO = "El cliente no existe.";
    private static final String CAMPO_CLIENTE = "clienteId";

    private final ContratoRepository contratos;
    private final ClienteRepository clientes;
    private final Clock clock;

    public ContratoService(
            ContratoRepository contratos,
            ClienteRepository clientes,
            Clock clock) {

        this.contratos = contratos;
        this.clientes = clientes;
        this.clock = clock;
    }

    /**
     * Lista los contratos que cumplen los filtros. La consulta carga el cliente
     * y su cuenta corporativa con {@code @EntityGraph} para evitar N+1. Un
     * {@code clienteId} inexistente produce una pagina vacia.
     */
    public PaginaResponse<ContratoResponse> listar(FiltroContratos filtro, Pageable pageable) {
        Specification<Contrato> especificacion = Specification.allOf(
                EspecificacionesCrm.clienteIgual(filtro.clienteId()),
                EspecificacionesCrm.estadoIgual(filtro.estado()));

        Page<ContratoResponse> pagina = contratos.findAll(especificacion, pageable)
                .map(ContratoResponse::desde);

        return PaginaResponse.desde(pagina);
    }

    /**
     * Obtiene un contrato por su identificador.
     *
     * @throws RecursoNoEncontradoException si el contrato no existe
     */
    public ContratoResponse obtener(UUID id) {
        return ContratoResponse.desde(buscarParaLectura(id));
    }

    /**
     * Crea un contrato en {@link EstadoContrato#BORRADOR} con la auditoria del
     * actor.
     *
     * @throws RecursoNoEncontradoException si el cliente no existe
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si el
     *         cliente esta inactivo o el codigo ya existe
     */
    @Transactional
    public ContratoResponse crear(ContratoCrearRequest solicitud, String actorSub) {
        Cliente cliente = clientes.findParaContratoById(solicitud.clienteId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        CLIENTE_NO_ENCONTRADO, CAMPO_CLIENTE));

        if (!cliente.isActivo()) {
            throw ConflictosCrm.clienteInactivoSinContratos();
        }

        verificarCodigoDisponible(solicitud.codigo(), null);

        Contrato contrato = Contrato.crear(
                cliente,
                solicitud.codigo(),
                solicitud.descripcion(),
                solicitud.fechaInicio(),
                solicitud.fechaFin(),
                solicitud.valor(),
                UUID.fromString(actorSub),
                ahora());

        return ContratoResponse.desde(contratos.save(contrato));
    }

    /**
     * Reemplaza los datos de un contrato en borrador; conserva el cliente, el
     * estado y la auditoria del estado.
     *
     * @throws RecursoNoEncontradoException si el contrato no existe
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si el
     *         contrato no esta en borrador o el codigo pertenece a otro
     *         contrato
     */
    @Transactional
    public ContratoResponse actualizar(
            UUID id,
            ContratoActualizarRequest solicitud,
            String actorSub) {

        Contrato contrato = buscarParaModificar(id);

        if (contrato.getEstado() != EstadoContrato.BORRADOR) {
            throw ConflictosCrm.contratoNoEditable(contrato.getEstado());
        }

        verificarCodigoDisponible(solicitud.codigo(), contrato.getId());

        contrato.actualizarDatos(
                solicitud.codigo(),
                solicitud.descripcion(),
                solicitud.fechaInicio(),
                solicitud.fechaFin(),
                solicitud.valor(),
                UUID.fromString(actorSub),
                ahora());

        return ContratoResponse.desde(contrato);
    }

    /**
     * Cambia el estado del contrato con una transicion permitida y actualiza
     * solo la auditoria del estado. No consulta el estado del cliente
     * (Requirements 14.2 y 14.5).
     *
     * @throws RecursoNoEncontradoException si el contrato no existe
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si la
     *         transicion no esta permitida
     */
    @Transactional
    public ContratoResponse cambiarEstado(UUID id, EstadoContrato destino, String actorSub) {
        Contrato contrato = buscarParaModificar(id);

        if (!contrato.getEstado().puedeTransicionarA(destino)) {
            throw ConflictosCrm.transicionNoPermitida(contrato.getEstado(), destino);
        }

        contrato.cambiarEstado(destino, UUID.fromString(actorSub), ahora());

        return ContratoResponse.desde(contrato);
    }

    private void verificarCodigoDisponible(String codigo, UUID contratoActualId) {
        contratos.findByCodigo(codigo)
                .filter(otro -> !otro.getId().equals(contratoActualId))
                .ifPresent(otro -> {
                    throw ConflictosCrm.contratoCodigoDuplicado();
                });
    }

    private Contrato buscarParaLectura(UUID id) {
        return contratos.findConClienteById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(CONTRATO_NO_ENCONTRADO));
    }

    private Contrato buscarParaModificar(UUID id) {
        return contratos.findParaModificarById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(CONTRATO_NO_ENCONTRADO));
    }

    private Instant ahora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
