package com.arise.corp.crm;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.arise.corp.config.PerfilJwtAuthenticationConverter;
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

/**
 * Datos ficticios para las pruebas MockMvc del CRM (Requirement 16.1–16.5,
 * 23.4–23.6).
 *
 * <p>Los tests lo importan con {@code @Import(CrmDatosPrueba.class)} y llaman a
 * {@link #limpiar()} en {@code @BeforeEach}. La autenticacion se construye con
 * {@link AutenticacionPerfilTestSupport#como} sobre el
 * {@link PerfilJwtAuthenticationConverter} real y perfiles persistidos en H2,
 * sin tokens de Supabase. Ningun dato es real.
 */
public class CrmDatosPrueba {

    public static final UUID ACTOR_ID =
            UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    public static final Instant INSTANTE = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDate FECHA_INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate FECHA_FIN = LocalDate.of(2026, 12, 31);
    private static final BigDecimal VALOR = new BigDecimal("1234.50");

    private final PerfilUsuarioRepository perfiles;
    private final CuentaCorporativaRepository cuentas;
    private final ClienteRepository clientes;
    private final ContratoRepository contratos;
    private final JdbcTemplate jdbc;
    private final PerfilJwtAuthenticationConverter converter;
    private final AtomicInteger secuencia = new AtomicInteger();

    public CrmDatosPrueba(
            PerfilUsuarioRepository perfiles,
            CuentaCorporativaRepository cuentas,
            ClienteRepository clientes,
            ContratoRepository contratos,
            JdbcTemplate jdbc,
            PerfilJwtAuthenticationConverter converter) {

        this.perfiles = perfiles;
        this.cuentas = cuentas;
        this.clientes = clientes;
        this.contratos = contratos;
        this.jdbc = jdbc;
        this.converter = converter;
    }

    /**
     * Vacia las tablas en orden de dependencias. Se llama en {@code @BeforeEach}.
     */
    public void limpiar() {
        contratos.deleteAll();
        clientes.deleteAll();
        cuentas.deleteAll();
        perfiles.deleteAll();
        jdbc.update("delete from auth.users");
        secuencia.set(0);
    }

    public UUID crearPerfil(RolUsuario rol) {
        return crearPerfil(rol, true);
    }

    public UUID crearPerfil(RolUsuario rol, boolean activo) {
        UUID usuarioId = siguienteUuid();
        perfiles.save(new PerfilUsuario(
                usuarioId, rol, activo, INSTANTE, INSTANTE, null));
        return usuarioId;
    }

    public RequestPostProcessor autenticacionDe(UUID usuarioId) {
        return AutenticacionPerfilTestSupport.como(
                converter, usuarioId.toString(), "persona@arise.test");
    }

    public CuentaCorporativa crearCuenta() {
        return crearCuenta(true);
    }

    public CuentaCorporativa crearCuenta(boolean activa) {
        int numero = secuencia.incrementAndGet();
        return crearCuenta(String.format("9%09d", numero), activa);
    }

    public CuentaCorporativa crearCuenta(String nit, boolean activa) {
        CuentaCorporativa cuenta = CuentaCorporativa.crear(
                nit,
                "Cuenta ficticia " + nit,
                "Contacto ficticio",
                "contacto" + nit + "@example.test",
                "3001234567",
                ACTOR_ID,
                INSTANTE);

        if (!activa) {
            cuenta.cambiarEstado(false, ACTOR_ID, INSTANTE);
        }

        return cuentas.save(cuenta);
    }

    public Cliente crearCliente(CuentaCorporativa cuenta) {
        return crearCliente(true, cuenta);
    }

    public Cliente crearCliente(boolean activo, CuentaCorporativa cuenta) {
        return crearCliente(siguienteDocumento(), activo, cuenta);
    }

    public Cliente crearCliente(
            String documento, boolean activo, CuentaCorporativa cuenta) {

        return crearCliente(documento, "Cliente ficticio " + documento, activo, cuenta);
    }

    public Cliente crearCliente(String documento, String nombre, boolean activo,
            CuentaCorporativa cuenta) {

        return crearCliente(documento, nombre, "cliente" + documento + "@example.test",
                "3001234567", "Calle ficticia 123", "Bogota", activo, cuenta);
    }

    public Cliente crearCliente(
            String documento,
            String nombre,
            String correo,
            String telefono,
            String direccion,
            String ciudad,
            boolean activo,
            CuentaCorporativa cuenta) {

        Cliente cliente = Cliente.crear(
                TipoDocumento.CC,
                documento,
                nombre,
                correo,
                telefono,
                direccion,
                ciudad,
                cuenta,
                ACTOR_ID,
                INSTANTE);

        if (!activo) {
            cliente.cambiarEstado(false, ACTOR_ID, INSTANTE);
        }

        return clientes.save(cliente);
    }

    public Contrato crearContrato(Cliente cliente) {
        return crearContrato(cliente, EstadoContrato.BORRADOR);
    }

    public Contrato crearContrato(Cliente cliente, EstadoContrato estado) {
        return crearContrato(siguienteCodigo(), cliente, estado);
    }

    public Contrato crearContrato(
            String codigo,
            Cliente cliente,
            EstadoContrato estado) {

        Contrato contrato = Contrato.crear(
                cliente,
                codigo,
                "Contrato ficticio " + codigo,
                FECHA_INICIO,
                FECHA_FIN,
                VALOR,
                ACTOR_ID,
                INSTANTE);

        switch (estado) {
            case BORRADOR -> {
                // Estado inicial de la fabrica.
            }
            case ACTIVO -> contrato.cambiarEstado(
                    EstadoContrato.ACTIVO, ACTOR_ID, INSTANTE);
            case FINALIZADO -> {
                contrato.cambiarEstado(EstadoContrato.ACTIVO, ACTOR_ID, INSTANTE);
                contrato.cambiarEstado(
                        EstadoContrato.FINALIZADO, ACTOR_ID, INSTANTE);
            }
            case CANCELADO -> contrato.cambiarEstado(
                    EstadoContrato.CANCELADO, ACTOR_ID, INSTANTE);
        }

        return contratos.save(contrato);
    }

    public String siguienteDocumento() {
        return String.format("10%08d", secuencia.incrementAndGet());
    }

    public String siguienteCodigo() {
        return String.format("CTR-FICT-%04d", secuencia.incrementAndGet());
    }

    public static BigDecimal valor() {
        return VALOR;
    }

    public static LocalDate fechaInicio() {
        return FECHA_INICIO;
    }

    public static LocalDate fechaFin() {
        return FECHA_FIN;
    }

    private UUID siguienteUuid() {
        long numero = secuencia.incrementAndGet();
        return new UUID(
                ACTOR_ID.getMostSignificantBits(),
                ACTOR_ID.getLeastSignificantBits() + numero);
    }
}
