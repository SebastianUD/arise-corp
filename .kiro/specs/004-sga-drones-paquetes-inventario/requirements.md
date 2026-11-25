# Requirements Document

## Introduction

Esta es la cuarta especificación del proyecto Arise Corp. (`004-sga-drones-paquetes-inventario`). Construye el módulo SGA (drones, paquetes con su historial de estados, inventario con movimientos controlados e indicadores) con las mismas convenciones del CRM de `002-crm-clientes-contratos` y del ERP de `003-erp-empleados-proveedores-finanzas` (`architecture.md`, "Orden de construcción", pasos 5 y 6). Los paquetes se asocian a clientes y contratos del CRM y a drones del propio SGA, y con ellos se cierra el flujo integrado del MVP: cliente → contrato → paquete → cambio de estado y trazabilidad (MVP §6.6, CA-05). La spec reutiliza la infraestructura existente (Docker, Docker Compose, Testcontainers y los tres workflows de CI) y amplía sus pruebas solo con los casos necesarios para las reglas de negocio del SGA.

Requisitos del MVP cubiertos:

- **Must:** SGA-01 (drones), SGA-02 (paquetes), SGA-03 (inventario con operaciones controladas), SGA-04 (historial de estados del paquete) y SGA-05 (asociación paquete–cliente/contrato–dron).
- **Should incluido por decisión del equipo:** SGA-06 (indicadores de paquetes, drones e inventario).
- **No funcionales:** NF-01 a NF-07.
- **Criterios globales:** CA-01, CA-02 (rechazo de tokens inválidos en los endpoints SGA), CA-03, CA-04 (operaciones mínimas del SGA), CA-05 (flujo integrado completo), CA-06 y CA-07.
- **Ingeniería:** ENG-01 (la imagen Docker sigue construyendo con el SGA), ENG-02 (pruebas unitarias), ENG-03 (pruebas de integración) y ENG-04 (CI con los workflows existentes).
- **Autenticación y errores de interfaz:** AUTH-03 (token de la sesión en las peticiones SGA) y AUTH-05 aplicados a las vistas SGA; AUTH-01, AUTH-02, CRM-01, CRM-03 y ERP-01 a ERP-05 solo como no regresión.

Decisiones aplicadas: DP-PRO-01, DP-PRO-02, DP-ARQ-01, DP-ARQ-02, DP-SEG-02, DP-SEG-05, DP-SEG-06, DP-API-01 a DP-API-06, DP-TEC-02, DP-TEC-03, DP-TEC-04, DP-EST-01 a DP-EST-05 y DP-SDD-03 a DP-SDD-05.

### Contexto de trabajo

- `main` contiene `001-foundation`, `002-crm-clientes-contratos` y `003-erp-empleados-proveedores-finanzas` integradas. El trabajo se realiza en la rama `feat/sga-drones-paquetes-inventario`, creada desde `main`, con un único Pull Request al completar la spec (DP-SDD-05).
- La matriz de `SecurityConfig` ya cubre `/api/sga/**` para ADMINISTRADOR y OPERACIONES y no cambia; COMERCIAL y FINANZAS_RRHH reciben 403 en todo el SGA. Las lecturas cruzadas que necesita SGA-05 ya existen: OPERACIONES lee `GET /api/crm/clientes` y `GET /api/crm/clientes/*` con la Vista_Reducida_Cliente, y `GET /api/crm/contratos` y `GET /api/crm/contratos/*`.
- El CRM ya ofrece `crm.service.ClienteReferenciaService` (`buscar`, `buscarTodos` y `buscarParaAsociar`, este último con bloqueo de lectura y transacción obligatoria). No existe una lectura equivalente de contratos para otros módulos: el diseño añade el Servicio_Contratos_CRM y lo registra en `architecture.md` (DP-ARQ-01) antes de programarlo.
- `AutorizacionModulosTests` conserva el endpoint de prueba `GET /api/sga/paquetes`, que chocará con el controlador real de paquetes. Sus casos se migran a pruebas de los controladores reales (`SgaAutorizacionTests`) en el mismo cambio que crea ese controlador, con el procedimiento de la decisión D16 de `003-erp-empleados-proveedores-finanzas`.
- El frontend no tiene funciones de permisos ni rutas del SGA: `frontend/src/lib/permisos.js` necesita `puedeGestionarSga`, las opciones de menú SGA y las transiciones del paquete y del dron, y `App.jsx` necesita un grupo `RolRoute` para las rutas `/sga/*`.
- `backend/Dockerfile`, `backend/.dockerignore`, `docker-compose.yml`, la base de pruebas de integración (`PostgresIntegracionBase`, `MigracionesSupabase` y el Script_Preparacion_IT) y los workflows `backend-ci.yml`, `integration-tests.yml` y `frontend-ci.yml` ya existen. Esta spec los reutiliza y solo los modifica si `design.md` justifica el cambio.
- La Migracion_SGA la prepara Davidson, la revisa otro integrante y Davidson la aplica de forma controlada a la Base_Compartida antes de la validación manual, según DP-TEC-03. Su timestamp es posterior a `20261118095500`, la última migración aplicada.
- No se prevén variables de entorno ni dependencias nuevas en backend ni en frontend.
- **Estrategia de pruebas acotada (decisión de esta spec):** las pruebas nuevas se concentran en las reglas de negocio del SGA (máquinas de estado, sincronización dron–paquete, asociación con el CRM, historial e inventario), en la matriz de acceso que `security.md` exige por endpoint y en las restricciones de la Migracion_SGA. No se repiten pruebas de componentes compartidos que `002` y `003` ya cubren (normalización, paginación, restricciones compartidas y traducción genérica de errores), ni se generan combinaciones masivas de filtros.

### Fuera de alcance

Control autónomo de vuelo, navegación, rutas, telemetría o conexión con drones físicos (MVP §10); capacidad de carga, peso, dimensiones o coordenadas de los paquetes; reasignación o desasignación del dron de un paquete `ASIGNADO`; historial de estados del dron aparte de la auditoría de su último cambio; mantenimiento programado de drones; relación del inventario con los paquetes o consumo automático de artículos; valoración o costos del inventario, lotes, bodegas, ubicaciones, números de serie y reservas; relación del inventario con los proveedores del ERP; edición o eliminación de movimientos de inventario y de registros del historial; desactivación de artículos; eliminación física de drones, paquetes o artículos; notificaciones al cliente, prueba o firma de entrega; funcionalidades nuevas de CRM o ERP; despliegue continuo y publicación de imágenes en registros externos.

### Endpoints y matriz de acceso del SGA

| Endpoint | ADMINISTRADOR | COMERCIAL | FINANZAS_RRHH | OPERACIONES |
|---|---|---|---|---|
| `GET /api/sga/drones`, `GET /api/sga/drones/{id}` | Permitido | 403 | 403 | Permitido |
| `POST /api/sga/drones`, `PUT /api/sga/drones/{id}`, `PATCH /api/sga/drones/{id}/estado` | Permitido | 403 | 403 | Permitido |
| `GET /api/sga/paquetes`, `GET /api/sga/paquetes/{id}` | Permitido | 403 | 403 | Permitido |
| `POST /api/sga/paquetes`, `PUT /api/sga/paquetes/{id}` | Permitido | 403 | 403 | Permitido |
| `GET /api/sga/paquetes/{id}/estados`, `POST /api/sga/paquetes/{id}/estados` | Permitido | 403 | 403 | Permitido |
| `GET /api/sga/articulos`, `GET /api/sga/articulos/{id}` | Permitido | 403 | 403 | Permitido |
| `POST /api/sga/articulos`, `PUT /api/sga/articulos/{id}` | Permitido | 403 | 403 | Permitido |
| `GET /api/sga/articulos/{id}/movimientos`, `POST /api/sga/articulos/{id}/movimientos` | Permitido | 403 | 403 | Permitido |
| `GET /api/sga/resumen` | Permitido | 403 | 403 | Permitido |
| `DELETE /api/sga/drones/{id}`, `DELETE /api/sga/paquetes/{id}`, `DELETE /api/sga/articulos/{id}` | 405 | 403 | 403 | 405 |
| `GET /api/crm/clientes`, `GET /api/crm/clientes/{id}` (existentes; selector de cliente) | Vista completa | Vista completa | Vista reducida | Vista reducida |
| `GET /api/crm/contratos`, `GET /api/crm/contratos/{id}` (existentes; selector de contrato) | Permitido | Permitido | 403 | Permitido |

### Supuestos adoptados

Los cinco primeros temas los decidió la responsable del proyecto en la aclaración de la spec. El resto son decisiones de este documento que completan esos acuerdos.

| Tema | Supuesto |
|---|---|
| Ciclo de vida del paquete (decidido) | Nace en `REGISTRADO`. Transiciones: `REGISTRADO→ASIGNADO` (con dron), `ASIGNADO→EN_TRANSITO`, `EN_TRANSITO→ENTREGADO`, `REGISTRADO→CANCELADO`, `ASIGNADO→CANCELADO` y `EN_TRANSITO→DEVUELTO` (entrega fallida). `ENTREGADO`, `CANCELADO` y `DEVUELTO` son finales. El estado cambia solo con `POST /api/sga/paquetes/{id}/estados` y el historial se consulta con `GET` en la misma ruta (DP-API-05); `PUT` no cambia el estado. |
| Historial del paquete (decidido) | Cada cambio, incluido el alta, crea un Registro_Historial con el estado anterior, el estado nuevo, el `sub` del Actor, el instante UTC y una observación opcional, en la misma transacción que el cambio (SGA-04, DP-SEG-06). El registro del alta tiene estado anterior nulo. Los registros no se editan ni se eliminan. |
| Asociación con el CRM (decidido) | El cliente es obligatorio, debe existir y estar activo. El contrato es opcional; si se indica, debe existir, pertenecer a ese cliente y estar en `ACTIVO`. Se admiten paquetes sin contrato. |
| Dron (decidido) | Estados `DISPONIBLE`, `EN_MISION`, `MANTENIMIENTO` y `FUERA_DE_SERVICIO`. Solo se asigna un dron `DISPONIBLE` sin otro Paquete_En_Curso. El dron pasa a `EN_MISION` cuando su paquete pasa a `EN_TRANSITO` y vuelve a `DISPONIBLE` cuando el paquete se entrega, se devuelve o se cancela, en la misma transacción. `MANTENIMIENTO` y `FUERA_DE_SERVICIO` se fijan a mano solo cuando el dron no tiene Paquete_En_Curso. |
| Inventario (decidido) | Catálogo de artículos con código, nombre, categoría, unidad y stock mínimo. La existencia solo cambia con `POST /api/sga/articulos/{id}/movimientos` de tipo `ENTRADA`, `SALIDA` o `AJUSTE`, y cada movimiento queda registrado con el `sub` del Actor y el instante (DP-SEG-06). Una salida que deje la existencia negativa responde 409. El inventario no se relaciona con los paquetes. |
| Indicadores (decidido) | SGA-06 incluido con tres indicadores: paquetes por estado, drones por estado operativo y artículos bajo stock mínimo. |
| Datos del dron | Código, modelo y observaciones. Código y modelo obligatorios; observaciones opcionales. El estado operativo lo controla el servidor y nace en `DISPONIBLE`. |
| Cambio manual del dron | Transiciones manuales entre `DISPONIBLE`, `MANTENIMIENTO` y `FUERA_DE_SERVICIO` en cualquier sentido. `EN_MISION` no se fija ni se abandona a mano: solo cambia con el paquete. Solicitar el mismo estado responde 409. Un dron en `DISPONIBLE` con un paquete `ASIGNADO` no puede pasar a `MANTENIMIENTO` ni a `FUERA_DE_SERVICIO`. `FUERA_DE_SERVICIO` hace las veces de desactivación: el dron no se elimina. |
| Paquete en curso | Un paquete está en curso mientras está en `ASIGNADO` o `EN_TRANSITO`. Un dron tiene como máximo un Paquete_En_Curso, regla que se repite como restricción de PostgreSQL. |
| Asignación del dron | El dron se indica con `dronId` en la transición a `ASIGNADO`, y solo en ella. Un paquete `ASIGNADO` conserva su dron hasta llegar a un estado final; para usar otro dron se cancela el paquete y se registra uno nuevo. El paquete conserva la referencia al dron después de llegar a un estado final, para la trazabilidad. |
| Cancelación desde `ASIGNADO` | El dron sigue en `DISPONIBLE` y deja de tener Paquete_En_Curso, así que vuelve a ser asignable. |
| Datos del paquete | Código, descripción, dirección de entrega, cliente y contrato opcional. El alta acepta además una observación opcional para su Registro_Historial. La dirección de entrega es obligatoria porque Operaciones la necesita para la entrega de última milla, y se trata como dato personal. |
| Código de dron, paquete y artículo | Lo escribe el usuario; se guarda sin espacios iniciales ni finales y en mayúsculas (Codigo_Normalizado, la misma regla del código de contrato de `002`); de 3 a 30 caracteres entre letras A–Z, dígitos y guiones. Es único dentro de su tabla. El código del dron y el del artículo se pueden editar; el del paquete es su identificador de seguimiento y no se edita. |
| Edición del paquete | Solo en `REGISTRADO` o `ASIGNADO` (antes de salir). `PUT` reemplaza descripción, dirección de entrega y contrato; el código, el cliente, el estado y el dron no se editan con `PUT`. Un contrato nuevo cumple las reglas de asociación; el contrato actual se conserva aunque ya no esté en `ACTIVO`. |
| Cliente o contrato que cambian después | El paquete conserva su cliente y su contrato si el cliente se desactiva o el contrato se finaliza o cancela después de asociarlos, y sigue admitiendo sus transiciones. |
| Integración con el CRM | El SGA comprueba la existencia, el estado activo y el nombre del Cliente mediante el Servicio_Clientes_CRM y la existencia, el cliente, el código y el estado del Contrato mediante el Servicio_Contratos_CRM, sin asociaciones JPA entre módulos; las claves foráneas `cliente_id` y `contrato_id` se definen en PostgreSQL (DP-ARQ-01). El nombre del cliente y el código y estado del contrato se leen del CRM en cada consulta y no se copian en las tablas del SGA. |
| Artículo | Código, nombre, categoría, unidad y stock mínimo, todos obligatorios. Nace con existencia 0; la cantidad inicial se registra con una `ENTRADA`. `PUT` no cambia la existencia. Los artículos no se desactivan ni se eliminan. |
| Categorías y unidades | Categoría: `REPUESTO`, `BATERIA`, `EMPAQUE` u `OTRO`. Unidad: `UNIDAD`, `CAJA`, `JUEGO` o `ROLLO`. |
| Cantidades | Enteras. Stock mínimo de 0 a 1 000 000. Cantidad de un movimiento: de 1 a 1 000 000 en `ENTRADA` y `SALIDA`; de 0 a 1 000 000 en `AJUSTE`. Existencia: entero mayor o igual a 0. Viajan como enteros JSON. |
| Tipos de movimiento | `ENTRADA` suma la cantidad; `SALIDA` la resta; `AJUSTE` fija la existencia al valor contado (la cantidad del movimiento) y exige una observación con el motivo. Cada movimiento guarda la existencia anterior y la resultante. Un error se corrige con un movimiento nuevo, por ejemplo un `AJUSTE`. |
| Bajo stock mínimo | Un artículo está bajo stock mínimo cuando su existencia es estrictamente menor que su stock mínimo. Con stock mínimo 0 nunca lo está. |
| Indicadores | Sin filtro de fechas: muestran el estado actual. Paquetes por cada uno de los seis estados y total; drones por cada uno de los cuatro estados y total; cantidad de artículos bajo stock mínimo y total de artículos. |
| Límites de texto | Código: 3 a 30; modelo del dron: 100; observaciones del dron: 500; descripción del paquete: 500; dirección de entrega: 200; observación de una transición, del alta o de un movimiento: 500; nombre del artículo: 200; búsqueda `q`: 100. |
| Historial sin paginación | `GET /api/sga/paquetes/{id}/estados` devuelve la lista completa en orden cronológico, sin paginación, porque un paquete tiene como máximo cuatro registros. Los movimientos de un artículo sí se paginan. |
| Respuesta de las operaciones de registro | `POST .../estados` y `POST .../movimientos` responden 201 con el registro creado y el encabezado `Location` de la colección en la que se consulta (`/api/sga/paquetes/{id}/estados` o `/api/sga/articulos/{id}/movimientos`); no existe un endpoint por registro individual. |
| Auditoría | `app.drones`, `app.paquetes` y `app.articulos_inventario` guardan `creado_en`, `actualizado_en` y `actualizado_por` (`sub` del JWT, `uuid not null` sin clave foránea a `auth.users`). Drones y paquetes guardan además el instante y el `sub` del último cambio de estado. El historial y los movimientos guardan `registrado_en` y `registrado_por`. Un movimiento de inventario no cambia `actualizado_en` ni `actualizado_por` del artículo; un cambio de estado tampoco cambia los de datos. |
| Vista de los recursos SGA | Una única representación para ADMINISTRADOR y OPERACIONES. Ningún otro rol lee drones, paquetes ni inventario; no hay lectura cruzada hacia el SGA. |

