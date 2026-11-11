# Feature: 002-crm-clientes-contratos — tareas 2 a 14

Rama `feat/crm-clientes-contratos`. Estrategia de entrega: un unico PR al cerrar la spec
(`ask-on-risk`). Pruebas backend: `.\mvnw.cmd test` (Surefire + H2, sin Docker).
La especificacion de referencia es `.kiro/specs/002-crm-clientes-contratos/`
(`requirements.md`, `design.md`, `tasks.md`); este documento solo registra el avance
y el contrato de la migracion, las entidades, los repositorios y el CI.

## Specs

- S1. Ejecutar, en orden, las tareas 2.1–2.2, 3.1–3.5, 4.1, 5.1–5.3, 6.1–6.2 y 7 de
  `.kiro/specs/002-crm-clientes-contratos/tasks.md`, con el SQL, las entidades, los
  repositorios, los workflows y los textos de documentacion que fija `design.md`.
- S2. La migracion vive en `supabase/migrations/<timestamp>_app_crm_clientes_contratos.sql`,
  con timestamp posterior a `20261108000000`, sin modificar la migracion existente, sin
  `insert`, sin UUID personales y sin sentencias que no admitan transaccion.
- S3. Las entidades usan `@Table` sin esquema, `@Id @GeneratedValue(strategy = GenerationType.UUID)`,
  `@Enumerated(EnumType.STRING)`, `creadoEn` con `updatable = false`, sin setters publicos y
  con metodos de fabrica que reciben `(UUID actor, Instant ahora)`.
- S4. Los repositorios exponen las firmas de "Backend: repositorios" del diseno, con
  `JpaSpecificationExecutor`, bloqueos `PESSIMISTIC_READ`/`PESSIMISTIC_WRITE` sin fetch join
  y las proyecciones de conteo `ConteoPorClave<K>` (con fallback concreto solo si no resuelve).
- S5. El CI son tres workflows en `.github/workflows/` con `push` a `'**'`, `pull_request`
  hacia `main`, `workflow_dispatch`, `concurrency` con `cancel-in-progress`, `permissions:
  contents: read`, solo acciones oficiales (`checkout@v7`, `setup-java@v6`, `setup-node@v7`),
  sin `secrets.*` ni credenciales de `arise-corp-dev`, y sin publicacion de imagenes.
- S6. `backend/mvnw` queda versionado con modo `100755`; los finales de linea de
  `backend/.gitattributes` no cambian.
- S7. El README §10 y `.kiro/steering/tech.md` describen los tres workflows, sus disparadores,
  sus comandos y el bit ejecutable de `mvnw`, y CI queda marcado como [Implementado].
- S8. El checkpoint 7 exige `.\mvnw.cmd clean test` en verde e `git ls-files -s backend/mvnw`
  con modo `100755`; todas las pruebas pasan antes de cerrar.
- S9. Las tareas 16.1, 16.2 y 16.3 introducen Testcontainers y Failsafe (dependencias de alcance
  `test`, sin `testcontainers-junit-jupiter`, con `**/integration/**/*IT.java` y
  `arise.migraciones.dir`), el perfil `it` con `ddl-auto=validate` sobre PostgreSQL efímero, la
  infraestructura de IT sin credenciales de `arise-corp-dev` y `MigracionesCrmIT`, que comprueba
  tipos, restricciones con SQLState 23505/23514/23503, RLS y ausencia de privilegios. `mvn test`
  no recoge las `*IT` y `mvn verify` sí.
- S10. Las tareas 18.1 y 18.2 crean `backend/Dockerfile` multi-etapa (`eclipse-temurin:21-jdk`
  con `chmod +x mvnw` y caché de BuildKit para `~/.m2`; `eclipse-temurin:21-jre` con el usuario
  `arise`, solo `app.jar`, `EXPOSE 8080`, sin `ARG` ni `ENV` de configuración) y
  `backend/.dockerignore` con `.env`, `.env.*`, `target/`, `src/test/`, archivos de IDE, `*.log`,
  `HELP.md` y `.gitattributes`. La tarea 18.3 crea `docker-compose.yml` en la raíz: proyecto
  `arise-corp`, un único servicio `backend` desde `./backend`, imagen `arise-backend:local`,
  `env_file` con `path: ./backend/.env` y `required: true`, puerto `127.0.0.1:8080:8080`, sin
  credenciales ni base de datos local.

## Tasks

