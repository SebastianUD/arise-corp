package com.arise.corp.sga.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arise.corp.sga.dto.DronRequest;
import com.arise.corp.sga.dto.DronResponse;
import com.arise.corp.sga.entity.Dron;
import com.arise.corp.sga.entity.EstadoDron;
import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.entity.Paquete;
import com.arise.corp.sga.repository.DronRepository;
import com.arise.corp.sga.repository.EspecificacionesSga;
import com.arise.corp.sga.repository.PaqueteRepository;
import com.arise.corp.shared.error.RecursoNoEncontradoException;
import com.arise.corp.shared.paginacion.PaginaResponse;

/**
 * Servicio de drones (SGA-01, Requirements 4, 5 y 6).
 *
 * <p>Las lecturas son transacciones de solo lectura y las escrituras usan
 * {@code @Transactional}. La entidad se convierte en DTO dentro de la
 * transaccion porque {@code open-in-view} esta desactivado. El actor es el
 * {@code sub} del perfil activo y las marcas temporales salen del
 * {@link Clock} de la aplicacion, truncadas a microsegundos (D14). No se
 * registran observaciones en logs (Requirement 17.3).
 */
@Service
@Transactional(readOnly = true)
public class DronService {

    private static final String DRON_NO_ENCONTRADO = "El dron no existe.";

    private final DronRepository drones;
    private final PaqueteRepository paquetes;
    private final Clock clock;

    public DronService(
            DronRepository drones,
            PaqueteRepository paquetes,
            Clock clock) {

        this.drones = drones;
        this.paquetes = paquetes;
        this.clock = clock;
    }

    /**
     * Lista los drones que cumplen todos los filtros. El Paquete_En_Curso de
     * la pagina se resuelve con una sola consulta (interpretacion 4).
     */
    public PaginaResponse<DronResponse> listar(
            FiltroDrones filtro,
            Pageable pageable) {

        Specification<Dron> especificacion = Specification.allOf(
                EspecificacionesSga.codigoOTextoContiene(filtro.q(), "modelo"),
                EspecificacionesSga.estadoIgual(filtro.estado()),
                EspecificacionesSga.asignable(filtro.asignable()));

        Page<Dron> pagina = drones.findAll(especificacion, pageable);

        Set<UUID> dronIds = pagina.getContent().stream()
                .map(Dron::getId)
                .collect(Collectors.toSet());

        Map<UUID, Paquete> enCurso = dronIds.isEmpty()
                ? Map.of()
                : paquetes.findByDronIdInAndEstadoIn(dronIds, EstadoPaquete.EN_CURSO).stream()
                        .collect(Collectors.toMap(
                                paquete -> paquete.getDron().getId(),
                                Function.identity(),
                                (primero, ignorado) -> primero));

        return PaginaResponse.desde(pagina.map(dron -> DronResponse.desde(
                dron, enCurso.get(dron.getId()))));
    }

    /**
     * Obtiene un dron por su identificador.
     *
     * @throws RecursoNoEncontradoException si el dron no existe
     */
    public DronResponse obtener(UUID id) {
        Dron dron = buscar(id);

        return DronResponse.desde(dron, paqueteEnCurso(id));
    }

    /**
     * Crea un dron en {@code DISPONIBLE} con la auditoria del actor.
     *
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si el
     *         codigo ya esta registrado
     */
    @Transactional
    public DronResponse crear(DronRequest solicitud, String actorSub) {
        if (drones.findByCodigo(solicitud.codigo()).isPresent()) {
            throw ConflictosSga.dronCodigoDuplicado();
        }

        Dron dron = drones.save(Dron.crear(
                solicitud.codigo(),
                solicitud.modelo(),
                solicitud.observaciones(),
                UUID.fromString(actorSub),
                ahora()));

        return DronResponse.desde(dron, null);
    }

    /**
     * Reemplaza los datos del dron y conserva su estado, su auditoria y su
     * fecha de creacion.
     *
     * @throws RecursoNoEncontradoException si el dron no existe
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si el
     *         codigo pertenece a otro dron
     */
    @Transactional
    public DronResponse actualizar(
            UUID id,
            DronRequest solicitud,
            String actorSub) {

        Dron dron = drones.findParaModificarById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        DRON_NO_ENCONTRADO));

        drones.findByCodigo(solicitud.codigo())
                .filter(otro -> !otro.getId().equals(dron.getId()))
                .ifPresent(otro -> {
                    throw ConflictosSga.dronCodigoDuplicado();
                });

        dron.actualizarDatos(
                solicitud.codigo(),
                solicitud.modelo(),
                solicitud.observaciones(),
                UUID.fromString(actorSub),
                ahora());

        return DronResponse.desde(dron, paqueteEnCurso(id));
    }

    /**
     * Cambio manual de estado en el orden de D9: existencia, transicion manual
     * y Paquete_En_Curso.
     *
     * @throws RecursoNoEncontradoException si el dron no existe
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si la
     *         transicion no es manual o si el dron tiene un paquete en curso
     */
    @Transactional
    public DronResponse cambiarEstado(
            UUID id,
            EstadoDron destino,
            String actorSub) {

        Dron dron = drones.findParaModificarById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        DRON_NO_ENCONTRADO));

        if (!dron.getEstado().puedeCambiarManualmenteA(destino)) {
            throw ConflictosSga.transicionDronNoPermitida(dron.getEstado(), destino);
        }

        paquetes.findFirstByDronIdAndEstadoIn(id, EstadoPaquete.EN_CURSO)
                .ifPresent(paquete -> {
                    throw ConflictosSga.dronConPaqueteEnCurso(paquete.getId(), null);
                });

        dron.cambiarEstadoManual(destino, UUID.fromString(actorSub), ahora());

        return DronResponse.desde(dron, null);
    }

    private Dron buscar(UUID id) {
        return drones.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        DRON_NO_ENCONTRADO));
    }

    private Paquete paqueteEnCurso(UUID dronId) {
        return paquetes.findFirstByDronIdAndEstadoIn(
                        dronId, EstadoPaquete.EN_CURSO)
                .orElse(null);
    }

    private Instant ahora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
