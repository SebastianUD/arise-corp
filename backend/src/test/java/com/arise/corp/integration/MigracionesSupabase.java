package com.arise.corp.integration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.testcontainers.containers.JdbcDatabaseContainer;

/**
 * Aplica las migraciones reales de {@code supabase/migrations} sobre el
 * PostgreSQL efimero de las pruebas de integracion (DP-TEC-04).
 *
 * <p>El PostgreSQL del contenedor no trae los objetos de Supabase que las
 * migraciones necesitan, asi que primero se ejecuta el script de preparacion
 * {@code integration/preparacion-supabase.sql}. Despues se aplican los
 * archivos {@code *.sql} ordenados por nombre: el prefijo con marca de tiempo
 * garantiza el orden cronologico. Cada archivo se ejecuta completo y en su
 * propia transaccion, sin copiarlo ni modificarlo.
 *
 * <p>Los archivos se leen del directorio que indica la propiedad de sistema
 * {@code arise.migraciones.dir}; Maven la fija en
 * {@code ${project.basedir}/../supabase/migrations} y desde el IDE se usa el
 * valor por defecto.
 */
final class MigracionesSupabase {

    static final String PROPIEDAD_DIRECTORIO = "arise.migraciones.dir";

    private static final String DIRECTORIO_POR_DEFECTO = "../supabase/migrations";
    private static final String SCRIPT_PREPARACION = "integration/preparacion-supabase.sql";

    private MigracionesSupabase() {
    }

    /**
     * Prepara el contenedor y aplica todas las migraciones versionadas.
     */
    static void aplicar(JdbcDatabaseContainer<?> contenedor) {
        List<Path> migraciones = archivosDeMigracion();

        try (Connection conexion = contenedor.createConnection("")) {
            ejecutar(conexion, leerRecurso(SCRIPT_PREPARACION), "preparacion-supabase.sql");

            for (Path archivo : migraciones) {
                ejecutar(conexion, Files.readString(archivo, StandardCharsets.UTF_8), archivo.getFileName().toString());
            }
        } catch (SQLException | IOException excepcion) {
            throw new IllegalStateException("No se pudieron preparar las migraciones del PostgreSQL efimero", excepcion);
        }
    }

    /**
     * Directorio de las migraciones versionadas.
     *
     * @throws IllegalStateException si el directorio no existe o no contiene
     *         ningun archivo {@code .sql}
     */
    static Path directorioMigraciones() {
        String configurado = System.getProperty(PROPIEDAD_DIRECTORIO, DIRECTORIO_POR_DEFECTO);
        Path directorio = Path.of(configurado).toAbsolutePath().normalize();

        if (!Files.isDirectory(directorio)) {
            throw new IllegalStateException(
                    "El directorio de migraciones no existe: " + directorio + " (propiedad " + PROPIEDAD_DIRECTORIO + ")");
        }
        return directorio;
    }

    /**
     * Archivos {@code *.sql} del directorio de migraciones, ordenados por nombre.
     */
    static List<Path> archivosDeMigracion() {
        Path directorio = directorioMigraciones();

        try (Stream<Path> archivos = Files.list(directorio)) {
            List<Path> migraciones = archivos
                    .filter(Files::isRegularFile)
                    .filter(archivo -> archivo.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(archivo -> archivo.getFileName().toString()))
                    .toList();

            if (migraciones.isEmpty()) {
                throw new IllegalStateException("El directorio de migraciones no contiene archivos .sql: " + directorio);
            }
            return migraciones;
        } catch (IOException excepcion) {
            throw new IllegalStateException("No se pudo leer el directorio de migraciones: " + directorio, excepcion);
        }
    }

    private static void ejecutar(Connection conexion, String sql, String nombre) throws SQLException {
        try (Statement sentencia = conexion.createStatement()) {
            conexion.setAutoCommit(false);
            sentencia.execute(sql);
            conexion.commit();
        } catch (SQLException excepcion) {
            conexion.rollback();
            throw new IllegalStateException("Fallo al aplicar " + nombre + ": " + excepcion.getMessage(), excepcion);
        }
    }

    private static String leerRecurso(String ruta) throws IOException {
        try (InputStream entrada = MigracionesSupabase.class.getResourceAsStream("/" + ruta)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontro el recurso de preparacion: " + ruta);
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
