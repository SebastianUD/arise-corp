package com.arise.corp.login.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arise.corp.config.PerfilJwtAuthenticationConverter;
import com.arise.corp.login.AutenticacionPerfilTestSupport;
import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.login.repository.PerfilUsuarioRepository;

/**
 * Pruebas de la matriz de autorizacion por rol en {@code SecurityConfig}.
 *
 * <p>El unico endpoint de modulo es un doble de prueba de SGA: permite
 * verificar el acceso permitido y denegado sin implementar ese modulo. El CRM
 * usa los controladores reales y su matriz se cubre en
 * {@code CrmAutorizacionTests}; el ERP usa sus controladores reales y se cubre
 * en {@code ErpAutorizacionTests} (Requirement 23.8). El rol se obtiene del
 * perfil persistido mediante el converter de produccion; no se usan tokens
 * reales de Supabase.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AutorizacionModulosTests.ConfiguracionEndpoints.class)
class AutorizacionModulosTests {

    private static final String RUTA_SGA = "/api/sga/paquetes";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;

    @Autowired
    private PerfilJwtAuthenticationConverter perfilAuthenticationConverter;

    @BeforeEach
    void limpiarPerfiles() {
        perfilUsuarioRepository.deleteAll();
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mvc.perform(get(RUTA_SGA))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void sinPerfilActivoDevuelve403EnCualquierModulo() throws Exception {
        RequestPostProcessor autenticacion =
                autenticacionDe(UUID.randomUUID());

        mvc.perform(get(RUTA_SGA).with(autenticacion))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void perfilInactivoDevuelve403EnCualquierModulo() throws Exception {
        UUID usuarioId = crearPerfil(RolUsuario.COMERCIAL, false);
        RequestPostProcessor autenticacion = autenticacionDe(usuarioId);

        mvc.perform(get(RUTA_SGA).with(autenticacion))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void operacionesAccedeASgaYFinanzasNo() throws Exception {
        UUID operacionesId = crearPerfil(RolUsuario.OPERACIONES, true);
        UUID finanzasId = crearPerfil(RolUsuario.FINANZAS_RRHH, true);

        mvc.perform(get(RUTA_SGA).with(autenticacionDe(operacionesId)))
                .andExpect(status().isOk());

        mvc.perform(get(RUTA_SGA).with(autenticacionDe(finanzasId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void comercialNoAccedeASga() throws Exception {
        UUID usuarioId = crearPerfil(RolUsuario.COMERCIAL, true);
        RequestPostProcessor autenticacion = autenticacionDe(usuarioId);

        mvc.perform(get(RUTA_SGA).with(autenticacion))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorAccedeASga() throws Exception {
        UUID usuarioId = crearPerfil(RolUsuario.ADMINISTRADOR, true);
        RequestPostProcessor autenticacion = autenticacionDe(usuarioId);

        mvc.perform(get(RUTA_SGA).with(autenticacion))
                .andExpect(status().isOk());
    }

    private UUID crearPerfil(RolUsuario rol, boolean activo) {
        UUID usuarioId = UUID.randomUUID();
        Instant ahora = Instant.now();

        perfilUsuarioRepository.save(new PerfilUsuario(
                usuarioId, rol, activo, ahora, ahora, null));

        return usuarioId;
    }

    private RequestPostProcessor autenticacionDe(UUID usuarioId) {
        return AutenticacionPerfilTestSupport.como(
                perfilAuthenticationConverter,
                usuarioId.toString(),
                "usuario@arise.test");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionEndpoints {

        @Bean
        EndpointsModulos endpointsModulos() {
            return new EndpointsModulos();
        }
    }

    @RestController
    static class EndpointsModulos {

        @GetMapping(RUTA_SGA)
        String listarPaquetes() {
            return "paquetes";
        }
    }
}
