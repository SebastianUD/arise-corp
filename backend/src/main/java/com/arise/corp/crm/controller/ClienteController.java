package com.arise.corp.crm.controller;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

import com.arise.corp.crm.dto.ClienteRequest;
import com.arise.corp.crm.dto.ClienteResponse;
import com.arise.corp.crm.dto.ClienteVista;
import com.arise.corp.crm.dto.EstadoClienteRequest;
import com.arise.corp.crm.service.ClienteService;
import com.arise.corp.crm.service.FiltroClientes;
import com.arise.corp.crm.service.VistaCliente;
import com.arise.corp.shared.paginacion.Paginacion;
import com.arise.corp.shared.paginacion.PaginaResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * API de clientes del CRM (CRM-01, Requirements 1.2, 1.3, 4, 5, 7 y 8).
 *
 * <p>Las restricciones de los parametros de lista se declaran en los
 * {@code @RequestParam}; Spring MVC aplica la validacion de metodos integrada
 * (D8) y no se usa {@code @Validated} a nivel de clase. El actor sale del
 * {@code sub} del JWT y la vista de cada lectura se decide con
 * {@link VistaClienteResolver}. No hay {@code DELETE}: un borrado responde 405.
 */
@RestController
@RequestMapping("/api/crm/clientes")
public class ClienteController {

    private static final Set<String> CAMPOS_ORDEN =
            Set.of("nombre", "numeroDocumento", "creadoEn");

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public PaginaResponse<ClienteVista> listar(
            @RequestParam(required = false)
            @Size(max = 100, message = "debe tener como máximo 100 caracteres")
            String q,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) UUID cuentaCorporativaId,
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
            String sort,
            Authentication authentication) {

        VistaCliente vista = VistaClienteResolver.desde(authentication);
        FiltroClientes filtro = new FiltroClientes(q, activo, cuentaCorporativaId);

        return clienteService.listar(
                filtro,
                Paginacion.crear(page, size, sort, CAMPOS_ORDEN),
                vista);
    }

    @GetMapping("/{id}")
    public ClienteVista obtener(
            @PathVariable UUID id,
            Authentication authentication) {

        return clienteService.obtener(id, VistaClienteResolver.desde(authentication));
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> crear(
            @Valid @RequestBody ClienteRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        ClienteResponse creado = clienteService.crear(request, jwt.getSubject());
        URI location = URI.create("/api/crm/clientes/" + creado.id());

        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    public ClienteResponse actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody ClienteRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return clienteService.actualizar(id, request, jwt.getSubject());
    }

    @PatchMapping("/{id}")
    public ClienteResponse cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody EstadoClienteRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return clienteService.cambiarEstado(id, request.activo(), jwt.getSubject());
    }
}
