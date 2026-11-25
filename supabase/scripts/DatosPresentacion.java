import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Herramienta manual: inspeccion o reemplazo transaccional con copia recuperable. */
class DatosPresentacion {
    static final List<String> TABLAS = List.of("cuentas_corporativas", "clientes", "contratos",
        "empleados", "proveedores", "movimientos_financieros", "drones", "paquetes",
        "historial_estados_paquete", "articulos_inventario", "movimientos_inventario");
    static Map<String, String> cargarEntorno(Path ruta) throws Exception {
        var valores = new HashMap<String, String>();
        for (var linea : Files.readAllLines(ruta, StandardCharsets.UTF_8)) {
            linea = linea.strip().replace("\uFEFF", "");
            if (linea.isEmpty() || linea.startsWith("#")) continue;
            int separador = linea.indexOf('=');
            if (separador > 0) valores.put(linea.substring(0, separador).strip(), linea.substring(separador + 1).strip());
        }
        return valores;
    }
    static Connection conectar(Path raiz) throws Exception {
        var env = cargarEntorno(raiz.resolve("backend/.env"));
        var props = new Properties();
        props.setProperty("user", env.get("SUPABASE_DB_USERNAME"));
        props.setProperty("password", env.get("SUPABASE_DB_PASSWORD"));
        props.setProperty("sslmode", "require");
        props.setProperty("connectTimeout", "15");
        props.setProperty("socketTimeout", "60");
        return DriverManager.getConnection("jdbc:postgresql://" + env.get("SUPABASE_DB_HOST") + ":"
            + env.getOrDefault("SUPABASE_DB_PORT", "5432") + "/" + env.getOrDefault("SUPABASE_DB_NAME", "postgres"), props);
    }
    static String literal(String valor) { return valor == null ? "NULL" : "'" + valor.replace("'", "''") + "'"; }
    static void conteos(Connection db) throws Exception {
        for (var tabla : TABLAS) {
            try (var stmt = db.createStatement(); var filas = stmt.executeQuery("select count(*) from app." + tabla)) {
                filas.next(); System.out.println(tabla + ": " + filas.getLong(1));
            }
        }
        try (var stmt = db.createStatement(); var filas = stmt.executeQuery("select rol, count(*) from app.perfiles_usuario where activo group by rol order by rol")) {
            while (filas.next()) System.out.println("Perfiles " + filas.getString(1) + ": " + filas.getLong(2));
        }
    }
    static String borrar() {
        var orden = new ArrayList<>(TABLAS); Collections.reverse(orden);
        return String.join("\n", orden.stream().map(t -> "DELETE FROM app." + t + ";").toList()) + "\n";
    }
    static Path respaldar(Connection db, Path raiz) throws Exception {
        var sql = new StringBuilder("-- Copia local privada. Restaurar solo en el mismo entorno.\nBEGIN;\nSET standard_conforming_strings = on;\n");
        sql.append(borrar());
        for (var tabla : TABLAS) {
            try (var stmt = db.createStatement(); var filas = stmt.executeQuery("select * from app." + tabla)) {
                var meta = filas.getMetaData();
                var columnas = new ArrayList<String>();
                for (int i = 1; i <= meta.getColumnCount(); i++) columnas.add("\"" + meta.getColumnName(i) + "\"");
                while (filas.next()) {
                    var valores = new ArrayList<String>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) valores.add(literal(filas.getString(i)));
                    sql.append("INSERT INTO app.").append(tabla).append(" (").append(String.join(",", columnas))
                        .append(") VALUES (").append(String.join(",", valores)).append(");\n");
                }
            }
        }
        sql.append("COMMIT;\n");
        Path carpeta = raiz.resolve("supabase/.temp"); Files.createDirectories(carpeta);
        Path destino = carpeta.resolve("antes-presentacion-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".sql");
        Files.writeString(destino, sql, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return destino;
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 0 || !Set.of("inspeccionar", "aplicar").contains(args[0]))
            throw new IllegalArgumentException("Uso: DatosPresentacion.java inspeccionar|aplicar [raiz]");
        Path raiz = args.length > 1 ? Path.of(args[1]) : Path.of(".").toAbsolutePath().normalize();
        try (var db = conectar(raiz)) {
            if (args[0].equals("inspeccionar")) { conteos(db); return; }
            db.setAutoCommit(false);
            db.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            try (var stmt = db.createStatement()) {
                stmt.execute("SET LOCAL lock_timeout = '10s'");
                stmt.execute("SET LOCAL standard_conforming_strings = on");
                stmt.execute("LOCK TABLE " + String.join(",", TABLAS.stream().map(t -> "app." + t).toList()) + " IN SHARE ROW EXCLUSIVE MODE");
                String actor;
                try (var filas = stmt.executeQuery("select p.usuario_id from app.perfiles_usuario p join auth.users u on u.id = p.usuario_id where p.activo and p.rol = 'ADMINISTRADOR' order by p.usuario_id limit 1")) {
                    if (!filas.next()) throw new IllegalStateException("Se requiere un administrador activo existente.");
                    actor = filas.getString(1);
                }
                Path respaldo = respaldar(db, raiz);
                System.out.println("Copia privada creada en supabase/.temp/" + respaldo.getFileName());
                try (var configurar = db.prepareStatement("select set_config('arise.actor', ?, true)")) {
                    configurar.setString(1, actor); configurar.execute();
                }
                stmt.execute(borrar());
                stmt.execute(Files.readString(raiz.resolve("supabase/demo/presentacion.sql"), StandardCharsets.UTF_8));
                conteos(db);
                db.commit();
                System.out.println("COMMIT: datos de presentacion sustituidos. Auth, perfiles y permisos conservados.");
            } catch (Exception e) { db.rollback(); throw e; }
        } catch (SQLException e) {
            System.err.println("Operacion cancelada; sin cambios parciales. SQLState=" + e.getSQLState());
            System.exit(1);
        }
    }
}
