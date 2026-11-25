package com.arise.corp.sga.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arise.corp.sga.dto.ResumenSgaResponse;
import com.arise.corp.sga.repository.ArticuloInventarioRepository;
import com.arise.corp.sga.repository.DronRepository;
import com.arise.corp.sga.repository.PaqueteRepository;

/**
 * Indicadores del SGA (SGA-06, Requirement 16, D13).
 *
 * <p>Una consulta agrupada por tabla y una funcion pura
 * {@link ResumenSgaResponse#desde} que completa ceros y calcula los totales.
 */
@Service
@Transactional(readOnly = true)
public class ResumenSgaService {

    private final PaqueteRepository paquetes;
    private final DronRepository drones;
    private final ArticuloInventarioRepository articulos;

    public ResumenSgaService(
            PaqueteRepository paquetes,
            DronRepository drones,
            ArticuloInventarioRepository articulos) {

        this.paquetes = paquetes;
        this.drones = drones;
        this.articulos = articulos;
    }

    public ResumenSgaResponse obtener() {
        return ResumenSgaResponse.desde(
                paquetes.contarPorEstado(),
                drones.contarPorEstado(),
                articulos.contarArticulos());
    }
}
