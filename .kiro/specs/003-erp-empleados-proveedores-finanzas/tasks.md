# Implementation Plan: 003-erp-empleados-proveedores-finanzas

## Overview

Este plan convierte el diseño aprobado de `003-erp-empleados-proveedores-finanzas` en tareas pequeñas e incrementales: migración y persistencia del ERP; utilidades compartidas y lectura de clientes desde el CRM; reglas y API REST de empleados, proveedores, movimientos financieros y panel financiero; pruebas unitarias, de seguridad y de integración con PostgreSQL efímero; vistas ERP del frontend; verificación de la imagen Docker y de los workflows existentes; y validación extremo a extremo con Supabase real.

Cada tarea indica **área**, **responsable** y **requisitos** (`requirements.md` e identificadores del MVP). Todo el trabajo se confirma en la rama `feat/erp-empleados-proveedores-finanzas` con Conventional Commits, con alcance `erp` para el código del módulo y `db`, `frontend`, `docs` o `ci` cuando corresponda, y se integra mediante un único Pull Request al completar la spec (DP-SDD-04, DP-SDD-05).

**Reparto de trabajo.** Cada responsable recibe verticales completas (persistencia → servicio → controlador → pruebas, o servicio → vista → pruebas) para reducir los cambios de contexto, y las tareas transversales se reparten para igualar la carga:

- **Davidson:** migración ERP (preparación, registro y aplicación controlada en `arise-corp-dev`), enumeraciones, entidades, repositorios, especificaciones, fragmento del panel y `ErpDatosPrueba`; `@FechaNoFutura` y el reloj de validación de `ClockConfig`; las verticales completas de empleados y proveedores (DTO, servicios, controladores y sus pruebas); `MigracionesErpIT`; verificación de CI y de la protección de `main`; registro de los ajustes de steering del inicio; documentación del backend; comprobación de las denegaciones en la validación extremo a extremo; y revisión final de trazabilidad.
- **Sebastián:** revisión de la migración; `ParametroInvalidoException`, `@ValorCopPositivo` y `GeneradoresErp`; `ClienteReferenciaService` en el CRM; máquina de estados `EstadoMovimiento` y catálogo `ConflictosErp`; las verticales completas de movimientos y del panel financiero; `ErpAutorizacionTests` con la retirada del doble ERP de `AutorizacionModulosTests`; `ErpLogsTests`; la ampliación de `PostgresIntegracionBase` y las IT de transacción y concurrencia, flujo ERP-05, consultas y resumen; comprobación de la imagen Docker; steering de API y seguridad; y el PR final.
- **Paula:** rama de la spec; `erpApi.js`, `validacionErp.js` (con las exportaciones de `validacionCrm.js`), permisos y menú `opcionesMenuErp`, icono `erp.svg` y tarjetas de `Panel.jsx`; `SelectorRegistro` y `EstadoMovimientoEtiqueta`; las vistas de empleados, proveedores, movimientos (incluido el cambio de estado) y panel financiero; las rutas `/erp/*` de `App.jsx`; los generadores y todas las pruebas Vitest y React Testing Library; documentación del frontend; y validación manual de las vistas en la prueba extremo a extremo.

**Trabajo en paralelo.** El contrato de la API está fijado en `design.md` ("Contratos de la API del ERP"), así que el frontend (grupos 19 a 29) avanza desde que existen la rama y los ajustes de steering, en paralelo con el backend, y sus pruebas simulan la API. En el backend, los cambios de `shared` (grupo 3) y `ClienteReferenciaService` (grupo 4) se completan antes que los servicios que los usan (grupos 8 a 11), y las entidades y repositorios (grupo 5) antes que cualquier servicio. Las verticales de empleados, proveedores y movimientos son independientes entre sí, salvo dos puntos: `MovimientoService` usa `ProveedorRepository` (5.4) y `ProveedorController` (9.5) se confirma en el mismo commit que la retirada del doble ERP (12.1).

### Notas de alcance (del diseño aprobado)

- **Must:** ERP-01, ERP-02 y ERP-03. **Should incluidos:** ERP-04 (panel financiero) y ERP-05 (relación de los movimientos con clientes y proveedores).
- **Pruebas de propiedades (D15):** no se añade ninguna librería. Las propiedades de dominio pequeño se prueban de forma exhaustiva con `@ParameterizedTest` o `it.each`. Las de dominio amplio usan generadores con semilla fija (`GeneradoresErp` con `SEMILLA = 20261113L` en Java y `frontend/src/test/generadores.js`) con al menos 100 casos más los bordes conocidos. Cada prueba se titula `Feature: 003-erp-empleados-proveedores-finanzas, Property {n}: {texto}`.
- **Sin dependencias ni variables de entorno nuevas (D21):** `pom.xml`, `package.json`, `backend/.env.example`, `frontend/.env.example`, `backend/Dockerfile`, `backend/.dockerignore`, `docker-compose.yml` y los tres workflows no cambian (Requirements 28.4, 28.8, 29.5). `SecurityConfig` tampoco cambia (Requirement 19.1).
- **Migración (D20, DP-TEC-03):** la prepara Davidson, la revisa Sebastián en la rama, `MigracionesErpIT` la valida en PostgreSQL efímero y Davidson la aplica de forma controlada en `arise-corp-dev` antes de la validación manual y del PR final. Una vez aplicada no se edita. Mientras no esté aplicada, el backend de la rama no arranca contra `arise-corp-dev` (`ddl-auto=validate`); las suites automáticas no se ven afectadas.
- `.\mvnw.cmd test` no necesita Docker; `.\mvnw.cmd verify` sí, porque ejecuta Testcontainers. Failsafe recoge las IT nuevas con la inclusión existente `**/integration/**/*IT.java`.

### Distribución

Recuento de subtareas (los checkpoints son tareas de primer nivel y se cuentan aparte). Las columnas de área suman el total de subtareas de cada integrante.

| Integrante | Subtareas | Obligatorias | Opcionales (`*`) | Base de datos | Backend | Frontend | Docker/CI | Documentación | Integración/Git/E2E | Checkpoints |
|---|---|---|---|---|---|---|---|---|---|---|
| Davidson | 36 | 34 | 2 | 3 | 26 | 0 | 2 | 3 | 2 | 2 |
| Sebastián | 37 | 33 | 4 | 1 | 33 | 0 | 1 | 1 | 1 | 2 |
| Paula | 37 | 35 | 2 | 0 | 0 | 34 | 0 | 1 | 2 | 2 |
| **Total** | **110** | **102** | **8** | **4** | **59** | **34** | **3** | **5** | **5** | **6** |

La diferencia máxima es de una subtarea en el total (≈3 %) y de dos en las obligatorias (≈6 %), por debajo del margen del 15 %. En esfuerzo, Sebastián asume el servicio más complejo (movimientos, con bloqueos y la regla de asociación) y las IT con generadores; Davidson, la migración, toda la persistencia y dos verticales de forma parecida; y Paula, diez páginas con sus pruebas. Las tareas de Git, steering, documentación, checkpoints, validación extremo a extremo y entrega se reparten para compensar.

## Tasks

- [ ] 1. Preparar la rama y las reglas de la spec
  - [ ] 1.1 Crear la rama `feat/erp-empleados-proveedores-finanzas` desde `main`
    - Área: integración/Git · Responsable: Paula
    - Actualizar `main` (`git switch main` y `git pull`), crear la rama con `git switch -c feat/erp-empleados-proveedores-finanzas` y publicarla con `git push -u origin feat/erp-empleados-proveedores-finanzas`
    - Confirmar en la rama `requirements.md`, `design.md`, `tasks.md` y `.config.kiro` de `.kiro/specs/003-erp-empleados-proveedores-finanzas/` con `docs(erp): agregar spec 003-erp-empleados-proveedores-finanzas`
    - Comprobar con `git status` que ningún archivo `.env` queda preparado para commit
    - _Requisitos: 29.6, DP-SDD-03, DP-SDD-04, DP-SDD-05, NF-01_

  - [ ] 1.2 Registrar en los steering los ajustes de reglas que adopta el diseño
    - Área: documentación · Responsable: Davidson
    - `architecture.md`, DP-ARQ-01: los módulos consumidores leen clientes mediante `crm.service.ClienteReferenciaService` (solo lectura, en lote y con bloqueo de lectura dentro de la transacción del llamador); el ERP define sus propios DTO de salida (D2, D3)
    - `api-conventions.md`, sección "Validaciones": las reglas entre parámetros de consulta (por ejemplo `desde ≤ hasta`) lanzan `ParametroInvalidoException` y responden 400 con `errors` en el parámetro afectado (D9)
    - `api-conventions.md`, DP-API-04: la "fecha actual" de una regla de negocio es la fecha calendario en `America/Bogota`, calculada con el `Clock` de la aplicación (D7)
    - `tech.md`, "Configuración del backend": `ClockConfig` registra el `ValidationConfigurationCustomizer` que entrega el `Clock` a Bean Validation (D7)
    - Se hace antes del código que aplica estas reglas (`sdd-workflow.md`, "Registro de decisiones") y se confirma con `docs(erp): registrar en steering los ajustes de la spec 003`. El estado [Implementado] de cada área se actualiza en las tareas 17.1, 17.2, 17.3 y 28.1
    - _Requisitos: 29.3, NF-05, DP-ARQ-01, DP-API-04_

- [ ] 2. Crear y revisar la migración del ERP
  - [ ] 2.1 Crear `supabase/migrations/<timestamp>_app_erp_empleados_proveedores_movimientos.sql`
    - Área: base de datos · Responsable: Davidson
    - Generar el archivo con `supabase migration new app_erp_empleados_proveedores_movimientos`; el timestamp debe ser posterior a `20261111120000`. No modificar las migraciones existentes
    - Escribir el SQL de "Migración ERP" del diseño: `app.empleados`, `app.proveedores` y `app.movimientos_financieros` en orden de dependencia, con `create table` sin `if not exists`; PK UUID; `UNIQUE` `empleados_documento_uk` y `proveedores_documento_uk`; `CHECK` de tipos y formato de documento (solo dígitos con `CC` en empleados y con `CC` o `NIT` en proveedores), longitudes, teléfono, `tipo`, `estado`, `valor > 0`, concepto, referencia y coherencia de `cliente_id` y `proveedor_id` con `tipo`; FK `cliente_id → app.clientes` y `proveedor_id → app.proveedores`; índices de `fecha`, `cliente_id` y `proveedor_id`; comentarios de tabla; auditoría `timestamptz` y `uuid not null` sin FK a `auth.users`. Sin `CHECK` de fecha de ingreso no futura (interpretación 9 del diseño)
    - Habilitar RLS en las tres tablas y ejecutar `revoke all ... from anon, authenticated`. Sin `insert`, UUID personales, secretos ni sentencias que no admitan transacción
    - Anotar en este `tasks.md` el nombre real del archivo
    - _Requisitos: 2.1–2.9, ERP-01, ERP-02, ERP-03, ERP-05, NF-01, NF-03, NF-05, DP-SEG-05, DP-SEG-06, DP-API-04_

  - [ ] 2.2 Registrar la migración en `supabase/migrations/README.md`
    - Área: base de datos · Responsable: Davidson
    - Añadir la fila de la Migracion_ERP a la tabla "Migraciones" con su nombre real, la descripción y `003-erp-empleados-proveedores-finanzas`. Las reglas de revisión y aplicación de DP-TEC-03 ya están en el archivo desde `002-crm-clientes-contratos` y no cambian
    - _Requisitos: 29.4, NF-05, DP-TEC-03_

  - [ ] 2.3 Revisar la migración en la rama antes de aplicarla
    - Área: base de datos · Responsable: Sebastián
    - Contrastar el SQL con "Migración ERP" y "Entidades JPA (`erp.entity`)" del diseño: columnas, tipos, `CHECK`, `UNIQUE`, FK, índices, comentarios, RLS, `revoke` y ausencia de datos. Los cambios que pida la revisión se hacen en el mismo archivo, porque todavía no está aplicado
    - No la apliques tú: la aplicación controlada es la tarea 15.1 y la realiza Davidson, autor de la migración, después de `MigracionesErpIT` (14.2) y de esta revisión
    - _Requisitos: 2.1–2.9, 29.7, DP-TEC-03, DP-SEG-05_