## Glossary

- **Backend:** API Spring Boot 4.1.2 (monolito modular) que valida el JWT, aplica la autorización por rol, valida las entradas, ejecuta las reglas de negocio y persiste los datos. El módulo SGA vive en el paquete `com.arise.corp.sga` y el CRM en `com.arise.corp.crm`.
- **Frontend:** SPA React con Vite, Tailwind CSS y React Router que presenta las vistas CRM, ERP y SGA y llama al Backend.
- **Cliente_HTTP:** módulo `frontend/src/services/apiClient.js` que envía las peticiones del Frontend al Backend con el token de acceso y traduce los errores, según `002-crm-clientes-contratos`.
- **Servicio_SGA_Frontend:** módulo nuevo `frontend/src/services/sgaApi.js` que agrupa las llamadas del Frontend a los endpoints del SGA mediante el Cliente_HTTP.
- **Servicio_CRM_Frontend:** módulo existente `frontend/src/services/crmApi.js` que el Frontend usa para leer clientes y contratos del CRM.
- **Base_Datos:** Supabase PostgreSQL; las tablas del SGA viven en el esquema privado `app` (DP-ARQ-02).
- **Base_Compartida:** instancia Supabase `arise-corp-dev` usada por el equipo para desarrollo.
- **Migracion_SGA:** archivo SQL nuevo `supabase/migrations/<timestamp>_app_sga_drones_paquetes_inventario.sql`, con un timestamp posterior a `20261118095500`, que crea los objetos del SGA (DP-TEC-03).
- **Dron:** aeronave de entrega de Arise Corp. registrada en el SGA e identificada por su código. El MVP no se conecta con el dron físico.
- **Estado_Dron:** `DISPONIBLE`, `EN_MISION`, `MANTENIMIENTO` o `FUERA_DE_SERVICIO`.
- **Transicion_Manual_Dron:** cambio de Estado_Dron que un Actor puede solicitar, incluido en el conjunto `DISPONIBLE→MANTENIMIENTO`, `DISPONIBLE→FUERA_DE_SERVICIO`, `MANTENIMIENTO→DISPONIBLE`, `MANTENIMIENTO→FUERA_DE_SERVICIO`, `FUERA_DE_SERVICIO→DISPONIBLE`, `FUERA_DE_SERVICIO→MANTENIMIENTO`.
- **Paquete:** envío de un Cliente que Arise Corp. entrega con un Dron, identificado por su código de seguimiento.
- **Estado_Paquete:** `REGISTRADO`, `ASIGNADO`, `EN_TRANSITO`, `ENTREGADO`, `CANCELADO` o `DEVUELTO`.
- **Estado_Final_Paquete:** `ENTREGADO`, `CANCELADO` o `DEVUELTO`; un Paquete en Estado_Final_Paquete no admite más transiciones ni ediciones.
- **Estado_Editable_Paquete:** `REGISTRADO` o `ASIGNADO`; solo en estos estados se edita un Paquete.
- **Transicion_Permitida_Paquete:** cambio de Estado_Paquete incluido en el conjunto `REGISTRADO→ASIGNADO`, `ASIGNADO→EN_TRANSITO`, `EN_TRANSITO→ENTREGADO`, `REGISTRADO→CANCELADO`, `ASIGNADO→CANCELADO`, `EN_TRANSITO→DEVUELTO`.
- **Paquete_En_Curso:** Paquete en estado `ASIGNADO` o `EN_TRANSITO`, considerado respecto del Dron que tiene asignado.
- **Dron_Asignable:** Dron en estado `DISPONIBLE` sin Paquete_En_Curso.
- **Registro_Historial:** entrada del historial de un Paquete con el estado anterior (nulo en el alta), el estado nuevo, el Dron asignado (solo en la transición a `ASIGNADO`), la observación opcional, el instante UTC y el `sub` del Actor.
- **Historial_Paquete:** lista de los Registro_Historial de un Paquete en orden cronológico (SGA-04).
- **Articulo:** artículo del catálogo de inventario del SGA (repuestos, baterías, empaques u otros), identificado por su código.
- **Categoria_Articulo:** `REPUESTO`, `BATERIA`, `EMPAQUE` u `OTRO`.
- **Unidad_Articulo:** `UNIDAD`, `CAJA`, `JUEGO` o `ROLLO`.
- **Existencia:** cantidad disponible de un Articulo, entera y mayor o igual a 0.
- **Stock_Minimo:** cantidad entera de 0 a 1 000 000 por debajo de la cual un Articulo necesita reposición.
- **Bajo_Stock_Minimo:** condición de un Articulo cuya Existencia es estrictamente menor que su Stock_Minimo.
- **Movimiento_Inventario:** registro de una operación sobre la Existencia de un Articulo con tipo, cantidad, Existencia anterior, Existencia resultante, observación, instante UTC y `sub` del Actor (SGA-03).
- **Tipo_Movimiento_Inventario:** `ENTRADA`, `SALIDA` o `AJUSTE`.
- **Cliente:** persona natural o jurídica registrada en `app.clientes`, cuyo módulo dueño es el CRM de `002-crm-clientes-contratos`.
- **Contrato:** acuerdo comercial registrado en `app.contratos` y vinculado a un único Cliente, cuyo módulo dueño es el CRM.
- **Estado_Contrato:** `BORRADOR`, `ACTIVO`, `FINALIZADO` o `CANCELADO`, definido en `002-crm-clientes-contratos`.
- **Servicio_Clientes_CRM:** servicio existente `crm.service.ClienteReferenciaService` que otros módulos usan para consultar la existencia, el nombre y el estado activo de los clientes (DP-ARQ-01).
- **Servicio_Contratos_CRM:** servicio nuevo de la capa `service` del módulo CRM que otros módulos usan para consultar la existencia, el Cliente, el código y el Estado_Contrato de los contratos (DP-ARQ-01).
- **Codigo_Normalizado:** código de un Dron, un Paquete o un Articulo sin espacios iniciales ni finales y con las letras en mayúsculas.
- **Actor:** usuario autenticado que realiza una petición; se identifica por el claim `sub` del JWT validado.
- **Perfil_Activo:** registro de `app.perfiles_usuario` con `activo = true` cuyo `usuario_id` coincide con el `sub` del Actor.
- **Rol_Gestor_SGA:** rol empresarial `ADMINISTRADOR` u `OPERACIONES`.
- **Rol_Sin_Acceso_SGA:** rol empresarial `COMERCIAL` o `FINANZAS_RRHH`.
- **Vista_Reducida_Cliente:** representación JSON del Cliente para FINANZAS_RRHH y OPERACIONES (`ClienteResumenResponse`), definida en `002-crm-clientes-contratos`.
- **Lista_Paginada:** respuesta JSON `{ "content": [], "page", "size", "totalElements", "totalPages" }` definida en DP-API-03.
- **Resumen_SGA:** respuesta de `GET /api/sga/resumen` con la cantidad de paquetes por Estado_Paquete y su total, la cantidad de drones por Estado_Dron y su total, y la cantidad de artículos Bajo_Stock_Minimo y el total de artículos (SGA-06).
- **Problem_Details:** cuerpo de error RFC 9457 (`application/problem+json`) con la extensión `errors` (`field`, `message`) para errores de campo y las extensiones `conflicto` y `campo` para conflictos y referencias (DP-API-02).
- **Suite_Backend:** pruebas unitarias, de contexto y de seguridad del Backend con sufijo `Tests`, ejecutadas por Surefire con `.\mvnw.cmd test`.
- **Suite_Integracion:** pruebas del Backend con sufijo `IT` en el paquete `com.arise.corp.integration`, ejecutadas por Failsafe con `.\mvnw.cmd verify` sobre PostgreSQL efímero con Testcontainers (DP-TEC-04, DP-EST-05).
- **Script_Preparacion_IT:** script SQL de prueba existente que crea en el PostgreSQL efímero el esquema `auth` con una tabla `auth.users` mínima y los roles `anon` y `authenticated`.
- **Suite_Frontend:** pruebas Vitest + React Testing Library del Frontend, ejecutadas con `npm test` desde `frontend/`.
- **Imagen_Backend:** imagen Docker construida desde `backend/Dockerfile`.
- **Compose_Local:** archivo `docker-compose.yml` en la raíz del repositorio.
- **Pipeline_CI:** workflows de GitHub Actions `backend-ci.yml`, `integration-tests.yml` y `frontend-ci.yml` en `.github/workflows/`.
- **Equipo:** Davidson Sánchez Gordillo, Paula Isabella Martínez Escalante y Sebastián Pardo Tirado.

## Requirements

### Requirement 1: Organización del módulo SGA (NF-04, DP-EST-01, DP-ARQ-01)

**User Story:** Como integrante del equipo, quiero que el SGA siga la estructura por módulo y capas del CRM y del ERP, para mantener un único patrón en el backend.

#### Acceptance Criteria

