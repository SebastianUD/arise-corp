package com.arise.corp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.arise.corp.config.PerfilJwtAuthenticationConverter;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.CuentaCorporativa;
import com.arise.corp.crm.entity.TipoDocumento;
import com.arise.corp.crm.repository.ClienteRepository;
import com.arise.corp.crm.repository.CuentaCorporativaRepository;
import com.arise.corp.login.AutenticacionPerfilTestSupport;
import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.login.repository.PerfilUsuarioRepository;
import com.arise.corp.sga.dto.MovimientoInventarioRequest;
import com.arise.corp.sga.dto.TransicionPaqueteRequest;
import com.arise.corp.sga.entity.ArticuloInventario;
import com.arise.corp.sga.entity.CategoriaArticulo;
import com.arise.corp.sga.entity.Dron;
import com.arise.corp.sga.entity.EstadoPaquete;
import com.arise.corp.sga.entity.HistorialEstadoPaquete;
import com.arise.corp.sga.entity.MovimientoInventario;
import com.arise.corp.sga.entity.Paquete;
import com.arise.corp.sga.entity.TipoMovimientoInventario;
import com.arise.corp.sga.entity.UnidadArticulo;
import com.arise.corp.sga.repository.ArticuloInventarioRepository;
import com.arise.corp.sga.repository.DronRepository;
import com.arise.corp.sga.repository.HistorialEstadoPaqueteRepository;
import com.arise.corp.sga.repository.MovimientoInventarioRepository;
import com.arise.corp.sga.repository.PaqueteRepository;
import com.arise.corp.sga.service.ConflictosSga;
import com.arise.corp.sga.service.MovimientoInventarioService;
import com.arise.corp.sga.service.PaqueteService;

import tools.jackson.databind.ObjectMapper;

/**
 * Transacciones y concurrencia del SGA sobre PostgreSQL efimero (Requirements
 * 10.5, 11.12, 15.9, 15.10, 26.7 y 26.8, DP-SEG-06, ENG-03).
 *
 * <p>Comprueba que un rollback posterior a una transicion de paquete o a un
 * movimiento de inventario no deja rastro, y que dos operaciones simultaneas
 * sobre el mismo dron o el mismo articulo dejan exactamente una aplicada y la
 * otra en 409, gracias a los bloqueos de escritura de D10.
 */
class SgaTransaccionIT extends PostgresIntegracionBase {

    private static final Instant INSTANTE = Instant.parse("2026-01-15T10:00:00Z");
    private static final String RUTA_PAQUETES = "/api/sga/paquetes";
    private static final String RUTA_ARTICULOS = "/api/sga/articulos";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PerfilJwtAuthenticationConverter converter;

    @Autowired
    private PerfilUsuarioRepository perfiles;

    @Autowired
    private CuentaCorporativaRepository cuentas;

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private DronRepository drones;

    @Autowired
    private PaqueteRepository paquetes;

    @Autowired
    private HistorialEstadoPaqueteRepository historial;

    @Autowired
    private ArticuloInventarioRepository articulos;

    @Autowired
    private MovimientoInventarioRepository movimientos;

    @Autowired
    private PaqueteService paqueteService;

    @Autowired
    private MovimientoInventarioService movimientoService;

    @Autowired
    private PlatformTransactionManager transacciones;

    private UUID operaciones;
    private RequestPostProcessor authOperaciones;

    @BeforeEach
    void prepararSesion() {
        operaciones = crearUsuarioAuth();
        perfiles.saveAndFlush(PerfilUsuario.crear(
                operaciones, RolUsuario.OPERACIONES, operaciones, INSTANTE));
        authOperaciones = AutenticacionPerfilTestSupport.como(
                converter, operaciones.toString(), "operaciones.tx@example.test");
    }

    @Test
    @DisplayName("El rollback posterior a cambiarEstado conserva el paquete, el historial y el dron")
    void elRollbackPosteriorACambiarEstadoConservaElEstadoElHistorialYElDron() {
        UUID clienteId = crearCliente();
        Dron dron = crearDron("DRN-TX-001");
        UUID paqueteId = crearPaquete(
                "PAQ-TX-001", clienteId, dron, EstadoPaquete.ASIGNADO);

        assertEquals("ASIGNADO", estadoPaquete(paqueteId));
        assertEquals("DISPONIBLE", estadoDron(dron.getId()));
        assertEquals(2L, contar(
                "select count(*) from app.historial_estados_paquete"
                        + " where paquete_id = ?",
                paqueteId));

        TransactionTemplate plantilla = new TransactionTemplate(transacciones);

        IllegalStateException fallo = assertThrows(
                IllegalStateException.class,
                () -> plantilla.execute(estado -> {
                    paqueteService.cambiarEstado(
                            paqueteId,
                            new TransicionPaqueteRequest(
                                    EstadoPaquete.EN_TRANSITO, null, null),
                            operaciones.toString());
                    throw new IllegalStateException(
                            "fallo simulado despues de la transicion");
                }));

        assertEquals("fallo simulado despues de la transicion", fallo.getMessage());
        assertEquals("ASIGNADO", estadoPaquete(paqueteId));
        assertEquals("DISPONIBLE", estadoDron(dron.getId()));
        assertEquals(2L, contar(
                "select count(*) from app.historial_estados_paquete"
                        + " where paquete_id = ?",
                paqueteId));
        assertEquals("ASIGNADO", jdbc.queryForObject(
                "select estado_nuevo from app.historial_estados_paquete"
                        + " where paquete_id = ? order by secuencia desc limit 1",
                String.class,
                paqueteId));
    }

