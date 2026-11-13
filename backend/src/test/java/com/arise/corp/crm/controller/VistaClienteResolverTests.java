package com.arise.corp.crm.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.arise.corp.config.PerfilJwtAuthenticationConverter;
import com.arise.corp.crm.service.VistaCliente;
import com.arise.corp.login.entity.RolUsuario;

/**
 * Property 10: la vista del cliente depende solo del rol (Requirements 6.1 a
 * 6.3, 10.6).
 */
class VistaClienteResolverTests {

    @ParameterizedTest
    @EnumSource(RolUsuario.class)
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 10: La vista del cliente depende solo del rol")
    void eligeLaVistaSegunElRol(RolUsuario rol) {
        VistaCliente esperada = switch (rol) {
            case ADMINISTRADOR, COMERCIAL -> VistaCliente.COMPLETA;
            case FINANZAS_RRHH, OPERACIONES -> VistaCliente.REDUCIDA;
        };

        assertEquals(esperada, VistaClienteResolver.desde(
                autenticacion(authority(rol))));
    }

    @Test
    void sinAuthoritiesDevuelveVistaReducida() {
        assertEquals(VistaCliente.REDUCIDA,
                VistaClienteResolver.desde(autenticacion()));
    }

    @Test
    void sinAutenticacionDevuelveVistaReducida() {
        assertEquals(VistaCliente.REDUCIDA,
                VistaClienteResolver.desde(null));
    }

    @Test
    void unaAuthoritySinElPrefijoDeRolNoOtorgaVistaCompleta() {
        assertEquals(VistaCliente.REDUCIDA, VistaClienteResolver.desde(
                autenticacion(new SimpleGrantedAuthority("ADMINISTRADOR"))));
        assertEquals(VistaCliente.REDUCIDA, VistaClienteResolver.desde(
                autenticacion(new SimpleGrantedAuthority("ROLE_OTRO"))));
        assertEquals(VistaCliente.REDUCIDA, VistaClienteResolver.desde(
                autenticacion(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 10: La vista del cliente depende solo del rol")
    void conVariasAuthoritiesBastaUnRolGestorParaLaVistaCompleta() {
        assertEquals(VistaCliente.COMPLETA, VistaClienteResolver.desde(
                autenticacion(
                        authority(RolUsuario.ADMINISTRADOR),
                        authority(RolUsuario.FINANZAS_RRHH))));

        assertEquals(VistaCliente.COMPLETA, VistaClienteResolver.desde(
                autenticacion(
                        authority(RolUsuario.COMERCIAL),
                        authority(RolUsuario.OPERACIONES))));

        assertEquals(VistaCliente.REDUCIDA, VistaClienteResolver.desde(
                autenticacion(
                        authority(RolUsuario.FINANZAS_RRHH),
                        authority(RolUsuario.OPERACIONES))));
    }

    private static SimpleGrantedAuthority authority(RolUsuario rol) {
        return new SimpleGrantedAuthority(
                PerfilJwtAuthenticationConverter.PREFIJO_ROL + rol.name());
    }

    private static Authentication autenticacion(GrantedAuthority... authorities) {
        return new UsernamePasswordAuthenticationToken(
                "actor@arise.test", "n/a", List.of(authorities));
    }
}