- [ ] 3. Ampliar los componentes compartidos del backend
  - [ ] 3.1 Crear `shared.error.ParametroInvalidoException` y su handler
    - Área: backend · Responsable: Sebastián
    - `ParametroInvalidoException(String parametro, String mensaje)` con `getParametro()` (D9)
    - Nuevo `@ExceptionHandler` en `GlobalExceptionHandler`: 400, `title` "Solicitud inválida", `detail` "Los parámetros de la solicitud no son válidos." y `errors: [{ field: parametro, message: mensaje }]`, sin registrar nada en el log. El resto de handlers no cambia
    - _Requisitos: 15.7, 18.5, 20.1, 20.2, CA-06, DP-API-02_

  - [ ] 3.2 Ampliar `ErroresApiTests`
    - Área: backend · Responsable: Sebastián
    - Caso con un endpoint de prueba que lanza `ParametroInvalidoException("hasta", ...)`: 400 con `application/problem+json`, `errors[0].field = "hasta"` y el mensaje recibido. Las pruebas existentes no cambian
    - **Property 19: Los errores del ERP no exponen información interna ni datos personales** (respuesta de `ParametroInvalidoException`) · **Validates: Requirements 20.1, 20.8**
    - _Requisitos: 15.7, 18.5, 20.8, NF-07_

  - [ ] 3.3 Crear `@ValorCopPositivo` en `shared.validacion`
    - Área: backend · Responsable: Sebastián
    - Restricción compuesta con `@ReportAsSingleViolation`, `@DecimalMin(value = "0.00", inclusive = false)` y `@Digits(integer = 13, fraction = 2)`; acepta `null`; mensaje "debe ser mayor que 0, con hasta 13 dígitos enteros y 2 decimales" (D6). `@ValorCop` del CRM no cambia
    - _Requisitos: 13.7, DP-API-04_

  - [ ] 3.4 Crear `@FechaNoFutura` en `shared.validacion`
    - Área: backend · Responsable: Davidson
    - Anotación con su `ConstraintValidator<FechaNoFutura, LocalDate>` y la constante `ZONA_COLOMBIA = ZoneId.of("America/Bogota")`; acepta `null`; informa "no puede ser posterior a la fecha actual" si la fecha es posterior a `LocalDate.now(contexto.getClockProvider().getClock().withZone(ZONA_COLOMBIA))` (D6, D7)
    - _Requisitos: 4.7, DP-API-04_

  - [ ] 3.5 Registrar el reloj de la aplicación en Bean Validation
    - Área: backend · Responsable: Davidson
    - En `config.ClockConfig`, `@Bean ValidationConfigurationCustomizer relojDeValidacion(Clock clock)` que devuelve `configuracion -> configuracion.clockProvider(() -> clock)`. Comprobar el paquete de `ValidationConfigurationCustomizer` en Spring Boot 4.1.2 (`org.springframework.boot.validation.autoconfigure`); no se añade ninguna dependencia (D7, D21)
    - _Requisitos: 4.7, 29.5, DP-API-04_

  - [ ] 3.6 Crear `GeneradoresErp` en el paquete de prueba `com.arise.corp.erp`
    - Área: backend · Responsable: Sebastián
    - `SEMILLA = 20261113L` y `nuevoRandom()`; reutiliza los generadores de `GeneradoresCrm` (`texto`, `cadena`, `documentoBase`, `varianteDocumento`, `documentoDeCualquierForma`, `correoDeCualquierForma`, `telefonoDeCualquierForma`, `textoDeLongitudVariable`, `fecha`) y añade `referenciaDeCualquierForma`, `varianteReferencia`, `conceptoDeCualquierForma`, `valorPositivoValido`, `valorDeCualquierForma` (incluye 0, negativos, 3 decimales y 14 dígitos enteros), `relojAleatorio` (instantes en que UTC y Bogotá tienen fechas distintas), `fechaAlrededorDe(LocalDate)`, `movimiento`, `conjuntoMovimientos(n)` y `rango` (límites presentes o ausentes, ordenados) (D15)
    - Solo datos ficticios
    - _Requisitos: 25.3, 25.4, NF-07_

  - [ ] 3.7 Escribir `shared/validacion/FechaNoFuturaTests`
    - Área: backend · Responsable: Davidson
    - `Validator` construido con `Validation.byDefaultProvider().configure().clockProvider(...)`: con el reloj fijo en `2026-11-21T03:30:00Z` (20 de noviembre en Bogotá) acepta `2026-11-20` y rechaza `2026-11-21`; `null` es válido
    - **Property 3: Las validaciones de formato coinciden con la regla de referencia** (fecha de ingreso con `relojAleatorio` y `fechaAlrededorDe`) · **Validates: Requirements 4.7**
    - _Requisitos: 4.7, 25.1, NF-07_

- [ ] 4. Ofrecer la lectura de clientes del CRM a otros módulos
  - [ ] 4.1 Crear `crm.dto.ClienteReferencia` y `crm.service.ClienteReferenciaService`
    - Área: backend · Responsable: Sebastián
    - `record ClienteReferencia(UUID id, String nombre, boolean activo)` con `static desde(Cliente)`
    - `@Service @Transactional(readOnly = true) ClienteReferenciaService` con `Optional<ClienteReferencia> buscar(UUID)`, `Map<UUID, ClienteReferencia> buscarTodos(Collection<UUID>)` (una sola consulta con `findAllById`; una colección vacía devuelve un mapa vacío sin consultar) y `@Transactional(propagation = Propagation.MANDATORY) Optional<ClienteReferencia> buscarParaAsociar(UUID)`, que reutiliza `ClienteRepository.findParaContratoById` (`PESSIMISTIC_READ`) (D2, D12)
    - Ampliar solo el Javadoc de `findParaContratoById` con el segundo uso. `ClienteService`, los controladores y los endpoints CRM no cambian; el CRM no importa nada de `erp`
    - _Requisitos: 1.5, 1.6, 1.8, 14.11, DP-ARQ-01, ERP-05_

  - [ ] 4.2 Escribir `crm/service/ClienteReferenciaServiceTests`
    - Área: backend · Responsable: Sebastián
    - `@SpringBootTest` con H2 y `CrmDatosPrueba`: `buscar` con cliente activo, inactivo e inexistente; `buscarTodos` con colección vacía (sin consulta), ids mezclados, repetidos e inexistentes; `buscarParaAsociar` dentro de un `TransactionTemplate`; fuera de transacción lanza `IllegalTransactionStateException`
    - _Requisitos: 1.6, 14.11, 25.1, NF-07_

