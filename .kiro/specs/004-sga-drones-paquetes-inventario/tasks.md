# Implementation Plan: 004-sga-drones-paquetes-inventario

## Overview

Este plan convierte el diseño aprobado de `004-sga-drones-paquetes-inventario` en tareas pequeñas e incrementales: registro en steering de la lectura de contratos del CRM; migración y persistencia del SGA; máquinas de estado del paquete y del dron, sincronización dron–paquete, asociación con clientes y contratos del CRM y aritmética del inventario; API REST de drones, paquetes con su historial, inventario e indicadores; pruebas unitarias, de seguridad y de integración con PostgreSQL efímero limitadas a las reglas de negocio; vistas SGA del frontend; verificación de la imagen Docker y de los workflows existentes; aplicación controlada de la migración; y validación extremo a extremo con Supabase real del flujo cliente → contrato → paquete → cambio de estado y trazabilidad (CA-05).

Cada tarea indica **área**, **responsable** y **requisitos** (`requirements.md` e identificadores del MVP). Todo el trabajo se confirma en la rama `feat/sga-drones-paquetes-inventario` con Conventional Commits, con alcance `sga` para el código del módulo y `crm`, `db`, `frontend`, `docs` o `ci` cuando corresponda, y se integra mediante un único Pull Request al completar la spec (DP-SDD-04, DP-SDD-05).

**Reparto de trabajo.** Cada responsable recibe capas completas de su área para reducir los cambios de contexto, y las tareas transversales se reparten para igualar la carga:

- **Davidson:** migración SGA (preparación, registro y aplicación controlada en `arise-corp-dev`); enumeraciones con las máquinas de estado del paquete y del dron y la aritmética del inventario; entidades, repositorios, especificaciones, `GeneradoresSga` y `SgaDatosPrueba`; los servicios de dominio de drones, paquetes (alta, edición, transiciones y sincronización del dron), artículos, movimientos de inventario e indicadores, con sus pruebas unitarias; registro de los ajustes de contrato en `api-conventions.md`; verificación de CI; documentación del backend y steering de arquitectura, estructura, tecnología y producto; validación manual del inventario; y verificación final de trazabilidad.
- **Sebastián:** `ContratoReferenciaService` del CRM y su registro temprano en `architecture.md`; revisión de la migración; catálogo `ConflictosSga`; DTO y restricciones de validación del SGA; los cuatro controladores; `SgaAutorizacionTests` con la retirada de `AutorizacionModulosTests` en el mismo commit que `PaqueteController` (D16); pruebas de controlador y `SgaLogsTests`; ampliación de `PostgresIntegracionBase` y las IT de migración, flujo CA-05, transacciones y concurrencia; comprobación de la imagen Docker; steering de API y seguridad; validación manual de las denegaciones; protección de `main` y PR final.
- **Paula:** rama de la spec; `sgaApi.js`, `validacionSga.js`, permisos, transiciones y menú `opcionesMenuSga`, icono `sga.svg` y tarjetas de `Panel.jsx`; `SelectorRegistro` con `textoVacio` y las etiquetas de estado; las vistas de drones, paquetes (historial y transiciones), inventario (movimientos) e indicadores; las rutas `/sga/*` de `App.jsx`; sus pruebas Vitest y React Testing Library; documentación del frontend; y validación manual del flujo CA-05 en las vistas.

**Trabajo en paralelo.** El contrato de la API está fijado en `design.md` ("Contratos de la API del SGA"), así que el frontend (grupos 19 a 28) avanza desde que existen la rama y los ajustes de steering, en paralelo con el backend, y sus pruebas simulan la API. En el backend, `ContratoReferenciaService` (grupo 3) se completa antes que `PaqueteService` (grupo 9); las enumeraciones (grupo 4) antes que las entidades, `ConflictosSga` y los DTO; las entidades y los repositorios (grupo 5) antes que los DTO de salida y los servicios; los DTO (grupo 6) antes que los servicios (grupos 8 a 10); y los servicios antes que los controladores (grupo 11). `PaqueteController` (11.4) se confirma en el mismo commit que `SgaAutorizacionTests` (11.5) y la retirada de `AutorizacionModulosTests`, así que los otros tres controladores se crean antes.

### Notas de alcance (del diseño aprobado)

- **Must:** SGA-01 a SGA-05. **Should incluido:** SGA-06 (indicadores de paquetes, drones e inventario). El flujo integrado CA-05 se cierra con esta spec.
- **Pruebas acotadas (D17):** no se añade ninguna librería. Solo se escriben las pruebas de "Testing Strategy": máquinas de estado, sincronización dron–paquete, asociación con el CRM, historial, aritmética del inventario, matriz de acceso, logs, controladores (un caso por código de conflicto, por referencia 404 y un 400 por DTO), las tres IT y, en el frontend, el servicio, las reglas y las vistas con reglas. No se repiten pruebas de normalización, paginación ni traducción genérica de errores que ya cubren `002` y `003`. Los dominios finitos se prueban de forma exhaustiva con `@ParameterizedTest` o `it.each`; los amplios usan generadores con semilla fija (`GeneradoresSga` con `SEMILLA = 20261125L` en Java y `frontend/src/test/generadores.js` sin cambios) con al menos 100 casos más los bordes conocidos. Cada prueba de propiedad se titula `Feature: 004-sga-drones-paquetes-inventario, Property {n}: {texto}`.
- **Sin dependencias ni variables de entorno nuevas (D22):** `pom.xml`, `package.json`, `backend/.env.example`, `frontend/.env.example`, `backend/Dockerfile`, `backend/.dockerignore`, `docker-compose.yml`, los tres workflows y `SecurityConfig` no cambian (Requirements 18.1, 28.4, 28.8, 29.6). Solo se añade el icono estático `frontend/public/assets/sga.svg`.
- **Migración (D21, DP-TEC-03):** la prepara Davidson, la revisa Sebastián en la rama, `MigracionesSgaIT` la valida en PostgreSQL efímero y Davidson la aplica de forma controlada en `arise-corp-dev` antes de la validación manual y del PR final. Una vez aplicada no se edita. Mientras no esté aplicada, el backend de la rama no arranca contra `arise-corp-dev` (`ddl-auto=validate`); las suites automáticas no se ven afectadas.
- `.\mvnw.cmd test` no necesita Docker; `.\mvnw.cmd verify` sí, porque ejecuta Testcontainers. Failsafe recoge las IT nuevas con la inclusión existente `**/integration/**/*IT.java`.

### Distribución

Recuento de subtareas (los checkpoints son tareas de primer nivel y se cuentan aparte). Las columnas de área suman el total de subtareas de cada integrante.

| Integrante | Subtareas | Obligatorias | Opcionales (`*`) | Base de datos | Backend | Frontend | Docker/CI | Documentación | Integración/Git/E2E | Checkpoints |
|---|---|---|---|---|---|---|---|---|---|---|
| Davidson | 29 | 29 | 0 | 3 | 20 | 0 | 1 | 3 | 2 | 2 |
| Sebastián | 29 | 28 | 1 | 1 | 22 | 0 | 2 | 2 | 2 | 2 |
| Paula | 29 | 29 | 0 | 0 | 0 | 26 | 0 | 1 | 2 | 2 |
| **Total** | **87** | **86** | **1** | **4** | **42** | **26** | **3** | **6** | **6** | **6** |

El total es igual para los tres y la diferencia en las obligatorias es de una subtarea (≈3 %). En esfuerzo, Davidson asume la persistencia y los servicios con más reglas (transiciones del paquete con bloqueos y sincronización del dron, aritmética del inventario); Sebastián, la lectura de contratos del CRM, los DTO, los controladores con sus pruebas, la matriz de autorización y las IT con concurrencia; y Paula, diez páginas con sus pruebas. Las tareas de Git, steering, documentación, Docker, CI, checkpoints, validación extremo a extremo y entrega se reparten para compensar: Davidson registra los ajustes de `api-conventions.md`, aplica la migración y verifica CI; Sebastián verifica la imagen Docker, la protección de `main` y abre el PR; Paula crea la rama y documenta el frontend.

## Tasks

- [x] 1. Preparar la rama y las reglas de la spec
  - [x] 1.1 Crear la rama `feat/sga-drones-paquetes-inventario` desde `main`
    - Área: integración/Git · Responsable: Paula
    - Actualizar `main` (`git switch main` y `git pull`), crear la rama con `git switch -c feat/sga-drones-paquetes-inventario` y publicarla con `git push -u origin feat/sga-drones-paquetes-inventario`
    - Confirmar en la rama `requirements.md`, `design.md` y `tasks.md` de `.kiro/specs/004-sga-drones-paquetes-inventario/` con `docs(sga): agregar spec 004-sga-drones-paquetes-inventario`. `.config.kiro` es metadata local excluida por `.gitignore` y no se versiona
    - Comprobar con `git status` que ningún archivo `.env` queda preparado para commit
    - _Requisitos: 29.7, DP-SDD-03, DP-SDD-04, DP-SDD-05, NF-01_

  - [x] 1.2 Registrar `ContratoReferenciaService` en `architecture.md` (DP-ARQ-01)
    - Área: documentación · Responsable: Sebastián
    - DP-ARQ-01: los módulos consumidores leen contratos mediante `crm.service.ContratoReferenciaService`, que expone solo identificador, cliente, código y estado (solo lectura, en lote y con bloqueo de lectura dentro de la transacción del llamador), junto a `ClienteReferenciaService`. El SGA define sus propios DTO de salida y nunca accede a `Contrato`, `EstadoContrato` ni `ContratoRepository` (D2, D3)
    - Se hace antes de la tarea 3.1 (`sdd-workflow.md`, "Registro de decisiones"; Requirement 29.2) y se confirma con `docs(crm): registrar ContratoReferenciaService en DP-ARQ-01`. El estado [Implementado] del SGA se actualiza en las tareas 17.2 y 28.1
    - _Requisitos: 1.5, 1.6, 29.2, NF-05, DP-ARQ-01_

  - [x] 1.3 Registrar en `api-conventions.md` los ajustes de contrato que adopta el diseño
    - Área: documentación · Responsable: Davidson
    - DP-API-04: las cantidades enteras se reciben como número JSON y se validan como enteros sin truncar (`BigDecimal` con `@CantidadEntera` en el DTO), porque Jackson 3 trunca decimales en campos `int` por defecto (D5)
    - DP-API-05: `POST .../estados` y `POST .../movimientos` responden 201 con el registro creado y el encabezado `Location` de la colección donde se consulta; el historial del paquete se devuelve completo como arreglo JSON, sin paginación, como excepción a DP-API-03 (interpretación 12)
    - Se hace antes del código que aplica estas reglas y se confirma con `docs(sga): registrar en steering los ajustes de la spec 004`. Los endpoints y los códigos de conflicto del SGA se añaden en la tarea 17.3
    - _Requisitos: 12.1, 15.1, 20.2, NF-05, DP-API-04, DP-API-05_

- [x] 2. Crear y revisar la migración del SGA
  - [x] 2.1 Crear `supabase/migrations/<timestamp>_app_sga_drones_paquetes_inventario.sql`
    - Área: base de datos · Responsable: Davidson
    - Generar el archivo con `supabase migration new app_sga_drones_paquetes_inventario`; el timestamp debe ser posterior a `20261118095500`. Si Supabase CLI no está disponible, crear el archivo con el mismo formato `<timestamp>_<nombre>.sql`, como en `003`. No modificar las migraciones existentes
    - Escribir el SQL de "Migración SGA" del diseño: `app.drones`, `app.paquetes`, `app.historial_estados_paquete`, `app.articulos_inventario` y `app.movimientos_inventario` en orden de dependencia, con `create table` sin `if not exists`; PK UUID; `UNIQUE` de los tres códigos y de `(paquete_id, secuencia)`; `CHECK` de formato de código `^[A-Z0-9-]{3,30}$`, longitudes, estados, categorías, unidades, `stock_minimo`, existencia no negativa, coherencia de `dron_id` con el estado del paquete (`paquetes_dron_estado_ck`), alta y dron del historial, rangos de cantidad por tipo, aritmética de cada tipo y observación del `AJUSTE`
    - FK `cliente_id → app.clientes`, `contrato_id → app.contratos`, `dron_id → app.drones` (paquetes e historial), `paquete_id → app.paquetes` y `articulo_id → app.articulos_inventario`; índice único parcial `paquetes_dron_en_curso_uk` sobre `dron_id` en `ASIGNADO` y `EN_TRANSITO`; índices de `cliente_id`, `contrato_id`, `dron_id` y `(articulo_id, registrado_en)`; `secuencia smallint` (D11); `existencia`, `existencia_anterior` y `existencia_resultante` como `bigint` (D5); auditoría `timestamptz` y `uuid not null` sin FK a `auth.users`; comentarios de tabla
    - Habilitar RLS en las cinco tablas y ejecutar `revoke all ... from anon, authenticated`. Sin `insert`, UUID personales, secretos ni sentencias que no admitan transacción
    - Anotar en este `tasks.md` el nombre real del archivo
    - Archivo real: `20261121120000_app_sga_drones_paquetes_inventario.sql`
    - _Requisitos: 2.1–2.13, SGA-01, SGA-02, SGA-03, SGA-04, SGA-05, NF-01, NF-03, NF-05, DP-SEG-05, DP-SEG-06, DP-API-04_

  - [x] 2.2 Registrar la migración en `supabase/migrations/README.md`
    - Área: base de datos · Responsable: Davidson
    - Añadir la fila de la Migracion_SGA a la tabla "Migraciones" con su nombre real, la descripción y `004-sga-drones-paquetes-inventario`. Las reglas de revisión y aplicación de DP-TEC-03 ya están en el archivo y no cambian
    - _Requisitos: 29.5, NF-05, DP-TEC-03_

  - [x] 2.3 Revisar la migración en la rama antes de aplicarla
    - Área: base de datos · Responsable: Sebastián
    - Contrastar el SQL con "Migración SGA" y "Entidades JPA (`sga.entity`)" del diseño: columnas, tipos, `CHECK`, `UNIQUE`, FK, índice único parcial, índices, comentarios, RLS, `revoke` y ausencia de datos. Los cambios que pida la revisión se hacen en el mismo archivo, porque todavía no está aplicado
    - No la apliques tú: la aplicación controlada es la tarea 15.1 y la realiza Davidson, autor de la migración, después de `MigracionesSgaIT` (14.2) y de esta revisión
    - _Requisitos: 2.1–2.13, 29.8, DP-TEC-03, DP-SEG-05_

- [x] 3. Ofrecer la lectura de contratos del CRM a otros módulos
  - [x] 3.1 Crear `crm.dto.ContratoReferencia`, `ContratoRepository.findParaAsociarById` y `crm.service.ContratoReferenciaService`
    - Área: backend · Responsable: Sebastián
    - `record ContratoReferencia(UUID id, UUID clienteId, String codigo, String estado)` con `static desde(Contrato)` (`estado = contrato.getEstado().name()` y `clienteId` tomado del proxy `LAZY` sin inicializarlo) y `boolean activo()`, que compara con `EstadoContrato.ACTIVO.name()` (D2)
    - `ContratoRepository.findParaAsociarById(UUID)` con `@Lock(LockModeType.PESSIMISTIC_READ)` y sin *fetch join*, como `findParaContratoById` de clientes
    - `@Service @Transactional(readOnly = true) ContratoReferenciaService` con `Optional<ContratoReferencia> buscar(UUID)`, `Map<UUID, ContratoReferencia> buscarTodos(Collection<UUID>)` (una sola consulta con `findAllById`; una colección vacía devuelve un mapa vacío sin consultar) y `@Transactional(propagation = Propagation.MANDATORY) Optional<ContratoReferencia> buscarParaAsociar(UUID)`
    - `ContratoService`, los controladores y los endpoints CRM no cambian; el CRM no importa nada de `sga` ni de `erp`
    - _Requisitos: 1.5, 1.6, 1.8, 7.12, 8.8, DP-ARQ-01, SGA-05_

  - [x] 3.2 Escribir `crm/service/ContratoReferenciaServiceTests`
    - Área: backend · Responsable: Sebastián
    - `@SpringBootTest` con H2 y `CrmDatosPrueba`: `buscar` con contrato existente (`clienteId` y `codigo` correctos) e inexistente; `activo()` en los cuatro Estado_Contrato; `buscarTodos` con colección vacía (sin consulta), ids mezclados, repetidos e inexistentes; `buscarParaAsociar` dentro de un `TransactionTemplate`; fuera de transacción lanza `IllegalTransactionStateException`
    - _Requisitos: 1.6, 7.12, NF-07_

