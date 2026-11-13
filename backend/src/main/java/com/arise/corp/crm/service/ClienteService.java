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

import com.arise.corp.crm.dto.ClienteRequest;
import com.arise.corp.crm.dto.ClienteResponse;
import com.arise.corp.crm.dto.ClienteResumenResponse;
import com.arise.corp.crm.dto.ClienteVista;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.CuentaCorporativa;
import com.arise.corp.crm.repository.ClienteRepository;
import com.arise.corp.crm.repository.CuentaCorporativaRepository;
import com.arise.corp.crm.repository.EspecificacionesCrm;
import com.arise.corp.shared.error.RecursoNoEncontradoException;
import com.arise.corp.shared.paginacion.PaginaResponse;

/**
 * Servicio de clientes (CRM-01, Requirements 4, 5, 6, 7, 8 y 10).
 *
 * <p>Las lecturas son transacciones de solo lectura y las escrituras usan
 * {@code @Transactional}. La entidad se convierte en DTO dentro de la
 * transaccion porque {@code open-in-view} esta desactivado. El actor se recibe
 * como el {@code sub} del perfil activo y las marcas temporales salen del
 * {@link Clock} de la aplicacion, truncadas a microsegundos para coincidir con
 * {@code timestamptz} (D10, DP-SEG-06). No se registran datos de clientes en
 * logs (Requirement 6.4).
 */
@Service
@Transactional(readOnly = true)
public class ClienteService {

    private static final String CLIENTE_NO_ENCONTRADO = "El cliente no existe.";
    private static final String CUENTA_NO_ENCONTRADA = "La cuenta corporativa no existe.";
    private static final String CAMPO_CUENTA = "cuentaCorporativaId";

    private final ClienteRepository clientes;
    private final CuentaCorporativaRepository cuentas;
    private final Clock clock;

    public ClienteService(
            ClienteRepository clientes,
            CuentaCorporativaRepository cuentas,
            Clock clock) {

        this.clientes = clientes;
        this.cuentas = cuentas;
        this.clock = clock;
    }

    /**
     * Lista los clientes que cumplen los filtros con la vista que corresponde
     * al actor. La cuenta corporativa se carga con {@code @EntityGraph} para
     * evitar N+1.
     */
    public PaginaResponse<ClienteVista> listar(
            FiltroClientes filtro,
            Pageable pageable,
            VistaCliente vista) {

        Specification<Cliente> especificacion = Specification.allOf(
                EspecificacionesCrm.nombreODocumentoContiene(filtro.q()),
                EspecificacionesCrm.activoIgual(filtro.activo()),
                EspecificacionesCrm.cuentaIgual(filtro.cuentaCorporativaId()));

        Page<ClienteVista> pagina = clientes.findAll(especificacion, pageable)
                .map(cliente -> convertir(cliente, vista));

        return PaginaResponse.desde(pagina);
    }

    /**
     * Obtiene un cliente por su identificador con la vista del actor.
     *
     * @throws RecursoNoEncontradoException si el cliente no existe
     */
    public ClienteVista obtener(UUID id, VistaCliente vista) {
        return convertir(buscar(id), vista);
    }

    /**
     * Crea un cliente activo con la auditoria del actor.
     *
     * @throws RecursoNoEncontradoException si la cuenta corporativa indicada no
     *         existe
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si el
     *         documento pertenece a un cliente activo o inactivo, o si la
     *         cuenta destino esta inactiva
     */
    @Transactional
    public ClienteResponse crear(ClienteRequest solicitud, String actorSub) {
        CuentaCorporativa cuenta = resolverCuenta(solicitud.cuentaCorporativaId(), null);
        verificarDocumentoLibre(solicitud, null);

        if (cuenta != null && !cuenta.isActiva()) {
            throw ConflictosCrm.cuentaInactiva();
        }

        Cliente cliente = Cliente.crear(
                solicitud.tipoDocumento(),
                solicitud.numeroDocumento(),
                solicitud.nombre(),
                solicitud.correo(),
                solicitud.telefono(),
                solicitud.direccion(),
                solicitud.ciudad(),
                cuenta,
                UUID.fromString(actorSub),
                ahora());

        return ClienteResponse.desde(clientes.save(cliente));
    }

