# Feature: 002-crm-clientes-contratos — tareas 2 a 7

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
