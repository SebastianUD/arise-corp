package com.arise.corp.sga.controller;

import java.net.URI;
import java.util.List;
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

import com.arise.corp.sga.dto.PaqueteActualizarRequest;
import com.arise.corp.sga.dto.PaqueteCrearRequest;
import com.arise.corp.sga.dto.PaqueteResponse;
import com.arise.corp.sga.dto.RegistroHistorialResponse;
import com.arise.corp.sga.dto.TransicionPaqueteRequest;
import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.service.FiltroPaquetes;
import com.arise.corp.sga.service.PaqueteService;
import com.arise.corp.shared.paginacion.Paginacion;
import com.arise.corp.shared.paginacion.PaginaResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * API de paquetes e historial del SGA (SGA-02, SGA-04, SGA-05, Requirements 1.2,
 * 7, 8, 9, 10 y 12).
 *
 * <p>El historial se devuelve como arreglo completo, sin paginacion (DP-API-05,
 * interpretacion 12). Las restricciones de los parametros de lista se declaran
 * en los {@code @RequestParam} sin {@code @Validated} a nivel de clase. El actor
 * sale del {@code sub} del JWT y las {@code Location} son relativas. No hay
 * {@code DELETE}: un borrado responde 405 con {@code Allow}.
 */
@RestController
@RequestMapping("/api/sga/paquetes")
public class PaqueteController {

    private static final Set<String> CAMPOS_ORDEN =
            Set.of("codigo", "creadoEn", "estadoActualizadoEn");

    private final PaqueteService paqueteService;

    public PaqueteController(PaqueteService paqueteService) {
        this.paqueteService = paqueteService;
    }

    @GetMapping
    public PaginaResponse<PaqueteResponse> listar(
            @RequestParam(required = false) EstadoPaquete estado,
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) UUID contratoId,
            @RequestParam(required = false) UUID dronId,
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
            @RequestParam(defaultValue = "creadoEn,desc")
            @Pattern(
                    regexp = "(codigo|creadoEn|estadoActualizadoEn),(asc|desc)",
                    message = "debe usar codigo, creadoEn o estadoActualizadoEn con asc o desc")
            String sort) {

        return paqueteService.listar(
                new FiltroPaquetes(estado, clienteId, contratoId, dronId, q),
                Paginacion.crear(page, size, sort, CAMPOS_ORDEN));
    }

    @GetMapping("/{id}")
    public PaqueteResponse obtener(@PathVariable UUID id) {
        return paqueteService.obtener(id);
    }

    @PostMapping
    public ResponseEntity<PaqueteResponse> crear(
            @Valid @RequestBody PaqueteCrearRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        PaqueteResponse creado = paqueteService.crear(
                request, jwt.getSubject());

        return ResponseEntity.created(
                URI.create("/api/sga/paquetes/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    public PaqueteResponse actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody PaqueteActualizarRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return paqueteService.actualizar(id, request, jwt.getSubject());
    }

    @GetMapping("/{id}/estados")
    public List<RegistroHistorialResponse> historial(@PathVariable UUID id) {
        return paqueteService.historial(id);
    }

    @PostMapping("/{id}/estados")
    public ResponseEntity<RegistroHistorialResponse> cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody TransicionPaqueteRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        RegistroHistorialResponse registro = paqueteService.cambiarEstado(
                id, request, jwt.getSubject());

        return ResponseEntity.created(
                URI.create("/api/sga/paquetes/" + id + "/estados"))
                .body(registro);
    }
}
