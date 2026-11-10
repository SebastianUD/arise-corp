package com.arise.corp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.login.service.PerfilUsuarioService;

class PerfilJwtAuthenticationConverterTests {

    private static final UUID USUARIO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000030");

    private PerfilUsuarioService perfilUsuarioService;
    private PerfilJwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        perfilUsuarioService = mock(PerfilUsuarioService.class);
        converter = new PerfilJwtAuthenticationConverter(perfilUsuarioService);
    }

    @Test
    void asignaLaAuthorityDelRolEmpresarialDelPerfilActivo() {
        Jwt jwt = crearJwt("authenticated");
        when(perfilUsuarioService.buscarPerfilActivo(USUARIO_ID.toString()))
                .thenReturn(Optional.of(crearPerfil(RolUsuario.COMERCIAL)));

        JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt);

        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertEquals(List.of("ROLE_COMERCIAL"), authorities);
        assertSame(jwt, authentication.getPrincipal());
        assertTrue(authentication.isAuthenticated());
    }

    @Test
    void noAsignaAuthoritiesSinPerfilActivo() {
        Jwt jwt = crearJwt("authenticated");
        when(perfilUsuarioService.buscarPerfilActivo(USUARIO_ID.toString()))
                .thenReturn(Optional.empty());

        JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt);

        assertTrue(authentication.getAuthorities().isEmpty());
        assertTrue(authentication.isAuthenticated());
    }

    private Jwt crearJwt(String rolTecnico) {
        Instant ahora = Instant.now();

        return Jwt.withTokenValue("token-simulado")
                .header("alg", "ES256")
                .subject(USUARIO_ID.toString())
                .claim("email", "usuario@arise.test")
                .claim("role", rolTecnico)
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(300))
                .build();
    }

    private PerfilUsuario crearPerfil(RolUsuario rol) {
        Instant ahora = Instant.now();

        return new PerfilUsuario(
                USUARIO_ID, rol, true, ahora, ahora, null);
    }
}