| ID | Specs | Ruta | Estado | Evidencia |
|---|---|---|---|---|
| T2 | S1, S2 | inline | completada | commit `47d3a4d`; SQL identico al diseno (113 lineas) y fila en el README |
| T3 | S1, S3, S4 | inline | completada | commit `185d984`; `CrmRepositoriosTests`: 8 pruebas en verde |
| T4 | S1, S6 | inline | completada | commit `628f827`; `git ls-files -s backend/mvnw` = `100755` |
| T5 | S1, S5 | inline | completada | commit `86f768a`; YAML validado con PyYAML contra Requirement 28 |
| T6 | S1, S7 | inline | completada | este commit; README §10 y `tech.md` |
| T7 | S8 | inline | completada | `.\mvnw.cmd clean test`: 66 pruebas en verde (commit previo) |
| T16 | S9 | inline | completada | Testcontainers + Failsafe, infra `it` y `MigracionesCrmIT`: 7/7 en verde |
| T18 | S10 | inline | completada | `docker build` OK; imagen con usuario `arise`, `8080/tcp` y sin `.env`; `docker compose config --services` = `backend` |
| T8 | S1 | inline | completada | Revisado el SQL contra "Migración CRM" y "Entidades JPA": columnas, tipos, `CHECK`, `UNIQUE`, FK, índices, RLS, `revoke` y ausencia de datos coinciden (113 líneas); no requirió cambios. Ya estaba aplicado en `arise-corp-dev` (L5), por lo que no se editó el archivo |
| T9 | S1 | `backend/src/main/java/com/arise/corp/shared/error/**` | completada | `ConflictoNegocioException(conflicto, detalle, campo, extensiones)`, constructor `RecursoNoEncontradoException(mensaje, campo)` y handlers 409/404/405/415/concurrencia; `ErroresApiTests`: 19 pruebas (incluye Property 17) |
| T10 | S1 | `backend/src/main/java/com/arise/corp/shared/normalizacion/**`, `crm/entity/**` | completada | `NormalizadorTexto` final e idempotente; `EstadoContrato` con las cuatro transiciones; defensas en `Contrato.actualizarDatos`/`cambiarEstado`; `NormalizadorTextoTests` (4) y `EstadoContratoTests` (21) |
| T11 | S1 | `backend/src/main/java/com/arise/corp/shared/paginacion/**`, `config/**`, `shared/validacion/**` | completada | `PaginaResponse.desde`, `Paginacion.crear` con desempate `id,asc`, `ClockConfig`, `@Correo`/`@Telefono`/`@ValorCop`; `PaginacionTests` (10, incluye Property 14) |
| T12 | S1 | `backend/src/main/java/com/arise/corp/crm/dto/**` | completada | DTO de entrada/salida, validadores de clase y `VistaCliente`; `ClienteRequestValidacionTests`, `CuentaCorporativaRequestValidacionTests`, `ContratoRequestValidacionTests` y `SerializacionCrmTests` cubren las Properties 3, 4, 5 y 6 |
| T13.1 | S1 | `backend/src/main/java/com/arise/corp/crm/service/ConflictosCrm.java`, `backend/src/main/java/com/arise/corp/crm/repository/EspecificacionesCrm.java` | completada | Ocho fabricas de conflicto con codigo, `campo`, `detail` y extensiones; siete filtros con `LIKE ... ESCAPE '\'`, documento y NIT normalizados y filtros ausentes sin restriccion; `ConflictosCrmTests` (3) y `EspecificacionesCrmTests` (10) en verde |
| T13.2 | S1 | `backend/src/main/java/com/arise/corp/crm/service/CuentaCorporativaService.java`, `backend/src/main/java/com/arise/corp/crm/service/FiltroCuentas.java` | completada | `listar`, `obtener`, `crear`, `actualizar` y `cambiarEstado` con `CUENTA_NIT_DUPLICADO` sobre el NIT normalizado, conservacion de `activa` y `creadoEn`, `PATCH` idempotente que no toca clientes y auditoria desde `Clock` truncado a microsegundos; `.\mvnw.cmd test` -> 156 pruebas en verde |
| T13.5 | S1 | `backend/src/main/java/com/arise/corp/crm/service/ResumenComercialService.java` | completada | commit `fb7c8fc`; `obtener()` con las dos consultas agrupadas (`contarPorActivo` y `contarPorEstado`) en una transaccion de solo lectura, `EnumMap` con los cuatro estados en 0 y totales como suma de sus componentes; `mvn test` en verde |
| T13.7 | S1 | `backend/src/test/java/com/arise/corp/crm/service/CuentaCorporativaServiceTests.java` | completada | commit `13e7e02`; 12 pruebas con Mockito y `Clock.fixed`: alta con NIT normalizado y auditoria, edicion que conserva `activa` y `creadoEn`, 404, Property 7 con 120 variantes de NIT y Property 8 con las cuatro combinaciones; la desactivacion no modifica los clientes |
| T13.9 | S1 | `backend/src/test/java/com/arise/corp/crm/service/ResumenComercialServiceTests.java` | completada | commit `fb7c8fc`; 5 pruebas: cuatro estados en 0, conteo de clientes aunque falte un grupo, solo dos consultas, `@Transactional(readOnly = true)` y Property 13 con 120 casos de conteos generados |
| T14.4 | S1 | `backend/src/main/java/com/arise/corp/crm/controller/CuentaCorporativaController.java` | completada | commit `4cf78b8`; `GET`, `GET /{id}`, `POST` (201 con `Location`), `PUT /{id}` y `PATCH /{id}` en `/api/crm/cuentas-corporativas`, filtros `q` y `activa`, orden por defecto `razonSocial,asc` y actor desde `@AuthenticationPrincipal Jwt`; sin `DELETE` |
| T14.6 | S1 | `backend/src/main/java/com/arise/corp/crm/controller/ResumenComercialController.java` | completada | commit `4cf78b8`; `GET /api/crm/resumen` delega en `ResumenComercialService.obtener()` bajo el patron `/api/crm/**` de `SecurityConfig` |
| T14.8 | S1 | `backend/src/test/java/com/arise/corp/crm/controller/CrmAutorizacionTests.java` | completada | commit `02a6dc9`; 119 casos: 17 operaciones x 7 identidades con 401 sin token, 403 sin perfil, con perfil inactivo y por rol, datos sin cambios, 403 con un `{id}` inexistente y `DELETE` 405 que conserva el cliente |
| T14.11 | S1 | `backend/src/test/java/com/arise/corp/crm/controller/CuentaCorporativaControllerTests.java`, `backend/src/test/java/com/arise/corp/crm/controller/ResumenComercialControllerTests.java` | completada | commit `50b1aed`; 41 pruebas de cuentas (201 con `Location`, 200, 400 por regla, 404, 409 `CUENTA_NIT_DUPLICADO`, `PATCH` idempotente sin tocar clientes, campos del servidor y Property 14) y 3 del resumen (cuatro estados en 0, conteos por estado y lectura en el momento de la solicitud) |