- [ ] 5. Implementar las entidades y los repositorios JPA del ERP
  - [ ] 5.1 Crear las enumeraciones de `erp.entity`
    - Área: backend · Responsable: Davidson
    - `TipoDocumentoEmpleado { CC, CE, PASAPORTE }`, `TipoDocumentoProveedor { NIT, CC, CE, PASAPORTE }`, `TipoMovimiento { INGRESO, EGRESO }` y `EstadoMovimiento { PENDIENTE, PAGADO, ANULADO }` (D4). Las reglas de transición se añaden en la tarea 6.1. No se reutiliza `crm.entity.TipoDocumento`
    - _Requisitos: 1.1, 1.5, 2.2–2.5, DP-API-04_

  - [ ] 5.2 Implementar las entidades `Empleado` y `Proveedor`
    - Área: backend · Responsable: Davidson
    - Mapeo según "Entidades JPA (`erp.entity`)" del diseño: `@Table` sin esquema con `uniqueConstraints` `empleados_documento_uk` y `proveedores_documento_uk`, `@Id @GeneratedValue(strategy = GenerationType.UUID)`, `@Enumerated(EnumType.STRING)`, `fechaIngreso` como `LocalDate` y `creadoEn` con `updatable = false`
    - Sin setters públicos: `static crear(..., UUID actor, Instant ahora)` con `activo = true`, `actualizarDatos(..., actor, ahora)` y `boolean cambiarEstado(boolean activo, actor, ahora)`, que devuelve `false` y no toca la auditoría si el valor no cambia
    - _Requisitos: 1.1, 2.2, 2.3, 2.7, 2.10, 4.1, 7.3, 9.1, 12.2, DP-SEG-06_

  - [ ] 5.3 Implementar la entidad `MovimientoFinanciero`
    - Área: backend · Responsable: Davidson
    - `valor` `BigDecimal` con `precision = 15, scale = 2`; `cliente_id` como `UUID clienteId` sin asociación JPA (D1); `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "proveedor_id") Proveedor proveedor` opcional, sin colecciones inversas; `estado`, `estadoActualizadoEn`, `estadoActualizadoPor` y auditoría de datos
    - `static crear(tipo, fecha, concepto, valor, referencia, clienteId, proveedor, actor, ahora)` en `PENDIENTE`, con la auditoría de estado igual a la de datos; `actualizarDatos(...)` cambia solo los siete campos editables y `actualizado_*`; `cambiarEstado(destino, actor, ahora)` cambia solo `estado` y su auditoría (D13). Las defensas de invariantes se añaden en la tarea 6.1
    - _Requisitos: 1.7, 2.4, 2.7, 2.10, 13.1, 16.5, 17.1, 17.5, DP-ARQ-01, DP-SEG-06_

  - [ ] 5.4 Crear los repositorios del ERP
    - Área: backend · Responsable: Davidson
    - `EmpleadoRepository` y `ProveedorRepository` con `JpaSpecificationExecutor` y `findByTipoDocumentoAndNumeroDocumento`; `ProveedorRepository.findParaAsociarById` con `@Lock(LockModeType.PESSIMISTIC_READ)`
    - `MovimientoFinancieroRepository` con `findAll(Specification, Pageable)` y `findConProveedorById` con `@EntityGraph(attributePaths = "proveedor")`, y `findParaModificarById` con `@Lock(LockModeType.PESSIMISTIC_WRITE)` sin *fetch join* (D12)
    - _Requisitos: 5.1, 10.1, 14.11, 15.1, 15.8, ERP-01, ERP-02, ERP-03_

  - [ ] 5.5 Crear el fragmento `TotalesMovimientos` del panel
    - Área: backend · Responsable: Davidson
    - `TotalesMovimientos.totalizar(LocalDate desde, LocalDate hasta)`, `record TotalMovimientos(TipoMovimiento tipo, EstadoMovimiento estado, Long cantidad, BigDecimal suma)` y `TotalesMovimientosImpl` con Criteria API que agrupa por `tipo` y `estado` y solo añade los límites presentes, sin parámetros nulos en JPQL (D10)
    - `MovimientoFinancieroRepository` pasa a extender `TotalesMovimientos`; Spring Data compone el fragmento por el sufijo `Impl` en el mismo paquete
    - _Requisitos: 18.1, 18.3, 18.6, ERP-04_

  - [ ] 5.6 Crear `erp.repository.EspecificacionesErp`
    - Área: backend · Responsable: Davidson
    - `nombreODocumentoContiene(q)` y `activoIgual(Boolean)` genéricas para `Empleado` y `Proveedor`; `tipoIgual`, `estadoIgual`, `clienteIgual`, `proveedorIgual` (`proveedor.id`, sin *join*), `fechaDesde`, `fechaHasta` y `conceptoOReferenciaContiene(q)` para movimientos
    - JPQL portable con `LIKE ... ESCAPE '\'` y `NormalizadorTexto.patronContiene`; el documento solo se compara si el Documento_Normalizado de `q` no queda vacío; una referencia nula no coincide; un filtro ausente devuelve `Specification.unrestricted()`
    - _Requisitos: 5.3, 5.4, 10.3, 10.4, 15.2–15.5, ERP-01, ERP-02, ERP-03_

  - [ ]* 5.7 Escribir `erp/repository/ErpRepositoriosTests`
    - Área: backend · Responsable: Davidson
    - `@DataJpaTest` en H2: guardar y leer las tres entidades, búsquedas derivadas, `@EntityGraph("proveedor")`, consultas con bloqueo y composición de `totalizar` con y sin límites, con datos ficticios
    - _Requisitos: 2.10, 18.1, NF-07_

  - [ ]* 5.8 Escribir `erp/repository/EspecificacionesErpTests`
    - Área: backend · Responsable: Davidson
    - `@DataJpaTest` en H2: cada especificación por separado y combinada, escape de `%`, `_` y `\`, referencia nula sin coincidencia y filtros ausentes sin restricción
    - _Requisitos: 5.3, 10.3, 15.4, 15.5, NF-07_

  - [ ] 5.9 Crear `ErpDatosPrueba` en el paquete de prueba `com.arise.corp.erp`
    - Área: backend · Responsable: Davidson
    - Componente de prueba que se importa con `@Import({CrmDatosPrueba.class, ErpDatosPrueba.class})`: crea empleados, proveedores y movimientos ficticios en cualquier estado (aplicando `cambiarEstado` de la entidad) y su `limpiar()` borra movimientos, proveedores y empleados y después llama a `CrmDatosPrueba.limpiar()`. Los perfiles y los clientes se crean con `CrmDatosPrueba`; la autenticación usa `AutenticacionPerfilTestSupport.como(...)` con el `PerfilJwtAuthenticationConverter` real
    - _Requisitos: 25.5, 25.6, NF-07_

- [ ] 6. Implementar la máquina de estados del movimiento y el catálogo de conflictos
  - [ ] 6.1 Añadir las transiciones a `EstadoMovimiento` y las defensas de `MovimientoFinanciero`
    - Área: backend · Responsable: Sebastián
    - `transicionesPermitidas()`, `puedeTransicionarA(destino)`, `esFinal()` y `esEditable()` según el código del diseño (D11)
    - En `MovimientoFinanciero`: `crear` y `actualizarDatos` lanzan `IllegalArgumentException` con `clienteId` en un `EGRESO` o con proveedor en un `INGRESO` (D8); `actualizarDatos` lanza `IllegalStateException` si `!estado.esEditable()`; `cambiarEstado` la lanza ante una transición no permitida. Son defensas de invariantes: el servicio comprueba cada regla antes
    - _Requisitos: 14.2, 14.3, 16.4, 17.1, 17.2, ERP-03, DP-API-05_

  - [ ] 6.2 Escribir `erp/entity/EstadoMovimientoTests`
    - Área: backend · Responsable: Sebastián
    - `@ParameterizedTest` exhaustivo sobre los 9 pares de origen y destino, `esFinal` y `esEditable`
    - **Property 12: Solo se aceptan las dos transiciones permitidas** (nivel enumeración) · **Validates: Requirements 17.1, 17.2, 25.2**
    - _Requisitos: 25.2, NF-07_

  - [ ]* 6.3 Escribir `erp/entity/MovimientoFinancieroTests`
    - Área: backend · Responsable: Sebastián
    - `crear` deja la auditoría de estado igual a la de datos; `cambiarEstado` no toca datos ni `actualizado_*`; `actualizarDatos` no toca el estado ni su auditoría; defensas `IllegalArgumentException` (relación incoherente) e `IllegalStateException` (no editable y transición no permitida)
    - _Requisitos: 13.1, 16.5, 17.5, NF-07_

  - [ ] 6.4 Crear `erp.service.ConflictosErp`
    - Área: backend · Responsable: Sebastián
    - Clase `final` con una fábrica de `ConflictoNegocioException` por fila del catálogo de "Error Handling": `empleadoDuplicado()`, `empleadoInactivoExistente(UUID)`, `proveedorDuplicado()`, `proveedorInactivoExistente(UUID)`, `clienteInactivo()`, `proveedorInactivo()`, `movimientoNoEditable(EstadoMovimiento)` y `transicionNoPermitida(EstadoMovimiento, EstadoMovimiento)`
    - Cada fábrica fija el código, `campo`, `detail` y las extensiones (`empleadoId`, `proveedorId`, `estadoActual`, `estadoSolicitado`), sin documentos, nombres ni datos de contacto
    - _Requisitos: 8.4, 20.4, 20.5, CA-06, DP-API-02_

- [ ] 7. Checkpoint de persistencia y componentes compartidos
  - Área: verificación · Responsable: Davidson
  - Ejecutar `.\mvnw.cmd clean test` desde `backend/`: el contexto arranca con las entidades ERP en H2, el `ValidationConfigurationCustomizer` queda registrado y las pruebas existentes de `001-foundation` y `002-crm-clientes-contratos` pasan. Asegurar que todas las pruebas pasan; consultar a la usuaria si surgen dudas.

- [ ] 8. Implementar la vertical de empleados
  - [ ] 8.1 Crear los DTO de empleados y la validación de documentos del ERP
    - Área: backend · Responsable: Davidson
    - `EmpleadoRequest` como el record del diseño: `@JsonIgnoreProperties(ignoreUnknown = true)`, normalización en el constructor compacto, `@NotNull` separado del formato, `@Correo`, `@Telefono`, `@FechaNoFutura`, mensajes en español y `toString()` "EmpleadoRequest[datos protegidos]"
    - `EmpleadoResponse` con los 13 campos, `static desde(Empleado)` y `toString()` protegido; `EstadoActivoRequest` con `activo` obligatorio, compartido con proveedores (D14)
    - En `erp.dto.validacion`: interfaz `DocumentoErp` y restricción de clase `@NumeroDocumentoErpValido` con su validador, que informa sobre el nodo `numeroDocumento` "debe tener entre 3 y 20 letras o dígitos" o `mensajeSoloDigitos()` (D4). `NumeroDocumentoValido` del CRM no cambia
    - _Requisitos: 3.1, 3.2, 3.5, 4.2–4.7, 4.10, 5.6, 7.4, 8.1–8.3, 20.2, ERP-01_

  - [ ] 8.2 Escribir `erp/dto/EmpleadoRequestValidacionTests`
    - Área: backend · Responsable: Davidson
    - `jakarta.validation.Validator` con `ClockProvider` fijo y `GeneradoresErp`; mensajes en español; `1.023.456-7` se normaliza a `10234567`; `tipoDocumento` `NIT` no es un valor de la enumeración
    - **Property 1: La normalización es idempotente** (campos de `EmpleadoRequest`) · **Validates: Requirements 3.4, 3.5**
    - **Property 2: Las variantes de formato de un documento o de una referencia son el mismo valor** (documento) · **Validates: Requirements 3.1, 3.2**
    - **Property 3: Las validaciones de formato coinciden con la regla de referencia** · **Validates: Requirements 4.4, 4.5, 4.6, 4.7, 6.2**
    - **Property 4: Un error por cada campo inválido** · **Validates: Requirements 4.2, 4.3, 20.2**
    - _Requisitos: 25.3, 25.7, NF-07_

  - [ ] 8.3 Implementar `EmpleadoService`
    - Área: backend · Responsable: Davidson
    - Record `erp.service.FiltroEmpleados(String q, Boolean activo)`
    - `listar`, `obtener` (404 "El empleado no existe."), `crear` (activo → `EMPLEADO_DUPLICADO`; inactivo → `EMPLEADO_INACTIVO_EXISTENTE` con `empleadoId`), `actualizar` (404; documento de otro empleado activo o inactivo → `EMPLEADO_DUPLICADO`; conserva `activo` y `creadoEn`) y `cambiarEstado` (sin tocar la auditoría si el valor coincide)
    - `ConflictosErp`, `EspecificacionesErp`, `@Transactional`, `Clock` con `Instant.now(clock).truncatedTo(ChronoUnit.MICROS)`, actor con `UUID.fromString(sub)` y conversión a DTO dentro de la transacción. No registrar datos de empleados en logs
    - _Requisitos: 1.4, 4.1, 4.8–4.10, 5.1–5.4, 5.6, 5.7, 6.1, 6.3–6.5, 7.1–7.3, 7.6, 8.3, 19.6, ERP-01, DP-SEG-06_

  - [ ] 8.4 Escribir `erp/service/EmpleadoServiceTests`
    - Área: backend · Responsable: Davidson
    - Mockito y `Clock.fixed`: creación y edición, conservación de `activo` y `creadoEn`, `actualizadoPor = sub` y un documento ya registrado como cliente o proveedor que no impide registrar el empleado
    - **Property 7: Los duplicados se detectan sobre el documento normalizado** (empleados activos e inactivos, con variantes) · **Validates: Requirements 4.8, 4.9, 6.3**
    - **Property 8: Activar y desactivar es idempotente** (empleados) · **Validates: Requirements 7.1, 7.2, 7.3**
    - **Property 6: Los campos controlados por el servidor no se toman del cuerpo** (auditoría y conservación en el servicio) · **Validates: Requirements 4.1, 6.1, 6.4, 19.6**
    - _Requisitos: 25.1, NF-07, ERP-01_

  - [ ] 8.5 Implementar `EmpleadoController`
    - Área: backend · Responsable: Davidson
    - `GET`, `GET /{id}`, `POST` (201 con `Location` relativa), `PUT /{id}` y `PATCH /{id}` en `/api/erp/empleados`; parámetros `q`, `activo`, `page`, `size` y `sort` (`nombre`, `numeroDocumento`, `fechaIngreso` o `creadoEn`; por defecto `nombre,asc`) con las restricciones en los `@RequestParam` y sin `@Validated` a nivel de clase
    - Actor tomado de `@AuthenticationPrincipal Jwt`; sin parámetro `Authentication` y sin `DELETE` mapeado, de modo que el gestor recibe 405 con `Allow`
    - _Requisitos: 1.2, 1.3, 4.1, 5.1–5.8, 6.1, 7.1–7.6, 8.1, ERP-01_

  - [ ] 8.6 Escribir `erp/controller/EmpleadoControllerTests`
    - Área: backend · Responsable: Davidson
    - MockMvc + H2 con `ErpDatosPrueba`: 201 con `Location`, 200, 400 por cada regla (incluidos `id` no UUID y `tipoDocumento: "NIT"`), 404, 409 `EMPLEADO_DUPLICADO` y `EMPLEADO_INACTIVO_EXISTENTE` con `conflicto`, `campo` y `empleadoId`, y 405 de `DELETE` con `Allow`. La fecha de ingreso futura se prueba con dos días más que la fecha actual en Bogotá
    - **Property 6: Los campos controlados por el servidor no se toman del cuerpo** · **Validates: Requirements 4.10, 6.4**
    - **Property 15: Los parámetros de lista y de rango se aceptan o rechazan según la regla** · **Validates: Requirements 5.2, 5.5**
    - **Property 17: Las representaciones del ERP contienen exactamente los campos del contrato** (ambos roles y parámetros extra) · **Validates: Requirements 5.6, 8.1, 8.2**
    - **Property 19: Los errores del ERP no exponen información interna ni datos personales** · **Validates: Requirements 8.4, 20.1, 20.3, 20.8**
    - _Requisitos: 4.1–4.10, 5.1–5.8, 6.1–6.5, 7.1–7.6, 25.7, NF-07_

  - [ ] 8.7 Escribir `config/ClockConfigTests`
    - Área: backend · Responsable: Davidson
    - `@SpringBootTest` + MockMvc con un `Clock` `@Primary` fijo en `2026-11-21T03:30:00Z` declarado en una `@TestConfiguration`: `POST /api/erp/empleados` con `fechaIngreso = 2026-11-21` responde 400 en `fechaIngreso` y con `2026-11-20` responde 201. Demuestra que Spring MVC valida con el reloj de la aplicación (D7)
    - _Requisitos: 4.7, 25.1, NF-07_

- [ ] 9. Implementar la vertical de proveedores
  - [ ] 9.1 Crear `ProveedorRequest` y `ProveedorResponse`
    - Área: backend · Responsable: Davidson
    - `ProveedorRequest` con la forma de `EmpleadoRequest`: `TipoDocumentoProveedor`; `nombreContacto`, `direccion` y `ciudad` opcionales (`@Size` sin `@NotNull`, vacío → `null`); `exigeSoloDigitos()` verdadero para `CC` y `NIT` con el mensaje "solo admite dígitos para el tipo CC o NIT"; `toString()` protegido
    - `ProveedorResponse` con los 13 campos, `static desde(Proveedor)` y `toString()` protegido
    - _Requisitos: 3.1, 3.2, 3.5, 8.1–8.3, 9.2–9.6, 9.9, 10.6, ERP-02_

  - [ ] 9.2 Escribir `erp/dto/ProveedorRequestValidacionTests`
    - Área: backend · Responsable: Davidson
    - `Validator` y `GeneradoresErp`; opcionales vacíos convertidos en `null`
    - **Property 2: Las variantes de formato de un documento o de una referencia son el mismo valor** (documento de proveedor) · **Validates: Requirements 3.1, 3.2**
    - **Property 3: Las validaciones de formato coinciden con la regla de referencia** · **Validates: Requirements 9.4, 9.5, 9.6, 11.2**
    - **Property 4: Un error por cada campo inválido** · **Validates: Requirements 9.2, 9.3**
    - _Requisitos: 25.3, 25.7, NF-07_

  - [ ] 9.3 Implementar `ProveedorService`
    - Área: backend · Responsable: Davidson
    - Record `FiltroProveedores(String q, Boolean activo)`; `listar`, `obtener` (404 "El proveedor no existe."), `crear` (`PROVEEDOR_DUPLICADO`; `PROVEEDOR_INACTIVO_EXISTENTE` con `proveedorId`), `actualizar` (opcionales omitidos quedan nulos; conserva `activo` y `creadoEn`) y `cambiarEstado` (no consulta ni modifica movimientos)
    - Mismo patrón transaccional, de reloj y de auditoría que `EmpleadoService`. Cada servicio busca duplicados solo en su tabla
    - _Requisitos: 8.3, 9.1, 9.7–9.9, 10.1–10.4, 10.6, 10.7, 11.1, 11.3–11.6, 12.1, 12.2, 12.4, 12.6, ERP-02, DP-SEG-06_

  - [ ] 9.4 Escribir `erp/service/ProveedorServiceTests`
    - Área: backend · Responsable: Davidson
    - Mockito y `Clock.fixed`: creación, edición con opcionales omitidos a `null`, `actualizadoPor = sub` y desactivación sin interacción con `MovimientoFinancieroRepository`
    - **Property 7: Los duplicados se detectan sobre el documento normalizado** (proveedores) · **Validates: Requirements 9.7, 9.8, 11.4**
    - **Property 8: Activar y desactivar es idempotente** (proveedores) · **Validates: Requirements 12.1, 12.2**
    - **Property 11: Desactivar un cliente o un proveedor conserva sus movimientos** (nivel unitario) · **Validates: Requirements 12.4**
    - _Requisitos: 25.1, NF-07, ERP-02_

  - [ ] 9.5 Implementar `ProveedorController`
    - Área: backend · Responsable: Davidson
    - `GET`, `GET /{id}`, `POST`, `PUT /{id}` y `PATCH /{id}` en `/api/erp/proveedores`, con `q`, `activo`, `page`, `size` y `sort` (`nombre`, `numeroDocumento` o `creadoEn`; por defecto `nombre,asc`) y sin `DELETE` mapeado
    - Confirmar en el mismo commit que la tarea 12.1 (retirada del doble `GET /api/erp/proveedores`), coordinado con Sebastián: si conviven, el contexto falla por mapeo ambiguo (D16)
    - _Requisitos: 1.2, 1.3, 9.1, 10.1–10.7, 11.1, 12.1–12.6, ERP-02_

  - [ ] 9.6 Escribir `erp/controller/ProveedorControllerTests`
    - Área: backend · Responsable: Davidson
    - 201 con `Location`, 200, 400 por cada regla, 404, 409 `PROVEEDOR_DUPLICADO` y `PROVEEDOR_INACTIVO_EXISTENTE` con `proveedorId`, `PUT` que deja nulos los opcionales omitidos y 405 de `DELETE` con `Allow`
    - **Property 6: Los campos controlados por el servidor no se toman del cuerpo** · **Validates: Requirements 9.1, 9.9, 11.1, 11.3, 11.5**
    - **Property 15: Los parámetros de lista y de rango se aceptan o rechazan según la regla** · **Validates: Requirements 10.2, 10.5**
    - **Property 17: Las representaciones del ERP contienen exactamente los campos del contrato** · **Validates: Requirements 8.1, 10.6**
    - **Property 19: Los errores del ERP no exponen información interna ni datos personales** · **Validates: Requirements 8.4, 20.8**
    - _Requisitos: 9.1–9.9, 10.1–10.7, 11.1–11.6, 12.1–12.6, 25.7, NF-07_

- [ ] 10. Implementar la vertical de movimientos financieros
  - [ ] 10.1 Crear los DTO de movimientos y `@RelacionCoherente`
    - Área: backend · Responsable: Sebastián
    - `MovimientoRequest` como el record del diseño, para `POST` y `PUT` (D14): `@ValorCopPositivo`, `@Pattern` de referencia y normalización con `NormalizadorTexto.texto` y `NormalizadorTexto.codigo`; `EstadoMovimientoRequest` con `estado` obligatorio
    - `RegistroAsociado(UUID id, String nombre, boolean activo)` con `desde(Proveedor)` y `desde(ClienteReferencia)`, que devuelven `null` con entrada `null`; `MovimientoResponse` con `valor` como texto `setScale(2, RoundingMode.UNNECESSARY).toPlainString()` y `static desde(MovimientoFinanciero, ClienteReferencia)` con `Objects.requireNonNull` si hay `clienteId` (D3)
    - Restricción de clase `@RelacionCoherente` en `erp.dto.validacion`: no informa nada si `tipo` es `null`; informa "solo se admite en movimientos de tipo INGRESO" sobre `clienteId` y "solo se admite en movimientos de tipo EGRESO" sobre `proveedorId` (D8)
    - _Requisitos: 3.3, 3.5, 13.2–13.9, 14.1–14.3, 14.12, 15.8, 15.9, 17.3, ERP-03, ERP-05, DP-API-04_

  - [ ] 10.2 Escribir `erp/dto/MovimientoRequestValidacionTests`
    - Área: backend · Responsable: Sebastián
    - `Validator` y `GeneradoresErp`; una referencia con `ñ` se rechaza; mensajes en español
    - **Property 2: Las variantes de formato de un documento o de una referencia son el mismo valor** (referencia) · **Validates: Requirements 3.3**
    - **Property 3: Las validaciones de formato coinciden con la regla de referencia** (concepto, valor y referencia) · **Validates: Requirements 13.4, 13.5, 13.6, 13.7, 13.8, 16.2**
    - **Property 4: Un error por cada campo inválido** · **Validates: Requirements 13.2, 13.3**
    - **Property 9: La relación es coherente con el tipo** (las 8 combinaciones a nivel de DTO) · **Validates: Requirements 14.1, 14.2, 14.3**
    - _Requisitos: 25.3, 25.7, NF-07_

  - [ ] 10.3 Crear `RangoFechas` y `FiltroMovimientos`
    - Área: backend · Responsable: Sebastián
    - `record RangoFechas(LocalDate desde, LocalDate hasta)` en `erp.service`, que lanza `ParametroInvalidoException("hasta", "debe ser igual o posterior a la fecha desde")` si ambos límites existen y `desde` es posterior a `hasta` (D9)
    - `record FiltroMovimientos(TipoMovimiento tipo, EstadoMovimiento estado, UUID clienteId, UUID proveedorId, RangoFechas rango, String q)`
    - _Requisitos: 15.3, 15.7, 18.3, 18.5_

  - [ ]* 10.4 Escribir `erp/service/RangoFechasTests`
    - Área: backend · Responsable: Sebastián
    - **Property 15: Los parámetros de lista y de rango se aceptan o rechazan según la regla** (rangos de `GeneradoresErp.rango` y rangos invertidos) · **Validates: Requirements 15.7, 18.5**
    - _Requisitos: 15.7, 18.5, NF-07_

  - [ ] 10.5 Implementar `MovimientoService`
    - Área: backend · Responsable: Sebastián
    - Dependencias: `MovimientoFinancieroRepository`, `ProveedorRepository`, `ClienteReferenciaService`, `ConflictosErp` y `Clock`
    - `listar` (`Specification.allOf` de los filtros, `findAll` con `@EntityGraph` y una sola llamada a `buscarTodos` con los `clienteId` de la página; lista vacía con un `clienteId` o `proveedorId` sin registro), `obtener` (404 "El movimiento financiero no existe.") y `crear` (cliente con `buscarParaAsociar` y proveedor con `findParaAsociarById`: 404 con `campo`; inactivo → `CLIENTE_INACTIVO` o `PROVEEDOR_INACTIVO`; creación en `PENDIENTE`)
    - `actualizar` con `findParaModificarById`, la regla de asociación del diseño (nulo elimina la relación; la actual se conserva aunque esté inactiva; una nueva debe existir y estar activa) y el orden 404 del movimiento → 404 de la referencia → `MOVIMIENTO_NO_EDITABLE` con `estadoActual` → `CLIENTE_INACTIVO` o `PROVEEDOR_INACTIVO`
    - `cambiarEstado` con `PESSIMISTIC_WRITE` y `TRANSICION_NO_PERMITIDA` con `estadoActual` y `estadoSolicitado`; no usa el estado del cliente ni del proveedor y solo actualiza la auditoría de estado (D12, D13). No registrar datos de clientes ni proveedores en logs
    - _Requisitos: 1.4, 1.5, 13.1, 14.4–14.12, 15.1–15.6, 15.8, 15.10, 16.1–16.6, 17.1, 17.2, 17.4–17.6, 17.8, 19.6, ERP-03, ERP-05, DP-ARQ-01, DP-SEG-06_

  - [ ] 10.6 Escribir `erp/service/MovimientoServiceTests`
    - Área: backend · Responsable: Sebastián
    - Mockito y `Clock.fixed` con `ClienteReferenciaService` y `ProveedorRepository` simulados: creación en `PENDIENTE` con auditorías iguales y el `sub` del actor; `listar` llama a `buscarTodos` una sola vez; orden 404 → 409 del `PUT`; `cambiarEstado` no llama a `buscarParaAsociar` ni a `findParaAsociarById`
    - **Property 10: Solo se asocian registros activos o la asociación actual** (15 combinaciones por cliente y por proveedor) · **Validates: Requirements 14.4, 14.5, 14.6, 14.7, 14.8, 14.9**
    - **Property 13: Solo se editan movimientos pendientes** · **Validates: Requirements 16.1, 16.4**
    - **Property 12: Solo se aceptan las dos transiciones permitidas** (relacionado ausente, activo o inactivo) · **Validates: Requirements 17.1, 17.2, 17.5, 17.6**
    - _Requisitos: 25.1, 25.2, NF-07, ERP-03, ERP-05_

  - [ ] 10.7 Implementar `MovimientoController`
    - Área: backend · Responsable: Sebastián
    - `GET`, `GET /{id}`, `POST`, `PUT /{id}` y `PATCH /{id}/estado` en `/api/erp/movimientos` según el código del diseño: filtros `tipo`, `estado`, `clienteId`, `proveedorId`, `desde` y `hasta` con `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)`, `q`, `page`, `size` y `sort` (`fecha`, `valor` o `creadoEn`; por defecto `fecha,desc`); `RangoFechas` construido en el controlador; sin `DELETE` mapeado
    - _Requisitos: 1.2, 1.3, 13.1, 15.1–15.10, 16.1, 17.1, 17.3, 17.7, 17.8, ERP-03_

  - [ ] 10.8 Escribir `erp/controller/MovimientoControllerTests`
    - Área: backend · Responsable: Sebastián
    - 201 con `Location`, 200, 400 por cada regla (incluidos `estado` desconocido, `fecha` inválida, `valor` `"abc"`, relación incoherente y `desde > hasta` en `hasta`), 404 de la ruta y de `clienteId` o `proveedorId` con `campo`, 409 de cada conflicto con `conflicto`, `campo` y extensiones, y 405 de `DELETE` con `Allow`; `valor` serializado con dos decimales
    - **Property 6: Los campos controlados por el servidor no se toman del cuerpo** · **Validates: Requirements 13.1, 13.9, 16.3, 16.5**
    - **Property 15: Los parámetros de lista y de rango se aceptan o rechazan según la regla** · **Validates: Requirements 15.1, 15.7**
    - **Property 17: Las representaciones del ERP contienen exactamente los campos del contrato** (`cliente` y `proveedor` anidados; el elemento de la lista es igual al detalle) · **Validates: Requirements 14.12, 15.8**
    - **Property 19: Los errores del ERP no exponen información interna ni datos personales** · **Validates: Requirements 8.4, 20.1, 20.3, 20.8**
    - _Requisitos: 13.1–13.9, 14.1–14.12, 15.1–15.10, 16.1–16.6, 17.1–17.8, 20.4–20.6, 25.7, NF-07_

  - [ ]* 10.9 Escribir `erp/dto/SerializacionErpTests`
    - Área: backend · Responsable: Sebastián
    - `@SpringBootTest` con el `ObjectMapper` de la aplicación; `cliente`, `proveedor`, `desde` y `hasta` se serializan como `null`
    - **Property 5: Los importes viajan como texto sin perder precisión** (`valor` de ida y vuelta como texto y como número) · **Validates: Requirements 15.9**
    - **Property 6: Los campos controlados por el servidor no se toman del cuerpo** (propiedades desconocidas ignoradas) · **Validates: Requirements 4.10, 9.9, 13.9**
    - _Requisitos: 15.9, NF-07_

- [ ] 11. Implementar el panel financiero
  - [ ] 11.1 Crear `ResumenFinancieroResponse`
    - Área: backend · Responsable: Sebastián
    - `record ResumenFinancieroResponse(LocalDate desde, LocalDate hasta, Map<TipoMovimiento, ResumenTipo> porTipo, String saldoPagado)` con `ResumenTipo(Map<EstadoMovimiento, ResumenEstado> porEstado, long cantidad)` y `ResumenEstado(long cantidad, String suma)`
    - Función pura `static desde(desde, hasta, List<TotalMovimientos>)` con los cinco pasos del diseño: `EnumMap` con las seis combinaciones en `(0, 0.00)`, copia de las filas, cantidad del tipo como suma de sus estados, `saldoPagado = suma(INGRESO, PAGADO) − suma(EGRESO, PAGADO)` y serialización con `setScale(2, RoundingMode.UNNECESSARY).toPlainString()` (D10)
    - _Requisitos: 18.1, 18.2, 18.4, 18.6–18.8, ERP-04, DP-API-04_

  - [ ] 11.2 Escribir `erp/dto/ResumenFinancieroResponseTests`
    - Área: backend · Responsable: Sebastián
    - Conjuntos de `GeneradoresErp.conjuntoMovimientos` agrupados por el modelo y comparados con el cálculo directo; caso sin filas
    - **Property 14: El resumen financiero es consistente con los movimientos** (función pura) · **Validates: Requirements 18.1, 18.2, 18.4, 18.6, 18.7, 25.4**
    - **Property 5: Los importes viajan como texto sin perder precisión** (sumas y saldo con dos decimales y signo) · **Validates: Requirements 18.8**
    - _Requisitos: 25.4, NF-07, ERP-04_

  - [ ] 11.3 Implementar `ResumenFinancieroService`
    - Área: backend · Responsable: Sebastián
    - `@Service @Transactional(readOnly = true)`; `obtener(RangoFechas)` llama una vez a `totalizar(desde, hasta)` y construye la respuesta con `ResumenFinancieroResponse.desde`
    - _Requisitos: 18.1, 18.3, 18.6, CA-04, ERP-04_

  - [ ]* 11.4 Escribir `erp/service/ResumenFinancieroServiceTests`
    - Área: backend · Responsable: Sebastián
    - Mockito: delega en `totalizar` con los límites recibidos, también nulos
    - _Requisitos: 18.3, NF-07_

  - [ ] 11.5 Implementar `ResumenFinancieroController`
    - Área: backend · Responsable: Sebastián
    - `GET /api/erp/resumen` con `desde` y `hasta` opcionales (`@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)`) y `resumenService.obtener(new RangoFechas(desde, hasta))`
    - _Requisitos: 1.2, 1.3, 18.1, 18.3–18.5, ERP-04_

  - [ ] 11.6 Escribir `erp/controller/ResumenFinancieroControllerTests`
    - Área: backend · Responsable: Sebastián
    - Los dos tipos y los tres estados presentes, con 0 y `"0.00"` sin movimientos; `desde` y `hasta` repetidos o `null`; saldo negativo con signo; 400 por una fecha inválida en el parámetro afectado y por `desde > hasta` en `hasta`
    - **Property 15: Los parámetros de lista y de rango se aceptan o rechazan según la regla** · **Validates: Requirements 18.5**
    - _Requisitos: 18.1–18.8, 25.7, NF-07_

- [ ] 12. Escribir las pruebas de autorización y de logs del ERP
  - [ ] 12.1 Retirar el doble ERP de `AutorizacionModulosTests`
    - Área: backend · Responsable: Sebastián
    - Eliminar la constante `RUTA_ERP` y el doble `listarProveedores()`; conservar el doble `GET /api/sga/paquetes`
    - `sinTokenDevuelve401`, `sinPerfilActivoDevuelve403EnCualquierModulo` y `perfilInactivoDevuelve403EnCualquierModulo` recorren solo `RUTA_SGA`; eliminar `finanzasAccedeAErpYOperacionesNo`; renombrar `comercialNoAccedeAErpNiSga` a `comercialNoAccedeASga` y `administradorAccedeATodosLosModulos` a `administradorAccedeASga`; `operacionesAccedeASgaYFinanzasNo` no cambia; el Javadoc indica que los dobles son solo de SGA y que CRM y ERP se prueban en `CrmAutorizacionTests` y `ErpAutorizacionTests` (D16)
    - Confirmar en el mismo commit que `ProveedorController` (9.5), coordinado con Davidson
    - _Requisitos: 25.9, CA-03_

  - [ ] 12.2 Escribir `erp/controller/ErpAutorizacionTests`
    - Área: backend · Responsable: Sebastián
    - Matriz exhaustiva: 19 operaciones (16 endpoints y los tres `DELETE`) × {sin token, sin perfil, perfil inactivo, ADMINISTRADOR, COMERCIAL, FINANZAS_RRHH, OPERACIONES}, con `ErpDatosPrueba` y el `PerfilJwtAuthenticationConverter` real sobre perfiles persistidos en H2. En los casos denegados, datos sin cambios y 403 también con un `{id}` inexistente; `DELETE` con un rol gestor → 405 con `Allow` y registro conservado
    - Caso adicional: FINANZAS_RRHH lee `GET /api/crm/clientes/{id}` con la Vista_Reducida_Cliente y recibe 403 en `POST /api/crm/clientes`
    - **Property 18: La matriz de acceso se aplica a todos los endpoints del ERP** · **Validates: Requirements 7.5, 12.5, 17.7, 19.1, 19.2, 19.3, 19.4, 19.5, 25.5**
    - _Requisitos: 19.1–19.7, 25.5, 25.6, 25.10, CA-02, CA-03, NF-02, NF-07_

  - [ ] 12.3 Escribir `erp/ErpLogsTests`
    - Área: backend · Responsable: Sebastián
    - Con `OutputCaptureExtension`: crear y editar un empleado y un proveedor y provocar un 409 de duplicado y un 500 (endpoint de prueba que lanza una excepción sin datos) con datos personales ficticios conocidos; la salida capturada no contiene el nombre, el número de documento, el correo, el teléfono ni la dirección
    - _Requisitos: 8.3, 25.8, CA-06_

- [ ] 13. Checkpoint del backend sin Docker
  - Área: verificación · Responsable: Sebastián
  - Ejecutar `.\mvnw.cmd clean test` desde `backend/` sin Docker, sin Supabase y sin `.env` (Requirement 25.11), con las pruebas de `001-foundation` y `002-crm-clientes-contratos` en verde. Asegurar que todas las pruebas pasan; consultar a la usuaria si surgen dudas.

- [ ] 14. Implementar las pruebas de integración del ERP con PostgreSQL efímero
  - [ ] 14.1 Ampliar `PostgresIntegracionBase`
    - Área: backend · Responsable: Sebastián
    - El `truncate` de `@BeforeEach` incluye `app.movimientos_financieros`, `app.proveedores` y `app.empleados` antes de las tablas del CRM, `app.perfiles_usuario` y `auth.users`, con `cascade`, según el diseño. `MigracionesSupabase`, `PostgresContenedorConfig`, el Script_Preparacion_IT y `pom.xml` no cambian
    - _Requisitos: 26.1, 26.9, 26.10, ENG-03_

  - [ ] 14.2 Escribir `MigracionesErpIT`
    - Área: backend · Responsable: Davidson
    - `ddl-auto=validate` acepta las entidades del ERP; las tres tablas están vacías tras migrar; `information_schema` confirma `uuid`, `date` (`fecha_ingreso`, `fecha`), `numeric(15,2)` (`valor`), `timestamptz` y `text`
    - Inserciones JDBC inválidas producen SQLState 23505 (documento duplicado de empleado y de proveedor), 23514 (tipos de documento, `CC` con letras en empleado, `NIT` con letras en proveedor, longitudes, `tipo`, `estado`, `valor = 0` y negativo, concepto corto, referencia en minúsculas, `cliente_id` con `EGRESO` y `proveedor_id` con `INGRESO`) y 23503 (FK de cliente y de proveedor); RLS activo y sin privilegios `select`, `insert`, `update` ni `delete` para `anon` y `authenticated`
    - **Property 9: La relación es coherente con el tipo** (restricciones `CHECK` de PostgreSQL) · **Validates: Requirements 14.2, 14.3**
    - _Requisitos: 2.1–2.10, 26.1–26.4, DP-SEG-05, ENG-03_

  - [ ] 14.3 Escribir `MovimientoTransaccionIT`
    - Área: backend · Responsable: Sebastián
    - Un cambio de estado confirmado actualiza juntas `estado`, `estado_actualizado_en` y `estado_actualizado_por` sin tocar `actualizado_*`; un `TransactionTemplate` que llama a `cambiarEstado` y después lanza una excepción no deja cambios en el estado ni en su auditoría
    - Concurrencia (D12): dos hilos intentan `PENDIENTE→PAGADO` y `PENDIENTE→ANULADO` sobre el mismo movimiento; exactamente uno se aplica y el otro termina en `TRANSICION_NO_PERMITIDA`
    - _Requisitos: 17.4, 17.5, 26.8, DP-SEG-06, ENG-03_

  - [ ] 14.4 Escribir `FlujoErpIT`
    - Área: backend · Responsable: Sebastián
    - Por MockMvc contra PostgreSQL: `INGRESO` asociado a un cliente activo del CRM; 409 `CLIENTE_INACTIVO` con un cliente inactivo; `EGRESO` asociado a un proveedor activo; un `PUT` que conserva el cliente inactivo se aplica; un `saveAndFlush` de un proveedor con documento duplicado lanza `DataIntegrityViolationException`, que la API traduce a 409 genérico
    - **Property 11: Desactivar un cliente o un proveedor conserva sus movimientos** (desactivación después de asociarlos) · **Validates: Requirements 12.4, 14.10**
    - _Requisitos: 14.6, 14.8, 14.10, 20.7, 26.5, CA-04, ENG-03_

  - [ ] 14.5 Escribir `ConsultasErpIT`
    - Área: backend · Responsable: Sebastián
    - Conjunto sembrado con semilla fija: empleados y proveedores con tildes, mayúsculas y documentos con letras, activos e inactivos; clientes del CRM; movimientos de ambos tipos en los tres estados con fechas repartidas en dos años. Al menos 100 combinaciones generadas de filtros y orden contra la API, incluidos `q` con `%` y `_`, `desde` y `hasta` inclusivos y `clienteId` o `proveedorId` inexistentes
    - **Property 16: Filtros, búsqueda y orden coinciden con un modelo en memoria** · **Validates: Requirements 5.1, 5.3, 5.4, 10.1, 10.3, 10.4, 15.2, 15.3, 15.4, 15.5, 15.6, 26.6**
    - _Requisitos: 26.6, ERP-01, ERP-02, ERP-03, ENG-03_

  - [ ] 14.6 Escribir `ResumenFinancieroIT`
    - Área: backend · Responsable: Sebastián
    - Conjunto sembrado y al menos 100 rangos generados (con y sin límites) comparados con conteos y sumas calculados con JDBC; sumas con dos decimales y saldo negativo
    - **Property 14: El resumen financiero es consistente con los movimientos** (contra PostgreSQL) · **Validates: Requirements 18.1, 18.2, 18.3, 18.4, 18.6, 18.7, 25.4**
    - _Requisitos: 26.7, ERP-04, ENG-03_

- [ ] 15. Aplicar la migración del ERP en `arise-corp-dev`
  - [ ] 15.1 Aplicar la migración revisada de forma controlada
    - Área: base de datos · Responsable: Davidson
    - Requisitos previos: revisión 2.3 aprobada y `MigracionesErpIT` (14.2) en verde. Aplicar con el mismo procedimiento controlado de la tarea 17.1 de `002-crm-clientes-contratos` (D20)
    - Ejecutar las consultas de verificación del diseño (RLS y privilegios de `anon` y `authenticated`) y confirmar en Supabase que `app` sigue fuera de los esquemas expuestos de la Data API
    - Registrar en este `tasks.md` solo resultado, fecha y entorno, sin UUID, credenciales ni datos personales. Desde aquí la migración no se edita; un cambio de esquema llega en una migración nueva
    - _Requisitos: 2.8, 29.7, DP-TEC-03, DP-SEG-05, NF-01_

- [ ] 16. Verificar la imagen Docker y los workflows con el ERP
  - [ ] 16.1 Verificar la imagen y Compose
    - Área: backend/Docker · Responsable: Sebastián
    - `docker build -t arise-backend:local backend` sin credenciales ni conexión a Supabase; `docker run --rm --entrypoint sh arise-backend:local -c "find / -name '.env*' 2>/dev/null"` no devuelve nada y `docker history --no-trunc` no muestra valores de configuración. `backend/Dockerfile`, `backend/.dockerignore` y `docker-compose.yml` no cambian (D21)
    - Con un `backend/.env` válido y la migración aplicada (15.1), `docker compose up backend` y `curl -i http://localhost:8080/api/erp/empleados` → 401 con `WWW-Authenticate`
    - Registrar el resultado en este `tasks.md` sin datos sensibles
    - _Requisitos: 28.1–28.3, ENG-01, CA-01, CA-02, NF-01_

  - [ ] 16.2 Verificar los workflows de CI en la rama
    - Área: CI · Responsable: Davidson
    - Una ejecución verde de `backend-ci`, `integration-tests` y `frontend-ci` para el último commit de la rama; el log de `integration-tests` lista `MigracionesErpIT`, `ConsultasErpIT`, `ResumenFinancieroIT`, `MovimientoTransaccionIT` y `FlujoErpIT`; los YAML de `.github/workflows/` no cambian y conservan `permissions: contents: read`, las acciones oficiales `actions/*` y la ausencia de secretos
    - Si un workflow necesitara cambios, justificarlos antes en `design.md` (Requirement 28.8)
    - _Requisitos: 26.11, 28.4–28.6, 28.8, ENG-04_

