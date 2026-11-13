package com.arise.corp.shared.paginacion;

import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Construccion de {@link Pageable} a partir de los parametros de lista
 * (DP-API-03, D7).
 *
 * <p>El {@code sort} llega validado por la anotacion {@code @Pattern} del
 * controlador; la lista de campos permitidos es una defensa adicional. Siempre
 * se agrega el desempate {@code id,asc} para que la paginacion sea estable.
 */
public final class Paginacion {

    private Paginacion() {
    }

    public static Pageable crear(
            int page,
            int size,
            String sort,
            Set<String> camposPermitidos) {

        String orden = sort == null || sort.isBlank() ? "id,asc" : sort.strip();
        String[] partes = orden.split(",", -1);

        if (partes.length != 2) {
            throw new IllegalArgumentException(
                    "El formato del orden debe ser campo,asc|desc");
        }

        String campo = partes[0].strip();
        String direccion = partes[1].strip().toLowerCase(Locale.ROOT);

        if (!camposPermitidos.contains(campo)) {
            throw new IllegalArgumentException("Campo de orden no permitido");
        }

        Sort principal = switch (direccion) {
            case "asc" -> Sort.by(Sort.Direction.ASC, campo);
            case "desc" -> Sort.by(Sort.Direction.DESC, campo);
            default -> throw new IllegalArgumentException(
                    "La direccion del orden debe ser asc o desc");
        };

        return PageRequest.of(
                page,
                size,
                principal.and(Sort.by(Sort.Direction.ASC, "id")));
    }
}
