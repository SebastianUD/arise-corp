package com.arise.corp.erp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import com.arise.corp.erp.ErpDatosPrueba;
import com.arise.corp.erp.entity.Empleado;
import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.MovimientoFinanciero;
import com.arise.corp.erp.entity.Proveedor;
import com.arise.corp.erp.entity.TipoMovimiento;
import com.arise.corp.erp.repository.EmpleadoRepository;
import com.arise.corp.erp.repository.MovimientoFinancieroRepository;
import com.arise.corp.erp.repository.ProveedorRepository;
import com.arise.corp.login.entity.RolUsuario;

import tools.jackson.databind.ObjectMapper;

/**
 * Matriz de autorizacion del ERP (Property 18, Requirements 7.5, 12.5, 17.7 y
 * 19.1 a 19.5, 25.5).
 *
 * <p>Recorre las 19 operaciones (16 endpoints y los tres {@code DELETE}) por
 * cada identidad: sin token, sin perfil, perfil inactivo y los cuatro roles.
 * Los casos denegados comprueban que los datos no cambian y que un
 * {@code {id}} inexistente tambien responde 403, porque la seguridad se evalua
 * antes del controlador. El {@code DELETE} con un rol gestor responde 405 con
 * {@code Allow} y conserva el registro. Incluye el caso de lectura cruzada de
 * FINANZAS_RRHH sobre el CRM.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({CrmDatosPrueba.class, ErpDatosPrueba.class})
class ErpAutorizacionTests {

    private static final Set<RolUsuario> GESTORES_ERP =
            Set.of(RolUsuario.ADMINISTRADOR, RolUsuario.FINANZAS_RRHH);

    private static final String EMPLEADOS = "/api/erp/empleados";
    private static final String PROVEEDORES = "/api/erp/proveedores";
    private static final String MOVIMIENTOS = "/api/erp/movimientos";
    private static final String RUTA_RESUMEN = "/api/erp/resumen";
    private static final String CLIENTES = "/api/crm/clientes";
    private static final String DOCUMENTO_EMPLEADO = "10234567";
    private static final String NOMBRE_EMPLEADO = "Empleado autorizacion";
    private static final String DOCUMENTO_PROVEEDOR = "900123456";
    private static final String NOMBRE_PROVEEDOR = "Proveedor autorizacion";
    private static final String CORREO = "persona.autorizacion@example.test";
    private static final String TELEFONO = "3001234567";
    private static final String CONCEPTO = "Movimiento de autorizacion";
    private static final String REFERENCIA = "AUT-001";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CrmDatosPrueba crm;

    @Autowired
    private ErpDatosPrueba datos;

    @Autowired
    private EmpleadoRepository empleados;

    @Autowired
    private ProveedorRepository proveedores;

    @Autowired
    private MovimientoFinancieroRepository movimientos;

    @BeforeEach
    void setUp() {
        datos.limpiar();
    }

    @ParameterizedTest(name = "{0} con {1}")
    @MethodSource("matriz")
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 18: La matriz de acceso se aplica a todos los endpoints del ERP")
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
                if (operacion.eliminar()) {
                    assertEquals(405, estado, mensaje);
                    String permitidos = resultado.getResponse()
                            .getHeader(HttpHeaders.ALLOW);
                    assertNotNull(permitidos, mensaje);
                    assertTrue(permitidos.contains("GET"), mensaje);
                    assertTrue(permitidos.contains("PUT"), mensaje);
                    assertRegistroConservado(contexto, mensaje);
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

    @Test
    void finanzasLeeClientesEnVistaReducidaYNoPuedeEscribir() throws Exception {
        UUID finanzas = crm.crearPerfil(RolUsuario.FINANZAS_RRHH);
        Cliente cliente = crm.crearCliente(
                "10234567", "Cliente autorizacion", CORREO, TELEFONO,
                "Calle Autorizacion 1", "Bogota", true, null);

        mvc.perform(get(CLIENTES + "/{id}", cliente.getId())
                        .with(crm.autenticacionDe(finanzas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Cliente autorizacion"))
                .andExpect(jsonPath("$.correo").doesNotExist())
                .andExpect(jsonPath("$.telefono").doesNotExist())
                .andExpect(jsonPath("$.direccion").doesNotExist());

        mvc.perform(post(CLIENTES)
                        .with(crm.autenticacionDe(finanzas))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(cliente("10234568"))))
                .andExpect(status().isForbidden());
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
        Empleado empleado = datos.crearEmpleado(
                DOCUMENTO_EMPLEADO, NOMBRE_EMPLEADO, true);
        Proveedor proveedor = datos.crearProveedor(
                DOCUMENTO_PROVEEDOR, NOMBRE_PROVEEDOR, true);
        UUID clienteId = datos.clienteFicticio();
        MovimientoFinanciero movimiento = datos.crearMovimiento(
                TipoMovimiento.INGRESO,
                EstadoMovimiento.PENDIENTE,
                ErpDatosPrueba.valor(),
                ErpDatosPrueba.fechaMovimiento(),
                clienteId,
                null);

        return new Contexto(
                empleado,
                proveedor,
                movimiento,
                clienteId,
                crm.crearPerfil(RolUsuario.ADMINISTRADOR),
                crm.crearPerfil(RolUsuario.COMERCIAL),
                crm.crearPerfil(RolUsuario.FINANZAS_RRHH),
                crm.crearPerfil(RolUsuario.OPERACIONES),
                crm.crearPerfil(RolUsuario.FINANZAS_RRHH, false),
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
            peticion.with(crm.autenticacionDe(usuario(identidad, contexto)));
        }

        return mvc.perform(peticion).andReturn();
    }

    private String cuerpo(Operacion operacion, Contexto contexto) {
        Map<String, Object> contenido = switch (operacion) {
            case CREAR_EMPLEADO -> empleado(datos.siguienteDocumento());
            case ACTUALIZAR_EMPLEADO -> empleado(DOCUMENTO_EMPLEADO);
            case CAMBIAR_ESTADO_EMPLEADO -> Map.of("activo", false);
            case CREAR_PROVEEDOR -> proveedor(datos.siguienteDocumento());
            case ACTUALIZAR_PROVEEDOR -> proveedor(DOCUMENTO_PROVEEDOR);
            case CAMBIAR_ESTADO_PROVEEDOR -> Map.of("activo", false);
            case CREAR_MOVIMIENTO -> movimiento(contexto.clienteId());
            case ACTUALIZAR_MOVIMIENTO -> movimiento(contexto.clienteId());
            case CAMBIAR_ESTADO_MOVIMIENTO -> Map.of("estado", "PAGADO");
            default -> throw new IllegalStateException(
                    "La operacion no lleva cuerpo: " + operacion);
        };

        return cuerpo(contenido);
    }

    private static Map<String, Object> empleado(String documento) {
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("tipoDocumento", "CC");
        contenido.put("numeroDocumento", documento);
        contenido.put("nombre", NOMBRE_EMPLEADO);
        contenido.put("cargo", "Analista");
        contenido.put("area", "Finanzas");
        contenido.put("correo", CORREO);
        contenido.put("telefono", TELEFONO);
        contenido.put("fechaIngreso", "2025-03-01");
        return contenido;
    }

    private static Map<String, Object> proveedor(String documento) {
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("tipoDocumento", "NIT");
        contenido.put("numeroDocumento", documento);
        contenido.put("nombre", NOMBRE_PROVEEDOR);
        contenido.put("correo", CORREO);
        contenido.put("telefono", TELEFONO);
        contenido.put("direccion", "Calle Autorizacion 1");
        contenido.put("ciudad", "Bogota");
        return contenido;
    }

    private static Map<String, Object> movimiento(UUID clienteId) {
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("tipo", "INGRESO");
        contenido.put("fecha", "2026-11-10");
        contenido.put("concepto", CONCEPTO);
        contenido.put("valor", "1234.50");
        contenido.put("referencia", REFERENCIA);
        contenido.put("clienteId", clienteId.toString());
        contenido.put("proveedorId", null);
        return contenido;
    }

    private static Map<String, Object> cliente(String documento) {
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("tipoDocumento", "CC");
        contenido.put("numeroDocumento", documento);
        contenido.put("nombre", "Cliente autorizacion");
        contenido.put("correo", CORREO);
        contenido.put("telefono", TELEFONO);
        contenido.put("direccion", "Calle Autorizacion 1");
        contenido.put("ciudad", "Bogota");
        contenido.put("cuentaCorporativaId", null);
        return contenido;
    }

    private static UUID idDe(Operacion operacion, Contexto contexto) {
        return switch (operacion.recurso()) {
            case NINGUNO -> null;
            case EMPLEADO -> contexto.empleado().getId();
            case PROVEEDOR -> contexto.proveedor().getId();
            case MOVIMIENTO -> contexto.movimiento().getId();
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
        Empleado empleado = empleados.findById(contexto.empleado().getId())
                .orElseThrow();
        Proveedor proveedor = proveedores.findById(contexto.proveedor().getId())
                .orElseThrow();
        MovimientoFinanciero movimiento = movimientos
                .findById(contexto.movimiento().getId())
                .orElseThrow();

        return new Instantanea(
                empleados.count(),
                proveedores.count(),
                movimientos.count(),
                empleado.getNombre(),
                empleado.isActivo(),
                proveedor.getNombre(),
                proveedor.isActivo(),
                movimiento.getConcepto(),
                movimiento.getEstado(),
                movimiento.getValor());
    }

    private void assertSinCambios(
            Instantanea antes, Contexto contexto, String mensaje) {

        assertEquals(antes, instantanea(contexto), mensaje);
    }

    private void assertRegistroConservado(Contexto contexto, String mensaje) {
        assertTrue(empleados.findById(contexto.empleado().getId()).isPresent(),
                mensaje);
        assertTrue(proveedores.findById(contexto.proveedor().getId()).isPresent(),
                mensaje);
        assertTrue(movimientos.findById(contexto.movimiento().getId()).isPresent(),
                mensaje);
    }

    private String cuerpo(Map<String, Object> contenido) {
        return objectMapper.writeValueAsString(contenido);
    }

    private enum Estado {
        NO_AUTENTICADO, DENEGADO, PERMITIDO
    }

    private enum Metodo {
        GET, POST, PUT, PATCH, DELETE
    }

    private enum Recurso {
        NINGUNO, EMPLEADO, PROVEEDOR, MOVIMIENTO
    }

    private enum Operacion {
        LISTAR_EMPLEADOS(Metodo.GET, EMPLEADOS, Recurso.NINGUNO, false, false),
        OBTENER_EMPLEADO(Metodo.GET, EMPLEADOS + "/{id}", Recurso.EMPLEADO,
                false, false),
        CREAR_EMPLEADO(Metodo.POST, EMPLEADOS, Recurso.NINGUNO, true, false),
        ACTUALIZAR_EMPLEADO(Metodo.PUT, EMPLEADOS + "/{id}", Recurso.EMPLEADO,
                true, false),
        CAMBIAR_ESTADO_EMPLEADO(Metodo.PATCH, EMPLEADOS + "/{id}",
                Recurso.EMPLEADO, true, false),
        ELIMINAR_EMPLEADO(Metodo.DELETE, EMPLEADOS + "/{id}", Recurso.EMPLEADO,
                false, true),
        LISTAR_PROVEEDORES(Metodo.GET, PROVEEDORES, Recurso.NINGUNO, false,
                false),
        OBTENER_PROVEEDOR(Metodo.GET, PROVEEDORES + "/{id}",
                Recurso.PROVEEDOR, false, false),
        CREAR_PROVEEDOR(Metodo.POST, PROVEEDORES, Recurso.NINGUNO, true, false),
        ACTUALIZAR_PROVEEDOR(Metodo.PUT, PROVEEDORES + "/{id}",
                Recurso.PROVEEDOR, true, false),
        CAMBIAR_ESTADO_PROVEEDOR(Metodo.PATCH, PROVEEDORES + "/{id}",
                Recurso.PROVEEDOR, true, false),
        ELIMINAR_PROVEEDOR(Metodo.DELETE, PROVEEDORES + "/{id}",
                Recurso.PROVEEDOR, false, true),
        LISTAR_MOVIMIENTOS(Metodo.GET, MOVIMIENTOS, Recurso.NINGUNO, false,
                false),
        OBTENER_MOVIMIENTO(Metodo.GET, MOVIMIENTOS + "/{id}",
                Recurso.MOVIMIENTO, false, false),
        CREAR_MOVIMIENTO(Metodo.POST, MOVIMIENTOS, Recurso.NINGUNO, true,
                false),
        ACTUALIZAR_MOVIMIENTO(Metodo.PUT, MOVIMIENTOS + "/{id}",
                Recurso.MOVIMIENTO, true, false),
        CAMBIAR_ESTADO_MOVIMIENTO(Metodo.PATCH, MOVIMIENTOS + "/{id}/estado",
                Recurso.MOVIMIENTO, true, false),
        ELIMINAR_MOVIMIENTO(Metodo.DELETE, MOVIMIENTOS + "/{id}",
                Recurso.MOVIMIENTO, false, true),
        RESUMEN(Metodo.GET, RUTA_RESUMEN, Recurso.NINGUNO, false, false);

        private final Metodo metodo;
        private final String ruta;
        private final Recurso recurso;
        private final boolean conCuerpo;
        private final boolean eliminar;

        Operacion(
                Metodo metodo,
                String ruta,
                Recurso recurso,
                boolean conCuerpo,
                boolean eliminar) {

            this.metodo = metodo;
            this.ruta = ruta;
            this.recurso = recurso;
            this.conCuerpo = conCuerpo;
            this.eliminar = eliminar;
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

        boolean eliminar() {
            return eliminar;
        }

        Set<RolUsuario> permitidos() {
            return GESTORES_ERP;
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
            Empleado empleado,
            Proveedor proveedor,
            MovimientoFinanciero movimiento,
            UUID clienteId,
            UUID administrador,
            UUID comercial,
            UUID finanzas,
            UUID operaciones,
            UUID inactivo,
            UUID sinPerfil) {
    }

    private record Instantanea(
            long empleados,
            long proveedores,
            long movimientos,
            String nombreEmpleado,
            boolean empleadoActivo,
            String nombreProveedor,
            boolean proveedorActivo,
            String conceptoMovimiento,
            EstadoMovimiento estadoMovimiento,
            BigDecimal valorMovimiento) {
    }
}