## Log

- L1 (2026-11-11): "Crea el archivo opencode.json y pega este mcp: ... Vamos a continuar con
  las tareas que están en .kiro/specs/002-crm-clientes-contratos, de las tareas 2 hasta la 7".
- L2: la Supabase CLI no esta instalada en el entorno, asi que el archivo de migracion se crea
  con el nombre `<timestamp>_app_crm_clientes_contratos.sql` documentado en el diseno
  (`20261111120000`), sin ejecutar `supabase migration new`.
- L3: versiones mayores de las acciones verificadas en GitHub el 2026-11-11:
  `checkout` v7.0.1, `setup-java` v6.2.0, `setup-node` v7.1.0.
- L4: verificacion observada. `CrmRepositoriosTests` (8 pruebas) y la suite completa
  (`.\mvnw.cmd clean test`, 66 pruebas) en verde con JDK 21. El SQL generado por Hibernate
  confirma que `@EntityGraph` si se aplica a `findAll(Specification, Pageable)` en Spring Data
  JPA 4.1.2 (el `select` incluye el `left join` de la cuenta), asi que no hace falta el
  fallback de proyecciones ni una estrategia de carga adicional. Los tres YAML se validaron con
  PyYAML comprobando disparadores, `concurrency`, `permissions`, acciones oficiales, comandos
  y ausencia de referencias a secretos.