- [x] 4. Implementar las enumeraciones y las máquinas de estado del SGA
  - [x] 4.1 Crear las enumeraciones de `sga.entity`
    - Área: backend · Responsable: Davidson
    - `EstadoPaquete` con `EN_CURSO`, `transicionesPermitidas()`, `puedeTransicionarA`, `esFinal`, `esEditable` y `efectoSobreDron(destino)`; `EstadoDron` con `transicionesManuales()` y `puedeCambiarManualmenteA`; `TipoMovimientoInventario` con `existenciaResultante(long anterior, int cantidad)` y `cantidadMinima()`; `CategoriaArticulo { REPUESTO, BATERIA, EMPAQUE, OTRO }` y `UnidadArticulo { UNIDAD, CAJA, JUEGO, ROLLO }`, según el código del diseño (D7, D8, D12)
    - No se reutilizan enumeraciones del CRM ni del ERP
    - _Requisitos: 6.1, 6.2, 9.3, 10.2, 11.7, 11.9, 11.10, 13.4, 15.2–15.4, DP-API-04, DP-API-05_

  - [x] 4.2 Escribir `sga/entity/EstadoPaqueteTests`
    - Área: backend · Responsable: Davidson
    - `@ParameterizedTest` exhaustivo sobre los 36 pares de origen y destino, `esFinal`, `esEditable` y `EN_CURSO`; `efectoSobreDron` para los 36 pares: solo `ASIGNADO→EN_TRANSITO` devuelve `EN_MISION` y solo `EN_TRANSITO→ENTREGADO` y `EN_TRANSITO→DEVUELTO` devuelven `DISPONIBLE`
    - **Property 1: Solo se aplican las transiciones permitidas del paquete** (nivel enumeración) · **Validates: Requirements 10.2, 25.2**
    - **Property 4: El estado del dron sigue al de su paquete** (`efectoSobreDron`) · **Validates: Requirements 11.7, 11.9, 11.10**
    - _Requisitos: 25.2, NF-07_

  - [x] 4.3 Escribir `sga/entity/EstadoDronTests`
    - Área: backend · Responsable: Davidson
    - `@ParameterizedTest` exhaustivo sobre los 16 pares: `EN_MISION` no tiene transiciones manuales ni es destino manual y el mismo estado no es una transición
    - **Property 2: El dron solo cambia a mano entre sus estados manuales y sin paquete en curso** (nivel enumeración) · **Validates: Requirements 6.1, 6.2, 25.2**
    - _Requisitos: 25.2, NF-07_

- [x] 5. Implementar las entidades y los repositorios JPA del SGA
  - [x] 5.1 Implementar la entidad `Dron`
    - Área: backend · Responsable: Davidson
    - Mapeo según "Entidades JPA (`sga.entity`)": `@Table(name = "drones")` sin esquema con `uniqueConstraints` `drones_codigo_uk`, `@Id @GeneratedValue(strategy = GenerationType.UUID)`, `@Enumerated(EnumType.STRING)`, `creadoEn` con `updatable = false` y auditoría de datos y de estado
    - Sin setters públicos: `static crear(codigo, modelo, observaciones, UUID actor, Instant ahora)` en `DISPONIBLE` con la auditoría de datos y de estado iguales; `actualizarDatos(...)` solo cambia los datos y `actualizado_*`; `cambiarEstadoManual(destino, actor, ahora)` con defensa `IllegalStateException` si `!puedeCambiarManualmenteA(destino)`; `sincronizarConPaquete(destino, actor, ahora)` con defensa que solo admite `DISPONIBLE→EN_MISION` y `EN_MISION→DISPONIBLE`. Los cambios de estado solo tocan `estado` y su auditoría (D8, D14)
    - _Requisitos: 1.1, 2.2, 2.14, 4.1, 4.8, 6.1, 6.5, 11.7, 11.9, DP-SEG-06_

  - [x] 5.2 Implementar las entidades `Paquete` y `HistorialEstadoPaquete`
    - Área: backend · Responsable: Davidson
    - `Paquete`: `codigo` y `clienteId` con `updatable = false`; `clienteId` y `contratoId` como `UUID` sin asociación JPA (D1, DP-ARQ-01); `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "dron_id") Dron dron` sin colecciones inversas; `static crear(...)` en `REGISTRADO` sin dron con la auditoría de datos y de estado iguales; `actualizarDatos(descripcion, direccion, contratoId, actor, ahora)` con defensa `esEditable`
    - `Paquete.aplicarTransicion(destino, dron, actor, ahora)` con los cinco pasos del diseño: `IllegalStateException` si la transición no está permitida; con `ASIGNADO`, `IllegalArgumentException` sin dron y asignación del dron; con otro destino, `IllegalArgumentException` si el dron recibido no es el asignado; actualización solo de `estado` y su auditoría; y llamada a `dron.sincronizarConPaquete` con el mismo instante y actor cuando `efectoSobreDron(destino)` tiene valor
    - `HistorialEstadoPaquete` con `@Immutable`, `@ManyToOne(fetch = LAZY)` a `Paquete` y a `Dron` (opcional), `secuencia`, `static alta(paquete, observacion, actor, ahora)` (secuencia 1, anterior `null`, nuevo `REGISTRADO`) y `static transicion(paquete, secuencia, anterior, nuevo, dron, observacion, actor, ahora)`
    - _Requisitos: 1.7, 2.3, 2.4, 2.6, 2.14, 7.1, 9.1, 9.4, 10.1, 10.7, 11.6, 11.11, 12.2, DP-ARQ-01, DP-SEG-06_

  - [x] 5.3 Implementar las entidades `ArticuloInventario` y `MovimientoInventario`
    - Área: backend · Responsable: Davidson
    - `ArticuloInventario`: `stockMinimo` `int` y `existencia` `long`; `static crear(...)` con existencia 0; `actualizarDatos(...)` sin recibir la existencia; `long aplicarMovimiento(tipo, cantidad)` con defensa de resultado ≥ 0 y sin tocar `actualizado_*`; `boolean bajoStockMinimo()` con `existencia < stockMinimo`
    - `MovimientoInventario` con `@Immutable`, `@ManyToOne(fetch = LAZY)` a `ArticuloInventario` y `static registrar(articulo, tipo, cantidad, anterior, resultante, observacion, actor, ahora)`
    - _Requisitos: 2.7, 2.8, 2.14, 13.1, 13.8, 14.6, 15.1–15.4, 15.12, 15.15, DP-SEG-06_

  - [x] 5.4 Crear los repositorios del SGA
    - Área: backend · Responsable: Davidson
    - Firmas del diseño: `DronRepository` (`findByCodigo`, `findParaModificarById` con `PESSIMISTIC_WRITE` y `contarPorEstado`), `PaqueteRepository` (`findByCodigo`, `findAll(Specification, Pageable)` con `@EntityGraph("dron")`, `findConDronById`, `findParaModificarById`, `findFirstByDronIdAndEstadoIn`, `findByDronIdInAndEstadoIn` y `contarPorEstado`), `HistorialEstadoPaqueteRepository` (`findByPaqueteIdOrderBySecuenciaAsc` con `@EntityGraph("dron")` y `countByPaqueteId`), `ArticuloInventarioRepository` (`findByCodigo`, `findParaModificarById` y `contarArticulos`) y `MovimientoInventarioRepository` (`findByArticuloId` paginado)
    - Proyecciones `ConteoPorEstado<E>` (`getClave()`, `getTotal()`) y `ConteoArticulos` (`getTotal()`, `getBajoStockMinimo()`) en `sga.repository`; no se reutiliza `crm.repository.ConteoPorClave`. Las consultas con bloqueo no hacen *fetch join* (D10)
    - _Requisitos: 1.5, 5.7, 8.1, 11.12, 12.1, 15.10, 15.11, 16.1, 16.3, 16.4_

  - [x] 5.5 Crear `sga.repository.EspecificacionesSga`
    - Área: backend · Responsable: Davidson
    - `codigoOTextoContiene(q, campoTexto)` genérica para `modelo`, `descripcion` o `nombre`; `estadoIgual`, `categoriaIgual`, `clienteIgual`, `contratoIgual` y `dronIgual` (`dron.id`, sin *join*); `asignable(Boolean)` con `estado = DISPONIBLE` y `not exists` de un paquete del dron en `EN_CURSO`, y su negación con `false`; `bajoStockMinimo(Boolean)` con `cb.lt(existencia, stockMinimo)` y su negación
    - JPQL portable con `LIKE ... ESCAPE '\'`, `NormalizadorTexto.codigo` y `NormalizadorTexto.patronContiene`; un filtro ausente devuelve `Specification.unrestricted()`
    - _Requisitos: 5.3–5.5, 8.2–8.5, 14.2–14.4, SGA-01, SGA-02, SGA-03, SGA-05_

  - [x] 5.6 Crear `GeneradoresSga` en el paquete de prueba `com.arise.corp.sga`
    - Área: backend · Responsable: Davidson
    - `SEMILLA = 20261125L` y `nuevoRandom()`; generadores de secuencias de movimientos (tipos, cantidades válidas y salidas que a veces superan la existencia), de valores numéricos de cantidad (enteros, decimales con parte fraccionaria, negativos, notación exponencial y los bordes 0, 1, 1000000 y 1000001) y de conteos por estado de paquetes y drones con estados ausentes (D17)
    - Solo datos ficticios
    - _Requisitos: 25.3, NF-07_

  - [x] 5.7 Crear `SgaDatosPrueba` en el paquete de prueba `com.arise.corp.sga`
    - Área: backend · Responsable: Davidson
    - Componente de prueba que se importa con `@Import({CrmDatosPrueba.class, SgaDatosPrueba.class})`: crea drones, paquetes en cualquier estado (aplicando `aplicarTransicion` de la entidad y creando su historial), artículos y movimientos ficticios; su `limpiar()` borra movimientos, artículos, historial, paquetes y drones y después llama a `CrmDatosPrueba.limpiar()`. Los perfiles, clientes y contratos se crean con `CrmDatosPrueba`; la autenticación usa `AutenticacionPerfilTestSupport.como(...)` con el `PerfilJwtAuthenticationConverter` real
    - _Requisitos: 25.4, 25.5, NF-07_

- [x] 6. Implementar el catálogo de conflictos y los DTO del SGA
  - [x] 6.1 Crear `sga.service.ConflictosSga`
    - Área: backend · Responsable: Sebastián
    - Clase `final` con una fábrica de `ConflictoNegocioException` por fila del catálogo de "Error Handling": `dronCodigoDuplicado()`, `paqueteCodigoDuplicado()`, `articuloCodigoDuplicado()`, `clienteInactivo()`, `contratoDeOtroCliente()`, `contratoNoActivo(String estado)`, `paqueteNoEditable(EstadoPaquete)`, `transicionNoPermitida(EstadoPaquete, EstadoPaquete)`, `dronNoDisponible(EstadoDron, String campo)`, `dronConPaqueteEnCurso(UUID paqueteId, String campo)`, `transicionDronNoPermitida(EstadoDron, EstadoDron)` y `existenciaInsuficiente(long existenciaActual, int cantidadSolicitada)`
    - Cada fábrica fija el código, `campo` (interpretación 5: `TRANSICION_DRON_NO_PERMITIDA` con `campo = "estado"`; `DRON_NO_DISPONIBLE` y `DRON_CON_PAQUETE_EN_CURSO` con el `campo` recibido, `"dronId"` al asignar y `null` en `EN_TRANSITO` y en el cambio manual), el `detail` del catálogo y las extensiones `estadoActual`, `estadoSolicitado`, `paqueteId`, `existenciaActual` y `cantidadSolicitada`, sin direcciones, observaciones ni nombres. Constantes propias, también `TRANSICION_NO_PERMITIDA`, sin importar las del ERP ni del CRM
    - _Requisitos: 17.4, 19.4, 19.5, CA-06, DP-API-02_

  - [x] 6.2 Crear `@CantidadEntera` en `sga.dto.validacion`
    - Área: backend · Responsable: Sebastián
    - Anotación con su validador sobre `BigDecimal`, con `min` y `max = 1_000_000`; acepta `null`; informa "debe ser un número entero entre {min} y {max}" si `valor.stripTrailingZeros().scale() > 0` o el valor está fuera de rango; expone el predicado estático `esEnteroEnRango(BigDecimal, long min, long max)` (D5)
    - _Requisitos: 13.5, 15.7, DP-API-04_

  - [x] 6.3 Crear los DTO de drones
    - Área: backend · Responsable: Sebastián
    - `DronRequest` (POST y PUT) con `@JsonIgnoreProperties(ignoreUnknown = true)`, normalización en el constructor compacto (`NormalizadorTexto.codigo` y `NormalizadorTexto.texto`), `@NotNull` "es obligatorio" separado del formato, `@Pattern` `[A-Z0-9-]{3,30}` con el mensaje de código común, `@Size` de `modelo` (100) y `observaciones` (500) y `toString()` "DronRequest[datos protegidos]"
    - `EstadoDronRequest` con `estado` obligatorio; `PaqueteEnCursoRef(UUID id, String codigo, EstadoPaquete estado)` con `static desde(Paquete)` que devuelve `null` con entrada `null`; `DronResponse` con los 11 campos de Requirement 5.7, `static desde(Dron, Paquete enCurso)` y `toString()` protegido
    - _Requisitos: 3.1–3.4, 4.2–4.5, 4.7, 4.9, 5.7, 6.4, 17.1, 17.3, SGA-01_

  - [x] 6.4 Crear los DTO de paquetes e historial y `@DronSegunEstado`
    - Área: backend · Responsable: Sebastián
    - `PaqueteCrearRequest`, `PaqueteActualizarRequest` (sin `codigo`, `clienteId`, `estado` ni `dronId`) y `TransicionPaqueteRequest` como el record del diseño, con normalización en el constructor compacto, mensajes en español y `toString()` protegido (D15)
    - Restricción de clase `@DronSegunEstado` en `sga.dto.validacion`: no informa nada si `estado` es `null`; con `ASIGNADO` y sin `dronId` informa "es obligatorio para asignar el paquete" y con otro estado y `dronId` informa "solo se admite en la transición a ASIGNADO", ambos sobre el nodo `dronId` (D6)
    - `ClienteAsociado.desde(ClienteReferencia)`, `ContratoAsociado.desde(ContratoReferencia)` y `DronAsociado.desde(Dron)` (estas dos devuelven `null` con entrada `null`), `DronRef(id, codigo)`, `PaqueteResponse.desde(Paquete, ClienteReferencia, ContratoReferencia)` con `Objects.requireNonNull` sobre las referencias cuando el paquete tiene `clienteId` o `contratoId` (interpretación 11) y `RegistroHistorialResponse.desde(HistorialEstadoPaquete)`; `toString()` protegido en `PaqueteResponse` y `RegistroHistorialResponse`
    - _Requisitos: 3.1, 3.4, 7.2–7.4, 7.11, 8.7, 8.8, 9.2, 9.4, 10.3, 10.4, 11.1, 11.2, 12.1, 17.1–17.3, SGA-02, SGA-04, SGA-05_

  - [x] 6.5 Crear los DTO de inventario y del resumen
    - Área: backend · Responsable: Sebastián
    - `ArticuloRequest` (POST y PUT) con `stockMinimo` `BigDecimal` y `@CantidadEntera(min = 0)`, `categoria` y `unidad` obligatorias; `ArticuloResponse` con los 11 campos de Requirement 14.6 y `bajoStockMinimo` desde la entidad
    - `MovimientoInventarioRequest` como el record del diseño, con `cantidadEntera()` y la restricción de clase `@MovimientoInventarioValido` (en `sga.dto.validacion`): rango de `cantidad` con `tipo.cantidadMinima()` (0 si `tipo` es `null`) sobre el nodo `cantidad` y "es obligatoria en un ajuste" sobre `observacion`, con un error por campo; `MovimientoInventarioResponse`; `toString()` protegido en ambos
    - `ResumenSgaResponse(ConteoEstados<EstadoPaquete> paquetes, ConteoEstados<EstadoDron> drones, ResumenArticulos articulos)` con `ConteoEstados(Map<E, Long> porEstado, long total)` y `ResumenArticulos(long bajoStockMinimo, long total)`; función pura `static desde(...)` que crea un `EnumMap` con todos los estados en 0, copia las filas y calcula cada `total` como la suma de sus cantidades (D13)
    - _Requisitos: 13.2–13.5, 13.7, 14.6, 15.1, 15.6–15.8, 16.1–16.4, 17.3, SGA-03, SGA-06, DP-API-04_

  - [x] 6.6 Escribir `sga/dto/SgaValidacionTests`
    - Área: backend · Responsable: Sebastián
    - `jakarta.validation.Validator` y `GeneradoresSga`: `stockMinimo` y `cantidad` con valores generados y bordes (`1.5` rechazado sin truncar; `5`, `5.0` y `5E0` aceptados como 5), una violación por campo, mensajes en español y `AJUSTE` sin observación con el error en `observacion`; `@DronSegunEstado` en los 6 estados × `dronId` presente o ausente
    - **Property 8: Las cantidades se aceptan solo como enteros dentro del rango de su tipo** (backend) · **Validates: Requirements 13.5, 15.6, 15.7, 15.8**
    - **Property 3: Solo se asigna un dron asignable** (parte del DTO) · **Validates: Requirements 11.1, 11.2**
    - _Requisitos: 11.1, 11.2, 13.5, 15.7, 15.8, NF-07_

  - [x]* 6.7 Escribir `sga/dto/ResumenSgaResponseTests`
    - Área: backend · Responsable: Sebastián
    - Conteos generados con `GeneradoresSga` (con estados ausentes) comparados con el cálculo directo; caso sin filas
    - **Property 9: Los indicadores son consistentes con los datos** (función pura) · **Validates: Requirements 16.1, 16.2, 16.3, 16.4**
    - _Requisitos: 16.2, 16.3, NF-07, SGA-06_

