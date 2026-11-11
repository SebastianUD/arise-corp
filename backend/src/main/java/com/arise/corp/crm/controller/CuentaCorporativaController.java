package com.arise.corp.crm.controller;

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

import com.arise.corp.crm.dto.CuentaCorporativaRequest;
import com.arise.corp.crm.dto.CuentaCorporativaResponse;
import com.arise.corp.crm.dto.EstadoCuentaRequest;
import com.arise.corp.crm.service.CuentaCorporativaService;
import com.arise.corp.crm.service.FiltroCuentas;
import com.arise.corp.shared.paginacion.Paginacion;
import com.arise.corp.shared.paginacion.PaginaResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * API de cuentas corporativas del CRM (CRM-02, Requirements 1.2, 1.3 y 9).
 *
 * <p>Las restricciones de los parametros de lista se declaran en los
 * {@code @RequestParam} sin {@code @Validated} a nivel de clase (D8). El actor
 * sale del {@code sub} del JWT. No hay {@code DELETE}: un borrado responde 405.
 */
@RestController
@RequestMapping("/api/crm/cuentas-corporativas")
public class CuentaCorporativaController {

    private static final Set<String> CAMPOS_ORDEN =
            Set.of("razonSocial", "nit", "creadoEn");

    private final CuentaCorporativaService cuentaService;

    public CuentaCorporativaController(CuentaCorporativaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    @GetMapping
    public PaginaResponse<CuentaCorporativaResponse> listar(
            @RequestParam(required = false)
            @Size(max = 100, message = "debe tener como máximo 100 caracteres")
            String q,
            @RequestParam(required = false) Boolean activa,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "debe ser mayor o igual a 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "debe estar entre 1 y 100")
            @Max(value = 100, message = "debe estar entre 1 y 100")
            int size,
            @RequestParam(defaultValue = "razonSocial,asc")
            @Pattern(
                    regexp = "(razonSocial|nit|creadoEn),(asc|desc)",
                    message = "debe usar razonSocial, nit o creadoEn con asc o desc")
            String sort) {

        FiltroCuentas filtro = new FiltroCuentas(q, activa);

        return cuentaService.listar(
                filtro, Paginacion.crear(page, size, sort, CAMPOS_ORDEN));
    }

    @GetMapping("/{id}")
    public CuentaCorporativaResponse obtener(@PathVariable UUID id) {
        return cuentaService.obtener(id);
    }

    @PostMapping
    public ResponseEntity<CuentaCorporativaResponse> crear(
            @Valid @RequestBody CuentaCorporativaRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        CuentaCorporativaResponse creada =
                cuentaService.crear(request, jwt.getSubject());
        URI location = URI.create("/api/crm/cuentas-corporativas/" + creada.id());

        return ResponseEntity.created(location).body(creada);
    }

    @PutMapping("/{id}")
    public CuentaCorporativaResponse actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody CuentaCorporativaRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return cuentaService.actualizar(id, request, jwt.getSubject());
    }

    @PatchMapping("/{id}")
    public CuentaCorporativaResponse cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody EstadoCuentaRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return cuentaService.cambiarEstado(id, request.activa(), jwt.getSubject());
    }
}
