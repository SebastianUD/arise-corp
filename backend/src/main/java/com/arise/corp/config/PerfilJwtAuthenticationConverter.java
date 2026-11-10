package com.arise.corp.config;

import java.util.List;
import java.util.Optional;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.service.PerfilUsuarioService;

/**
 * Convierte el JWT validado en una autenticacion con el rol empresarial del
 * perfil activo.
 *
 * <p>Las authorities se derivan exclusivamente de {@code app.perfiles_usuario},
 * nunca del claim tecnico {@code role} de Supabase (DP-SEG-01). Un JWT valido
 * sin perfil activo queda autenticado pero sin authorities: las rutas que
 * exigen rol responden 403 y {@code /api/me} lo rechaza en el servicio.
 */
@Component
public class PerfilJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String PREFIJO_ROL = "ROLE_";

    private final PerfilUsuarioService perfilUsuarioService;

    public PerfilJwtAuthenticationConverter(
            PerfilUsuarioService perfilUsuarioService) {

        this.perfilUsuarioService = perfilUsuarioService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Optional<PerfilUsuario> perfil =
                perfilUsuarioService.buscarPerfilActivo(jwt.getSubject());

        List<GrantedAuthority> authorities = perfil
                .<List<GrantedAuthority>>map(activo -> List.of(
                        new SimpleGrantedAuthority(
                                PREFIJO_ROL + activo.getRol().name())))
                .orElseGet(List::of);

        return new JwtAuthenticationToken(jwt, authorities);
    }
}
