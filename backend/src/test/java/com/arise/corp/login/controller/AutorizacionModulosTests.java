package com.arise.corp.login.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arise.corp.config.PerfilJwtAuthenticationConverter;
import com.arise.corp.login.AutenticacionPerfilTestSupport;
import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.login.repository.PerfilUsuarioRepository;

/**
 * Pruebas de la matriz de autorizacion por rol en {@code SecurityConfig}.
 *
 * <p>Los endpoints de modulo son dobles de prueba: permiten verificar el
 * acceso permitido y denegado sin implementar CRM, ERP ni SGA. El rol se
 * obtiene del perfil persistido mediante el converter de produccion; no se
 * usan tokens reales de Supabase.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AutorizacionModulosTests.ConfiguracionEndpoints.class)
class AutorizacionModulosTests {

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
        mvc.perform(get("/api/crm/clientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void sinPerfilActivoDevuelve403EnCualquierModulo() throws Exception {
        mvc.perform(get("/api/crm/clientes")
                        .with(autenticacionDe(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void perfilInactivoDevuelve403EnCualquierModulo() throws Exception {
        UUID usuarioId = crearPerfil(RolUsuario.COMERCIAL, false);

        mvc.perform(get("/api/crm/clientes")
                        .with(autenticacionDe(usuarioId)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void comercialAccedeACrm() throws Exception {
        UUID usuarioId = crearPerfil(RolUsuario.COMERCIAL, true);

        mvc.perform(get("/api/crm/clientes")
                        .with(autenticacionDe(usuarioId)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/crm/clientes")
                        .with(autenticacionDe(usuarioId)))
                .andExpect(status().isOk());
    }

    @Test
    void finanzasPuedeLeerClientesDeCrmPeroNoEscribir() throws Exception {
        UUID usuarioId = crearPerfil(RolUsuario.FINANZAS_RRHH, true);

        mvc.perform(get("/api/crm/clientes")
                        .with(autenticacionDe(usuarioId)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/crm/clientes/{id}", UUID.randomUUID())
                        .with(autenticacionDe(usuarioId)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/crm/clientes")
                        .with(autenticacionDe(usuarioId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void finanzasNoPuedeLeerContratosDeCrm() throws Exception {
        UUID usuarioId = crearPerfil(RolUsuario.FINANZAS_RRHH, true);

        mvc.perform(get("/api/crm/contratos/{id}", UUID.randomUUID())
                        .with(autenticacionDe(usuarioId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void operacionesPuedeLeerClientesYContratosDeCrmPeroNoEscribir() throws Exception {
        UUID usuarioId = crearPerfil(RolUsuario.OPERACIONES, true);

        mvc.perform(get("/api/crm/clientes")
                        .with(autenticacionDe(usuarioId)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/crm/contratos/{id}", UUID.randomUUID())
                        .with(autenticacionDe(usuarioId)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/crm/clientes")
                        .with(autenticacionDe(usuarioId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void finanzasAccedeAErpYOperacionesNo() throws Exception {
        UUID finanzasId = crearPerfil(RolUsuario.FINANZAS_RRHH, true);
        UUID operacionesId = crearPerfil(RolUsuario.OPERACIONES, true);

        mvc.perform(get("/api/erp/proveedores")
                        .with(autenticacionDe(finanzasId)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/erp/proveedores")
                        .with(autenticacionDe(operacionesId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void operacionesAccedeASgaYFinanzasNo() throws Exception {
        UUID operacionesId = crearPerfil(RolUsuario.OPERACIONES, true);
        UUID finanzasId = crearPerfil(RolUsuario.FINANZAS_RRHH, true);

        mvc.perform(get("/api/sga/paquetes")
                        .with(autenticacionDe(operacionesId)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/sga/paquetes")
                        .with(autenticacionDe(finanzasId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void comercialNoAccedeAErpNiSga() throws Exception {
        UUID usuarioId = crearPerfil(RolUsuario.COMERCIAL, true);

        mvc.perform(get("/api/erp/proveedores")
                        .with(autenticacionDe(usuarioId)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/sga/paquetes")
                        .with(autenticacionDe(usuarioId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorAccedeATodosLosModulos() throws Exception {
        UUID usuarioId = crearPerfil(RolUsuario.ADMINISTRADOR, true);

        mvc.perform(get("/api/crm/clientes")
                        .with(autenticacionDe(usuarioId)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/erp/proveedores")
                        .with(autenticacionDe(usuarioId)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/sga/paquetes")
                        .with(autenticacionDe(usuarioId)))
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

        @GetMapping("/api/crm/clientes")
        String listarClientes() {
            return "clientes";
        }

        @GetMapping("/api/crm/clientes/{id}")
        String obtenerCliente(@PathVariable String id) {
            return "cliente";
        }

        @PostMapping("/api/crm/clientes")
        String crearCliente() {
            return "creado";
        }

        @GetMapping("/api/crm/contratos/{id}")
        String obtenerContrato(@PathVariable String id) {
            return "contrato";
        }

        @GetMapping("/api/erp/proveedores")
        String listarProveedores() {
            return "proveedores";
        }

        @GetMapping("/api/sga/paquetes")
        String listarPaquetes() {
            return "paquetes";
        }
    }
}
