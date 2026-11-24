package com.arise.corp.sga.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.sga.SgaDatosPrueba;
import com.arise.corp.sga.entity.ArticuloInventario;
import com.arise.corp.sga.entity.Dron;
import com.arise.corp.sga.entity.EstadoDron;
import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.entity.Paquete;
import com.arise.corp.sga.repository.ArticuloInventarioRepository;
import com.arise.corp.sga.repository.DronRepository;
import com.arise.corp.sga.repository.HistorialEstadoPaqueteRepository;
import com.arise.corp.sga.repository.MovimientoInventarioRepository;
import com.arise.corp.sga.repository.PaqueteRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Matriz de autorizacion del SGA (Property 10, Requirements 4.11, 9.9, 13.10,
 * 18.1-18.6 y 25.4, 25.5, 25.8).
 *
 * <p>Recorre las 21 operaciones (18 endpoints y los tres {@code DELETE}) por
 * cada identidad: sin token, sin perfil, perfil inactivo y los cuatro roles.
 * Los casos denegados comprueban que los datos no cambian y que un
 * {@code {id}} inexistente tambien responde 403, porque la seguridad se evalua
 * antes del controlador. El {@code DELETE} con un rol gestor responde 405 con
 * {@code Allow} y conserva el registro.
 *
 * <p>Recoge los casos de la clase retirada {@code AutorizacionModulosTests}; la
 * matriz de los tres modulos se prueba sobre controladores reales en
 * {@code CrmAutorizacionTests}, {@code ErpAutorizacionTests} y esta clase. El
 * rol se obtiene del perfil persistido mediante el converter de produccion; no
 * se usan tokens reales de Supabase.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({CrmDatosPrueba.class, SgaDatosPrueba.class})
class SgaAutorizacionTests {

    private static final Set<RolUsuario> GESTORES_SGA =
            Set.of(RolUsuario.ADMINISTRADOR, RolUsuario.OPERACIONES);

    private static final String DRONES = "/api/sga/drones";
    private static final String PAQUETES = "/api/sga/paquetes";
    private static final String ARTICULOS = "/api/sga/articulos";
    private static final String RUTA_RESUMEN = "/api/sga/resumen";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CrmDatosPrueba crm;

    @Autowired
    private SgaDatosPrueba datos;

    @Autowired
    private DronRepository drones;

    @Autowired
    private PaqueteRepository paquetes;

    @Autowired
    private ArticuloInventarioRepository articulos;

    @Autowired
    private HistorialEstadoPaqueteRepository historial;

    @Autowired
    private MovimientoInventarioRepository movimientos;

    @BeforeEach
    void setUp() {
        datos.limpiar();
    }

    @ParameterizedTest(name = "{0} con {1}")
    @MethodSource("matriz")
    @DisplayName("Feature: 004-sga-drones-paquetes-inventario, "
            + "Property 10: La matriz de acceso se aplica a todos los endpoints del SGA")
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
        Cliente cliente = crm.crearCliente(crm.crearCuenta());
        Dron dron = datos.crearDron();
        Paquete paquete = datos.crearPaquete(EstadoPaquete.REGISTRADO);
        ArticuloInventario articulo = datos.crearArticulo();

        return new Contexto(
                cliente,
                dron,
                paquete,
                articulo,
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
            case CREAR_DRON -> dron("DRN-AUT-001");
            case ACTUALIZAR_DRON -> dron(contexto.dron().getCodigo());
            case CAMBIAR_ESTADO_DRON ->
                    Map.of("estado", EstadoDron.MANTENIMIENTO.name());
            case CREAR_PAQUETE -> paquete("PAQ-AUT-001", contexto);
            case ACTUALIZAR_PAQUETE -> actualizacionDePaquete();
            case CREAR_ESTADO_PAQUETE -> Map.of(
                    "estado", EstadoPaquete.ASIGNADO.name(),
                    "dronId", contexto.dron().getId().toString());
            case CREAR_ARTICULO -> articulo("ART-AUT-001");
            case ACTUALIZAR_ARTICULO ->
                    articulo(contexto.articulo().getCodigo());
            case CREAR_MOVIMIENTO -> Map.of(
                    "tipo", "ENTRADA",
                    "cantidad", 1);
            default -> throw new IllegalStateException(
                    "La operacion no lleva cuerpo: " + operacion);
        };