- [x] 7. Checkpoint de persistencia y DTO
  - Área: verificación · Responsable: Davidson
  - Ejecutar `.\mvnw.cmd clean test` desde `backend/`: el contexto arranca con las entidades del SGA en H2, las pruebas nuevas de enumeraciones, `ContratoReferenciaServiceTests` y DTO pasan y las pruebas existentes de `001-foundation`, `002-crm-clientes-contratos` y `003-erp-empleados-proveedores-finanzas` siguen en verde. Asegurar que todas las pruebas pasan; consultar a la usuaria si surgen dudas.

- [x] 8. Implementar el servicio de drones
  - [x] 8.1 Implementar `FiltroDrones` y `DronService`
    - Área: backend · Responsable: Davidson
    - Record `sga.service.FiltroDrones(String q, EstadoDron estado, Boolean asignable)`
    - `listar` con `EspecificacionesSga` y una sola consulta `findByDronIdInAndEstadoIn(ids de la página, EN_CURSO)` para el `paqueteEnCurso` (interpretación 4); `obtener` (404 "El dron no existe." y `findFirstByDronIdAndEstadoIn`); `crear` (`DRON_CODIGO_DUPLICADO`; `Dron.crear` en `DISPONIBLE`); `actualizar` (`findParaModificarById`, 404, código de otro dron → `DRON_CODIGO_DUPLICADO`, conserva el estado, su auditoría y `creadoEn`)
    - `cambiarEstado` en el orden de D9: `findParaModificarById` (404) → `TRANSICION_DRON_NO_PERMITIDA` → `DRON_CON_PAQUETE_EN_CURSO` con `paqueteId` y sin `campo` → `cambiarEstadoManual`
    - `@Transactional`, `Clock` con `Instant.now(clock).truncatedTo(ChronoUnit.MICROS)`, actor con `UUID.fromString(sub)` y conversión a DTO dentro de la transacción. No registrar observaciones en logs
    - _Requisitos: 1.3, 4.1, 4.6, 4.8, 4.10, 5.1, 5.3–5.5, 5.7, 5.8, 6.1–6.3, 6.5, 6.6, 18.6, SGA-01, SGA-05, DP-SEG-06_

  - [x] 8.2 Escribir `sga/service/DronServiceTests`
    - Área: backend · Responsable: Davidson
    - Mockito y `Clock.fixed`: creación con la auditoría de datos y de estado iguales y el `sub` del actor; duplicado de código en el alta y en la edición; la edición conserva el estado y su auditoría
    - **Property 2: El dron solo cambia a mano entre sus estados manuales y sin paquete en curso** (nivel servicio: 16 pares × con y sin paquete en curso; en los rechazos el dron no cambia) · **Validates: Requirements 6.1, 6.2, 6.3, 6.5**
    - _Requisitos: 25.1, 25.2, NF-07, SGA-01_

- [x] 9. Implementar el servicio de paquetes
  - [x] 9.1 Implementar `FiltroPaquetes` y las consultas, el alta y la edición de `PaqueteService`
    - Área: backend · Responsable: Davidson
    - Record `sga.service.FiltroPaquetes(EstadoPaquete estado, UUID clienteId, UUID contratoId, UUID dronId, String q)`
    - `listar` con `Specification.allOf`, `findAll` con `@EntityGraph("dron")` y una llamada a `clientesCrm.buscarTodos` y otra a `contratosCrm.buscarTodos` con los identificadores de la página (D3); un identificador sin paquetes o sin registro produce una lista vacía. `obtener` con `findConDronById` (404 "El paquete no existe."), `clientesCrm.buscar` y, si hay contrato, `contratosCrm.buscar`. `historial` con `existsById` (404) y `findByPaqueteIdOrderBySecuenciaAsc`
    - `crear` en el orden de D9: `clientesCrm.buscarParaAsociar` (404 "El cliente indicado no existe." con `campo = "clienteId"`) → `contratosCrm.buscarParaAsociar` si hay contrato (404 con `campo = "contratoId"`) → `CLIENTE_INACTIVO` → `CONTRATO_DE_OTRO_CLIENTE` → `CONTRATO_NO_ACTIVO` con `estadoActual` → `PAQUETE_CODIGO_DUPLICADO` → `save(Paquete.crear(...))` y `save(HistorialEstadoPaquete.alta(...))` en la misma transacción, con la observación del alta en el primer registro (interpretación 10)
    - `actualizar`: `findParaModificarById` (404) → contrato destino (`null`; el actual con `contratosCrm.buscar` sin comprobar su estado; o uno nuevo con `buscarParaAsociar`, 404 con `campo`) → `PAQUETE_NO_EDITABLE` con `estadoActual` → `CONTRATO_DE_OTRO_CLIENTE` o `CONTRATO_NO_ACTIVO` solo para un contrato nuevo → `actualizarDatos`
    - Mismo patrón transaccional, de reloj y de auditoría que `DronService`. No usa `crm.entity` ni `crm.repository` y no registra direcciones, observaciones ni nombres de clientes en logs
    - _Requisitos: 1.3, 1.5, 7.1, 7.5–7.10, 7.12, 8.1–8.5, 8.7–8.9, 9.1, 9.3–9.8, 12.1, 12.7, 17.3, DP-ARQ-01, DP-SEG-06, SGA-02, SGA-04, SGA-05_

  - [x] 9.2 Implementar `PaqueteService.cambiarEstado`
    - Área: backend · Responsable: Davidson
    - Según el fragmento del diseño: `findParaModificarById` del paquete (404) → `findParaModificarById` del dron (el de `dronId` en `ASIGNADO`, con 404 "El dron indicado no existe." y `campo = "dronId"`; el asignado cuando `efectoSobreDron` tiene valor) → `TRANSICION_NO_PERMITIDA` → `DRON_NO_DISPONIBLE` (`campo = "dronId"` al asignar; sin campo en `EN_TRANSITO`) → `DRON_CON_PAQUETE_EN_CURSO` con `paqueteId` → `secuencia = countByPaqueteId + 1` → `paquete.aplicarTransicion(...)` → `save(HistorialEstadoPaquete.transicion(...))` con el dron solo en `ASIGNADO`
    - Bloqueo en el orden paquete → dron (D10); no tiene en cuenta el estado del cliente ni del contrato (Requirement 10.6); no toca los datos del paquete ni `actualizado_*`
    - _Requisitos: 10.1–10.8, 11.3–11.12, 12.2–12.5, 18.6, DP-API-05, DP-SEG-06, SGA-01, SGA-04, SGA-05_

  - [x] 9.3 Escribir `sga/service/PaqueteServiceTests` del alta y la edición
    - Área: backend · Responsable: Davidson
    - Mockito y `Clock.fixed` con `ClienteReferenciaService` y `ContratoReferenciaService` simulados: el alta crea el paquete en `REGISTRADO` sin dron y su primer registro (secuencia 1, anterior nulo y la observación del alta) con el `sub` del actor; orden de D9 (cliente inactivo con código duplicado → `CLIENTE_INACTIVO`; `PUT` sobre un paquete `EN_TRANSITO` con un contrato inexistente → 404 en `contratoId`); en los rechazos no se llama a `save`
    - **Property 6: Solo se asocian clientes activos y contratos activos del mismo cliente** (todas las combinaciones del alta y del `PUT`) · **Validates: Requirements 7.6, 7.7, 7.8, 7.9, 7.10, 9.5, 9.6, 9.7**
    - **Property 1: Solo se aplican las transiciones permitidas del paquete** (edición solo en `REGISTRADO` y `ASIGNADO`) · **Validates: Requirements 9.1, 9.3**
    - _Requisitos: 25.1, NF-07, SGA-02, SGA-05_

  - [x] 9.4 Ampliar `sga/service/PaqueteServiceTests` con las transiciones
    - Área: backend · Responsable: Davidson
    - Entidades reales con repositorios simulados que guardan en memoria: todos los prefijos de los recorridos `R→C`, `R→A→C`, `R→A→T→E` y `R→A→T→D`, intercalados con solicitudes rechazadas; en los rechazos no se llama a `historial.save` ni cambia el dron; orden de D9 (`ASIGNADO` con un `dronId` inexistente sobre un paquete `ENTREGADO` → 404 en `dronId`); el `sub` del actor en el paquete, el dron y el historial
    - **Property 1: Solo se aplican las transiciones permitidas del paquete** (nivel servicio, 36 pares) · **Validates: Requirements 10.1, 10.2, 10.7**
    - **Property 3: Solo se asigna un dron asignable** (4 estados del dron × con y sin otro paquete en curso) · **Validates: Requirements 11.4, 11.5, 11.6**
    - **Property 4: El estado del dron sigue al de su paquete** · **Validates: Requirements 11.6, 11.7, 11.9, 11.10, 11.11**
    - **Property 5: El historial está encadenado** · **Validates: Requirements 7.1, 12.2, 12.3, 12.4, 12.5**
    - _Requisitos: 25.1, 25.2, NF-07, SGA-04, SGA-05_

- [x] 10. Implementar los servicios de inventario y de indicadores
  - [x] 10.1 Implementar `FiltroArticulos` y `ArticuloService`
    - Área: backend · Responsable: Davidson
    - Record `sga.service.FiltroArticulos(String q, CategoriaArticulo categoria, Boolean bajoStockMinimo)`; `listar` con `EspecificacionesSga`; `obtener` (404 "El artículo no existe."); `crear` (`ARTICULO_CODIGO_DUPLICADO`; existencia 0); `actualizar` (`findParaModificarById`, 404, código de otro artículo → 409, sin tocar la existencia)
    - Mismo patrón transaccional, de reloj y de auditoría que `DronService`
    - _Requisitos: 1.3, 13.1, 13.6, 13.8, 13.9, 14.1–14.4, 14.6, 14.7, SGA-03, DP-SEG-06_

  - [x] 10.2 Implementar `MovimientoInventarioService`
    - Área: backend · Responsable: Davidson
    - `registrar`: `articulos.findParaModificarById` con `PESSIMISTIC_WRITE` (404) → `anterior = articulo.getExistencia()` → `resultante = tipo.existenciaResultante(anterior, cantidadEntera())` → `resultante < 0` → `EXISTENCIA_INSUFICIENTE` con `existenciaActual = anterior` y `cantidadSolicitada` → `articulo.aplicarMovimiento(...)` → `save(MovimientoInventario.registrar(...))`, sin tocar `actualizado_*` del artículo. Un `AJUSTE` con la misma cantidad que la existencia se registra y una `SALIDA` que deja la existencia en 0 se acepta (interpretaciones 8 y 9)
    - `listar`: `articulos.existsById` (404) y `movimientos.findByArticuloId(articuloId, pageable)`
    - _Requisitos: 15.1–15.5, 15.9–15.12, 15.14, 15.15, 18.6, SGA-03, DP-API-05, DP-SEG-06_

  - [x] 10.3 Escribir `sga/service/MovimientoInventarioServiceTests`
    - Área: backend · Responsable: Davidson
    - Mockito y `Clock.fixed` sobre una entidad real, con secuencias de `GeneradoresSga`: `sub` del actor en el movimiento; `AJUSTE` con la misma cantidad que la existencia
    - **Property 7: La existencia sigue la aritmética de los movimientos y nunca es negativa** · **Validates: Requirements 15.1, 15.2, 15.3, 15.4, 15.5, 15.15, 25.3**
    - _Requisitos: 25.1, 25.3, NF-07, SGA-03_

  - [x] 10.4 Implementar `ResumenSgaService`
    - Área: backend · Responsable: Davidson
    - `@Service @Transactional(readOnly = true)`; `obtener()` llama una vez a `paquetes.contarPorEstado()`, `drones.contarPorEstado()` y `articulos.contarArticulos()` y construye la respuesta con `ResumenSgaResponse.desde` (D13)
    - _Requisitos: 16.1, 16.3, CA-04, SGA-06_

- [x] 11. Implementar los controladores del SGA y la matriz de autorización
  - [x] 11.1 Implementar `DronController`
    - Área: backend · Responsable: Sebastián
    - `GET`, `GET /{id}`, `POST` (201 con `Location` relativa), `PUT /{id}` y `PATCH /{id}/estado` en `/api/sga/drones`; parámetros `q`, `estado`, `asignable`, `page`, `size` y `sort` (`codigo`, `modelo` o `creadoEn`; por defecto `codigo,asc`) con las restricciones en los `@RequestParam` y sin `@Validated` a nivel de clase
    - Actor tomado de `@AuthenticationPrincipal Jwt`; sin `DELETE` mapeado, de modo que el gestor recibe 405 con `Allow`
    - _Requisitos: 1.2, 1.3, 4.1, 4.8, 4.11, 5.1–5.9, 6.1, 6.4, 18.6, SGA-01_

  - [x] 11.2 Implementar `ArticuloController`
    - Área: backend · Responsable: Sebastián
    - `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `GET /{id}/movimientos` (`sort` solo `registradoEn`; por defecto `registradoEn,desc`) y `POST /{id}/movimientos` (201 con `Location: /api/sga/articulos/{id}/movimientos`) en `/api/sga/articulos`; parámetros `q`, `categoria`, `bajoStockMinimo`, `page`, `size` y `sort` (`codigo`, `nombre`, `existencia` o `creadoEn`; por defecto `nombre,asc`); sin `DELETE` mapeado
    - _Requisitos: 1.2, 13.1, 13.8, 13.10, 14.1–14.7, 15.1, 15.11, 15.13, 15.14, SGA-03_

  - [x] 11.3 Implementar `ResumenSgaController`
    - Área: backend · Responsable: Sebastián
    - `GET /api/sga/resumen` sin parámetros, que delega en `ResumenSgaService.obtener()`
    - _Requisitos: 1.2, 16.1, SGA-06_

  - [x] 11.4 Implementar `PaqueteController` y retirar `AutorizacionModulosTests`
    - Área: backend · Responsable: Sebastián
    - `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `GET /{id}/estados` (arreglo JSON) y `POST /{id}/estados` (201 con `Location: /api/sga/paquetes/{id}/estados`) en `/api/sga/paquetes`, según el fragmento del diseño; filtros `estado`, `clienteId`, `contratoId`, `dronId` y `q`, y `sort` (`codigo`, `creadoEn` o `estadoActualizadoEn`; por defecto `creadoEn,desc`); sin `DELETE` mapeado
    - En el mismo cambio, eliminar `backend/src/test/java/com/arise/corp/login/controller/AutorizacionModulosTests.java`: su único endpoint es el doble `GET /api/sga/paquetes`, que con el controlador real provoca un error de mapeo ambiguo al arrancar el contexto (D16)
    - Confirmar en el mismo commit que la tarea 11.5 (`SgaAutorizacionTests`), que recoge los casos de la clase retirada (Requirement 25.8)
    - _Requisitos: 1.2, 7.1, 8.1–8.9, 9.1, 9.9, 10.1, 12.1, 12.6, 12.7, 25.8, SGA-02, SGA-04_

  - [x] 11.5 Escribir `sga/controller/SgaAutorizacionTests`
    - Área: backend · Responsable: Sebastián
    - Matriz exhaustiva parametrizada: 21 operaciones (18 endpoints y los tres `DELETE`) × {sin token, sin perfil, perfil inactivo, ADMINISTRADOR, COMERCIAL, FINANZAS_RRHH, OPERACIONES}, con `SgaDatosPrueba` y el `PerfilJwtAuthenticationConverter` real sobre perfiles persistidos en H2. Sin token, 401 con `WWW-Authenticate`; en los denegados, 403 sin cambios en los datos y también con un `{id}` inexistente; `DELETE` con un rol gestor → 405 con `Allow` y registro conservado; éxito para ADMINISTRADOR y OPERACIONES
    - Recoge los casos de `AutorizacionModulosTests` (`sinTokenDevuelve401`, `sinPerfilActivoDevuelve403EnCualquierModulo`, `perfilInactivoDevuelve403EnCualquierModulo`, `operacionesAccedeASgaYFinanzasNo`, `comercialNoAccedeASga` y `administradorAccedeASga`); el Javadoc indica que la matriz de los tres módulos se prueba sobre controladores reales en `CrmAutorizacionTests`, `ErpAutorizacionTests` y `SgaAutorizacionTests`. Las lecturas de OPERACIONES sobre el CRM siguen en `CrmAutorizacionTests` y `ClienteControllerTests`, sin duplicarlas (interpretación 14)
    - **Property 10: La matriz de acceso se aplica a todos los endpoints del SGA** · **Validates: Requirements 4.11, 9.9, 13.10, 18.1, 18.2, 18.3, 18.4, 18.5, 25.4, 25.5**
    - _Requisitos: 18.1–18.6, 25.4, 25.5, 25.8, CA-02, CA-03, NF-02, NF-07_