- [ ] 17. Documentar el backend y actualizar los steering
  - [ ] 17.1 Actualizar el README
    - Área: documentación · Responsable: Davidson
    - Aviso inicial y §4: estado con el ERP implementado. §5: estructura con `erp/`, `crm/service/ClienteReferenciaService`, `shared/validacion` ampliado, `shared/error/ParametroInvalidoException`, `pages/erp`, `services/erpApi.js` y la Migracion_ERP. La §11.6 la actualiza Paula en la tarea 28.1
    - Confirmar que no hay variables de entorno ni dependencias nuevas y que los `.env.example` no cambian
    - _Requisitos: 29.2, 29.5, CA-01_

  - [ ] 17.2 Actualizar los steering de arquitectura, estructura, tecnología y producto
    - Área: documentación · Responsable: Davidson
    - `architecture.md`: la API incluye el ERP; PostgreSQL incluye `app.empleados`, `app.proveedores` y `app.movimientos_financieros`; módulo `erp` con su relación de solo lectura con el CRM mediante `ClienteReferenciaService`
    - `structure.md`: árbol del backend con `erp/` y sus capas, `crm/service/ClienteReferenciaService`, `shared/validacion` ampliado, `shared/error/ParametroInvalidoException`, pruebas `erp/`, IT nuevas y la Migracion_ERP
    - `tech.md`: persistencia "[Implementado: perfil, CRM y ERP]" y validación "[Implementado] en DTO de login, CRM y ERP". `product.md`: "Estado actual" con ERP-01 a ERP-05 implementados en el backend; "Aprobado, pendiente de implementar" queda solo con SGA. Las líneas del frontend las añade Paula en la tarea 28.1
    - Los ajustes de reglas quedaron registrados en la tarea 1.2; aquí solo se comprueba que el código los cumple
    - _Requisitos: 29.3, CA-07, NF-05_

  - [ ] 17.3 Actualizar los steering de API y seguridad
    - Área: documentación · Responsable: Sebastián
    - `api-conventions.md`: endpoints del ERP en "Endpoints existentes", códigos de conflicto del ERP, `campo` en los 404 de `clienteId` y `proveedorId` y 405 de los `DELETE` del ERP
    - `security.md`: datos personales de empleados y proveedores con una sola representación para los gestores del ERP, sin lectura cruzada, DTO con `toString()` protegido y `ErpLogsTests` como verificación
    - _Requisitos: 8.3, 29.3, CA-07_

