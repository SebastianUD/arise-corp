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
import com.arise.corp.crm.entity.Contrato;
import com.arise.corp.crm.entity.CuentaCorporativa;
import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.crm.entity.TipoDocumento;
import com.arise.corp.crm.repository.ClienteRepository;
import com.arise.corp.crm.repository.ContratoRepository;
import com.arise.corp.crm.repository.CuentaCorporativaRepository;
import com.arise.corp.login.AutenticacionPerfilTestSupport;
import com.arise.corp.login.entity.PerfilUsuario;
import com.arise.corp.login.entity.RolUsuario;
import com.arise.corp.login.repository.PerfilUsuarioRepository;
import com.arise.corp.shared.normalizacion.NormalizadorTexto;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Property 15 sobre PostgreSQL efimero (Requirements 5.1, 5.3, 5.4, 5.5, 9.6,
 * 12.1 y 24.7, CRM-01, ENG-03).
 *
 * <p>Siembra un conjunto determinista con tildes, mayusculas, documentos con
 * letras, cuentas activas e inactivas y contratos en los cuatro estados. Luego
 * ejecuta 120 combinaciones generadas de filtros y orden contra la API y las
 * compara con un modelo en memoria que reproduce las especificaciones JPA. El
 * orden generado usa solo claves con un orden estable en la colacion de
 * PostgreSQL (documentos, NIT, codigo, fechas y marcas de creacion); el orden
 * por nombre y razon social se comprueba aparte con un subconjunto ASCII.
 */
class ConsultasCrmIT extends PostgresIntegracionBase {

    private static final UUID ACTOR =
            UUID.fromString("00000000-0000-4000-8000-000000000201");
    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    private static final int CASOS_GENERADOS = 120;

    private static final String RUTA_CLIENTES = "/api/crm/clientes";
    private static final String RUTA_CUENTAS = "/api/crm/cuentas-corporativas";
    private static final String RUTA_CONTRATOS = "/api/crm/contratos";

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
    private ContratoRepository contratos;

    private RequestPostProcessor autenticacion;

    private final List<CuentaModelo> modeloCuentas = new ArrayList<>();
    private final List<ClienteModelo> modeloClientes = new ArrayList<>();
    private final List<ContratoModelo> modeloContratos = new ArrayList<>();
    private final List<CuentaCorporativa> entidadesCuentas = new ArrayList<>();
    private final List<Cliente> entidadesClientes = new ArrayList<>();