1. THE Backend SHALL ubicar el código del SGA en el paquete `com.arise.corp.sga` con las capas `controller`, `service`, `repository`, `entity` y `dto`. _(NF-04, DP-EST-01)_
2. THE Backend SHALL recibir y devolver DTO en los controladores del SGA y mantener las entidades JPA dentro de la capa de servicio. _(NF-04)_
3. THE Backend SHALL ubicar las reglas de negocio y las transacciones del SGA en la capa de servicio, con controladores limitados a validar el DTO, delegar en el servicio y construir la respuesta HTTP. _(NF-04)_
4. THE Backend SHALL usar en el SGA los componentes de `com.arise.corp.shared` para el manejo de errores, la normalización, la paginación y las validaciones comunes. _(NF-04)_
5. THE Backend SHALL consultar los datos de clientes y contratos desde el módulo SGA únicamente a través del Servicio_Clientes_CRM y del Servicio_Contratos_CRM, sin usar desde `sga` repositorios ni entidades del módulo `crm`. _(DP-ARQ-01)_
6. THE Backend SHALL ofrecer en el Servicio_Contratos_CRM una consulta de solo lectura que, dado el identificador de un Contrato, informe si existe, el identificador de su Cliente, su código y su Estado_Contrato, sin cambiar el comportamiento de los endpoints del CRM. _(DP-ARQ-01, SGA-05)_
7. THE Backend SHALL representar la asociación de un Paquete con su Cliente y su Contrato mediante los UUID `clienteId` y `contratoId`, sin asociaciones JPA entre entidades del SGA y del CRM. _(DP-ARQ-01)_
8. THE Backend SHALL mantener los módulos CRM y ERP sin dependencias hacia el módulo `sga`, y el módulo SGA sin dependencias hacia el módulo `erp`. _(DP-ARQ-01)_

### Requirement 2: Modelo de datos y migración del SGA (NF-03, NF-05, DP-TEC-03, DP-SEG-05)

**User Story:** Como integrante del equipo, quiero que las tablas del SGA se creen con una migración versionada y restricciones en PostgreSQL, para que la integridad de drones, paquetes e inventario no dependa solo del backend.

#### Acceptance Criteria

1. THE Migracion_SGA SHALL crear las tablas `app.drones`, `app.paquetes`, `app.historial_estados_paquete`, `app.articulos_inventario` y `app.movimientos_inventario` en un archivo nuevo de `supabase/migrations/` con un timestamp posterior a `20261118095500`, sin modificar las migraciones existentes. _(NF-05, DP-TEC-03)_
2. THE Migracion_SGA SHALL definir en `app.drones` un identificador UUID como clave primaria, `codigo` obligatorio y único, `modelo` obligatorio, `observaciones` opcional, `estado` obligatorio con valor por defecto `DISPONIBLE` y una restricción `CHECK` sobre los cuatro Estado_Dron, `estado_actualizado_en`, `estado_actualizado_por`, `creado_en`, `actualizado_en` y `actualizado_por`. _(SGA-01, NF-03)_
3. THE Migracion_SGA SHALL definir en `app.paquetes` un identificador UUID como clave primaria, `codigo` obligatorio y único, `descripcion` y `direccion_entrega` obligatorias, `estado` obligatorio con valor por defecto `REGISTRADO` y una restricción `CHECK` sobre los seis Estado_Paquete, `cliente_id` obligatorio con clave foránea a `app.clientes`, `contrato_id` opcional con clave foránea a `app.contratos`, `dron_id` opcional con clave foránea a `app.drones`, `estado_actualizado_en`, `estado_actualizado_por`, `creado_en`, `actualizado_en` y `actualizado_por`. _(SGA-02, SGA-05, NF-03, DP-SEG-06)_
4. THE Migracion_SGA SHALL declarar en `app.paquetes` una restricción `CHECK` que exija `dron_id` nulo en `REGISTRADO` y no nulo en `ASIGNADO`, `EN_TRANSITO`, `ENTREGADO` y `DEVUELTO`. _(SGA-05, NF-03)_
5. THE Migracion_SGA SHALL declarar sobre `app.paquetes` un índice único parcial que impida que dos paquetes en `ASIGNADO` o `EN_TRANSITO` tengan el mismo `dron_id`. _(SGA-05, NF-03)_
6. THE Migracion_SGA SHALL definir en `app.historial_estados_paquete` un identificador UUID como clave primaria, `paquete_id` obligatorio con clave foránea a `app.paquetes`, `estado_anterior` opcional, `estado_nuevo` obligatorio, ambos con una restricción `CHECK` sobre los seis Estado_Paquete, `dron_id` opcional con clave foránea a `app.drones`, `observacion` opcional, `registrado_en` y `registrado_por` obligatorios. _(SGA-04, DP-SEG-06)_
7. THE Migracion_SGA SHALL definir en `app.articulos_inventario` un identificador UUID como clave primaria, `codigo` obligatorio y único, `nombre` obligatorio, `categoria` y `unidad` obligatorias con restricciones `CHECK` sobre los valores de Categoria_Articulo y Unidad_Articulo, `stock_minimo` entero obligatorio entre 0 y 1 000 000, `existencia` entera obligatoria con valor por defecto 0 y una restricción `CHECK` de valor mayor o igual a 0, `creado_en`, `actualizado_en` y `actualizado_por`. _(SGA-03, NF-03)_
8. THE Migracion_SGA SHALL definir en `app.movimientos_inventario` un identificador UUID como clave primaria, `articulo_id` obligatorio con clave foránea a `app.articulos_inventario`, `tipo` obligatorio con una restricción `CHECK` sobre los tres Tipo_Movimiento_Inventario, `cantidad`, `existencia_anterior` y `existencia_resultante` enteras y obligatorias, `observacion` opcional, `registrado_en` y `registrado_por` obligatorios. _(SGA-03, DP-SEG-06)_
9. THE Migracion_SGA SHALL declarar en `app.movimientos_inventario` restricciones `CHECK` que exijan `existencia_resultante` mayor o igual a 0, `existencia_resultante = existencia_anterior + cantidad` en `ENTRADA`, `existencia_resultante = existencia_anterior - cantidad` en `SALIDA`, `existencia_resultante = cantidad` en `AJUSTE`, los rangos de cantidad de "Supuestos adoptados" y `observacion` no nula en `AJUSTE`. _(SGA-03, NF-03)_
10. THE Migracion_SGA SHALL declarar restricciones `CHECK` con el formato del Codigo_Normalizado y los límites de texto de "Supuestos adoptados" en las cinco tablas. _(NF-03)_
11. THE Migracion_SGA SHALL usar `timestamptz` para los instantes y `uuid not null` sin clave foránea a `auth.users` para `actualizado_por`, `estado_actualizado_por` y `registrado_por`. _(DP-API-04, DP-SEG-06)_
12. THE Migracion_SGA SHALL habilitar RLS sin políticas en las cinco tablas y revocar sus privilegios a los roles `anon` y `authenticated`. _(DP-SEG-05)_
13. THE Migracion_SGA SHALL contener únicamente estructura, sin datos de ejemplo, UUID personales ni secretos. _(NF-01)_
14. WHEN el Backend arranca contra una base con todas las migraciones aplicadas, THE Backend SHALL superar la validación `spring.jpa.hibernate.ddl-auto=validate` de las entidades del SGA. _(DP-TEC-03)_

### Requirement 3: Normalización de códigos y textos (SGA-01, SGA-02, SGA-03)

**User Story:** Como usuario de Operaciones, quiero que el sistema reconozca el mismo código escrito con distintas mayúsculas o espacios, para no registrar dos veces el mismo dron, paquete o artículo.

#### Acceptance Criteria

1. WHEN el Backend recibe el código de un Dron, de un Paquete o de un Articulo, THE Backend SHALL calcular el Codigo_Normalizado antes de validar, comparar o almacenar el valor, con la misma regla que aplica al código de contrato del CRM. _(SGA-01, SGA-02, SGA-03)_
2. THE Backend SHALL tratar como el mismo código dos entradas que difieren solo en espacios iniciales o finales o en mayúsculas y minúsculas, por ejemplo `drn-001` escrito con espacios alrededor y `DRN-001`. _(SGA-01, SGA-02, SGA-03)_
3. THE Backend SHALL producir, al normalizar un Codigo_Normalizado, un resultado idéntico a la entrada. _(SGA-01, SGA-02, SGA-03)_
4. WHEN el Backend recibe un campo de texto del SGA, THE Backend SHALL eliminar los espacios iniciales y finales y almacenar como nulo un campo opcional que quede vacío. _(NF-03)_

### Requirement 4: Registro y edición de drones (SGA-01)

**User Story:** Como usuario de Operaciones, quiero registrar y editar drones con su código, modelo y observaciones, para saber con qué aeronaves cuenta la operación.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_SGA envía `POST /api/sga/drones` con datos válidos, THE Backend SHALL crear el Dron en estado `DISPONIBLE`, asignar `creadoEn`, `actualizadoEn`, `actualizadoPor`, `estadoActualizadoEn` y `estadoActualizadoPor` con el instante de creación y el `sub` del Actor, y responder 201 con el Dron y el encabezado `Location: /api/sga/drones/{id}`. _(SGA-01, DP-SEG-06)_
2. THE Backend SHALL exigir en el registro de un Dron los campos `codigo` y `modelo`, y aceptar `observaciones` como opcional. _(SGA-01)_
3. IF falta un campo obligatorio o llega vacío, THEN THE Backend SHALL responder 400 con Problem_Details y una entrada en `errors` por cada campo faltante, sin crear el Dron. _(SGA-01, CA-06)_
4. IF el Codigo_Normalizado tiene menos de 3 o más de 30 caracteres o contiene caracteres distintos de letras A–Z, dígitos y guiones, THEN THE Backend SHALL responder 400 con un error en el campo `codigo`. _(SGA-01)_
5. IF `modelo` supera 100 caracteres u `observaciones` supera 500 caracteres, THEN THE Backend SHALL responder 400 con un error por cada campo inválido. _(SGA-01)_
6. IF el Codigo_Normalizado pertenece a otro Dron, THEN THE Backend SHALL responder 409 con `conflicto` `DRON_CODIGO_DUPLICADO` y `campo = "codigo"`, sin crear ni modificar el Dron. _(SGA-01)_
7. WHEN el cuerpo de la solicitud incluye `id`, `estado` o campos de auditoría, THE Backend SHALL ignorar esos valores y asignar los valores controlados por el servidor. _(NF-02, `security.md`)_
8. WHEN un Actor con Rol_Gestor_SGA envía `PUT /api/sga/drones/{id}` con datos válidos, THE Backend SHALL reemplazar `codigo`, `modelo` y `observaciones`, actualizar `actualizadoEn` y `actualizadoPor` con el `sub` del Actor, conservar el Estado_Dron, su auditoría y `creadoEn`, y responder 200 con el Dron. _(SGA-01, DP-SEG-06)_
9. THE Backend SHALL aplicar en `PUT /api/sga/drones/{id}` las mismas validaciones de campos que en el registro de drones y dejar nulo `observaciones` cuando se omite o se envía nulo. _(SGA-01)_
10. IF el `{id}` de `PUT /api/sga/drones/{id}` no corresponde a ningún Dron, THEN THE Backend SHALL responder 404. _(CA-06)_
11. IF un Actor con Rol_Gestor_SGA envía `DELETE /api/sga/drones/{id}`, THEN THE Backend SHALL responder 405 con Problem_Details y el encabezado `Allow`, y conservar el Dron; sin token o con un Rol_Sin_Acceso_SGA se aplican primero las respuestas 401 y 403 del Requirement 18. _(SGA-01)_

### Requirement 5: Listado y consulta de drones (SGA-01, DP-API-03)

**User Story:** Como usuario de Operaciones, quiero listar, buscar y consultar drones por estado, para saber cuáles puedo asignar a una entrega.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_SGA envía `GET /api/sga/drones`, THE Backend SHALL responder 200 con una Lista_Paginada de drones en cualquier Estado_Dron. _(SGA-01, DP-API-03)_
2. THE Backend SHALL aceptar en `GET /api/sga/drones` los parámetros `page` (desde 0, por defecto 0), `size` (de 1 a 100, por defecto 20) y `sort` con los campos `codigo`, `modelo` y `creadoEn` en dirección `asc` o `desc`, con `codigo,asc` por defecto. _(DP-API-03)_
3. WHERE la solicitud incluye el parámetro `q`, THE Backend SHALL devolver los drones cuyo código contiene el Codigo_Normalizado de `q` o cuyo `modelo` contiene `q` sin distinguir mayúsculas y minúsculas. _(SGA-01)_
4. WHERE la solicitud incluye el parámetro `estado`, THE Backend SHALL devolver solo los drones cuyo Estado_Dron coincide con el valor recibido. _(SGA-01)_
5. WHERE la solicitud incluye el parámetro `asignable` con valor `true`, THE Backend SHALL devolver solo los Dron_Asignable, y con valor `false` solo los drones que no son Dron_Asignable. _(SGA-05)_
6. IF `page` es negativo, `size` está fuera de 1 a 100, `sort` usa un campo o una dirección no permitidos, `q` supera 100 caracteres, `estado` no es un Estado_Dron o `asignable` no es booleano, THEN THE Backend SHALL responder 400 con Problem_Details. _(DP-API-03, CA-06)_
7. WHEN un Actor con Rol_Gestor_SGA envía `GET /api/sga/drones/{id}` para un Dron existente, THE Backend SHALL responder 200 con `id`, `codigo`, `modelo`, `observaciones`, `estado`, el Paquete_En_Curso (identificador, código y Estado_Paquete, o nulo), `estadoActualizadoEn`, `estadoActualizadoPor`, `creadoEn`, `actualizadoEn` y `actualizadoPor`. _(SGA-01, SGA-05)_
8. IF el `{id}` de `GET /api/sga/drones/{id}` no corresponde a ningún Dron, THEN THE Backend SHALL responder 404 con Problem_Details. _(CA-06)_
9. IF el `{id}` de una ruta del SGA no es un UUID, THEN THE Backend SHALL responder 400 con Problem_Details y `errors[0].field = "id"`. _(CA-06)_