        return cuerpo(contenido);
    }

    private static Map<String, Object> dron(String codigo) {
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("codigo", codigo);
        contenido.put("modelo", "Dron de autorizacion");
        contenido.put("observaciones", "Observacion ficticia");
        return contenido;
    }

    private static Map<String, Object> paquete(
            String codigo, Contexto contexto) {

        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("codigo", codigo);
        contenido.put("descripcion", "Paquete de autorizacion");
        contenido.put("direccionEntrega", "Calle ficticia 456");
        contenido.put("clienteId", contexto.cliente().getId().toString());
        return contenido;
    }

    private static Map<String, Object> actualizacionDePaquete() {
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("descripcion", "Paquete actualizado");
        contenido.put("direccionEntrega", "Calle ficticia 789");
        return contenido;
    }

    private static Map<String, Object> articulo(String codigo) {
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("codigo", codigo);
        contenido.put("nombre", "Articulo de autorizacion");
        contenido.put("categoria", "REPUESTO");
        contenido.put("unidad", "UNIDAD");
        contenido.put("stockMinimo", 10);
        return contenido;
    }

    private static UUID idDe(Operacion operacion, Contexto contexto) {
        return switch (operacion.recurso()) {
            case NINGUNO -> null;
            case DRON -> contexto.dron().getId();
            case PAQUETE -> contexto.paquete().getId();
            case ARTICULO -> contexto.articulo().getId();
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
        Dron dron = drones.findById(contexto.dron().getId()).orElseThrow();
        Paquete paquete = paquetes.findById(contexto.paquete().getId())
                .orElseThrow();
        ArticuloInventario articulo = articulos
                .findById(contexto.articulo().getId()).orElseThrow();

        return new Instantanea(
                drones.count(),
                paquetes.count(),
                historial.count(),
                articulos.count(),
                movimientos.count(),
                dron.getEstado(),
                paquete.getEstado(),
                historial.countByPaqueteId(paquete.getId()),
                articulo.getExistencia());
    }

    private void assertSinCambios(
            Instantanea antes, Contexto contexto, String mensaje) {

        assertEquals(antes, instantanea(contexto), mensaje);
    }

    private void assertRegistroConservado(Contexto contexto, String mensaje) {
        assertTrue(drones.findById(contexto.dron().getId()).isPresent(),
                mensaje);
        assertTrue(paquetes.findById(contexto.paquete().getId()).isPresent(),
                mensaje);
        assertTrue(articulos.findById(contexto.articulo().getId()).isPresent(),
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
        NINGUNO, DRON, PAQUETE, ARTICULO
    }

    private enum Operacion {
        LISTAR_DRONES(Metodo.GET, DRONES, Recurso.NINGUNO, false, false),
        OBTENER_DRON(Metodo.GET, DRONES + "/{id}", Recurso.DRON, false, false),
        CREAR_DRON(Metodo.POST, DRONES, Recurso.NINGUNO, true, false),
        ACTUALIZAR_DRON(Metodo.PUT, DRONES + "/{id}", Recurso.DRON, true, false),
        CAMBIAR_ESTADO_DRON(Metodo.PATCH, DRONES + "/{id}/estado",
                Recurso.DRON, true, false),
        ELIMINAR_DRON(Metodo.DELETE, DRONES + "/{id}", Recurso.DRON, false, true),
        LISTAR_PAQUETES(Metodo.GET, PAQUETES, Recurso.NINGUNO, false, false),
        OBTENER_PAQUETE(Metodo.GET, PAQUETES + "/{id}", Recurso.PAQUETE,
                false, false),
        CREAR_PAQUETE(Metodo.POST, PAQUETES, Recurso.NINGUNO, true, false),
        ACTUALIZAR_PAQUETE(Metodo.PUT, PAQUETES + "/{id}", Recurso.PAQUETE,
                true, false),
        LISTAR_HISTORIAL(Metodo.GET, PAQUETES + "/{id}/estados",
                Recurso.PAQUETE, false, false),
        CREAR_ESTADO_PAQUETE(Metodo.POST, PAQUETES + "/{id}/estados",
                Recurso.PAQUETE, true, false),
        ELIMINAR_PAQUETE(Metodo.DELETE, PAQUETES + "/{id}", Recurso.PAQUETE,
                false, true),
        LISTAR_ARTICULOS(Metodo.GET, ARTICULOS, Recurso.NINGUNO, false, false),
        OBTENER_ARTICULO(Metodo.GET, ARTICULOS + "/{id}", Recurso.ARTICULO,
                false, false),
        CREAR_ARTICULO(Metodo.POST, ARTICULOS, Recurso.NINGUNO, true, false),
        ACTUALIZAR_ARTICULO(Metodo.PUT, ARTICULOS + "/{id}", Recurso.ARTICULO,
                true, false),
        LISTAR_MOVIMIENTOS(Metodo.GET, ARTICULOS + "/{id}/movimientos",
                Recurso.ARTICULO, false, false),
        CREAR_MOVIMIENTO(Metodo.POST, ARTICULOS + "/{id}/movimientos",
                Recurso.ARTICULO, true, false),
        ELIMINAR_ARTICULO(Metodo.DELETE, ARTICULOS + "/{id}", Recurso.ARTICULO,
                false, true),
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
            return GESTORES_SGA;
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
            Cliente cliente,
            Dron dron,
            Paquete paquete,
            ArticuloInventario articulo,
            UUID administrador,
            UUID comercial,
            UUID finanzas,
            UUID operaciones,
            UUID inactivo,
            UUID sinPerfil) {
    }

    private record Instantanea(
            long drones,
            long paquetes,
            long historial,
            long articulos,
            long movimientos,
            EstadoDron estadoDron,
            EstadoPaquete estadoPaquete,
            long historialDelPaquete,
            long existenciaArticulo) {
    }
}
