package com.arise.corp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;

/**
 * Verifica la migracion del CRM sobre PostgreSQL efimero (Requirement 2).
 *
 * <p>Cubre el contrato que H2 no puede comprobar: el esquema real que produce
 * el SQL versionado, los tipos de columna, las restricciones con su SQLState de
 * PostgreSQL y la seguridad de RLS y privilegios de {@code anon} y
 * {@code authenticated}.
 */
class MigracionesCrmIT extends PostgresIntegracionBase {

    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private static final List<String> TABLAS_DEL_CRM = List.of("cuentas_corporativas", "clientes", "contratos");

    @Autowired
    private Environment entorno;

    @Test
    @DisplayName("El contexto arranca con ddl-auto=validate sobre el esquema migrado")
    void validaElEsquemaConDdlAutoValidate() {
        assertEquals("validate", entorno.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("app", entorno.getProperty("spring.jpa.properties.hibernate.default_schema"));
    }

    @Test
    @DisplayName("Las migraciones solo crean estructura y dejan las tablas vacias")
    void lasTablasDelCrmQuedanVaciasTrasMigrar() throws Exception {
        for (Path archivo : MigracionesSupabase.archivosDeMigracion()) {
            String sql = Files.readString(archivo, StandardCharsets.UTF_8);
            assertFalse(
                    sql.matches("(?is).*\\binsert\\s+into\\b.*"),
                    () -> archivo.getFileName() + " no debe insertar datos");
        }

        assertEquals(0, contar("app.cuentas_corporativas"));
        assertEquals(0, contar("app.clientes"));
        assertEquals(0, contar("app.contratos"));
    }

    @Test
    @DisplayName("information_schema confirma los tipos y la ausencia de la cuenta en contratos")
    void informationSchemaConfirmaLosTiposDelDiseno() {
        assertEquals("uuid", tipoDe("cuentas_corporativas", "id"));
        assertEquals("uuid", tipoDe("cuentas_corporativas", "actualizado_por"));
        assertEquals("timestamptz", tipoDe("cuentas_corporativas", "creado_en"));
        assertEquals("timestamptz", tipoDe("cuentas_corporativas", "actualizado_en"));
        assertEquals("bool", tipoDe("cuentas_corporativas", "activa"));

        assertEquals("uuid", tipoDe("clientes", "cuenta_corporativa_id"));
        assertEquals("timestamptz", tipoDe("clientes", "creado_en"));
        assertEquals("bool", tipoDe("clientes", "activo"));

        assertEquals("uuid", tipoDe("contratos", "cliente_id"));
        assertEquals("date", tipoDe("contratos", "fecha_inicio"));
        assertEquals("date", tipoDe("contratos", "fecha_fin"));
        assertEquals("timestamptz", tipoDe("contratos", "estado_actualizado_en"));
        assertEquals("text", tipoDe("contratos", "estado"));

        Map<String, Object> valor = jdbc.queryForMap(
                "select data_type, numeric_precision, numeric_scale from information_schema.columns"
                        + " where table_schema = 'app' and table_name = 'contratos' and column_name = 'valor'");
        assertEquals("numeric", valor.get("data_type"));
        assertEquals(15, ((Number) valor.get("numeric_precision")).intValue());
        assertEquals(2, ((Number) valor.get("numeric_scale")).intValue());

        assertTrue(existeColumna("clientes", "cuenta_corporativa_id"));
        assertFalse(existeColumna("contratos", "cuenta_corporativa_id"),
                "la cuenta corporativa se obtiene a traves del cliente");
    }

    @Test
    @DisplayName("La unicidad de NIT, documento y codigo falla con SQLState 23505")
    void lasRestriccionesDeUnicidadFallanConSqlState23505() {
        insertarCuenta("900123456");
        assertEquals("23505", sqlStateDe(() -> insertarCuenta("900123456")));

        insertarCliente("CC", "10234567");
        assertEquals("23505", sqlStateDe(() -> insertarCliente("CC", "10234567")));

        UUID clienteId = insertarCliente("PASAPORTE", "AB12345");
        insertarContrato(clienteId, "CT-001");
        assertEquals("23505", sqlStateDe(() -> insertarContrato(clienteId, "CT-001")));
    }

    @Test
    @DisplayName("Los CHECK de tipo, formato, estado, valor y fechas fallan con SQLState 23514")
    void lasRestriccionesDeComprobacionFallanConSqlState23514() {
        assertEquals("23514", sqlStateDe(() -> insertarCliente("XX", "10234567")));
        assertEquals("23514", sqlStateDe(() -> insertarCliente("CC", "ABC")));

        UUID clienteId = insertarCliente("CC", "10234567");
        assertEquals("23514", sqlStateDe(() -> insertarContratoConEstado(clienteId, "CT-001", "PENDIENTE")));
        assertEquals("23514", sqlStateDe(() -> insertarContratoConValor(clienteId, "CT-002", new BigDecimal("-1"))));
        assertEquals("23514", sqlStateDe(() -> insertarContratoConFechas(
                clienteId, "CT-003", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 1))));
    }

