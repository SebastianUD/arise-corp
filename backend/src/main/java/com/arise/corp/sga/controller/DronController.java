package com.arise.corp.sga.controller;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

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

import com.arise.corp.sga.dto.DronRequest;
import com.arise.corp.sga.dto.DronResponse;
import com.arise.corp.sga.dto.EstadoDronRequest;
import com.arise.corp.sga.entity.EstadoDron;
import com.arise.corp.sga.service.DronService;
import com.arise.corp.sga.service.FiltroDrones;
import com.arise.corp.shared.paginacion.Paginacion;
import com.arise.corp.shared.paginacion.PaginaResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * API de drones del SGA (SGA-01, Requirements 1.2, 1.3, 4, 5, 6 y 18.6).
 *
 * <p>Las restricciones de los parametros de lista se declaran en los
 * {@code @RequestParam} sin {@code @Validated} a nivel de clase. El actor sale
 * del {@code sub} del JWT y las {@code Location} son relativas. No hay
 * {@code DELETE}: un borrado responde 405 con {@code Allow}.
 */
@RestController
@RequestMapping("/api/sga/drones")
public class DronController {

    private static final Set<String> CAMPOS_ORDEN =
            Set.of("codigo", "modelo", "creadoEn");

    private final DronService dronService;

    public DronController(DronService dronService) {
        this.dronService = dronService;
    }

    @GetMapping
    public PaginaResponse<DronResponse> listar(
            @RequestParam(required = false)
            @Size(max = 100, message = "debe tener como máximo 100 caracteres")
            String q,
            @RequestParam(required = false) EstadoDron estado,
            @RequestParam(required = false) Boolean asignable,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "debe ser mayor o igual a 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "debe estar entre 1 y 100")
            @Max(value = 100, message = "debe estar entre 1 y 100")
            int size,
            @RequestParam(defaultValue = "codigo,asc")
            @Pattern(
                    regexp = "(codigo|modelo|creadoEn),(asc|desc)",
                    message = "debe usar codigo, modelo o creadoEn con asc o desc")
            String sort) {

        return dronService.listar(
                new FiltroDrones(q, estado, asignable),
                Paginacion.crear(page, size, sort, CAMPOS_ORDEN));
    }

    @GetMapping("/{id}")
    public DronResponse obtener(@PathVariable UUID id) {
        return dronService.obtener(id);
    }

    @PostMapping
    public ResponseEntity<DronResponse> crear(
            @Valid @RequestBody DronRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        DronResponse creado = dronService.crear(request, jwt.getSubject());

        return ResponseEntity.created(
                URI.create("/api/sga/drones/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    public DronResponse actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody DronRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return dronService.actualizar(id, request, jwt.getSubject());
    }

    @PatchMapping("/{id}/estado")
    public DronResponse cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody EstadoDronRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return dronService.cambiarEstado(
                id, request.estado(), jwt.getSubject());
    }
}
