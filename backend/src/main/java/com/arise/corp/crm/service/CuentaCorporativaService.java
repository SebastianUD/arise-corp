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

import com.arise.corp.crm.dto.CuentaCorporativaRequest;
import com.arise.corp.crm.dto.CuentaCorporativaResponse;
import com.arise.corp.crm.entity.CuentaCorporativa;
import com.arise.corp.crm.repository.CuentaCorporativaRepository;
import com.arise.corp.crm.repository.EspecificacionesCrm;
import com.arise.corp.shared.error.RecursoNoEncontradoException;
import com.arise.corp.shared.paginacion.PaginaResponse;

/**
 * Servicio de cuentas corporativas (CRM-02, Requirement 9).
 *
 * <p>Las lecturas son transacciones de solo lectura y las escrituras usan
 * {@code @Transactional}. La entidad se convierte en DTO dentro de la
 * transaccion porque {@code open-in-view} esta desactivado. El actor se
 * recibe como el {@code sub} del perfil activo y las marcas temporales salen
 * del {@link Clock} de la aplicacion, truncadas a microsegundos para coincidir
 * con {@code timestamptz} (D10, DP-SEG-06). No se registran datos de la cuenta
 * en logs.
 */
@Service
@Transactional(readOnly = true)
public class CuentaCorporativaService {

    private static final String CUENTA_NO_ENCONTRADA = "La cuenta corporativa no existe.";

    private final CuentaCorporativaRepository cuentas;
    private final Clock clock;

    public CuentaCorporativaService(CuentaCorporativaRepository cuentas, Clock clock) {
        this.cuentas = cuentas;
        this.clock = clock;
    }

    /**
     * Lista las cuentas que cumplen los filtros, con la paginacion y el orden
     * ya validados por el controlador.
     */
    public PaginaResponse<CuentaCorporativaResponse> listar(FiltroCuentas filtro, Pageable pageable) {
        Specification<CuentaCorporativa> especificacion = Specification.allOf(
                EspecificacionesCrm.razonSocialONitContiene(filtro.q()),
                EspecificacionesCrm.activaIgual(filtro.activa()));

        Page<CuentaCorporativaResponse> pagina = cuentas.findAll(especificacion, pageable)
                .map(CuentaCorporativaResponse::desde);

        return PaginaResponse.desde(pagina);
    }

    /**
     * Obtiene una cuenta por su identificador.
     *
     * @throws RecursoNoEncontradoException si la cuenta no existe
     */
    public CuentaCorporativaResponse obtener(UUID id) {
        return CuentaCorporativaResponse.desde(buscar(id));
    }

    /**
     * Crea una cuenta activa con la auditoria del actor.
     *
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si el NIT
     *         pertenece a otra cuenta, activa o inactiva
     */
    @Transactional
    public CuentaCorporativaResponse crear(CuentaCorporativaRequest solicitud, String actorSub) {
        verificarNitDisponible(solicitud.nit(), null);

        Instant ahora = ahora();
        CuentaCorporativa cuenta = CuentaCorporativa.crear(
                solicitud.nit(),
                solicitud.razonSocial(),
                solicitud.nombreContacto(),
                solicitud.correoContacto(),
                solicitud.telefonoContacto(),
                UUID.fromString(actorSub),
                ahora);

        return CuentaCorporativaResponse.desde(cuentas.save(cuenta));
    }

    /**
     * Reemplaza los datos de la cuenta y conserva su estado y su fecha de
     * creacion.
     *
     * @throws RecursoNoEncontradoException si la cuenta no existe
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si el NIT
     *         pertenece a otra cuenta
     */
    @Transactional
    public CuentaCorporativaResponse actualizar(
            UUID id,
            CuentaCorporativaRequest solicitud,
            String actorSub) {

        CuentaCorporativa cuenta = buscar(id);
        verificarNitDisponible(solicitud.nit(), cuenta.getId());

        cuenta.actualizarDatos(
                solicitud.nit(),
                solicitud.razonSocial(),
                solicitud.nombreContacto(),
                solicitud.correoContacto(),
                solicitud.telefonoContacto(),
                UUID.fromString(actorSub),
                ahora());

        return CuentaCorporativaResponse.desde(cuenta);
    }

    /**
     * Activa o desactiva la cuenta sin tocar sus clientes. Si el estado
     * coincide, la auditoria no cambia.
     *
     * @throws RecursoNoEncontradoException si la cuenta no existe
     */
    @Transactional
    public CuentaCorporativaResponse cambiarEstado(UUID id, boolean activa, String actorSub) {
        CuentaCorporativa cuenta = buscar(id);
        cuenta.cambiarEstado(activa, UUID.fromString(actorSub), ahora());

        return CuentaCorporativaResponse.desde(cuenta);
    }

    private void verificarNitDisponible(String nit, UUID cuentaActualId) {
        cuentas.findByNit(nit)
                .filter(otra -> !otra.getId().equals(cuentaActualId))
                .ifPresent(otra -> {
                    throw ConflictosCrm.cuentaNitDuplicado();
                });
    }

    private CuentaCorporativa buscar(UUID id) {
        return cuentas.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(CUENTA_NO_ENCONTRADA));
    }

    private Instant ahora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
