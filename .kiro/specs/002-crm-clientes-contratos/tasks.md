# Implementation Plan: 002-crm-clientes-contratos

## Overview

Este plan convierte el diseño aprobado de `002-crm-clientes-contratos` en tareas pequeñas e incrementales: migración y persistencia del CRM; reglas y API REST de clientes, cuentas corporativas, contratos y resumen comercial; pruebas unitarias, de seguridad y de integración con PostgreSQL efímero; imagen Docker y Compose del backend; workflows de GitHub Actions; vistas CRM del frontend; y validación extremo a extremo con Supabase real.

Cada tarea indica **área**, **responsable** y **requisitos** (`requirements.md` e identificadores del MVP). Todo el trabajo se confirma en la rama `feat/crm-clientes-contratos` con Conventional Commits y se integra mediante un único Pull Request al completar la spec (DP-SDD-04, DP-SDD-05).

**Reparto de trabajo.** Cada responsable recibe una vertical completa (persistencia → servicio → controlador → pruebas) para reducir los cambios de contexto:

- **Davidson:** base de datos y migración CRM (preparación y aplicación controlada en `arise-corp-dev`), entidades y repositorios JPA, la vertical de cuentas corporativas y el resumen comercial (servicios, controladores y pruebas), infraestructura de las pruebas de integración, `MigracionesCrmIT`, contenedores Docker y Compose, workflows de CI, documentación (README, steering), validación extremo a extremo con Supabase real y entrega del PR.
- **Sebastián:** revisión de la migración, manejo de errores, normalización, máquina de estados, utilidades compartidas, DTO, servicios y controladores de clientes y contratos con sus pruebas, pruebas de autorización, logs y de integración de clientes y contratos.
- **Paula:** cliente HTTP, reglas de interfaz, layout privado y protección por rol, componentes compartidos, vistas del CRM y pruebas del frontend.

### Notas de alcance (del diseño aprobado)

- **Must:** CRM-01, CRM-03 y CRM-04. **Should incluidos:** CRM-02 (cuentas corporativas) y CRM-05 (resumen comercial).
- **Pruebas de propiedades (D13):** no se añade ninguna librería. Las propiedades de dominio pequeño se prueban de forma exhaustiva con `@ParameterizedTest` o `it.each`. Las de dominio amplio usan generadores con semilla fija (`GeneradoresCrm` en Java y `frontend/src/test/generadores.js`) con al menos 100 casos más los bordes conocidos. Cada prueba se titula `Feature: 002-crm-clientes-contratos, Property {n}: {texto}`.
- **Dependencias nuevas:** solo de prueba (`spring-boot-testcontainers`, `testcontainers-postgresql`) y el plugin `maven-failsafe-plugin`, sin fijar versiones que gestiona el parent (DP-TEC-04). El frontend no añade dependencias.
- **Sin variables de entorno nuevas:** `backend/.env.example` y `frontend/.env.example` no cambian (Requirement 29.7).
- **Migración (D18):** se revisa en la rama, se valida en PostgreSQL efímero con `MigracionesCrmIT` y se aplica de forma controlada en `arise-corp-dev` antes de la validación manual y del PR final. Una vez aplicada no se edita.
- `.\mvnw.cmd test` no necesita Docker; `.\mvnw.cmd verify` sí, porque ejecuta Testcontainers.

## Tasks

- [ ] 1. Preparar la rama y las reglas de la spec
  - [x] 1.1 Crear la rama `feat/crm-clientes-contratos` desde `main`
    - Área: integración/Git · Responsable: Sebastián
    - Actualizar `main` (`git switch main` y `git pull`), crear la rama con `git switch -c feat/crm-clientes-contratos` y publicarla con `git push -u origin feat/crm-clientes-contratos`
    - Confirmar en la rama `requirements.md`, `design.md` y `tasks.md` de `.kiro/specs/002-crm-clientes-contratos/` con `docs(crm): agregar spec 002-crm-clientes-contratos`
    - Comprobar con `git status` que ningún archivo `.env` queda preparado para commit
    - _Requisitos: 29.8, DP-SDD-03, DP-SDD-04, DP-SDD-05, NF-05_

  - [x] 1.2 Verificar y completar en los steering los ajustes de reglas que adopta el diseño
    - Área: documentación · Responsable: Davidson
    - `tech.md`: comprobar que la entrada DP-TEC-03 coincide literalmente con el texto registrado en "Aclaración de DP-TEC-03" del diseño (D18), que ya está escrito en el steering; si difiere, alinearlo. Este ajuste no se redacta de nuevo en esta tarea
    - `api-conventions.md`, sección "Validaciones": sustituir la frase sobre `@Validated` en el controlador por la validación de métodos integrada de Spring MVC, con las restricciones en los `@RequestParam` y sin `@Validated` a nivel de clase (D8)
    - `structure.md`, DP-EST-05, sección "Pruebas" y entrada `unit/` del árbol del monorepo: las `*Tests` van en el paquete de la clase probada y el paquete `unit` no se usa; las `*IT` siguen en `integration` (D19)
    - Se hace antes del código que aplica estas reglas (`sdd-workflow.md`, "Registro de decisiones"); DP-TEC-03 ya está registrado en `tech.md`, y los ajustes de D8 y D19 se confirman con `docs(crm): registrar en steering los ajustes de la spec 002`. El estado [Implementado] de cada área se actualiza en las tareas 6.2, 19.2 y 29.1
    - _Requisitos: 29.5, 29.10, NF-05, DP-TEC-03, DP-EST-05_

- [x] 2. Crear la migración del CRM
  - [x] 2.1 Crear `supabase/migrations/<timestamp>_app_crm_clientes_contratos.sql`
    - Área: base de datos · Responsable: Davidson
    - Generar el archivo con `supabase migration new app_crm_clientes_contratos`; el timestamp debe ser posterior a `20261108000000`. No modificar `20261108000000_app_perfiles_usuario.sql`
    - Escribir el SQL de "Migración CRM" del diseño: `app.cuentas_corporativas`, `app.clientes` y `app.contratos` en orden de dependencia, con `create table` sin `if not exists`; PK UUID; `UNIQUE` de `nit`, `(tipo_documento, numero_documento)` y `codigo`; `CHECK` de tipo y formato de documento, NIT, longitudes, teléfono, estado, `valor >= 0` y fechas; FK `cuenta_corporativa_id` y `cliente_id`; índices de FK y de `estado`; auditoría `timestamptz` y `uuid not null` sin FK a `auth.users` (D11); sin columna de cuenta en `contratos`
    - Habilitar RLS en las tres tablas y ejecutar `revoke all ... from anon, authenticated`. Sin `insert`, UUID personales, secretos ni sentencias que no admitan transacción
    - _Requisitos: 2.1–2.9, CRM-01, CRM-02, CRM-03, CRM-04, NF-01, NF-03, NF-05, DP-SEG-05, DP-SEG-06, DP-API-04_

  - [x] 2.2 Registrar la migración en `supabase/migrations/README.md`
    - Área: base de datos · Responsable: Davidson
    - Añadir la fila de la migración CRM a la tabla (archivo, descripción y `002-crm-clientes-contratos`)
    - Sustituir las reglas "se revisan en PR" y "Davidson prepara y aplica ... tras el merge" por la aclaración de DP-TEC-03 ya registrada en `tech.md` y verificada en la tarea 1.2: otro integrante revisa antes de aplicar; Davidson la aplica de forma controlada después del merge como regla general y también antes del PR final cuando la validación extremo a extremo lo requiere y la migración es aditiva; nunca se edita una migración aplicada
    - _Requisitos: 29.6, 29.10, NF-05, DP-TEC-03_

