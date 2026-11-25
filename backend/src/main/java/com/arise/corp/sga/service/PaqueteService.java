package com.arise.corp.sga.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
import com.arise.corp.crm.dto.ContratoReferencia;
import com.arise.corp.crm.service.ClienteReferenciaService;
import com.arise.corp.crm.service.ContratoReferenciaService;
import com.arise.corp.sga.dto.PaqueteActualizarRequest;
import com.arise.corp.sga.dto.PaqueteCrearRequest;
import com.arise.corp.sga.dto.PaqueteResponse;
import com.arise.corp.sga.dto.RegistroHistorialResponse;
import com.arise.corp.sga.dto.TransicionPaqueteRequest;
import com.arise.corp.sga.entity.Dron;
import com.arise.corp.sga.entity.EstadoDron;
import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.entity.HistorialEstadoPaquete;
import com.arise.corp.sga.entity.Paquete;
import com.arise.corp.sga.repository.DronRepository;
import com.arise.corp.sga.repository.EspecificacionesSga;
import com.arise.corp.sga.repository.HistorialEstadoPaqueteRepository;
import com.arise.corp.sga.repository.PaqueteRepository;
import com.arise.corp.shared.error.RecursoNoEncontradoException;
import com.arise.corp.shared.paginacion.PaginaResponse;

/**
 * Servicio de paquetes con historial y sincronizacion del dron (SGA-02,
 * SGA-04, SGA-05).
 *
 * <p>Las lecturas son transacciones de solo lectura y las escrituras usan
 * {@code @Transactional}. Clientes y contratos se leen del CRM mediante
 * {@link ClienteReferenciaService} y {@link ContratoReferenciaService}: el SGA
 * no ve {@code crm.entity} ni {@code crm.repository} (DP-ARQ-01). El actor es
 * el {@code sub} del perfil activo y las marcas temporales salen del
 * {@link Clock}, truncadas a microsegundos (D14). No se registran direcciones,
 * observaciones ni nombres de clientes en logs (Requirement 17.3).
 */
@Service
@Transactional(readOnly = true)
public class PaqueteService {

    private static final String PAQUETE_NO_ENCONTRADO = "El paquete no existe.";
    private static final String CLIENTE_NO_ENCONTRADO = "El cliente indicado no existe.";
    private static final String CONTRATO_NO_ENCONTRADO = "El contrato indicado no existe.";
    private static final String DRON_NO_ENCONTRADO = "El dron indicado no existe.";
    private static final String CAMPO_CLIENTE = "clienteId";
    private static final String CAMPO_CONTRATO = "contratoId";
    private static final String CAMPO_DRON = "dronId";

    private final PaqueteRepository paquetes;
    private final DronRepository drones;
    private final HistorialEstadoPaqueteRepository historial;
    private final ClienteReferenciaService clientesCrm;
    private final ContratoReferenciaService contratosCrm;
    private final Clock clock;

    public PaqueteService(
            PaqueteRepository paquetes,
            DronRepository drones,
            HistorialEstadoPaqueteRepository historial,
            ClienteReferenciaService clientesCrm,
            ContratoReferenciaService contratosCrm,
            Clock clock) {

        this.paquetes = paquetes;
        this.drones = drones;
        this.historial = historial;
        this.clientesCrm = clientesCrm;
        this.contratosCrm = contratosCrm;
        this.clock = clock;
    }

