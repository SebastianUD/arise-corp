package com.arise.corp.crm.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.arise.corp.crm.CrmDatosPrueba;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.Contrato;
import com.arise.corp.crm.entity.CuentaCorporativa;
import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.crm.repository.ClienteRepository;
import com.arise.corp.crm.repository.ContratoRepository;
import com.arise.corp.crm.repository.CuentaCorporativaRepository;
import com.arise.corp.login.entity.RolUsuario;

import tools.jackson.databind.ObjectMapper;

/**
 * Matriz de autorizacion del CRM (Property 16, Requirements 16.1 a 16.5 y
 * 23.4 a 23.6).
 *
 * <p>Recorre las 17 operaciones (16 endpoints y el {@code DELETE} de clientes)
 * por cada identidad: sin token, sin perfil, perfil inactivo y los cuatro
 * roles. Los casos denegados comprueban que los datos no cambian y que un
 * {@code {id}} inexistente tambien responde 403, porque la seguridad se evalua
 * antes del controlador. El {@code DELETE} con un rol gestor responde 405 y
 * conserva el cliente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CrmDatosPrueba.class)
class CrmAutorizacionTests {

    private static final Set<RolUsuario> GESTORES =
            Set.of(RolUsuario.ADMINISTRADOR, RolUsuario.COMERCIAL);
    private static final Set<RolUsuario> LECTURA_CLIENTES =
            Set.of(RolUsuario.ADMINISTRADOR, RolUsuario.COMERCIAL,
                    RolUsuario.FINANZAS_RRHH, RolUsuario.OPERACIONES);
    private static final Set<RolUsuario> LECTURA_CONTRATOS =
            Set.of(RolUsuario.ADMINISTRADOR, RolUsuario.COMERCIAL,
                    RolUsuario.OPERACIONES);

    private static final String CLIENTES = "/api/crm/clientes";
    private static final String CUENTAS = "/api/crm/cuentas-corporativas";
    private static final String CONTRATOS = "/api/crm/contratos";
    private static final String RUTA_RESUMEN = "/api/crm/resumen";
    private static final String DOCUMENTO = "10234567";
    private static final String NOMBRE = "Cliente autorizacion";
    private static final String CORREO = "cliente.autorizacion@example.test";
    private static final String TELEFONO = "3001234567";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CrmDatosPrueba datos;

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private CuentaCorporativaRepository cuentas;

    @Autowired
    private ContratoRepository contratos;

    @BeforeEach
    void setUp() {
        datos.limpiar();
    }

    @ParameterizedTest(name = "{0} con {1}")
    @MethodSource("matriz")
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 16: La matriz de acceso se aplica a todos los endpoints")
    void aplicaLaMatrizDeAccesoAOperacionesYRoles(
            Operacion operacion, Identidad identidad) throws Exception {

        Contexto contexto = preparar();
        Instantanea antes = instantanea(contexto);
        Estado esperado = esperado(operacion, identidad);
        String mensaje = operacion + " con " + identidad;

        MvcResult resultado = ejecutar(operacion, contexto, identidad, null);
        int estado = resultado.getResponse().getStatus();

        switch (esperado) {
            case NO_AUTENTICADO -> {
                assertEquals(401, estado, mensaje);
                assertNotNull(resultado.getResponse()
                        .getHeader(HttpHeaders.WWW_AUTHENTICATE), mensaje);
            }
            case DENEGADO -> {
                assertEquals(403, estado, mensaje);
                assertSinCambios(antes, contexto, mensaje);

                if (operacion.conId()) {
                    MvcResult inexistente = ejecutar(
                            operacion, contexto, identidad, UUID.randomUUID());
                    assertEquals(403, inexistente.getResponse().getStatus(),
                            mensaje + " con un id inexistente");
                    assertSinCambios(antes, contexto, mensaje);
                }
            }
            case PERMITIDO -> {
                if (operacion == Operacion.ELIMINAR_CLIENTE) {
                    assertEquals(405, estado, mensaje);
                    String permitidos = resultado.getResponse()
                            .getHeader(HttpHeaders.ALLOW);
                    assertNotNull(permitidos, mensaje);
                    assertTrue(permitidos.contains("GET"), mensaje);
                    assertTrue(permitidos.contains("PATCH"), mensaje);
                    assertTrue(clientes.findById(contexto.cliente().getId())
                            .isPresent(), mensaje);
                } else {
                    assertNotEquals(401, estado, mensaje);
                    assertNotEquals(403, estado, mensaje);
                    assertEquals(
                            operacion.metodo() == Metodo.POST ? 201 : 200,
                            estado, mensaje);
                }
            }
        }
    }

    static Stream<Arguments> matriz() {
        List<Arguments> casos = new ArrayList<>();

        for (Operacion operacion : Operacion.values()) {
            for (Identidad identidad : Identidad.values()) {
                casos.add(Arguments.of(operacion, identidad));
            }
        }

        return casos.stream();
    }

    private Contexto preparar() {
        CuentaCorporativa cuenta = datos.crearCuenta();
        Cliente cliente = datos.crearCliente(
                DOCUMENTO, NOMBRE, true, cuenta);
        Contrato contrato = datos.crearContrato(cliente, EstadoContrato.BORRADOR);

        return new Contexto(
                cuenta,
                cliente,
                contrato,
                datos.crearPerfil(RolUsuario.ADMINISTRADOR),
                datos.crearPerfil(RolUsuario.COMERCIAL),
                datos.crearPerfil(RolUsuario.FINANZAS_RRHH),
                datos.crearPerfil(RolUsuario.OPERACIONES),
                datos.crearPerfil(RolUsuario.COMERCIAL, false),
                UUID.randomUUID());
    }

    private MvcResult ejecutar(
            Operacion operacion,
            Contexto contexto,
            Identidad identidad,
            UUID idRuta) throws Exception {

        UUID id = idRuta != null ? idRuta : idDe(operacion, contexto);
        String ruta = id == null
                ? operacion.ruta()
                : operacion.ruta().replace("{id}", id.toString());

        MockHttpServletRequestBuilder peticion = switch (operacion.metodo()) {
            case GET -> get(ruta);
            case POST -> post(ruta);
            case PUT -> put(ruta);
            case PATCH -> patch(ruta);
            case DELETE -> delete(ruta);
        };

        if (operacion.conCuerpo()) {
            peticion.contentType(MediaType.APPLICATION_JSON)
                    .content(cuerpo(operacion, contexto));
        }

        if (identidad != Identidad.SIN_TOKEN) {
            peticion.with(datos.autenticacionDe(usuario(identidad, contexto)));
        }

        return mvc.perform(peticion).andReturn();
    }

    private String cuerpo(Operacion operacion, Contexto contexto) {
        Map<String, Object> contenido = switch (operacion) {
            case CREAR_CLIENTE -> cliente(
                    datos.siguienteDocumento(), contexto.cuenta().getId());
            case ACTUALIZAR_CLIENTE -> cliente(
                    DOCUMENTO, contexto.cuenta().getId());
            case CAMBIAR_ESTADO_CLIENTE -> Map.of("activo", false);
            case CREAR_CUENTA -> cuenta("900777777");
            case ACTUALIZAR_CUENTA -> cuenta(contexto.cuenta().getNit());
            case CAMBIAR_ESTADO_CUENTA -> Map.of("activa", false);
            case CREAR_CONTRATO -> contrato(
                    "CTR-AUT-001", contexto.cliente().getId());
            case ACTUALIZAR_CONTRATO -> contrato(
                    contexto.contrato().getCodigo(), contexto.cliente().getId());
            case CAMBIAR_ESTADO_CONTRATO -> Map.of("estado", "ACTIVO");
            default -> throw new IllegalStateException(
                    "La operacion no lleva cuerpo: " + operacion);
        };

        return objectMapper.writeValueAsString(contenido);
    }

    private static Map<String, Object> cliente(String documento, UUID cuentaId) {
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("tipoDocumento", "CC");
        contenido.put("numeroDocumento", documento);
        contenido.put("nombre", NOMBRE);
        contenido.put("correo", CORREO);
        contenido.put("telefono", TELEFONO);
        contenido.put("direccion", "Calle Autorizacion 1");
        contenido.put("ciudad", "Bogota");
        contenido.put("cuentaCorporativaId",
                cuentaId == null ? null : cuentaId.toString());
        return contenido;
    }

    private static Map<String, Object> cuenta(String nit) {
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("nit", nit);
        contenido.put("razonSocial", "Cuenta autorizacion");
        contenido.put("nombreContacto", "Contacto autorizacion");
        contenido.put("correoContacto", "cuenta.autorizacion@example.test");
        contenido.put("telefonoContacto", TELEFONO);
        return contenido;
    }

    private static Map<String, Object> contrato(String codigo, UUID clienteId) {
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("clienteId", clienteId.toString());
        contenido.put("codigo", codigo);
        contenido.put("descripcion", "Contrato autorizacion");
        contenido.put("fechaInicio", "2026-01-01");
        contenido.put("fechaFin", "2026-12-31");
        contenido.put("valor", "1234.50");
        return contenido;
    }

    private static UUID idDe(Operacion operacion, Contexto contexto) {
        return switch (operacion.recurso()) {
            case NINGUNO -> null;
            case CLIENTE -> contexto.cliente().getId();
            case CUENTA -> contexto.cuenta().getId();
            case CONTRATO -> contexto.contrato().getId();
        };
    }

    private static UUID usuario(Identidad identidad, Contexto contexto) {
        return switch (identidad) {
            case SIN_PERFIL -> contexto.sinPerfil();
            case PERFIL_INACTIVO -> contexto.inactivo();
            case ADMINISTRADOR -> contexto.administrador();
            case COMERCIAL -> contexto.comercial();
            case FINANZAS_RRHH -> contexto.finanzas();
            case OPERACIONES -> contexto.operaciones();
            case SIN_TOKEN -> throw new IllegalStateException(
                    "Sin token no usa usuario");
        };
    }

    private static Estado esperado(Operacion operacion, Identidad identidad) {
        if (identidad == Identidad.SIN_TOKEN) {
            return Estado.NO_AUTENTICADO;
        }
        if (identidad.rol() == null) {
            return Estado.DENEGADO;
        }
        return operacion.permitidos().contains(identidad.rol())
                ? Estado.PERMITIDO
                : Estado.DENEGADO;
    }

    private Instantanea instantanea(Contexto contexto) {
        Cliente cliente = clientes.findById(contexto.cliente().getId())
                .orElseThrow();
        CuentaCorporativa cuenta = cuentas.findById(contexto.cuenta().getId())
                .orElseThrow();
        Contrato contrato = contratos.findById(contexto.contrato().getId())
                .orElseThrow();

        return new Instantanea(
                clientes.count(),
                cuentas.count(),
                contratos.count(),
                cliente.getNombre(),
                cliente.isActivo(),
                cuenta.getRazonSocial(),
                cuenta.isActiva(),
                contrato.getCodigo(),
                contrato.getEstado());
    }

    private void assertSinCambios(
            Instantanea antes, Contexto contexto, String mensaje) {

        assertEquals(antes, instantanea(contexto), mensaje);
    }

    private enum Estado {
        NO_AUTENTICADO, DENEGADO, PERMITIDO
    }

    private enum Metodo {
        GET, POST, PUT, PATCH, DELETE
    }

    private enum Recurso {
        NINGUNO, CLIENTE, CUENTA, CONTRATO
    }

    private enum Operacion {
        LISTAR_CLIENTES(Metodo.GET, CLIENTES, Recurso.NINGUNO, false,
                LECTURA_CLIENTES),
        OBTENER_CLIENTE(Metodo.GET, CLIENTES + "/{id}", Recurso.CLIENTE, false,
                LECTURA_CLIENTES),
        CREAR_CLIENTE(Metodo.POST, CLIENTES, Recurso.NINGUNO, true, GESTORES),
        ACTUALIZAR_CLIENTE(Metodo.PUT, CLIENTES + "/{id}", Recurso.CLIENTE,
                true, GESTORES),
        CAMBIAR_ESTADO_CLIENTE(Metodo.PATCH, CLIENTES + "/{id}",
                Recurso.CLIENTE, true, GESTORES),
        ELIMINAR_CLIENTE(Metodo.DELETE, CLIENTES + "/{id}", Recurso.CLIENTE,
                false, GESTORES),
        LISTAR_CUENTAS(Metodo.GET, CUENTAS, Recurso.NINGUNO, false, GESTORES),
        OBTENER_CUENTA(Metodo.GET, CUENTAS + "/{id}", Recurso.CUENTA, false,
                GESTORES),
        CREAR_CUENTA(Metodo.POST, CUENTAS, Recurso.NINGUNO, true, GESTORES),
        ACTUALIZAR_CUENTA(Metodo.PUT, CUENTAS + "/{id}", Recurso.CUENTA, true,
                GESTORES),
        CAMBIAR_ESTADO_CUENTA(Metodo.PATCH, CUENTAS + "/{id}", Recurso.CUENTA,
                true, GESTORES),
        LISTAR_CONTRATOS(Metodo.GET, CONTRATOS, Recurso.NINGUNO, false,
                LECTURA_CONTRATOS),
        OBTENER_CONTRATO(Metodo.GET, CONTRATOS + "/{id}", Recurso.CONTRATO,
                false, LECTURA_CONTRATOS),
        CREAR_CONTRATO(Metodo.POST, CONTRATOS, Recurso.NINGUNO, true,
                GESTORES),
        ACTUALIZAR_CONTRATO(Metodo.PUT, CONTRATOS + "/{id}", Recurso.CONTRATO,
                true, GESTORES),
        CAMBIAR_ESTADO_CONTRATO(Metodo.PATCH, CONTRATOS + "/{id}/estado",
                Recurso.CONTRATO, true, GESTORES),
        RESUMEN(Metodo.GET, RUTA_RESUMEN, Recurso.NINGUNO, false, GESTORES);

        private final Metodo metodo;
        private final String ruta;
        private final Recurso recurso;
        private final boolean conCuerpo;
        private final Set<RolUsuario> permitidos;

        Operacion(
                Metodo metodo,
                String ruta,
                Recurso recurso,
                boolean conCuerpo,
                Set<RolUsuario> permitidos) {

            this.metodo = metodo;
            this.ruta = ruta;
            this.recurso = recurso;
            this.conCuerpo = conCuerpo;
            this.permitidos = permitidos;
        }

        Metodo metodo() {
            return metodo;
        }

        String ruta() {
            return ruta;
        }

        Recurso recurso() {
            return recurso;
        }

        boolean conCuerpo() {
            return conCuerpo;
        }

        Set<RolUsuario> permitidos() {
            return permitidos;
        }

        boolean conId() {
            return recurso != Recurso.NINGUNO;
        }
    }

    private enum Identidad {
        SIN_TOKEN(null),
        SIN_PERFIL(null),
        PERFIL_INACTIVO(null),
        ADMINISTRADOR(RolUsuario.ADMINISTRADOR),
        COMERCIAL(RolUsuario.COMERCIAL),
        FINANZAS_RRHH(RolUsuario.FINANZAS_RRHH),
        OPERACIONES(RolUsuario.OPERACIONES);

        private final RolUsuario rol;

        Identidad(RolUsuario rol) {
            this.rol = rol;
        }

        RolUsuario rol() {
            return rol;
        }
    }

    private record Contexto(
            CuentaCorporativa cuenta,
            Cliente cliente,
            Contrato contrato,
            UUID administrador,
            UUID comercial,
            UUID finanzas,
            UUID operaciones,
            UUID inactivo,
            UUID sinPerfil) {
    }

    private record Instantanea(
            long clientes,
            long cuentas,
            long contratos,
            String nombreCliente,
            boolean clienteActivo,
            String razonSocial,
            boolean cuentaActiva,
            String codigoContrato,
            EstadoContrato estadoContrato) {
    }
}