- [x] 12. Escribir las pruebas de controlador y de logs del SGA
  - [x] 12.1 Escribir `sga/controller/DronControllerTests`
    - Área: backend · Responsable: Sebastián
    - MockMvc + H2 con `SgaDatosPrueba`: 201 con `Location`, 200, un 400 de `DronRequest` con `errors` por campo, 400 de `EstadoDronRequest` con un `estado` desconocido, `id` no UUID, 404, 409 `DRON_CODIGO_DUPLICADO` con el código escrito con espacios y minúsculas, 409 `TRANSICION_DRON_NO_PERMITIDA` con `campo`, `estadoActual` y `estadoSolicitado`, 409 `DRON_CON_PAQUETE_EN_CURSO` con `paqueteId`, campos del servidor ignorados, `paqueteEnCurso` en la lista y en el detalle, y 405 de `DELETE` con `Allow`
    - _Requisitos: 3.2, 4.1–4.11, 5.7–5.9, 6.1–6.6, 19.2, 19.4, 19.5, 25.6, NF-07_

  - [x] 12.2 Escribir `sga/controller/PaqueteControllerTests`
    - Área: backend · Responsable: Sebastián
    - 201 con `Location` (alta y `.../estados` con la colección); un 400 de `PaqueteCrearRequest`, `PaqueteActualizarRequest` y `TransicionPaqueteRequest` (incluido `dronId` según el estado); 404 de la ruta y de `clienteId`, `contratoId` y `dronId` con `campo`; un caso por código: `PAQUETE_CODIGO_DUPLICADO`, `CLIENTE_INACTIVO`, `CONTRATO_DE_OTRO_CLIENTE`, `CONTRATO_NO_ACTIVO`, `PAQUETE_NO_EDITABLE`, `TRANSICION_NO_PERMITIDA`, `DRON_NO_DISPONIBLE` y `DRON_CON_PAQUETE_EN_CURSO` con `conflicto`, `campo` y extensiones
    - 405 de `DELETE` y de `PUT`, `PATCH` y `DELETE` sobre `.../estados` con `Allow`; `estado` y `dronId` del alta ignorados; historial como arreglo ordenado; cliente con solo `id`, `nombre` y `activo`, leído del CRM en cada consulta; `detail` sin dirección, observación ni nombre del cliente
    - _Requisitos: 7.1–7.11, 8.7–8.9, 9.1–9.9, 10.1–10.4, 11.1–11.5, 12.1, 12.6, 12.7, 17.2, 17.4, 19.2, 19.4–19.6, 25.6, NF-07_

  - [x] 12.3 Escribir `sga/controller/ArticuloControllerTests`
    - Área: backend · Responsable: Sebastián
    - 201 con `Location` (artículo y colección de movimientos); un 400 de `ArticuloRequest` y de `MovimientoInventarioRequest` (incluido `cantidad: 1.5`); 404 de la ruta y de los movimientos; 409 `ARTICULO_CODIGO_DUPLICADO` y `EXISTENCIA_INSUFICIENTE` con `campo = "cantidad"`, `existenciaActual` y `cantidadSolicitada`; 405 de `DELETE` y de `PUT`, `PATCH` y `DELETE` sobre `.../movimientos` con `Allow`; `existencia` del cuerpo ignorada; `bajoStockMinimo` en la respuesta
    - _Requisitos: 13.1–13.10, 14.6, 14.7, 15.1, 15.5–15.8, 15.13, 15.14, 19.2, 19.4, 19.5, 25.6, NF-07_

  - [x] 12.4 Escribir `sga/SgaLogsTests`
    - Área: backend · Responsable: Sebastián
    - Con `OutputCaptureExtension` y `@MockitoBean ContratoReferenciaService` cuyo `buscarParaAsociar` lanza una excepción sin datos: crear y editar paquetes sin contrato, provocar un 409 de código duplicado y un 500 (alta con `contratoId`, que llega al doble), con una dirección de entrega, una observación y un nombre de cliente ficticios conocidos; la salida capturada no contiene esos valores
    - _Requisitos: 17.3, 25.7, CA-06_

- [x] 13. Checkpoint del backend sin Docker
  - Área: verificación · Responsable: Sebastián
  - Ejecutar `.\mvnw.cmd clean test` desde `backend/` sin Docker, sin Supabase y sin `.env` (Requirement 25.10), con las pruebas de `001-foundation`, `002-crm-clientes-contratos` y `003-erp-empleados-proveedores-finanzas` en verde, incluidas `CrmAutorizacionTests` y `ClienteControllerTests` sin cambios (Requirement 25.9). Asegurar que todas las pruebas pasan; consultar a la usuaria si surgen dudas.

- [x] 14. Implementar las pruebas de integración del SGA con PostgreSQL efímero
  - [x] 14.1 Ampliar `PostgresIntegracionBase`
    - Área: backend · Responsable: Sebastián
    - El `truncate` de `@BeforeEach` incluye `app.movimientos_inventario`, `app.articulos_inventario`, `app.historial_estados_paquete`, `app.paquetes` y `app.drones` antes de las tablas del ERP, del CRM, `app.perfiles_usuario` y `auth.users`, con `cascade`, según el diseño. Requiere la migración de la tarea 2.1. `MigracionesSupabase`, `PostgresContenedorConfig`, el Script_Preparacion_IT y `pom.xml` no cambian
    - _Requisitos: 26.1, 26.10, 26.11, ENG-03_

  - [x] 14.2 Escribir `MigracionesSgaIT`
    - Área: backend · Responsable: Sebastián
    - `ddl-auto=validate` acepta las entidades del SGA; las cinco tablas están vacías tras migrar; `information_schema` confirma `uuid`, `integer`, `bigint`, `smallint`, `timestamptz` y `text`
    - Inserciones JDBC inválidas producen SQLState 23505 (códigos duplicados en las tres tablas, segundo paquete en curso con el mismo dron y `secuencia` repetida), 23514 (estados, categorías, unidades, formato de código, longitudes, dron incoherente con el estado del paquete, dron en un registro que no es `ASIGNADO`, alta sin `REGISTRADO`, existencia negativa, cantidad fuera de rango por tipo, aritmética incorrecta de cada tipo y `AJUSTE` sin observación) y 23503 (FK a `app.clientes`, `app.contratos`, `app.drones`, `app.paquetes` y `app.articulos_inventario`); dos paquetes `CANCELADO` con el mismo dron se aceptan
    - RLS activo (`relrowsecurity = true`) y `has_table_privilege` falso para `anon` y `authenticated` en `select`, `insert`, `update` y `delete` sobre las cinco tablas
    - _Requisitos: 2.1–2.14, 26.1–26.4, DP-SEG-05, ENG-03_

  - [x] 14.3 Escribir `FlujoSgaIT`
    - Área: backend · Responsable: Sebastián
    - Por MockMvc contra PostgreSQL: cliente activo con contrato `ACTIVO` creados por COMERCIAL; dron y paquete asociado a ambos creados por OPERACIONES; transiciones `ASIGNADO`, `EN_TRANSITO` y `ENTREGADO`; historial con cuatro registros encadenados y el `sub` del actor; dron en `DISPONIBLE`, `EN_MISION` y de nuevo `DISPONIBLE`; conteos del resumen (CA-05)
    - Recorridos `EN_TRANSITO→DEVUELTO` y `ASIGNADO→CANCELADO` con el dron de nuevo asignable; con un conjunto pequeño de datos, filtros de paquetes por estado, cliente y dron, `asignable` de drones (cuatro estados × con y sin paquete en curso) y `bajoStockMinimo` de artículos (existencia menor, igual y mayor que el mínimo, y mínimo 0); el nombre del cliente cambiado en el CRM se refleja en el detalle del paquete
    - **Property 4: El estado del dron sigue al de su paquete** (recorridos contra PostgreSQL) · **Validates: Requirements 11.7, 11.9, 11.10, 11.11**
    - **Property 5: El historial está encadenado** (contra PostgreSQL) · **Validates: Requirements 12.2, 12.3, 12.5**
    - _Requisitos: 8.8, 26.5, 26.6, 26.9, CA-04, CA-05, ENG-03_

  - [x] 14.4 Escribir `SgaTransaccionIT`
    - Área: backend · Responsable: Sebastián
    - Un `TransactionTemplate` que llama a `PaqueteService.cambiarEstado` (`ASIGNADO→EN_TRANSITO`) y después lanza una excepción deja el estado, el historial y el dron anteriores; lo mismo con `MovimientoInventarioService.registrar` y la existencia
    - Concurrencia con dos hilos y un `CountDownLatch` (D10): dos asignaciones del mismo dron a paquetes distintos terminan en un 201 y un 409, con un solo paquete en curso; dos `SALIDA` de 7 sobre una existencia de 10 terminan en un 201 y un 409 `EXISTENCIA_INSUFICIENTE`, con existencia final 3
    - _Requisitos: 10.5, 11.12, 15.9, 15.10, 26.7, 26.8, DP-SEG-06, ENG-03_

- [x] 15. Aplicar la migración del SGA en `arise-corp-dev`
  - [x] 15.1 Aplicar la migración revisada de forma controlada
    - Área: base de datos · Responsable: Davidson
    - Requisitos previos: revisión 2.3 aprobada y `MigracionesSgaIT` (14.2) en verde. Aplicar con el mismo procedimiento controlado de la tarea 15.1 de `003-erp-empleados-proveedores-finanzas` (D21)
    - Ejecutar las consultas de verificación del diseño (`pg_class.relrowsecurity` y `has_table_privilege` de `anon` y `authenticated` sobre las cinco tablas) y confirmar en Supabase (Settings → Data API → Exposed schemas) que `app` sigue fuera de la lista
    - Registrar en este `tasks.md` solo resultado, fecha y entorno, sin UUID, credenciales ni datos personales. Desde aquí la migración no se edita; un cambio de esquema llega en una migración nueva
    - Evidencia (2026-11-24): aplicada en `arise-corp-dev` y registrada en `supabase_migrations.schema_migrations` con la versión `20261121120000` y el nombre `app_sga_drones_paquetes_inventario`. Verificado sobre las cinco tablas: están vacías, tienen RLS activo (`pg_class.relrowsecurity`), no conceden `select`, `insert`, `update` ni `delete` a `anon` ni a `authenticated` (`has_table_privilege`), usan `uuid`, `smallint`, `integer`, `bigint` y `timestamptz`, y conservan las FK a `app.clientes`, `app.contratos`, `app.drones`, `app.paquetes` y `app.articulos_inventario` y los índices previstos (incluido el único parcial `paquetes_dron_en_curso_uk`). El backend de la rama arrancó con `docker compose up backend` contra `arise-corp-dev` (`ddl-auto=validate`) y `GET /api/sga/drones` respondió 401 con `WWW-Authenticate`. La comprobación de "Exposed schemas" en el panel de Supabase queda manual.
    - _Requisitos: 2.12, 29.8, DP-TEC-03, DP-SEG-05, NF-01_

- [x] 16. Verificar la imagen Docker y los workflows con el SGA
  - [x] 16.1 Verificar la imagen y Compose
    - Área: backend/Docker · Responsable: Sebastián
    - `docker build -t arise-backend:local backend` sin credenciales ni conexión a Supabase; `docker run --rm --entrypoint sh arise-backend:local -c "find / -name '.env*' 2>/dev/null"` no devuelve nada y `docker history --no-trunc` no muestra valores de configuración. `backend/Dockerfile`, `backend/.dockerignore` y `docker-compose.yml` no cambian (D22)
    - Con un `backend/.env` válido y la migración aplicada (15.1), `docker compose up backend` y `curl -i http://localhost:8080/api/sga/drones` → 401 con `WWW-Authenticate`; al terminar, `docker compose down`
    - Registrar el resultado en este `tasks.md` sin datos sensibles
    - _Requisitos: 28.1–28.3, ENG-01, CA-01, CA-02, NF-01_

  - [x] 16.2 Verificar los workflows de CI en la rama
    - Área: CI · Responsable: Davidson
    - Una ejecución verde de `backend-ci`, `integration-tests` y `frontend-ci` para el último commit de la rama; el log de `integration-tests` lista `MigracionesSgaIT`, `FlujoSgaIT` y `SgaTransaccionIT`; los YAML de `.github/workflows/` no cambian y conservan `permissions: contents: read`, las acciones oficiales `actions/*` y la ausencia de secretos
    - Si un workflow necesitara cambios, justificarlos antes en `design.md` (Requirement 28.8). Registrar el resultado en este `tasks.md`
    - Evidencia (2026-11-24): `backend-ci`, `integration-tests` y `frontend-ci` concluyeron en verde para el commit `0f331ce` de la rama; los YAML no cambian. El log de `integration-tests` no se descarga sin sesión de GitHub, pero `.\mvnw.cmd -B verify` lista `MigracionesSgaIT` (9 pruebas), `FlujoSgaIT` (4) y `SgaTransaccionIT` (4) en verde.
    - _Requisitos: 26.12, 28.4–28.6, 28.8, ENG-04_

