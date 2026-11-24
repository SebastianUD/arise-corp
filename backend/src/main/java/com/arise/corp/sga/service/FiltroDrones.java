package com.arise.corp.sga.service;

import com.arise.corp.sga.entity.EstadoDron;

/**
 * Filtros de la lista de drones (Requirement 5).
 *
 * <p>Record sin logica: todos los campos son opcionales y se combinan con
 * {@code y}.
 */
public record FiltroDrones(String q, EstadoDron estado, Boolean asignable) {
}
