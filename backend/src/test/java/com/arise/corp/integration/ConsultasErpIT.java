package com.arise.corp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.arise.corp.config.PerfilJwtAuthenticationConverter;
import com.arise.corp.crm.GeneradoresCrm;
import com.arise.corp.crm.entity.Cliente;
import com.arise.corp.crm.entity.TipoDocumento;
import com.arise.corp.crm.repository.ClienteRepository;
import com.arise.corp.erp.GeneradoresErp;
import com.arise.corp.erp.entity.Empleado;
import com.arise.corp.erp.entity.EstadoMovimiento;
import com.arise.corp.erp.entity.MovimientoFinanciero;
import com.arise.corp.erp.entity.Proveedor;
import com.arise.corp.erp.entity.TipoDocumentoEmpleado;
import com.arise.corp.erp.entity.TipoDocumentoProveedor;
import com.arise.corp.erp.entity.TipoMovimiento;
import com.arise.corp.erp.repository.EmpleadoRepository;
import com.arise.corp.erp.repository.MovimientoFinancieroRepository;
import com.arise.corp.erp.repository.ProveedorRepository;
import com.arise.corp.login.AutenticacionPerfilTestSupport;
import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.login.repository.PerfilUsuarioRepository;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Property 16 sobre PostgreSQL efimero (Requirements 5.1, 5.3, 5.4, 10.1,
 * 10.3, 10.4, 15.2-15.6 y 26.6, ENG-03).
 *
 * <p>Siembra un conjunto determinista con tildes, mayusculas, documentos con
 * letras, registros activos e inactivos, clientes del CRM y movimientos de los
 * dos tipos en los tres estados repartidos en dos anos. Despues ejecuta 120
 * combinaciones generadas de filtros y orden contra la API y las compara con un
 * modelo en memoria que reproduce {@code EspecificacionesErp}. El orden
 * generado usa claves con un orden estable en PostgreSQL (documentos, fechas,
 * valores y marcas de creacion) con el desempate {@code id,asc}; el orden por
 * nombre se comprueba aparte con un subconjunto ASCII.
 */
class ConsultasErpIT extends PostgresIntegracionBase {

    private static final UUID ACTOR =
            UUID.fromString("00000000-0000-4000-8000-000000000211");
    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDate PRIMER_DIA = LocalDate.of(2025, 1, 1);
    private static final int CASOS_GENERADOS = 120;

    private static final String RUTA_EMPLEADOS = "/api/erp/empleados";
    private static final String RUTA_PROVEEDORES = "/api/erp/proveedores";
    private static final String RUTA_MOVIMIENTOS = "/api/erp/movimientos";