    /**
     * Reemplaza los datos del cliente y conserva su estado y su fecha de
     * creacion. La cuenta destino {@code null} quita la asociacion; la cuenta
     * actual se conserva aunque este inactiva.
     *
     * @throws RecursoNoEncontradoException si el cliente o la cuenta destino no
     *         existen
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si el
     *         documento pertenece a otro cliente o si la cuenta destino es
     *         distinta a la actual y esta inactiva
     */
    @Transactional
    public ClienteResponse actualizar(UUID id, ClienteRequest solicitud, String actorSub) {
        Cliente cliente = buscar(id);
        CuentaCorporativa cuentaActual = cliente.getCuentaCorporativa();
        CuentaCorporativa cuentaDestino = resolverCuenta(
                solicitud.cuentaCorporativaId(), cuentaActual);

        verificarDocumentoDeOtroCliente(solicitud, cliente.getId());

        if (cuentaDestino != null
                && cuentaDestino != cuentaActual
                && !cuentaDestino.isActiva()) {
            throw ConflictosCrm.cuentaInactiva();
        }

        cliente.actualizarDatos(
                solicitud.tipoDocumento(),
                solicitud.numeroDocumento(),
                solicitud.nombre(),
                solicitud.correo(),
                solicitud.telefono(),
                solicitud.direccion(),
                solicitud.ciudad(),
                cuentaDestino,
                UUID.fromString(actorSub),
                ahora());

        return ClienteResponse.desde(cliente);
    }

    /**
     * Activa o desactiva el cliente sin consultar ni modificar sus contratos.
     * Si el estado coincide, la auditoria no cambia.
     *
     * @throws RecursoNoEncontradoException si el cliente no existe
     */
    @Transactional
    public ClienteResponse cambiarEstado(UUID id, boolean activo, String actorSub) {
        Cliente cliente = buscar(id);
        cliente.cambiarEstado(activo, UUID.fromString(actorSub), ahora());

        return ClienteResponse.desde(cliente);
    }

    private ClienteVista convertir(Cliente cliente, VistaCliente vista) {
        return vista == VistaCliente.COMPLETA
                ? ClienteResponse.desde(cliente)
                : ClienteResumenResponse.desde(cliente);
    }

    private CuentaCorporativa resolverCuenta(UUID cuentaId, CuentaCorporativa actual) {
        if (cuentaId == null) {
            return null;
        }
        if (actual != null && actual.getId().equals(cuentaId)) {
            return actual;
        }

        return cuentas.findParaAsignarById(cuentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        CUENTA_NO_ENCONTRADA, CAMPO_CUENTA));
    }

    private void verificarDocumentoLibre(ClienteRequest solicitud, UUID clienteActualId) {
        clientes.findByTipoDocumentoAndNumeroDocumento(
                        solicitud.tipoDocumento(), solicitud.numeroDocumento())
                .filter(otro -> !otro.getId().equals(clienteActualId))
                .ifPresent(otro -> {
                    if (otro.isActivo()) {
                        throw ConflictosCrm.clienteDuplicado();
                    }
                    throw ConflictosCrm.clienteInactivoExistente(otro.getId());
                });
    }

    private void verificarDocumentoDeOtroCliente(ClienteRequest solicitud, UUID clienteId) {
        clientes.findByTipoDocumentoAndNumeroDocumento(
                        solicitud.tipoDocumento(), solicitud.numeroDocumento())
                .filter(otro -> !otro.getId().equals(clienteId))
                .ifPresent(otro -> {
                    throw ConflictosCrm.clienteDuplicado();
                });
    }

    private Cliente buscar(UUID id) {
        return clientes.findConCuentaById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(CLIENTE_NO_ENCONTRADO));
    }

    private Instant ahora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
