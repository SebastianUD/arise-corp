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
 * Verifica la migracion del ERP sobre PostgreSQL efimero (Requirements 2,
 * 26.1–26.4, DP-SEG-05).
 *
 * <p>Cubre el contrato que H2 no puede comprobar: {@code ddl-auto=validate}
 * sobre el esquema migrado, los tipos de columna, las restricciones con su
 * SQLState de PostgreSQL, la coherencia tipo-relacion y la seguridad de RLS y
 * privilegios de {@code anon} y {@code authenticated}.
 */
class MigracionesErpIT extends PostgresIntegracionBase {

    private static final UUID ACTOR =
            UUID.fromString("00000000-0000-4000-8000-000000000001");

    private static final List<String> TABLAS_DEL_ERP = List.of(
            "empleados", "proveedores", "movimientos_financieros");

    @Autowired
    private Environment entorno;

    @Test
    @DisplayName("El contexto arranca con ddl-auto=validate sobre el esquema migrado")
    void validaElEsquemaConDdlAutoValidate() {
        assertEquals("validate", entorno.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("app", entorno.getProperty("spring.jpa.properties.hibernate.default_schema"));
    }

    @Test
    @DisplayName("Las migraciones solo crean estructura y dejan las tablas del ERP vacias")
    void lasTablasDelErpQuedanVaciasTrasMigrar() throws Exception {
        for (Path archivo : MigracionesSupabase.archivosDeMigracion()) {
            String sql = Files.readString(archivo, StandardCharsets.UTF_8);
            assertFalse(
                    sql.matches("(?is).*\\binsert\\s+into\\b.*"),
                    () -> archivo.getFileName() + " no debe insertar datos");
        }

        for (String tabla : TABLAS_DEL_ERP) {
            assertEquals(0, contar("app." + tabla));
        }
    }

    @Test
    @DisplayName("information_schema confirma uuid, date, numeric(15,2), timestamptz y text")
    void informationSchemaConfirmaLosTiposDelDiseno() {
        assertEquals("uuid", tipoDe("empleados", "id"));
        assertEquals("uuid", tipoDe("empleados", "actualizado_por"));
        assertEquals("date", tipoDe("empleados", "fecha_ingreso"));
        assertEquals("timestamptz", tipoDe("empleados", "creado_en"));
        assertEquals("text", tipoDe("empleados", "tipo_documento"));

        assertEquals("uuid", tipoDe("proveedores", "id"));
        assertEquals("bool", tipoDe("proveedores", "activo"));
        assertEquals("timestamptz", tipoDe("proveedores", "actualizado_en"));

        assertEquals("date", tipoDe("movimientos_financieros", "fecha"));
        assertEquals("uuid", tipoDe("movimientos_financieros", "cliente_id"));
        assertEquals("uuid", tipoDe("movimientos_financieros", "proveedor_id"));
        assertEquals("timestamptz", tipoDe("movimientos_financieros", "estado_actualizado_en"));
        assertEquals("text", tipoDe("movimientos_financieros", "estado"));

        Map<String, Object> valor = jdbc.queryForMap(
                "select data_type, numeric_precision, numeric_scale from information_schema.columns"
                        + " where table_schema = 'app' and table_name = 'movimientos_financieros'"
                        + " and column_name = 'valor'");
        assertEquals("numeric", valor.get("data_type"));
        assertEquals(15, ((Number) valor.get("numeric_precision")).intValue());
        assertEquals(2, ((Number) valor.get("numeric_scale")).intValue());
    }

    @Test
    @DisplayName("Los documentos duplicados de empleado y proveedor fallan con SQLState 23505")
    void losDocumentosDuplicadosFallanConSqlState23505() {
        insertarEmpleado("CC", "10234567", "Empleado de prueba");
        assertEquals("23505", sqlStateDe(
                () -> insertarEmpleado("CC", "10234567", "Otro empleado")));

        insertarProveedor("NIT", "900765432", "Proveedor de prueba");
        assertEquals("23505", sqlStateDe(
                () -> insertarProveedor("NIT", "900765432", "Otro proveedor")));
    }

    @Test
    @DisplayName("Los CHECK de documento, tipo, estado, valor, concepto y referencia fallan con 23514")
    void lasRestriccionesDeComprobacionFallanConSqlState23514() {
        assertEquals("23514", sqlStateDe(
                () -> insertarEmpleado("XX", "10234567", "Empleado")));
        assertEquals("23514", sqlStateDe(
                () -> insertarEmpleado("CC", "ABC123", "Empleado")));
        assertEquals("23514", sqlStateDe(
                () -> insertarEmpleado("CE", "AB", "Empleado")));
        assertEquals("23514", sqlStateDe(
                () -> insertarEmpleado("CE", "AB1234", "x".repeat(201))));

        assertEquals("23514", sqlStateDe(
                () -> insertarProveedor("XX", "900765432", "Proveedor")));
        assertEquals("23514", sqlStateDe(
                () -> insertarProveedor("NIT", "ABC123", "Proveedor")));
        assertEquals("23514", sqlStateDe(
                () -> insertarProveedor("CE", "AB1234", "x".repeat(201))));

        assertEquals("23514", sqlStateDe(
                () -> insertarMovimiento("XX", null, null,
                        new BigDecimal("10.00"), "Concepto", null, "PENDIENTE")));
        assertEquals("23514", sqlStateDe(
                () -> insertarMovimiento("INGRESO", null, null,
                        new BigDecimal("10.00"), "Concepto", null, "PAUSADO")));
        assertEquals("23514", sqlStateDe(
                () -> insertarMovimiento("INGRESO", null, null,
                        BigDecimal.ZERO, "Concepto", null, "PENDIENTE")));
        assertEquals("23514", sqlStateDe(
                () -> insertarMovimiento("INGRESO", null, null,
                        new BigDecimal("-1.00"), "Concepto", null, "PENDIENTE")));
        assertEquals("23514", sqlStateDe(
                () -> insertarMovimiento("INGRESO", null, null,
                        new BigDecimal("10.00"), "ab", null, "PENDIENTE")));
        assertEquals("23514", sqlStateDe(
                () -> insertarMovimiento("INGRESO", null, null,
                        new BigDecimal("10.00"), "Concepto", "fac-1", "PENDIENTE")));
    }

    @Test
    @DisplayName("La relacion incoherente con el tipo falla con SQLState 23514")
    void laRelacionIncoherenteConElTipoFallaConSqlState23514() {
        UUID clienteId = insertarCliente("CC", "10234567");
        UUID proveedorId = insertarProveedor("NIT", "900765432", "Proveedor");

        assertEquals("23514", sqlStateDe(
                () -> insertarMovimiento("EGRESO", clienteId, null,
                        new BigDecimal("10.00"), "Concepto", null, "PENDIENTE")));
        assertEquals("23514", sqlStateDe(
                () -> insertarMovimiento("INGRESO", null, proveedorId,
                        new BigDecimal("10.00"), "Concepto", null, "PENDIENTE")));

        // La combinacion coherente si se inserta.
        insertarMovimiento("INGRESO", clienteId, null,
                new BigDecimal("10.00"), "Concepto", null, "PENDIENTE");
        insertarMovimiento("EGRESO", null, proveedorId,
                new BigDecimal("10.00"), "Concepto", null, "PENDIENTE");
    }

    @Test
    @DisplayName("Las claves foraneas de cliente y proveedor fallan con SQLState 23503")
    void lasClavesForaneasFallanConSqlState23503() {
        assertEquals("23503", sqlStateDe(
                () -> insertarMovimiento("INGRESO", UUID.randomUUID(), null,
                        new BigDecimal("10.00"), "Concepto", null, "PENDIENTE")));
        assertEquals("23503", sqlStateDe(
                () -> insertarMovimiento("EGRESO", null, UUID.randomUUID(),
                        new BigDecimal("10.00"), "Concepto", null, "PENDIENTE")));
    }

    @Test
    @DisplayName("Las tres tablas tienen RLS y ningun privilegio para anon ni authenticated")
    void lasTablasTienenRlsYSinPrivilegiosParaLosRolesDeCliente() {
        for (String tabla : TABLAS_DEL_ERP) {
            Boolean rls = jdbc.queryForObject(
                    "select relrowsecurity from pg_class where oid = ?::regclass",
                    Boolean.class, "app." + tabla);
            assertTrue(Boolean.TRUE.equals(rls), () -> tabla + " debe tener RLS activo");

            for (String rol : List.of("anon", "authenticated")) {
                for (String privilegio : List.of("select", "insert", "update", "delete")) {
                    Boolean concedido = jdbc.queryForObject(
                            "select has_table_privilege(?, ?, ?)",
                            Boolean.class, rol, "app." + tabla, privilegio);
                    assertFalse(Boolean.TRUE.equals(concedido),
                            () -> rol + " no debe tener " + privilegio
                                    + " sobre app." + tabla);
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
                String.class, tabla, columna);
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

    private UUID insertarCliente(String tipoDocumento, String numeroDocumento) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into app.clientes (id, tipo_documento, numero_documento, nombre, correo,
                    telefono, actualizado_por)
                values (?, ?, ?, 'Cliente de prueba', 'cliente@example.com', '3001234567', ?)
                """, id, tipoDocumento, numeroDocumento, ACTOR);
        return id;
    }

    private void insertarEmpleado(String tipoDocumento, String numeroDocumento, String nombre) {
        jdbc.update("""
                insert into app.empleados (tipo_documento, numero_documento, nombre, cargo, area,
                    correo, telefono, fecha_ingreso, actualizado_por)
                values (?, ?, ?, 'Cargo', 'Area', 'empleado@example.com', '3001234567',
                    date '2025-03-01', ?)
                """, tipoDocumento, numeroDocumento, nombre, ACTOR);
    }

    private UUID insertarProveedor(String tipoDocumento, String numeroDocumento, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into app.proveedores (id, tipo_documento, numero_documento, nombre, correo,
                    telefono, actualizado_por)
                values (?, ?, ?, ?, 'proveedor@example.com', '6010000000', ?)
                """, id, tipoDocumento, numeroDocumento, nombre, ACTOR);
        return id;
    }

    private void insertarMovimiento(
            String tipo,
            UUID clienteId,
            UUID proveedorId,
            BigDecimal valor,
            String concepto,
            String referencia,
            String estado) {

        jdbc.update("""
                insert into app.movimientos_financieros (tipo, fecha, concepto, valor, referencia,
                    estado, cliente_id, proveedor_id, estado_actualizado_por, actualizado_por)
                values (?, date '2026-11-10', ?, ?, ?, ?, ?, ?, ?, ?)
                """, tipo, concepto, valor, referencia, estado, clienteId, proveedorId,
                ACTOR, ACTOR);
    }
}