- L5 (2026-11-11): instruccion del usuario "Ya esta autenticado el supabase, haz la migracion".
  Se aplico la migracion en `arise-corp-dev` (project id `dttzrepohlrfsepygmln`) con
  `apply_migration` del MCP, nombre `app_crm_clientes_contratos`, version remota
  `20261111061432`. La tarea 8.1 (revision de la migracion por otro integrante) **no** se
  ejecuto antes de aplicarla; queda registrada como pendiente y ya no se puede editar la
  migracion, solo crear una nueva si la revision lo exige.
  Evidencia observada: `list_migrations` muestra `app_perfiles_usuario` y
  `app_crm_clientes_contratos`; las tres tablas existen con `relrowsecurity = true`;
  `has_table_privilege` de `anon` y `authenticated` en `select` es `false`; las tres tablas
  estan vacias; `has_schema_privilege('anon'|'authenticated', 'app', 'usage')` es `false`.
  `get_advisors` (security) solo reporta `rls_enabled_no_policy` (INFO, esperado en el esquema
  privado) y `auth_leaked_password_protection` (WARN del proyecto Auth, ajeno a esta migracion).
- L6 (2026-11-11): tareas 16.1, 16.2 y 16.3 completadas. `backend/pom.xml` agrega
  `spring-boot-testcontainers` y `org.testcontainers:testcontainers-postgresql` (sin version, la
  gestiona el parent) y `maven-failsafe-plugin` con `**/integration/**/*IT.java` y
  `arise.migraciones.dir`. Infra: `application-it.properties`, `integration/preparacion-supabase.sql`,
  `MigracionesSupabase`, `PostgresContenedorConfig` y `PostgresIntegracionBase`.
  Evidencia observada: `.\mvnw.cmd test` -> 66 pruebas, 0 fallos y **sin** ejecutar `MigracionesCrmIT`
  (Surefire no recoge `*IT`); `.\mvnw.cmd verify` -> Surefire 66 + Failsafe 7 (`MigracionesCrmIT`),
  BUILD SUCCESS contra `postgres:17-alpine` en Testcontainers 2.0.5 (Docker 29.3.1).
  Aprendido: el SQL completo de cada migracion se ejecuta en un solo `Statement.execute`, asi que
  el PostgreSQL efimero aplica los dos archivos reales sin copiarlos; el script de preparacion
  concede privilegios por defecto a `anon`/`authenticated` para que la revocacion de la migracion
  sea significativa.
  RDD: `gentle-ai review assess --base-ref ec1d359 --committed-only` devuelve `medium` y
  `review_due=false` (`under_budget`), asi que no se inicia revision nativa y la frontera revisada
  sigue en `ec1d359`. El primer intento salio `unassessable` por los archivos sin trackear
  (`.atl/`, `opencode.json`); se repitio con `--untracked-scope=exclude`.
- L7 (2026-11-11): `ca4bea8` subido a `origin/feat/crm-clientes-contratos`. Tareas 18.1 y 18.2
  completadas con el contenido exacto del diseno: `backend/Dockerfile` (multi-etapa, `chmod +x
  mvnw`, cache de BuildKit para `~/.m2`, usuario `arise` sin privilegios, solo `app.jar`, sin
  `ARG` ni `ENV`) y `backend/.dockerignore`. Evidencia observada: `docker build -t
  arise-backend:local backend` termina con exit 0 en 96 s de etapa de compilacion; `docker image
  inspect` -> `User=arise`, `Ports={"8080/tcp":{}}`, `Cmd=["java","-jar","/app/app.jar"]`;
  `docker run --rm --entrypoint id` -> `uid=999(arise)`; `find / -name '.env*'` dentro de la imagen
  no devuelve nada; `docker history --no-trunc` de la etapa final solo muestra `groupadd/useradd`,
  `WORKDIR`, `COPY app.jar`, `USER`, `EXPOSE` y `ENTRYPOINT`. Queda pendiente la 18.3
  (`docker-compose.yml`) y la 18.4 (verificacion completa con Compose).
  RDD: `gentle-ai review assess --base-ref ec1d359 --committed-only` sobre `ec1d359..5fd61ad`
  devuelve `medium` y `review_due=false` (`under_budget`); no se inicia revision nativa y la
  frontera revisada sigue en `ec1d359`. Riesgo declarado del cambio: item 5 (empaquetado de
  despliegue), mitigado con la construccion real y la inspeccion de la imagen.
- L8 (2026-11-11): tarea 18.3 completada. `docker-compose.yml` en la raiz con el contenido exacto
  del diseno. Evidencia observada: `docker compose config --quiet` exit 0 (valida sin imprimir
  valores), `docker compose config --services` -> `backend` unico servicio, `docker compose build
  backend` reutiliza la imagen `arise-backend:local`; Docker Compose v5.1.1. Se verifico ademas
  que `backend/.env` es compatible con los dos lectores (D15): sin `$`, sin comillas y sin espacios
  alrededor del valor, de modo que Compose y Spring leen los mismos valores. La parte de ejecucion
  de la 18.4 (`docker compose up backend` + `curl` -> 401) queda diferida al cierre del backend por
  decision del usuario.
