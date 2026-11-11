package com.arise.corp.crm.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arise.corp.crm.dto.ResumenComercialResponse;
import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.crm.repository.ClienteRepository;
import com.arise.corp.crm.repository.ConteoPorClave;
import com.arise.corp.crm.repository.ContratoRepository;

/**
 * Resumen comercial del CRM (CRM-05, Requirement 15).
 *
 * <p>Ejecuta dos consultas agrupadas dentro de una transaccion de solo lectura:
 * {@code group by activo} sobre clientes y {@code group by estado} sobre
 * contratos. Los totales se calculan como suma de sus componentes, de modo que
 * la coherencia del resumen no depende de consultas adicionales
 * (Requirement 15.3) y los cuatro estados aparecen siempre, con 0 cuando no hay
 * contratos (Requirement 15.2).
 */
@Service
@Transactional(readOnly = true)
public class ResumenComercialService {

    private final ClienteRepository clientes;
    private final ContratoRepository contratos;

    public ResumenComercialService(
            ClienteRepository clientes,
            ContratoRepository contratos) {

        this.clientes = clientes;
        this.contratos = contratos;
    }

    /**
     * Obtiene el resumen con los datos persistidos en el momento de la
     * solicitud.
     */
    public ResumenComercialResponse obtener() {
        Map<Boolean, Long> porActivo = indexar(clientes.contarPorActivo());
        Map<EstadoContrato, Long> porEstado = indexar(contratos.contarPorEstado());

        return new ResumenComercialResponse(
                ResumenComercialResponse.ConteoClientes.desde(
                        porActivo.getOrDefault(Boolean.TRUE, 0L),
                        porActivo.getOrDefault(Boolean.FALSE, 0L)),
                ResumenComercialResponse.ConteoContratos.desde(porEstado));
    }

    private static <K> Map<K, Long> indexar(List<ConteoPorClave<K>> conteos) {
        Map<K, Long> indice = new HashMap<>();

        for (ConteoPorClave<K> conteo : conteos) {
            if (conteo != null && conteo.getClave() != null) {
                indice.put(conteo.getClave(), conteo.getTotal());
            }
        }

        return indice;
    }
}
