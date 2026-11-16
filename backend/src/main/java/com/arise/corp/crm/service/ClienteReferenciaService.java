package com.arise.corp.crm.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.arise.corp.crm.dto.ClienteReferencia;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.repository.ClienteRepository;

/**
 * Lectura de clientes para los modulos consumidores (D2, DP-ARQ-01).
 *
 * <p>Es la unica puerta del CRM para que otros modulos consulten clientes: el
 * consumidor nunca ve la entidad {@link Cliente} ni el repositorio. Las
 * lecturas se unen a la transaccion del servicio llamador (propagacion
 * {@code REQUIRED}) y {@link #buscarParaAsociar(UUID)} exige una transaccion
 * abierta para que el bloqueo de lectura proteja la asociacion.
 */
@Service
@Transactional(readOnly = true)
public class ClienteReferenciaService {

    private final ClienteRepository clientes;

    public ClienteReferenciaService(ClienteRepository clientes) {
        this.clientes = clientes;
    }

    /**
     * Existencia, nombre y estado del cliente, sin bloqueo.
     */
    public Optional<ClienteReferencia> buscar(UUID id) {
        return clientes.findById(id).map(ClienteReferencia::desde);
    }

    /**
     * Referencias de varios clientes en una sola consulta ({@code findAllById}).
     * Una coleccion vacia devuelve un mapa vacio sin consultar. Los ids sin
     * cliente no aparecen.
     */
    public Map<UUID, ClienteReferencia> buscarTodos(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ClienteReferencia> referencias = new HashMap<>();
        for (Cliente cliente : clientes.findAllById(ids)) {
            ClienteReferencia referencia = ClienteReferencia.desde(cliente);
            referencias.put(referencia.id(), referencia);
        }

        return Map.copyOf(referencias);
    }

    /**
     * Carga el cliente con bloqueo de lectura ({@code PESSIMISTIC_READ}) dentro
     * de la transaccion del llamador, que debe existir. Reutiliza
     * {@link ClienteRepository#findParaContratoById(UUID)}.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException
     *         si se invoca fuera de una transaccion
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ClienteReferencia> buscarParaAsociar(UUID id) {
        return clientes.findParaContratoById(id).map(ClienteReferencia::desde);
    }
}
