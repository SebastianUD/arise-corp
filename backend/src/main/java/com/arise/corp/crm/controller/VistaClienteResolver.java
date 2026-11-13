package com.arise.corp.crm.controller;

import java.util.Objects;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import com.arise.corp.config.PerfilJwtAuthenticationConverter;
import com.arise.corp.crm.service.VistaCliente;
import com.arise.corp.login.entity.RolUsuario;

/**
 * Elige la vista del cliente a partir del rol del Perfil_Activo (D6,
 * Requirements 6.1 a 6.3).
 *
 * <p>Solo las authorities construidas con
 * {@link PerfilJwtAuthenticationConverter#PREFIJO_ROL} y uno de los roles
 * gestores devuelven {@link VistaCliente#COMPLETA}. Cualquier otro caso,
 * incluida una autenticacion ausente o sin authorities, devuelve
 * {@link VistaCliente#REDUCIDA}: un error de configuracion nunca expone datos
 * personales. No lee parametros de la peticion.
 */
public final class VistaClienteResolver {

    private static final Set<String> ROLES_VISTA_COMPLETA = Set.of(
            PerfilJwtAuthenticationConverter.PREFIJO_ROL
                    + RolUsuario.ADMINISTRADOR.name(),
            PerfilJwtAuthenticationConverter.PREFIJO_ROL
                    + RolUsuario.COMERCIAL.name());

    private VistaClienteResolver() {
    }

    public static VistaCliente desde(Authentication authentication) {
        if (authentication == null) {
            return VistaCliente.REDUCIDA;
        }

        boolean gestorCrm = authentication.getAuthorities().stream()
                .filter(Objects::nonNull)
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLES_VISTA_COMPLETA::contains);

        return gestorCrm ? VistaCliente.COMPLETA : VistaCliente.REDUCIDA;
    }
}