### Requirement 6: Cambio manual del estado operativo del dron (SGA-01, SGA-05, DP-SEG-06)

**User Story:** Como usuario de Operaciones, quiero poner un dron en mantenimiento o fuera de servicio y devolverlo a disponible, para que no se asigne a entregas mientras no puede volar.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_SGA envía `PATCH /api/sga/drones/{id}/estado` con un `estado` que forma una Transicion_Manual_Dron desde el estado actual y el Dron no tiene Paquete_En_Curso, THE Backend SHALL actualizar `estado`, `estadoActualizadoEn` con el instante UTC y `estadoActualizadoPor` con el `sub` del Actor en una misma transacción y responder 200 con el Dron. _(SGA-01, DP-SEG-06)_
2. IF el `estado` solicitado no forma una Transicion_Manual_Dron desde el estado actual, incluido el mismo estado, el paso a `EN_MISION` o cualquier cambio desde `EN_MISION`, THEN THE Backend SHALL responder 409 con `conflicto` `TRANSICION_DRON_NO_PERMITIDA`, las extensiones `estadoActual` y `estadoSolicitado` y un mensaje que indique ambos estados, sin modificar el Dron. _(SGA-01)_
3. IF la solicitud forma una Transicion_Manual_Dron y el Dron tiene un Paquete_En_Curso, THEN THE Backend SHALL responder 409 con `conflicto` `DRON_CON_PAQUETE_EN_CURSO`, la extensión `paqueteId` y un mensaje que indique que el dron tiene un paquete en curso, sin modificar el Dron. _(SGA-01, SGA-05)_
4. IF el cuerpo no contiene `estado` o `estado` no es un Estado_Dron, THEN THE Backend SHALL responder 400 con un error en el campo `estado`. _(CA-06)_
5. THE Backend SHALL conservar `codigo`, `modelo`, `observaciones`, `actualizadoEn` y `actualizadoPor` al procesar `PATCH /api/sga/drones/{id}/estado`. _(SGA-01, DP-SEG-06)_
6. IF el `{id}` de `PATCH /api/sga/drones/{id}/estado` no corresponde a ningún Dron, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 7: Registro de paquetes y asociación con cliente y contrato (SGA-02, SGA-04, SGA-05)

**User Story:** Como usuario de Operaciones, quiero registrar un paquete asociado a un cliente y, cuando aplique, a uno de sus contratos activos, para que cada entrega quede ligada a la relación comercial que la origina.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_SGA envía `POST /api/sga/paquetes` con datos válidos, THE Backend SHALL crear en una misma transacción el Paquete en estado `REGISTRADO` sin Dron, con su auditoría y su auditoría de estado iguales al instante de creación y al `sub` del Actor, y su primer Registro_Historial con estado anterior nulo, estado nuevo `REGISTRADO` y la `observacion` recibida, y responder 201 con el Paquete y el encabezado `Location: /api/sga/paquetes/{id}`. _(SGA-02, SGA-04, DP-SEG-06)_
2. THE Backend SHALL exigir en el registro de un Paquete los campos `codigo`, `descripcion`, `direccionEntrega` y `clienteId`, y aceptar como opcionales `contratoId` y `observacion`. _(SGA-02, SGA-05)_
3. IF falta un campo obligatorio o llega vacío, THEN THE Backend SHALL responder 400 con Problem_Details y una entrada en `errors` por cada campo faltante, sin crear el Paquete. _(SGA-02, CA-06)_
4. IF el Codigo_Normalizado incumple el formato de código de los supuestos, `descripcion` supera 500 caracteres, `direccionEntrega` supera 200 caracteres u `observacion` supera 500 caracteres, THEN THE Backend SHALL responder 400 con un error por cada campo inválido. _(SGA-02)_
5. IF el Codigo_Normalizado pertenece a otro Paquete, THEN THE Backend SHALL responder 409 con `conflicto` `PAQUETE_CODIGO_DUPLICADO` y `campo = "codigo"`, sin crear el Paquete. _(SGA-02)_
6. IF `clienteId` no corresponde a ningún Cliente, THEN THE Backend SHALL responder 404 con la extensión `campo = "clienteId"`, sin crear el Paquete. _(SGA-05, CA-06)_
7. IF `clienteId` corresponde a un Cliente inactivo, THEN THE Backend SHALL responder 409 con `conflicto` `CLIENTE_INACTIVO` y `campo = "clienteId"`, sin crear el Paquete. _(SGA-05)_
8. IF `contratoId` no corresponde a ningún Contrato, THEN THE Backend SHALL responder 404 con la extensión `campo = "contratoId"`, sin crear el Paquete. _(SGA-05, CA-06)_
9. IF `contratoId` corresponde a un Contrato de un Cliente distinto de `clienteId`, THEN THE Backend SHALL responder 409 con `conflicto` `CONTRATO_DE_OTRO_CLIENTE` y `campo = "contratoId"`, sin crear el Paquete. _(SGA-05)_
10. IF `contratoId` corresponde a un Contrato del mismo Cliente con un Estado_Contrato distinto de `ACTIVO`, THEN THE Backend SHALL responder 409 con `conflicto` `CONTRATO_NO_ACTIVO`, `campo = "contratoId"` y la extensión `estadoActual`, sin crear el Paquete. _(SGA-05)_
11. WHEN el cuerpo de la solicitud incluye `id`, `estado`, `dronId` o campos de auditoría, THE Backend SHALL ignorar esos valores y crear el Paquete en estado `REGISTRADO` sin Dron. _(NF-02, `security.md`)_
12. THE Backend SHALL comprobar el Cliente mediante el Servicio_Clientes_CRM y el Contrato mediante el Servicio_Contratos_CRM dentro de la transacción que crea o modifica el Paquete. _(DP-ARQ-01, SGA-05)_

### Requirement 8: Listado y consulta de paquetes (SGA-02, SGA-05, DP-API-03)

**User Story:** Como usuario de Operaciones, quiero listar y filtrar paquetes por estado, cliente, contrato o dron, para encontrar rápido las entregas que debo atender.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_SGA envía `GET /api/sga/paquetes`, THE Backend SHALL responder 200 con una Lista_Paginada de paquetes que admite `page` (desde 0, por defecto 0), `size` (de 1 a 100, por defecto 20) y `sort` sobre `codigo`, `creadoEn` y `estadoActualizadoEn` en dirección `asc` o `desc`, con `creadoEn,desc` por defecto. _(SGA-02, DP-API-03)_
2. WHERE la solicitud incluye `estado`, `clienteId`, `contratoId` o `dronId`, THE Backend SHALL devolver solo los paquetes cuyo campo correspondiente coincide con el valor recibido. _(SGA-02, SGA-05)_
3. WHERE la solicitud incluye el parámetro `q`, THE Backend SHALL devolver los paquetes cuyo código contiene el Codigo_Normalizado de `q` o cuya `descripcion` contiene `q` sin distinguir mayúsculas y minúsculas. _(SGA-02)_
4. WHEN la solicitud incluye varios filtros, THE Backend SHALL devolver solo los paquetes que cumplen todos los filtros recibidos. _(DP-API-03)_
5. WHERE la solicitud incluye un `clienteId`, un `contratoId` o un `dronId` sin paquetes o sin registro correspondiente, THE Backend SHALL responder 200 con una Lista_Paginada vacía. _(SGA-05)_
6. IF un parámetro de `GET /api/sga/paquetes` incumple las reglas de paginación u orden, `q` supera 100 caracteres, `estado` no es un Estado_Paquete o `clienteId`, `contratoId` o `dronId` no son UUID, THEN THE Backend SHALL responder 400 con Problem_Details. _(DP-API-03, CA-06)_
7. WHEN un Actor con Rol_Gestor_SGA envía `GET /api/sga/paquetes/{id}` para un Paquete existente, THE Backend SHALL responder 200 con `id`, `codigo`, `descripcion`, `direccionEntrega`, `estado`, el Cliente (identificador, nombre y estado activo), el Contrato (identificador, código y Estado_Contrato, o nulo), el Dron (identificador, código y Estado_Dron, o nulo), `estadoActualizadoEn`, `estadoActualizadoPor`, `creadoEn`, `actualizadoEn` y `actualizadoPor`. _(SGA-02, SGA-05)_
8. THE Backend SHALL obtener del CRM, en cada consulta, el nombre y el estado activo del Cliente y el código y el Estado_Contrato del Contrato, sin copiarlos en las tablas del SGA. _(DP-ARQ-01, MVP §6.6)_
9. IF el `{id}` de `GET /api/sga/paquetes/{id}` no corresponde a ningún Paquete, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 9: Edición de paquetes antes de la salida (SGA-02, SGA-05)

**User Story:** Como usuario de Operaciones, quiero corregir los datos de un paquete mientras no ha salido, para no alterar entregas en curso ni terminadas.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_SGA envía `PUT /api/sga/paquetes/{id}` con datos válidos para un Paquete en Estado_Editable_Paquete, THE Backend SHALL reemplazar `descripcion`, `direccionEntrega` y `contratoId`, actualizar `actualizadoEn` y `actualizadoPor` con el `sub` del Actor y responder 200 con el Paquete. _(SGA-02, DP-SEG-06)_
2. THE Backend SHALL aplicar en `PUT /api/sga/paquetes/{id}` las mismas validaciones de campos que en el registro de paquetes para `descripcion`, `direccionEntrega` y `contratoId`. _(SGA-02)_
3. IF el Paquete no está en un Estado_Editable_Paquete, THEN THE Backend SHALL responder 409 con `conflicto` `PAQUETE_NO_EDITABLE`, la extensión `estadoActual` y un mensaje que indique que solo se editan paquetes registrados o asignados, sin modificar el Paquete. _(SGA-02)_
4. THE Backend SHALL conservar el código, el Cliente, el Estado_Paquete, su auditoría de estado, el Dron y el Historial_Paquete al procesar `PUT /api/sga/paquetes/{id}`, aunque el cuerpo incluya `codigo`, `clienteId`, `estado` o `dronId`. _(SGA-02, DP-API-05)_
5. IF `PUT /api/sga/paquetes/{id}` asigna un `contratoId` distinto del Contrato actual, THEN THE Backend SHALL aplicar las respuestas 404, 409 `CONTRATO_DE_OTRO_CLIENTE` y 409 `CONTRATO_NO_ACTIVO` del Requirement 7 respecto del Cliente del Paquete, sin modificar el Paquete. _(SGA-05)_
6. WHEN `PUT /api/sga/paquetes/{id}` conserva el Contrato actual del Paquete y ese Contrato ya no está en `ACTIVO`, THE Backend SHALL aplicar la edición. _(SGA-05)_
7. WHEN `PUT /api/sga/paquetes/{id}` omite `contratoId` o lo envía nulo, THE Backend SHALL eliminar la asociación del Paquete con su Contrato. _(SGA-05)_
8. IF el `{id}` de `PUT /api/sga/paquetes/{id}` no corresponde a ningún Paquete, THEN THE Backend SHALL responder 404. _(CA-06)_
9. IF un Actor con Rol_Gestor_SGA envía `DELETE /api/sga/paquetes/{id}`, THEN THE Backend SHALL responder 405 con Problem_Details y el encabezado `Allow`, y conservar el Paquete; sin token o con un Rol_Sin_Acceso_SGA se aplican primero las respuestas 401 y 403 del Requirement 18. _(SGA-02)_

### Requirement 10: Cambio de estado del paquete (SGA-02, SGA-04, DP-API-05, DP-SEG-06)

**User Story:** Como usuario de Operaciones, quiero avanzar un paquete por estados controlados y registrar cada cambio, para que su recorrido sea coherente y trazable.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_SGA envía `POST /api/sga/paquetes/{id}/estados` con un `estado` que forma una Transicion_Permitida_Paquete desde el estado actual y cumple las reglas del Requirement 11, THE Backend SHALL, en una misma transacción, actualizar `estado`, `estadoActualizadoEn` con el instante UTC y `estadoActualizadoPor` con el `sub` del Actor, crear un Registro_Historial con el estado anterior, el estado nuevo, la `observacion` recibida, el mismo instante y el mismo `sub`, aplicar el cambio del Dron del Requirement 11, y responder 201 con el Registro_Historial y el encabezado `Location: /api/sga/paquetes/{id}/estados`. _(SGA-04, DP-API-05, DP-SEG-06)_
2. IF el `estado` solicitado no forma una Transicion_Permitida_Paquete desde el estado actual, incluido el mismo estado, el paso a `REGISTRADO`, un salto de estados o cualquier cambio desde un Estado_Final_Paquete, THEN THE Backend SHALL responder 409 con `conflicto` `TRANSICION_NO_PERMITIDA`, `campo = "estado"`, las extensiones `estadoActual` y `estadoSolicitado` y un mensaje que indique ambos estados, sin modificar el Paquete, el Historial_Paquete ni el Dron. _(SGA-04, DP-API-05)_
3. IF el cuerpo no contiene `estado` o `estado` no es un Estado_Paquete, THEN THE Backend SHALL responder 400 con un error en el campo `estado`. _(CA-06)_
4. IF `observacion` supera 500 caracteres, THEN THE Backend SHALL responder 400 con un error en el campo `observacion`. _(SGA-04)_
5. IF la actualización del estado, la creación del Registro_Historial o el cambio del Dron falla, THEN THE Backend SHALL revertir la operación completa y conservar el estado, el Historial_Paquete y el Dron anteriores. _(SGA-04, DP-SEG-06)_
6. WHILE el Cliente del Paquete está inactivo o su Contrato ya no está en `ACTIVO`, THE Backend SHALL aplicar a ese Paquete las mismas Transicion_Permitida_Paquete que a los demás paquetes. _(SGA-05)_
7. THE Backend SHALL conservar los datos del Paquete, `actualizadoEn` y `actualizadoPor` al procesar `POST /api/sga/paquetes/{id}/estados`. _(SGA-04, DP-SEG-06)_
8. IF el `{id}` de `POST /api/sga/paquetes/{id}/estados` no corresponde a ningún Paquete, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 11: Asignación del dron y sincronización de su estado (SGA-01, SGA-05)

