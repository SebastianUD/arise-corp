package com.arise.corp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;

/**
 * Verifica la migracion del SGA sobre PostgreSQL efimero (Requirements 2,
 * 26.1-26.4, DP-SEG-05).
 *
 * <p>Cubre el contrato que H2 no puede comprobar: {@code ddl-auto=validate}
 * sobre el esquema migrado, los tipos de columna, las restricciones con su
 * SQLState de PostgreSQL, la coherencia dron-estado, la unicidad del paquete en
 * curso y la seguridad de RLS y privilegios de {@code anon} y
 * {@code authenticated}.
 */
class MigracionesSgaIT extends PostgresIntegracionBase {

    private static final UUID ACTOR =
            UUID.fromString("00000000-0000-4000-8000-000000000001");

    private static final List<String> TABLAS_DEL_SGA = List.of(
            "drones", "paquetes", "historial_estados_paquete",
            "articulos_inventario", "movimientos_inventario");

    private static final String DESCRIPCION = "Paquete de prueba";
    private static final String DIRECCION = "Calle 10 # 20-30";

    @Autowired
    private Environment entorno;

    @Test
    @DisplayName("El contexto arranca con ddl-auto=validate sobre el esquema migrado")
    void validaElEsquemaConDdlAutoValidate() {
        assertEquals("validate", entorno.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("app", entorno.getProperty("spring.jpa.properties.hibernate.default_schema"));
    }

    @Test
    @DisplayName("Las migraciones solo crean estructura y dejan las tablas del SGA vacias")
    void lasTablasDelSgaQuedanVaciasTrasMigrar() throws Exception {
        for (Path archivo : MigracionesSupabase.archivosDeMigracion()) {
            String sql = Files.readString(archivo, StandardCharsets.UTF_8);
            assertFalse(
                    sql.matches("(?is).*\\binsert\\s+into\\b.*"),
                    () -> archivo.getFileName() + " no debe insertar datos");
        }

        for (String tabla : TABLAS_DEL_SGA) {
            assertEquals(0, contar("app." + tabla));
        }
    }

    @Test
    @DisplayName("information_schema confirma uuid, integer, bigint, smallint, timestamptz y text")
    void informationSchemaConfirmaLosTiposDelDiseno() {
        assertEquals("uuid", tipoDe("drones", "id"));
        assertEquals("uuid", tipoDe("drones", "estado_actualizado_por"));
        assertEquals("timestamptz", tipoDe("drones", "estado_actualizado_en"));
        assertEquals("text", tipoDe("drones", "estado"));

        assertEquals("uuid", tipoDe("paquetes", "id"));
        assertEquals("uuid", tipoDe("paquetes", "cliente_id"));
        assertEquals("uuid", tipoDe("paquetes", "contrato_id"));
        assertEquals("uuid", tipoDe("paquetes", "dron_id"));
        assertEquals("text", tipoDe("paquetes", "estado"));

        assertEquals("uuid", tipoDe("historial_estados_paquete", "id"));
        assertEquals("uuid", tipoDe("historial_estados_paquete", "paquete_id"));
        assertEquals("smallint", dataTypeDe("historial_estados_paquete", "secuencia"));
        assertEquals("timestamptz", tipoDe("historial_estados_paquete", "registrado_en"));
        assertEquals("text", tipoDe("historial_estados_paquete", "estado_nuevo"));

        assertEquals("uuid", tipoDe("articulos_inventario", "id"));
        assertEquals("integer", dataTypeDe("articulos_inventario", "stock_minimo"));
        assertEquals("bigint", dataTypeDe("articulos_inventario", "existencia"));
        assertEquals("text", tipoDe("articulos_inventario", "categoria"));

        assertEquals("uuid", tipoDe("movimientos_inventario", "id"));
        assertEquals("uuid", tipoDe("movimientos_inventario", "articulo_id"));
        assertEquals("integer", dataTypeDe("movimientos_inventario", "cantidad"));
        assertEquals("bigint", dataTypeDe("movimientos_inventario", "existencia_anterior"));
        assertEquals("bigint", dataTypeDe("movimientos_inventario", "existencia_resultante"));
        assertEquals("text", tipoDe("movimientos_inventario", "tipo"));
    }

    @Test
    @DisplayName("Los codigos duplicados, el segundo paquete en curso y la secuencia repetida fallan con 23505")
    void laUnicidadDelSgaFallaConSqlState23505() {
        insertarDron("DRN-001", "Arise X4", "DISPONIBLE");
        assertEquals("23505", sqlStateDe(
                () -> insertarDron("DRN-001", "Arise X4", "DISPONIBLE")));

        UUID clienteId = insertarCliente("CC", "10234567");
        insertarPaquete("PAQ-001", DESCRIPCION, DIRECCION,
                clienteId, null, null, "REGISTRADO");
        assertEquals("23505", sqlStateDe(() -> insertarPaquete("PAQ-001", DESCRIPCION,
                DIRECCION, clienteId, null, null, "REGISTRADO")));

        insertarArticulo("ART-001", "Bateria LiPo", "BATERIA", "UNIDAD", 2, 10);
        assertEquals("23505", sqlStateDe(() -> insertarArticulo(
                "ART-001", "Otra bateria", "BATERIA", "UNIDAD", 2, 10)));

        UUID dronId = insertarDron("DRN-002", "Arise X4", "EN_MISION");
        insertarPaquete("PAQ-002", DESCRIPCION, DIRECCION,
                clienteId, null, dronId, "ASIGNADO");
        assertEquals("23505", sqlStateDe(() -> insertarPaquete("PAQ-003", DESCRIPCION,
                DIRECCION, clienteId, null, dronId, "EN_TRANSITO")));

        UUID paqueteId = insertarPaquete("PAQ-004", DESCRIPCION, DIRECCION,
                clienteId, null, null, "REGISTRADO");
        insertarHistorial(paqueteId, 1, null, "REGISTRADO", null, null);
        assertEquals("23505", sqlStateDe(() -> insertarHistorial(
                paqueteId, 1, null, "REGISTRADO", null, null)));
    }

    @Test
    @DisplayName("Los CHECK de estados, formato, longitudes, cantidades y aritmetica fallan con 23514")
    void lasRestriccionesDeComprobacionFallanConSqlState23514() {
        assertEquals("23514", sqlStateDe(
                () -> insertarDron("DRN-001", "Arise X4", "PAUSADO")));
        assertEquals("23514", sqlStateDe(
                () -> insertarDron("drn-001", "Arise X4", "DISPONIBLE")));
        assertEquals("23514", sqlStateDe(
                () -> insertarDron("DRN-002", "", "DISPONIBLE")));

        UUID clienteId = insertarCliente("CC", "10234567");
        assertEquals("23514", sqlStateDe(() -> insertarPaquete("PAQ-001",
                DESCRIPCION, DIRECCION, clienteId, null, null, "PERDIDO")));
        assertEquals("23514", sqlStateDe(() -> insertarPaquete("paq-001",
                DESCRIPCION, DIRECCION, clienteId, null, null, "REGISTRADO")));
        assertEquals("23514", sqlStateDe(() -> insertarPaquete("PAQ-002",
                "", DIRECCION, clienteId, null, null, "REGISTRADO")));
        assertEquals("23514", sqlStateDe(() -> insertarPaquete("PAQ-003",
                DESCRIPCION, "x".repeat(201), clienteId, null, null, "REGISTRADO")));

        assertEquals("23514", sqlStateDe(() -> insertarArticulo(
                "ART-001", "Bateria", "COMIDA", "UNIDAD", 2, 10)));
        assertEquals("23514", sqlStateDe(() -> insertarArticulo(
                "ART-002", "Bateria", "BATERIA", "LITRO", 2, 10)));
        assertEquals("23514", sqlStateDe(() -> insertarArticulo(
                "art-003", "Bateria", "BATERIA", "UNIDAD", 2, 10)));
        assertEquals("23514", sqlStateDe(() -> insertarArticulo(
                "ART-004", "", "BATERIA", "UNIDAD", 2, 10)));
        assertEquals("23514", sqlStateDe(() -> insertarArticulo(
                "ART-005", "Bateria", "BATERIA", "UNIDAD", -1, 10)));
        assertEquals("23514", sqlStateDe(() -> insertarArticulo(
                "ART-006", "Bateria", "BATERIA", "UNIDAD", 1_000_001, 10)));
        assertEquals("23514", sqlStateDe(() -> insertarArticulo(
                "ART-007", "Bateria", "BATERIA", "UNIDAD", 2, -1)));

        UUID articuloId = insertarArticulo(
                "ART-008", "Bateria LiPo", "BATERIA", "UNIDAD", 2, 10);
        assertEquals("23514", sqlStateDe(() -> insertarMovimiento(
                articuloId, "TRANSFERENCIA", 1, 0, 1, null)));
        assertEquals("23514", sqlStateDe(() -> insertarMovimiento(
                articuloId, "ENTRADA", 0, 0, 0, null)));
        assertEquals("23514", sqlStateDe(() -> insertarMovimiento(
                articuloId, "SALIDA", 1_000_001, 0, 0, null)));
        assertEquals("23514", sqlStateDe(() -> insertarMovimiento(
                articuloId, "ENTRADA", 5, 10, 16, null)));
        assertEquals("23514", sqlStateDe(() -> insertarMovimiento(
                articuloId, "SALIDA", 5, 10, 4, null)));
        assertEquals("23514", sqlStateDe(() -> insertarMovimiento(
                articuloId, "AJUSTE", 7, 10, 8, "Conteo fisico")));
        assertEquals("23514", sqlStateDe(() -> insertarMovimiento(
                articuloId, "AJUSTE", 7, 10, 7, null)));
        assertEquals("23514", sqlStateDe(() -> insertarMovimiento(
                articuloId, "ENTRADA", 5, 10, 15, "")));

        UUID paqueteId = insertarPaquete("PAQ-004", DESCRIPCION, DIRECCION,
                clienteId, null, null, "REGISTRADO");
        assertEquals("23514", sqlStateDe(() -> insertarHistorial(
                paqueteId, 5, "REGISTRADO", "ASIGNADO", null, null)));
    }

    @Test
    @DisplayName("El dron debe ser coherente con el estado del paquete y del historial")
    void elDronDebeSerCoherenteConElEstado() {
        UUID clienteId = insertarCliente("CC", "10234567");
        UUID dronId = insertarDron("DRN-001", "Arise X4", "EN_MISION");

        assertEquals("23514", sqlStateDe(() -> insertarPaquete("PAQ-001",
                DESCRIPCION, DIRECCION, clienteId, null, dronId, "REGISTRADO")));
        assertEquals("23514", sqlStateDe(() -> insertarPaquete("PAQ-002",
                DESCRIPCION, DIRECCION, clienteId, null, null, "ASIGNADO")));

        UUID paqueteId = insertarPaquete("PAQ-003", DESCRIPCION, DIRECCION,
                clienteId, null, null, "REGISTRADO");

        // El alta es el primer registro y debe estar en REGISTRADO.
        assertEquals("23514", sqlStateDe(() -> insertarHistorial(
                paqueteId, 1, null, "ASIGNADO", dronId, null)));
        // El dron solo se registra en la transicion a ASIGNADO.
        assertEquals("23514", sqlStateDe(() -> insertarHistorial(
                paqueteId, 2, "REGISTRADO", "EN_TRANSITO", dronId, null)));
        // La transicion a ASIGNADO exige el dron.
        assertEquals("23514", sqlStateDe(() -> insertarHistorial(
                paqueteId, 2, "REGISTRADO", "ASIGNADO", null, null)));

        insertarHistorial(paqueteId, 1, null, "REGISTRADO", null, null);
        insertarHistorial(paqueteId, 2, "REGISTRADO", "ASIGNADO", dronId, null);
        assertEquals(2, contar("app.historial_estados_paquete"));
    }

    @Test
    @DisplayName("Las claves foraneas del SGA fallan con SQLState 23503")
    void lasClavesForaneasFallanConSqlState23503() {
        UUID clienteId = insertarCliente("CC", "10234567");
        UUID paqueteId = insertarPaquete("PAQ-001", DESCRIPCION, DIRECCION,
                clienteId, null, null, "REGISTRADO");
        insertarArticulo("ART-001", "Bateria LiPo", "BATERIA", "UNIDAD", 2, 10);

        assertEquals("23503", sqlStateDe(() -> insertarPaquete("PAQ-002",
                DESCRIPCION, DIRECCION, UUID.randomUUID(), null, null, "REGISTRADO")));
        assertEquals("23503", sqlStateDe(() -> insertarPaquete("PAQ-003",
                DESCRIPCION, DIRECCION, clienteId, UUID.randomUUID(), null, "REGISTRADO")));
        assertEquals("23503", sqlStateDe(() -> insertarPaquete("PAQ-004",
                DESCRIPCION, DIRECCION, clienteId, null, UUID.randomUUID(), "ASIGNADO")));

        assertEquals("23503", sqlStateDe(() -> insertarHistorial(
                UUID.randomUUID(), 1, null, "REGISTRADO", null, null)));
        assertEquals("23503", sqlStateDe(() -> insertarHistorial(
                paqueteId, 2, "REGISTRADO", "ASIGNADO", UUID.randomUUID(), null)));

        assertEquals("23503", sqlStateDe(() -> insertarMovimiento(
                UUID.randomUUID(), "ENTRADA", 1, 0, 1, null)));
        assertEquals(1, contar("app.paquetes"));
        assertEquals(1, contar("app.articulos_inventario"));
        assertEquals(0, contar("app.historial_estados_paquete"));
        assertEquals(0, contar("app.movimientos_inventario"));
    }

    @Test
    @DisplayName("Dos paquetes CANCELADO con el mismo dron se aceptan")
    void dosPaquetesCanceladosConElMismoDronSeAceptan() {
        UUID clienteId = insertarCliente("CC", "10234567");
        UUID dronId = insertarDron("DRN-001", "Arise X4", "DISPONIBLE");

        insertarPaquete("PAQ-001", DESCRIPCION, DIRECCION,
                clienteId, null, dronId, "CANCELADO");
        insertarPaquete("PAQ-002", DESCRIPCION, DIRECCION,
                clienteId, null, dronId, "CANCELADO");

        assertEquals(2, contar("app.paquetes"));
    }

    @Test
    @DisplayName("Las cinco tablas tienen RLS y ningun privilegio para anon ni authenticated")
    void lasTablasTienenRlsYSinPrivilegiosParaLosRolesDeCliente() {
        for (String tabla : TABLAS_DEL_SGA) {
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

    private String dataTypeDe(String tabla, String columna) {
        return jdbc.queryForObject(
                "select data_type from information_schema.columns"
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

    private UUID insertarDron(String codigo, String modelo, String estado) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into app.drones (id, codigo, modelo, estado, estado_actualizado_por,
                    actualizado_por)
                values (?, ?, ?, ?, ?, ?)
                """, id, codigo, modelo, estado, ACTOR, ACTOR);
        return id;
    }

    private UUID insertarPaquete(
            String codigo,
            String descripcion,
            String direccion,
            UUID clienteId,
            UUID contratoId,
            UUID dronId,
            String estado) {

        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into app.paquetes (id, codigo, descripcion, direccion_entrega, estado,
                    cliente_id, contrato_id, dron_id, estado_actualizado_por, actualizado_por)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, codigo, descripcion, direccion, estado, clienteId, contratoId,
                dronId, ACTOR, ACTOR);
        return id;
    }

    private void insertarHistorial(
            UUID paqueteId,
            int secuencia,
            String estadoAnterior,
            String estadoNuevo,
            UUID dronId,
            String observacion) {

        jdbc.update("""
                insert into app.historial_estados_paquete (paquete_id, secuencia, estado_anterior,
                    estado_nuevo, dron_id, observacion, registrado_por)
                values (?, ?, ?, ?, ?, ?, ?)
                """, paqueteId, secuencia, estadoAnterior, estadoNuevo, dronId, observacion,
                ACTOR);
    }

    private UUID insertarArticulo(
            String codigo,
            String nombre,
            String categoria,
            String unidad,
            int stockMinimo,
            long existencia) {

        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into app.articulos_inventario (id, codigo, nombre, categoria, unidad,
                    stock_minimo, existencia, actualizado_por)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, codigo, nombre, categoria, unidad, stockMinimo, existencia, ACTOR);
        return id;
    }

    private void insertarMovimiento(
            UUID articuloId,
            String tipo,
            int cantidad,
            long existenciaAnterior,
            long existenciaResultante,
            String observacion) {

        jdbc.update("""
                insert into app.movimientos_inventario (articulo_id, tipo, cantidad,
                    existencia_anterior, existencia_resultante, observacion, registrado_por)
                values (?, ?, ?, ?, ?, ?, ?)
                """, articuloId, tipo, cantidad, existenciaAnterior, existenciaResultante,
                observacion, ACTOR);
    }
}
