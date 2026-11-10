package com.arise.corp.login.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arise.corp.login.dto.MeResponse;
import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.service.PerfilUsuarioService;

/**
 * Endpoint protegido de identidad del usuario autenticado.
 *
 * <p>El rol devuelto proviene del perfil empresarial activo, no del claim
 * tecnico {@code role} del JWT (AUTH-04).
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final PerfilUsuarioService perfilUsuarioService;

    public AuthController(PerfilUsuarioService perfilUsuarioService) {
        this.perfilUsuarioService = perfilUsuarioService;
    }

    @GetMapping("/me")
    public MeResponse getCurrentUser(
            @AuthenticationPrincipal Jwt jwt) {

        String sub = jwt.getSubject();
        PerfilUsuario perfil = perfilUsuarioService.obtenerPerfilActivo(sub);

        return new MeResponse(
                sub,
                jwt.getClaimAsString("email"),
                perfil.getRol().name());
    }
}