- [ ] 18. Checkpoint del backend completo
  - Área: verificación · Responsable: Davidson
  - Ejecutar desde `backend/` `.\mvnw.cmd clean test` y, con Docker en ejecución, `.\mvnw.cmd verify` (Requirement 26.11); desde la raíz, `docker compose build backend`. Asegurar que todas las pruebas pasan; consultar a la usuaria si surgen dudas.

- [ ] 19. Implementar el servicio y las reglas de interfaz del ERP
  - [ ] 19.1 Crear `services/erpApi.js`
    - Área: frontend · Responsable: Paula
    - Las 16 funciones de la tabla "Servicio del ERP" del diseño sobre `apiRequest` de `apiClient.js`, con una función local `listar(ruta, filtros)` igual a la de `crmApi.js`: solo añade a `URLSearchParams` los filtros con valor no nulo y no vacío después de `trim`
    - `crearMovimiento` y `actualizarMovimiento` envían `valor: String(datos.valor)`; las fechas viajan como `AAAA-MM-DD`. Se trabaja sobre el contrato de `design.md`, sin esperar al backend
    - _Requisitos: 21.1–21.3, AUTH-03, DP-API-03, DP-API-04_

  - [ ] 19.2 Ampliar `src/test/generadores.js`
    - Área: frontend · Responsable: Paula
    - Conservar `mulberry32(semilla)` y añadir `entero(azar, min, max)`, `elegir(azar, lista)` y `cadena(azar, alfabeto, longitud)`; las pruebas existentes no cambian
    - _Requisitos: 27.1, 27.2, NF-07_

  - [ ] 19.3 Escribir `services/erpApi.test.js`
    - Área: frontend · Responsable: Paula
    - `fetch` simulado con `vi.stubGlobal` y Supabase con `vi.mock('../lib/supabase.js')`: ruta, método, consulta y cuerpo JSON de cada función, sin llamadas de red reales
    - **Property 20: El servicio ERP del frontend construye las peticiones del contrato** (filtros generados con valores nulos, vacíos, solo espacios o con contenido) · **Validates: Requirements 21.1, 21.3, 27.1**
    - **Property 5: Los importes viajan como texto sin perder precisión** (lado frontend) · **Validates: Requirements 21.2, 23.11**
    - _Requisitos: 27.1, 27.5, NF-07_

  - [ ] 19.4 Crear `lib/validacionErp.js`
    - Área: frontend · Responsable: Paula
    - `lib/validacionCrm.js` pasa a exportar `texto`, `revisar`, `maximo`, `correo`, `telefono` y `fecha` sin cambiar su comportamiento (D18)
    - `normalizarReferencia`, `fechaHoyColombia(ahora)` con `Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts`, `validarEmpleado(datos, hoy)`, `validarProveedor`, `validarMovimiento`, `validarRangoFechas(desde, hasta)` y `cuerpoMovimiento(datos)`, con los límites y mensajes del contrato de API (D7)
    - `validarMovimiento` valida `valor` como texto con `^\d{1,13}(\.\d{1,2})?$` y al menos un dígito distinto de 0, sin convertirlo a número, y la referencia normalizada con `^[A-Z0-9 ./-]{1,50}$`; `cuerpoMovimiento` envía `clienteId` solo con `INGRESO` y `proveedorId` solo con `EGRESO`
    - _Requisitos: 21.2, 22.6, 23.4, 23.8, 23.10, 23.11, 24.4, NF-06_

  - [ ] 19.5 Escribir `lib/validacionErp.test.js`
    - Área: frontend · Responsable: Paula
    - `fechaHoyColombia` con instantes fijos a ambos lados de la medianoche de Bogotá; las pruebas de `validacionCrm.test.js` siguen sin cambios
    - **Property 1: La normalización es idempotente** (`normalizarDocumento` y `normalizarReferencia`) · **Validates: Requirements 3.4**
    - **Property 2: Las variantes de formato de un documento o de una referencia son el mismo valor** · **Validates: Requirements 3.2, 3.3**
    - **Property 3: Las validaciones de formato coinciden con la regla de referencia** · **Validates: Requirements 22.6, 23.10**
    - **Property 9: La relación es coherente con el tipo** (`cuerpoMovimiento` tras secuencias de cambios de tipo y de selecciones) · **Validates: Requirements 23.8**
    - **Property 15: Los parámetros de lista y de rango se aceptan o rechazan según la regla** (`validarRangoFechas`) · **Validates: Requirements 23.4, 24.4**
    - _Requisitos: 27.2, NF-07_

  - [ ] 19.6 Ampliar `lib/permisos.js`
    - Área: frontend · Responsable: Paula
    - `puedeGestionarErp`, `TRANSICIONES_MOVIMIENTO`, `esMovimientoEditable`, `OPCIONES_ERP` y `opcionesMenuErp(rol)` según el diseño; `opcionesMenu(rol)` y el resto de funciones no cambian (D11, D17)
    - _Requisitos: 21.5–21.7, 23.14, 23.15, DP-PRO-01_

  - [ ] 19.7 Ampliar `lib/permisos.test.js`
    - Área: frontend · Responsable: Paula
    - Los casos existentes de `opcionesMenu` no cambian
    - **Property 12: Solo se aceptan las dos transiciones permitidas** (`TRANSICIONES_MOVIMIENTO` y `esMovimientoEditable`) · **Validates: Requirements 23.14, 23.15**
    - **Property 21: El menú y el panel ERP derivan del rol** (`puedeGestionarErp` y `opcionesMenuErp` para los cuatro roles, sin perfil y con un valor desconocido) · **Validates: Requirements 21.5, 21.6, 21.7**
    - _Requisitos: 27.2, NF-07_

  - [ ]* 19.8 Ampliar `lib/formato.test.js`
    - Área: frontend · Responsable: Paula
    - `formatoCop('-1200000.50')` empieza con el signo menos; `"0.00"` y `"9999999999999.99"`. `lib/formato.js` no cambia
    - _Requisitos: 23.12, 24.1, 24.7_