    @Test
    @DisplayName("Las claves foraneas de cuenta y de cliente fallan con SQLState 23503")
    void lasClavesForaneasFallanConSqlState23503() {
        UUID inexistente = UUID.randomUUID();

        assertEquals("23503", sqlStateDe(() -> jdbc.update("""
                insert into app.clientes (tipo_documento, numero_documento, nombre, correo, telefono,
                    cuenta_corporativa_id, actualizado_por)
                values ('CC', '10234567', 'Cliente', 'cliente@example.com', '3001234567', ?, ?)
                """, inexistente, ACTOR)));

        assertEquals("23503", sqlStateDe(() -> insertarContrato(inexistente, "CT-001")));
    }

    @Test
    @DisplayName("Las tres tablas tienen RLS y ningun privilegio para anon ni authenticated")
    void lasTablasTienenRlsYSinPrivilegiosParaLosRolesDeCliente() {
        for (String tabla : TABLAS_DEL_CRM) {
            Boolean rls = jdbc.queryForObject(
                    "select relrowsecurity from pg_class where oid = ?::regclass", Boolean.class, "app." + tabla);
            assertTrue(Boolean.TRUE.equals(rls), () -> tabla + " debe tener RLS activo");

            for (String rol : List.of("anon", "authenticated")) {
                for (String privilegio : List.of("select", "insert", "update", "delete")) {
                    Boolean concedido = jdbc.queryForObject(
                            "select has_table_privilege(?, ?, ?)", Boolean.class, rol, "app." + tabla, privilegio);
                    assertFalse(Boolean.TRUE.equals(concedido),
                            () -> rol + " no debe tener " + privilegio + " sobre app." + tabla);
                }
            }
        }
    }

    private long contar(String tabla) {
        Long total = jdbc.queryForObject("select count(*) from " + tabla, Long.class);
        assertNotNull(total);
        return total;
    }

    private String tipoDe(String tabla, String columna) {
        return jdbc.queryForObject(
                "select udt_name from information_schema.columns"
                        + " where table_schema = 'app' and table_name = ? and column_name = ?",
                String.class,
                tabla,
                columna);
    }

    private boolean existeColumna(String tabla, String columna) {
        Integer total = jdbc.queryForObject(
                "select count(*) from information_schema.columns"
                        + " where table_schema = 'app' and table_name = ? and column_name = ?",
                Integer.class,
                tabla,
                columna);
        return total != null && total > 0;
    }

    private String sqlStateDe(Executable sentencia) {
        DataAccessException fallo = assertThrows(DataAccessException.class, sentencia);
        Throwable causa = fallo;
        while (causa != null) {
            if (causa instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            causa = causa.getCause();
        }
        fail("La excepcion no contiene un SQLException con SQLState");
        return null;
    }

    private void insertarCuenta(String nit) {
        jdbc.update("""
                insert into app.cuentas_corporativas (nit, razon_social, actualizado_por)
                values (?, 'Arise Corp.', ?)
                """, nit, ACTOR);
    }

    private UUID insertarCliente(String tipoDocumento, String numeroDocumento) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into app.clientes (id, tipo_documento, numero_documento, nombre, correo, telefono,
                    actualizado_por)
                values (?, ?, ?, 'Cliente de prueba', 'cliente@example.com', '3001234567', ?)
                """, id, tipoDocumento, numeroDocumento, ACTOR);
        return id;
    }

    private void insertarContrato(UUID clienteId, String codigo) {
        insertarContratoConEstado(clienteId, codigo, "BORRADOR");
    }

    private void insertarContratoConEstado(UUID clienteId, String codigo, String estado) {
        jdbc.update("""
                insert into app.contratos (cliente_id, codigo, descripcion, fecha_inicio, valor, estado,
                    estado_actualizado_por, actualizado_por)
                values (?, ?, 'Contrato de prueba', date '2026-01-01', 1500000.00, ?, ?, ?)
                """, clienteId, codigo, estado, ACTOR, ACTOR);
    }

    private void insertarContratoConValor(UUID clienteId, String codigo, BigDecimal valor) {
        jdbc.update("""
                insert into app.contratos (cliente_id, codigo, descripcion, fecha_inicio, valor,
                    estado_actualizado_por, actualizado_por)
                values (?, ?, 'Contrato de prueba', date '2026-01-01', ?, ?, ?)
                """, clienteId, codigo, valor, ACTOR, ACTOR);
    }

    private void insertarContratoConFechas(UUID clienteId, String codigo, LocalDate inicio, LocalDate fin) {
        jdbc.update("""
                insert into app.contratos (cliente_id, codigo, descripcion, fecha_inicio, fecha_fin, valor,
                    estado_actualizado_por, actualizado_por)
                values (?, ?, 'Contrato de prueba', ?, ?, 1500000.00, ?, ?)
                """, clienteId, codigo, java.sql.Date.valueOf(inicio), java.sql.Date.valueOf(fin), ACTOR, ACTOR);
    }
}