    /**
     * Lista los paquetes que cumplen todos los filtros. Los clientes y los
     * contratos de la pagina se resuelven con una consulta en lote por servicio
     * (D3).
     */
    public PaginaResponse<PaqueteResponse> listar(
            FiltroPaquetes filtro,
            Pageable pageable) {

        Specification<Paquete> especificacion = Specification.allOf(
                EspecificacionesSga.estadoIgual(filtro.estado()),
                EspecificacionesSga.clienteIgual(filtro.clienteId()),
                EspecificacionesSga.contratoIgual(filtro.contratoId()),
                EspecificacionesSga.dronIgual(filtro.dronId()),
                EspecificacionesSga.codigoOTextoContiene(filtro.q(), "descripcion"));

        Page<Paquete> pagina = paquetes.findAll(especificacion, pageable);

        Set<UUID> clienteIds = pagina.getContent().stream()
                .map(Paquete::getClienteId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<UUID> contratoIds = pagina.getContent().stream()
                .map(Paquete::getContratoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, ClienteReferencia> clientes = clientesCrm.buscarTodos(clienteIds);
        Map<UUID, ContratoReferencia> contratos = contratosCrm.buscarTodos(contratoIds);

        return PaginaResponse.desde(pagina.map(paquete -> PaqueteResponse.desde(
                paquete,
                clientes.get(paquete.getClienteId()),
                paquete.getContratoId() == null
                        ? null
                        : contratos.get(paquete.getContratoId()))));
    }

    /**
     * Obtiene un paquete por su identificador.
     *
     * @throws RecursoNoEncontradoException si el paquete no existe
     */
    public PaqueteResponse obtener(UUID id) {
        Paquete paquete = paquetes.findConDronById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        PAQUETE_NO_ENCONTRADO));

        ClienteReferencia cliente = clientesCrm.buscar(paquete.getClienteId())
                .orElse(null);

        ContratoReferencia contrato = paquete.getContratoId() == null
                ? null
                : contratosCrm.buscar(paquete.getContratoId()).orElse(null);

        return PaqueteResponse.desde(paquete, cliente, contrato);
    }

    /**
     * Devuelve el historial completo del paquete ordenado por secuencia.
     *
     * @throws RecursoNoEncontradoException si el paquete no existe
     */
    public List<RegistroHistorialResponse> historial(UUID id) {
        if (!paquetes.existsById(id)) {
            throw new RecursoNoEncontradoException(PAQUETE_NO_ENCONTRADO);
        }

        return historial.findByPaqueteIdOrderBySecuenciaAsc(id).stream()
                .map(RegistroHistorialResponse::desde)
                .toList();
    }

    /**
     * Crea un paquete en {@code REGISTRADO} con su primer registro de historial
     * en el orden de D9: referencia de cliente, referencia de contrato, cliente
     * activo, contrato del cliente, contrato activo y codigo libre.
     *
     * @throws RecursoNoEncontradoException si el cliente o el contrato no
     *         existen
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si el
     *         cliente esta inactivo, el contrato es de otro cliente o no esta
     *         activo, o el codigo ya existe
     */
    @Transactional
    public PaqueteResponse crear(PaqueteCrearRequest solicitud, String actorSub) {
        ClienteReferencia cliente = clientesCrm.buscarParaAsociar(solicitud.clienteId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        CLIENTE_NO_ENCONTRADO, CAMPO_CLIENTE));

        ContratoReferencia contrato = solicitud.contratoId() == null
                ? null
                : contratosCrm.buscarParaAsociar(solicitud.contratoId())
                        .orElseThrow(() -> new RecursoNoEncontradoException(
                                CONTRATO_NO_ENCONTRADO, CAMPO_CONTRATO));

        if (!cliente.activo()) {
            throw ConflictosSga.clienteInactivo();
        }
        if (contrato != null && !contrato.clienteId().equals(solicitud.clienteId())) {
            throw ConflictosSga.contratoDeOtroCliente();
        }
        if (contrato != null && !contrato.activo()) {
            throw ConflictosSga.contratoNoActivo(contrato.estado());
        }
        if (paquetes.findByCodigo(solicitud.codigo()).isPresent()) {
            throw ConflictosSga.paqueteCodigoDuplicado();
        }

        UUID actor = UUID.fromString(actorSub);
        Instant ahora = ahora();

        Paquete paquete = paquetes.save(Paquete.crear(
                solicitud.codigo(),
                solicitud.descripcion(),
                solicitud.direccionEntrega(),
                solicitud.clienteId(),
                solicitud.contratoId(),
                actor,
                ahora));

        historial.save(HistorialEstadoPaquete.alta(
                paquete, solicitud.observacion(), actor, ahora));

