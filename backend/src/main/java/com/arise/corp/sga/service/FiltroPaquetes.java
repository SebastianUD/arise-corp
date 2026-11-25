package com.arise.corp.sga.service;

import java.util.UUID;

import com.arise.corp.sga.entity.EstadoPaquete;

/**
 * Filtros de la lista de paquetes (Requirement 8).
 *
 * <p>Record sin logica: todos los campos son opcionales y se combinan con
 * {@code y}. Un identificador de cliente, contrato o dron no comprueba su
 * existencia.
 */
public record FiltroPaquetes(
        EstadoPaquete estado,
        UUID clienteId,
        UUID contratoId,
        UUID dronId,
        String q) {
}
