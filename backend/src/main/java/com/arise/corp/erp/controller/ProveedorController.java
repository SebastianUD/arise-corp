package com.arise.corp.erp.controller;

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

import com.arise.corp.erp.dto.EstadoActivoRequest;
import com.arise.corp.erp.dto.ProveedorRequest;
import com.arise.corp.erp.dto.ProveedorResponse;
import com.arise.corp.erp.service.FiltroProveedores;
import com.arise.corp.erp.service.ProveedorService;
import com.arise.corp.shared.paginacion.Paginacion;
import com.arise.corp.shared.paginacion.PaginaResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * API de proveedores del ERP (ERP-02, Requirements 1.2, 1.3, 9, 10, 11 y 12).
 *
 * <p>Las restricciones de los parametros de lista se declaran en los
 * {@code @RequestParam}. El actor sale del {@code sub} del JWT y la
 * representacion es unica para los dos roles gestores. No hay {@code DELETE}:
 * un borrado responde 405.
 */
@RestController
@RequestMapping("/api/erp/proveedores")
public class ProveedorController {

    private static final Set<String> CAMPOS_ORDEN =
            Set.of("nombre", "numeroDocumento", "creadoEn");

    private final ProveedorService proveedorService;

    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    @GetMapping
    public PaginaResponse<ProveedorResponse> listar(
            @RequestParam(required = false)
            @Size(max = 100, message = "debe tener como máximo 100 caracteres")
            String q,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "debe ser mayor o igual a 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "debe estar entre 1 y 100")
            @Max(value = 100, message = "debe estar entre 1 y 100")
            int size,
            @RequestParam(defaultValue = "nombre,asc")
            @Pattern(
                    regexp = "(nombre|numeroDocumento|creadoEn),(asc|desc)",
                    message = "debe usar nombre, numeroDocumento o creadoEn con asc o desc")
            String sort) {

        return proveedorService.listar(
                new FiltroProveedores(q, activo),
                Paginacion.crear(page, size, sort, CAMPOS_ORDEN));
    }

    @GetMapping("/{id}")
    public ProveedorResponse obtener(@PathVariable UUID id) {
        return proveedorService.obtener(id);
    }

    @PostMapping
    public ResponseEntity<ProveedorResponse> crear(
            @Valid @RequestBody ProveedorRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        ProveedorResponse creado = proveedorService.crear(
                request, jwt.getSubject());

        return ResponseEntity.created(
                URI.create("/api/erp/proveedores/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    public ProveedorResponse actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody ProveedorRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return proveedorService.actualizar(id, request, jwt.getSubject());
    }

    @PatchMapping("/{id}")
    public ProveedorResponse cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody EstadoActivoRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return proveedorService.cambiarEstado(
                id, request.activo(), jwt.getSubject());
    }
}