- [x] 17. Documentar el backend y actualizar los steering
  - [x] 17.1 Actualizar el README
    - Área: documentación · Responsable: Davidson
    - Aviso inicial y §4: estado con el SGA implementado y el flujo CA-05 demostrable. §5: estructura con `sga/`, `crm/service/ContratoReferenciaService`, `pages/sga`, `services/sgaApi.js` y la Migracion_SGA. La §11.6 la actualiza Paula en la tarea 28.1
    - Confirmar que no hay variables de entorno ni dependencias nuevas y que los `.env.example` no cambian
    - Evidencia (2026-11-24): README actualizado (aviso inicial, §4 y §5) con el SGA backend, `crm/service/ContratoReferenciaService`, el módulo `sga/` con sus capas, las pruebas y la Migracion_SGA; las rutas del frontend del SGA quedan marcadas para la tarea 28.1. Sin variables ni dependencias nuevas.
    - _Requisitos: 29.3, 29.6, CA-01_

  - [x] 17.2 Actualizar los steering de arquitectura, estructura, tecnología y producto
    - Área: documentación · Responsable: Davidson
    - `architecture.md`: la API incluye el SGA; PostgreSQL incluye las cinco tablas del SGA; módulo `sga` con su relación de solo lectura con el CRM mediante `ClienteReferenciaService` y `ContratoReferenciaService`
    - `structure.md`: árbol del backend con `sga/` y sus capas, `crm/service/ContratoReferenciaService`, `crm/dto/ContratoReferencia`, pruebas `sga/`, IT nuevas y la Migracion_SGA
    - `tech.md`: persistencia "[Implementado: perfil, CRM, ERP y SGA]" y validación "[Implementado] en DTO de login, CRM, ERP y SGA". `product.md`: "Estado actual" con SGA-01 a SGA-06 implementados en el backend y el flujo CA-05 demostrable; "Aprobado, pendiente de implementar" queda vacío; `004-sga-drones-paquetes-inventario` queda cerrada al integrar su PR. Las líneas del frontend las añade Paula en la tarea 28.1
    - Los ajustes de reglas quedaron registrados en las tareas 1.2 y 1.3; aquí solo se comprueba que el código los cumple
    - Evidencia (2026-11-24): `architecture.md` (API, PostgreSQL y esquema del SGA), `structure.md` (árbol de `sga/`, `ContratoReferenciaService`, pruebas e IT), `tech.md` (persistencia y validación) y `product.md` (SGA-01 a SGA-06 en backend, CA-05 demostrable y cierre de la spec al integrar el PR) actualizados. Se anota que la migración está pendiente de aplicar y que las vistas del frontend llegan en las tareas 19-28.
    - _Requisitos: 29.4, CA-07, NF-05_

  - [x] 17.3 Actualizar los steering de API y seguridad
    - Área: documentación · Responsable: Sebastián
    - `api-conventions.md`: endpoints del SGA en "Endpoints existentes" con sus roles y respuestas; códigos de conflicto del SGA con sus extensiones; `campo` en los 404 de `clienteId`, `contratoId` y `dronId`; lista vacía cuando un filtro de `GET /api/sga/paquetes` no tiene registro; 405 con `Allow` de los `DELETE` y de los métodos no mapeados de `.../estados` y `.../movimientos`
    - `security.md`: `/api/sga/**` sin lecturas cruzadas; el SGA lee clientes y contratos solo mediante `ClienteReferenciaService` y `ContratoReferenciaService`; dirección de entrega, observaciones y nombre del cliente fuera de logs y de `detail`; una sola representación para los gestores del SGA; DTO con `toString()` protegido y `SgaLogsTests` como verificación
    - _Requisitos: 17.1–17.4, 29.4, CA-07_

- [x] 18. Checkpoint del backend completo
  - Área: verificación · Responsable: Davidson
  - Ejecutar desde `backend/` `.\mvnw.cmd clean test` y, con Docker en ejecución, `.\mvnw.cmd verify` (Requirement 26.12); desde la raíz, `docker compose build backend`. Asegurar que todas las pruebas pasan; consultar a la usuaria si surgen dudas.
  - Evidencia (2026-11-24): `.\mvnw.cmd -B clean verify` con Docker: 1418 pruebas unitarias y 52 de integración, 0 fallos; `docker compose build backend` construye `arise-backend:local` sin errores.

- [x] 19. Implementar el servicio y las reglas de interfaz del SGA
  - [x] 19.1 Crear `services/sgaApi.js`
    - Área: frontend · Responsable: Paula
    - Las 18 funciones de la tabla "Servicio del SGA" del diseño sobre `apiRequest` de `apiClient.js`, con una función local `listar(ruta, filtros)` igual a la de `erpApi.js`: solo añade a `URLSearchParams` los filtros con valor no nulo y no vacío después de `trim`
    - Exportar `cuerpoTransicion` (`dronId` solo con `ASIGNADO`) y `cuerpoMovimientoInventario` (`cantidad` como número entero); `crearArticulo` y `actualizarArticulo` envían `stockMinimo: Number(...)`. Se trabaja sobre el contrato de `design.md`, sin esperar al backend; `crmApi.js` no cambia
    - _Requisitos: 20.1–20.3, AUTH-03, DP-API-03, DP-API-04_

  - [x] 19.2 Escribir `services/sgaApi.test.js`
    - Área: frontend · Responsable: Paula
    - `fetch` simulado con `vi.stubGlobal` y Supabase con `vi.mock('../lib/supabase.js')`: ruta, método, consulta sin parámetros vacíos y cuerpo JSON de cada función, sin llamadas de red reales; cantidades y stock mínimo como enteros JSON
    - **Property 3: Solo se asigna un dron asignable** (lado frontend: `cuerpoTransicion` incluye `dronId` si y solo si el estado es `ASIGNADO`, para los seis estados) · **Validates: Requirements 20.2**
    - **Property 8: Las cantidades se aceptan solo como enteros dentro del rango de su tipo** (cuerpo enviado como número entero JSON) · **Validates: Requirements 20.2**
    - _Requisitos: 27.1, 27.7, NF-07_

  - [x] 19.3 Crear `lib/validacionSga.js`
    - Área: frontend · Responsable: Paula
    - `esCodigoValido`, `esEnteroEnRango(valor, min, max)` (texto sin espacios exteriores con `/^\d{1,7}$/` y `min ≤ n ≤ max`), `validarDron`, `validarPaquete`, `validarEdicionPaquete`, `validarTransicion`, `validarArticulo` y `validarMovimientoInventario`, con los límites y mensajes del contrato de API, a partir de `texto`, `revisar`, `maximo` y `normalizarCodigo` de `lib/validacionCrm.js`, que no cambia (D19)
    - _Requisitos: 21.5, 22.9, 22.13, 23.6, 23.9, NF-06_

  - [x] 19.4 Escribir `lib/validacionSga.test.js`
    - Área: frontend · Responsable: Paula
    - Con los generadores de `src/test/generadores.js` (sin cambios): textos de cantidad generados por tipo y bordes; reglas de código (también con espacios y minúsculas), longitudes, `dronId` obligatorio con `ASIGNADO` y observación obligatoria en `AJUSTE`
    - **Property 8: Las cantidades se aceptan solo como enteros dentro del rango de su tipo** (frontend) · **Validates: Requirements 23.6, 23.9**
    - _Requisitos: 27.2, NF-07_

  - [x] 19.5 Ampliar `lib/permisos.js`
    - Área: frontend · Responsable: Paula
    - `puedeGestionarSga`, `TRANSICIONES_PAQUETE`, `esPaqueteFinal`, `esPaqueteEditable`, `TRANSICIONES_MANUALES_DRON`, `transicionesManualesDron`, `OPCIONES_SGA` y `opcionesMenuSga(rol)` según el diseño; `opcionesMenu(rol)` y el resto de funciones no cambian (D7, D18)
    - _Requisitos: 20.5–20.8, 21.9, 21.10, 22.7, 22.12, 22.15, DP-PRO-01_

  - [x] 19.6 Ampliar `lib/permisos.test.js`
    - Área: frontend · Responsable: Paula
    - `puedeGestionarSga` y `opcionesMenuSga` para los cuatro roles, sin perfil y con un valor desconocido; los casos existentes no cambian
    - **Property 1: Solo se aplican las transiciones permitidas del paquete** (`TRANSICIONES_PAQUETE`, `esPaqueteEditable` y `esPaqueteFinal`) · **Validates: Requirements 22.7, 22.12, 22.15**
    - **Property 2: El dron solo cambia a mano entre sus estados manuales y sin paquete en curso** (`transicionesManualesDron` para cuatro estados × con y sin paquete en curso) · **Validates: Requirements 21.9, 21.10**
    - _Requisitos: 27.2, NF-07_

- [x] 20. Incorporar el menú y las tarjetas del SGA
  - [x] 20.1 Ampliar `components/AppLayout.jsx` con el grupo SGA
    - Área: frontend · Responsable: Paula
    - Crear `frontend/public/assets/sga.svg`, un SVG estático propio sin datos ni referencias externas
    - `const sga = opcionesMenuSga(rol)`; si no está vacío, `GrupoMenu` "SGA" con el icono y los cuatro enlaces debajo del grupo ERP; sección "SGA" en la ruta de navegación cuando `pathname.startsWith('/sga/')`; título de la página buscado en `[...opciones, ...erp, ...sga]`. Los menús CRM y ERP no cambian; OPERACIONES conserva Clientes y Contratos en modo consulta
    - _Requisitos: 20.5–20.7, 20.11, DP-PRO-01_

  - [x] 20.2 Ampliar `pages/Panel.jsx` con las tarjetas SGA
    - Área: frontend · Responsable: Paula
    - Si `puedeGestionarSga(rol)`, una tarjeta por cada elemento de `OPCIONES_SGA` con `<small>SGA</small>`, el título y el enlace `accion →`; ninguna tarjeta SGA para COMERCIAL ni FINANZAS_RRHH
    - _Requisitos: 20.7, 20.8, SGA-06_

- [x] 21. Crear los componentes compartidos del SGA
  - [x] 21.1 Ampliar `SelectorRegistro` y crear `EstadoPaqueteEtiqueta` y `EstadoDronEtiqueta`
    - Área: frontend · Responsable: Paula
    - `components/SelectorRegistro.jsx`: prop opcional `textoVacio` (por defecto "Sin asociación") para la primera opción; el resto no cambia y las vistas ERP lo siguen usando igual (D20)
    - `components/EstadoPaqueteEtiqueta.jsx` (`REGISTRADO` y `ASIGNADO → borrador`, `EN_TRANSITO` y `ENTREGADO → activo`, `CANCELADO` y `DEVUELTO → inactivo`) y `components/EstadoDronEtiqueta.jsx` (`DISPONIBLE → activo`, `EN_MISION → borrador`, `MANTENIMIENTO → finalizado`, `FUERA_DE_SERVICIO → inactivo`), con las clases existentes. Ningún componente usa `dangerouslySetInnerHTML`
    - _Requisitos: 21.8, 22.1, 22.4, 22.11, NF-06_

- [x] 22. Checkpoint de la base del frontend
  - Área: verificación · Responsable: Paula
  - Ejecutar `npm test` desde `frontend/`: las pruebas nuevas del servicio y de las reglas pasan junto con las de `001-foundation`, `002-crm-clientes-contratos` y `003-erp-empleados-proveedores-finanzas`. Asegurar que todas las pruebas pasan; consultar a la usuaria si surgen dudas.
  - Verificación (2026-11-24): `npm test -- --reporter=dot`, 33 archivos y 317 pruebas aprobadas, incluidas las suites de las specs anteriores. Sin cambios en backend ni dependencias.

- [x] 23. Implementar las vistas de drones
  - [x] 23.1 Implementar `pages/sga/DronesLista.jsx`
    - Área: frontend · Responsable: Paula
    - Búsqueda `q`, filtro por Estado_Dron y paginación en la URL (`useSearchParams`), como `EmpleadosLista`; columnas código, modelo y estado con `EstadoDronEtiqueta`; "Nuevo dron"; "No hay drones para los criterios elegidos." si la página está vacía; carga con `EstadoConsulta`
    - _Requisitos: 21.1–21.3, SGA-01, DP-API-03_

  - [x] 23.2 Implementar `pages/sga/DronFormulario.jsx`
    - Área: frontend · Responsable: Paula
    - Alta y edición con `codigo` y `modelo` marcados con `*` y `observaciones` opcional; validación local con `validarDron` antes de enviar; `fieldset` deshabilitado y "Guardando…" durante el envío; errores 400 y 409 junto al campo (`errors[].field` o `campo`) sin perder lo escrito
    - _Requisitos: 21.4–21.7, SGA-01, NF-06_

  - [x] 23.3 Implementar `pages/sga/DronDetalle.jsx`
    - Área: frontend · Responsable: Paula
    - Datos, estado, auditoría del último cambio de estado con `formatoInstante` y enlace a `/sga/paquetes/{id}` del Paquete_En_Curso; botones de `transicionesManualesDron(dron)` ("Marcar como disponible", "Enviar a mantenimiento", "Poner fuera de servicio") con `DialogoConfirmacion` y `cambiarEstadoDron`; con `EN_MISION` o paquete en curso no muestra botones e indica "El dron tiene el paquete {código} en curso"; al recibir 200 muestra el nuevo estado; un 409 muestra el mensaje en `role="alert"` y vuelve a cargar el dron
    - _Requisitos: 21.8–21.12, SGA-01, SGA-05_

  - [x] 23.4 Escribir `pages/sga/DronDetalle.test.jsx`
    - Área: frontend · Responsable: Paula
    - Cambio manual oculto con paquete en curso o en `EN_MISION` y enlace al paquete; confirmación y `PATCH` con el nuevo estado tras el 200; recarga tras un 409
    - **Property 2: El dron solo cambia a mano entre sus estados manuales y sin paquete en curso** (botones ofrecidos en el detalle) · **Validates: Requirements 21.9, 21.10**
    - _Requisitos: 21.8–21.12, 27.5, 27.7_

- [x] 24. Implementar las vistas de paquetes
  - [x] 24.1 Implementar `pages/sga/PaquetesLista.jsx`
    - Área: frontend · Responsable: Paula
    - Filtro por Estado_Paquete, búsqueda por código o descripción y paginación en la URL; columnas código, descripción, cliente, dron ("—" sin dron) y estado con `EstadoPaqueteEtiqueta`; "Nuevo paquete"; "No hay paquetes para los criterios elegidos."; carga con `EstadoConsulta`
    - _Requisitos: 22.1–22.3, SGA-02, DP-API-03_

  - [x] 24.2 Implementar `pages/sga/PaqueteFormulario.jsx`
    - Área: frontend · Responsable: Paula
    - Alta: `codigo`, `descripcion`, `direccionEntrega` y cliente obligatorios; `SelectorRegistro` de clientes con `textoVacio="Selecciona un cliente"` y `q => listarClientes({ activo: true, q, size: 20, sort: 'nombre,asc' })` de `crmApi.js`; `<select>` de contrato con "Sin contrato" y los contratos `ACTIVO` del cliente elegido (`listarContratos({ clienteId, estado: 'ACTIVO', size: 100, sort: 'codigo,asc' })`); observación opcional. Cambiar de cliente quita la selección de contrato
    - Edición solo en Estado_Editable_Paquete (si no, "Solo se editan paquetes registrados o asignados." con enlace al detalle): código y cliente en lectura; si el contrato actual ya no está en `ACTIVO`, aparece seleccionado como "{código} ({estado})" con el texto "El contrato asociado ya no está activo; puedes conservarlo"
    - Validación local con `validarPaquete` o `validarEdicionPaquete`; `fieldset` deshabilitado durante el envío; errores 400, 404 y 409 con `campo` junto al campo (también bajo los selectores) con los valores conservados; un 409 sin campo (`PAQUETE_NO_EDITABLE`) muestra el mensaje y vuelve a cargar el paquete
    - _Requisitos: 22.4–22.10, 22.17, SGA-02, SGA-05, NF-06_

  - [x] 24.3 Implementar `pages/sga/PaqueteDetalle.jsx`
    - Área: frontend · Responsable: Paula
    - Datos, estado, enlaces a `/crm/clientes/{id}`, `/crm/contratos/{id}` y `/sga/drones/{id}`, y tabla del Historial_Paquete en orden con estado anterior, estado nuevo, dron, observación, fecha y hora (`formatoInstante`) y actor
    - Fuera de un Estado_Final_Paquete: "Editar" solo si es editable y una sección "Cambiar estado" con las opciones de `TRANSICIONES_PAQUETE[estado]` (etiquetas del diseño) y una observación opcional; al elegir `ASIGNADO` carga `listarDrones({ asignable: true, size: 100, sort: 'codigo,asc' })` y exige elegir uno; un destino final abre `DialogoConfirmacion` con "Este cambio es irreversible."; envía `cambiarEstadoPaquete`
    - Un 201 o un 409 vuelven a cargar el paquete y el historial; el 409 muestra el mensaje en `role="alert"`. En estado final no muestra edición ni transiciones
    - _Requisitos: 22.11–22.17, SGA-04, SGA-05, CA-05, DP-API-05_

  - [x] 24.4 Escribir `pages/sga/PaqueteFormulario.test.jsx`
    - Área: frontend · Responsable: Paula
    - Selector de contratos `ACTIVO` del cliente elegido, limpieza del contrato al cambiar de cliente, contrato actual no activo conservado en la edición, validación local sin petición y errores 400, 404 y 409 por campo con los valores conservados
    - _Requisitos: 22.4–22.10, 27.3, 27.7_

  - [x] 24.5 Escribir `pages/sga/PaqueteDetalle.test.jsx`
    - Área: frontend · Responsable: Paula
    - Elección obligatoria de un dron asignable para `ASIGNADO`, confirmación irreversible en los destinos finales, recarga tras 201 y tras 409, historial en orden y enlaces al cliente, al contrato y al dron
    - **Property 1: Solo se aplican las transiciones permitidas del paquete** (transiciones y edición ofrecidas en cada estado con `it.each`) · **Validates: Requirements 22.12, 22.15**
    - _Requisitos: 22.11–22.17, 27.4, 27.7_