**User Story:** Como usuario de Operaciones, quiero asignar un dron disponible a un paquete y que el estado del dron siga al del paquete, para no enviar dos entregas con el mismo dron ni dejar drones bloqueados.

#### Acceptance Criteria

1. IF `POST /api/sga/paquetes/{id}/estados` solicita `ASIGNADO` sin `dronId`, THEN THE Backend SHALL responder 400 con un error en el campo `dronId`. _(SGA-05)_
2. IF `POST /api/sga/paquetes/{id}/estados` incluye `dronId` con un `estado` distinto de `ASIGNADO`, THEN THE Backend SHALL responder 400 con un error en el campo `dronId`. _(SGA-05)_
3. IF `dronId` no corresponde a ningún Dron, THEN THE Backend SHALL responder 404 con la extensión `campo = "dronId"`, sin modificar el Paquete. _(SGA-05, CA-06)_
4. IF el Dron indicado no está en `DISPONIBLE`, THEN THE Backend SHALL responder 409 con `conflicto` `DRON_NO_DISPONIBLE`, `campo = "dronId"` y la extensión `estadoActual` con el Estado_Dron, sin modificar el Paquete ni el Dron. _(SGA-01, SGA-05)_
5. IF el Dron indicado está en `DISPONIBLE` y tiene un Paquete_En_Curso, THEN THE Backend SHALL responder 409 con `conflicto` `DRON_CON_PAQUETE_EN_CURSO`, `campo = "dronId"` y la extensión `paqueteId`, sin modificar el Paquete ni el Dron. _(SGA-05)_
6. WHEN el Backend aplica la transición `REGISTRADO→ASIGNADO`, THE Backend SHALL asociar el Dron al Paquete, registrar el Dron en el Registro_Historial y conservar el Dron en `DISPONIBLE`. _(SGA-05)_
7. WHEN el Backend aplica la transición `ASIGNADO→EN_TRANSITO`, THE Backend SHALL cambiar el Dron asignado a `EN_MISION` y actualizar su auditoría de estado con el mismo instante y el mismo `sub` que el Registro_Historial. _(SGA-01, SGA-05)_
8. IF al solicitar `ASIGNADO→EN_TRANSITO` el Dron asignado no está en `DISPONIBLE`, THEN THE Backend SHALL responder 409 con `conflicto` `DRON_NO_DISPONIBLE` y la extensión `estadoActual`, sin modificar el Paquete ni el Dron. _(SGA-01)_
9. WHEN el Backend aplica la transición `EN_TRANSITO→ENTREGADO` o `EN_TRANSITO→DEVUELTO`, THE Backend SHALL cambiar el Dron asignado a `DISPONIBLE` y actualizar su auditoría de estado con el mismo instante y el mismo `sub` que el Registro_Historial. _(SGA-01, SGA-05)_
10. WHEN el Backend aplica la transición `ASIGNADO→CANCELADO`, THE Backend SHALL conservar el Dron en `DISPONIBLE`, de modo que vuelva a ser Dron_Asignable. _(SGA-05)_
11. THE Backend SHALL conservar la referencia al Dron en un Paquete que llega a un Estado_Final_Paquete. _(SGA-04, SGA-05)_
12. WHEN el Backend recibe solicitudes simultáneas que asignan el mismo Dron a paquetes distintos, THE Backend SHALL aceptar como máximo una y responder 409 a las demás, sin dejar el Dron con más de un Paquete_En_Curso. _(SGA-05, NF-03)_

### Requirement 12: Historial de estados del paquete (SGA-04)

**User Story:** Como usuario de Operaciones, quiero consultar el historial completo de un paquete, para saber quién cambió su estado, cuándo y con qué observación.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_SGA envía `GET /api/sga/paquetes/{id}/estados` para un Paquete existente, THE Backend SHALL responder 200 con la lista completa de sus Registro_Historial en orden cronológico ascendente, cada uno con `id`, `estadoAnterior`, `estadoNuevo`, el Dron (identificador y código, o nulo), `observacion`, `registradoEn` y `registradoPor`. _(SGA-04)_
2. THE Backend SHALL mantener en el Historial_Paquete exactamente un Registro_Historial por el alta y uno por cada transición aplicada. _(SGA-04)_
3. THE Backend SHALL encadenar el Historial_Paquete de modo que el primer Registro_Historial tenga estado anterior nulo y estado nuevo `REGISTRADO`, el estado anterior de cada registro siguiente sea el estado nuevo del registro previo y el estado nuevo del último registro sea el Estado_Paquete actual. _(SGA-04)_
4. IF una solicitud de cambio de estado responde 400, 404 o 409, THEN THE Backend SHALL conservar el Historial_Paquete sin registros nuevos. _(SGA-04)_
5. THE Backend SHALL tomar `registradoPor` del claim `sub` del JWT validado. _(DP-SEG-06)_
6. IF un Actor con Rol_Gestor_SGA envía `PUT`, `PATCH` o `DELETE` a `/api/sga/paquetes/{id}/estados`, THEN THE Backend SHALL responder 405 con Problem_Details y el encabezado `Allow`, y conservar el Historial_Paquete. _(SGA-04)_
7. IF el `{id}` de `GET /api/sga/paquetes/{id}/estados` no corresponde a ningún Paquete, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 13: Catálogo de artículos de inventario (SGA-03)

**User Story:** Como usuario de Operaciones, quiero registrar y editar los artículos del inventario con su categoría, unidad y stock mínimo, para controlar los repuestos, baterías y empaques que usa la operación.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_SGA envía `POST /api/sga/articulos` con datos válidos, THE Backend SHALL crear el Articulo con Existencia 0, asignar `creadoEn`, `actualizadoEn` y `actualizadoPor` con el `sub` del Actor, y responder 201 con el Articulo y el encabezado `Location: /api/sga/articulos/{id}`. _(SGA-03, DP-SEG-06)_
2. THE Backend SHALL exigir en el registro de un Articulo los campos `codigo`, `nombre`, `categoria`, `unidad` y `stockMinimo`. _(SGA-03)_
3. IF falta un campo obligatorio o llega vacío, THEN THE Backend SHALL responder 400 con Problem_Details y una entrada en `errors` por cada campo faltante, sin crear el Articulo. _(SGA-03, CA-06)_
4. IF `categoria` no es una Categoria_Articulo o `unidad` no es una Unidad_Articulo, THEN THE Backend SHALL responder 400 con un error en el campo correspondiente. _(SGA-03)_
5. IF el Codigo_Normalizado incumple el formato de código de los supuestos, `nombre` supera 200 caracteres o `stockMinimo` no es un entero entre 0 y 1 000 000, THEN THE Backend SHALL responder 400 con un error por cada campo inválido. _(SGA-03)_
6. IF el Codigo_Normalizado pertenece a otro Articulo, THEN THE Backend SHALL responder 409 con `conflicto` `ARTICULO_CODIGO_DUPLICADO` y `campo = "codigo"`, sin crear ni modificar el Articulo. _(SGA-03)_
7. WHEN el cuerpo de la solicitud incluye `id`, `existencia` o campos de auditoría, THE Backend SHALL ignorar esos valores y asignar los valores controlados por el servidor. _(SGA-03, NF-02)_
8. WHEN un Actor con Rol_Gestor_SGA envía `PUT /api/sga/articulos/{id}` con datos válidos, THE Backend SHALL reemplazar `codigo`, `nombre`, `categoria`, `unidad` y `stockMinimo` con las mismas validaciones del registro, actualizar `actualizadoEn` y `actualizadoPor`, conservar la Existencia y `creadoEn`, y responder 200 con el Articulo. _(SGA-03, DP-SEG-06)_
9. IF el `{id}` de `PUT /api/sga/articulos/{id}` no corresponde a ningún Articulo, THEN THE Backend SHALL responder 404. _(CA-06)_
10. IF un Actor con Rol_Gestor_SGA envía `DELETE /api/sga/articulos/{id}`, THEN THE Backend SHALL responder 405 con Problem_Details y el encabezado `Allow`, y conservar el Articulo; sin token o con un Rol_Sin_Acceso_SGA se aplican primero las respuestas 401 y 403 del Requirement 18. _(SGA-03)_

### Requirement 14: Listado y consulta de artículos (SGA-03, DP-API-03)

**User Story:** Como usuario de Operaciones, quiero listar los artículos y ver cuáles están por debajo de su stock mínimo, para planear su reposición.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_SGA envía `GET /api/sga/articulos`, THE Backend SHALL responder 200 con una Lista_Paginada de artículos que admite `page` (desde 0, por defecto 0), `size` (de 1 a 100, por defecto 20) y `sort` sobre `codigo`, `nombre`, `existencia` y `creadoEn` en dirección `asc` o `desc`, con `nombre,asc` por defecto. _(SGA-03, DP-API-03)_
2. WHERE la solicitud incluye el parámetro `q`, THE Backend SHALL devolver los artículos cuyo código contiene el Codigo_Normalizado de `q` o cuyo `nombre` contiene `q` sin distinguir mayúsculas y minúsculas. _(SGA-03)_
3. WHERE la solicitud incluye el parámetro `categoria`, THE Backend SHALL devolver solo los artículos de esa Categoria_Articulo. _(SGA-03)_
4. WHERE la solicitud incluye el parámetro `bajoStockMinimo` con valor `true`, THE Backend SHALL devolver solo los artículos en condición Bajo_Stock_Minimo, y con valor `false` solo los que no lo están. _(SGA-03, SGA-06)_
5. IF un parámetro de `GET /api/sga/articulos` incumple las reglas de paginación u orden, `q` supera 100 caracteres, `categoria` no es una Categoria_Articulo o `bajoStockMinimo` no es booleano, THEN THE Backend SHALL responder 400 con Problem_Details. _(DP-API-03, CA-06)_
6. WHEN un Actor con Rol_Gestor_SGA envía `GET /api/sga/articulos/{id}` para un Articulo existente, THE Backend SHALL responder 200 con `id`, `codigo`, `nombre`, `categoria`, `unidad`, `stockMinimo`, `existencia`, `bajoStockMinimo`, `creadoEn`, `actualizadoEn` y `actualizadoPor`. _(SGA-03)_
7. IF el `{id}` de `GET /api/sga/articulos/{id}` no corresponde a ningún Articulo, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 15: Movimientos de inventario (SGA-03, DP-API-05, DP-SEG-06)

**User Story:** Como usuario de Operaciones, quiero registrar entradas, salidas y ajustes de inventario, para que la existencia de cada artículo cambie solo con operaciones registradas y nunca sea negativa.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_SGA envía `POST /api/sga/articulos/{id}/movimientos` con datos válidos, THE Backend SHALL, en una misma transacción, crear el Movimiento_Inventario con `tipo`, `cantidad`, `existenciaAnterior`, `existenciaResultante`, `observacion`, `registradoEn` con el instante UTC y `registradoPor` con el `sub` del Actor, fijar la Existencia del Articulo en `existenciaResultante`, y responder 201 con el Movimiento_Inventario y el encabezado `Location: /api/sga/articulos/{id}/movimientos`. _(SGA-03, DP-SEG-06)_
2. WHEN el Backend registra una `ENTRADA`, THE Backend SHALL calcular `existenciaResultante` como la Existencia anterior más la `cantidad`. _(SGA-03)_
3. WHEN el Backend registra una `SALIDA`, THE Backend SHALL calcular `existenciaResultante` como la Existencia anterior menos la `cantidad`. _(SGA-03)_
4. WHEN el Backend registra un `AJUSTE`, THE Backend SHALL fijar `existenciaResultante` en la `cantidad` recibida, que representa la cantidad contada. _(SGA-03)_
5. IF una `SALIDA` dejaría la Existencia por debajo de 0, THEN THE Backend SHALL responder 409 con `conflicto` `EXISTENCIA_INSUFICIENTE`, `campo = "cantidad"`, las extensiones `existenciaActual` y `cantidadSolicitada` y un mensaje que indique la existencia disponible, sin registrar el movimiento ni cambiar la Existencia. _(SGA-03)_
6. IF falta `tipo` o `tipo` no es un Tipo_Movimiento_Inventario, THEN THE Backend SHALL responder 400 con un error en el campo `tipo`. _(SGA-03)_
7. IF falta `cantidad`, `cantidad` no es un entero, o está fuera de 1 a 1 000 000 en una `ENTRADA` o una `SALIDA` o fuera de 0 a 1 000 000 en un `AJUSTE`, THEN THE Backend SHALL responder 400 con un error en el campo `cantidad`. _(SGA-03)_
8. IF un `AJUSTE` llega sin `observacion` o `observacion` supera 500 caracteres en cualquier tipo, THEN THE Backend SHALL responder 400 con un error en el campo `observacion`. _(SGA-03)_
9. IF la creación del Movimiento_Inventario o la actualización de la Existencia falla, THEN THE Backend SHALL revertir la operación completa y conservar la Existencia anterior. _(SGA-03, DP-SEG-06)_
10. WHEN el Backend recibe solicitudes simultáneas de `SALIDA` sobre el mismo Articulo, THE Backend SHALL aplicarlas una tras otra, de modo que la Existencia final sea la Existencia inicial menos la suma de las salidas aceptadas y ninguna salida aceptada la deje por debajo de 0. _(SGA-03, NF-03)_
11. WHEN un Actor con Rol_Gestor_SGA envía `GET /api/sga/articulos/{id}/movimientos`, THE Backend SHALL responder 200 con una Lista_Paginada de los movimientos del Articulo con `page`, `size` y `sort` sobre `registradoEn` en dirección `asc` o `desc`, con `registradoEn,desc` por defecto. _(SGA-03, DP-API-03)_
12. THE Backend SHALL cambiar la Existencia de un Articulo únicamente mediante `POST /api/sga/articulos/{id}/movimientos`. _(SGA-03, DP-API-05)_
13. IF un Actor con Rol_Gestor_SGA envía `PUT`, `PATCH` o `DELETE` a `/api/sga/articulos/{id}/movimientos`, THEN THE Backend SHALL responder 405 con Problem_Details y el encabezado `Allow`, y conservar los movimientos. _(SGA-03)_
14. IF el `{id}` de una ruta de movimientos no corresponde a ningún Articulo, THEN THE Backend SHALL responder 404. _(CA-06)_
15. THE Backend SHALL conservar `actualizadoEn` y `actualizadoPor` del Articulo al registrar un Movimiento_Inventario. _(DP-SEG-06)_