- [x] 3. Implementar las entidades y los repositorios JPA del CRM
  - [x] 3.1 Crear las enumeraciones `TipoDocumento` y `EstadoContrato` en `crm.entity`
    - Área: backend · Responsable: Davidson
    - `TipoDocumento { CC, CE, NIT, PASAPORTE }` y `EstadoContrato { BORRADOR, ACTIVO, FINALIZADO, CANCELADO }`, con la misma ubicación que `RolUsuario` en `login.entity`. Las reglas de transición se añaden en la tarea 10.3
    - _Requisitos: 1.1, 2.3, 2.5, DP-API-04_

  - [x] 3.2 Implementar las entidades `CuentaCorporativa` y `Cliente`
    - Área: backend · Responsable: Davidson
    - Mapeo según "Entidades JPA" del diseño: `@Table` sin esquema, `@Id @GeneratedValue(strategy = GenerationType.UUID)`, `@Enumerated(EnumType.STRING)`, `creadoEn` con `updatable = false`, restricciones `UNIQUE` repetidas en las anotaciones y `@ManyToOne(fetch = FetchType.LAZY)` de `Cliente` a `CuentaCorporativa`, sin colecciones inversas (D1)
    - Sin setters públicos: `static crear(..., UUID actor, Instant ahora)`, `actualizarDatos(..., actor, ahora)` y `boolean cambiarEstado(boolean, actor, ahora)`, que devuelve `false` y no toca la auditoría si el valor no cambia (D10)
    - _Requisitos: 1.1, 1.4, 2.2, 2.3, 2.7, 2.10, 8.3, 9.10, DP-ARQ-01, DP-SEG-06_

  - [x] 3.3 Implementar la entidad `Contrato`
    - Área: backend · Responsable: Davidson
    - `@ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(updatable = false) Cliente cliente`; `valor` `BigDecimal` con `precision = 15, scale = 2`; `estado` de tipo `EstadoContrato`; `estadoActualizadoEn`, `estadoActualizadoPor` y auditoría de datos
    - `static crear(...)` en `BORRADOR`, con la auditoría de estado igual a la de datos; `actualizarDatos(..., actor, ahora)` cambia solo los datos y `actualizado_*`; `cambiarEstado(destino, actor, ahora)` cambia solo `estado` y su auditoría (D10). Las defensas de invariantes de la máquina de estados se añaden en la tarea 10.3
    - _Requisitos: 2.4–2.7, 2.10, 11.1, 13.4, 14.1, CRM-03, DP-SEG-06_

  - [x] 3.4 Crear los repositorios del CRM
    - Área: backend · Responsable: Davidson
    - `CuentaCorporativaRepository`, `ClienteRepository` y `ContratoRepository` con las firmas de "Backend: repositorios": `JpaSpecificationExecutor`, búsquedas por NIT, documento y código, `@EntityGraph` contra N+1, métodos con `@Lock(PESSIMISTIC_READ)` o `@Lock(PESSIMISTIC_WRITE)` sin *fetch join* (D9) y consultas agrupadas `contarPorActivo` y `contarPorEstado`
    - Crear la proyección `ConteoPorClave<K>`; si Spring Data no resuelve la proyección genérica, usar `ConteoPorActivo` y `ConteoPorEstado` sin cambiar el contrato que consume el servicio
    - _Requisitos: 1.1, 5.1, 9.6, 12.1, 15.1, CRM-01, CRM-03, CRM-05_

  - [x]* 3.5 Escribir una prueba de humo de los repositorios en H2
    - Área: backend · Responsable: Davidson
    - `crm/repository/CrmRepositoriosTests` con `@DataJpaTest` (comprobar el paquete de la anotación en la referencia de Spring Boot 4.1.2): guardar y leer las tres entidades, búsquedas derivadas, `@EntityGraph` y resolución de las proyecciones de conteo, con datos ficticios
    - _Requisitos: 2.10, 15.1, NF-07_

- [x] 4. Preparar `mvnw` para los runners Linux
  - [x] 4.1 Fijar el bit ejecutable de `backend/mvnw` en Git
    - Área: CI · Responsable: Davidson
    - Ejecutar `git update-index --chmod=+x backend/mvnw`, confirmar con `chore(ci): marcar mvnw como ejecutable` y comprobar con `git ls-files -s backend/mvnw` que el modo es `100755`
    - No cambiar los finales de línea que fija `backend/.gitattributes` (D16)
    - _Requisitos: 28.7, 28.8, ENG-04_

- [x] 5. Crear los workflows de GitHub Actions
  - [x] 5.1 Crear `.github/workflows/backend-ci.yml`
    - Área: CI · Responsable: Davidson
    - YAML del diseño: `push` a `'**'`, `pull_request` hacia `main` y `workflow_dispatch`, sin filtros de rutas (D17); `concurrency` por workflow y ref con `cancel-in-progress: true`; `permissions: contents: read`; job `backend-ci` en `ubuntu-latest` con `timeout-minutes: 20` y `working-directory: backend`
    - Pasos: `actions/checkout@v7` con `persist-credentials: false`, `actions/setup-java@v6` (Temurin 21 y `cache: maven`), `./mvnw -B test` y `docker build --pull -t arise-backend:ci .`, sin publicar la imagen ni iniciar sesión en registros. Antes de fijar las versiones mayores del diseño, comprobarlas en las páginas de *releases* de cada acción y usar las mismas en los tres workflows
    - El paso `docker build` necesita `backend/Dockerfile` (tarea 18.1); hasta entonces el job termina en rojo en la rama, con el fallo solo en ese paso. Se acepta sin añadir condiciones al YAML (diseño, "Riesgos y limitaciones"): en ese periodo, el estado de las pruebas se lee en el paso `./mvnw -B test`
    - _Requisitos: 28.1–28.7, 28.10, 28.11, ENG-01, ENG-02, ENG-04, NF-01_

  - [x] 5.2 Crear `.github/workflows/integration-tests.yml`
    - Área: CI · Responsable: Davidson
    - Mismos disparadores, `concurrency` y permisos; job `integration-tests` con `timeout-minutes: 30`, `actions/checkout@v7` con `persist-credentials: false`, `actions/setup-java@v6` (Temurin 21 con caché de Maven) y `./mvnw -B verify` desde `backend/` con el Docker del runner. Sin variables de Supabase ni `secrets.*`
    - Hasta la tarea 16.1 `verify` solo ejecuta Surefire y el empaquetado, así que este workflow puede estar en verde desde su creación
    - _Requisitos: 28.1–28.6, 28.8, 28.10, 28.11, ENG-03, DP-TEC-04_

  - [x] 5.3 Crear `.github/workflows/frontend-ci.yml`
    - Área: CI · Responsable: Davidson
    - Mismos disparadores, `concurrency` y permisos; job `frontend-ci` con `timeout-minutes: 15` y `working-directory: frontend`; `actions/checkout@v7` con `persist-credentials: false` y `actions/setup-node@v7` con `node-version-file: frontend/.nvmrc`, `cache: npm` y `cache-dependency-path: frontend/package-lock.json`
    - Pasos: `npm ci`, `npm test`, `npm run build` con `VITE_SUPABASE_URL`, `VITE_SUPABASE_PUBLISHABLE_KEY` y `VITE_API_BASE_URL` ficticias declaradas solo en ese paso, y `npm run check:secrets`. Comprobar que los valores ficticios no coinciden con los patrones de `scripts/check-secrets.mjs`
    - _Requisitos: 25.6, 28.1–28.6, 28.9, 28.10, 28.11, ENG-02, ENG-04, NF-01_

- [x] 6. Documentar la integración continua
  - [x] 6.1 Actualizar el README §10
    - Área: documentación · Responsable: Davidson
    - Describir los tres workflows, sus disparadores, los comandos que ejecutan, la ausencia de secretos y de credenciales de `arise-corp-dev`, y los checks `backend-ci`, `integration-tests` y `frontend-ci` como obligatorios en la protección de `main`
    - _Requisitos: 29.3, 29.9, ENG-04_

  - [x] 6.2 Actualizar la sección "GitHub Actions y CI" de `.kiro/steering/tech.md`
    - Área: documentación · Responsable: Davidson
    - Marcar CI como [Implementado] con los nombres de los workflows y las versiones mayores de las acciones, y registrar el bit ejecutable de `mvnw`. La aclaración de DP-TEC-03 ya quedó registrada en la tarea 1.2; el resto de `tech.md` se actualiza en la tarea 19.2
    - _Requisitos: 29.5, ENG-04_

- [x] 7. Checkpoint de persistencia y CI
  - Área: verificación · Responsable: Davidson
  - Ejecutar `.\mvnw.cmd clean test` desde `backend/`: el contexto arranca con las entidades CRM en H2 y las pruebas existentes pasan. Comprobar `git ls-files -s backend/mvnw` (`100755`) y revisar el YAML contra el Requirement 28. Asegurar que todas las pruebas pasan; consultar al usuario si surgen dudas.

- [ ] 8. Revisar la migración del CRM
  - [ ] 8.1 Revisar la migración en la rama antes de aplicarla
    - Área: base de datos · Responsable: Sebastián
    - Contrastar el SQL con "Migración CRM" y "Entidades JPA" del diseño: columnas, tipos, `CHECK`, `UNIQUE`, FK, índices, RLS, `revoke` y ausencia de datos. Los cambios que pida la revisión se hacen en el mismo archivo, porque todavía no está aplicado
    - No la apliques tú: la aplicación controlada es la tarea 17.1 y la realiza Davidson, autor de la migración, después de `MigracionesCrmIT` y de esta revisión
    - _Requisitos: 2.1–2.9, 29.10, DP-TEC-03, DP-SEG-05_

- [ ] 9. Ampliar el manejo de errores en `shared.error`
  - [ ] 9.1 Crear `ConflictoNegocioException` y ampliar `RecursoNoEncontradoException`
    - Área: backend · Responsable: Sebastián
    - `ConflictoNegocioException(conflicto, detalle, campo, extensiones)` con código estable en `UPPER_SNAKE_CASE` (D5); nuevo constructor `RecursoNoEncontradoException(String mensaje, String campo)` que conserva el actual
    - _Requisitos: 10.3, 11.10, 17.4, CA-06, DP-API-02_

  - [ ] 9.2 Ampliar `GlobalExceptionHandler`
    - Área: backend · Responsable: Sebastián
    - Aplicar la tabla "Cambios en GlobalExceptionHandler": 409 con `conflicto`, `campo` y extensiones; `campo` en 404; deduplicación de `errors` por campo; `HttpMessageNotReadableException` con el campo de la `tools.jackson.databind.DatabindException`; `handleTypeMismatch` con el nombre del parámetro; 405 en español que conserva `Allow`; 415 en español; `ConcurrencyFailureException` → 409
    - Mensajes según el tipo de destino, sin nombres de clases. No registrar los mensajes de excepciones de datos; el 500 registra la traza pero no la devuelve
    - _Requisitos: 8.6, 17.1–17.6, CA-06, DP-API-02_

  - [ ] 9.3 Ampliar `ErroresApiTests`
    - Área: backend · Responsable: Sebastián
    - Casos: conflicto con extensiones, `campo` en 404, error de tipo en el cuerpo con su campo, error de parámetro, 405 con `Allow`, 415 y deduplicación de `errors`
    - **Property 17: Los errores no exponen información interna** (todas las respuestas de la clase) · **Validates: Requirements 17.1, 17.3, 17.5, 17.6**
    - _Requisitos: 17.1–17.6, CA-06, NF-07_