- [ ] 20. Incorporar el menú y las tarjetas del ERP
  - [ ] 20.1 Ampliar `components/AppLayout.jsx` con el grupo ERP
    - Área: frontend · Responsable: Paula
    - Crear `frontend/public/assets/erp.svg`, un SVG estático propio sin datos ni referencias externas
    - `const erp = opcionesMenuErp(rol)`; si no está vacío, grupo "ERP" con el icono y los cuatro enlaces debajo del grupo CRM; sección "ERP" en la ruta de navegación cuando `pathname.startsWith('/erp/')`; título de la página buscado en `[...opciones, ...erp]`. El menú CRM no cambia
    - _Requisitos: 21.5–21.7, 21.11, DP-PRO-01_

  - [ ] 20.2 Ampliar `pages/Panel.jsx` con las tarjetas ERP
    - Área: frontend · Responsable: Paula
    - Si `puedeGestionarErp(rol)`, una tarjeta por cada elemento de `OPCIONES_ERP` con `<small>ERP</small>`, el título y el enlace `accion →`; ninguna tarjeta ERP para COMERCIAL ni OPERACIONES
    - _Requisitos: 21.7, 21.8, ERP-04_

- [ ] 21. Crear los componentes compartidos del ERP
  - [ ] 21.1 Implementar `SelectorRegistro` y `EstadoMovimientoEtiqueta`
    - Área: frontend · Responsable: Paula
    - `components/SelectorRegistro.jsx` con las props `{ etiqueta, buscar, valor, onCambiar, error, deshabilitado }`: búsqueda con el botón "Buscar"; `<select>` con "Sin asociación", los resultados de `buscar(q)` y, si `valor` no está entre ellos, la opción actual "`<nombre>` (inactivo)" cuando `valor.activo === false`; usa `useConsulta`, `EstadoConsulta` y `Campo` (D19)
    - `components/EstadoMovimientoEtiqueta.jsx` con las clases existentes: `PENDIENTE → borrador`, `PAGADO → activo` y `ANULADO → inactivo`. Ningún componente usa `dangerouslySetInnerHTML`
    - _Requisitos: 23.6, 23.7, 23.9, NF-06_

  - [ ]* 21.2 Escribir `components/ComponentesErp.test.jsx`
    - Área: frontend · Responsable: Paula
    - `SelectorRegistro`: búsqueda con `q`, selección, "Sin asociación" y opción "(inactivo)" para la asociación actual; `EstadoMovimientoEtiqueta` para los tres estados
    - _Requisitos: 23.6, 23.7, 23.9_