- [x] 25. Implementar las vistas de inventario
  - [x] 25.1 Implementar `pages/sga/ArticulosLista.jsx`
    - Área: frontend · Responsable: Paula
    - Búsqueda, filtro por Categoria_Articulo y casilla "Solo bajo stock mínimo" (`bajoStockMinimo=true`) en la URL; columnas código, nombre, categoría, existencia con su unidad (`toLocaleString('es-CO')`), stock mínimo y la etiqueta "Bajo stock" cuando corresponde; "Nuevo artículo"; "No hay artículos para los criterios elegidos."; carga con `EstadoConsulta`
    - _Requisitos: 23.1–23.4, SGA-03, DP-API-03_

  - [x] 25.2 Implementar `pages/sga/ArticuloFormulario.jsx`
    - Área: frontend · Responsable: Paula
    - `codigo`, `nombre`, selectores de categoría y unidad y `stockMinimo` (`inputMode="numeric"`), todos obligatorios y sin campo de existencia; validación local con `validarArticulo`; mismo tratamiento del envío y de los errores 400 y 409 que `DronFormulario`
    - _Requisitos: 23.5–23.7, SGA-03, NF-06_

  - [x] 25.3 Implementar `pages/sga/ArticuloDetalle.jsx`
    - Área: frontend · Responsable: Paula
    - Datos, existencia con su unidad y etiqueta "Bajo stock"; formulario de movimiento con tipo, cantidad y observación que, con `AJUSTE`, muestra "La cantidad es la existencia contada y la observación es obligatoria."; validación local con `validarMovimientoInventario` y envío con `registrarMovimientoInventario`
    - Un 201 muestra "Existencia resultante: {n}" y vuelve a cargar el artículo y los movimientos; un 409 `EXISTENCIA_INSUFICIENTE` muestra el mensaje junto a la cantidad, conserva lo escrito y vuelve a cargar el artículo; tabla paginada de movimientos con fecha y hora, tipo, cantidad, existencia anterior, existencia resultante y observación
    - _Requisitos: 23.2, 23.8–23.12, SGA-03_

  - [x] 25.4 Escribir `pages/sga/ArticuloDetalle.test.jsx`
    - Área: frontend · Responsable: Paula
    - `AJUSTE` sin observación bloqueado localmente sin petición, existencia resultante tras el 201 y 409 `EXISTENCIA_INSUFICIENTE` junto a la cantidad con los valores conservados
    - _Requisitos: 23.8–23.11, 27.5, 27.7_

- [x] 26. Implementar la vista de indicadores del SGA
  - [x] 26.1 Implementar `pages/sga/IndicadoresSga.jsx`
    - Área: frontend · Responsable: Paula
    - Tres secciones: paquetes con los seis estados y el total, drones con los cuatro estados y el total, y artículos bajo stock mínimo sobre el total con el enlace "Ver artículos bajo stock mínimo" a `/sga/inventario?bajoStockMinimo=true`; los estados con 0 se muestran; carga con `EstadoConsulta` y error con "Reintentar"
    - _Requisitos: 24.1–24.5, SGA-06, NF-06_

  - [x] 26.2 Escribir `pages/sga/IndicadoresSga.test.jsx`
    - Área: frontend · Responsable: Paula
    - Estados con 0 visibles, totales, enlace a los artículos bajo stock mínimo, y carga y error con "Reintentar"
    - _Requisitos: 24.1–24.5, 27.5, 27.7_

- [ ] 27. Integrar las rutas SGA en la aplicación
  - [ ] 27.1 Registrar las rutas `/sga/*` en `App.jsx`
    - Área: frontend · Responsable: Paula
    - Bloque `<Route element={<RolRoute permite={puedeGestionarSga} />}>` dentro de `<Route element={<AppLayout />}>`, con las 13 rutas de la tabla "Rutas" del diseño y las páginas cargadas con `lazy(() => import(...))`; `/login`, `/perfil`, `/panel`, `/admin/usuarios`, `/crm/*` y `/erp/*` no cambian
    - _Requisitos: 20.9, 20.11, 21.1, 22.1, 23.1, 24.1_

  - [ ] 27.2 Ampliar `App.test.jsx` con la navegación SGA
    - Área: frontend · Responsable: Paula
    - Menú y tarjetas SGA por rol; una ruta `/sga/*` con COMERCIAL o FINANZAS_RRHH muestra el mensaje de permisos sin peticiones a la API; OPERACIONES conserva Clientes y Contratos y no ve el ERP; las pruebas existentes siguen en verde
    - _Requisitos: 20.5–20.9, 27.6, 27.7, 27.8_

- [ ] 28. Documentar el frontend del SGA
  - [ ] 28.1 Actualizar la documentación del frontend
    - Área: documentación · Responsable: Paula
    - README §11.6: subsección "Vistas del SGA por rol" con la tabla de rutas `/sga/*` y sus roles (ADMINISTRADOR y OPERACIONES) y la nota de que OPERACIONES consulta clientes y contratos en modo lectura para asociarlos a paquetes; comandos `npm test`, `npm run build` y `npm run check:secrets`, sin variables nuevas
    - `structure.md`: `pages/sga`, `components/EstadoPaqueteEtiqueta`, `components/EstadoDronEtiqueta`, `SelectorRegistro` con `textoVacio`, `services/sgaApi.js`, `lib/validacionSga.js` y `public/assets/sga.svg`. `architecture.md`: la SPA presenta las vistas SGA. `product.md`: vistas SGA del frontend implementadas en "Estado actual"
    - _Requisitos: 29.3, 29.4, CA-01, CA-07_

- [ ] 29. Checkpoint del frontend
  - Área: verificación · Responsable: Paula
  - Ejecutar desde `frontend/` `npm test`, `npm run build` y `npm run check:secrets` (Requirements 27.8, 27.9). Asegurar que todas las pruebas pasan; consultar a la usuaria si surgen dudas.

- [ ] 30. Validar el SGA y el flujo integrado de extremo a extremo con Supabase real
  - [ ] 30.1 Validar el flujo cliente → contrato → paquete → estado y trazabilidad (CA-05)
    - Área: integración · Responsable: Paula
    - Precondiciones: migración aplicada (15.1); cuentas de prueba con perfiles COMERCIAL, OPERACIONES y FINANZAS_RRHH creados con `PUT /api/admin/usuarios/{id}/perfil`; backend con `docker compose up backend` y frontend con `npm run dev`
    - Con COMERCIAL: registrar un cliente ficticio y un contrato, y pasarlo a `ACTIVO`. Con OPERACIONES: registrar un dron, registrar un paquete asociado a ese cliente y a ese contrato, asignarlo al dron, pasarlo a `EN_TRANSITO` y a `ENTREGADO`; recargar la aplicación y consultar desde el detalle del paquete el historial (cuatro registros encadenados), el cliente y el contrato, y comprobar que el dron volvió a `DISPONIBLE`
    - Registrar en este `tasks.md` fecha, pasos y resultado sin credenciales, tokens, UUID ni datos personales reales
    - _Requisitos: 30.1, 30.6, CA-04, CA-05, SGA-01, SGA-02, SGA-04, SGA-05_

  - [ ] 30.2 Validar el inventario y los indicadores
    - Área: integración · Responsable: Davidson
    - Con OPERACIONES: registrar un artículo ficticio, una `ENTRADA` de 10, una `SALIDA` de 4 y una `SALIDA` de 20; la última responde 409 con la existencia disponible (6) y no cambia la existencia. Recargar y comprobar la existencia, los movimientos y los indicadores de paquetes, drones y artículos bajo stock mínimo
    - Registrar en este `tasks.md` fecha, pasos y resultado sin datos sensibles
    - _Requisitos: 30.2, 30.6, CA-04, SGA-03, SGA-06_

  - [ ] 30.3 Validar las denegaciones del SGA y del CRM
    - Área: integración · Responsable: Sebastián
    - Con COMERCIAL y con FINANZAS_RRHH, `/sga/drones` muestra el mensaje de falta de permisos sin pedir datos. Sin copiar tokens: el usuario OPERACIONES abre la edición de un dron, el Administrador cambia temporalmente su rol a COMERCIAL y el usuario envía el formulario; el backend responde 403, el dron no cambia y la interfaz muestra "No tienes permisos para realizar esta operación." sin cerrar la sesión. Se repite con FINANZAS_RRHH
    - Con OPERACIONES, el detalle de un cliente muestra la vista reducida sin edición; un usuario COMERCIAL con el formulario de un cliente abierto, cuyo rol se cambia a OPERACIONES, recibe 403 sin cambios
    - Al terminar, restablecer los roles, poner el dron de la prueba en `FUERA_DE_SERVICIO` y registrar en este `tasks.md` fecha, pasos y resultado sin datos sensibles
    - _Requisitos: 30.3–30.6, AUTH-05, CA-03, NF-02, DP-PRO-01_

- [ ] 31. Checkpoint final
  - Área: verificación · Responsable: Sebastián
  - Ejecutar `.\mvnw.cmd clean test` y `.\mvnw.cmd verify` desde `backend/`; `npm test`, `npm run build` y `npm run check:secrets` desde `frontend/`; y `docker compose build backend` desde la raíz. Asegurar que todas las pruebas pasan; consultar a la usuaria si surgen dudas.

- [ ] 32. Verificar y entregar la spec
  - [x] 32.1 Verificar CI, secretos, documentación y trazabilidad
    - Área: integración/documentación · Responsable: Davidson
    - Una ejecución verde de `backend-ci`, `integration-tests` y `frontend-ci` en la rama; ningún `.env` rastreado (`git ls-files`); contratos de `design.md` coherentes con los cuatro controladores del SGA (18 endpoints); `sga` sin importaciones de `erp`, `crm.entity` ni `crm.repository`, y `crm` y `erp` sin importaciones de `sga`; trazabilidad requisito–tarea revisada
    - `git diff main` sin cambios en `pom.xml`, `package.json`, los `.env.example`, el Dockerfile, `.dockerignore`, `docker-compose.yml`, `.github/workflows` ni `SecurityConfig`; `product.md` indica que `004-sga-drones-paquetes-inventario` queda cerrada al integrar el PR. Registrar el resultado en este `tasks.md`
    - Evidencia (2026-11-24): workflows verdes para `0f331ce`; solo `backend/.env.example` y `frontend/.env.example` rastreados; 18 endpoints del SGA en los cuatro controladores (drones 5, paquetes 6, artículos 6 y resumen 1); `sga` sin imports de `erp`, `crm.entity` ni `crm.repository`, y `crm`/`erp` sin imports de `sga`; `git diff main` sin cambios en los archivos congelados; `product.md` declara el cierre de la spec al integrar el PR.
    - _Requisitos: 1.5, 1.8, 28.4–28.8, 29.1, 29.4, 29.6, CA-01, CA-07, NF-01, ENG-04_

  - [x] 32.2 Comprobar los checks obligatorios en la protección de `main`
    - Área: CI · Responsable: Sebastián
    - La protección configurada en `002-crm-clientes-contratos` sigue activa: PR obligatorio con una aprobación de otro integrante, *squash merge*, rama actualizada antes de integrar y `backend-ci`, `integration-tests` y `frontend-ci` como checks obligatorios. Si algo cambió, restablecerlo sin guardar tokens ni credenciales en el repositorio
    - _Requisitos: 28.7, DP-SDD-05_

  - [ ] 32.3 Abrir el Pull Request único hacia `main`
    - Área: integración · Responsable: Sebastián
    - PR desde `feat/sga-drones-paquetes-inventario` con enlace a la spec, requisitos y tareas, instrucciones de prueba, resultados de las suites, construcción Docker, CI en verde, nombre de la Migracion_SGA aplicada, evidencia del flujo CA-05 y la lista de comprobación de `sdd-workflow.md`; revisión de un integrante distinto del autor de cada cambio; sin secretos en la descripción ni en las capturas
    - _Requisitos: 29.7, CA-05, CA-07, DP-SDD-04, DP-SDD-05_

## Notes