- [ ] 10. Implementar la normalización y la máquina de estados
  - [ ] 10.1 Implementar `shared.normalizacion.NormalizadorTexto`
    - Área: backend · Responsable: Sebastián
    - Clase `final` con métodos estáticos puros `texto`, `documento`, `codigo` y `patronContiene` (escapa `\`, `%` y `_`), según el diseño (D2)
    - _Requisitos: 3.1–3.5, CRM-04_

  - [ ] 10.2 Escribir `NormalizadorTextoTests`
    - Área: backend · Responsable: Sebastián
    - Crear `GeneradoresCrm` con semilla fija en el paquete de prueba; ejemplos de `patronContiene` y de `1.023.456-7` → `10234567`
    - **Property 1: La normalización es idempotente** · **Validates: Requirements 3.2, 3.4, 3.5**
    - **Property 2: Las variantes de formato de un documento son el mismo documento** · **Validates: Requirements 3.1, 3.3**
    - _Requisitos: 23.3, NF-07, CRM-04_

  - [ ] 10.3 Añadir las transiciones a `EstadoContrato` y las defensas de `Contrato`
    - Área: backend · Responsable: Sebastián
    - `transicionesPermitidas()`, `puedeTransicionarA(destino)` y `esFinal()` con las cuatro Transicion_Permitida del diseño
    - En `Contrato`, `actualizarDatos` lanza `IllegalStateException` fuera de `BORRADOR` y `cambiarEstado` la lanza ante una transición no permitida. Son defensas de invariantes: el servicio comprueba la regla antes
    - _Requisitos: 13.3, 14.1, 14.2, CRM-03, DP-API-05_

  - [ ] 10.4 Escribir `EstadoContratoTests`
    - Área: backend · Responsable: Sebastián
    - `@ParameterizedTest` exhaustivo sobre los 16 pares de origen y destino, y sobre `esFinal`
    - **Property 11: Solo se aceptan las cuatro transiciones permitidas** (nivel enumeración) · **Validates: Requirements 14.1, 14.2, 23.2**
    - _Requisitos: 23.2, NF-07_

- [ ] 11. Implementar las utilidades compartidas de paginación, reloj y validación
  - [ ] 11.1 Implementar `shared.paginacion.PaginaResponse` y `Paginacion`
    - Área: backend · Responsable: Sebastián
    - `PaginaResponse<T>.desde(Page<T>)` con `content`, `page`, `size`, `totalElements` y `totalPages`; `Paginacion.crear(page, size, sort, camposPermitidos)` con desempate `id,asc` e `IllegalArgumentException` ante un campo no permitido (D7)
    - _Requisitos: 5.2, 9.6, 12.1, DP-API-03_

  - [ ]* 11.2 Escribir `PaginacionTests`
    - Área: backend · Responsable: Sebastián
    - Desempate por `id`, rechazo de campos fuera de la lista y conservación de los metadatos en `PaginaResponse.desde`
    - **Property 14: Los parámetros de lista se aceptan o rechazan según DP-API-03** (parte unitaria) · **Validates: Requirements 5.2, 5.6, 9.7, 12.3**
    - _Requisitos: 5.2, NF-07_

  - [ ] 11.3 Crear `config.ClockConfig`
    - Área: backend · Responsable: Sebastián
    - `@Bean Clock clock()` con `Clock.systemUTC()` (D10)
    - _Requisitos: 14.1, DP-SEG-06_

  - [ ] 11.4 Crear las restricciones compuestas de `shared.validacion`
    - Área: backend · Responsable: Sebastián
    - `@Correo`, `@Telefono` y `@ValorCop` con `@ReportAsSingleViolation`; aceptan `null` y declaran sus mensajes en español (D3)
    - _Requisitos: 4.6, 9.4, 11.8, 17.2, CRM-04_

- [ ] 12. Implementar los DTO del CRM
  - [ ] 12.1 Crear los DTO de entrada y sus validadores de clase
    - Área: backend · Responsable: Sebastián
    - `ClienteRequest`, `EstadoClienteRequest`, `CuentaCorporativaRequest`, `EstadoCuentaRequest`, `ContratoCrearRequest`, `ContratoActualizarRequest` (sin `clienteId` ni `estado`) y `EstadoContratoRequest` como `record` con `@JsonIgnoreProperties(ignoreUnknown = true)`, normalización en el constructor compacto, `@NotNull` separado del formato y mensajes en español
    - `@NumeroDocumentoValido` (informa sobre `numeroDocumento`) y `@RangoFechasContrato` (informa sobre `fechaFin`) en `crm.dto.validacion`; `toString()` sin datos personales en `ClienteRequest` y `CuentaCorporativaRequest`
    - _Requisitos: 3.1, 3.4, 3.5, 4.2–4.6, 4.9, 6.4, 9.2–9.4, 11.2–11.8, 11.12, 13.2, 13.4, CRM-04_

  - [ ] 12.2 Crear los DTO de salida y `VistaCliente`
    - Área: backend · Responsable: Sebastián
    - `ClienteVista` (interfaz sellada), `ClienteResponse`, `ClienteResumenResponse` (sin `correo`, `telefono`, `direccion` ni auditoría), `CuentaCorporativaRef`, `ClienteRef`, `CuentaCorporativaResponse`, `ContratoResponse` con `valor` como texto de dos decimales y `ResumenComercialResponse` con un `EnumMap` de los cuatro estados; `toString()` sin datos personales en `ClienteResponse` y `CuentaCorporativaResponse`
    - `crm.service.VistaCliente { COMPLETA, REDUCIDA }`
    - _Requisitos: 6.1, 6.2, 6.4, 10.6, 12.4, 12.5, 15.1, 15.2, DP-PRO-01, DP-API-04_

  - [ ]* 12.3 Escribir las pruebas de validación de los DTO de entrada
    - Área: backend · Responsable: Sebastián
    - `ClienteRequestValidacionTests`, `CuentaCorporativaRequestValidacionTests` y `ContratoRequestValidacionTests` con `jakarta.validation.Validator` y `GeneradoresCrm`
    - **Property 3: Las validaciones de formato coinciden con la regla de referencia** · **Validates: Requirements 4.4, 4.5, 4.6, 7.2, 9.3, 9.4, 11.4, 11.5, 11.7, 11.8, 13.2**
    - **Property 4: Un error por cada campo inválido** · **Validates: Requirements 4.2, 4.3, 9.2, 11.2, 11.3, 17.2**
    - _Requisitos: 23.7, NF-07_

  - [ ]* 12.4 Escribir `SerializacionCrmTests`
    - Área: backend · Responsable: Sebastián
    - `@SpringBootTest` con el `ObjectMapper` de la aplicación
    - **Property 5: El valor del contrato viaja como texto sin perder precisión** · **Validates: Requirements 12.5**
    - **Property 6: Los campos controlados por el servidor no se toman del cuerpo** (propiedades desconocidas ignoradas) · **Validates: Requirements 4.9, 11.12, 13.4**
    - _Requisitos: 12.5, NF-07_

- [ ] 13. Implementar los servicios del CRM
  - [ ] 13.1 Crear `ConflictosCrm` y `EspecificacionesCrm`
    - Área: backend · Responsable: Sebastián
    - `crm.service.ConflictosCrm`: una fábrica por cada fila del catálogo de conflictos (código, `campo`, `detail` y extensiones), sin datos personales
    - `crm.repository.EspecificacionesCrm`: `nombreODocumentoContiene`, `activoIgual`, `cuentaIgual`, `razonSocialONitContiene`, `activaIgual`, `clienteIgual` y `estadoIgual` con JPQL portable, `LIKE ... ESCAPE '\'` y filtros ausentes ignorados
    - _Requisitos: 5.3–5.5, 9.6, 12.1, 17.4, CRM-01, CRM-04_

  - [ ] 13.2 Implementar `CuentaCorporativaService`
    - Área: backend · Responsable: Davidson
    - `listar`, `obtener`, `crear`, `actualizar` y `cambiarEstado` según el diseño: NIT duplicado, de cuenta activa o inactiva → `CUENTA_NIT_DUPLICADO`; conservación de `activa` y `creadoEn`; `PATCH` idempotente que no toca clientes; `@Transactional`, `Clock`, actor con `UUID.fromString(sub)` y conversión a DTO dentro de la transacción
    - _Requisitos: 9.1–9.13, 16.6, CRM-02, CRM-04, DP-SEG-06_

  - [ ] 13.3 Implementar `ClienteService`
    - Área: backend · Responsable: Sebastián
    - `listar`, `obtener`, `crear`, `actualizar` y `cambiarEstado` con los pasos del diseño: cuenta cargada con `PESSIMISTIC_READ` (404 con `campo`), `CLIENTE_DUPLICADO`, `CLIENTE_INACTIVO_EXISTENTE` con `clienteId`, regla de asignación de cuenta (`CUENTA_INACTIVA`), vista completa o reducida, conservación de `activo` y `creadoEn`, y desactivación sin tocar contratos. No registrar datos de clientes en logs
    - _Requisitos: 4.1, 4.7–4.9, 5.1–5.8, 6.1–6.4, 7.1–7.6, 8.1–8.5, 8.7, 10.1–10.6, 16.6, CRM-01, CRM-02, CRM-04_

  - [ ] 13.4 Implementar `ContratoService`
    - Área: backend · Responsable: Sebastián
    - `listar` (lista vacía con un `clienteId` inexistente), `obtener`, `crear` (cliente con `PESSIMISTIC_READ`, `CLIENTE_INACTIVO_SIN_CONTRATOS`, `CONTRATO_CODIGO_DUPLICADO`), y `actualizar` y `cambiarEstado` con `PESSIMISTIC_WRITE`, `CONTRATO_NO_EDITABLE` y `TRANSICION_NO_PERMITIDA` con `estadoActual` y `estadoSolicitado`
    - `cambiarEstado` no consulta el estado del cliente y solo actualiza la auditoría de estado (D9, D10)
    - _Requisitos: 11.1, 11.9–11.12, 12.1–12.6, 13.1, 13.3–13.6, 14.1–14.6, 16.6, CRM-03, CRM-04, DP-API-05, DP-SEG-06_

  - [ ] 13.5 Implementar `ResumenComercialService`
    - Área: backend · Responsable: Davidson
    - Dos consultas agrupadas en una transacción de solo lectura; `EnumMap` con los cuatro estados en 0 y totales calculados como suma de sus componentes
    - _Requisitos: 15.1–15.3, CRM-05, CA-04_

  - [ ] 13.6 Escribir `ClienteServiceTests`
    - Área: backend · Responsable: Sebastián
    - Mockito y `Clock.fixed`: creación y edición, conservación de `activo` y `creadoEn`, `actualizadoPor = sub`, desactivación sin tocar contratos y documento duplicado con variantes
    - **Property 7: Los duplicados se detectan sobre el valor normalizado** (documento) · **Validates: Requirements 4.7, 4.8**
    - **Property 8: Activar y desactivar es idempotente** (clientes) · **Validates: Requirements 8.1, 8.2, 8.3**
    - **Property 9: Las referencias inactivas no se asignan** (combinaciones de cuenta actual y destino) · **Validates: Requirements 10.1, 10.2, 10.4, 10.5**
    - _Requisitos: 23.1, NF-07, CRM-01, CRM-04_

  - [ ] 13.7 Escribir `CuentaCorporativaServiceTests`
    - Área: backend · Responsable: Davidson
    - Mockito y `Clock.fixed`: NIT duplicado con variantes (mayúsculas, espacios y guiones) y conservación de los clientes al desactivar una cuenta
    - **Property 7: Los duplicados se detectan sobre el valor normalizado** (NIT) · **Validates: Requirements 7.4, 9.5**
    - **Property 8: Activar y desactivar es idempotente** (cuentas) · **Validates: Requirements 9.10**
    - _Requisitos: 23.1, NF-07, CRM-02, CRM-04_

  - [ ] 13.8 Escribir `ContratoServiceTests`
    - Área: backend · Responsable: Sebastián
    - Creación en `BORRADOR` con auditoría, cliente inactivo, código duplicado con variantes y auditoría de estado con el `sub` del actor
    - **Property 11: Solo se aceptan las cuatro transiciones permitidas** (con cliente activo e inactivo) · **Validates: Requirements 14.1, 14.2, 14.5**
    - **Property 12: Solo se editan contratos en borrador** · **Validates: Requirements 13.1, 13.3**
    - **Property 9: Las referencias inactivas no se asignan** (cliente inactivo sin contratos nuevos) · **Validates: Requirements 11.11**
    - _Requisitos: 23.1, 23.2, NF-07, CRM-03_

  - [ ]* 13.9 Escribir `ResumenComercialServiceTests`
    - Área: backend · Responsable: Davidson
    - **Property 13: El resumen comercial es consistente** (conteos simulados generados) · **Validates: Requirements 15.1, 15.2, 15.3**
    - _Requisitos: 15.1–15.3, NF-07_

- [ ] 14. Implementar los controladores del CRM y sus pruebas MockMvc
  - [ ] 14.1 Retirar los dobles CRM de `AutorizacionModulosTests`
    - Área: backend · Responsable: Sebastián
    - Eliminar los endpoints de prueba `GET /api/crm/clientes`, `GET /api/crm/clientes/{id}`, `POST /api/crm/clientes` y `GET /api/crm/contratos/{id}`; conservar `GET /api/erp/proveedores` y `GET /api/sga/paquetes`
    - Reasignar `sinTokenDevuelve401`, `sinPerfilActivoDevuelve403EnCualquierModulo` y `perfilInactivoDevuelve403EnCualquierModulo` a ERP y SGA; `administradorAccedeATodosLosModulos` conserva ERP y SGA; los casos CRM pasan a `CrmAutorizacionTests` (14.8)
    - Confirmar este cambio en el mismo commit que los controladores 14.3–14.6: si los dobles y los controladores reales conviven, el contexto falla por mapeo ambiguo
    - _Requisitos: 23.8, CA-03_

  - [ ] 14.2 Implementar `VistaClienteResolver`
    - Área: backend · Responsable: Sebastián
    - `desde(Authentication)` devuelve `COMPLETA` solo con `ROLE_ADMINISTRADOR` o `ROLE_COMERCIAL` (construidas con `PerfilJwtAuthenticationConverter.PREFIJO_ROL` y `RolUsuario`) y `REDUCIDA` en cualquier otro caso; no lee parámetros de la petición (D6)
    - _Requisitos: 6.1–6.3, NF-02, DP-PRO-01_

  - [ ] 14.3 Implementar `ClienteController`
    - Área: backend · Responsable: Sebastián
    - `GET`, `GET /{id}`, `POST` (201 con `Location` relativa), `PUT /{id}` y `PATCH /{id}` en `/api/crm/clientes`; restricciones en los `@RequestParam` sin `@Validated` a nivel de clase (D8); actor tomado de `@AuthenticationPrincipal Jwt`; sin `DELETE` mapeado, de modo que el gestor recibe 405
    - _Requisitos: 1.2, 1.3, 4.1, 5.1–5.9, 7.1, 8.1–8.7, 16.6, CRM-01_

  - [ ] 14.4 Implementar `CuentaCorporativaController`
    - Área: backend · Responsable: Davidson
    - `GET`, `GET /{id}`, `POST`, `PUT /{id}` y `PATCH /{id}` en `/api/crm/cuentas-corporativas`, con sus parámetros y el orden por defecto `razonSocial,asc`
    - _Requisitos: 1.2, 1.3, 9.1–9.13, CRM-02_

  - [ ] 14.5 Implementar `ContratoController`
    - Área: backend · Responsable: Sebastián
    - `GET`, `GET /{id}`, `POST`, `PUT /{id}` y `PATCH /{id}/estado` en `/api/crm/contratos`, con los filtros `clienteId` y `estado` y el orden por defecto `creadoEn,desc`
    - _Requisitos: 1.2, 1.3, 11.1, 12.1–12.6, 13.1, 14.1, CRM-03_

  - [ ] 14.6 Implementar `ResumenComercialController`
    - Área: backend · Responsable: Davidson
    - `GET /api/crm/resumen`
    - _Requisitos: 1.2, 15.1, CRM-05_

  - [ ] 14.7 Crear `CrmDatosPrueba`
    - Área: backend · Responsable: Sebastián
    - `CrmDatosPrueba` (paquete de prueba `com.arise.corp.crm`) crea perfiles, cuentas, clientes y contratos ficticios y limpia los repositorios en `@BeforeEach`. La autenticación usa `AutenticacionPerfilTestSupport.como(...)` con el `PerfilJwtAuthenticationConverter` real sobre perfiles persistidos en H2, sin tokens reales de Supabase
    - _Requisitos: 16.1–16.5, 23.4–23.6, NF-07_

  - [ ] 14.8 Escribir `CrmAutorizacionTests`
    - Área: backend · Responsable: Sebastián
    - Matriz exhaustiva: 17 operaciones (16 endpoints y `DELETE`) × {sin token, sin perfil, perfil inactivo, ADMINISTRADOR, COMERCIAL, FINANZAS_RRHH, OPERACIONES}. En los casos denegados, datos sin cambios y 403 también con un `{id}` inexistente; `DELETE` con rol gestor → 405 y cliente conservado; lecturas cruzadas de FINANZAS_RRHH y OPERACIONES
    - **Property 16: La matriz de acceso se aplica a todos los endpoints** · **Validates: Requirements 16.1, 16.2, 16.3, 16.4, 16.5, 23.4, 23.6**
    - _Requisitos: 8.6, 16.1–16.5, 23.4–23.6, CA-02, CA-03, NF-02, NF-07_

  - [ ] 14.9 Escribir `ClienteControllerTests` y `VistaClienteResolverTests`
    - Área: backend · Responsable: Sebastián
    - 201 con `Location`, 200, 400 por cada regla, 404 del cliente y de `cuentaCorporativaId` (con `campo`), 409 por cada conflicto con su `conflicto` y `campo`, y 405 de `DELETE`. `VistaClienteResolverTests` exhaustivo para cada authority, sin authorities y con varias
    - **Property 10: La vista del cliente depende solo del rol** · **Validates: Requirements 6.1, 6.2, 6.3, 10.6**
    - **Property 6: Los campos controlados por el servidor no se toman del cuerpo** · **Validates: Requirements 4.1, 4.9, 16.6**
    - **Property 14: Los parámetros de lista se aceptan o rechazan según DP-API-03** · **Validates: Requirements 5.2, 5.6**
    - _Requisitos: 4.1–4.9, 5.1–5.9, 7.1–7.6, 8.1–8.7, 10.3, 10.4, 23.7, NF-07_

  - [ ] 14.10 Escribir `ContratoControllerTests`
    - Área: backend · Responsable: Sebastián
    - 201 con `Location`, 200, 400 por cada regla (código, descripción, fechas, valor y `estado` desconocido), 404 de la ruta y de `clienteId`, y 409 `CLIENTE_INACTIVO_SIN_CONTRATOS`, `CONTRATO_CODIGO_DUPLICADO`, `CONTRATO_NO_EDITABLE` y `TRANSICION_NO_PERMITIDA`; `valor` serializado con dos decimales; `clienteId` y `estado` ignorados en `PUT`
    - **Property 6: Los campos controlados por el servidor no se toman del cuerpo** · **Validates: Requirements 11.1, 11.12, 13.4**
    - **Property 14: Los parámetros de lista se aceptan o rechazan según DP-API-03** · **Validates: Requirements 12.3**
    - _Requisitos: 11.1–11.12, 12.1–12.6, 13.1–13.6, 14.1–14.6, 23.7, NF-07_

  - [ ]* 14.11 Escribir `CuentaCorporativaControllerTests` y `ResumenComercialControllerTests`
    - Área: backend · Responsable: Davidson
    - Cuentas: 201, 200, 400, 404, 409 `CUENTA_NIT_DUPLICADO`, `PATCH` idempotente y parámetros de lista. Resumen: los cuatro estados presentes, con 0 cuando no hay contratos, y totales coherentes
    - **Property 14: Los parámetros de lista se aceptan o rechazan según DP-API-03** · **Validates: Requirements 9.7**
    - _Requisitos: 9.1–9.13, 15.1–15.3, 23.7, NF-07_

  - [ ] 14.12 Escribir `CrmLogsTests`
    - Área: backend · Responsable: Sebastián
    - Con `OutputCaptureExtension`, crear, editar y provocar 409 y 500 con datos personales ficticios conocidos; la salida capturada no contiene el correo, el teléfono, la dirección ni el número de documento
    - _Requisitos: 6.4, 17.6, CA-06_

- [ ] 15. Checkpoint del backend sin Docker
  - Área: verificación · Responsable: Sebastián
  - Ejecutar `.\mvnw.cmd clean test` desde `backend/` sin Docker, sin Supabase y sin `.env` (Requirement 23.9). Asegurar que todas las pruebas pasan; consultar al usuario si surgen dudas.

- [ ] 16. Implementar las pruebas de integración con PostgreSQL efímero
  - [x] 16.1 Añadir Testcontainers y Failsafe a `backend/pom.xml`
    - Área: backend · Responsable: Davidson
    - Dependencias de alcance `test` `org.springframework.boot:spring-boot-testcontainers` y `org.testcontainers:testcontainers-postgresql`, sin versión; `maven-failsafe-plugin` con la inclusión `**/integration/**/*IT.java`, la propiedad `arise.migraciones.dir=${project.basedir}/../supabase/migrations` y los objetivos `integration-test` y `verify`
    - Sin `testcontainers-junit-jupiter` (D12) y sin configurar Surefire, para que `.\mvnw.cmd test` no recoja las `*IT`
    - _Requisitos: 23.9, 24.1, 24.2, DP-TEC-04, DP-EST-05_

  - [x] 16.2 Crear la infraestructura de las IT
    - Área: backend · Responsable: Davidson
    - `src/test/resources/application-it.properties` (perfil `it`, `ddl-auto=validate`, `default_schema=app`, `spring.sql.init.mode=never`) y `src/test/resources/integration/preparacion-supabase.sql` (roles `anon` y `authenticated`, `auth.users` mínima y privilegios por defecto desfavorables)
    - En `com.arise.corp.integration`: `MigracionesSupabase.aplicar` (script previo y migraciones ordenadas por nombre, cada una en su transacción, con error que nombra el archivo), `PostgresContenedorConfig` (`@Bean @ServiceConnection` con `postgres:17-alpine`, alineada con la versión mayor de `arise-corp-dev`) y `PostgresIntegracionBase` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("it")`, `truncate ... cascade` en `@BeforeEach` y ayudante para un `auth.users` ficticio)
    - Ningún archivo usa credenciales de `arise-corp-dev`, `.env` ni red hacia Supabase
    - _Requisitos: 24.3, 24.4, 24.10, ENG-03, NF-01_

  - [x] 16.3 Escribir `MigracionesCrmIT`
    - Área: backend · Responsable: Davidson
    - `ddl-auto=validate` acepta las entidades; las tablas están vacías tras migrar; `information_schema` confirma los tipos y la ausencia de columna de cuenta en `contratos`; inserciones JDBC inválidas producen SQLState 23505, 23514 y 23503 (unicidad, `CHECK` y FK); RLS activo y sin privilegios `select`, `insert`, `update` ni `delete` para `anon` y `authenticated`
    - _Requisitos: 2.2–2.10, 24.5, 24.6, 24.9, DP-SEG-05_

  - [ ] 16.4 Escribir `ContratoTransaccionIT`
    - Área: backend · Responsable: Sebastián
    - Un cambio de estado confirmado actualiza juntas las tres columnas de estado; un `TransactionTemplate` que llama a `cambiarEstado` y después lanza una excepción no deja cambios en el estado ni en su auditoría
    - _Requisitos: 14.4, 24.8, DP-SEG-06_

  - [ ] 16.5 Escribir `FlujoCrmIT`
    - Área: backend · Responsable: Sebastián
    - Flujo cuenta → cliente → contrato → `ACTIVO` → `FINALIZADO` por MockMvc contra PostgreSQL; un `saveAndFlush` de un cliente duplicado lanza `DataIntegrityViolationException`, que la API traduce a 409 genérico
    - _Requisitos: 17.5, 24.11, CA-04, CA-05, ENG-03_

  - [ ] 16.6 Escribir `ConsultasCrmIT`
    - Área: backend · Responsable: Sebastián
    - Conjunto sembrado con semilla fija (tildes, mayúsculas, documentos con letras, cuentas activas e inactivas y contratos en los cuatro estados) y al menos 100 combinaciones generadas de filtros y orden, incluidos valores de `q` con `%` y `_`
    - **Property 15: Filtros, búsqueda y orden coinciden con un modelo en memoria** · **Validates: Requirements 5.1, 5.3, 5.4, 5.5, 9.6, 12.1, 24.7**
    - _Requisitos: 24.7, CRM-01, ENG-03_

  - [ ]* 16.7 Escribir `ResumenComercialIT`
    - Área: backend · Responsable: Davidson
    - **Property 13: El resumen comercial es consistente** (contra PostgreSQL) · **Validates: Requirements 15.1, 15.2, 15.3, 24.7**
    - _Requisitos: 24.7, CRM-05_

- [ ] 17. Aplicar la migración del CRM en `arise-corp-dev`
  - [ ] 17.1 Aplicar la migración revisada de forma controlada
    - Área: base de datos · Responsable: Davidson
    - Requisitos previos: revisión 8.1 aprobada y `MigracionesCrmIT` en verde. Aplicar con el mismo procedimiento controlado de la tarea 20 de `001-foundation` (D18)
    - Ejecutar las consultas de verificación del diseño (RLS y privilegios de `anon` y `authenticated`) y confirmar en Supabase que `app` sigue fuera de los esquemas expuestos de la Data API
    - Registrar en este `tasks.md` solo resultado, fecha y entorno, sin UUID, credenciales ni datos personales. Desde aquí la migración no se edita; un cambio de esquema llega en una migración nueva
    - _Requisitos: 2.8, 29.10, DP-TEC-03, DP-SEG-05, NF-01_

- [ ] 18. Contenerizar el backend
  - [x] 18.1 Crear `backend/Dockerfile`
    - Área: backend/Docker · Responsable: Davidson
    - Dockerfile multi-stage del diseño: etapa `eclipse-temurin:21-jdk` con `chmod +x mvnw` y `./mvnw -B -q -Dmaven.test.skip=true package` con caché de BuildKit para `~/.m2`; etapa `eclipse-temurin:21-jre` con el usuario `arise` sin privilegios, solo `app.jar`, `EXPOSE 8080` y sin `ARG` ni `ENV` de configuración
    - _Requisitos: 26.1–26.3, 26.5, 26.7, ENG-01, ENG-04, NF-01_

  - [x] 18.2 Crear `backend/.dockerignore`
    - Área: backend/Docker · Responsable: Davidson
    - `.dockerignore` con `.env`, `.env.*`, `target/`, `src/test/`, archivos de IDE, `*.log`, `HELP.md` y `.gitattributes`
    - _Requisitos: 26.6, NF-01_

  - [x] 18.3 Crear `docker-compose.yml` en la raíz
    - Área: backend/Docker · Responsable: Davidson
    - Proyecto `arise-corp` con un único servicio `backend` construido desde `./backend`, imagen `arise-backend:local`, `env_file` con `path: ./backend/.env` y `required: true`, y puerto `127.0.0.1:8080:8080`. Sin credenciales ni base de datos local (D15)
    - _Requisitos: 27.1–27.4, ENG-01, NF-01_

  - [ ] 18.4 Verificar la imagen y Compose
    - Área: backend/Docker · Responsable: Davidson
    - `docker build -t arise-backend:local backend` sin credenciales ni conexión a Supabase; `docker run --rm --entrypoint id arise-backend:local` muestra `arise`; `docker image inspect` muestra `8080/tcp`; la búsqueda de `.env*` dentro de la imagen no devuelve nada y `docker history --no-trunc` no muestra valores de configuración
    - `docker compose config --services` devuelve solo `backend`. Con un `backend/.env` válido y la migración aplicada (17.1, necesaria por `ddl-auto=validate`), `docker compose up backend` y `curl -i http://localhost:8080/api/me` → 401 con `WWW-Authenticate`
    - _Requisitos: 26.2, 26.3, 26.5, 26.7, 27.1, 27.5, CA-01, CA-02_

- [ ] 19. Documentar el backend, Docker y las reglas del proyecto
  - [ ] 19.1 Actualizar el README del backend y de Docker
    - Área: documentación · Responsable: Davidson
    - §4 y §5: estado y estructura con `crm/`, `shared/`, `Dockerfile`, `docker-compose.yml` y `.github/workflows/`. §8 y §11.5: `.\mvnw.cmd test` (Surefire, H2, sin Docker) frente a `.\mvnw.cmd verify` (Failsafe y Testcontainers, requiere Docker en ejecución)
    - §9: `docker compose build backend`, `docker compose up backend`, `docker build -t arise-backend:local backend`, `env_file` en tiempo de ejecución, Docker Compose 2.24 o posterior, `backend/.env` sin comillas ni `$`, y comprobación con `curl -i http://localhost:8080/api/me`
    - Confirmar que no hay variables de entorno nuevas y que los `.env.example` no cambian
    - _Requisitos: 29.2, 29.4, 29.7, CA-01, ENG-01, ENG-03_

  - [ ] 19.2 Actualizar los steering del backend
    - Área: documentación · Responsable: Davidson
    - `tech.md`: dependencias de prueba, Failsafe, comando `verify`, Docker [Implementado] y nota sobre `$` en `.env`. `structure.md`: árbol de `crm/`, `shared/normalizacion`, `shared/paginacion`, `shared/validacion` y `config/ClockConfig`; Dockerfile, `.dockerignore`, Compose y workflows [Implementado]
    - `api-conventions.md`: endpoints CRM, extensiones `conflicto` y `campo`, `campo` en 404, y 405 y 415 en español. `security.md`: vista reducida implementada y DTO sin datos personales en logs. `architecture.md`: fila de la API con CRM, contenedor y CI. `product.md`: CRM-01 a CRM-05 en backend, Docker, Testcontainers y CI implementados
    - El ajuste de DP-TEC-03 ya está registrado en `tech.md` (se verificó en la tarea 1.2); los de validación de parámetros y de DP-EST-05 quedaron registrados en la tarea 1.2. Aquí solo se comprueba que el código los cumple
    - _Requisitos: 6.4, 29.5, CA-07, NF-05_

- [ ] 20. Checkpoint del backend completo
  - Área: verificación · Responsable: Davidson
  - Ejecutar desde `backend/` `.\mvnw.cmd clean test` y, con Docker en ejecución, `.\mvnw.cmd verify` (Requirement 24.11); desde la raíz, `docker compose build backend`. Asegurar que todas las pruebas pasan; consultar al usuario si surgen dudas.

- [x] 21. Ampliar el cliente HTTP del frontend
  - [x] 21.1 Ampliar `services/apiClient.js`
    - Área: frontend · Responsable: Paula
    - `apiRequest(path, { method = 'GET', body } = {})`, compatible con las llamadas actuales: Bearer vigente; `Content-Type: application/json` y `JSON.stringify(body)` solo cuando hay cuerpo; 204 → `null`
    - `ApiError` conserva `status`, `problem`, `title`, `detail` y `errors`, y añade `kind`, `conflicto`, `campo` y `reintentable` según la tabla del diseño. 401 sin cambios; 403 sin leer el cuerpo ni cerrar sesión, con mensaje propio para escrituras; 5xx sin leer el cuerpo
    - _Requisitos: 18.1–18.9, AUTH-03, AUTH-05_

  - [x] 21.2 Ampliar `services/apiClient.test.js`
    - Área: frontend · Responsable: Paula
    - Métodos, cuerpo JSON y `Content-Type`, 400, 401, 403 en lectura y en escritura, 404, 409, 5xx y tiempo agotado. Las pruebas existentes se conservan sin cambios
    - _Requisitos: 18.1–18.9, 25.1, ENG-02_

  - [x]* 21.3 Escribir la prueba de propiedad del cliente HTTP
    - Área: frontend · Responsable: Paula
    - **Property 18: El cliente HTTP clasifica cada respuesta de forma uniforme** (métodos × estados de 200 a 599) · **Validates: Requirements 18.1, 18.2, 18.3, 18.4, 18.5, 18.7, 18.8, 25.1**
    - _Requisitos: 25.1, NF-07_

  - [x] 21.4 Crear `services/crmApi.js`
    - Área: frontend · Responsable: Paula
    - Las 16 funciones del diseño, con consultas construidas con `URLSearchParams` que omiten los parámetros vacíos, y `valor` enviado como texto
    - _Requisitos: 18.1, 18.2, 21.4, DP-API-04_

  - [x]* 21.5 Escribir `services/crmApi.test.js`
    - Área: frontend · Responsable: Paula
    - Rutas, métodos y consultas sin parámetros vacíos
    - **Property 5: El valor del contrato viaja como texto sin perder precisión** (lado frontend) · **Validates: Requirements 21.4**
    - _Requisitos: 21.4, NF-07_

- [x] 22. Implementar las reglas de interfaz del CRM
  - [x] 22.1 Crear `lib/permisos.js`
    - Área: frontend · Responsable: Paula
    - `puedeGestionarCrm`, `puedeLeerClientes`, `puedeLeerContratos`, `TRANSICIONES`, `esEstadoFinal` y `opcionesMenu(rol)`
    - _Requisitos: 19.2–19.4, 21.7, DP-PRO-01_

  - [x] 22.2 Crear `lib/validacionCrm.js`
    - Área: frontend · Responsable: Paula
    - `normalizarDocumento`, `normalizarCodigo`, `validarCliente`, `validarCuenta` y `validarContrato` con los límites y mensajes del contrato de API; `valor` validado con `^\d{1,13}(\.\d{1,2})?$` sobre el texto, sin convertirlo a número
    - _Requisitos: 20.5, 21.3, 21.4, 22.3, NF-06_

  - [x] 22.3 Crear `lib/formato.js`
    - Área: frontend · Responsable: Paula
    - `formatoCop` con `Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', minimumFractionDigits: 2 })` aplicado a la cadena decimal; `formatoFecha` y `formatoInstante` en `es-CO`
    - _Requisitos: 21.5, CRM-03_

  - [x]* 22.4 Escribir `lib/permisos.test.js`
    - Área: frontend · Responsable: Paula
    - **Property 11: Solo se aceptan las cuatro transiciones permitidas** (transiciones que ofrece el frontend) · **Validates: Requirements 21.7**
    - **Property 19: El menú y los controles derivan del rol** · **Validates: Requirements 19.2, 19.3, 19.4**
    - _Requisitos: 19.2–19.4, 21.7, NF-07_

  - [x]* 22.5 Escribir `lib/validacionCrm.test.js`
    - Área: frontend · Responsable: Paula
    - Crear `src/test/generadores.js` (`mulberry32` con semilla fija)
    - **Property 1: La normalización es idempotente** · **Validates: Requirements 3.2**
    - **Property 2: Las variantes de formato de un documento son el mismo documento** · **Validates: Requirements 3.3**
    - **Property 3: Las validaciones de formato coinciden con la regla de referencia** · **Validates: Requirements 20.5, 21.3, 22.3**
    - _Requisitos: 20.5, 21.3, 22.3, NF-07_

  - [x]* 22.6 Escribir `lib/formato.test.js`
    - Área: frontend · Responsable: Paula
    - Formato COP de `"0.00"`, `"1500000.5"` y `"9999999999999.99"`
    - _Requisitos: 21.5_

- [x] 23. Compartir el perfil e incorporar el layout privado y la protección por rol
  - [x] 23.1 Crear `context/PerfilContext.jsx` y adaptar `pages/Perfil.jsx`
    - Área: frontend · Responsable: Paula
    - `PerfilProvider` hace una sola llamada a `GET /api/me` por montaje, con la deduplicación actual bajo StrictMode; `usePerfil()` devuelve `{ perfil, rol, cargando, error, recargar }` (D14)
    - `Perfil` consume `usePerfil()` y conserva textos, estados y botones; "Cerrar sesión" pasa al layout
    - _Requisitos: 18.9, 19.1, 19.7, AUTH-04_

  - [x] 23.2 Crear `components/AppLayout.jsx` y `routes/RolRoute.jsx`
    - Área: frontend · Responsable: Paula
    - `AppLayout`: `PerfilProvider`, menú construido con `opcionesMenu(rol)`, enlace "Perfil", botón "Cerrar sesión" y `<Outlet />`
    - `RolRoute`: "Cargando…" mientras carga el perfil; si `permite(rol)` es falso, muestra "No tienes permisos para acceder a esta sección." sin renderizar la página ni pedir sus datos
    - _Requisitos: 19.1–19.6, AUTH-05, NF-02_

  - [x] 23.3 Envolver las rutas privadas con `AppLayout` en `App.jsx`
    - Área: frontend · Responsable: Paula
    - `ProtectedRoute` → `AppLayout` → `/perfil`; `/login` y la redirección `*` → `/perfil` sin cambios
    - _Requisitos: 19.1, 19.7, AUTH-01, AUTH-02_

  - [x] 23.4 Ampliar `App.test.jsx` para el layout y el perfil
    - Área: frontend · Responsable: Paula
    - Menú por rol en `/perfil`, un único botón "Cerrar sesión" y `/perfil` con una sola petición a `/api/me`; las pruebas de `001-foundation` siguen en verde
    - _Requisitos: 19.1–19.3, 19.7, 25.3, 25.5_

- [x] 24. Crear los componentes compartidos del CRM
  - [x] 24.1 Implementar los componentes compartidos y el hook de consulta
    - Área: frontend · Responsable: Paula
    - `EstadoConsulta` (carga con `role="status"`, error con `role="alert"` y "Reintentar" si `reintentable`, 403 y 404), `Campo` (marca de obligatorio, `aria-invalid` y `aria-describedby`), `Paginacion`, `DialogoConfirmacion` (`role="dialog"` y `aria-modal`), `EstadoContratoEtiqueta` y `lib/useConsulta.js`, que ignora respuestas de efectos desmontados
    - Ningún componente usa `dangerouslySetInnerHTML`
    - _Requisitos: 18.3–18.8, 20.2, 20.6, NF-06, AUTH-05_

- [x] 25. Implementar las vistas de clientes
  - [x] 25.1 Implementar `pages/crm/ClientesLista.jsx`
    - Área: frontend · Responsable: Paula
    - Búsqueda `q` con el botón "Buscar", filtro Todos/Activos/Inactivos y paginación, con el estado en la URL (`useSearchParams`); columnas nombre, documento, ciudad, cuenta y estado; "Nuevo cliente" solo para gestor; mensaje de lista vacía
    - _Requisitos: 19.4, 20.1–20.3, CRM-01, DP-API-03_

  - [x] 25.2 Implementar `pages/crm/ClienteFormulario.jsx`
    - Área: frontend · Responsable: Paula
    - Alta y edición con selector de tipo de documento, campos obligatorios marcados y selector opcional de cuentas activas (la cuenta actual inactiva aparece como "(inactiva)"); validación local antes de enviar; "Guardando…" con el botón deshabilitado; errores 400 y 409 junto al campo sin perder lo escrito; enlace "Ver cliente" con `CLIENTE_INACTIVO_EXISTENTE`
    - _Requisitos: 10.5, 20.4–20.8, CRM-01, CRM-02, CRM-04, NF-06_

  - [x] 25.3 Implementar `pages/crm/ClienteDetalle.jsx`
    - Área: frontend · Responsable: Paula
    - Datos según la vista recibida, estado y cuenta. Para gestor: "Editar", "Desactivar"/"Reactivar" con confirmación y "Nuevo contrato" solo si el cliente está activo; si está inactivo, el texto de cliente inactivo. Tabla de contratos para gestor y OPERACIONES; con FINANZAS_RRHH no se piden contratos
    - _Requisitos: 19.4, 20.9–20.11, 21.1, 21.2, CRM-01, CRM-03_

  - [x] 25.4 Escribir `pages/crm/ClientesLista.test.jsx`
    - Área: frontend · Responsable: Paula
    - Carga, lista vacía, búsqueda, filtro, paginación y modo consulta para los roles de lectura cruzada
    - _Requisitos: 19.4, 20.1–20.3, 25.3_

  - [x] 25.5 Escribir `pages/crm/ClienteFormulario.test.jsx`
    - Área: frontend · Responsable: Paula
    - Validación local sin petición, botón deshabilitado durante el envío, errores 400 por campo con los valores conservados y 409 con enlace al cliente inactivo
    - _Requisitos: 20.5–20.8, 25.2_

  - [x] 25.6 Escribir `pages/crm/ClienteDetalle.test.jsx`
    - Área: frontend · Responsable: Paula
    - Contratos para gestor y OPERACIONES, ninguna solicitud de contratos con FINANZAS_RRHH, confirmación de la desactivación y alta oculta para un cliente inactivo
    - _Requisitos: 20.9–20.11, 21.2, 25.2, 25.3_

- [x] 26. Implementar las vistas de contratos
  - [x] 26.1 Implementar `pages/crm/ContratoFormulario.jsx`
    - Área: frontend · Responsable: Paula
    - Alta desde un cliente activo y edición solo en `BORRADOR`; campos código, descripción, fechas y `valor` como texto con `inputMode="decimal"`; validación local; 409 `CONTRATO_CODIGO_DUPLICADO` junto a "Código" con los valores conservados
    - _Requisitos: 21.1, 21.3, 21.4, 21.6, 21.10, CRM-03, CRM-04_

  - [x] 26.2 Implementar `pages/crm/ContratoDetalle.jsx`
    - Área: frontend · Responsable: Paula
    - Datos, valor en COP y auditoría de estado. Para gestor: un botón por cada estado de `TRANSICIONES[estado]`, confirmación "Este cambio es irreversible." hacia los estados finales, "Editar" solo en `BORRADOR` y recarga tras un 409. OPERACIONES ve el detalle sin acciones
    - _Requisitos: 21.5, 21.7–21.10, CRM-03, DP-API-05_

  - [x] 26.3 Escribir `pages/crm/ContratoFormulario.test.jsx`
    - Área: frontend · Responsable: Paula
    - Fecha de fin anterior a la de inicio, valor negativo o con más de 2 decimales, `valor` enviado como texto y 409 de código junto al campo
    - _Requisitos: 21.3, 21.4, 21.6, 25.2_

  - [x] 26.4 Escribir `pages/crm/ContratoDetalle.test.jsx`
    - Área: frontend · Responsable: Paula
    - Transiciones ofrecidas en cada estado, confirmación irreversible, recarga tras un 409, edición solo en `BORRADOR` y OPERACIONES sin acciones
    - _Requisitos: 21.7–21.10, 25.2, 25.3_

- [ ] 27. Implementar las vistas de cuentas corporativas y del resumen comercial
  - [ ] 27.1 Implementar `CuentasLista`, `CuentaFormulario` y `CuentaDetalle` en `pages/crm/`
    - Área: frontend · Responsable: Paula
    - Lista con búsqueda por razón social o NIT, filtro Todas/Activas/Inactivas y paginación; formulario con `nit` y `razonSocial` obligatorios, validación local y errores 400 y 409 junto al campo; detalle con "Activar"/"Desactivar" confirmado y los clientes de `GET /api/crm/clientes?cuentaCorporativaId={id}`
    - _Requisitos: 22.1–22.6, CRM-02, NF-06_

  - [ ] 27.2 Implementar `pages/crm/ResumenComercial.jsx`
    - Área: frontend · Responsable: Paula
    - Tarjetas con clientes activos, inactivos y total, y contratos por estado y total; indicador de carga y error con reintento
    - _Requisitos: 22.7, CRM-05, NF-06_

  - [ ]* 27.3 Escribir las pruebas de las vistas de cuentas
    - Área: frontend · Responsable: Paula
    - `CuentasLista.test.jsx`, `CuentaFormulario.test.jsx` y `CuentaDetalle.test.jsx`: lista, formulario con 400 y 409, y detalle con los clientes asociados
    - _Requisitos: 22.1–22.6, 25.2_

  - [ ]* 27.4 Escribir `pages/crm/ResumenComercial.test.jsx`
    - Área: frontend · Responsable: Paula
    - Carga, error con reintento y conteos
    - _Requisitos: 22.7_

- [ ] 28. Integrar las rutas CRM en la aplicación
  - [ ] 28.1 Registrar las rutas `/crm/*` en `App.jsx`
    - Área: frontend · Responsable: Paula
    - Grupos de `RolRoute` del diseño: `puedeLeerClientes` (lista y detalle de clientes), `puedeLeerContratos` (detalle de contrato) y `puedeGestionarCrm` (formularios, cuentas y resumen)
    - _Requisitos: 19.5, 19.7, 20.1, 21.1, 22.1, 22.7_

  - [ ] 28.2 Escribir las pruebas de integración de la navegación CRM en `App.test.jsx`
    - Área: frontend · Responsable: Paula
    - Menú por rol con enlaces a `/crm/*`; una ruta CRM no permitida (por ejemplo `/crm/clientes/nuevo` con FINANZAS_RRHH) muestra el mensaje sin solicitar sus datos; un 403 del backend muestra el mensaje de permisos sin cerrar sesión
    - _Requisitos: 19.5, 19.6, 25.3, 25.4_

- [ ] 29. Documentar el frontend del CRM
  - [ ] 29.1 Actualizar la documentación del frontend
    - Área: documentación · Responsable: Paula
    - README §11.6: vistas y rutas CRM por rol, y comandos `npm test`, `npm run build` y `npm run check:secrets`, sin variables nuevas. `structure.md`: `pages/crm`, `context/PerfilContext`, `routes/RolRoute`, `components/AppLayout` y `lib/permisos`, `validacionCrm` y `formato`. `architecture.md`: fila de la SPA con las vistas CRM. `product.md`: vistas CRM del frontend implementadas en "Estado actual"
    - _Requisitos: 29.5, 29.7, CA-01, CA-07_

- [ ] 30. Checkpoint del frontend
  - Área: verificación · Responsable: Paula
  - Ejecutar desde `frontend/` `npm test`, `npm run build` y `npm run check:secrets` (Requirements 25.5, 25.6). Asegurar que todas las pruebas pasan; consultar al usuario si surgen dudas.

- [ ] 31. Validar el CRM de extremo a extremo con Supabase real
  - [ ] 31.1 Ejecutar la validación manual del Requirement 30
    - Área: integración/documentación · Responsable: Davidson
    - Precondiciones: migración aplicada (17.1); cuentas de prueba con perfiles COMERCIAL, FINANZAS_RRHH y OPERACIONES creados con `PUT /api/admin/usuarios/{id}/perfil`; backend con `docker compose up backend` y frontend con `npm run dev`
    - Con COMERCIAL: cuenta → cliente asociado → búsqueda por documento escrito con puntos → edición del teléfono → contrato → `ACTIVO` → recarga con los datos persistidos. Con FINANZAS_RRHH y OPERACIONES: vista reducida, modo consulta y mensaje de permisos en `/crm/clientes/nuevo`
    - Escritura denegada sin copiar tokens: con el formulario de edición abierto, el Administrador cambia el rol del usuario a FINANZAS_RRHH; el envío recibe 403, el cliente no cambia y la interfaz muestra el mensaje sin cerrar sesión; al terminar se restablece el rol
    - Registrar en este `tasks.md` fecha, pasos y resultado sin credenciales, tokens, UUID ni datos personales reales; desactivar los datos ficticios creados
    - _Requisitos: 30.1–30.4, AUTH-05, CA-03, CA-04, CA-05, NF-02_

- [ ] 32. Checkpoint final
  - Área: verificación · Responsable: Davidson
  - Ejecutar `.\mvnw.cmd clean test` y `.\mvnw.cmd verify` desde `backend/`; `npm test`, `npm run build` y `npm run check:secrets` desde `frontend/`; y `docker compose build backend` desde la raíz. Asegurar que todas las pruebas pasan; consultar al usuario si surgen dudas.

- [ ] 33. Verificar y entregar la spec
  - [ ] 33.1 Verificar CI, secretos, documentación y trazabilidad
    - Área: integración/documentación · Responsable: Davidson
    - Una ejecución verde de `backend-ci`, `integration-tests` y `frontend-ci` en la rama; ningún `.env` rastreado (`git ls-files`); contratos de `design.md` coherentes con el código; trazabilidad requisito–tarea revisada; `product.md` indica que `002-crm-clientes-contratos` queda cerrada al integrar el PR
    - _Requisitos: 28.1–28.11, 29.1, 29.5, CA-01, CA-07, NF-01, ENG-04_

  - [ ] 33.2 Configurar los checks obligatorios en la protección de `main`
    - Área: CI · Responsable: Davidson
    - PR obligatorio con una aprobación de otro integrante, *squash merge*, rama actualizada antes de integrar y `backend-ci`, `integration-tests` y `frontend-ci` como checks obligatorios
    - _Requisitos: 29.9, DP-SDD-05_

  - [ ] 33.3 Abrir el Pull Request único hacia `main`
    - Área: integración · Responsable: Davidson
    - PR desde `feat/crm-clientes-contratos` con enlace a la spec, requisitos y tareas, instrucciones de prueba, resultados de las suites, construcción Docker, CI en verde y la lista de comprobación de `sdd-workflow.md`; revisión de un integrante distinto del autor de cada cambio; sin secretos en la descripción ni en las capturas
    - _Requisitos: 29.8, CA-07, DP-SDD-04, DP-SDD-05_

## Notes

- Las subtareas marcadas con `*` son pruebas que no condicionan los Must: generadores de propiedades sin un requisito que los exija y pruebas de detalle de los Should CRM-02 y CRM-05. No son opcionales la matriz de autorización de todos los endpoints (14.8, CA-03), la normalización (10.2), la máquina de estados (10.4), los servicios (13.6, 13.7, 13.8), los controladores de clientes y contratos (14.9, 14.10), los logs (14.12), las IT principales (16.3–16.6) ni las pruebas de clientes y contratos del frontend.
- Cada prueba de propiedad cita su Property del diseño y los criterios que valida; la entrada que falla aparece en el mensaje de la aserción.
- `backend-ci` termina en rojo en la rama desde la tarea 5.1 hasta que exista `backend/Dockerfile` (18.1), con el fallo solo en el paso `docker build`. Se acepta: no hay PR abierto en ese periodo y el PR único (33.3) se abre con el Dockerfile ya creado. Mientras tanto, el estado de las pruebas se lee en el paso `./mvnw -B test`. `integration-tests` y `frontend-ci` pueden estar en verde desde su creación.
- Si `.\mvnw.cmd test` muestra `ConflictingBeanDefinitionException` tras mover o renombrar clases, ejecutar `.\mvnw.cmd clean test` (observación del cierre de `001-foundation`).
- La aplicación de la migración (17.1), la validación manual (31.1) y la protección de `main` (33.2) se hacen fuera de CI y sin guardar credenciales, tokens ni UUID en el repositorio.
- Cada tarea referencia los requisitos de `requirements.md` y los identificadores del MVP para trazabilidad.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "2.1", "3.1", "4.1"] },
    { "id": 2, "tasks": ["2.2", "3.2"] },
    { "id": 3, "tasks": ["3.3"] },
    { "id": 4, "tasks": ["3.4"] },
    { "id": 5, "tasks": ["3.5", "5.1", "5.2", "5.3"] },
    { "id": 6, "tasks": ["6.1", "6.2"] },
    { "id": 7, "tasks": ["8.1", "9.1", "10.1", "11.1", "11.3", "11.4"] },
    { "id": 8, "tasks": ["9.2", "10.2", "10.3"] },
    { "id": 9, "tasks": ["9.3", "10.4", "11.2", "12.1"] },
    { "id": 10, "tasks": ["12.2", "13.1"] },
    { "id": 11, "tasks": ["12.3", "13.2", "13.3", "13.4", "13.5"] },
    { "id": 12, "tasks": ["12.4", "14.1", "14.2"] },
    { "id": 13, "tasks": ["13.6", "13.7", "14.3", "14.4", "14.5", "14.6"] },
    { "id": 14, "tasks": ["13.8", "14.7"] },
    { "id": 15, "tasks": ["13.9", "14.8", "14.9", "14.10", "14.11", "14.12"] },
    { "id": 16, "tasks": ["16.1", "18.1", "18.2", "18.3"] },
    { "id": 17, "tasks": ["16.2"] },
    { "id": 18, "tasks": ["16.3", "16.4", "16.5", "16.7"] },
    { "id": 19, "tasks": ["16.6", "17.1"] },
    { "id": 20, "tasks": ["18.4", "19.1", "19.2"] },
    { "id": 21, "tasks": ["21.1", "22.1", "22.2", "22.3", "23.1"] },
    { "id": 22, "tasks": ["21.2", "21.4", "22.4", "22.5", "22.6", "23.2", "24.1"] },
    { "id": 23, "tasks": ["21.3", "21.5", "23.3"] },
    { "id": 24, "tasks": ["23.4", "25.1", "25.2", "25.3", "26.1", "26.2", "27.1", "27.2"] },
    { "id": 25, "tasks": ["25.4", "25.5", "25.6", "26.3", "26.4", "27.3", "27.4", "28.1"] },
    { "id": 26, "tasks": ["28.2", "29.1"] },
    { "id": 27, "tasks": ["31.1"] },
    { "id": 28, "tasks": ["33.1"] },
    { "id": 29, "tasks": ["33.2"] },
    { "id": 30, "tasks": ["33.3"] }
  ]
}
```
