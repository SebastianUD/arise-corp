package com.arise.corp.erp.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arise.corp.erp.dto.ProveedorRequest;
import com.arise.corp.erp.dto.ProveedorResponse;
import com.arise.corp.erp.entity.Proveedor;
import com.arise.corp.erp.repository.EspecificacionesErp;
import com.arise.corp.erp.repository.ProveedorRepository;
import com.arise.corp.shared.error.RecursoNoEncontradoException;
import com.arise.corp.shared.paginacion.PaginaResponse;

/**
 * Servicio de proveedores (ERP-02, Requirements 9, 10, 11 y 12).
 *
 * <p>Mismo patron transaccional, de reloj y de auditoria que
 * {@link EmpleadoService}. Cada servicio busca duplicados solo en su tabla, asi
 * que un mismo documento puede existir a la vez como empleado, proveedor y
 * cliente. Desactivar un proveedor no consulta ni modifica sus movimientos
 * (Requirement 12.4) y no se registran datos de contacto en logs.
 */
@Service
@Transactional(readOnly = true)
public class ProveedorService {

    private static final String PROVEEDOR_NO_ENCONTRADO =
            "El proveedor no existe.";

    private final ProveedorRepository proveedores;
    private final Clock clock;

    public ProveedorService(ProveedorRepository proveedores, Clock clock) {
        this.proveedores = proveedores;
        this.clock = clock;
    }

    /**
     * Lista los proveedores que cumplen los filtros.
     */
    public PaginaResponse<ProveedorResponse> listar(
            FiltroProveedores filtro,
            Pageable pageable) {

        Specification<Proveedor> especificacion = Specification.allOf(
                EspecificacionesErp.nombreODocumentoContiene(filtro.q()),
                EspecificacionesErp.activoIgual(filtro.activo()));

        return PaginaResponse.desde(proveedores.findAll(especificacion, pageable)
                .map(ProveedorResponse::desde));
    }

    /**
     * Obtiene un proveedor por su identificador.
     *
     * @throws RecursoNoEncontradoException si el proveedor no existe
     */
    public ProveedorResponse obtener(UUID id) {
        return ProveedorResponse.desde(buscar(id));
    }

    /**
     * Crea un proveedor activo con la auditoria del actor.
     *
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si el
     *         documento pertenece a un proveedor activo (duplicado) o inactivo
     *         (inactivo existente)
     */
    @Transactional
    public ProveedorResponse crear(
            ProveedorRequest solicitud,
            String actorSub) {

        verificarDocumentoLibre(solicitud, null);

        Proveedor proveedor = Proveedor.crear(
                solicitud.tipoDocumento(),
                solicitud.numeroDocumento(),
                solicitud.nombre(),
                solicitud.nombreContacto(),
                solicitud.correo(),
                solicitud.telefono(),
                solicitud.direccion(),
                solicitud.ciudad(),
                UUID.fromString(actorSub),
                ahora());

        return ProveedorResponse.desde(proveedores.save(proveedor));
    }

    /**
     * Reemplaza los datos del proveedor y conserva su estado y su fecha de
     * creacion. Un opcional omitido queda {@code null}. Un documento de otro
     * proveedor es un conflicto.
     *
     * @throws RecursoNoEncontradoException si el proveedor no existe
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si el
     *         documento pertenece a otro proveedor
     */
    @Transactional
    public ProveedorResponse actualizar(
            UUID id,
            ProveedorRequest solicitud,
            String actorSub) {

        Proveedor proveedor = buscar(id);
        verificarDocumentoDeOtroProveedor(solicitud, proveedor.getId());

        proveedor.actualizarDatos(
                solicitud.tipoDocumento(),
                solicitud.numeroDocumento(),
                solicitud.nombre(),
                solicitud.nombreContacto(),
                solicitud.correo(),
                solicitud.telefono(),
                solicitud.direccion(),
                solicitud.ciudad(),
                UUID.fromString(actorSub),
                ahora());

        return ProveedorResponse.desde(proveedor);
    }

    /**
     * Activa o desactiva el proveedor sin consultar ni modificar sus
     * movimientos. Si el estado coincide, la auditoria no cambia.
     *
     * @throws RecursoNoEncontradoException si el proveedor no existe
     */
    @Transactional
    public ProveedorResponse cambiarEstado(
            UUID id,
            boolean activo,
            String actorSub) {

        Proveedor proveedor = buscar(id);
        proveedor.cambiarEstado(activo, UUID.fromString(actorSub), ahora());

        return ProveedorResponse.desde(proveedor);
    }

    private void verificarDocumentoLibre(
            ProveedorRequest solicitud,
            UUID proveedorActualId) {

        proveedores.findByTipoDocumentoAndNumeroDocumento(
                        solicitud.tipoDocumento(), solicitud.numeroDocumento())
                .filter(otro -> !otro.getId().equals(proveedorActualId))
                .ifPresent(otro -> {
                    if (otro.isActivo()) {
                        throw ConflictosErp.proveedorDuplicado();
                    }
                    throw ConflictosErp.proveedorInactivoExistente(otro.getId());
                });
    }

    private void verificarDocumentoDeOtroProveedor(
            ProveedorRequest solicitud,
            UUID proveedorId) {

        proveedores.findByTipoDocumentoAndNumeroDocumento(
                        solicitud.tipoDocumento(), solicitud.numeroDocumento())
                .filter(otro -> !otro.getId().equals(proveedorId))
                .ifPresent(otro -> {
                    throw ConflictosErp.proveedorDuplicado();
                });
    }

    private Proveedor buscar(UUID id) {
        return proveedores.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        PROVEEDOR_NO_ENCONTRADO));
    }

    private Instant ahora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