### Requirement 16: Indicadores del SGA (SGA-06)

**User Story:** Como usuario de Operaciones, quiero ver cuántos paquetes hay en cada estado, cuántos drones en cada estado operativo y cuántos artículos están bajo stock mínimo, para conocer de un vistazo la situación de la operación.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_SGA envía `GET /api/sga/resumen`, THE Backend SHALL responder 200 con el Resumen_SGA: la cantidad de paquetes en cada Estado_Paquete y el total de paquetes, la cantidad de drones en cada Estado_Dron y el total de drones, y la cantidad de artículos Bajo_Stock_Minimo y el total de artículos. _(SGA-06)_
2. THE Backend SHALL incluir en el Resumen_SGA los seis Estado_Paquete y los cuatro Estado_Dron, con cantidad 0 para los estados sin registros. _(SGA-06)_
3. THE Backend SHALL calcular el Resumen_SGA con los datos persistidos en el momento de la solicitud, con cada total igual a la suma de sus cantidades por estado. _(SGA-06, CA-04)_
4. THE Backend SHALL contar como Bajo_Stock_Minimo solo los artículos cuya Existencia es estrictamente menor que su Stock_Minimo. _(SGA-06)_

### Requirement 17: Protección de datos personales en el SGA (NF-02, CA-06, `security.md`)

**User Story:** Como Administrador, quiero que las direcciones de entrega, las observaciones y los datos de clientes que usa el SGA no aparezcan en logs ni en mensajes de error, para limitar su exposición.

#### Acceptance Criteria

1. THE Backend SHALL devolver a ADMINISTRADOR y a OPERACIONES la misma representación de drones, paquetes, historial, artículos y movimientos, sin aceptar parámetros de la petición que cambien los campos devueltos. _(DP-PRO-01, NF-02)_
2. THE Backend SHALL limitar los datos del Cliente que devuelve el SGA a su identificador, su nombre y su estado activo. _(DP-PRO-01, `security.md`)_
3. THE Backend SHALL excluir de los logs la dirección de entrega, las observaciones del historial, del alta y de los movimientos, y el nombre del Cliente. _(`security.md`)_
4. THE Backend SHALL excluir de los mensajes `detail` de los errores del SGA las direcciones de entrega, las observaciones y los nombres y datos de contacto de los clientes. _(CA-06)_

### Requirement 18: Autorización por rol en el SGA (NF-02, CA-03, DP-PRO-01, DP-SEG-02)

**User Story:** Como Administrador, quiero que el backend aplique la matriz de acceso del SGA, para que solo Operaciones y Administración operen sobre drones, paquetes e inventario.

#### Acceptance Criteria

1. THE Backend SHALL aplicar a los endpoints del SGA la matriz de acceso de la Introducción con los patrones y roles actuales de `SecurityConfig`, sin modificarlos. _(NF-02, DP-SEG-02)_
2. IF una petición a un endpoint del SGA llega sin token o con un token inválido o vencido, THEN THE Backend SHALL responder 401 con Problem_Details y el encabezado `WWW-Authenticate`. _(CA-02, CA-06)_
3. IF una petición a un endpoint del SGA llega con un JWT válido sin Perfil_Activo, THEN THE Backend SHALL responder 403 sin ejecutar la operación. _(CA-03, DP-PRO-01)_
4. IF el Perfil_Activo tiene un Rol_Sin_Acceso_SGA, THEN THE Backend SHALL responder 403 a cualquier endpoint del SGA sin ejecutar la operación ni modificar datos. _(CA-03, NF-02)_
5. IF un Actor sin permiso sobre un endpoint del SGA solicita un recurso inexistente, THEN THE Backend SHALL responder 403, sin revelar si el recurso existe. _(CA-06, `security.md`)_
6. THE Backend SHALL tomar el actor de `actualizadoPor`, `estadoActualizadoPor` y `registradoPor` del claim `sub` del JWT validado. _(DP-SEG-06)_
7. WHILE el Actor tiene el rol `OPERACIONES`, THE Backend SHALL conservar la Vista_Reducida_Cliente en `GET /api/crm/clientes` y `GET /api/crm/clientes/{id}`, la lectura de `GET /api/crm/contratos` y `GET /api/crm/contratos/{id}` y la respuesta 403 en las escrituras del CRM. _(DP-PRO-01, SGA-05)_

### Requirement 19: Respuestas de error del SGA (CA-06, DP-API-02, AUTH-05)

**User Story:** Como usuario, quiero mensajes de error comprensibles cuando una operación del SGA falla, para saber qué corregir.

#### Acceptance Criteria

1. THE Backend SHALL responder los errores del SGA con Problem_Details y mensajes en español. _(DP-API-02, DP-EST-02)_
2. IF la validación de un DTO del SGA falla, THEN THE Backend SHALL responder 400 con una entrada en `errors` por cada campo inválido, con `field` y un `message` comprensible. _(DP-API-02, NF-06)_
3. IF el cuerpo es un JSON mal formado, un campo tiene un tipo incorrecto o un valor de enumeración desconocido, THEN THE Backend SHALL responder 400 sin exponer detalles del deserializador. _(CA-06)_
4. IF el servicio del SGA detecta un conflicto de negocio, THEN THE Backend SHALL responder 409 con un `detail` que describa el conflicto y la extensión `conflicto` con uno de los códigos `DRON_CODIGO_DUPLICADO`, `PAQUETE_CODIGO_DUPLICADO`, `ARTICULO_CODIGO_DUPLICADO`, `CLIENTE_INACTIVO`, `CONTRATO_DE_OTRO_CLIENTE`, `CONTRATO_NO_ACTIVO`, `PAQUETE_NO_EDITABLE`, `TRANSICION_NO_PERMITIDA`, `DRON_NO_DISPONIBLE`, `DRON_CON_PAQUETE_EN_CURSO`, `TRANSICION_DRON_NO_PERMITIDA` o `EXISTENCIA_INSUFICIENTE`. _(SGA-01 a SGA-05, CA-06)_
5. THE Backend SHALL incluir en los 409 del SGA la extensión `campo` cuando el conflicto corresponde a un campo (`codigo`, `clienteId`, `contratoId`, `dronId`, `estado` o `cantidad`) y las extensiones `estadoActual`, `estadoSolicitado`, `paqueteId`, `existenciaActual` y `cantidadSolicitada` en los conflictos que las definen en los Requirements 6, 7, 9, 10, 11 y 15. _(DP-API-02)_
6. IF una referencia del cuerpo no existe, THEN THE Backend SHALL responder 404 con la extensión `campo` con el nombre del campo que la originó (`clienteId`, `contratoId` o `dronId`). _(CA-06, NF-06)_
7. IF la Base_Datos rechaza una operación del SGA por una restricción, por ejemplo por un código duplicado concurrente, THEN THE Backend SHALL responder 409 con un mensaje genérico, sin exponer SQL ni nombres de restricciones. _(NF-03, CA-06)_
8. THE Backend SHALL excluir de todas las respuestas de error del SGA las trazas de pila, el SQL, los nombres de clases y los datos internos. _(CA-06)_

### Requirement 20: Servicios y navegación SGA del frontend (NF-02, AUTH-05, DP-PRO-01)

**User Story:** Como usuario autenticado, quiero ver en el menú y en el panel principal solo las opciones del SGA que corresponden a mi rol, para llegar a mis tareas sin encontrar opciones que no puedo usar.

#### Acceptance Criteria

1. THE Servicio_SGA_Frontend SHALL enviar las peticiones a los endpoints del SGA mediante el Cliente_HTTP, con el token de la sesión vigente. _(AUTH-03)_
2. THE Servicio_SGA_Frontend SHALL enviar las cantidades y el stock mínimo como enteros JSON e incluir `dronId` solo en la transición a `ASIGNADO`. _(DP-API-04, SGA-05)_
3. THE Servicio_SGA_Frontend SHALL incluir en los parámetros de consulta solo los filtros que tienen valor. _(DP-API-03)_
4. THE Frontend SHALL tratar las respuestas 400, 401, 403, 404, 409, 5xx y el tiempo de espera agotado del SGA con el comportamiento del Cliente_HTTP definido en `002-crm-clientes-contratos`. _(AUTH-05, NF-06)_
5. WHILE el usuario tiene Rol_Gestor_SGA, THE Frontend SHALL mostrar en el menú las opciones Drones, Paquetes, Inventario e Indicadores SGA. _(DP-PRO-01)_
6. WHILE el usuario tiene el rol `OPERACIONES`, THE Frontend SHALL conservar en el menú las opciones Clientes y Contratos en modo consulta y omitir las demás opciones del CRM y las del ERP. _(DP-PRO-01, SGA-05)_
7. WHILE el usuario tiene un Rol_Sin_Acceso_SGA, THE Frontend SHALL omitir del menú y del panel principal las opciones del SGA. _(DP-PRO-01)_
8. WHILE el usuario tiene Rol_Gestor_SGA, THE Frontend SHALL mostrar en el panel principal (`Panel.jsx`) tarjetas de acceso a Drones, Paquetes, Inventario e Indicadores SGA. _(SGA-06, DP-PRO-01)_
9. IF el usuario abre directamente la ruta de una vista SGA sin Rol_Gestor_SGA, THEN THE Frontend SHALL mostrar un mensaje de falta de permisos sin solicitar datos de esa vista. _(AUTH-05)_
10. THE Frontend SHALL tratar la visibilidad de menús y controles como ayuda de interfaz y mostrar el mensaje de falta de permisos cuando el Backend responda 403. _(NF-02, AUTH-05)_
11. THE Frontend SHALL conservar las rutas `/login`, `/perfil` y `/panel`, las vistas CRM y ERP y su comportamiento para cada rol. _(AUTH-01, AUTH-02, CRM-01, ERP-01)_

### Requirement 21: Vistas de drones (SGA-01, NF-06)

**User Story:** Como usuario de Operaciones, quiero pantallas para listar, registrar, editar y consultar drones y cambiar su estado operativo, para gestionar la flota sin usar la API directamente.

#### Acceptance Criteria

1. THE Frontend SHALL mostrar una lista paginada de drones con búsqueda por código o modelo, filtro por Estado_Dron y controles de página, a partir de `GET /api/sga/drones`. _(SGA-01, DP-API-03)_
2. WHILE la lista de drones se está cargando, THE Frontend SHALL mostrar un indicador de carga. _(NF-06)_
3. WHEN la lista de drones no tiene resultados, THE Frontend SHALL mostrar un mensaje que indique que no hay drones para los criterios elegidos. _(NF-06)_
4. THE Frontend SHALL presentar un formulario de alta y edición de drones con `codigo` y `modelo` marcados como obligatorios y `observaciones` opcional. _(SGA-01, NF-06)_
5. IF el usuario envía el formulario de Dron con campos obligatorios vacíos, un código con formato inválido o valores que superan los límites de los supuestos, THEN THE Frontend SHALL mostrar un mensaje junto a cada campo inválido sin enviar la solicitud. _(NF-06)_
6. WHILE el envío de un formulario está en curso, THE Frontend SHALL deshabilitar el botón de envío y mostrar un indicador de carga. _(NF-06)_
7. WHEN el Backend responde 400 con errores por campo o 409 con `campo`, THE Frontend SHALL mostrar cada mensaje junto a su campo y conservar los valores introducidos. _(NF-06, AUTH-05)_
8. THE Frontend SHALL mostrar una vista de detalle del Dron con sus datos, su Estado_Dron, la auditoría de su último cambio de estado y un enlace al detalle de su Paquete_En_Curso cuando existe. _(SGA-01, SGA-05)_
9. WHILE el Dron no está en `EN_MISION` ni tiene Paquete_En_Curso, THE Frontend SHALL ofrecer en el detalle solo las Transicion_Manual_Dron desde su estado actual. _(SGA-01)_
10. WHILE el Dron está en `EN_MISION` o tiene un Paquete_En_Curso, THE Frontend SHALL ocultar el cambio manual de estado e indicar el paquete que lo impide. _(SGA-01, SGA-05)_
11. WHEN el usuario elige un cambio manual de estado, THE Frontend SHALL pedir confirmación, enviar `PATCH /api/sga/drones/{id}/estado` y mostrar el nuevo estado al recibir 200. _(SGA-01, NF-06)_
12. WHEN el Backend responde 409 a un cambio manual de estado, THE Frontend SHALL mostrar el mensaje y volver a cargar el Dron. _(AUTH-05)_