- La única subtarea marcada con `*` es `ResumenSgaResponseTests` (6.7): SGA-06 es un Should y sus totales y ceros también los comprueban `FlujoSgaIT` (14.3) e `IndicadoresSga.test.jsx` (26.2). No son opcionales las máquinas de estado (4.2, 4.3, 8.2, Requirement 25.2), la asociación con el CRM (3.2, 9.3), la sincronización dron–paquete y el historial (9.4), la aritmética del inventario y la validación de cantidades enteras (6.6, 10.3, Requirement 25.3), los controladores (12.1–12.3, Requirement 25.6), la matriz de autorización (11.5, CA-03), los logs (12.4, Requirement 25.7), las IT (14.2–14.4, Requirement 26) ni las pruebas del frontend que exige el Requirement 27.
- No se añaden pruebas fuera de "Testing Strategy" del diseño: sin pruebas de repositorios, de `ArticuloService`, de `ResumenSgaService`, de las listas ni de los formularios de dron y artículo, que se apoyan en componentes ya probados y en `validacionSga.test.js`.
- Cada prueba de propiedad cita su Property del diseño y los criterios que valida; la entrada que falla aparece en el mensaje de la aserción.
- `PaqueteController` y la retirada de `AutorizacionModulosTests` (11.4) se confirman en el mismo commit que `SgaAutorizacionTests` (11.5). Antes de ese commit las demás pruebas MockMvc siguen arrancando, porque `DronController`, `ArticuloController` y `ResumenSgaController` no comparten rutas con el doble `GET /api/sga/paquetes`.
- `PaqueteServiceTests` se escribe en dos tareas sobre el mismo archivo (9.3 y 9.4), en olas distintas del grafo.
- H2 no tiene el índice único parcial ni las FK al CRM: la regla de un paquete en curso por dron se prueba en el servicio (9.4) y el índice y las FK en `MigracionesSgaIT` y `SgaTransaccionIT`.
- Las vistas SGA enlazan `/sga/*` desde el menú y el panel (20.1, 20.2) antes de que existan las rutas (27.1); mientras tanto, la redirección `*` → `/perfil` atiende esos enlaces en la rama.
- Si `.\mvnw.cmd test` muestra `ConflictingBeanDefinitionException` tras mover o renombrar clases, ejecutar `.\mvnw.cmd clean test` (observación del cierre de `001-foundation`).
- La aplicación de la migración (15.1), la verificación de Compose (16.1), la validación manual (30.1–30.3) y la protección de `main` (32.2) se hacen fuera de CI y sin guardar credenciales, tokens ni UUID en el repositorio.
- Cada tarea referencia los requisitos de `requirements.md` y los identificadores del MVP para trazabilidad.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3"] },
    { "id": 2, "tasks": ["2.1", "3.1", "4.1", "19.1", "19.3", "19.5", "21.1"] },
    { "id": 3, "tasks": ["2.2", "2.3", "3.2", "4.2", "4.3", "5.1", "5.3", "5.6", "6.1", "6.2", "19.2", "19.4", "19.6", "20.1", "20.2"] },
    { "id": 4, "tasks": ["5.2", "23.1", "23.2", "23.3", "24.1", "24.2", "24.3", "25.1", "25.2", "25.3", "26.1"] },
    { "id": 5, "tasks": ["5.4", "5.5", "6.3", "6.4", "23.4", "24.4", "24.5", "25.4", "26.2", "27.1"] },
    { "id": 6, "tasks": ["5.7", "6.5", "27.2"] },
    { "id": 7, "tasks": ["6.6", "6.7", "8.1", "9.1", "10.1", "10.2", "10.4", "28.1"] },
    { "id": 8, "tasks": ["8.2", "9.2", "9.3", "10.3", "11.1", "11.2", "11.3"] },
    { "id": 9, "tasks": ["9.4", "11.4", "12.1", "12.3"] },
    { "id": 10, "tasks": ["11.5", "12.2", "12.4"] },
    { "id": 11, "tasks": ["14.1"] },
    { "id": 12, "tasks": ["14.2", "14.3", "14.4"] },
    { "id": 13, "tasks": ["15.1", "17.1", "17.2", "17.3"] },
    { "id": 14, "tasks": ["16.1"] },
    { "id": 15, "tasks": ["16.2"] },
    { "id": 16, "tasks": ["30.1"] },
    { "id": 17, "tasks": ["30.2"] },
    { "id": 18, "tasks": ["30.3"] },
    { "id": 19, "tasks": ["32.1"] },
    { "id": 20, "tasks": ["32.2"] },
    { "id": 21, "tasks": ["32.3"] }
  ]
}
```

## Verificación de las tareas realizadas

Fecha: 2026-11-23 · Rama: `feature/sga-drones-paquetes-inventario`

### Registro de la lectura de contratos y revisión de la migración (tareas 1.2 y 2.3)

| Tarea | Entregable | Verificación |
|---|---|---|
| 1.2 | `architecture.md`, DP-ARQ-01 | El párrafo de DP-ARQ-01 registra `crm.service.ContratoReferenciaService` junto a `ClienteReferenciaService`, con identificador, cliente, código y estado, solo lectura, en lote y con bloqueo de lectura dentro de la transacción del llamador. El ERP y el SGA definen sus DTO y no acceden a `Cliente`, `Contrato`, `EstadoContrato` ni a sus repositorios; el CRM no depende del ERP ni del SGA |
| 2.3 | Revisión de `20261121120000_app_sga_drones_paquetes_inventario.sql` | El SQL coincide línea a línea con el bloque "Migración SGA" del diseño (la única diferencia es la cerca de código del documento). Las cinco tablas, columnas y tipos coinciden con "Entidades JPA"; incluye los `CHECK`, `UNIQUE`, FK, el índice único parcial `paquetes_dron_en_curso_uk`, los índices de FK y de movimientos, los comentarios, RLS y `revoke` para `anon` y `authenticated`, y no inserta datos. No se requirieron cambios en la migración |

Alcance respetado:

- No se aplicó la migración a `arise-corp-dev`: la aplicación controlada sigue siendo la tarea 15.1 de Davidson, tras `MigracionesSgaIT` (14.2).
- No se modificaron `MigracionesSupabase`, `PostgresContenedorConfig`, el Script_Preparacion_IT ni `pom.xml`.
- Sin credenciales, tokens, UUID ni datos personales en este registro.

Commits: `22bab61` (`docs(sga)`).

### Lectura de contratos del CRM para otros módulos (tareas 3.1 y 3.2)

| Tarea | Entregable | Verificación |
|---|---|---|
| 3.1 | `crm.dto.ContratoReferencia`, `ContratoRepository.findParaAsociarById`, `crm.service.ContratoReferenciaService` | El `record ContratoReferencia(UUID id, UUID clienteId, String codigo, String estado)` convierte con `desde(Contrato)` tomando `clienteId` del proxy `LAZY` sin inicializarlo y `estado` con `getEstado().name()`; `activo()` compara con `EstadoContrato.ACTIVO.name()`. `findParaAsociarById` usa `@Lock(PESSIMISTIC_READ)` y no hace *fetch join*. El servicio es `@Transactional(readOnly = true)` con `buscar`, `buscarTodos` (una consulta con `findAllById`; colección vacía devuelve mapa vacío sin consultar) y `buscarParaAsociar` con `Propagation.MANDATORY`. `ContratoService`, los controladores y los endpoints del CRM no cambian; el CRM no importa nada de `sga` ni de `erp` |
| 3.2 | `crm/service/ContratoReferenciaServiceTests` | `.\mvnw.cmd -B -Dtest=ContratoReferenciaServiceTests test` → **10 pruebas, 0 fallos, 0 errores, 0 omitidas, BUILD SUCCESS**. Cubre `buscar` con contrato existente (id, `clienteId`, `codigo` y `estado`) e inexistente; `activo()` en los cuatro `EstadoContrato` con `@EnumSource`; `buscarTodos` con colección vacía sin consultar (`verify(..., never())`), ids mezclados, repetidos e inexistentes en una sola consulta; `buscarParaAsociar` dentro de `TransactionTemplate` y fuera de transacción lanza `IllegalTransactionStateException`. Regresión: `.\mvnw.cmd -B clean test` → **1059 pruebas, 0 fallos, 0 errores, 0 omitidas, BUILD SUCCESS** |

Alcance respetado:

- Solo se tocó el CRM en la puerta de lectura nueva; no se modificaron `ContratoService`, sus controladores ni sus endpoints, y no se importó nada del SGA ni del ERP.
- El repositorio conserva sus consultas con bloqueo sin *fetch join* (D9).
- Sin credenciales, tokens, UUID ni datos personales en este registro.

Commits: `461c48c` (`feat(crm)` para 3.1 y 3.2).

### Catálogo de conflictos y DTO del SGA (tareas 6.1–6.7)

| Tarea | Entregable | Verificación |
|---|---|---|
| 6.1 | `sga/service/ConflictosSga` | Clase `final` con las doce fábricas del catálogo: `dronCodigoDuplicado`, `paqueteCodigoDuplicado`, `articuloCodigoDuplicado`, `clienteInactivo`, `contratoDeOtroCliente`, `contratoNoActivo(String)`, `paqueteNoEditable`, `transicionNoPermitida`, `dronNoDisponible(EstadoDron, String campo)`, `dronConPaqueteEnCurso(UUID, String campo)`, `transicionDronNoPermitida` y `existenciaInsuficiente`. Cada una fija el código, el `campo`, el `detail` exacto del catálogo y las extensiones (`estadoActual`, `estadoSolicitado`, `paqueteId`, `existenciaActual`, `cantidadSolicitada`), sin nombres, direcciones ni observaciones. `TRANSICION_DRON_NO_PERMITIDA` usa `campo = "estado"`; `DRON_NO_DISPONIBLE` y `DRON_CON_PAQUETE_EN_CURSO` reciben el `campo` del llamador (`"dronId"` al asignar y `null` en `EN_TRANSITO` y en el cambio manual). Constantes propias, también `TRANSICION_NO_PERMITIDA` |
| 6.2 | `sga/dto/validacion/CantidadEntera` | Anotación sobre `BigDecimal` con `min` y `max = 1_000_000`; acepta `null`; informa "debe ser un número entero entre {min} y {max}" si `stripTrailingZeros().scale() > 0` o el valor está fuera de rango. El predicado estático `esEnteroEnRango(BigDecimal, long, long)` se expone en `CantidadEntera.Validador` (un tipo anotación de Java no admite métodos estáticos; el diseño lo escribía como `CantidadEntera.esEnteroEnRango`) y lo reutiliza `@MovimientoInventarioValido` |
| 6.3 | DTO de drones | `DronRequest` (normaliza código, modelo y observaciones; `@NotNull` separado del formato; patrón de código; `@Size` 100/500; `toString()` protegido), `EstadoDronRequest`, `PaqueteEnCursoRef` con `desde(Paquete)` nulo-seguro y `DronResponse` con los once campos de Requirement 5.7 y `desde(Dron, Paquete)` |
| 6.4 | DTO de paquetes e historial y `@DronSegunEstado` | `PaqueteCrearRequest` (código, descripción, dirección, `clienteId`, `contratoId` y `observacion`; normalización y `toString()` protegido), `PaqueteActualizarRequest` sin `codigo`, `clienteId`, `estado` ni `dronId`, y `TransicionPaqueteRequest` con `@DronSegunEstado`: con `ASIGNADO` y sin dron informa "es obligatorio para asignar el paquete"; con otro estado y dron informa "solo se admite en la transición a ASIGNADO"; ambos en el nodo `dronId` y sin informar si `estado` es `null`. `ClienteAsociado`, `ContratoAsociado` y `DronAsociado` (los dos últimos nulo-seguros), `DronRef`, `PaqueteResponse.desde(...)` con `Objects.requireNonNull` sobre las referencias cuando el paquete tiene cliente o contrato y `RegistroHistorialResponse.desde(...)`; `toString()` protegido en `PaqueteResponse` y `RegistroHistorialResponse` |
| 6.5 | DTO de inventario y del resumen | `ArticuloRequest` con `stockMinimo` `BigDecimal` y `@CantidadEntera(min = 0)`, categoría y unidad obligatorias; `ArticuloResponse` con los once campos de Requirement 14.6 y `bajoStockMinimo` desde la entidad; `MovimientoInventarioRequest` con `cantidadEntera()` y `@MovimientoInventarioValido` (rango según `tipo.cantidadMinima()`, `0` si `tipo` es `null`, y "es obligatoria en un ajuste" en `observacion`, un error por campo); `MovimientoInventarioResponse`; `toString()` protegido en ambos. `ResumenSgaResponse` con `ConteoEstados` (`EnumMap` completo e inmutable) y `ResumenArticulos`, y `desde(...)` puro que crea los ceros, copia las filas y calcula cada total |
| 6.6 | `sga/dto/SgaValidacionTests` | `.\mvnw.cmd -B "-Dtest=SgaValidacionTests,ResumenSgaResponseTests" test` → **25 pruebas, 0 fallos, 0 errores, 0 omitidas, BUILD SUCCESS**. Property 8 con los bordes y 120 valores generados para `stockMinimo` y para la cantidad de los tres tipos; `1.5` rechazado sin truncar y `5`, `5.0` y `5E0` aceptados como 5; una sola entrada por campo en artículos y movimientos; mensajes en español; `@DronSegunEstado` en los 6 estados × `dronId` presente o ausente; normalización y `toString()` protegido |
| 6.7* | `sga/dto/ResumenSgaResponseTests` | Property 9 con 120 conteos generados (con estados ausentes) comparados con el cálculo directo; caso sin filas con los seis y cuatro estados en 0; el mapa de estados no se puede modificar |

Alcance respetado:

- Solo se crearon clases nuevas en `sga.service`, `sga.dto` y `sga.dto.validacion`; no se modificaron entidades, repositorios ni servicios de Davidson, ni el CRM, el ERP o `001-foundation`.
- El CRM y el ERP no importan nada del SGA; el SGA solo consume `ClienteReferencia` y `ContratoReferencia` del CRM.
- Sin credenciales, tokens, UUID ni datos personales en este registro.

Regresión: `.\mvnw.cmd -B clean test` → **1084 pruebas, 0 fallos, 0 errores, 0 omitidas, BUILD SUCCESS**.

Commits: `65de4c5` (`feat(sga)` para 6.1–6.7).

### Pruebas de integración de la migración del SGA (tareas 14.1 y 14.2)

| Tarea | Entregable | Verificación |
|---|---|---|
| 14.1 | `integration/PostgresIntegracionBase` | El `truncate` de `@BeforeEach` incluye, en este orden y con `cascade`, `app.movimientos_inventario`, `app.articulos_inventario`, `app.historial_estados_paquete`, `app.paquetes` y `app.drones`, antes de las tablas del ERP, del CRM, `app.perfiles_usuario` y `auth.users`. No cambian `MigracionesSupabase`, `PostgresContenedorConfig`, el Script_Preparacion_IT ni `pom.xml` |
| 14.2 | `integration/MigracionesSgaIT` | Nueve pruebas sobre PostgreSQL 17 efímero con las migraciones reales: `ddl-auto=validate` arranca con las entidades del SGA; las cinco tablas quedan vacías y ninguna migración inserta datos; `information_schema` confirma `uuid`, `integer`, `bigint`, `smallint`, `timestamptz` y `text`; SQLState 23505 para códigos duplicados en drones, paquetes y artículos, segundo paquete en curso con el mismo dron y `secuencia` repetida; 23514 para estados, categorías, unidades, formato y longitudes, existencia negativa, cantidad fuera de rango por tipo, aritmética de `ENTRADA`/`SALIDA`/`AJUSTE`, `AJUSTE` sin observación, `secuencia` fuera de 1–4, dron incoherente con el estado del paquete, dron en un registro que no es `ASIGNADO` y alta sin `REGISTRADO`; 23503 para las FK a clientes, contratos, drones, paquetes y artículos; dos paquetes `CANCELADO` con el mismo dron se aceptan; RLS activo y sin privilegios para `anon` y `authenticated` en las cinco tablas |

Verificación: `.\mvnw.cmd -B clean verify "-Dit.test=MigracionesSgaIT"` desde `backend/` → **1084 pruebas unitarias y 9 de integración, 0 fallos, 0 errores, 0 omitidas, BUILD SUCCESS** (PostgreSQL 17.10 en contenedor, `ddl-auto=validate` sobre el esquema migrado, sin credenciales ni red hacia Supabase).

Alcance respetado:

- Solo se amplió la base común de IT y se añadió una clase nueva; no se tocaron entidades, repositorios ni servicios del SGA, ni el CRM, el ERP o `001-foundation`.
- La migración no se aplicó a `arise-corp-dev`: sigue pendiente la tarea 15.1 de Davidson, tras esta validación.
- Sin credenciales, tokens, UUID personales ni datos personales en este registro.

Commits: `645bd61` (`test(sga)` para 14.1–14.2).

### Controladores del SGA y matriz de autorización (tareas 11.1–11.5)

| Tarea | Entregable | Verificación |
|---|---|---|
| 11.1 | `sga/controller/DronController` | `GET`, `GET /{id}`, `POST` (201 con `Location: /api/sga/drones/{id}`), `PUT /{id}` y `PATCH /{id}/estado`; `q` con `@Size(100)`, `estado` como `EstadoDron`, `asignable` como `Boolean`, `page`/`size` con sus mínimos y máximos y `sort` `(codigo|modelo|creadoEn),(asc|desc)` por defecto `codigo,asc`, todos en los `@RequestParam` y sin `@Validated` de clase. El actor sale de `@AuthenticationPrincipal Jwt` y no hay `DELETE` mapeado |
| 11.2 | `sga/controller/ArticuloController` | `GET`, `GET /{id}`, `POST` (201), `PUT /{id}`, `GET /{id}/movimientos` (`sort` solo `registradoEn`, por defecto `registradoEn,desc`) y `POST /{id}/movimientos` (201 con `Location: /api/sga/articulos/{id}/movimientos`); filtros `q`, `categoria` y `bajoStockMinimo` y orden `(codigo|nombre|existencia|creadoEn),(asc|desc)` por defecto `nombre,asc`; sin `DELETE` mapeado |
| 11.3 | `sga/controller/ResumenSgaController` | `GET /api/sga/resumen` sin parámetros, delega en `ResumenSgaService.obtener()` |
| 11.4 | `sga/controller/PaqueteController` y retirada de `AutorizacionModulosTests` | `GET`, `GET /{id}`, `POST` (201), `PUT /{id}`, `GET /{id}/estados` como arreglo y `POST /{id}/estados` (201 con `Location: /api/sga/paquetes/{id}/estados`); filtros `estado`, `clienteId`, `contratoId`, `dronId` y `q`, y `sort` `(codigo|creadoEn|estadoActualizadoEn),(asc|desc)` por defecto `creadoEn,desc`; sin `DELETE` mapeado. Se eliminó `login/controller/AutorizacionModulosTests.java`, cuyo doble `GET /api/sga/paquetes` provocaba un mapeo ambiguo al arrancar el contexto, en el mismo commit que el controlador real (D16) |
| 11.5 | `sga/controller/SgaAutorizacionTests` | 147 casos parametrizados: las 21 operaciones (18 endpoints y los tres `DELETE`) × siete identidades (sin token, sin perfil, perfil inactivo y los cuatro roles) con `SgaDatosPrueba` y el `PerfilJwtAuthenticationConverter` real sobre perfiles persistidos en H2. Sin token responde 401 con `WWW-Authenticate`; los denegados responden 403 sin cambios en las cinco tablas y también con un `{id}` inexistente; `DELETE` con ADMINISTRADOR u OPERACIONES responde 405 con `Allow` y conserva el registro; las demás operaciones responden 200 o 201 para los dos roles gestores. Recoge los seis casos de la clase retirada |

Verificación: `.\mvnw.cmd -B "-Dtest=SgaAutorizacionTests" test` desde `backend/` → **147 pruebas, 0 fallos, 0 errores, 0 omitidas, BUILD SUCCESS**. Regresión: `.\mvnw.cmd -B clean test` → **1389 pruebas, 0 fallos, 0 errores, 0 omitidas, BUILD SUCCESS**, sin Docker, Supabase ni `.env`.

Alcance respetado:

- Solo se añadieron los cuatro controladores y la prueba de autorización; no se modificaron los servicios, entidades ni repositorios del SGA de Davidson, ni el CRM, el ERP o `001-foundation`.
- Las rutas, los parámetros, los códigos de estado, las `Location` y los roles siguen los contratos de `design.md`; las lecturas de OPERACIONES sobre el CRM no se duplican (interpretación 14).
- Sin credenciales, tokens, UUID ni datos personales en este registro.

Commits: `f28f47a` (`feat(sga)` para 11.1–11.5).

### Pruebas de controlador y de logs del SGA (tareas 12.1–12.4)

| Tarea | Entregable | Verificación |
|---|---|---|
| 12.1 | `sga/controller/DronControllerTests` | 10 pruebas con MockMvc y H2: alta con 201 y `Location`, campos del servidor ignorados (`id`, `estado`, `creadoEn`, `actualizadoPor`), 400 de `DronRequest` por `codigo`/`modelo`/`observaciones`, 400 de `EstadoDronRequest` con `estado` desconocido y el mensaje de las cuatro constantes, 400 por `id` no UUID y por `page`, `size`, `sort`, `q`, `estado` y `asignable`, 404 de `GET`/`PUT`/`PATCH`, 409 `DRON_CODIGO_DUPLICADO` con el código escrito con espacios y minúsculas en el alta y en la edición, 409 `TRANSICION_DRON_NO_PERMITIDA` con `campo`, `estadoActual` y `estadoSolicitado` (desde `EN_MISION` y con el mismo estado), 409 `DRON_CON_PAQUETE_EN_CURSO` con `paqueteId`, `paqueteEnCurso` en lista y detalle, cambio manual con la auditoría del estado sin tocar `actualizadoEn`, edición que conserva estado y `creadoEn`, los once campos de la representación y 405 de `DELETE` con `Allow` |
| 12.2 | `sga/controller/PaqueteControllerTests` | 10 pruebas: alta con 201 y `Location` que ignora `id`, `estado`, `dronId`, `creadoEn` y `actualizadoPor`; alta con contrato activo y filtros de lista; 400 de `PaqueteCrearRequest`, `PaqueteActualizarRequest` y `TransicionPaqueteRequest` (incluidos `dronId` obligatorio al asignar y prohibido en otro estado) y de los parámetros de lista; 404 de la ruta, del historial y de `clienteId`, `contratoId` y `dronId` con `campo`; un caso por cada uno de los ocho conflictos con `conflicto`, `campo` y extensiones (`PAQUETE_CODIGO_DUPLICADO`, `CLIENTE_INACTIVO`, `CONTRATO_DE_OTRO_CLIENTE`, `CONTRATO_NO_ACTIVO`, `PAQUETE_NO_EDITABLE`, `TRANSICION_NO_PERMITIDA`, `DRON_NO_DISPONIBLE` y `DRON_CON_PAQUETE_EN_CURSO`); transición a `ASIGNADO` con 201 y `Location` de la colección; historial como arreglo ordenado por secuencia con el dron solo en `ASIGNADO`; cliente con solo `id`, `nombre` y `activo` releído del CRM en cada consulta; 405 de `DELETE` y de `PUT`, `PATCH` y `DELETE` sobre `.../estados` con `Allow`; los `detail` no contienen dirección, observación ni nombre del cliente; los trece campos de la representación |
| 12.3 | `sga/controller/ArticuloControllerTests` | 8 pruebas: alta con 201 y `Location` que ignora `id`, `existencia`, `creadoEn` y `actualizadoPor`; filtros por `bajoStockMinimo`, `categoria` y `q`; 400 de `ArticuloRequest` (`codigo`, `nombre`, `categoria`, `unidad`, `stockMinimo` con `1.5`, `-1` y `1 000 001`) y de `MovimientoInventarioRequest` (`cantidad: 1.5`, `SALIDA` con 0, `AJUSTE` sin observación, `observacion` de 501 y `tipo` ausente) y de los parámetros de lista; 404 de la ruta y de los movimientos; 409 `ARTICULO_CODIGO_DUPLICADO` con el código normalizado y `EXISTENCIA_INSUFICIENTE` con `campo = "cantidad"`, `existenciaActual` y `cantidadSolicitada`, sin tocar la existencia; movimiento con 201 y `Location` de la colección, `existenciaAnterior`/`existenciaResultante` y `bajoStockMinimo` recalculado; 405 de `DELETE` y de `PUT`, `PATCH` y `DELETE` sobre `.../movimientos` con `Allow`; los once campos del artículo y los nueve del movimiento |
| 12.4 | `sga/SgaLogsTests` | Con `OutputCaptureExtension` y `@MockitoBean ContratoReferenciaService` cuyo `buscarParaAsociar` lanza una excepción sin datos: alta y edición de un paquete sin contrato, 409 de código duplicado y 500 con un alta que lleva `contratoId` hasta el doble, con una dirección, una observación y un nombre de cliente ficticios conocidos. La salida capturada no contiene ninguno de esos valores |

Verificación: `.\mvnw.cmd -B "-Dtest=DronControllerTests,PaqueteControllerTests,ArticuloControllerTests,SgaLogsTests" test` desde `backend/` → **29 pruebas, 0 fallos, 0 errores, 0 omitidas, BUILD SUCCESS**. Regresión: `.\mvnw.cmd -B clean test` → **1418 pruebas, 0 fallos, 0 errores, 0 omitidas, BUILD SUCCESS**, sin Docker, Supabase ni `.env`.

Alcance respetado:

- Solo se añadieron las cuatro clases de prueba; no se modificaron los controladores, servicios, entidades ni repositorios del SGA de Davidson, ni el CRM, el ERP o `001-foundation`.
- Las pruebas usan `SgaDatosPrueba` y `CrmDatosPrueba` con el `PerfilJwtAuthenticationConverter` real sobre H2; los datos son ficticios y no hay tokens ni credenciales.
- Sin credenciales, tokens, UUID personales ni datos personales en este registro.

### Checkpoint del backend sin Docker (tarea 13)

Verificación: `.\mvnw.cmd -B clean test` desde `backend/` → **1418 pruebas, 0 fallos, 0 errores, 0 omitidas, BUILD SUCCESS**, con H2 en memoria, sin Docker, sin Supabase, sin `.env` y sin red. Las suites de `001-foundation`, `002-crm-clientes-contratos` y `003-erp-empleados-proveedores-finanzas` quedan en verde, incluidas `CrmAutorizacionTests` (119 pruebas) y `ClienteControllerTests` (62 pruebas), que no se modificaron en esta rama.

Alcance respetado:

- No se cambió código de producción ni pruebas de los módulos anteriores; el checkpoint solo ejecuta y registra la regresión.
- Sin credenciales, tokens ni datos personales en este registro.

### Pruebas de integración del SGA con PostgreSQL efímero (tareas 14.3 y 14.4)

| Tarea | Entregable | Verificación |
|---|---|---|
| 14.3 | `integration/FlujoSgaIT` | 4 pruebas por MockMvc contra PostgreSQL 17 efímero, con perfiles COMERCIAL y OPERACIONES persistidos y el `PerfilJwtAuthenticationConverter` real. Flujo completo: cliente y contrato creados por COMERCIAL y activado el contrato, dron y paquete asociados creados por OPERACIONES, transiciones `ASIGNADO` → `EN_TRANSITO` → `ENTREGADO`, dron `DISPONIBLE` → `DISPONIBLE` → `EN_MISION` → `DISPONIBLE`, historial de cuatro registros encadenados con el `sub` del actor y el dron solo en `ASIGNADO`, y conteos del resumen. Recorridos `EN_TRANSITO → DEVUELTO` y `ASIGNADO → CANCELADO` con el dron de nuevo asignable. Filtros de paquetes por `estado`, `clienteId` y `dronId`; `asignable` con los cuatro estados de dron con y sin paquete en curso (solo el `DISPONIBLE` sin paquete resulta asignable) y `bajoStockMinimo` con existencia menor, igual y mayor que el mínimo y mínimo 0. El nombre del cliente actualizado por el CRM se refleja en el detalle del paquete |
| 14.4 | `integration/SgaTransaccionIT` | 4 pruebas: un `TransactionTemplate` que llama a `PaqueteService.cambiarEstado` (`ASIGNADO → EN_TRANSITO`) y después lanza una excepción conserva el estado `ASIGNADO`, los dos registros del historial y el dron `DISPONIBLE`; lo mismo con `MovimientoInventarioService.registrar` y la existencia 10 con un solo movimiento. Con dos hilos y un `CountDownLatch`, dos asignaciones del mismo dron a paquetes distintos dejan un 201 y un 409 de dron (`DRON_CON_PAQUETE_EN_CURSO` o `DRON_NO_DISPONIBLE`), un solo paquete en curso y el otro en `REGISTRADO`; dos `SALIDA` de 7 sobre una existencia de 10 dejan un 201 y un 409 `EXISTENCIA_INSUFICIENTE`, existencia final 3 y una sola salida registrada |

Verificación: `.\mvnw.cmd -B verify "-Dit.test=FlujoSgaIT,SgaTransaccionIT"` desde `backend/` → **8 pruebas de integración, 0 fallos, 0 errores, 0 omitidas, BUILD SUCCESS** con Docker y `postgres:17-alpine`. Regresión completa: `.\mvnw.cmd -B verify` → **1418 pruebas unitarias y 52 pruebas de integración, 0 fallos, 0 errores, 0 omitidas, BUILD SUCCESS**, incluidas `MigracionesSgaIT`, `FlujoSgaIT` y `SgaTransaccionIT` en la misma ejecución de Failsafe.

Alcance respetado:

- Solo se añadieron `FlujoSgaIT` y `SgaTransaccionIT`; no se modificaron las entidades, servicios, repositorios, controladores ni migraciones del SGA, ni los módulos anteriores.
- Las IT usan el PostgreSQL efímero de `PostgresContenedorConfig` y todas las migraciones de `supabase/migrations/`, sin copiarlas, sin red hacia Supabase y sin `.env` ni credenciales de `arise-corp-dev`.
- Sin credenciales, tokens, UUID personales ni datos personales en este registro.

### Steering de API y seguridad del SGA (tarea 17.3)

| Archivo | Cambio |
|---|---|
| `.kiro/steering/api-conventions.md` | Doce filas nuevas en "Endpoints existentes" para `/api/sga/drones`, `/api/sga/paquetes`, `/api/sga/paquetes/{id}/estados`, `/api/sga/articulos`, `/api/sga/articulos/{id}/movimientos` y `/api/sga/resumen`, con los roles Administrador y Operaciones y la respuesta de éxito de cada uno. Un bloque con los doce códigos de conflicto del SGA (`DRON_CODIGO_DUPLICADO`, `PAQUETE_CODIGO_DUPLICADO`, `ARTICULO_CODIGO_DUPLICADO`, `CLIENTE_INACTIVO`, `CONTRATO_DE_OTRO_CLIENTE`, `CONTRATO_NO_ACTIVO`, `PAQUETE_NO_EDITABLE`, `TRANSICION_NO_PERMITIDA`, `DRON_NO_DISPONIBLE`, `DRON_CON_PAQUETE_EN_CURSO`, `TRANSICION_DRON_NO_PERMITIDA`, `EXISTENCIA_INSUFICIENTE`) con su `campo` y sus extensiones. `campo` en los 404 de `clienteId`, `contratoId` y `dronId`. Lista vacía en `GET /api/sga/paquetes` cuando un filtro no tiene registro. 405 con `Allow` (`GET`, `PUT`) en los `DELETE` de drones, paquetes y artículos y con `Allow` (`GET`, `POST`) en los métodos no mapeados de `/estados` y `/movimientos` |
| `.kiro/steering/security.md` | `/api/sga/**` sin lecturas cruzadas, con una sola representación para Administrador y Operaciones. El SGA lee clientes y contratos solo mediante `ClienteReferenciaService` y `ContratoReferenciaService`, dentro de su transacción y sin acceso a entidades ni repositorios del CRM. Dirección de entrega, observaciones y nombre del cliente fuera de logs y de `detail`; `toString()` protegido en los DTO del paquete, del historial y de los movimientos, con `SgaLogsTests` como verificación |

Verificación: revisión de los dos archivos contra `ConflictosSga`, los cuatro controladores del SGA, `SecurityConfig` y `SgaLogsTests`; `git diff --check` sin espacios sobrantes. No se ejecutaron pruebas porque la tarea solo modifica documentación de steering.

Alcance respetado:

- Solo se modificaron `api-conventions.md`, `security.md` y este registro; no se tocó código de producción ni de pruebas.
- No se añadieron dependencias, variables de entorno ni secretos, y no se copiaron datos de `.env`.
- Los ejemplos usan códigos y estados ficticios; sin credenciales, tokens ni datos personales.

### Verificación de la imagen y Compose (tarea 16.1)

| Comprobación | Resultado |
|---|---|
| `docker build -t arise-backend:local backend` | Imagen construida sin credenciales ni conexión a Supabase, con la etapa Maven y `-Dmaven.test.skip=true`; `arise-backend:local`, 591 MB |
| `docker run --rm --entrypoint sh arise-backend:local -c "find / -name '.env*' 2>/dev/null"` | Sin resultados: la imagen no contiene `.env` ni `.env.*` |
| `docker history --no-trunc arise-backend:local` | Solo aparecen los `ENV` de la imagen base (`JAVA_VERSION`, `LANG`, `PATH`, `JAVA_HOME`) y los comandos del `Dockerfile`; ningún valor de configuración de la aplicación |
| `docker image inspect arise-backend:local` | `User=arise` (uid y gid 999, sin privilegios) y `Config.Env` con las cinco variables de la imagen base |
| Contenido de `/app` | Solo `app.jar`; sin `src/test`, sin `target` y sin archivos de configuración local |
| `git diff main -- backend/Dockerfile backend/.dockerignore docker-compose.yml` | Sin cambios (D22) |
| `docker compose up -d backend` con `backend/.env` y la migración aplicada | Contenedor `arise-corp-backend-1` en ejecución; el backend arrancó con `ddl-auto=validate` contra `arise-corp-dev` |
| `curl -i http://localhost:8080/api/sga/drones` | `HTTP/1.1 401`, `WWW-Authenticate: Bearer`, `Content-Type: application/problem+json;charset=UTF-8` y cuerpo Problem Details, sin token |
| `docker compose down` | Contenedor y red `arise-corp_default` eliminados; sin servicios activos |

Alcance respetado:

- No se abrió, leyó ni mostró `backend/.env`; Compose lo inyecta y el build no recibe credenciales.
- No se modificaron `backend/Dockerfile`, `backend/.dockerignore` ni `docker-compose.yml`.
- Sin credenciales, tokens ni valores de configuración en este registro.

### Protección de `main` (tarea 32.2)

Comprobación del 2026-11-23 con la API de GitHub (`GET /repos/SebastianUD/arise-corp/branches/main/protection`), usando la credencial del almacén local de Git Credential Manager sin escribirla ni mostrarla:

| Ajuste | Valor comprobado |
|---|---|
| Pull request obligatorio | Sí, con **1 aprobación** |
| Rama actualizada antes de integrar | `strict = true` |
| Checks obligatorios | `backend-ci`, `integration-tests`, `frontend-ci` |
| Conversaciones resueltas | Sí |
| Reglas aplicadas a administradores | Sí (`enforce_admins = true`) |
| Force push y borrado de `main` | Deshabilitados |
| Merges permitidos | Solo *squash* (`allow_merge_commit` y `allow_rebase_merge` en `false`) |
| Bypass y restricciones de descarte | Sin excepciones configuradas |

Resultado: la protección sigue activa tal como se configuró en `002-crm-clientes-contratos`; no hubo que restablecer nada.

Alcance respetado:

- Solo se consultó la configuración; no se modificaron reglas, workflows ni el repositorio.
- La credencial se usó desde el almacén local y no se escribió, mostró ni registró.
- Sin tokens, credenciales ni UUID en este registro.



