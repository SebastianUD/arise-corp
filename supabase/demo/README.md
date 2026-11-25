# Datos para la presentación

Conjunto ficticio con nombres verosímiles, correos `example.com` e identificaciones sintéticas. Incluye cuentas, clientes, contratos, empleados, proveedores, movimientos financieros, drones, envíos con los seis estados e historial encadenado y existencias con entradas y salidas.

No se presenta como información de empresas o personas reales. No crea ni modifica usuarios de Auth, perfiles, roles, seguridad ni estructura. Las migraciones existentes no se modifican.

## Aplicación manual

Desde la raíz, con Java 21 o posterior y el JDBC PostgreSQL utilizado por el backend:

```powershell
java --class-path '<ruta-al-postgresql-jdbc.jar>' supabase/scripts/DatosPresentacion.java inspeccionar
java --class-path '<ruta-al-postgresql-jdbc.jar>' supabase/scripts/DatosPresentacion.java aplicar
```

El ejecutor carga la conexión local del backend en memoria. `inspeccionar` solo muestra conteos y roles, sin credenciales ni identificadores personales. `aplicar` reemplaza **todos los registros de las once tablas de negocio del entorno configurado**. Se utiliza únicamente para preparar la base de demostración autorizada.

Antes de borrar, bloquea las tablas y guarda una copia SQL completa en `supabase/.temp/antes-presentacion-<fecha>.sql`, excluida de Git. Después borra en orden de dependencias, inserta y comprueba el conjunto en una transacción serializable. Si falla cualquier paso, hace rollback. Conserva la copia privada para recuperación; no publicarla.

La copia contiene un script transaccional para restaurar las once tablas al momento del respaldo. Su ejecución también sustituye todos los datos de negocio posteriores; revisar el destino antes de restaurar. No usar `TRUNCATE CASCADE`.

## Validación real del frontend

Con backend en `localhost:8080`, desde `frontend/`:

```powershell
node manual/ejecutar-real.mjs
```

Solicita la contraseña por entrada oculta; no la escribe en archivos ni argumentos. Utiliza las cuentas de demostración Comercial y Operaciones indicadas en `manual/flujo-sga.live.jsx`. Esta ejecución manual **crea registros persistentes** para presentación y requiere autorización del propietario del entorno. Se excluye de `npm test` y de CI por su nombre y configuración independientes.

Recorre las vistas React con jsdom y los servicios, Supabase Auth, Spring Boot y PostgreSQL reales. Comprueba el flujo CA-05 y la recuperación tras remontar la aplicación; adicionalmente comprueba inventario `10 - 4 = 6` y el rechazo 409 de una salida de 20. jsdom no valida el aspecto visual ni sustituye una revisión en navegador.
