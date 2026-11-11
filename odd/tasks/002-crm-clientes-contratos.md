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

## Tasks

| ID | Specs | Ruta | Estado | Evidencia |
|---|---|---|---|---|
| T2 | S1, S2 | inline | completada | commit `47d3a4d`; SQL identico al diseno (113 lineas) y fila en el README |
| T3 | S1, S3, S4 | inline | completada | commit `185d984`; `CrmRepositoriosTests`: 8 pruebas en verde |
| T4 | S1, S6 | inline | completada | commit `628f827`; `git ls-files -s backend/mvnw` = `100755` |
| T5 | S1, S5 | inline | completada | commit `86f768a`; YAML validado con PyYAML contra Requirement 28 |
| T6 | S1, S7 | inline | completada | este commit; README §10 y `tech.md` |
| T7 | S8 | inline | completada | `.\mvnw.cmd clean test`: 66 pruebas en verde (commit previo) |

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