    @BeforeEach
    void sembrarDatos() {
        modeloCuentas.clear();
        modeloClientes.clear();
        modeloContratos.clear();
        entidadesCuentas.clear();
        entidadesClientes.clear();

        UUID comercial = crearUsuarioAuth();
        perfiles.saveAndFlush(PerfilUsuario.crear(
                comercial, RolUsuario.COMERCIAL, comercial, BASE));
        autenticacion = AutenticacionPerfilTestSupport.como(
                converter, comercial.toString(), "comercial@example.test");

        sembrarCuentas();
        sembrarClientes();
        sembrarContratos();
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 15: Filtros, búsqueda y orden coinciden con un modelo en memoria")
    void filtrosBusquedaYOrdenCoincidenConElModeloEnMemoria() throws Exception {
        Random azar = GeneradoresCrm.nuevoRandom();
        int casos = 0;

        for (int i = 0; i < CASOS_GENERADOS; i++) {
            switch (i % 3) {
                case 0 -> verificarClientes(consultaClientes(azar, i), i);
                case 1 -> verificarCuentas(consultaCuentas(azar, i), i);
                default -> verificarContratos(consultaContratos(azar, i), i);
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
            List<UUID> clientesEsperados = modeloClientes.stream()
                    .filter(cliente -> coincideTexto(
                            cliente.nombre(), cliente.numeroDocumento(), q))
                    .map(ClienteModelo::id)
                    .toList();
            List<UUID> cuentasEsperadas = modeloCuentas.stream()
                    .filter(cuenta -> coincideTexto(
                            cuenta.razonSocial(), cuenta.nit(), q))
                    .map(CuentaModelo::id)
                    .toList();

            assertFalse(clientesEsperados.isEmpty(),
                    () -> "el conjunto debe tener un cliente con " + q);
            assertFalse(cuentasEsperadas.isEmpty(),
                    () -> "el conjunto debe tener una cuenta con " + q);

            assertEquals(clientesEsperados,
                    ids(consultarClientes(q, "numeroDocumento,asc")),
                    () -> "q=" + q + " debe compararse literalmente en clientes");
            assertEquals(cuentasEsperadas,
                    ids(consultarCuentas(q, "nit,asc")),
                    () -> "q=" + q + " debe compararse literalmente en cuentas");
        }
    }

    @Test
    @DisplayName("El orden por nombre y razon social coincide en un subconjunto ASCII")
    void elOrdenPorNombreYPorRazonSocialCoincide() throws Exception {
        List<String> nombresAscendente = nombresDe(
                ids(consultarClientes("Orden", "nombre,asc")));
        assertEquals(
                List.of("Orden Alfa", "Orden Beta", "Orden Delta", "Orden Gamma"),
                nombresAscendente);

        List<String> nombresDescendente = nombresDe(
                ids(consultarClientes("Orden", "nombre,desc")));
        assertEquals(
                List.of("Orden Gamma", "Orden Delta", "Orden Beta", "Orden Alfa"),
                nombresDescendente);

        List<String> razonesAscendente = razonesDe(
                ids(consultarCuentas("Orden", "razonSocial,asc")));
        assertEquals(
                List.of("Orden Alfa S.A.S.", "Orden Beta S.A.S.",
                        "Orden Delta S.A.S.", "Orden Gamma S.A.S."),
                razonesAscendente);

        List<String> razonesDescendente = razonesDe(
                ids(consultarCuentas("Orden", "razonSocial,desc")));
        assertEquals(
                List.of("Orden Gamma S.A.S.", "Orden Delta S.A.S.",
                        "Orden Beta S.A.S.", "Orden Alfa S.A.S."),
                razonesDescendente);
    }

    private void sembrarCuentas() {
        String[] razonesSociales = {
                "Distribuidora Ávila S.A.S.",
                "CONSTRUCTORA Ñandú Ltda.",
                "Cien % Corporativo S.A.S.",
                "Guion_Bajo Corp.",
                "Barra \\ Invertida S.A.S.",
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

        for (int i = 0; i < razonesSociales.length; i++) {
            Instant creadoEn = BASE.plusSeconds(i * 60L);
            CuentaCorporativa cuenta = CuentaCorporativa.crear(
                    String.format("9%08d", i + 1),
                    razonesSociales[i],
                    "Contacto " + (i + 1),
                    "cuenta" + (i + 1) + "@example.test",
                    "3001234567",
                    ACTOR,
                    creadoEn);

            if (i % 3 == 1) {
                cuenta.cambiarEstado(false, ACTOR, creadoEn.plusSeconds(30));
            }

            cuentas.saveAndFlush(cuenta);
            entidadesCuentas.add(cuenta);
            modeloCuentas.add(new CuentaModelo(
                    cuenta.getId(),
                    cuenta.getNit(),
                    cuenta.getRazonSocial(),
                    cuenta.isActiva(),
                    creadoEn));
        }

        String[] razonesOrden = {
                "Orden Alfa S.A.S.",
                "Orden Beta S.A.S.",
                "Orden Gamma S.A.S.",
                "Orden Delta S.A.S."
        };

        for (int i = 0; i < razonesOrden.length; i++) {
            Instant creadoEn = BASE.plusSeconds(5_000 + i * 60L);
            CuentaCorporativa cuenta = CuentaCorporativa.crear(
                    String.format("8%08d", i + 1),
                    razonesOrden[i],
                    null,
                    null,
                    null,
                    ACTOR,
                    creadoEn);

            cuentas.saveAndFlush(cuenta);
            entidadesCuentas.add(cuenta);
            modeloCuentas.add(new CuentaModelo(
                    cuenta.getId(),
                    cuenta.getNit(),
                    cuenta.getRazonSocial(),
                    cuenta.isActiva(),
                    creadoEn));
        }
    }

    private void sembrarClientes() {
        String[] nombres = {
                "Ana María Rodríguez",
                "LUIS FERNANDO GÓMEZ",
                "María José Peña",
                "ANDRÉS CAMILO RUIZ",
                "Sofía Ángel",
                "JORGE ELIÉCER TAFUR",
                "Pedro Páramo",
                "Ñandú Pérez",
                "Mixed Case Name",
                "CARLOS ANDRÉS",
                "Cien % Real",
                "Guion_Bajo",
                "Barra \\ Invertida"
        };

        for (int i = 0; i < 18; i++) {
            boolean pasaporte = i % 4 == 0;
            TipoDocumento tipo = pasaporte ? TipoDocumento.PASAPORTE : TipoDocumento.CC;
            String documento = pasaporte
                    ? String.format("P%07d", i)
                    : String.format("%08d", 20_000_000 + i);
            CuentaCorporativa cuenta = i < 15
                    ? entidadesCuentas.get(i % 12)
                    : null;
            Instant creadoEn = BASE.plusSeconds(10_000 + i * 60L);

            Cliente cliente = Cliente.crear(
                    tipo,
                    documento,
                    nombres[i % nombres.length],
                    "cliente" + i + "@example.test",
                    "3001234567",
                    null,
                    null,
                    cuenta,
                    ACTOR,
                    creadoEn);

            if (i % 5 == 0) {
                cliente.cambiarEstado(false, ACTOR, creadoEn.plusSeconds(30));
            }

            clientes.saveAndFlush(cliente);
            entidadesClientes.add(cliente);
            modeloClientes.add(new ClienteModelo(
                    cliente.getId(),
                    documento,
                    cliente.getNombre(),
                    cliente.isActivo(),
                    cuenta == null ? null : cuenta.getId(),
                    creadoEn));
        }

        String[] nombresOrden = {"Orden Alfa", "Orden Beta", "Orden Gamma", "Orden Delta"};

        for (int i = 0; i < nombresOrden.length; i++) {
            Instant creadoEn = BASE.plusSeconds(20_000 + i * 60L);
            Cliente cliente = Cliente.crear(
                    TipoDocumento.CC,
                    String.format("%08d", 30_000_000 + i),
                    nombresOrden[i],
                    "orden" + i + "@example.test",
                    "3001234567",
                    null,
                    null,
                    entidadesCuentas.get(0),
                    ACTOR,
                    creadoEn);

            clientes.saveAndFlush(cliente);
            entidadesClientes.add(cliente);
            modeloClientes.add(new ClienteModelo(
                    cliente.getId(),
                    cliente.getNumeroDocumento(),
                    cliente.getNombre(),
                    cliente.isActivo(),
                    cliente.getCuentaCorporativa().getId(),
                    creadoEn));
        }
    }

    private void sembrarContratos() {
        EstadoContrato[] estados = EstadoContrato.values();
        BigDecimal valorBase = new BigDecimal("1000000.00");

        for (int i = 0; i < 30; i++) {
            Cliente cliente = entidadesClientes.get(i % entidadesClientes.size());
            String codigo = String.format("CTR-%04d", i + 1);
            LocalDate fechaInicio = LocalDate.of(2025, 1, 1).plusDays(i * 3L);
            Instant creadoEn = BASE.plusSeconds(30_000 + i * 60L);

            Contrato contrato = Contrato.crear(
                    cliente,
                    codigo,
                    "Contrato " + codigo,
                    fechaInicio,
                    fechaInicio.plusMonths(6),
                    valorBase.add(BigDecimal.valueOf(i * 1000L)),
                    ACTOR,
                    creadoEn);

            switch (estados[i % estados.length]) {
                case BORRADOR -> {
                    // El contrato nace en este estado.
                }
                case ACTIVO -> contrato.cambiarEstado(
                        EstadoContrato.ACTIVO, ACTOR, creadoEn.plusSeconds(1));
                case FINALIZADO -> {
                    contrato.cambiarEstado(
                            EstadoContrato.ACTIVO, ACTOR, creadoEn.plusSeconds(1));
                    contrato.cambiarEstado(
                            EstadoContrato.FINALIZADO, ACTOR, creadoEn.plusSeconds(2));
                }
                case CANCELADO -> contrato.cambiarEstado(
                        EstadoContrato.CANCELADO, ACTOR, creadoEn.plusSeconds(1));
            }

            contratos.saveAndFlush(contrato);
            modeloContratos.add(new ContratoModelo(
                    contrato.getId(),
                    codigo,
                    cliente.getId(),
                    contrato.getEstado(),
                    fechaInicio,
                    creadoEn));
        }
    }

    private ConsultaClientes consultaClientes(Random azar, int i) {
        int variante = i / 3;
        String q = switch (variante % 14) {
            case 0 -> "%";
            case 1 -> "_";
            case 2 -> "\\";
            case 3 -> null;
            case 4 -> "";
            case 5 -> "   ";
            case 6 -> "  Ana  ";
            case 7 -> "ana";
            case 8 -> "MARÍA";
            case 9 -> "peña";
            case 10 -> "2000";
            case 11 -> "P00";
            case 12 -> "zzz";
            default -> GeneradoresCrm.cadena(azar, 1 + azar.nextInt(6))
                    .toLowerCase(Locale.ROOT);
        };
        Boolean activo = switch (variante % 3) {
            case 0 -> null;
            case 1 -> Boolean.TRUE;
            default -> Boolean.FALSE;
        };
        UUID cuentaId = switch (variante % 4) {
            case 0 -> null;
            case 1 -> modeloCuentas.get(variante % modeloCuentas.size()).id();
            case 2 -> UUID.randomUUID();
            default -> modeloCuentas.get(1).id();
        };
        String campo = (variante / 4) % 2 == 0 ? "numeroDocumento" : "creadoEn";
        boolean descendente = (variante / 2) % 2 == 1;

        return new ConsultaClientes(q, activo, cuentaId, campo, descendente);
    }

    private ConsultaCuentas consultaCuentas(Random azar, int i) {
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
        Boolean activa = switch (variante % 3) {
            case 0 -> null;
            case 1 -> Boolean.TRUE;
            default -> Boolean.FALSE;
        };
        String campo = (variante / 3) % 2 == 0 ? "nit" : "creadoEn";
        boolean descendente = (variante / 2) % 2 == 1;

        return new ConsultaCuentas(q, activa, campo, descendente);
    }

    private ConsultaContratos consultaContratos(Random azar, int i) {
        int variante = i / 3;
        UUID clienteId = switch (variante % 4) {
            case 0 -> null;
            case 1 -> modeloClientes.get(variante % modeloClientes.size()).id();
            case 2 -> UUID.randomUUID();
            default -> entidadesClientes.get(0).getId();
        };
        EstadoContrato estado = switch (variante % 6) {
            case 0 -> null;
            case 1 -> EstadoContrato.BORRADOR;
            case 2 -> EstadoContrato.ACTIVO;
            case 3 -> EstadoContrato.FINALIZADO;
            case 4 -> EstadoContrato.CANCELADO;
            default -> null;
        };
        String campo = switch (variante % 3) {
            case 0 -> "codigo";
            case 1 -> "fechaInicio";
            default -> "creadoEn";
        };
        boolean descendente = (variante / 3) % 2 == 1;

        return new ConsultaContratos(clienteId, estado, campo, descendente);
    }

    private void verificarClientes(ConsultaClientes consulta, int caso) throws Exception {
        MockHttpServletRequestBuilder peticion = get(RUTA_CLIENTES)
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
        if (consulta.cuentaId() != null) {
            peticion.param("cuentaCorporativaId", consulta.cuentaId().toString());
        }

        MvcResult resultado = mvc.perform(peticion.with(autenticacion))
                .andExpect(status().isOk())
                .andReturn();

        List<UUID> esperados = modeloClientes.stream()
                .filter(cliente -> coincideCliente(cliente, consulta))
                .sorted(comparadorClientes(consulta))
                .map(ClienteModelo::id)
                .toList();

        assertEquals(esperados, ids(resultado),
                () -> "caso " + caso + " de clientes: " + consulta);
    }

    private void verificarCuentas(ConsultaCuentas consulta, int caso) throws Exception {
        MockHttpServletRequestBuilder peticion = get(RUTA_CUENTAS)
                .param("page", "0")
                .param("size", "100")
                .param("sort", consulta.campoOrden() + ","
                        + (consulta.descendente() ? "desc" : "asc"));

        if (consulta.q() != null) {
            peticion.param("q", consulta.q());
        }
        if (consulta.activa() != null) {
            peticion.param("activa", consulta.activa().toString());
        }

        MvcResult resultado = mvc.perform(peticion.with(autenticacion))
                .andExpect(status().isOk())
                .andReturn();

        List<UUID> esperados = modeloCuentas.stream()
                .filter(cuenta -> coincideCuenta(cuenta, consulta))
                .sorted(comparadorCuentas(consulta))
                .map(CuentaModelo::id)
                .toList();

        assertEquals(esperados, ids(resultado),
                () -> "caso " + caso + " de cuentas: " + consulta);
    }

    private void verificarContratos(ConsultaContratos consulta, int caso) throws Exception {
        MockHttpServletRequestBuilder peticion = get(RUTA_CONTRATOS)
                .param("page", "0")
                .param("size", "100")
                .param("sort", consulta.campoOrden() + ","
                        + (consulta.descendente() ? "desc" : "asc"));

        if (consulta.clienteId() != null) {
            peticion.param("clienteId", consulta.clienteId().toString());
        }
        if (consulta.estado() != null) {
            peticion.param("estado", consulta.estado().name());
        }

        MvcResult resultado = mvc.perform(peticion.with(autenticacion))
                .andExpect(status().isOk())
                .andReturn();

        List<UUID> esperados = modeloContratos.stream()
                .filter(contrato -> coincideContrato(contrato, consulta))
                .sorted(comparadorContratos(consulta))
                .map(ContratoModelo::id)
                .toList();

        assertEquals(esperados, ids(resultado),
                () -> "caso " + caso + " de contratos: " + consulta);
    }

    private MvcResult consultarClientes(String q, String sort) throws Exception {
        return mvc.perform(get(RUTA_CLIENTES)
                        .param("page", "0")
                        .param("size", "100")
                        .param("sort", sort)
                        .param("q", q)
                        .with(autenticacion))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult consultarCuentas(String q, String sort) throws Exception {
        return mvc.perform(get(RUTA_CUENTAS)
                        .param("page", "0")
                        .param("size", "100")
                        .param("sort", sort)
                        .param("q", q)
                        .with(autenticacion))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static boolean coincideCliente(ClienteModelo cliente, ConsultaClientes consulta) {
        if (!coincideTexto(cliente.nombre(), cliente.numeroDocumento(), consulta.q())) {
            return false;
        }
        if (consulta.activo() != null && cliente.activo() != consulta.activo()) {
            return false;
        }
        return consulta.cuentaId() == null
                || consulta.cuentaId().equals(cliente.cuentaId());
    }

    private static boolean coincideCuenta(CuentaModelo cuenta, ConsultaCuentas consulta) {
        if (!coincideTexto(cuenta.razonSocial(), cuenta.nit(), consulta.q())) {
            return false;
        }
        return consulta.activa() == null || cuenta.activa() == consulta.activa();
    }

    private static boolean coincideContrato(ContratoModelo contrato, ConsultaContratos consulta) {
        if (consulta.clienteId() != null
                && !consulta.clienteId().equals(contrato.clienteId())) {
            return false;
        }
        return consulta.estado() == null || consulta.estado() == contrato.estado();
    }

    /**
     * Reproduce {@code EspecificacionesCrm}: texto recortado, nombre o razon
     * social sin distinguir mayusculas y documento normalizado. Los comodines
     * se escaparon en SQL, asi que aqui se comparan como texto literal.
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

    private static Comparator<ClienteModelo> comparadorClientes(ConsultaClientes consulta) {
        Comparator<ClienteModelo> comparador = switch (consulta.campoOrden()) {
            case "numeroDocumento" -> Comparator.comparing(ClienteModelo::numeroDocumento);
            case "creadoEn" -> Comparator.comparing(ClienteModelo::creadoEn);
            default -> throw new IllegalArgumentException(
                    "Campo de orden de clientes no soportado: " + consulta.campoOrden());
        };
        return consulta.descendente() ? comparador.reversed() : comparador;
    }

    private static Comparator<CuentaModelo> comparadorCuentas(ConsultaCuentas consulta) {
        Comparator<CuentaModelo> comparador = switch (consulta.campoOrden()) {
            case "nit" -> Comparator.comparing(CuentaModelo::nit);
            case "creadoEn" -> Comparator.comparing(CuentaModelo::creadoEn);
            default -> throw new IllegalArgumentException(
                    "Campo de orden de cuentas no soportado: " + consulta.campoOrden());
        };
        return consulta.descendente() ? comparador.reversed() : comparador;
    }

    private static Comparator<ContratoModelo> comparadorContratos(ConsultaContratos consulta) {
        Comparator<ContratoModelo> comparador = switch (consulta.campoOrden()) {
            case "codigo" -> Comparator.comparing(ContratoModelo::codigo);
            case "fechaInicio" -> Comparator.comparing(ContratoModelo::fechaInicio);
            case "creadoEn" -> Comparator.comparing(ContratoModelo::creadoEn);
            default -> throw new IllegalArgumentException(
                    "Campo de orden de contratos no soportado: " + consulta.campoOrden());
        };
        return consulta.descendente() ? comparador.reversed() : comparador;
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

    private List<String> nombresDe(List<UUID> ids) {
        return ids.stream()
                .map(id -> modeloClientes.stream()
                        .filter(cliente -> cliente.id().equals(id))
                        .findFirst()
                        .orElseThrow())
                .map(ClienteModelo::nombre)
                .toList();
    }

    private List<String> razonesDe(List<UUID> ids) {
        return ids.stream()
                .map(id -> modeloCuentas.stream()
                        .filter(cuenta -> cuenta.id().equals(id))
                        .findFirst()
                        .orElseThrow())
                .map(CuentaModelo::razonSocial)
                .toList();
    }

    private record CuentaModelo(
            UUID id,
            String nit,
            String razonSocial,
            boolean activa,
            Instant creadoEn) {
    }

    private record ClienteModelo(
            UUID id,
            String numeroDocumento,
            String nombre,
            boolean activo,
            UUID cuentaId,
            Instant creadoEn) {
    }

    private record ContratoModelo(
            UUID id,
            String codigo,
            UUID clienteId,
            EstadoContrato estado,
            LocalDate fechaInicio,
            Instant creadoEn) {
    }

    private record ConsultaClientes(
            String q,
            Boolean activo,
            UUID cuentaId,
            String campoOrden,
            boolean descendente) {
    }

    private record ConsultaCuentas(
            String q,
            Boolean activa,
            String campoOrden,
            boolean descendente) {
    }

    private record ConsultaContratos(
            UUID clienteId,
            EstadoContrato estado,
            String campoOrden,
            boolean descendente) {
    }
}
