package com.arise.corp.erp.controller;

import java.net.URI;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.arise.corp.erp.dto.EstadoMovimientoRequest;
import com.arise.corp.erp.dto.MovimientoRequest;
import com.arise.corp.erp.dto.MovimientoResponse;
import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.TipoMovimiento;
import com.arise.corp.erp.service.FiltroMovimientos;
import com.arise.corp.erp.service.MovimientoService;
import com.arise.corp.erp.service.RangoFechas;
import com.arise.corp.shared.paginacion.Paginacion;
import com.arise.corp.shared.paginacion.PaginaResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * API de movimientos financieros del ERP (ERP-03, ERP-05, Requirements 13, 14,
 * 15, 16 y 17).
 *
 * <p>Las restricciones de los parametros de lista se declaran en los
 * {@code @RequestParam} y Spring MVC aplica la validacion de metodos
 * integrada. El rango de fechas lo valida {@link RangoFechas} al construirse y
 * el actor sale del {@code sub} del JWT. No hay {@code DELETE}: un borrado
 * responde 405.
 */
@RestController
@RequestMapping("/api/erp/movimientos")
public class MovimientoController {

    private static final Set<String> CAMPOS_ORDEN =
            Set.of("fecha", "valor", "creadoEn");

    private final MovimientoService movimientoService;

    public MovimientoController(MovimientoService movimientoService) {
        this.movimientoService = movimientoService;
    }

    @GetMapping
    public PaginaResponse<MovimientoResponse> listar(
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) EstadoMovimiento estado,
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) UUID proveedorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false)
            @Size(max = 100, message = "debe tener como máximo 100 caracteres")
            String q,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "debe ser mayor o igual a 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "debe estar entre 1 y 100")
            @Max(value = 100, message = "debe estar entre 1 y 100")
            int size,
            @RequestParam(defaultValue = "fecha,desc")
            @Pattern(
                    regexp = "(fecha|valor|creadoEn),(asc|desc)",
                    message = "debe usar fecha, valor o creadoEn con asc o desc")
            String sort) {

        FiltroMovimientos filtro = new FiltroMovimientos(
                tipo, estado, clienteId, proveedorId,
                new RangoFechas(desde, hasta), q);

        return movimientoService.listar(
                filtro, Paginacion.crear(page, size, sort, CAMPOS_ORDEN));
    }

    @GetMapping("/{id}")
    public MovimientoResponse obtener(@PathVariable UUID id) {
        return movimientoService.obtener(id);
    }

    @PostMapping
    public ResponseEntity<MovimientoResponse> crear(
            @Valid @RequestBody MovimientoRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        MovimientoResponse creado = movimientoService.crear(
                request, jwt.getSubject());

        return ResponseEntity.created(
                URI.create("/api/erp/movimientos/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    public MovimientoResponse actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody MovimientoRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return movimientoService.actualizar(id, request, jwt.getSubject());
    }

    @PatchMapping("/{id}/estado")
    public MovimientoResponse cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody EstadoMovimientoRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return movimientoService.cambiarEstado(
                id, request.estado(), jwt.getSubject());
    }
}