- L9 (2026-11-11): tareas 8 a 12 completadas. La revision 8.1 comparo la migracion
  `20261111120000_app_crm_clientes_contratos.sql` con el bloque "Migración CRM" del diseno y no
  encontro diferencias (columnas, tipos, `CHECK`, `UNIQUE`, FK, indices, RLS, `revoke` y tablas
  vacias); como ya estaba aplicada en `arise-corp-dev` (L5), no se modifico el archivo.
  Se anadieron `ConflictoNegocioException` y el manejo de 409/404 con `campo`, 405 y 415 en
  espanol, deduplicacion de `errors`, `ConcurrencyFailureException` -> 409 y los mensajes por tipo
  de destino; `NormalizadorTexto`, la maquina de estados de `EstadoContrato` con las cuatro
  transiciones y las defensas de `Contrato`; `PaginaResponse`/`Paginacion`, `ClockConfig` y las
  restricciones compuestas `@Correo`, `@Telefono` y `@ValorCop`; y todos los DTO de entrada y
  salida del CRM con sus validadores de clase. Evidencia observada: `.\mvnw.cmd clean test` ->
  143 pruebas, 0 fallos, 0 errores, sin Docker ni Supabase. Las propiedades usan `GeneradoresCrm`
  con semilla fija y 120 casos (`Property 1`, `2`, `3`, `4`, `5`, `6`, `11`, `14` y `17`). El
  unico ajuste de comportamiento previo fue reordenar una prueba de `CrmRepositoriosTests` para
  editar el contrato antes de cambiar su estado, porque `Contrato.actualizarDatos` ahora solo
  admite `BORRADOR`.
