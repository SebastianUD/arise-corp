package com.arise.corp.crm.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.arise.corp.crm.dto.ContratoReferencia;
import com.arise.corp.crm.entity.Contrato;
import com.arise.corp.crm.repository.ContratoRepository;

/**
 * Lectura de contratos para los modulos consumidores (D2, DP-ARQ-01).
 *
 * <p>Es la unica puerta del CRM para que otros modulos consulten contratos: el
 * consumidor nunca ve la entidad {@link Contrato} ni el repositorio. Las
 * lecturas se unen a la transaccion del servicio llamador (propagacion
 * {@code REQUIRED}) y {@link #buscarParaAsociar(UUID)} exige una transaccion
 * abierta para que el bloqueo de lectura proteja la asociacion.
 */
@Service
@Transactional(readOnly = true)
public class ContratoReferenciaService {

    private final ContratoRepository contratos;

    public ContratoReferenciaService(ContratoRepository contratos) {
        this.contratos = contratos;
    }

    /**
     * Existencia, cliente, codigo y estado del contrato, sin bloqueo.
     */
    public Optional<ContratoReferencia> buscar(UUID id) {
        return contratos.findById(id).map(ContratoReferencia::desde);
    }

    /**
     * Referencias de varios contratos en una sola consulta
     * ({@code findAllById}). Una coleccion vacia devuelve un mapa vacio sin
     * consultar. Los ids sin contrato no aparecen.
     */
    public Map<UUID, ContratoReferencia> buscarTodos(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ContratoReferencia> referencias = new HashMap<>();
        for (Contrato contrato : contratos.findAllById(ids)) {
            ContratoReferencia referencia = ContratoReferencia.desde(contrato);
            referencias.put(referencia.id(), referencia);
        }

        return Map.copyOf(referencias);
    }

    /**
     * Carga el contrato con bloqueo de lectura ({@code PESSIMISTIC_READ})
     * dentro de la transaccion del llamador, que debe existir. Reutiliza
     * {@link ContratoRepository#findParaAsociarById(UUID)}.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException
     *         si se invoca fuera de una transaccion
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ContratoReferencia> buscarParaAsociar(UUID id) {
        return contratos.findParaAsociarById(id).map(ContratoReferencia::desde);
    }
}