### Requirement 22: Vistas de paquetes y su trazabilidad (SGA-02, SGA-04, SGA-05, NF-06)

**User Story:** Como usuario de Operaciones, quiero registrar paquetes asociados a clientes y contratos, asignarles un dron, avanzarlos por sus estados y consultar su historial, para seguir cada entrega de principio a fin.

#### Acceptance Criteria

1. THE Frontend SHALL mostrar una lista paginada de paquetes con filtro por Estado_Paquete, búsqueda por código o descripción y controles de página, con código, descripción, nombre del Cliente, código del Dron y estado. _(SGA-02, DP-API-03)_
2. WHILE la lista de paquetes se está cargando, THE Frontend SHALL mostrar un indicador de carga. _(NF-06)_
3. WHEN la lista de paquetes no tiene resultados, THE Frontend SHALL mostrar un mensaje que indique que no hay paquetes para los criterios elegidos. _(NF-06)_
4. THE Frontend SHALL presentar un formulario de alta de paquetes con `codigo`, `descripcion`, `direccionEntrega` y Cliente marcados como obligatorios, un selector de clientes activos con búsqueda obtenido con `GET /api/crm/clientes?activo=true` mediante el Servicio_CRM_Frontend, un selector opcional de Contrato y una observación opcional. _(SGA-02, SGA-05, NF-06)_
5. WHILE el usuario ha elegido un Cliente en el formulario, THE Frontend SHALL ofrecer en el selector de Contrato solo los contratos en `ACTIVO` de ese Cliente, obtenidos con `GET /api/crm/contratos?clienteId={id}&estado=ACTIVO`. _(SGA-05)_
6. WHEN el usuario cambia el Cliente en el formulario, THE Frontend SHALL quitar la selección de Contrato. _(SGA-05)_
7. WHILE el Paquete está en un Estado_Editable_Paquete, THE Frontend SHALL ofrecer su edición con el código y el Cliente en modo lectura y la descripción, la dirección de entrega y el Contrato editables. _(SGA-02)_
8. WHILE se edita un Paquete cuyo Contrato actual ya no está en `ACTIVO`, THE Frontend SHALL mostrar ese Contrato como seleccionado e indicar su Estado_Contrato. _(SGA-05)_
9. IF el usuario envía el formulario de Paquete con campos obligatorios vacíos, un código con formato inválido o valores que superan los límites de los supuestos, THEN THE Frontend SHALL mostrar un mensaje junto a cada campo inválido sin enviar la solicitud. _(NF-06)_
10. WHEN el Backend responde 400, 404 o 409 con errores por campo o con `campo`, THE Frontend SHALL mostrar cada mensaje junto a su campo y conservar los valores introducidos. _(NF-06, AUTH-05)_
11. THE Frontend SHALL mostrar una vista de detalle del Paquete con sus datos, su estado, enlaces al detalle del Cliente (`/crm/clientes/{id}`), del Contrato (`/crm/contratos/{id}`) y del Dron, y el Historial_Paquete en orden cronológico con estado anterior, estado nuevo, Dron, observación, fecha y hora y actor de cada registro. _(SGA-04, SGA-05, CA-05)_
12. WHILE el Paquete no está en un Estado_Final_Paquete, THE Frontend SHALL ofrecer solo las Transicion_Permitida_Paquete desde su estado actual, cada una con una observación opcional. _(SGA-04, DP-API-05)_
13. WHEN el usuario elige la transición a `ASIGNADO`, THE Frontend SHALL exigir la elección de un Dron_Asignable obtenido con `GET /api/sga/drones?asignable=true` antes de enviar la solicitud. _(SGA-05)_
14. WHEN el usuario elige una transición hacia un Estado_Final_Paquete, THE Frontend SHALL pedir una confirmación que indique que el cambio es irreversible antes de enviar `POST /api/sga/paquetes/{id}/estados`. _(SGA-04, NF-06)_
15. WHILE el Paquete está en un Estado_Final_Paquete, THE Frontend SHALL ocultar la edición y las transiciones. _(SGA-04)_
16. WHEN el Backend responde 201 a un cambio de estado, THE Frontend SHALL volver a cargar el Paquete y su Historial_Paquete. _(SGA-04)_
17. WHEN el Backend responde 409 a una edición o a un cambio de estado, THE Frontend SHALL mostrar el mensaje recibido y volver a cargar el Paquete y su Historial_Paquete. _(SGA-04, AUTH-05)_

### Requirement 23: Vistas de inventario (SGA-03, NF-06)

**User Story:** Como usuario de Operaciones, quiero pantallas para gestionar el catálogo de artículos y registrar sus movimientos, para controlar la existencia sin usar la API directamente.

#### Acceptance Criteria

1. THE Frontend SHALL mostrar una lista paginada de artículos con búsqueda por código o nombre, filtro por Categoria_Articulo, filtro de artículos Bajo_Stock_Minimo y controles de página, con código, nombre, categoría, Existencia con su unidad y Stock_Minimo. _(SGA-03, DP-API-03)_
2. THE Frontend SHALL marcar en la lista y en el detalle los artículos en condición Bajo_Stock_Minimo. _(SGA-03, SGA-06)_
3. WHILE la lista de artículos se está cargando, THE Frontend SHALL mostrar un indicador de carga. _(NF-06)_
4. WHEN la lista de artículos no tiene resultados, THE Frontend SHALL mostrar un mensaje que indique que no hay artículos para los criterios elegidos. _(NF-06)_
5. THE Frontend SHALL presentar un formulario de alta y edición de artículos con `codigo`, `nombre`, un selector de Categoria_Articulo, un selector de Unidad_Articulo y `stockMinimo`, todos marcados como obligatorios, sin campo de Existencia. _(SGA-03, NF-06)_
6. IF el usuario envía el formulario de Articulo con campos obligatorios vacíos, un código con formato inválido, un nombre de más de 200 caracteres o un stock mínimo que no es un entero entre 0 y 1 000 000, THEN THE Frontend SHALL mostrar un mensaje junto a cada campo inválido sin enviar la solicitud. _(NF-06)_
7. WHEN el Backend responde 400 o 409 con errores por campo o con `campo` al formulario de Articulo, THE Frontend SHALL mostrar cada mensaje junto a su campo y conservar los valores introducidos. _(NF-06, AUTH-05)_
8. THE Frontend SHALL mostrar en el detalle del Articulo un formulario de movimiento con tipo, cantidad y observación, que indique en un `AJUSTE` que la cantidad es la existencia contada y que la observación es obligatoria. _(SGA-03, NF-06)_
9. IF el usuario envía el formulario de movimiento con una cantidad que no es un entero dentro del rango de su tipo o con un `AJUSTE` sin observación, THEN THE Frontend SHALL mostrar un mensaje junto al campo inválido sin enviar la solicitud. _(SGA-03, NF-06)_
10. WHEN el Backend responde 201 a un movimiento, THE Frontend SHALL mostrar la Existencia resultante y volver a cargar la lista de movimientos. _(SGA-03)_
11. IF el Backend responde 409 `EXISTENCIA_INSUFICIENTE`, THEN THE Frontend SHALL mostrar el mensaje con la Existencia disponible, conservar los valores introducidos y volver a cargar el Articulo. _(SGA-03, AUTH-05)_
12. THE Frontend SHALL mostrar en el detalle del Articulo la lista paginada de sus movimientos con fecha y hora, tipo, cantidad, Existencia anterior, Existencia resultante y observación. _(SGA-03)_

### Requirement 24: Vista de indicadores del SGA (SGA-06, NF-06)

**User Story:** Como usuario de Operaciones, quiero una pantalla con los indicadores de paquetes, drones e inventario, para revisar la situación de la operación en un solo lugar.

#### Acceptance Criteria

1. WHILE el usuario tiene Rol_Gestor_SGA, THE Frontend SHALL mostrar un panel con la cantidad de paquetes por Estado_Paquete y su total, la cantidad de drones por Estado_Dron y su total, y la cantidad de artículos Bajo_Stock_Minimo y el total de artículos del Resumen_SGA. _(SGA-06)_
2. THE Frontend SHALL mostrar en el panel los seis Estado_Paquete y los cuatro Estado_Dron, incluidos los que tienen cantidad 0. _(SGA-06)_
3. THE Frontend SHALL ofrecer en el panel un enlace a la lista de artículos filtrada por Bajo_Stock_Minimo. _(SGA-06)_
4. WHILE la consulta del panel está en curso, THE Frontend SHALL mostrar un indicador de carga. _(NF-06)_
5. IF la consulta del panel falla, THEN THE Frontend SHALL mostrar un mensaje de error con la opción de reintentar. _(NF-06, AUTH-05)_

### Requirement 25: Pruebas unitarias y de seguridad del backend (ENG-02, NF-07, CA-03)

**User Story:** Como integrante del equipo, quiero pruebas automatizadas de las reglas de negocio y los permisos del SGA, limitadas a los casos necesarios, para detectar regresiones sin multiplicar pruebas redundantes.

#### Acceptance Criteria

1. THE Suite_Backend SHALL incluir pruebas unitarias con Mockito de los servicios del SGA que cubran la creación del Paquete con su primer Registro_Historial, la asociación con un Cliente inactivo y con un Contrato de otro Cliente o fuera de `ACTIVO`, la conservación del Contrato actual en la edición, la edición solo en Estado_Editable_Paquete, la asignación de un Dron no asignable, la sincronización del Estado_Dron en cada transición, el cambio manual de un Dron con Paquete_En_Curso, la `SALIDA` con Existencia insuficiente, el `AJUSTE` sin observación y el registro del `sub` del Actor en la auditoría, el historial y los movimientos. _(ENG-02, NF-07)_
2. THE Suite_Backend SHALL verificar de forma exhaustiva las 36 combinaciones de estado de origen y destino del Paquete y las 16 del cambio manual del Dron, y comprobar que solo se aceptan las Transicion_Permitida_Paquete y las Transicion_Manual_Dron. _(NF-07, DP-API-05)_
3. THE Suite_Backend SHALL verificar sobre secuencias variadas de Movimiento_Inventario, generadas con una semilla fija, que la Existencia final coincide con un modelo de referencia, que ninguna salida aceptada deja la Existencia por debajo de 0 y que una salida rechazada no cambia la Existencia. _(NF-07, SGA-03)_
4. THE Suite_Backend SHALL incluir para cada endpoint del SGA pruebas MockMvc de: sin token (401), JWT sin perfil (403), perfil inactivo (403), `COMERCIAL` (403), `FINANZAS_RRHH` (403), `OPERACIONES` (éxito) y `ADMINISTRADOR` (éxito), agrupadas en una prueba parametrizada. _(CA-03, NF-07, `security.md`)_
5. THE Suite_Backend SHALL obtener las authorities de las pruebas de seguridad con `PerfilJwtAuthenticationConverter` a partir de perfiles persistidos en H2, con JWT simulados y sin tokens reales de Supabase. _(CA-03, DP-TEC-02)_
6. THE Suite_Backend SHALL verificar con MockMvc un caso por cada código de conflicto del Requirement 19, la respuesta 404 de cada referencia del cuerpo (`clienteId`, `contratoId` y `dronId`), una respuesta 400 por cada DTO de entrada con sus errores por campo y la respuesta 405 de los `DELETE`, sin repetir las pruebas de normalización, paginación y traducción genérica de errores que ya cubren `002` y `003`. _(NF-07, CA-06)_
7. THE Suite_Backend SHALL incluir una prueba que, al crear y editar paquetes y al provocar respuestas 409 y 500 con una dirección de entrega, una observación y un nombre de Cliente ficticios conocidos, verifique que la salida de logs no contiene esos valores. _(`security.md`, NF-07)_
8. THE Suite_Backend SHALL sustituir el endpoint de prueba SGA de `AutorizacionModulosTests` (`GET /api/sga/paquetes`) por pruebas sobre los controladores reales en `SgaAutorizacionTests`, en el mismo cambio que crea el controlador de paquetes. _(CA-03)_
9. THE Suite_Backend SHALL conservar en verde las pruebas existentes que verifican que `OPERACIONES` recibe la Vista_Reducida_Cliente, lee contratos y recibe 403 en las escrituras del CRM. _(CA-03, DP-PRO-01)_
10. WHEN se ejecuta `.\mvnw.cmd test` desde `backend/`, THE Suite_Backend SHALL pasar sin Docker, sin Supabase y sin el archivo `.env`, incluidas las pruebas existentes de `001-foundation`, `002-crm-clientes-contratos` y `003-erp-empleados-proveedores-finanzas`. _(ENG-02, DP-TEC-02)_

### Requirement 26: Pruebas de integración con PostgreSQL (ENG-03, DP-TEC-04, DP-EST-05)