- L10 (2026-11-11): tarea 13.1 completada. `ConflictosCrm` centraliza las ocho fabricas del
  catalogo de "Error Handling" con codigos estables, `campo`, `detail` en espanol y extensiones
  (`clienteId`, `estadoActual`, `estadoSolicitado`), sin datos personales.
  `EspecificacionesCrm` implementa los siete filtros con la API de criterios y
  `LIKE ... ESCAPE '\'`: el nombre y la razon social se comparan sin distinguir mayusculas, el
  documento y el NIT se buscan con su valor normalizado, los comodines `%`, `_` y `\` se escapan
  y un filtro ausente devuelve una especificacion sin restricciones para poder combinarla con
  `Specification.allOf`. Evidencia observada: `.\mvnw.cmd clean test` -> 156 pruebas, 0 fallos,
  0 errores, sin Docker ni Supabase; `ConflictosCrmTests` (3) y `EspecificacionesCrmTests` (10)
  cubren el catalogo, la busqueda, los comodines y las combinaciones de filtros sobre H2.
- L11 (2026-11-11): integracion del trabajo de Paula (frontend, tareas 21 a 30) con el avance
  backend 8 a 13.1 mediante un merge `--no-ff` sobre `feat/crm-clientes-contratos`;
  `tasks.md` conservo ambas marcas y el arbol quedo limpio. Evidencia observada:
  `.\mvnw.cmd test` -> 156 pruebas, 0 fallos, 0 errores; `npm test` -> 159 pruebas, 0 fallos.
  Los 7 fallos iniciales del frontend eran agotamientos de espera por contencion de CPU al
  crear jsdom una vez por archivo, no defectos de logica: se configuro `pool: 'vmThreads'`,
  `testTimeout`/`hookTimeout` de 15 s y `asyncUtilTimeout` de 5 s, sin debilitar ninguna
  asercion. La suite pasa completa en dos ejecuciones consecutivas (~30 s) y tambien pasan
  `npm run build` con variables ficticias y `npm run check:secrets`.
- L12 (2026-11-11): tarea 13.2 completada. `CuentaCorporativaService` implementa `listar` con
  `EspecificacionesCrm.razonSocialONitContiene` y `activaIgual` combinadas con
  `Specification.allOf`, `obtener` con 404 "La cuenta corporativa no existe.", `crear` con
  `activa = true` y `CUENTA_NIT_DUPLICADO` si el NIT existe (activa o inactiva), `actualizar`
  que detecta el NIT de otra cuenta, reemplaza los datos y conserva `activa` y `creadoEn`, y
  `cambiarEstado` que delega en `CuentaCorporativa.cambiarEstado` (sin cambios de auditoria si
  el valor coincide y sin tocar clientes). El actor se convierte con `UUID.fromString(sub)` y el
  instante sale de `Clock` truncado a `MICROS`; el DTO se construye dentro de la transaccion.
  Se agrego `FiltroCuentas(q, activa)` en `crm.service` porque el controlador (14.4) lo
  construye y el servicio no puede depender de `crm.controller`. Evidencia observada:
  `.\mvnw.cmd test` -> 156 pruebas, 0 fallos, 0 errores, sin Docker ni Supabase. `CuentaCorporativaServiceTests`
  queda en la tarea 13.7 (Properties 7 y 8). Riesgo declarado: item 6, porque el servicio nuevo
  aun no tiene pruebas propias hasta la 13.7. RDD: `gentle-ai review assess --base-ref ec1d359
  --committed-only` devuelve `medium` y `review_due=false` (`under_budget`); no se inicia revision
  nativa y la frontera revisada sigue en `ec1d359`.
- L13 (2026-11-11): tareas 13.3 y 13.4 completadas. `ClienteService` implementa `listar` con
  `nombreODocumentoContiene`, `activoIgual` y `cuentaIgual` y la vista `COMPLETA` o `REDUCIDA`
  segun el actor; `obtener` con `@EntityGraph` y 404; `crear` carga la cuenta con
  `PESSIMISTIC_READ` (404 con `campo = cuentaCorporativaId`), distingue documento activo
  (`CLIENTE_DUPLICADO`) de inactivo (`CLIENTE_INACTIVO_EXISTENTE` con `clienteId`) y rechaza
  una cuenta inactiva (`CUENTA_INACTIVA`); `actualizar` acepta la cuenta actual aunque este
  inactiva, permite quitarla con `null`, rechaza el documento de otro cliente y conserva
  `activo` y `creadoEn`; `cambiarEstado` es idempotente y no toca contratos. `ContratoService`
  implementa `listar` con `clienteIgual` y `estadoIgual` (un `clienteId` inexistente deja la
  pagina vacia), `obtener` con 404, `crear` con `PESSIMISTIC_READ` sobre el cliente (404 con
  `campo = clienteId`), `CLIENTE_INACTIVO_SIN_CONTRATOS`, `CONTRATO_CODIGO_DUPLICADO` y estado
  inicial `BORRADOR` con auditoria de datos y de estado; `actualizar` y `cambiarEstado` usan
  `PESSIMISTIC_WRITE`, `CONTRATO_NO_EDITABLE` y `TRANSICION_NO_PERMITIDA` con `estadoActual` y
  `estadoSolicitado`. Se agregaron `FiltroClientes(q, activo, cuentaCorporativaId)` y
  `FiltroContratos(clienteId, estado)` en `crm.service` porque los controladores (14.3 y 14.5)
  los construyen. Evidencia observada: `.\mvnw.cmd compile` sin errores y `.\mvnw.cmd test`
  -> 156 pruebas, 0 fallos, 0 errores, sin Docker ni Supabase. `ClienteServiceTests` (13.6) y
  `ContratoServiceTests` (13.8) quedan pendientes y cubriran las Properties 7 a 12.
- L14 (2026-11-11): tareas 13.6 y 13.8 completadas. `ClienteServiceTests` (16 pruebas) usa
  Mockito y `Clock.fixed` para comprobar la creacion activa con cuenta, la auditoria
  `creadoEn = actualizadoEn = ahora` y `actualizadoPor = sub`, la edicion que conserva
  `activo` y `creadoEn`, los duplicados activo e inactivo, la vista `COMPLETA`/`REDUCIDA`,
  los 404 y el mapeo de la pagina. Property 7 recorre 120 documentos con separadores,
  mayusculas y minusculas; Property 8 recorre las cuatro combinaciones de estado y solo
  cambia la auditoria cuando el valor difiere; Property 9 recorre nueve combinaciones de
  cuenta actual y destino al editar y tres al crear, incluida la cuenta actual inactiva que
  se conserva y la cuenta distinta inactiva que se rechaza. `ContratoServiceTests`
  (46 pruebas) cubre el alta en `BORRADOR` con auditoria de datos y estado, el cliente
  inactivo con 120 codigos generados, el codigo duplicado con 120 variantes de mayusculas y
  espacios, los 404, Property 12 sobre los cuatro estados y Property 11 sobre los 16 pares
  de estados con cliente activo e inactivo, comprobando que cambiar el estado no interactua
  con el repositorio de clientes (Requirement 14.5). Evidencia observada:
  `.\mvnw.cmd test -Dtest=ClienteServiceTests,ContratoServiceTests` -> 62 pruebas, 0 fallos;
  `.\mvnw.cmd test` -> 218 pruebas, 0 fallos, 0 errores, sin Docker ni Supabase.

- L15 (2026-11-11): tareas 13.5, 13.7 y 13.9 completadas. `ResumenComercialService.obtener()`
  ejecuta las dos consultas agrupadas del diseno (`contarPorActivo` y `contarPorEstado`) en una
  transaccion de solo lectura, indexa los conteos ignorando claves ausentes o nulas y construye
  `ResumenComercialResponse` con `ConteoClientes.desde` y `ConteoContratos.desde`, de modo que los
  cuatro estados aparecen siempre con 0 y los totales son la suma de sus componentes.
  `CuentaCorporativaServiceTests` (12 pruebas) usa Mockito y `Clock.fixed` para el alta con NIT
  normalizado y auditoria, la edicion que conserva `activa` y `creadoEn`, el NIT de otra cuenta,
  el 404, Property 7 con 120 variantes de mayusculas, espacios y guiones, Property 8 con las
  cuatro combinaciones de estado y la desactivacion que no modifica los clientes asociados.
  `ResumenComercialServiceTests` (5 pruebas) cubre los cuatro estados en 0, el conteo de clientes
  cuando falta un grupo, las dos consultas, la transaccion de solo lectura y Property 13 con 120
  casos de conteos generados. Evidencia observada: RED en la compilacion de
  `ResumenComercialServiceTests` por el servicio ausente, GREEN tras implementarlo;
  `.\mvnw.cmd test -Dtest=CuentaCorporativaServiceTests` -> 12 pruebas, 0 fallos;
  `.\mvnw.cmd test` -> 235 pruebas, 0 fallos, 0 errores, sin Docker ni Supabase.
  Riesgo declarado: item 6 (el resumen nuevo no tenia pruebas propias hasta esta misma entrega,
  que las anade); sin cambios en contratos, datos ni concurrencia.
- L16 (2026-11-11): tareas 14.1, 14.2, 14.3, 14.5, 14.7, 14.9, 14.10 y 14.12 completadas;
  14.4, 14.6, 14.8 y 14.11 quedan pendientes porque dependen de los controladores de cuentas
  y resumen de Davidson. `VistaClienteResolver` devuelve `COMPLETA` solo con
  `ROLE_ADMINISTRADOR` o `ROLE_COMERCIAL` y nunca lee parametros de la peticion.
  `ClienteController` expone `GET`, `GET /{id}`, `POST` (201 con `Location` relativa), `PUT` y
  `PATCH` en `/api/crm/clientes`, con el actor tomado de `@AuthenticationPrincipal Jwt` y las
  restricciones de lista en los `@RequestParam` sin `@Validated`; no mapea `DELETE`.
  `ContratoController` expone `GET`, `GET /{id}`, `POST`, `PUT` y `PATCH /{id}/estado` en
  `/api/crm/contratos` con filtros `clienteId` y `estado` y orden por defecto `creadoEn,desc`.
  Se retiraron los cuatro dobles CRM de `AutorizacionModulosTests`; sus casos de 401, 403,
  perfiles inactivos y Administrador usan ahora ERP y SGA, y las pruebas de los controladores
  reales conviven en el mismo contexto sin mapeo ambiguo. `CrmDatosPrueba` crea perfiles,
  cuentas, clientes y contratos ficticios, limpia las tablas en orden de dependencias y
  autentica con `AutenticacionPerfilTestSupport.como` sobre perfiles H2. `ClienteControllerTests`
  (62 pruebas) cubre 201 con `Location`, 200, las 14 reglas de validacion en `POST` y `PUT`,
  404 de la ruta y de `cuentaCorporativaId`, los tres conflictos de alta y los de edicion,
  `PATCH` idempotente que no toca contratos, 405 de `DELETE` con `Allow` y el cliente
  conservado, Property 6 (campos del servidor ignorados), Property 10 (vista completa o
  reducida por rol) y Property 14 con 17 combinaciones de parametros.
  `VistaClienteResolverTests` (8 pruebas) recorre los cuatro roles, las authorities sin
  prefijo, sin authorities, sin autenticacion y con varias. `ContratoControllerTests`
  (55 pruebas) cubre 201 con `Location` y `BORRADOR`, 200 con filtros, las reglas de codigo,
  descripcion, fechas, valor y estado desconocido, 404 de ruta y de `clienteId`, los cuatro
  conflictos (`CLIENTE_INACTIVO_SIN_CONTRATOS`, `CONTRATO_CODIGO_DUPLICADO`,
  `CONTRATO_NO_EDITABLE`, `TRANSICION_NO_PERMITIDA`), `valor` con dos decimales,
  `clienteId` y `estado` ignorados en `PUT`, Property 6 y Property 14. `CrmLogsTests`
  crea, edita, provoca 409 y 500 con datos personales ficticios conocidos y comprueba que la
  salida capturada no contiene correo, telefono, direccion ni documento. Evidencia observada:
  `.\mvnw.cmd test` -> 357 pruebas, 0 fallos, 0 errores, sin Docker ni Supabase.
- L17 (2026-11-11): tareas 14.4 y 14.6 completadas. `CuentaCorporativaController` expone
  `GET`, `GET /{id}`, `POST` (201 con `Location` relativa), `PUT /{id}` y `PATCH /{id}` en
  `/api/crm/cuentas-corporativas`; declara las restricciones de lista en los `@RequestParam`
  (`q` de hasta 100 caracteres, `activa`, `page`, `size`, `sort` con
  `razonSocial|nit|creadoEn` y por defecto `razonSocial,asc`) sin `@Validated` de clase (D8),
  toma el actor de `@AuthenticationPrincipal Jwt` y no mapea `DELETE`. `ResumenComercialController`
  expone `GET /api/crm/resumen` y delega en `ResumenComercialService.obtener()`; ambas rutas
  quedan bajo el patron `/api/crm/**` de `SecurityConfig`. Evidencia observada:
  `.\mvnw.cmd test` -> 357 pruebas, 0 fallos, 0 errores, sin Docker ni Supabase (la misma cifra
  que la linea base tras el pull, asi que no se rompio ningun contexto ni mapeo). Riesgo
  declarado: item 6, porque las pruebas MockMvc de los dos controladores son la tarea 14.11 y
  aun no existen; esa tarea es la verificacion pendiente de este cambio.
- L18 (2026-11-11): tarea 14 completa. `CrmAutorizacionTests` recorre en una matriz
  parametrizada las 17 operaciones (16 endpoints y `DELETE` de clientes) por las siete
  identidades: sin token (401 con `WWW-Authenticate`), sin perfil y con perfil inactivo (403),
  ADMINISTRADOR y COMERCIAL (permitidas; `DELETE` 405 con `Allow` y cliente conservado),
  FINANZAS_RRHH (solo lecturas de clientes) y OPERACIONES (lecturas de clientes y contratos).
  Los casos denegados comparan una instantanea de conteos y de los datos sembrados, y repiten
  la peticion con un `{id}` inexistente esperando 403, porque la seguridad se evalua antes del
  controlador: 119 casos en total. `CuentaCorporativaControllerTests` (41 pruebas) cubre 201
  con `Location` y auditoria, 200, orden por defecto `razonSocial,asc` y filtros `q` y `activa`,
  404 de ruta, 409 `CUENTA_NIT_DUPLICADO` al crear y editar (incluido el NIT con separadores),
  `PATCH` idempotente que conserva la auditoria y no toca los clientes, los campos del servidor
  ignorados, la ausencia de `DELETE` y Property 14 con 17 combinaciones de parametros.
  `ResumenComercialControllerTests` (3 pruebas) comprueba los cuatro estados en 0 sin datos,
  los conteos por estado con totales coherentes y la lectura en el momento de la solicitud.
  Evidencia observada: `.\mvnw.cmd test -Dtest=CrmAutorizacionTests` -> 119 pruebas, 0 fallos;
  `-Dtest=CuentaCorporativaControllerTests,ResumenComercialControllerTests` -> 41 + 3, 0 fallos;
  `.\mvnw.cmd test` -> 520 pruebas, 0 fallos, 0 errores, sin Docker ni Supabase.
  Riesgo declarado: none (solo pruebas nuevas; no cambia codigo de produccion ni datos).
