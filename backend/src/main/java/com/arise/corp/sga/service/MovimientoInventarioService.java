package com.arise.corp.sga.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arise.corp.sga.dto.MovimientoInventarioRequest;
import com.arise.corp.sga.dto.MovimientoInventarioResponse;
import com.arise.corp.sga.entity.ArticuloInventario;
import com.arise.corp.sga.entity.MovimientoInventario;
import com.arise.corp.sga.repository.ArticuloInventarioRepository;
import com.arise.corp.sga.repository.MovimientoInventarioRepository;
import com.arise.corp.shared.error.RecursoNoEncontradoException;
import com.arise.corp.shared.paginacion.PaginaResponse;

/**
 * Servicio de movimientos de inventario (SGA-03, Requirement 15).
 *
 * <p>Toda entrada, salida o ajuste se aplica bajo bloqueo de escritura del
 * articulo y registra el movimiento en la misma transaccion (D10, D12). Un
 * resultado negativo se rechaza antes de tocar el articulo. Las marcas
 * temporales salen del {@link Clock}, truncadas a microsegundos (D14), y el
 * movimiento no toca la auditoria de datos del articulo.
 */
@Service
@Transactional(readOnly = true)
public class MovimientoInventarioService {

    private static final String ARTICULO_NO_ENCONTRADO = "El artículo no existe.";

    private final ArticuloInventarioRepository articulos;
    private final MovimientoInventarioRepository movimientos;
    private final Clock clock;

    public MovimientoInventarioService(
            ArticuloInventarioRepository articulos,
            MovimientoInventarioRepository movimientos,
            Clock clock) {

        this.articulos = articulos;
        this.movimientos = movimientos;
        this.clock = clock;
    }

    /**
     * Registra un movimiento en el orden de D12: articulo con bloqueo,
     * aritmetica del tipo, rechazo de un resultado negativo y aplicacion sobre
     * la entidad.
     *
     * @throws RecursoNoEncontradoException si el articulo no existe
     * @throws com.arise.corp.shared.error.ConflictoNegocioException si la
     *         salida supera la existencia disponible
     */
    @Transactional
    public MovimientoInventarioResponse registrar(
            UUID articuloId,
            MovimientoInventarioRequest solicitud,
            String actorSub) {

        ArticuloInventario articulo = articulos.findParaModificarById(articuloId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        ARTICULO_NO_ENCONTRADO));

        int cantidad = solicitud.cantidadEntera();
        long anterior = articulo.getExistencia();
        long resultante = solicitud.tipo().existenciaResultante(anterior, cantidad);

        if (resultante < 0) {
            throw ConflictosSga.existenciaInsuficiente(anterior, cantidad);
        }

        articulo.aplicarMovimiento(solicitud.tipo(), cantidad);

        MovimientoInventario movimiento = movimientos.save(
                MovimientoInventario.registrar(
                        articulo,
                        solicitud.tipo(),
                        cantidad,
                        anterior,
                        resultante,
                        solicitud.observacion(),
                        UUID.fromString(actorSub),
                        ahora()));

        return MovimientoInventarioResponse.desde(movimiento);
    }

    /**
     * Lista los movimientos de un articulo.
     *
     * @throws RecursoNoEncontradoException si el articulo no existe
     */
    public PaginaResponse<MovimientoInventarioResponse> listar(
            UUID articuloId,
            Pageable pageable) {

        if (!articulos.existsById(articuloId)) {
            throw new RecursoNoEncontradoException(ARTICULO_NO_ENCONTRADO);
        }

        return PaginaResponse.desde(movimientos
                .findByArticuloId(articuloId, pageable)
                .map(MovimientoInventarioResponse::desde));
    }

    private Instant ahora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
