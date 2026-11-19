package com.arise.corp.erp.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arise.corp.erp.dto.EmpleadoRequest;
import com.arise.corp.erp.dto.EmpleadoResponse;
import com.arise.corp.erp.entity.Empleado;
import com.arise.corp.erp.repository.EmpleadoRepository;
import com.arise.corp.erp.repository.EspecificacionesErp;
import com.arise.corp.shared.error.RecursoNoEncontradoException;
import com.arise.corp.shared.paginacion.PaginaResponse;

/**
 * Servicio de empleados (ERP-01, Requirements 4, 5, 6, 7 y 8).
 *
 * <p>Las lecturas son transacciones de solo lectura y las escrituras usan
 * {@code @Transactional}. La entidad se convierte en DTO dentro de la
 * transaccion porque {@code open-in-view} esta desactivado. El actor es el
 * {@code sub} del perfil activo y las marcas temporales salen del
 * {@link Clock} de la aplicacion, truncadas a microsegundos para coincidir con
 * {@code timestamptz}. No se registran datos de empleados en logs
 * (Requirement 8.3).
 */
@Service
@Transactional(readOnly = true)
public class EmpleadoService {

    private static final String EMPLEADO_NO_ENCONTRADO = "El empleado no existe.";

    private final EmpleadoRepository empleados;
    private final Clock clock;

    public EmpleadoService(EmpleadoRepository empleados, Clock clock) {
        this.empleados = empleados;
        this.clock = clock;
    }

    /**
     * Lista los empleados que cumplen los filtros.
     */
    public PaginaResponse<EmpleadoResponse> listar(
            FiltroEmpleados filtro,
            Pageable pageable) {

        Specification<Empleado> especificacion = Specification.allOf(
                EspecificacionesErp.nombreODocumentoContiene(filtro.q()),
                EspecificacionesErp.activoIgual(filtro.activo()));

        return PaginaResponse.desde(empleados.findAll(especificacion, pageable)
                .map(EmpleadoResponse::desde));
    }

    /**
     * Obtiene un empleado por su identificador.
     *
     * @throws RecursoNoEncontradoException si el empleado no existe
     */
    public EmpleadoResponse obtener(UUID id) {
        return EmpleadoResponse.desde(buscar(id));
    }

    /**
     * Crea un empleado activo con la auditoria del actor.
     *
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si el
     *         documento pertenece a un empleado activo (duplicado) o inactivo
     *         (inactivo existente)
     */
    @Transactional
    public EmpleadoResponse crear(EmpleadoRequest solicitud, String actorSub) {
        verificarDocumentoLibre(solicitud, null);

        Empleado empleado = Empleado.crear(
                solicitud.tipoDocumento(),
                solicitud.numeroDocumento(),
                solicitud.nombre(),
                solicitud.cargo(),
                solicitud.area(),
                solicitud.correo(),
                solicitud.telefono(),
                solicitud.fechaIngreso(),
                UUID.fromString(actorSub),
                ahora());

        return EmpleadoResponse.desde(empleados.save(empleado));
    }

    /**
     * Reemplaza los datos del empleado y conserva su estado y su fecha de
     * creacion. Un documento de otro empleado, activo o inactivo, es un
     * conflicto.
     *
     * @throws RecursoNoEncontradoException si el empleado no existe
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si el
     *         documento pertenece a otro empleado
     */
    @Transactional
    public EmpleadoResponse actualizar(
            UUID id,
            EmpleadoRequest solicitud,
            String actorSub) {

        Empleado empleado = buscar(id);
        verificarDocumentoDeOtroEmpleado(solicitud, empleado.getId());

        empleado.actualizarDatos(
                solicitud.tipoDocumento(),
                solicitud.numeroDocumento(),
                solicitud.nombre(),
                solicitud.cargo(),
                solicitud.area(),
                solicitud.correo(),
                solicitud.telefono(),
                solicitud.fechaIngreso(),
                UUID.fromString(actorSub),
                ahora());

        return EmpleadoResponse.desde(empleado);
    }

    /**
     * Activa o desactiva el empleado. Si el estado coincide, la auditoria no
     * cambia.
     *
     * @throws RecursoNoEncontradoException si el empleado no existe
     */
    @Transactional
    public EmpleadoResponse cambiarEstado(
            UUID id,
            boolean activo,
            String actorSub) {

        Empleado empleado = buscar(id);
        empleado.cambiarEstado(activo, UUID.fromString(actorSub), ahora());

        return EmpleadoResponse.desde(empleado);
    }

    private void verificarDocumentoLibre(
            EmpleadoRequest solicitud,
            UUID empleadoActualId) {

        empleados.findByTipoDocumentoAndNumeroDocumento(
                        solicitud.tipoDocumento(), solicitud.numeroDocumento())
                .filter(otro -> !otro.getId().equals(empleadoActualId))
                .ifPresent(otro -> {
                    if (otro.isActivo()) {
                        throw ConflictosErp.empleadoDuplicado();
                    }
                    throw ConflictosErp.empleadoInactivoExistente(otro.getId());
                });
    }

    private void verificarDocumentoDeOtroEmpleado(
            EmpleadoRequest solicitud,
            UUID empleadoId) {

        empleados.findByTipoDocumentoAndNumeroDocumento(
                        solicitud.tipoDocumento(), solicitud.numeroDocumento())
                .filter(otro -> !otro.getId().equals(empleadoId))
                .ifPresent(otro -> {
                    throw ConflictosErp.empleadoDuplicado();
                });
    }

    private Empleado buscar(UUID id) {
        return empleados.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        EMPLEADO_NO_ENCONTRADO));
    }

    private Instant ahora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
