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

import com.arise.corp.erp.dto.EmpleadoRequest;
import com.arise.corp.erp.dto.EmpleadoResponse;
import com.arise.corp.erp.dto.EstadoActivoRequest;
import com.arise.corp.erp.service.EmpleadoService;
import com.arise.corp.erp.service.FiltroEmpleados;
import com.arise.corp.shared.paginacion.Paginacion;
import com.arise.corp.shared.paginacion.PaginaResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * API de empleados del ERP (ERP-01, Requirements 1.2, 1.3, 4, 5, 6 y 7).
 *
 * <p>Las restricciones de los parametros de lista se declaran en los
 * {@code @RequestParam}; Spring MVC aplica la validacion de metodos integrada y
 * no se usa {@code @Validated} a nivel de clase. El actor sale del {@code sub}
 * del JWT y la representacion es unica para los dos roles gestores. No hay
 * {@code DELETE}: un borrado responde 405.
 */
@RestController
@RequestMapping("/api/erp/empleados")
public class EmpleadoController {

    private static final Set<String> CAMPOS_ORDEN =
            Set.of("nombre", "numeroDocumento", "fechaIngreso", "creadoEn");

    private final EmpleadoService empleadoService;

    public EmpleadoController(EmpleadoService empleadoService) {
        this.empleadoService = empleadoService;
    }

    @GetMapping
    public PaginaResponse<EmpleadoResponse> listar(
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
                    regexp = "(nombre|numeroDocumento|fechaIngreso|creadoEn),(asc|desc)",
                    message = "debe usar nombre, numeroDocumento, fechaIngreso o creadoEn con asc o desc")
            String sort) {

        return empleadoService.listar(
                new FiltroEmpleados(q, activo),
                Paginacion.crear(page, size, sort, CAMPOS_ORDEN));
    }

    @GetMapping("/{id}")
    public EmpleadoResponse obtener(@PathVariable UUID id) {
        return empleadoService.obtener(id);
    }

    @PostMapping
    public ResponseEntity<EmpleadoResponse> crear(
            @Valid @RequestBody EmpleadoRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        EmpleadoResponse creado = empleadoService.crear(
                request, jwt.getSubject());

        return ResponseEntity.created(
                URI.create("/api/erp/empleados/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    public EmpleadoResponse actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody EmpleadoRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return empleadoService.actualizar(id, request, jwt.getSubject());
    }

    @PatchMapping("/{id}")
    public EmpleadoResponse cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody EstadoActivoRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return empleadoService.cambiarEstado(
                id, request.activo(), jwt.getSubject());
    }
}