- [ ] 22. Checkpoint de la base del frontend
  - Área: verificación · Responsable: Paula
  - Ejecutar `npm test` desde `frontend/`: las pruebas nuevas del servicio, las reglas y los componentes pasan junto con las de `001-foundation` y `002-crm-clientes-contratos`. Asegurar que todas las pruebas pasan; consultar a la usuaria si surgen dudas.

- [ ] 23. Implementar las vistas de empleados
  - [ ] 23.1 Implementar `pages/erp/EmpleadosLista.jsx`
    - Área: frontend · Responsable: Paula
    - Búsqueda `q` con el botón "Buscar", filtro Todos/Activos/Inactivos y paginación, con el estado en la URL (`useSearchParams`) como `ClientesLista`; columnas nombre, documento, cargo, área, fecha de ingreso y estado; "Nuevo empleado"; "No hay empleados para los criterios elegidos." si la página está vacía
    - _Requisitos: 22.1–22.3, ERP-01, DP-API-03_

  - [ ] 23.2 Implementar `pages/erp/EmpleadoFormulario.jsx`
    - Área: frontend · Responsable: Paula
    - Alta y edición con selector de tipo `CC`, `CE` y `PASAPORTE`, obligatorios marcados con `*` y fecha de ingreso `type="date"` con `max={fechaHoyColombia()}`
    - Validación local con `validarEmpleado` antes de enviar; `fieldset` deshabilitado y "Guardando…" durante el envío; errores 400 y 409 junto al campo (`errors[].field` o `campo`) sin perder lo escrito; con `EMPLEADO_INACTIVO_EXISTENTE`, el mensaje y el enlace "Ver empleado" a `/erp/empleados/{problem.empleadoId}`
    - _Requisitos: 22.4, 22.6–22.9, ERP-01, NF-06_

  - [ ] 23.3 Implementar `pages/erp/EmpleadoDetalle.jsx`
    - Área: frontend · Responsable: Paula
    - Datos, estado y fechas de creación y actualización con `formatoInstante`; "Editar"; "Desactivar" o "Reactivar" con `DialogoConfirmacion` y `cambiarEstadoEmpleado`; al recibir 200 muestra el nuevo estado
    - _Requisitos: 22.10, 22.11, ERP-01_

  - [ ] 23.4 Escribir `pages/erp/EmpleadosLista.test.jsx`
    - Área: frontend · Responsable: Paula
    - Carga, lista vacía, búsqueda, filtro activo y paginación con la API simulada
    - _Requisitos: 22.1–22.3, 27.4, 27.5_

  - [ ] 23.5 Escribir `pages/erp/EmpleadoFormulario.test.jsx`
    - Área: frontend · Responsable: Paula
    - Validación local sin petición (incluida la fecha de ingreso futura), botón deshabilitado durante el envío, errores 400 por campo con los valores conservados, 409 de duplicado junto a `numeroDocumento` y 409 inactivo existente con el enlace
    - _Requisitos: 22.6–22.9, 27.3_

  - [ ] 23.6 Escribir `pages/erp/EmpleadoDetalle.test.jsx`
    - Área: frontend · Responsable: Paula
    - Confirmación y `PATCH` de desactivación y reactivación, y nuevo estado tras el 200
    - _Requisitos: 22.10, 22.11, 27.3_

- [ ] 24. Implementar las vistas de proveedores
  - [ ] 24.1 Implementar `pages/erp/ProveedoresLista.jsx`
    - Área: frontend · Responsable: Paula
    - Igual que `EmpleadosLista`; columnas nombre, documento, contacto, ciudad y estado; "Nuevo proveedor"; "No hay proveedores para los criterios elegidos."
    - _Requisitos: 22.1–22.3, ERP-02, DP-API-03_

  - [ ] 24.2 Implementar `pages/erp/ProveedorFormulario.jsx`
    - Área: frontend · Responsable: Paula
    - Selector de tipo `NIT`, `CC`, `CE` y `PASAPORTE`; opcionales de contacto, dirección y ciudad; validación local con `validarProveedor`; mismo tratamiento del envío y de los errores que `EmpleadoFormulario`; con `PROVEEDOR_INACTIVO_EXISTENTE`, enlace "Ver proveedor" a `/erp/proveedores/{problem.proveedorId}`
    - _Requisitos: 22.5–22.9, ERP-02, NF-06_

  - [ ] 24.3 Implementar `pages/erp/ProveedorDetalle.jsx`
    - Área: frontend · Responsable: Paula
    - Datos, estado, auditoría, "Editar" y "Desactivar" o "Reactivar" con confirmación; tabla paginada de sus movimientos con `listarMovimientos({ proveedorId, page })`: fecha, concepto, valor con `formatoCop` y estado con `EstadoMovimientoEtiqueta`, enlazados a `/erp/movimientos/{id}`
    - _Requisitos: 22.10–22.12, ERP-02, ERP-05_

  - [ ] 24.4 Escribir `pages/erp/ProveedoresLista.test.jsx`
    - Área: frontend · Responsable: Paula
    - Carga, lista vacía, búsqueda, filtro activo y paginación
    - _Requisitos: 22.1–22.3, 27.4, 27.5_

  - [ ] 24.5 Escribir `pages/erp/ProveedorFormulario.test.jsx`
    - Área: frontend · Responsable: Paula
    - Validación local sin petición, opcionales vacíos, botón deshabilitado durante el envío, 400 por campo con los valores conservados, 409 de duplicado junto a `numeroDocumento` y 409 inactivo existente con el enlace
    - _Requisitos: 22.5–22.9, 27.3_

  - [ ] 24.6 Escribir `pages/erp/ProveedorDetalle.test.jsx`
    - Área: frontend · Responsable: Paula
    - Confirmación y `PATCH` de desactivación y reactivación; movimientos del proveedor pedidos con `proveedorId` y valores en COP
    - _Requisitos: 22.10–22.12, 27.3, 27.4_

- [ ] 25. Implementar las vistas de movimientos financieros
  - [ ] 25.1 Implementar `pages/erp/MovimientosLista.jsx`
    - Área: frontend · Responsable: Paula
    - Filtros tipo, estado, `desde`, `hasta` y búsqueda `q` en la URL; si `validarRangoFechas` falla, mensaje junto a "Hasta" y sin consulta; columnas fecha, tipo, concepto, valor en COP, estado y nombre del cliente o proveedor ("—" sin relación); paginación; "Nuevo movimiento"; "No hay movimientos para los criterios elegidos."
    - _Requisitos: 23.1–23.4, 23.12, ERP-03, ERP-05, DP-API-03_

  - [ ] 25.2 Implementar `pages/erp/MovimientoFormulario.jsx`
    - Área: frontend · Responsable: Paula
    - Alta y edición solo en `PENDIENTE` (si no, "Solo se editan movimientos pendientes." con enlace al detalle); tipo, fecha, concepto, valor como texto con `inputMode="decimal"` y el ejemplo "2500000.50" y referencia opcional
    - `SelectorRegistro` de clientes con `q => listarClientes({ activo: true, q, size: 20, sort: 'nombre,asc' })` de `crmApi.js` para `INGRESO` y de proveedores con `listarProveedores` para `EGRESO`; cambiar el tipo quita la selección incompatible; al editar, la asociación actual aparece seleccionada y, si está inactiva, con "(inactivo)" y el texto "El registro asociado está inactivo; puedes conservarlo."
    - Validación local con `validarMovimiento`; envía `cuerpoMovimiento(datos)`; errores 400, 404 y 409 con `campo` junto al campo (también bajo el selector) con los valores conservados; un 409 sin campo (`MOVIMIENTO_NO_EDITABLE`) muestra el mensaje y vuelve a cargar el movimiento
    - _Requisitos: 21.2, 23.5–23.11, 23.13, 23.17, ERP-03, ERP-05, NF-06_

  - [ ] 25.3 Implementar `pages/erp/MovimientoDetalle.jsx`
    - Área: frontend · Responsable: Paula
    - Datos, valor en COP, estado, auditoría de datos y del último cambio de estado, y enlace a `/crm/clientes/{id}` o `/erp/proveedores/{id}` según la relación
    - En `PENDIENTE`: "Editar", "Marcar como pagado" y "Anular" a partir de `TRANSICIONES_MOVIMIENTO`, cada transición con `DialogoConfirmacion` "Este cambio es irreversible." antes de `cambiarEstadoMovimiento`; en `PAGADO` o `ANULADO`, sin edición ni transiciones; un 409 muestra el mensaje en `role="alert"` y vuelve a cargar el movimiento
    - _Requisitos: 23.12, 23.14–23.18, ERP-03, ERP-05, DP-API-05_

  - [ ] 25.4 Escribir `pages/erp/MovimientosLista.test.jsx`
    - Área: frontend · Responsable: Paula
    - Filtros en la consulta, rango inválido sin petición, columnas con el nombre del relacionado, lista vacía y paginación
    - _Requisitos: 23.1–23.4, 27.4_

  - [ ] 25.5 Escribir `pages/erp/MovimientoFormulario.test.jsx`
    - Área: frontend · Responsable: Paula
    - Selector según el tipo con `listarClientes({ activo: true, ... })`, limpieza al cambiar el tipo, asociación inactiva conservada, validación local sin petición, `valor` enviado como texto, y 404 y 409 con `campo` junto al selector
    - _Requisitos: 23.5–23.11, 23.13, 27.3, 27.4_

  - [ ] 25.6 Escribir `pages/erp/MovimientoDetalle.test.jsx`
    - Área: frontend · Responsable: Paula
    - Confirmación irreversible, recarga tras un 409, edición solo en `PENDIENTE` y enlaces al cliente y al proveedor
    - **Property 12: Solo se aceptan las dos transiciones permitidas** (transiciones ofrecidas en cada estado con `it.each`) · **Validates: Requirements 23.14, 23.15**
    - _Requisitos: 23.14–23.18, 27.3, 27.4_

