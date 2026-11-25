package com.arise.corp.sga.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arise.corp.sga.dto.ArticuloRequest;
import com.arise.corp.sga.dto.ArticuloResponse;
import com.arise.corp.sga.entity.ArticuloInventario;
import com.arise.corp.sga.repository.ArticuloInventarioRepository;
import com.arise.corp.sga.repository.EspecificacionesSga;
import com.arise.corp.shared.error.RecursoNoEncontradoException;
import com.arise.corp.shared.paginacion.PaginaResponse;

/**
 * Servicio del catalogo de inventario (SGA-03, Requirements 13 y 14).
 *
 * <p>Las lecturas son transacciones de solo lectura y las escrituras usan
 * {@code @Transactional}. Solo {@link MovimientoInventarioService} cambia la
 * existencia (Requirement 15.12): aqui {@code actualizar} no la recibe. El
 * actor es el {@code sub} del perfil activo y las marcas temporales salen del
 * {@link Clock}, truncadas a microsegundos (D14).
 */
@Service
@Transactional(readOnly = true)
public class ArticuloService {

    private static final String ARTICULO_NO_ENCONTRADO = "El artículo no existe.";

    private final ArticuloInventarioRepository articulos;
    private final Clock clock;

    public ArticuloService(ArticuloInventarioRepository articulos, Clock clock) {
        this.articulos = articulos;
        this.clock = clock;
    }

    /**
     * Lista los articulos que cumplen todos los filtros.
     */
    public PaginaResponse<ArticuloResponse> listar(
            FiltroArticulos filtro,
            Pageable pageable) {

        Specification<ArticuloInventario> especificacion = Specification.allOf(
                EspecificacionesSga.codigoOTextoContiene(filtro.q(), "nombre"),
                EspecificacionesSga.categoriaIgual(filtro.categoria()),
                EspecificacionesSga.bajoStockMinimo(filtro.bajoStockMinimo()));

        return PaginaResponse.desde(articulos.findAll(especificacion, pageable)
                .map(ArticuloResponse::desde));
    }

    /**
     * Obtiene un articulo por su identificador.
     *
     * @throws RecursoNoEncontradoException si el articulo no existe
     */
    public ArticuloResponse obtener(UUID id) {
        return ArticuloResponse.desde(articulos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        ARTICULO_NO_ENCONTRADO)));
    }

    /**
     * Crea un articulo con existencia 0 y la auditoria del actor.
     *
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si el
     *         codigo ya esta registrado
     */
    @Transactional
    public ArticuloResponse crear(ArticuloRequest solicitud, String actorSub) {
        if (articulos.findByCodigo(solicitud.codigo()).isPresent()) {
            throw ConflictosSga.articuloCodigoDuplicado();
        }

        ArticuloInventario articulo = articulos.save(ArticuloInventario.crear(
                solicitud.codigo(),
                solicitud.nombre(),
                solicitud.categoria(),
                solicitud.unidad(),
                solicitud.stockMinimo().intValueExact(),
                UUID.fromString(actorSub),
                ahora()));

        return ArticuloResponse.desde(articulo);
    }

    /**
     * Reemplaza los datos del articulo y conserva su existencia y su fecha de
     * creacion (Requirement 13.9).
     *
     * @throws RecursoNoEncontradoException si el articulo no existe
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si el
     *         codigo pertenece a otro articulo
     */
    @Transactional
    public ArticuloResponse actualizar(
            UUID id,
            ArticuloRequest solicitud,
            String actorSub) {

        ArticuloInventario articulo = articulos.findParaModificarById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        ARTICULO_NO_ENCONTRADO));

        articulos.findByCodigo(solicitud.codigo())
                .filter(otro -> !otro.getId().equals(articulo.getId()))
                .ifPresent(otro -> {
                    throw ConflictosSga.articuloCodigoDuplicado();
                });

        articulo.actualizarDatos(
                solicitud.codigo(),
                solicitud.nombre(),
                solicitud.categoria(),
                solicitud.unidad(),
                solicitud.stockMinimo().intValueExact(),
                UUID.fromString(actorSub),
                ahora());

        return ArticuloResponse.desde(articulo);
    }

    private Instant ahora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