        return PaqueteResponse.desde(paquete, cliente, contrato);
    }

    /**
     * Reemplaza los datos editables del paquete. Un contrato destino se acepta
     * si es {@code null}, si es el actual (aunque ya no este activo) o si es
     * otro del cliente y esta {@code ACTIVO} (Requirements 9.5-9.7).
     *
     * @throws RecursoNoEncontradoException si el paquete o el contrato nuevo no
     *         existen
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si el
     *         paquete no es editable o el contrato nuevo no es del cliente o no
     *         esta activo
     */
    @Transactional
    public PaqueteResponse actualizar(
            UUID id,
            PaqueteActualizarRequest solicitud,
            String actorSub) {

        Paquete paquete = paquetes.findParaModificarById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        PAQUETE_NO_ENCONTRADO));

        UUID contratoId = solicitud.contratoId();
        boolean contratoActual = contratoId != null
                && contratoId.equals(paquete.getContratoId());

        ContratoReferencia contrato = null;
        if (contratoId != null) {
            contrato = contratoActual
                    ? contratosCrm.buscar(contratoId).orElse(null)
                    : contratosCrm.buscarParaAsociar(contratoId)
                            .orElseThrow(() -> new RecursoNoEncontradoException(
                                    CONTRATO_NO_ENCONTRADO, CAMPO_CONTRATO));
        }

        if (!paquete.getEstado().esEditable()) {
            throw ConflictosSga.paqueteNoEditable(paquete.getEstado());
        }

        if (contrato != null && !contratoActual) {
            if (!contrato.clienteId().equals(paquete.getClienteId())) {
                throw ConflictosSga.contratoDeOtroCliente();
            }
            if (!contrato.activo()) {
                throw ConflictosSga.contratoNoActivo(contrato.estado());
            }
        }

        paquete.actualizarDatos(
                solicitud.descripcion(),
                solicitud.direccionEntrega(),
                contratoId,
                UUID.fromString(actorSub),
                ahora());

        ClienteReferencia cliente = clientesCrm.buscar(paquete.getClienteId())
                .orElse(null);

        return PaqueteResponse.desde(paquete, cliente, contrato);
    }

    /**
     * Aplica una transicion de estado en el orden de D9: existencia del
     * paquete, dron implicado, transicion permitida, dron disponible y dron sin
     * Paquete_En_Curso. El paquete, el dron y el historial se confirman juntos
     * (D8, D10).
     *
     * @throws RecursoNoEncontradoException si el paquete o el dron indicado no
     *         existen
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si la
     *         transicion no esta permitida, el dron no esta disponible o tiene
     *         un paquete en curso
     */
    @Transactional
    public RegistroHistorialResponse cambiarEstado(
            UUID id,
            TransicionPaqueteRequest solicitud,
            String actorSub) {

        Paquete paquete = paquetes.findParaModificarById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        PAQUETE_NO_ENCONTRADO));

        EstadoPaquete origen = paquete.getEstado();
        EstadoPaquete destino = solicitud.estado();

        Dron dron = null;
        if (destino == EstadoPaquete.ASIGNADO) {
            dron = drones.findParaModificarById(solicitud.dronId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            DRON_NO_ENCONTRADO, CAMPO_DRON));
        } else if (origen.efectoSobreDron(destino).isPresent()) {
            dron = drones.findParaModificarById(paquete.getDron().getId())
                    .orElseThrow();
        }

        if (!origen.puedeTransicionarA(destino)) {
            throw ConflictosSga.transicionNoPermitida(origen, destino);
        }

        if (destino == EstadoPaquete.ASIGNADO) {
            if (dron.getEstado() != EstadoDron.DISPONIBLE) {
                throw ConflictosSga.dronNoDisponible(dron.getEstado(), CAMPO_DRON);
            }
            paquetes.findFirstByDronIdAndEstadoIn(dron.getId(), EstadoPaquete.EN_CURSO)
                    .ifPresent(otro -> {
                        throw ConflictosSga.dronConPaqueteEnCurso(
                                otro.getId(), CAMPO_DRON);
                    });
        } else if (destino == EstadoPaquete.EN_TRANSITO
                && dron.getEstado() != EstadoDron.DISPONIBLE) {
            throw ConflictosSga.dronNoDisponible(dron.getEstado(), null);
        }

        UUID actor = UUID.fromString(actorSub);
        Instant ahora = ahora();
        int secuencia = (int) historial.countByPaqueteId(id) + 1;

        paquete.aplicarTransicion(destino, dron, actor, ahora);

        HistorialEstadoPaquete registro = historial.save(
                HistorialEstadoPaquete.transicion(
                        paquete,
                        secuencia,
                        origen,
                        destino,
                        destino == EstadoPaquete.ASIGNADO ? dron : null,
                        solicitud.observacion(),
                        actor,
                        ahora));

        return RegistroHistorialResponse.desde(registro);
    }

    private Instant ahora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
