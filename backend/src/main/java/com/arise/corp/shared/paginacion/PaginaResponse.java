package com.arise.corp.shared.paginacion;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Representacion de una pagina de la API (DP-API-03).
 *
 * <p>No se serializa directamente {@code Page} de Spring Data: la respuesta
 * expone solo {@code content}, {@code page}, {@code size},
 * {@code totalElements} y {@code totalPages}.
 */
public record PaginaResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PaginaResponse<T> desde(Page<T> pagina) {
        return new PaginaResponse<>(
                List.copyOf(pagina.getContent()),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }
}