    @Test
    @DisplayName("El rollback posterior a registrar conserva la existencia y los movimientos")
    void elRollbackPosteriorARegistrarConservaLaExistenciaYLosMovimientos() {
        UUID articuloId = crearArticuloConExistencia("ART-TX-001", 10);
        TransactionTemplate plantilla = new TransactionTemplate(transacciones);

        assertThrows(IllegalStateException.class, () -> plantilla.execute(estado -> {
            movimientoService.registrar(
                    articuloId,
                    new MovimientoInventarioRequest(
                            TipoMovimientoInventario.SALIDA,
                            new BigDecimal("4"),
                            null),
                    operaciones.toString());
            throw new IllegalStateException(
                    "fallo simulado despues del movimiento");
        }));

        assertEquals(10L, existencia(articuloId));
        assertEquals(1L, contar(
                "select count(*) from app.movimientos_inventario"
                        + " where articulo_id = ?",
                articuloId));
    }

    @Test
    @DisplayName("Dos asignaciones simultaneas del mismo dron dejan una aplicada y la otra en 409")
    void dosAsignacionesSimultaneasDelMismoDronDejanUnaAplicadaYLaOtraEn409()
            throws Exception {

        UUID clienteId = crearCliente();
        Dron dron = crearDron("DRN-TX-CONC");
        UUID paqueteA = crearPaquete(
                "PAQ-TX-CONC-A", clienteId, null, EstadoPaquete.REGISTRADO);
        UUID paqueteB = crearPaquete(
                "PAQ-TX-CONC-B", clienteId, null, EstadoPaquete.REGISTRADO);

        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch arranque = new CountDownLatch(1);

        try {
            Future<Intento> primera = ejecutor.submit(
                    () -> asignar(paqueteA, dron.getId(), listos, arranque));
            Future<Intento> segunda = ejecutor.submit(
                    () -> asignar(paqueteB, dron.getId(), listos, arranque));

            assertTrue(listos.await(10, TimeUnit.SECONDS),
                    "los dos hilos deben estar listos antes de arrancar");
            arranque.countDown();

            List<Intento> intentos = List.of(
                    primera.get(30, TimeUnit.SECONDS),
                    segunda.get(30, TimeUnit.SECONDS));

            assertEquals(1, intentos.stream()
                    .filter(intento -> intento.status() == 201)
                    .count(),
                    "exactamente una asignacion debe aplicarse");

            Intento perdedor = intentos.stream()
                    .filter(intento -> intento.status() == 409)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "la otra asignacion debe responder 409"));

            assertTrue(Set.of(
                            ConflictosSga.DRON_CON_PAQUETE_EN_CURSO,
                            ConflictosSga.DRON_NO_DISPONIBLE)
                    .contains(perdedor.conflicto()),
                    "el conflicto debe ser de dron, no de otro catalogo");