- [ ] 26. Implementar la vista del panel financiero
  - [ ] 26.1 Implementar `pages/erp/PanelFinanciero.jsx`
    - Área: frontend · Responsable: Paula
    - Filtro `desde` y `hasta` en la URL; con un rango inválido, mensaje junto a "Hasta" y sin petición; secciones Ingresos y Egresos con una fila por estado (incluidos los que tienen 0) con cantidad y suma en COP y la cantidad del tipo
    - Tarjeta "Saldo pagado" con `formatoCop(saldoPagado)`, que muestra el signo menos si es negativo; límites aplicados ("Todo el periodo" si ambos son nulos); carga y error con "Reintentar" mediante `EstadoConsulta`
    - _Requisitos: 24.1–24.7, ERP-04, NF-06_

  - [ ] 26.2 Escribir `pages/erp/PanelFinanciero.test.jsx`
    - Área: frontend · Responsable: Paula
    - Tres estados por tipo con valores en 0, saldo negativo con signo menos, filtro de fechas que vuelve a consultar con `desde` y `hasta`, rango inválido sin petición, y carga y error con "Reintentar"
    - _Requisitos: 24.1–24.7, 27.4_

- [ ] 27. Integrar las rutas ERP en la aplicación
  - [ ] 27.1 Registrar las rutas `/erp/*` en `App.jsx`
    - Área: frontend · Responsable: Paula
    - Bloque `<Route element={<RolRoute permite={puedeGestionarErp} />}>` del diseño dentro de `<Route element={<AppLayout />}>`, con las 13 rutas y las páginas cargadas con `lazy(() => import(...))`; `/login`, `/perfil`, `/panel`, `/admin/usuarios` y `/crm/*` no cambian
    - _Requisitos: 21.9, 21.11, 22.1, 23.1, 24.1_

  - [ ] 27.2 Ampliar `App.test.jsx` con la navegación ERP
    - Área: frontend · Responsable: Paula
    - Menú y tarjetas del panel por rol; una ruta `/erp/*` con COMERCIAL u OPERACIONES muestra el mensaje de permisos sin peticiones a la API; FINANZAS_RRHH conserva Clientes en modo consulta y ve el grupo ERP; un 403 del backend en una vista ERP muestra el mensaje de permisos sin cerrar sesión; las pruebas existentes siguen en verde
    - **Property 21: El menú y el panel ERP derivan del rol** (integración) · **Validates: Requirements 21.5, 21.6, 21.7, 21.8, 21.9**
    - _Requisitos: 21.4–21.11, 27.4–27.6_

- [ ] 28. Documentar el frontend del ERP
  - [ ] 28.1 Actualizar la documentación del frontend
    - Área: documentación · Responsable: Paula
    - README §11.6: subsección "Vistas del ERP por rol" con la tabla de rutas `/erp/*` y sus roles (ADMINISTRADOR y FINANZAS_RRHH) y la nota de que FINANZAS_RRHH consulta clientes en modo lectura para asociarlos a ingresos; comandos `npm test`, `npm run build` y `npm run check:secrets`, sin variables nuevas
    - `structure.md`: `pages/erp`, `components/SelectorRegistro`, `components/EstadoMovimientoEtiqueta`, `services/erpApi.js`, `lib/validacionErp.js` y `public/assets/erp.svg`. `architecture.md`: la SPA presenta las vistas ERP. `product.md`: vistas ERP del frontend implementadas en "Estado actual"
    - _Requisitos: 29.2, 29.3, CA-01, CA-07_

- [ ] 29. Checkpoint del frontend
  - Área: verificación · Responsable: Paula
  - Ejecutar desde `frontend/` `npm test`, `npm run build` y `npm run check:secrets` (Requirements 27.6, 27.7). Asegurar que todas las pruebas pasan; consultar a la usuaria si surgen dudas.

- [ ] 30. Validar el ERP de extremo a extremo con Supabase real
  - [ ] 30.1 Validar el flujo de FINANZAS_RRHH en las vistas ERP
    - Área: integración · Responsable: Paula
    - Precondiciones: migración aplicada (15.1); cuentas de prueba con perfiles FINANZAS_RRHH, COMERCIAL y OPERACIONES creados con `PUT /api/admin/usuarios/{id}/perfil`; al menos un cliente activo ficticio creado por COMERCIAL; backend con `docker compose up backend` y frontend con `npm run dev`
    - Con FINANZAS_RRHH: registrar un empleado, editar su cargo, registrar un proveedor, registrar un `EGRESO` asociado al proveedor y un `INGRESO` asociado al cliente activo buscado en el selector, marcar uno como `PAGADO`, anular el otro, abrir el panel financiero y recargar la aplicación para comprobar que los datos y los totales persisten
    - Registrar en este `tasks.md` fecha, pasos y resultado sin credenciales, tokens, UUID ni datos personales reales
    - _Requisitos: 30.1, 30.5, CA-04, ERP-01, ERP-02, ERP-03, ERP-04, ERP-05_

  - [ ] 30.2 Validar las denegaciones del ERP y del CRM
    - Área: integración · Responsable: Davidson
    - Con COMERCIAL y con OPERACIONES, `/erp/empleados` muestra el mensaje de falta de permisos sin pedir datos. Sin copiar tokens: el usuario FINANZAS_RRHH abre la edición de un empleado, el Administrador cambia temporalmente su rol a COMERCIAL y el usuario envía el formulario; el backend responde 403, el empleado no cambia y la interfaz muestra "No tienes permisos para realizar esta operación." sin cerrar la sesión. Se repite con OPERACIONES
    - Con FINANZAS_RRHH, el detalle de un cliente muestra la vista reducida sin edición; un usuario COMERCIAL con un formulario de cliente abierto, cuyo rol se cambia a FINANZAS_RRHH, recibe 403 sin cambios
    - Al terminar, restablecer los roles, desactivar los empleados y proveedores ficticios y registrar en este `tasks.md` fecha, pasos y resultado sin datos sensibles
    - _Requisitos: 30.2–30.5, AUTH-05, CA-03, NF-02, DP-PRO-01_

- [ ] 31. Checkpoint final
  - Área: verificación · Responsable: Sebastián
  - Ejecutar `.\mvnw.cmd clean test` y `.\mvnw.cmd verify` desde `backend/`; `npm test`, `npm run build` y `npm run check:secrets` desde `frontend/`; y `docker compose build backend` desde la raíz. Asegurar que todas las pruebas pasan; consultar a la usuaria si surgen dudas.

- [ ] 32. Verificar y entregar la spec
  - [ ] 32.1 Verificar CI, secretos, documentación y trazabilidad
    - Área: integración/documentación · Responsable: Davidson
    - Una ejecución verde de `backend-ci`, `integration-tests` y `frontend-ci` en la rama; ningún `.env` rastreado (`git ls-files`); contratos de `design.md` coherentes con los controladores; trazabilidad requisito–tarea revisada; `pom.xml`, `package.json`, los `.env.example`, el Dockerfile, Compose y los workflows sin cambios; `product.md` indica que `003-erp-empleados-proveedores-finanzas` queda cerrada al integrar el PR
    - _Requisitos: 28.4–28.8, 29.1, 29.3, 29.5, CA-01, CA-07, NF-01, ENG-04_

  - [ ] 32.2 Comprobar los checks obligatorios en la protección de `main`
    - Área: CI · Responsable: Davidson
    - La protección configurada en `002-crm-clientes-contratos` sigue activa: PR obligatorio con una aprobación de otro integrante, *squash merge*, rama actualizada antes de integrar y `backend-ci`, `integration-tests` y `frontend-ci` como checks obligatorios. Si algo cambió, restablecerlo sin guardar tokens ni credenciales en el repositorio
    - _Requisitos: 28.7, DP-SDD-05_

  - [ ] 32.3 Abrir el Pull Request único hacia `main`
    - Área: integración · Responsable: Sebastián
    - PR desde `feat/erp-empleados-proveedores-finanzas` con enlace a la spec, requisitos y tareas, instrucciones de prueba, resultados de las suites, construcción Docker, CI en verde, nombre de la Migracion_ERP aplicada y la lista de comprobación de `sdd-workflow.md`; revisión de un integrante distinto del autor de cada cambio; sin secretos en la descripción ni en las capturas
    - _Requisitos: 29.6, CA-07, DP-SDD-04, DP-SDD-05_

## Notes

- Las subtareas marcadas con `*` son pruebas que ningún requisito exige de forma directa y cuyo contenido también lo comprueban pruebas obligatorias: humo de repositorios y especificaciones en H2 (5.7, 5.8), invariantes de la entidad (6.3), rango de fechas aislado (10.4), serialización (10.9), delegación del servicio del panel (11.4), formato COP (19.8) y componentes ERP aislados (21.2). No son opcionales la máquina de estados (6.2, Requirement 25.2), las pruebas de DTO con las propiedades de normalización (8.2, 9.2, 10.2, Requirement 25.3), los servicios (8.4, 9.4, 10.6, Requirement 25.1), el resumen (11.2, Requirement 25.4), los controladores (8.6, 9.6, 10.8, 11.6, Requirement 25.7), la matriz de autorización (12.2, Requirement 25.5), los logs (12.3, Requirement 25.8), las IT (14.2–14.6, Requirement 26) ni las pruebas del frontend que exige el Requirement 27.
- Cada prueba de propiedad cita su Property del diseño y los criterios que valida; la entrada que falla aparece en el mensaje de la aserción.
- `ProveedorController` (9.5) y la retirada del doble ERP de `AutorizacionModulosTests` (12.1) van en el mismo commit; antes de ese commit las demás pruebas MockMvc siguen arrancando porque el doble y los controladores nuevos no comparten rutas.
- Las vistas ERP enlazan `/erp/*` desde el menú y el panel (20.1, 20.2) antes de que existan las rutas (27.1); mientras tanto, la redirección `*` → `/perfil` atiende esos enlaces en la rama.
- Si `.\mvnw.cmd test` muestra `ConflictingBeanDefinitionException` tras mover o renombrar clases, ejecutar `.\mvnw.cmd clean test` (observación del cierre de `001-foundation`).
- La aplicación de la migración (15.1), la verificación de Compose (16.1), la validación manual (30.1, 30.2) y la protección de `main` (32.2) se hacen fuera de CI y sin guardar credenciales, tokens ni UUID en el repositorio.
- Cada tarea referencia los requisitos de `requirements.md` y los identificadores del MVP para trazabilidad.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2"] },
    { "id": 2, "tasks": ["2.1", "3.1", "3.3", "3.4", "3.5", "3.6", "4.1", "5.1", "19.1", "19.2", "19.4", "19.6"] },
    { "id": 3, "tasks": ["2.2", "2.3", "3.2", "3.7", "4.2", "5.2", "6.4", "19.3", "19.5", "19.7", "19.8", "20.1", "20.2", "21.1"] },
    { "id": 4, "tasks": ["5.3", "21.2", "23.1", "23.2", "23.3", "24.1", "24.2", "24.3", "25.1", "25.2", "25.3", "26.1"] },
    { "id": 5, "tasks": ["5.4", "6.1", "23.4", "23.5", "23.6", "24.4", "24.5", "24.6", "25.4", "25.5", "25.6", "26.2", "27.1"] },
    { "id": 6, "tasks": ["5.5", "5.6", "5.9", "6.2", "6.3", "27.2"] },
    { "id": 7, "tasks": ["5.7", "5.8", "8.1", "10.1", "10.3", "11.1", "28.1"] },
    { "id": 8, "tasks": ["8.2", "8.3", "9.1", "10.2", "10.4", "11.2", "11.3"] },
    { "id": 9, "tasks": ["8.4", "8.5", "9.2", "9.3", "10.5", "11.4", "11.5"] },
    { "id": 10, "tasks": ["8.6", "8.7", "9.4", "9.5", "10.6", "10.7", "11.6", "12.1"] },
    { "id": 11, "tasks": ["9.6", "10.8", "10.9", "12.2", "12.3"] },
    { "id": 12, "tasks": ["14.1", "14.2"] },
    { "id": 13, "tasks": ["14.3", "14.4", "14.5", "14.6"] },
    { "id": 14, "tasks": ["15.1", "17.1", "17.3"] },
    { "id": 15, "tasks": ["16.1", "17.2"] },
    { "id": 16, "tasks": ["16.2"] },
    { "id": 17, "tasks": ["30.1"] },
    { "id": 18, "tasks": ["30.2"] },
    { "id": 19, "tasks": ["32.1"] },
    { "id": 20, "tasks": ["32.2"] },
    { "id": 21, "tasks": ["32.3"] }
  ]
}
```
