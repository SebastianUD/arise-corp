package com.arise.corp.login.controller;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.arise.corp.config.PerfilJwtAuthenticationConverter;
import com.arise.corp.login.AutenticacionPerfilTestSupport;
import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.login.repository.PerfilUsuarioRepository;

/**
 * Pruebas de autorizacion y contrato de la administracion de perfiles.
 *
 * <p>Solo un Administrador con perfil activo puede crear/actualizar o
 * activar/desactivar perfiles. Se usan JWT simulados convertidos por el mismo
 * converter de produccion a partir de perfiles persistidos en H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminPerfilControllerTests {

    private static final UUID ADMINISTRADOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000031");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PerfilJwtAuthenticationConverter perfilAuthenticationConverter;

    @BeforeEach
    void limpiarDatos() {
        perfilUsuarioRepository.deleteAll();
        jdbcTemplate.update("delete from auth.users");
    }

    @Test
    void putSinTokenDevuelve401() throws Exception {
        mvc.perform(put("/api/admin/usuarios/{id}/perfil", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"COMERCIAL\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void putSinPerfilActivoDevuelve403() throws Exception {
        mvc.perform(put("/api/admin/usuarios/{id}/perfil", UUID.randomUUID())
                        .with(autenticacionDe(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"COMERCIAL\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.title", is("Acceso denegado")));
    }

    @Test
    void putConPerfilInactivoDevuelve403() throws Exception {
        guardarPerfil(ADMINISTRADOR_ID, RolUsuario.ADMINISTRADOR, false);

        mvc.perform(put("/api/admin/usuarios/{id}/perfil", UUID.randomUUID())
                        .with(autenticacionDe(ADMINISTRADOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"COMERCIAL\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    void putConRolDistintoDeAdministradorDevuelve403() throws Exception {
        guardarPerfil(ADMINISTRADOR_ID, RolUsuario.COMERCIAL, true);

        mvc.perform(put("/api/admin/usuarios/{id}/perfil", UUID.randomUUID())
                        .with(autenticacionDe(ADMINISTRADOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"COMERCIAL\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    void administradorCreaPerfilYDevuelve201ConLocation() throws Exception {
        guardarPerfil(ADMINISTRADOR_ID, RolUsuario.ADMINISTRADOR, true);
        UUID objetivo = crearUsuarioAuth();

        mvc.perform(put("/api/admin/usuarios/{id}/perfil", objetivo)
                        .with(autenticacionDe(ADMINISTRADOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"COMERCIAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "/api/admin/usuarios/" + objetivo + "/perfil"))
                .andExpect(jsonPath("$.usuarioId", is(objetivo.toString())))
                .andExpect(jsonPath("$.rol", is(RolUsuario.COMERCIAL.name())))
                .andExpect(jsonPath("$.activo", is(true)))
                .andExpect(jsonPath("$.actualizadoPor",
                        is(ADMINISTRADOR_ID.toString())));

        PerfilUsuario creado = perfilUsuarioRepository.findById(objetivo)
                .orElseThrow();

        assertEquals(RolUsuario.COMERCIAL, creado.getRol());
        assertTrue(creado.isActivo());
    }

    @Test
    void administradorActualizaRolYDevuelve200ConservandoCreacion() throws Exception {
        guardarPerfil(ADMINISTRADOR_ID, RolUsuario.ADMINISTRADOR, true);
        UUID objetivo = UUID.randomUUID();
        Instant creadoEn = Instant.parse("2026-01-01T00:00:00Z");
        perfilUsuarioRepository.save(new PerfilUsuario(
                objetivo, RolUsuario.OPERACIONES, true,
                creadoEn, creadoEn, null));

        mvc.perform(put("/api/admin/usuarios/{id}/perfil", objetivo)
                        .with(autenticacionDe(ADMINISTRADOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"FINANZAS_RRHH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol",
                        is(RolUsuario.FINANZAS_RRHH.name())))
                .andExpect(jsonPath("$.activo", is(true)))
                .andExpect(jsonPath("$.actualizadoPor",
                        is(ADMINISTRADOR_ID.toString())));

        PerfilUsuario actualizado = perfilUsuarioRepository.findById(objetivo)
                .orElseThrow();

        assertEquals(RolUsuario.FINANZAS_RRHH, actualizado.getRol());
        assertEquals(creadoEn, actualizado.getCreadoEn());
        assertTrue(actualizado.isActivo());
    }

    @Test
    void administradorNoCreaPerfilParaIdentidadInexistente() throws Exception {
        guardarPerfil(ADMINISTRADOR_ID, RolUsuario.ADMINISTRADOR, true);
        UUID inexistente = UUID.randomUUID();

        mvc.perform(put("/api/admin/usuarios/{id}/perfil", inexistente)
                        .with(autenticacionDe(ADMINISTRADOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"COMERCIAL\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.title", is("Recurso no encontrado")));

        assertTrue(perfilUsuarioRepository.findById(inexistente).isEmpty());
    }

    @Test
    void administradorActivaYDesactivaUnPerfil() throws Exception {
        guardarPerfil(ADMINISTRADOR_ID, RolUsuario.ADMINISTRADOR, true);
        UUID objetivo = UUID.randomUUID();
        Instant ahora = Instant.now();
        perfilUsuarioRepository.save(new PerfilUsuario(
                objetivo, RolUsuario.COMERCIAL, true, ahora, ahora, null));

        mvc.perform(patch("/api/admin/usuarios/{id}/estado", objetivo)
                        .with(autenticacionDe(ADMINISTRADOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo", is(false)));

        assertFalse(perfilUsuarioRepository.findById(objetivo)
                .orElseThrow().isActivo());

        mvc.perform(patch("/api/admin/usuarios/{id}/estado", objetivo)
                        .with(autenticacionDe(ADMINISTRADOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo", is(true)));

        PerfilUsuario reactivado = perfilUsuarioRepository.findById(objetivo)
                .orElseThrow();

        assertTrue(reactivado.isActivo());
        assertEquals(ADMINISTRADOR_ID, reactivado.getActualizadoPor());
    }

    @Test
    void patchDePerfilInexistenteDevuelve404() throws Exception {
        guardarPerfil(ADMINISTRADOR_ID, RolUsuario.ADMINISTRADOR, true);

        mvc.perform(patch("/api/admin/usuarios/{id}/estado", UUID.randomUUID())
                        .with(autenticacionDe(ADMINISTRADOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void patchSinActivoDevuelve400() throws Exception {
        guardarPerfil(ADMINISTRADOR_ID, RolUsuario.ADMINISTRADOR, true);

        mvc.perform(patch("/api/admin/usuarios/{id}/estado", UUID.randomUUID())
                        .with(autenticacionDe(ADMINISTRADOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.errors[0].field", is("activo")));
    }

    @Test
    void putSinRolDevuelve400() throws Exception {
        guardarPerfil(ADMINISTRADOR_ID, RolUsuario.ADMINISTRADOR, true);

        mvc.perform(put("/api/admin/usuarios/{id}/perfil", UUID.randomUUID())
                        .with(autenticacionDe(ADMINISTRADOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.errors[0].field", is("rol")));
    }

    private void guardarPerfil(UUID usuarioId, RolUsuario rol, boolean activo) {
        Instant ahora = Instant.now();

        perfilUsuarioRepository.save(new PerfilUsuario(
                usuarioId, rol, activo, ahora, ahora, null));
    }

    private UUID crearUsuarioAuth() {
        UUID usuarioId = UUID.randomUUID();
        jdbcTemplate.update("insert into auth.users (id) values (?)", usuarioId);
        return usuarioId;
    }

    private RequestPostProcessor autenticacionDe(UUID usuarioId) {
        return AutenticacionPerfilTestSupport.como(
                perfilAuthenticationConverter,
                usuarioId.toString(),
                "admin@arise.test");
    }
}
