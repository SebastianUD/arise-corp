package com.arise.corp.login.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.login.repository.PerfilUsuarioRepository;
import com.arise.corp.login.repository.UsuarioAuthRepository;

class PerfilUsuarioServiceTests {

    private static final UUID USUARIO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final UUID ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000021");

    private PerfilUsuarioRepository perfilUsuarioRepository;
    private UsuarioAuthRepository usuarioAuthRepository;
    private PerfilUsuarioService perfilUsuarioService;

    @BeforeEach
    void setUp() {
        perfilUsuarioRepository = mock(PerfilUsuarioRepository.class);
        usuarioAuthRepository = mock(UsuarioAuthRepository.class);
        perfilUsuarioService = new PerfilUsuarioService(
                perfilUsuarioRepository, usuarioAuthRepository);
    }

    @Test
    void devuelveElPerfilActivoDelUsuario() {
        PerfilUsuario perfil = crearPerfil(RolUsuario.ADMINISTRADOR, true);
        when(perfilUsuarioRepository.findByUsuarioId(USUARIO_ID))
                .thenReturn(Optional.of(perfil));

        PerfilUsuario resultado =
                perfilUsuarioService.obtenerPerfilActivo(USUARIO_ID.toString());

        assertEquals(RolUsuario.ADMINISTRADOR, resultado.getRol());
    }

    @Test
    void buscarPerfilActivoDevuelveVacioParaUnPerfilInactivo() {
        PerfilUsuario perfil = crearPerfil(RolUsuario.COMERCIAL, false);
        when(perfilUsuarioRepository.findByUsuarioId(USUARIO_ID))
                .thenReturn(Optional.of(perfil));

        assertTrue(perfilUsuarioService
                .buscarPerfilActivo(USUARIO_ID.toString())
                .isEmpty());
    }

    @Test
    void rechazaUnPerfilInexistente() {
        when(perfilUsuarioRepository.findByUsuarioId(USUARIO_ID))
                .thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> perfilUsuarioService.obtenerPerfilActivo(USUARIO_ID.toString()));
    }

    @Test
    void rechazaUnPerfilInactivo() {
        PerfilUsuario perfil = crearPerfil(RolUsuario.COMERCIAL, false);
        when(perfilUsuarioRepository.findByUsuarioId(USUARIO_ID))
                .thenReturn(Optional.of(perfil));

        assertThrows(AccessDeniedException.class,
                () -> perfilUsuarioService.obtenerPerfilActivo(USUARIO_ID.toString()));
    }

    @Test
    void rechazaUnSubQueNoEsUuid() {
        assertThrows(AccessDeniedException.class,
                () -> perfilUsuarioService.obtenerPerfilActivo("no-es-un-uuid"));
    }

    @Test
    void creaUnPerfilCuandoLaIdentidadExisteEnAuth() {
        when(perfilUsuarioRepository.findByUsuarioId(USUARIO_ID))
                .thenReturn(Optional.empty());
        when(usuarioAuthRepository.existe(USUARIO_ID)).thenReturn(true);

        ResultadoPerfil resultado = perfilUsuarioService
                .crearOActualizarPerfil(
                        USUARIO_ID, RolUsuario.COMERCIAL, ACTOR_ID.toString())
                .orElseThrow();

        assertTrue(resultado.creado());
        assertEquals(RolUsuario.COMERCIAL, resultado.perfil().getRol());
        assertTrue(resultado.perfil().isActivo());
        assertEquals(ACTOR_ID, resultado.perfil().getActualizadoPor());
        verify(perfilUsuarioRepository).save(any(PerfilUsuario.class));
    }

    @Test
    void noCreaUnPerfilParaUnaIdentidadInexistente() {
        when(perfilUsuarioRepository.findByUsuarioId(USUARIO_ID))
                .thenReturn(Optional.empty());
        when(usuarioAuthRepository.existe(USUARIO_ID)).thenReturn(false);

        Optional<ResultadoPerfil> resultado = perfilUsuarioService
                .crearOActualizarPerfil(
                        USUARIO_ID, RolUsuario.COMERCIAL, ACTOR_ID.toString());

        assertTrue(resultado.isEmpty());
        verify(perfilUsuarioRepository, never()).save(any(PerfilUsuario.class));
    }

    @Test
    void actualizaElRolConservandoLaFechaDeCreacion() {
        PerfilUsuario perfil = crearPerfil(RolUsuario.OPERACIONES, true);
        Instant creadoEn = perfil.getCreadoEn();
        when(perfilUsuarioRepository.findByUsuarioId(USUARIO_ID))
                .thenReturn(Optional.of(perfil));

        ResultadoPerfil resultado = perfilUsuarioService
                .crearOActualizarPerfil(
                        USUARIO_ID, RolUsuario.FINANZAS_RRHH, ACTOR_ID.toString())
                .orElseThrow();

        assertFalse(resultado.creado());
        assertEquals(RolUsuario.FINANZAS_RRHH, resultado.perfil().getRol());
        assertEquals(creadoEn, resultado.perfil().getCreadoEn());
        assertEquals(ACTOR_ID, resultado.perfil().getActualizadoPor());
        verify(usuarioAuthRepository, never()).existe(any());
    }

    @Test
    void cambiaElEstadoDeUnPerfilExistente() {
        PerfilUsuario perfil = crearPerfil(RolUsuario.COMERCIAL, true);
        when(perfilUsuarioRepository.findByUsuarioId(USUARIO_ID))
                .thenReturn(Optional.of(perfil));

        PerfilUsuario resultado = perfilUsuarioService
                .cambiarEstado(USUARIO_ID, false, ACTOR_ID.toString())
                .orElseThrow();

        assertFalse(resultado.isActivo());
        assertEquals(ACTOR_ID, resultado.getActualizadoPor());
    }

    @Test
    void cambiarEstadoDevuelveVacioSiElPerfilNoExiste() {
        when(perfilUsuarioRepository.findByUsuarioId(USUARIO_ID))
                .thenReturn(Optional.empty());

        assertTrue(perfilUsuarioService
                .cambiarEstado(USUARIO_ID, false, ACTOR_ID.toString())
                .isEmpty());
    }

    private PerfilUsuario crearPerfil(RolUsuario rol, boolean activo) {
        Instant ahora = Instant.now();

        return new PerfilUsuario(
                USUARIO_ID, rol, activo, ahora, ahora, null);
    }
}