**User Story:** Como responsable de las pruebas de integración, quiero ejecutar el SGA contra un PostgreSQL efímero con las migraciones reales, para comprobar restricciones, transacciones, concurrencia y el flujo integrado sin tocar la base compartida.

#### Acceptance Criteria

1. THE Suite_Integracion SHALL aplicar en orden de timestamp todas las migraciones de `supabase/migrations/`, incluida la Migracion_SGA, sobre el PostgreSQL efímero preparado con el Script_Preparacion_IT, sin copiarlas ni modificarlas. _(ENG-03, DP-TEC-03)_
2. WHEN el contexto de Spring arranca contra el PostgreSQL efímero, THE Suite_Integracion SHALL verificar que `ddl-auto=validate` acepta las entidades del SGA. _(DP-TEC-03)_
3. THE Suite_Integracion SHALL verificar en `MigracionesSgaIT` la unicidad de los códigos, las restricciones `CHECK` de estados, categorías, unidades, cantidades, coherencia entre el Dron y el Estado_Paquete y aritmética de los movimientos, las claves foráneas a `app.clientes`, `app.contratos`, `app.drones`, `app.paquetes` y `app.articulos_inventario`, y el índice único parcial de un Paquete_En_Curso por Dron. _(NF-03, ENG-03)_
4. THE Suite_Integracion SHALL verificar que las tablas del SGA tienen RLS habilitado y que `anon` y `authenticated` carecen de privilegios sobre ellas. _(DP-SEG-05)_
5. THE Suite_Integracion SHALL verificar en `FlujoSgaIT`, contra PostgreSQL y por la API, el flujo integrado: Cliente activo con Contrato en `ACTIVO`, Paquete asociado a ambos, transiciones `ASIGNADO`, `EN_TRANSITO` y `ENTREGADO`, Historial_Paquete con cuatro registros encadenados y el `sub` del Actor, Dron en `DISPONIBLE`, `EN_MISION` y de nuevo `DISPONIBLE`, y los conteos del Resumen_SGA. _(CA-05, SGA-02, SGA-04, SGA-05, SGA-06)_
6. THE Suite_Integracion SHALL verificar los recorridos `EN_TRANSITO→DEVUELTO` y `ASIGNADO→CANCELADO`, con el Dron de nuevo como Dron_Asignable al terminar cada uno. _(SGA-04, SGA-05)_
7. THE Suite_Integracion SHALL verificar que un fallo durante un cambio de estado no deja cambios parciales en el Paquete, el Historial_Paquete ni el Dron, y que un fallo durante un movimiento no deja cambios parciales en la Existencia. _(DP-SEG-06, ENG-03)_
8. THE Suite_Integracion SHALL verificar que dos asignaciones simultáneas del mismo Dron aceptan como máximo una y que dos `SALIDA` simultáneas que juntas superan la Existencia aceptan solo las que caben, sin Existencia negativa. _(SGA-03, SGA-05, NF-03)_
9. THE Suite_Integracion SHALL verificar contra PostgreSQL, con un conjunto pequeño de datos, los filtros por estado, Cliente y Dron de los paquetes, el filtro `asignable` de los drones y el filtro `bajoStockMinimo` de los artículos. _(ENG-03, DP-API-03)_
10. THE Suite_Integracion SHALL vaciar las tablas del SGA, del ERP y del CRM antes de cada prueba, para que el resultado de una prueba no dependa de otra. _(ENG-03)_
11. THE Suite_Integracion SHALL ejecutarse sin credenciales de la Base_Compartida, sin el archivo `.env` y sin conexión a Supabase. _(ENG-03, NF-01)_
12. WHEN se ejecuta `.\mvnw.cmd verify` desde `backend/` con Docker disponible, THE Suite_Integracion SHALL pasar junto con la Suite_Backend y con las pruebas de integración existentes del CRM y del ERP. _(ENG-03)_

### Requirement 27: Pruebas del frontend (ENG-02, ENG-03)

**User Story:** Como responsable del frontend, quiero pruebas automatizadas de las vistas SGA con más reglas y de su servicio, para asegurar que la interfaz se comporta según el contrato de la API sin probar cada pantalla de forma exhaustiva.

#### Acceptance Criteria

1. THE Suite_Frontend SHALL incluir pruebas unitarias del Servicio_SGA_Frontend para las rutas, los métodos, los parámetros de consulta y los cuerpos JSON, incluidas las cantidades enteras y `dronId` solo en la transición a `ASIGNADO`. _(ENG-02)_
2. THE Suite_Frontend SHALL incluir pruebas unitarias de los permisos y opciones de menú SGA para los cuatro roles, de las transiciones ofrecidas para cada Estado_Paquete y cada Estado_Dron, y de las validaciones locales de los formularios del SGA. _(ENG-02, DP-PRO-01)_
3. THE Suite_Frontend SHALL incluir pruebas del formulario de Paquete que cubran el selector de contratos en `ACTIVO` del Cliente elegido, la limpieza del Contrato al cambiar de Cliente, la validación local y los errores por campo del Backend. _(ENG-02, SGA-05, NF-06)_
4. THE Suite_Frontend SHALL incluir pruebas del detalle del Paquete que cubran las transiciones según el estado, la elección obligatoria de un Dron_Asignable para `ASIGNADO`, la confirmación irreversible, la recarga tras un 409 y el Historial_Paquete en orden cronológico. _(ENG-03, SGA-04, SGA-05)_
5. THE Suite_Frontend SHALL incluir pruebas del formulario de movimiento de inventario que cubran el `AJUSTE` sin observación y el 409 `EXISTENCIA_INSUFICIENTE`, y pruebas del detalle del Dron y del panel de indicadores que cubran el bloqueo del cambio manual con Paquete_En_Curso y los estados con cantidad 0. _(ENG-02, SGA-01, SGA-03, SGA-06)_
6. THE Suite_Frontend SHALL incluir una prueba de integración de la navegación por rol que verifique el menú SGA y la denegación de una ruta SGA sin Rol_Gestor_SGA sin solicitar sus datos. _(ENG-03, DP-PRO-01)_
7. THE Suite_Frontend SHALL simular la API y Supabase Auth, sin llamadas de red reales. _(ENG-03)_
8. WHEN se ejecuta `npm test` desde `frontend/`, THE Suite_Frontend SHALL pasar, incluidas las pruebas existentes de `001-foundation`, `002-crm-clientes-contratos` y `003-erp-empleados-proveedores-finanzas`. _(ENG-02)_
9. WHEN se ejecutan `npm run build` y `npm run check:secrets` desde `frontend/`, THE Frontend SHALL completar ambos comandos sin errores. _(NF-01)_

### Requirement 28: Construcción Docker e integración continua (ENG-01, ENG-04)

**User Story:** Como responsable de CI, quiero que la imagen Docker y los workflows existentes validen el SGA sin cambios de infraestructura, para que `main` solo reciba cambios verificados.

#### Acceptance Criteria

1. WHEN se ejecuta `docker build` sobre `backend/` con el código del SGA, THE Imagen_Backend SHALL construirse sin credenciales, sin conexión a Supabase y sin ejecutar la Suite_Integracion. _(ENG-01, ENG-04)_
2. THE Imagen_Backend SHALL contener cero archivos `.env`, credenciales y argumentos `ARG` o `ENV` con valores secretos. _(NF-01)_
3. WHEN se ejecuta `docker compose up backend` con un `backend/.env` válido y la Migracion_SGA aplicada a la Base_Compartida, THE Backend SHALL atender en `http://localhost:8080` y responder 401 a `GET /api/sga/drones` sin token. _(ENG-01, CA-01, CA-02)_
4. THE Pipeline_CI SHALL validar esta spec con los workflows `backend-ci.yml`, `integration-tests.yml` y `frontend-ci.yml` existentes, sin workflows nuevos ni GitHub Secrets. _(ENG-04, NF-01)_
5. WHEN se hace push a la rama de la spec o se abre o actualiza el Pull Request hacia `main`, THE Pipeline_CI SHALL ejecutar la Suite_Backend, la Suite_Integracion con las pruebas del SGA, la Suite_Frontend y la construcción de la Imagen_Backend. _(ENG-02, ENG-03, ENG-04)_
6. IF un paso de un workflow falla, THEN THE Pipeline_CI SHALL marcar como fallida la ejecución de ese workflow. _(ENG-04)_
7. THE Equipo SHALL conservar los tres workflows del Pipeline_CI como verificaciones obligatorias en la protección de `main`, de modo que un workflow fallido impida integrar el Pull Request. _(ENG-04, DP-SDD-05)_
8. WHERE la implementación necesita cambiar un workflow, THE Equipo SHALL justificar el cambio en `design.md` y conservar `permissions: contents: read`, las acciones oficiales `actions/*` y la ausencia de secretos. _(ENG-04, `security.md`)_

### Requirement 29: Documentación, steering y tareas de cierre (CA-01, CA-07, NF-05)

**User Story:** Como integrante del equipo, quiero documentación y reglas actualizadas al cerrar la spec, para que cualquiera pueda ejecutar, probar y revisar el SGA y el flujo integrado.

#### Acceptance Criteria

1. THE Equipo SHALL documentar en `design.md` el contrato de cada endpoint del SGA con requisitos, roles, solicitud, respuesta y errores. _(DP-API-06)_
2. WHEN el diseño define el Servicio_Contratos_CRM, THE Equipo SHALL registrarlo en `architecture.md` (DP-ARQ-01) junto al Servicio_Clientes_CRM antes de programarlo. _(DP-ARQ-01, `sdd-workflow.md`)_
3. THE Equipo SHALL actualizar el README con el estado del proyecto y, en §11.6, las rutas del frontend SGA y los roles que las usan. _(CA-01)_
4. THE Equipo SHALL actualizar los steering `product.md` ("Estado actual del proyecto"), `architecture.md` (componentes y módulo `sga`), `structure.md` (paquete `sga`, vistas SGA, `sgaApi.js` y migración), `api-conventions.md` (endpoints existentes y códigos de conflicto del SGA), `tech.md` (persistencia) y `security.md` (datos personales del SGA y prueba de logs) para reflejar el SGA como implementado. _(CA-07, `product.md`)_
5. THE Equipo SHALL registrar la Migracion_SGA en la tabla de `supabase/migrations/README.md`. _(NF-05)_
6. IF la implementación requiere una dependencia o una variable de entorno nueva, THEN THE Equipo SHALL justificarla en `design.md` antes de añadirla y, para una variable, añadirla a `.env.example` con un valor ficticio y documentarla en el README. _(NF-01, CA-01, `tech.md`)_
7. THE Equipo SHALL confirmar el trabajo en la rama `feat/sga-drones-paquetes-inventario` con commits Conventional Commits y abrir un único Pull Request hacia `main` al completar la spec, con la lista de comprobación de `sdd-workflow.md`. _(DP-SDD-04, DP-SDD-05)_
8. WHEN un integrante distinto del autor ha revisado la Migracion_SGA y `MigracionesSgaIT` la ha validado en PostgreSQL efímero, THE Equipo SHALL aplicarla de forma controlada a la Base_Compartida antes de la validación manual del Requirement 30 y del Pull Request final, sin editarla después de aplicada. _(DP-TEC-03)_

### Requirement 30: Validación extremo a extremo del flujo integrado (CA-03, CA-04, CA-05, AUTH-05)

**User Story:** Como equipo, quiero demostrar con Supabase real el flujo cliente → contrato → paquete → cambio de estado y trazabilidad, para cumplir el criterio central del MVP.

#### Acceptance Criteria

1. WHEN la Migracion_SGA está aplicada a la Base_Compartida, THE Equipo SHALL validar manualmente, con el Backend ejecutado mediante Compose_Local y el Frontend en local, que un usuario `COMERCIAL` registra un Cliente con un Contrato en `ACTIVO` y que un usuario `OPERACIONES` registra un Dron, registra un Paquete asociado a ese Cliente y a ese Contrato, lo asigna al Dron, lo pasa a `EN_TRANSITO` y a `ENTREGADO` y, después de recargar la aplicación, consulta el Historial_Paquete y los datos del Cliente y del Contrato desde el detalle del Paquete. _(CA-04, CA-05, SGA-01, SGA-02, SGA-04, SGA-05)_
2. WHEN el usuario `OPERACIONES` registra durante la validación manual una `ENTRADA` y una `SALIDA` de un Articulo y una `SALIDA` mayor que la Existencia, THE Backend SHALL aplicar las dos primeras, responder 409 a la tercera y reflejar la Existencia y los indicadores del Resumen_SGA después de recargar. _(CA-04, SGA-03, SGA-06)_
3. WHEN un usuario `COMERCIAL` o `FINANZAS_RRHH` intenta una operación del SGA durante la validación manual, THE Backend SHALL responder 403 sin modificar datos. _(CA-03, NF-02)_
4. WHEN un usuario `OPERACIONES` intenta una escritura del CRM durante la validación manual, THE Backend SHALL responder 403 sin modificar datos. _(CA-03, DP-PRO-01)_
5. WHEN el Backend responde 403 durante la validación manual, THE Frontend SHALL mostrar el mensaje de falta de permisos sin cerrar la sesión. _(AUTH-05)_
6. THE Equipo SHALL registrar la evidencia de la validación manual en `tasks.md` sin credenciales, tokens, UUID personales ni datos personales reales. _(NF-01, CA-07)_