    private static final LocalDate[] HITOS = {
            LocalDate.of(2024, 12, 31),
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 6, 15),
            LocalDate.of(2025, 12, 31),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2027, 1, 1)
    };

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PerfilJwtAuthenticationConverter converter;

    @Autowired
    private PerfilUsuarioRepository perfiles;

    @Autowired
    private EmpleadoRepository empleados;

    @Autowired
    private ProveedorRepository proveedores;

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private MovimientoFinancieroRepository movimientos;

    private RequestPostProcessor autenticacion;

    private final List<EmpleadoModelo> modeloEmpleados = new ArrayList<>();
    private final List<ProveedorModelo> modeloProveedores = new ArrayList<>();
    private final List<MovimientoModelo> modeloMovimientos = new ArrayList<>();
    private final List<Proveedor> entidadesProveedores = new ArrayList<>();

    @BeforeEach
    void sembrarDatos() {
        modeloEmpleados.clear();
        modeloProveedores.clear();
        modeloMovimientos.clear();
        entidadesProveedores.clear();

        UUID gestor = crearUsuarioAuth();
        perfiles.saveAndFlush(PerfilUsuario.crear(
                gestor, RolUsuario.ADMINISTRADOR, gestor, BASE));
        autenticacion = AutenticacionPerfilTestSupport.como(
                converter, gestor.toString(), "admin@example.test");

        sembrarEmpleados();
        sembrarProveedores();
        sembrarMovimientos();
    }

    @Test
    @DisplayName("Feature: 003-erp-empleados-proveedores-finanzas, "
            + "Property 16: Filtros, búsqueda y orden coinciden con un modelo en memoria")
    void filtrosBusquedaYOrdenCoincidenConElModeloEnMemoria() throws Exception {
        Random azar = GeneradoresErp.nuevoRandom();
        int casos = 0;

        for (int i = 0; i < CASOS_GENERADOS; i++) {
            switch (i % 3) {
                case 0 -> verificarEmpleados(consultaEmpleados(azar, i), i);
                case 1 -> verificarProveedores(consultaProveedores(azar, i), i);
                default -> verificarMovimientos(consultaMovimientos(azar, i), i);
            }
            casos++;
        }

        assertTrue(casos >= 100,
                "la propiedad debe ejecutar al menos 100 combinaciones generadas");
    }

    @Test
    @DisplayName("Los valores de q con % o _ se comparan de forma literal")
    void losComodinesDeQSeComparanDeFormaLiteral() throws Exception {
        for (String q : List.of("%", "_", "\\")) {
            List<UUID> empleadosEsperados = modeloEmpleados.stream()
                    .filter(empleado -> coincideTexto(
                            empleado.nombre(), empleado.numeroDocumento(), q))
                    .sorted(Comparator.comparing(EmpleadoModelo::numeroDocumento)
                            .thenComparing((a, b) -> compararUuid(a.id(), b.id())))
                    .map(EmpleadoModelo::id)
                    .toList();
            List<UUID> proveedoresEsperados = modeloProveedores.stream()
                    .filter(proveedor -> coincideTexto(
                            proveedor.nombre(), proveedor.numeroDocumento(), q))
                    .sorted(Comparator.comparing(ProveedorModelo::numeroDocumento)
                            .thenComparing((a, b) -> compararUuid(a.id(), b.id())))
                    .map(ProveedorModelo::id)
                    .toList();
            List<UUID> movimientosEsperados = modeloMovimientos.stream()
                    .filter(movimiento -> coincideTextoMovimiento(
                            movimiento.concepto(), movimiento.referencia(), q))
                    .sorted(Comparator.comparing(MovimientoModelo::fecha)
                            .thenComparing((a, b) -> compararUuid(a.id(), b.id())))
                    .map(MovimientoModelo::id)
                    .toList();

            assertFalse(empleadosEsperados.isEmpty(),
                    () -> "el conjunto debe tener un empleado con " + q);
            assertFalse(proveedoresEsperados.isEmpty(),
                    () -> "el conjunto debe tener un proveedor con " + q);
            assertFalse(movimientosEsperados.isEmpty(),
                    () -> "el conjunto debe tener un movimiento con " + q);

            assertEquals(empleadosEsperados,
                    ids(consultar(RUTA_EMPLEADOS, q, "numeroDocumento,asc")),
                    () -> "q=" + q + " debe compararse literalmente en empleados");
            assertEquals(proveedoresEsperados,
                    ids(consultar(RUTA_PROVEEDORES, q, "numeroDocumento,asc")),
                    () -> "q=" + q + " debe compararse literalmente en proveedores");
            assertEquals(movimientosEsperados,
                    ids(consultar(RUTA_MOVIMIENTOS, q, "fecha,asc")),
                    () -> "q=" + q + " debe compararse literalmente en movimientos");
        }
    }

    @Test
    @DisplayName("El orden por nombre coincide en un subconjunto ASCII")
    void elOrdenPorNombreCoincide() throws Exception {
        assertEquals(
                List.of("Orden Alfa", "Orden Beta", "Orden Delta", "Orden Gamma"),
                nombresDeEmpleados(ids(consultar(RUTA_EMPLEADOS, "Orden", "nombre,asc"))));
        assertEquals(
                List.of("Orden Gamma", "Orden Delta", "Orden Beta", "Orden Alfa"),
                nombresDeEmpleados(ids(consultar(RUTA_EMPLEADOS, "Orden", "nombre,desc"))));
        assertEquals(
                List.of("Orden Alfa", "Orden Beta", "Orden Delta", "Orden Gamma"),
                nombresDeProveedores(ids(consultar(RUTA_PROVEEDORES, "Orden", "nombre,asc"))));
        assertEquals(
                List.of("Orden Gamma", "Orden Delta", "Orden Beta", "Orden Alfa"),
                nombresDeProveedores(ids(consultar(RUTA_PROVEEDORES, "Orden", "nombre,desc"))));
    }

    private void sembrarEmpleados() {
        String[] nombres = {
                "Ana María Restrepo",
                "LUIS FERNANDO GÓMEZ",
                "María José Peña",
                "ANDRÉS CAMILO RUIZ",
                "Sofía Ángel",
                "JORGE ELIÉCER TAFUR",
                "Pedro Páramo",
                "Ñandú Pérez",
                "Mixed Case Name",
                "CARLOS ANDRÉS",
                "Cien % Empleado",
                "Guion_Bajo Empleado",
                "Barra \\ Empleado"
        };

        for (int i = 0; i < 16; i++) {
            TipoDocumentoEmpleado tipo = switch (i % 4) {
                case 0 -> TipoDocumentoEmpleado.PASAPORTE;
                case 1 -> TipoDocumentoEmpleado.CE;
                default -> TipoDocumentoEmpleado.CC;
            };
            String documento = switch (tipo) {
                case PASAPORTE -> String.format("P%07d", i);
                case CE -> String.format("CE%06d", i);
                case CC -> String.format("%08d", 10_000_000 + i);
            };
            Instant creadoEn = BASE.plusSeconds(100 + i * 60L);
            LocalDate fechaIngreso = LocalDate.of(2024, 1, 15).plusDays(i * 11L);

            Empleado empleado = Empleado.crear(
                    tipo,
                    documento,
                    nombres[i % nombres.length],
                    "Cargo " + i,
                    "Area " + i,
                    "empleado" + i + "@example.test",
                    "3001234567",
                    fechaIngreso,
                    ACTOR,
                    creadoEn);

            if (i % 5 == 0) {
                empleado.cambiarEstado(false, ACTOR, creadoEn.plusSeconds(30));
            }

            empleados.saveAndFlush(empleado);
            modeloEmpleados.add(new EmpleadoModelo(
                    empleado.getId(),
                    empleado.getNumeroDocumento(),
                    empleado.getNombre(),
                    empleado.isActivo(),
                    empleado.getFechaIngreso(),
                    creadoEn));
        }

        String[] nombresOrden = {"Orden Alfa", "Orden Beta", "Orden Gamma", "Orden Delta"};

        for (int i = 0; i < nombresOrden.length; i++) {
            Instant creadoEn = BASE.plusSeconds(20_000 + i * 60L);
            Empleado empleado = Empleado.crear(
                    TipoDocumentoEmpleado.CC,
                    String.format("%08d", 30_000_000 + i),
                    nombresOrden[i],
                    "Cargo orden",
                    "Area orden",
                    "orden" + i + "@example.test",
                    "3001234567",
                    LocalDate.of(2025, 2, 1).plusDays(i),
                    ACTOR,
                    creadoEn);

            empleados.saveAndFlush(empleado);
            modeloEmpleados.add(new EmpleadoModelo(
                    empleado.getId(),
                    empleado.getNumeroDocumento(),
                    empleado.getNombre(),
                    empleado.isActivo(),
                    empleado.getFechaIngreso(),
                    creadoEn));
        }
    }

    private void sembrarProveedores() {
        String[] nombres = {
                "Distribuidora Ávila S.A.S.",
                "CONSTRUCTORA Ñandú Ltda.",
                "Cien % Proveedor",
                "Guion_Bajo Proveedor",
                "Barra \\ Proveedor",
                "Comercializadora Andina S.A.",
                "Servicios Técnicos del Caribe",
                "Grupo Empresarial Örejuela",
                "Logística del Pacífico",
                "INVERSIONES ÁGUILA",
                "Textiles Medellín",
                "Agroindustrias del Llano",
                "Transportes Córdoba",
                "Alimentos La Sabana",
                "Tecnología Andina"
        };

        for (int i = 0; i < 16; i++) {
            TipoDocumentoProveedor tipo = i % 3 == 0
                    ? TipoDocumentoProveedor.CE
                    : TipoDocumentoProveedor.NIT;
            String documento = tipo == TipoDocumentoProveedor.NIT
                    ? String.format("9%08d", i + 1)
                    : String.format("CE%06d", i);
            Instant creadoEn = BASE.plusSeconds(5_000 + i * 60L);

            Proveedor proveedor = Proveedor.crear(
                    tipo,
                    documento,
                    nombres[i % nombres.length],
                    "Contacto " + i,
                    "proveedor" + i + "@example.test",
                    "601 000 0000",
                    "Calle " + i,
                    "Bogotá",
                    ACTOR,
                    creadoEn);

            if (i % 4 == 1) {
                proveedor.cambiarEstado(false, ACTOR, creadoEn.plusSeconds(30));
            }

            proveedores.saveAndFlush(proveedor);
            entidadesProveedores.add(proveedor);
            modeloProveedores.add(new ProveedorModelo(
                    proveedor.getId(),
                    proveedor.getNumeroDocumento(),
                    proveedor.getNombre(),
                    proveedor.isActivo(),
                    creadoEn));
        }

        String[] nombresOrden = {"Orden Alfa", "Orden Beta", "Orden Gamma", "Orden Delta"};

        for (int i = 0; i < nombresOrden.length; i++) {
            Instant creadoEn = BASE.plusSeconds(25_000 + i * 60L);
            Proveedor proveedor = Proveedor.crear(
                    TipoDocumentoProveedor.NIT,
                    String.format("8%08d", i + 1),
                    nombresOrden[i],
                    null,
                    "orden" + i + "@example.test",
                    "601 000 0000",
                    null,
                    null,
                    ACTOR,
                    creadoEn);

            proveedores.saveAndFlush(proveedor);
            entidadesProveedores.add(proveedor);
            modeloProveedores.add(new ProveedorModelo(
                    proveedor.getId(),
                    proveedor.getNumeroDocumento(),
                    proveedor.getNombre(),
                    proveedor.isActivo(),
                    creadoEn));
        }
    }

    private void sembrarMovimientos() {
        List<Cliente> entidadesClientes = sembrarClientes();

        String[] conceptos = {
                "Pago de factura",
                "PAGO proveedor",
                "Concepto con % literal",
                "Concepto con _ guion",
                "Concepto con \\ barra",
                "Servicio técnico",
                "Nómina y prestaciones"
        };

        for (int i = 0; i < 30; i++) {
            TipoMovimiento tipo = i % 2 == 0
                    ? TipoMovimiento.INGRESO
                    : TipoMovimiento.EGRESO;
            EstadoMovimiento estado = switch (i % 3) {
                case 0 -> EstadoMovimiento.PENDIENTE;
                case 1 -> EstadoMovimiento.PAGADO;
                default -> EstadoMovimiento.ANULADO;
            };
            LocalDate fecha = PRIMER_DIA.plusDays((i * 47L) % 730);
            BigDecimal valor = new BigDecimal(String.format(
                    "%d.%02d", 100_000 + i * 1_000, (i * 7) % 100));
            Instant creadoEn = BASE.plusSeconds(1_000 + i * 60L);

            MovimientoFinanciero movimiento = MovimientoFinanciero.crear(
                    tipo,
                    fecha,
                    conceptos[i % conceptos.length] + " " + i,
                    valor,
                    String.format("FAC-2025-%04d", i),
                    tipo == TipoMovimiento.INGRESO
                            ? entidadesClientes.get(i % entidadesClientes.size()).getId()
                            : null,
                    tipo == TipoMovimiento.EGRESO
                            ? entidadesProveedores.get(i % entidadesProveedores.size())
                            : null,
                    ACTOR,
                    creadoEn);

            if (estado != EstadoMovimiento.PENDIENTE) {
                movimiento.cambiarEstado(estado, ACTOR, creadoEn.plusSeconds(1));
            }

            movimientos.saveAndFlush(movimiento);
            modeloMovimientos.add(new MovimientoModelo(
                    movimiento.getId(),
                    tipo,
                    estado,
                    fecha,
                    valor,
                    movimiento.getConcepto(),
                    movimiento.getReferencia(),
                    movimiento.getClienteId(),
                    movimiento.getProveedor() == null
                            ? null
                            : movimiento.getProveedor().getId(),
                    creadoEn));
        }
    }

    private List<Cliente> sembrarClientes() {
        String[] nombres = {
                "Cliente Uno",
                "Cliente Dos",
                "Cliente Tres",
                "Cliente Cuatro",
                "Cliente Cinco",
                "Cliente Seis"
        };
        List<Cliente> entidades = new ArrayList<>(nombres.length);

        for (int i = 0; i < nombres.length; i++) {
            Cliente cliente = Cliente.crear(
                    TipoDocumento.CC,
                    String.format("%08d", 20_000_000 + i),
                    nombres[i],
                    "cliente" + i + "@example.test",
                    "3001234567",
                    null,
                    null,
                    null,
                    ACTOR,
                    BASE.plusSeconds(50_000 + i));

            if (i % 3 == 2) {
                cliente.cambiarEstado(false, ACTOR, BASE.plusSeconds(50_100 + i));
            }

            entidades.add(clientes.saveAndFlush(cliente));
        }

        return entidades;
    }

    private ConsultaEmpleados consultaEmpleados(Random azar, int i) {
        int variante = i / 3;
        String q = switch (variante % 14) {
            case 0 -> "%";
            case 1 -> "_";
            case 2 -> "\\";
            case 3 -> null;
            case 4 -> "";
            case 5 -> "   ";
            case 6 -> "  ana  ";
            case 7 -> "ana";
            case 8 -> "MARÍA";
            case 9 -> "peña";
            case 10 -> "P00";
            case 11 -> "2000";
            case 12 -> "zzz";
            default -> GeneradoresCrm.cadena(azar, 1 + azar.nextInt(6))
                    .toLowerCase(Locale.ROOT);
        };
        Boolean activo = switch (variante % 3) {
            case 0 -> null;
            case 1 -> Boolean.TRUE;
            default -> Boolean.FALSE;
        };
        String campo = switch ((variante / 3) % 3) {
            case 0 -> "numeroDocumento";
            case 1 -> "fechaIngreso";
            default -> "creadoEn";
        };
        boolean descendente = (variante / 2) % 2 == 1;

        return new ConsultaEmpleados(q, activo, campo, descendente);
    }

    private ConsultaProveedores consultaProveedores(Random azar, int i) {
        int variante = i / 3;
        String q = switch (variante % 11) {
            case 0 -> "%";
            case 1 -> "_";
            case 2 -> "\\";
            case 3 -> null;
            case 4 -> "   ";
            case 5 -> "  andina  ";
            case 6 -> "ÁVILA";
            case 7 -> "ávila";
            case 8 -> "9000";
            case 9 -> "S.A.S.";
            default -> GeneradoresCrm.cadena(azar, 1 + azar.nextInt(6))
                    .toLowerCase(Locale.ROOT);
        };
        Boolean activo = switch (variante % 3) {
            case 0 -> null;
            case 1 -> Boolean.TRUE;
            default -> Boolean.FALSE;
        };
        String campo = (variante / 3) % 2 == 0 ? "numeroDocumento" : "creadoEn";
        boolean descendente = (variante / 2) % 2 == 1;

        return new ConsultaProveedores(q, activo, campo, descendente);
    }

    private ConsultaMovimientos consultaMovimientos(Random azar, int i) {
        int variante = i / 3;
        TipoMovimiento tipo = switch (variante % 4) {
            case 0 -> null;
            case 1 -> TipoMovimiento.INGRESO;
            case 2 -> TipoMovimiento.EGRESO;
            default -> TipoMovimiento.INGRESO;
        };
        EstadoMovimiento estado = switch (variante % 5) {
            case 0 -> null;
            case 1 -> EstadoMovimiento.PENDIENTE;
            case 2 -> EstadoMovimiento.PAGADO;
            case 3 -> EstadoMovimiento.ANULADO;
            default -> null;
        };
        UUID clienteId = switch (variante % 4) {
            case 0 -> null;
            case 1 -> modeloMovimientos.get(
                    variante % modeloMovimientos.size()).clienteId();
            case 2 -> UUID.randomUUID();
            default -> null;
        };
        UUID proveedorId = switch (variante % 5) {
            case 0 -> null;
            case 1 -> modeloMovimientos.get(
                    variante % modeloMovimientos.size()).proveedorId();
            case 2 -> UUID.randomUUID();
            default -> null;
        };
        LocalDate[] rango = switch (variante % 8) {
            case 0 -> new LocalDate[] {null, null};
            case 1 -> new LocalDate[] {HITOS[1], null};
            case 2 -> new LocalDate[] {null, HITOS[4]};
            case 3 -> new LocalDate[] {HITOS[0], HITOS[7]};
            case 4 -> new LocalDate[] {HITOS[2], HITOS[2]};
            case 5 -> new LocalDate[] {HITOS[5], HITOS[7]};
            case 6 -> new LocalDate[] {HITOS[3], HITOS[6]};
            default -> new LocalDate[] {null, HITOS[1]};
        };
        String q = switch (variante % 12) {
            case 0 -> "%";
            case 1 -> "_";
            case 2 -> "\\";
            case 3 -> null;
            case 4 -> "   ";
            case 5 -> "  pago  ";
            case 6 -> "PAGO";
            case 7 -> "MARÍA";
            case 8 -> "factura";
            case 9 -> "FAC-2025";
            case 10 -> "zzz";
            default -> GeneradoresCrm.cadena(azar, 1 + azar.nextInt(6))
                    .toLowerCase(Locale.ROOT);
        };
        String campo = switch ((variante / 2) % 3) {
            case 0 -> "fecha";
            case 1 -> "valor";
            default -> "creadoEn";
        };
        boolean descendente = (variante / 3) % 2 == 1;

        return new ConsultaMovimientos(
                tipo, estado, clienteId, proveedorId,
                rango[0], rango[1], q, campo, descendente);
    }

    private void verificarEmpleados(ConsultaEmpleados consulta, int caso) throws Exception {
        MockHttpServletRequestBuilder peticion = get(RUTA_EMPLEADOS)
                .param("page", "0")
                .param("size", "100")
                .param("sort", consulta.campoOrden() + ","
                        + (consulta.descendente() ? "desc" : "asc"));

        if (consulta.q() != null) {
            peticion.param("q", consulta.q());
        }
        if (consulta.activo() != null) {
            peticion.param("activo", consulta.activo().toString());
        }

        List<UUID> esperados = modeloEmpleados.stream()
                .filter(empleado -> coincideEmpleado(empleado, consulta))
                .sorted(comparadorEmpleados(consulta))
                .map(EmpleadoModelo::id)
                .toList();

        assertEquals(esperados, ids(peticion),
                () -> "caso " + caso + " de empleados: " + consulta);
    }

    private void verificarProveedores(ConsultaProveedores consulta, int caso) throws Exception {
        MockHttpServletRequestBuilder peticion = get(RUTA_PROVEEDORES)
                .param("page", "0")
                .param("size", "100")
                .param("sort", consulta.campoOrden() + ","
                        + (consulta.descendente() ? "desc" : "asc"));

        if (consulta.q() != null) {
            peticion.param("q", consulta.q());
        }
        if (consulta.activo() != null) {
            peticion.param("activo", consulta.activo().toString());
        }

        List<UUID> esperados = modeloProveedores.stream()
                .filter(proveedor -> coincideProveedor(proveedor, consulta))
                .sorted(comparadorProveedores(consulta))
                .map(ProveedorModelo::id)
                .toList();

        assertEquals(esperados, ids(peticion),
                () -> "caso " + caso + " de proveedores: " + consulta);
    }

    private void verificarMovimientos(ConsultaMovimientos consulta, int caso) throws Exception {
        MockHttpServletRequestBuilder peticion = get(RUTA_MOVIMIENTOS)
                .param("page", "0")
                .param("size", "100")
                .param("sort", consulta.campoOrden() + ","
                        + (consulta.descendente() ? "desc" : "asc"));

        if (consulta.tipo() != null) {
            peticion.param("tipo", consulta.tipo().name());
        }
        if (consulta.estado() != null) {
            peticion.param("estado", consulta.estado().name());
        }
        if (consulta.clienteId() != null) {
            peticion.param("clienteId", consulta.clienteId().toString());
        }
        if (consulta.proveedorId() != null) {
            peticion.param("proveedorId", consulta.proveedorId().toString());
        }
        if (consulta.desde() != null) {
            peticion.param("desde", consulta.desde().toString());
        }
        if (consulta.hasta() != null) {
            peticion.param("hasta", consulta.hasta().toString());
        }
        if (consulta.q() != null) {
            peticion.param("q", consulta.q());
        }

        List<UUID> esperados = modeloMovimientos.stream()
                .filter(movimiento -> coincideMovimiento(movimiento, consulta))
                .sorted(comparadorMovimientos(consulta))
                .map(MovimientoModelo::id)
                .toList();

        assertEquals(esperados, ids(peticion),
                () -> "caso " + caso + " de movimientos: " + consulta);
    }

    private MvcResult consultar(String ruta, String q, String sort) throws Exception {
        return mvc.perform(get(ruta)
                        .param("page", "0")
                        .param("size", "100")
                        .param("sort", sort)
                        .param("q", q)
                        .with(autenticacion))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static boolean coincideEmpleado(
            EmpleadoModelo empleado, ConsultaEmpleados consulta) {

        if (!coincideTexto(empleado.nombre(), empleado.numeroDocumento(), consulta.q())) {
            return false;
        }

        return consulta.activo() == null || empleado.activo() == consulta.activo();
    }

    private static boolean coincideProveedor(
            ProveedorModelo proveedor, ConsultaProveedores consulta) {

        if (!coincideTexto(proveedor.nombre(), proveedor.numeroDocumento(), consulta.q())) {
            return false;
        }

        return consulta.activo() == null || proveedor.activo() == consulta.activo();
    }

    private static boolean coincideMovimiento(
            MovimientoModelo movimiento, ConsultaMovimientos consulta) {

        if (consulta.tipo() != null && consulta.tipo() != movimiento.tipo()) {
            return false;
        }
        if (consulta.estado() != null && consulta.estado() != movimiento.estado()) {
            return false;
        }
        if (consulta.clienteId() != null
                && !consulta.clienteId().equals(movimiento.clienteId())) {
            return false;
        }
        if (consulta.proveedorId() != null
                && !consulta.proveedorId().equals(movimiento.proveedorId())) {
            return false;
        }
        if (consulta.desde() != null && movimiento.fecha().isBefore(consulta.desde())) {
            return false;
        }
        if (consulta.hasta() != null && movimiento.fecha().isAfter(consulta.hasta())) {
            return false;
        }

        return coincideTextoMovimiento(
                movimiento.concepto(), movimiento.referencia(), consulta.q());
    }

    /**
     * Reproduce {@code EspecificacionesErp.nombreODocumentoContiene}: texto
     * recortado, nombre sin distinguir mayusculas y documento normalizado. Los
     * comodines se escaparon en SQL, asi que aqui se comparan como texto
     * literal.
     */
    private static boolean coincideTexto(String texto, String identificador, String q) {
        String buscado = NormalizadorTexto.texto(q);
        if (buscado == null) {
            return true;
        }

        if (texto.toLowerCase(Locale.ROOT)
                .contains(buscado.toLowerCase(Locale.ROOT))) {
            return true;
        }

        String normalizado = NormalizadorTexto.documento(buscado);
        return normalizado != null && identificador.contains(normalizado);
    }

    /**
     * Reproduce {@code EspecificacionesErp.conceptoOReferenciaContiene}. Una
     * referencia nula no coincide con un patron.
     */
    private static boolean coincideTextoMovimiento(
            String concepto, String referencia, String q) {

        String buscado = NormalizadorTexto.texto(q);
        if (buscado == null) {
            return true;
        }

        if (concepto.toLowerCase(Locale.ROOT)
                .contains(buscado.toLowerCase(Locale.ROOT))) {
            return true;
        }

        String codigo = NormalizadorTexto.codigo(buscado);
        return codigo != null && referencia != null && referencia.contains(codigo);
    }

    private static Comparator<EmpleadoModelo> comparadorEmpleados(
            ConsultaEmpleados consulta) {

        Comparator<EmpleadoModelo> comparador = switch (consulta.campoOrden()) {
            case "numeroDocumento" -> Comparator.comparing(EmpleadoModelo::numeroDocumento);
            case "fechaIngreso" -> Comparator.comparing(EmpleadoModelo::fechaIngreso);
            case "creadoEn" -> Comparator.comparing(EmpleadoModelo::creadoEn);
            default -> throw new IllegalArgumentException(
                    "Campo de orden de empleados no soportado: " + consulta.campoOrden());
        };

        return ordenar(comparador, consulta.descendente(), EmpleadoModelo::id);
    }

    private static Comparator<ProveedorModelo> comparadorProveedores(
            ConsultaProveedores consulta) {

        Comparator<ProveedorModelo> comparador = switch (consulta.campoOrden()) {
            case "numeroDocumento" -> Comparator.comparing(ProveedorModelo::numeroDocumento);
            case "creadoEn" -> Comparator.comparing(ProveedorModelo::creadoEn);
            default -> throw new IllegalArgumentException(
                    "Campo de orden de proveedores no soportado: " + consulta.campoOrden());
        };

        return ordenar(comparador, consulta.descendente(), ProveedorModelo::id);
    }

    private static Comparator<MovimientoModelo> comparadorMovimientos(
            ConsultaMovimientos consulta) {

        Comparator<MovimientoModelo> comparador = switch (consulta.campoOrden()) {
            case "fecha" -> Comparator.comparing(MovimientoModelo::fecha);
            case "valor" -> Comparator.comparing(MovimientoModelo::valor);
            case "creadoEn" -> Comparator.comparing(MovimientoModelo::creadoEn);
            default -> throw new IllegalArgumentException(
                    "Campo de orden de movimientos no soportado: " + consulta.campoOrden());
        };

        return ordenar(comparador, consulta.descendente(), MovimientoModelo::id);
    }

    /**
     * Aplica la direccion al campo y despues el desempate {@code id,asc}, que en
     * PostgreSQL compara los 16 bytes del {@code uuid} sin signo.
     */
    private static <T> Comparator<T> ordenar(
            Comparator<T> campo,
            boolean descendente,
            java.util.function.Function<T, UUID> id) {

        Comparator<T> principal = descendente ? campo.reversed() : campo;
        return principal.thenComparing((a, b) -> compararUuid(id.apply(a), id.apply(b)));
    }

    private static int compararUuid(UUID a, UUID b) {
        int alto = Long.compareUnsigned(
                a.getMostSignificantBits(), b.getMostSignificantBits());
        return alto != 0
                ? alto
                : Long.compareUnsigned(
                        a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    private List<UUID> ids(MockHttpServletRequestBuilder peticion) throws Exception {
        return ids(mvc.perform(peticion.with(autenticacion))
                .andExpect(status().isOk())
                .andReturn());
    }

    private List<UUID> ids(MvcResult resultado) throws Exception {
        JsonNode contenido = objectMapper.readTree(
                resultado.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("content");
        List<UUID> ids = new ArrayList<>(contenido.size());

        for (int i = 0; i < contenido.size(); i++) {
            ids.add(UUID.fromString(contenido.get(i).get("id").asString()));
        }

        return ids;
    }

    private List<String> nombresDeEmpleados(List<UUID> ids) {
        return ids.stream()
                .map(id -> modeloEmpleados.stream()
                        .filter(empleado -> empleado.id().equals(id))
                        .findFirst()
                        .orElseThrow())
                .map(EmpleadoModelo::nombre)
                .toList();
    }

    private List<String> nombresDeProveedores(List<UUID> ids) {
        return ids.stream()
                .map(id -> modeloProveedores.stream()
                        .filter(proveedor -> proveedor.id().equals(id))
                        .findFirst()
                        .orElseThrow())
                .map(ProveedorModelo::nombre)
                .toList();
    }

    private record EmpleadoModelo(
            UUID id,
            String numeroDocumento,
            String nombre,
            boolean activo,
            LocalDate fechaIngreso,
            Instant creadoEn) {
    }

    private record ProveedorModelo(
            UUID id,
            String numeroDocumento,
            String nombre,
            boolean activo,
            Instant creadoEn) {
    }

    private record MovimientoModelo(
            UUID id,
            TipoMovimiento tipo,
            EstadoMovimiento estado,
            LocalDate fecha,
            BigDecimal valor,
            String concepto,
            String referencia,
            UUID clienteId,
            UUID proveedorId,
            Instant creadoEn) {
    }

    private record ConsultaEmpleados(
            String q,
            Boolean activo,
            String campoOrden,
            boolean descendente) {
    }

    private record ConsultaProveedores(
            String q,
            Boolean activo,
            String campoOrden,
            boolean descendente) {
    }

    private record ConsultaMovimientos(
            TipoMovimiento tipo,
            EstadoMovimiento estado,
            UUID clienteId,
            UUID proveedorId,
            LocalDate desde,
            LocalDate hasta,
            String q,
            String campoOrden,
            boolean descendente) {
    }
}
