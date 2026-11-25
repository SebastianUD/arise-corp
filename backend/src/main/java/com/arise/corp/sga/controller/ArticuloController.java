package com.arise.corp.sga.controller;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.arise.corp.sga.dto.ArticuloRequest;
import com.arise.corp.sga.dto.ArticuloResponse;
import com.arise.corp.sga.dto.MovimientoInventarioRequest;
import com.arise.corp.sga.dto.MovimientoInventarioResponse;
import com.arise.corp.sga.entity.CategoriaArticulo;
import com.arise.corp.sga.service.ArticuloService;
import com.arise.corp.sga.service.FiltroArticulos;
import com.arise.corp.sga.service.MovimientoInventarioService;
import com.arise.corp.shared.paginacion.Paginacion;
import com.arise.corp.shared.paginacion.PaginaResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * API de articulos e inventario del SGA (SGA-03, Requirements 1.2, 13, 14 y
 * 15).
 *
 * <p>Las restricciones de los parametros de lista se declaran en los
 * {@code @RequestParam} sin {@code @Validated} a nivel de clase. El actor sale
 * del {@code sub} del JWT y las {@code Location} son relativas. No hay
 * {@code DELETE}: un borrado responde 405 con {@code Allow}.
 */
@RestController
@RequestMapping("/api/sga/articulos")
public class ArticuloController {

    private static final Set<String> CAMPOS_ORDEN =
            Set.of("codigo", "nombre", "existencia", "creadoEn");
    private static final Set<String> CAMPOS_ORDEN_MOVIMIENTOS =
            Set.of("registradoEn");

    private final ArticuloService articuloService;
    private final MovimientoInventarioService movimientoService;

    public ArticuloController(
            ArticuloService articuloService,
            MovimientoInventarioService movimientoService) {

        this.articuloService = articuloService;
        this.movimientoService = movimientoService;
    }

    @GetMapping
    public PaginaResponse<ArticuloResponse> listar(
            @RequestParam(required = false)
            @Size(max = 100, message = "debe tener como máximo 100 caracteres")
            String q,
            @RequestParam(required = false) CategoriaArticulo categoria,
            @RequestParam(required = false) Boolean bajoStockMinimo,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "debe ser mayor o igual a 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "debe estar entre 1 y 100")
            @Max(value = 100, message = "debe estar entre 1 y 100")
            int size,
            @RequestParam(defaultValue = "nombre,asc")
            @Pattern(
                    regexp = "(codigo|nombre|existencia|creadoEn),(asc|desc)",
                    message = "debe usar codigo, nombre, existencia o creadoEn con asc o desc")
            String sort) {

        return articuloService.listar(
                new FiltroArticulos(q, categoria, bajoStockMinimo),
                Paginacion.crear(page, size, sort, CAMPOS_ORDEN));
    }

    @GetMapping("/{id}")
    public ArticuloResponse obtener(@PathVariable UUID id) {
        return articuloService.obtener(id);
    }

    @PostMapping
    public ResponseEntity<ArticuloResponse> crear(
            @Valid @RequestBody ArticuloRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        ArticuloResponse creado = articuloService.crear(
                request, jwt.getSubject());

        return ResponseEntity.created(
                URI.create("/api/sga/articulos/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    public ArticuloResponse actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody ArticuloRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return articuloService.actualizar(id, request, jwt.getSubject());
    }

    @GetMapping("/{id}/movimientos")
    public PaginaResponse<MovimientoInventarioResponse> listarMovimientos(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "debe ser mayor o igual a 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "debe estar entre 1 y 100")
            @Max(value = 100, message = "debe estar entre 1 y 100")
            int size,
            @RequestParam(defaultValue = "registradoEn,desc")
            @Pattern(
                    regexp = "registradoEn,(asc|desc)",
                    message = "debe usar registradoEn con asc o desc")
            String sort) {

        return movimientoService.listar(
                id,
                Paginacion.crear(
                        page, size, sort, CAMPOS_ORDEN_MOVIMIENTOS));
    }

    @PostMapping("/{id}/movimientos")
    public ResponseEntity<MovimientoInventarioResponse> registrarMovimiento(
            @PathVariable UUID id,
            @Valid @RequestBody MovimientoInventarioRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        MovimientoInventarioResponse creado = movimientoService.registrar(
                id, request, jwt.getSubject());

        return ResponseEntity.created(
                URI.create("/api/sga/articulos/" + id + "/movimientos"))
                .body(creado);
    }
}
