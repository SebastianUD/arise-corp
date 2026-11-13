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

import com.arise.corp.crm.dto.ContratoActualizarRequest;
import com.arise.corp.crm.dto.ContratoCrearRequest;
import com.arise.corp.crm.dto.ContratoResponse;
import com.arise.corp.crm.dto.EstadoContratoRequest;
import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.crm.service.ContratoService;
import com.arise.corp.crm.service.FiltroContratos;
import com.arise.corp.shared.paginacion.Paginacion;
import com.arise.corp.shared.paginacion.PaginaResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * API de contratos del CRM (CRM-03, Requirements 1.2, 1.3, 11 a 14).
 *
 * <p>Las restricciones de los parametros de lista se declaran en los
 * {@code @RequestParam} sin {@code @Validated} a nivel de clase (D8). El actor
 * sale del {@code sub} del JWT. La ruta {@code /{id}/estado} queda bajo
 * {@code /api/crm/**} en {@code SecurityConfig} y solo la usan los gestores.
 */
@RestController
@RequestMapping("/api/crm/contratos")
public class ContratoController {

    private static final Set<String> CAMPOS_ORDEN =
            Set.of("codigo", "fechaInicio", "creadoEn");

    private final ContratoService contratoService;

    public ContratoController(ContratoService contratoService) {
        this.contratoService = contratoService;
    }

    @GetMapping
    public PaginaResponse<ContratoResponse> listar(
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) EstadoContrato estado,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "debe ser mayor o igual a 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "debe estar entre 1 y 100")
            @Max(value = 100, message = "debe estar entre 1 y 100")
            int size,
            @RequestParam(defaultValue = "creadoEn,desc")
            @Pattern(
                    regexp = "(codigo|fechaInicio|creadoEn),(asc|desc)",
                    message = "debe usar codigo, fechaInicio o creadoEn con asc o desc")
            String sort) {

        FiltroContratos filtro = new FiltroContratos(clienteId, estado);

        return contratoService.listar(
                filtro, Paginacion.crear(page, size, sort, CAMPOS_ORDEN));
    }

    @GetMapping("/{id}")
    public ContratoResponse obtener(@PathVariable UUID id) {
        return contratoService.obtener(id);
    }

    @PostMapping
    public ResponseEntity<ContratoResponse> crear(
            @Valid @RequestBody ContratoCrearRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        ContratoResponse creado = contratoService.crear(request, jwt.getSubject());
        URI location = URI.create("/api/crm/contratos/" + creado.id());

        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    public ContratoResponse actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody ContratoActualizarRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return contratoService.actualizar(id, request, jwt.getSubject());
    }

    @PatchMapping("/{id}/estado")
    public ContratoResponse cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody EstadoContratoRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return contratoService.cambiarEstado(id, request.estado(), jwt.getSubject());
    }
}