            assertEquals(1L, contar(
                    "select count(*) from app.paquetes"
                            + " where dron_id = ? and estado in ('ASIGNADO', 'EN_TRANSITO')",
                    dron.getId()),
                    "solo un paquete puede quedar en curso con el dron");
            assertEquals(1L, contar(
                    "select count(*) from app.paquetes where estado = 'REGISTRADO'"));
        } finally {
            ejecutor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Dos salidas simultaneas de 7 sobre 10 dejan una aplicada y la otra en 409")
    void dosSalidasSimultaneasDeSieteSobreDiezDejanUnaAplicadaYLaOtraEn409()
            throws Exception {

        UUID articuloId = crearArticuloConExistencia("ART-TX-CONC", 10);

        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch arranque = new CountDownLatch(1);

        try {
            Future<Intento> primera = ejecutor.submit(
                    () -> salida(articuloId, listos, arranque));
            Future<Intento> segunda = ejecutor.submit(
                    () -> salida(articuloId, listos, arranque));

            assertTrue(listos.await(10, TimeUnit.SECONDS),
                    "los dos hilos deben estar listos antes de arrancar");
            arranque.countDown();

            List<Intento> intentos = List.of(
                    primera.get(30, TimeUnit.SECONDS),
                    segunda.get(30, TimeUnit.SECONDS));

            assertEquals(1, intentos.stream()
                    .filter(intento -> intento.status() == 201)
                    .count(),
                    "exactamente una salida debe aplicarse");

            Intento perdedor = intentos.stream()
                    .filter(intento -> intento.status() == 409)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "la otra salida debe responder 409"));

            assertEquals(ConflictosSga.EXISTENCIA_INSUFICIENTE,
                    perdedor.conflicto());
            assertEquals(3L, existencia(articuloId),
                    "la existencia final debe ser la de una sola salida");
            assertEquals(1L, contar(
                    "select count(*) from app.movimientos_inventario"
                            + " where articulo_id = ? and tipo = 'SALIDA'",
                    articuloId));
        } finally {
            ejecutor.shutdownNow();
        }
    }

    private Intento asignar(
            UUID paqueteId,
            UUID dronId,
            CountDownLatch listos,
            CountDownLatch arranque) throws Exception {

        listos.countDown();
        assertTrue(arranque.await(10, TimeUnit.SECONDS));

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("estado", "ASIGNADO");
        datos.put("dronId", dronId.toString());

        MvcResult resultado = mvc.perform(post(
                                RUTA_PAQUETES + "/{id}/estados", paqueteId)
                        .with(authOperaciones)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(datos)))
                .andReturn();

        return new Intento(
                resultado.getResponse().getStatus(), conflictoDe(resultado));
    }

    private Intento salida(
            UUID articuloId,
            CountDownLatch listos,
            CountDownLatch arranque) throws Exception {

        listos.countDown();
        assertTrue(arranque.await(10, TimeUnit.SECONDS));

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipo", "SALIDA");
        datos.put("cantidad", 7);

        MvcResult resultado = mvc.perform(post(
                                RUTA_ARTICULOS + "/{id}/movimientos", articuloId)
                        .with(authOperaciones)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(datos)))
                .andReturn();

        return new Intento(
                resultado.getResponse().getStatus(), conflictoDe(resultado));
    }

    private String conflictoDe(MvcResult resultado) throws Exception {
        if (resultado.getResponse().getStatus() != 409) {
            return null;
        }

        return objectMapper
                .readTree(resultado.getResponse().getContentAsString())
                .get("conflicto")
                .asText();
    }

    private UUID crearCliente() {
        CuentaCorporativa cuenta = cuentas.saveAndFlush(CuentaCorporativa.crear(
                "900000501",
                "Cuenta de transacciones SGA S.A.S.",
                null,
                null,
                null,
                operaciones,
                INSTANTE));

        Cliente cliente = clientes.saveAndFlush(Cliente.crear(
                TipoDocumento.CC,
                "10000501",
                "Cliente de transacciones SGA",
                "transacciones.sga@example.test",
                "3001234567",
                null,
                null,
                cuenta,
                operaciones,
                INSTANTE));

        return cliente.getId();
    }

    private Dron crearDron(String codigo) {
        return drones.saveAndFlush(Dron.crear(
                codigo, "Dron de transacciones", null, operaciones, INSTANTE));
    }

    private UUID crearPaquete(
            String codigo,
            UUID clienteId,
            Dron dron,
            EstadoPaquete estado) {

        Paquete paquete = paquetes.save(Paquete.crear(
                codigo,
                "Paquete de transacciones",
                "Calle ficticia 123",
                clienteId,
                null,
                operaciones,
                INSTANTE));

        historial.save(HistorialEstadoPaquete.alta(
                paquete, null, operaciones, INSTANTE));

        if (estado == EstadoPaquete.ASIGNADO) {
            paquete.aplicarTransicion(
                    EstadoPaquete.ASIGNADO, dron, operaciones, INSTANTE);
            historial.save(HistorialEstadoPaquete.transicion(
                    paquete,
                    2,
                    EstadoPaquete.REGISTRADO,
                    EstadoPaquete.ASIGNADO,
                    dron,
                    null,
                    operaciones,
                    INSTANTE));
        }

        paquetes.saveAndFlush(paquete);
        return paquete.getId();
    }

    private UUID crearArticuloConExistencia(String codigo, int existencia) {
        ArticuloInventario articulo = articulos.save(ArticuloInventario.crear(
                codigo,
                "Articulo de transacciones",
                CategoriaArticulo.REPUESTO,
                UnidadArticulo.UNIDAD,
                5,
                operaciones,
                INSTANTE));

        articulo.aplicarMovimiento(
                TipoMovimientoInventario.ENTRADA, existencia);
        articulos.saveAndFlush(articulo);

        movimientos.saveAndFlush(MovimientoInventario.registrar(
                articulo,
                TipoMovimientoInventario.ENTRADA,
                existencia,
                0L,
                existencia,
                null,
                operaciones,
                INSTANTE));

        return articulo.getId();
    }

    private String estadoPaquete(UUID id) {
        return jdbc.queryForObject(
                "select estado from app.paquetes where id = ?", String.class, id);
    }

    private String estadoDron(UUID id) {
        return jdbc.queryForObject(
                "select estado from app.drones where id = ?", String.class, id);
    }

    private long existencia(UUID id) {
        Long valor = jdbc.queryForObject(
                "select existencia from app.articulos_inventario where id = ?",
                Long.class,
                id);
        return valor == null ? 0L : valor.longValue();
    }

    private long contar(String sql, Object... argumentos) {
        Long total = jdbc.queryForObject(sql, Long.class, argumentos);
        return total == null ? 0L : total;
    }

    private record Intento(int status, String conflicto) {
    }
}
