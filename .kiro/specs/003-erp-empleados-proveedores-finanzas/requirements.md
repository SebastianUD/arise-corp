# Requirements Document

## Introduction

Esta es la tercera especificación del proyecto Arise Corp. (`003-erp-empleados-proveedores-finanzas`). Construye el módulo ERP (empleados, proveedores, movimientos financieros básicos y panel financiero) con las mismas convenciones del CRM de `002-crm-clientes-contratos`, que es el patrón de referencia (`architecture.md`, "Orden de construcción", paso 5). Los movimientos financieros se relacionan con clientes del CRM y con proveedores del ERP. La spec reutiliza la infraestructura existente (Docker, Docker Compose, Testcontainers y los tres workflows de CI) y amplía sus pruebas.

Requisitos del MVP cubiertos:

- **Must:** ERP-01 (empleados), ERP-02 (proveedores) y ERP-03 (movimientos financieros básicos).
- **Should incluidos por decisión del equipo:** ERP-04 (panel financiero) y ERP-05 (relación de los movimientos con clientes y proveedores).
- **No funcionales:** NF-01 a NF-07.
- **Criterios globales:** CA-01, CA-02 (rechazo de tokens inválidos en los endpoints ERP), CA-03, CA-04 (operaciones mínimas del ERP), CA-06 y CA-07.
- **Ingeniería:** ENG-01 (la imagen Docker sigue construyendo con el ERP), ENG-02 (pruebas unitarias), ENG-03 (pruebas de integración) y ENG-04 (CI con los workflows existentes).
- **Autenticación y errores de interfaz:** AUTH-03 (token de la sesión en las peticiones ERP) y AUTH-05 aplicados a las vistas ERP; AUTH-01, AUTH-02 y CRM-01 solo como no regresión.

Decisiones aplicadas: DP-PRO-01, DP-PRO-02, DP-ARQ-01, DP-ARQ-02, DP-SEG-02, DP-SEG-05, DP-SEG-06, DP-API-01 a DP-API-06, DP-TEC-02, DP-TEC-03, DP-TEC-04, DP-EST-01 a DP-EST-05 y DP-SDD-03 a DP-SDD-05.

### Contexto de trabajo

- `main` contiene `001-foundation` y `002-crm-clientes-contratos` integradas. El trabajo se realiza en la rama `feat/erp-empleados-proveedores-finanzas`, creada desde `main`, con un único Pull Request al completar la spec (DP-SDD-05).
- La matriz de `SecurityConfig` ya cubre `/api/erp/**` para ADMINISTRADOR y FINANZAS_RRHH y no cambia; COMERCIAL y OPERACIONES reciben 403 en todo el ERP. La lectura cruzada que necesita ERP-05 (`GET /api/crm/clientes` y `GET /api/crm/clientes/*` para FINANZAS_RRHH, con la Vista_Reducida_Cliente) ya existe.
- `AutorizacionModulosTests` define el endpoint de prueba `GET /api/erp/proveedores`, que chocará con el controlador real de proveedores. Sus casos ERP se migran a pruebas de los controladores reales (`ErpAutorizacionTests`) y se conserva el endpoint de prueba SGA `GET /api/sga/paquetes`, con el mismo procedimiento que la tarea 14.1 de `002-crm-clientes-contratos`.
- `backend/Dockerfile`, `backend/.dockerignore`, `docker-compose.yml`, la base de pruebas de integración (`PostgresIntegracionBase`, `MigracionesSupabase` y el Script_Preparacion_IT) y los workflows `backend-ci.yml`, `integration-tests.yml` y `frontend-ci.yml` ya existen. Esta spec los reutiliza y solo los modifica si `design.md` justifica el cambio.
- La Migracion_ERP la prepara Davidson, la revisa otro integrante y Davidson la aplica de forma controlada a la Base_Compartida antes de la validación manual, según DP-TEC-03.
- No se prevén variables de entorno ni dependencias nuevas en backend ni en frontend.

### Fuera de alcance

Libro mayor, impuestos (IVA, retenciones), nómina y salarios, datos bancarios de empleados o proveedores, conciliación bancaria, cierres contables, facturación electrónica, numeración consecutiva de facturas, vencimientos y cartera, monedas distintas de COP, adjuntos, importación o exportación masiva, eliminación física de empleados, proveedores o movimientos, relación de movimientos con empleados, historial de cambios de los movimientos aparte de la auditoría del último cambio, funcionalidades de SGA, despliegue continuo y publicación de imágenes en registros externos.

### Endpoints y matriz de acceso del ERP

| Endpoint | ADMINISTRADOR | COMERCIAL | FINANZAS_RRHH | OPERACIONES |
|---|---|---|---|---|
| `GET /api/erp/empleados`, `GET /api/erp/empleados/{id}` | Permitido | 403 | Permitido | 403 |
| `POST /api/erp/empleados`, `PUT /api/erp/empleados/{id}`, `PATCH /api/erp/empleados/{id}` | Permitido | 403 | Permitido | 403 |
| `GET /api/erp/proveedores`, `GET /api/erp/proveedores/{id}` | Permitido | 403 | Permitido | 403 |
| `POST /api/erp/proveedores`, `PUT /api/erp/proveedores/{id}`, `PATCH /api/erp/proveedores/{id}` | Permitido | 403 | Permitido | 403 |
| `GET /api/erp/movimientos`, `GET /api/erp/movimientos/{id}` | Permitido | 403 | Permitido | 403 |
| `POST /api/erp/movimientos`, `PUT /api/erp/movimientos/{id}`, `PATCH /api/erp/movimientos/{id}/estado` | Permitido | 403 | Permitido | 403 |
| `GET /api/erp/resumen` | Permitido | 403 | Permitido | 403 |
| `DELETE /api/erp/empleados/{id}`, `DELETE /api/erp/proveedores/{id}`, `DELETE /api/erp/movimientos/{id}` | 405 | 403 | 405 | 403 |
| `GET /api/crm/clientes`, `GET /api/crm/clientes/{id}` (existentes; selector de cliente de ERP-05) | Vista completa | Vista completa | Vista reducida | Vista reducida |

### Supuestos adoptados

| Tema | Supuesto |
|---|---|
| Ubicación de los proveedores | Los proveedores son datos del ERP (`architecture.md`, tabla "Módulos"). La lectura cruzada de DP-PRO-01 se aplica solo a los clientes del CRM; FINANZAS_RRHH gestiona los proveedores como módulo dueño. |
| Empleado | Persona vinculada a Arise Corp. Tipo y número de documento, nombre, cargo, área, correo, teléfono y fecha de ingreso, todos obligatorios. Sin salario, datos bancarios ni otros datos de nómina. Se desactiva lógicamente con `activo`, igual que los clientes del CRM. |
| Tipos de documento del empleado | `CC`, `CE` y `PASAPORTE`. Se excluye `NIT` porque el empleado es una persona natural. |
| Proveedor | Persona natural o jurídica que suministra bienes o servicios. Tipo y número de documento, nombre o razón social, correo y teléfono obligatorios; nombre de contacto, dirección y ciudad opcionales. Correo y teléfono son obligatorios porque Finanzas necesita un medio de contacto para gestionar pagos, igual que el CRM exige ambos para los clientes. Se desactiva lógicamente con `activo`. |
| Tipos de documento del proveedor | `NIT`, `CC`, `CE` y `PASAPORTE`. |
| Normalización de documentos | Igual que en `002`: se eliminan espacios, puntos y guiones y las letras pasan a mayúsculas. `CC` y `NIT` admiten solo dígitos; `CE` y `PASAPORTE` admiten letras y dígitos. Longitud normalizada: 3 a 20 caracteres. |
| Unicidad | El par (tipo de documento, número normalizado) es único entre empleados activos e inactivos y, por separado, entre proveedores activos e inactivos. Un registro inactivo se reactiva en lugar de duplicarse; el 409 incluye su identificador. Un mismo documento puede existir a la vez como Empleado, Proveedor y Cliente. |
| Fecha de ingreso | `date` obligatoria, igual o anterior a la Fecha_Actual_Colombia. No se admiten ingresos futuros: el ERP registra empleados ya vinculados. |
| Movimiento financiero | Registro único de tipo `INGRESO` o `EGRESO` con fecha, concepto, valor COP, referencia opcional (por ejemplo, el número de una factura) y estado. Cubre las "facturas o movimientos" de ERP-03 sin numeración consecutiva, vencimientos ni estados de factura (`BORRADOR`, `EMITIDA`). |
| Fecha del movimiento | `date` obligatoria; se admite cualquier fecha ISO-8601 válida, incluidas fechas futuras para movimientos `PENDIENTE` previstos. |
| Concepto | Obligatorio, de 3 a 300 caracteres después de eliminar los espacios iniciales y finales. |
| Referencia | Opcional; se guarda sin espacios iniciales ni finales y en mayúsculas; de 1 a 50 caracteres entre letras A–Z, dígitos, espacios, puntos, barras y guiones. No es única. |
| Valor del movimiento | COP, `numeric(15,2)` / `BigDecimal`, mayor que 0, con hasta 13 dígitos enteros y 2 decimales; viaja como texto decimal JSON (DP-API-04). |
| Estados del movimiento | Nace en `PENDIENTE`. Transiciones: `PENDIENTE→PAGADO` y `PENDIENTE→ANULADO`. `PAGADO` y `ANULADO` son finales. Un movimiento no se elimina: se anula. |
| Edición del movimiento | Solo en `PENDIENTE`. `PUT` reemplaza `tipo`, `fecha`, `concepto`, `valor`, `referencia`, `clienteId` y `proveedorId`; el estado no se edita con `PUT`. El tipo puede cambiar mientras el movimiento está `PENDIENTE` y la relación enviada debe ser coherente con el tipo enviado; una combinación incoherente responde 400. |
| Relación con cliente o proveedor (ERP-05) | Opcional. Un `INGRESO` puede asociarse a un Cliente del CRM y un `EGRESO` a un Proveedor; ninguno admite la relación del otro tipo. Una asociación nueva exige un Cliente o Proveedor activo. La asociación existente se conserva si el Cliente o el Proveedor se desactiva después, y una edición que la conserva se acepta. |
| Integración con el CRM | El ERP comprueba la existencia, el estado activo y el nombre del Cliente mediante el Servicio_Clientes_CRM, sin asociaciones JPA entre módulos; la clave foránea `cliente_id` se define en PostgreSQL (DP-ARQ-01). El movimiento muestra solo el identificador, el nombre y el estado activo del Cliente, que ya forman parte de la Vista_Reducida_Cliente. La relación con el Proveedor es interna del ERP. |
| Panel financiero | Para cada tipo de movimiento, la cantidad y la suma de valores por estado y la cantidad total del tipo; Saldo_Pagado = suma de `INGRESO` en `PAGADO` − suma de `EGRESO` en `PAGADO`. Filtro opcional por rango de fechas del movimiento (`desde`, `hasta`), con límites inclusivos. |
| Límites de texto | Nombre y razón social: 200; cargo: 100; área: 100; correo: 254; teléfono: 7 a 20 caracteres entre dígitos, espacios, `+`, `-` y paréntesis; dirección: 200; ciudad: 100; nombre de contacto: 200; concepto: 3 a 300; referencia: 50; búsqueda `q`: 100. |
| Auditoría | Las tres tablas guardan `creado_en`, `actualizado_en` y `actualizado_por` (`sub` del JWT, `uuid not null` sin clave foránea a `auth.users`). Los movimientos guardan además el instante y el `sub` del último cambio de estado (DP-SEG-06). |
| Vista de empleados y proveedores | Una única representación completa para ADMINISTRADOR y FINANZAS_RRHH. Ningún otro rol lee empleados ni proveedores; no hay lectura cruzada del ERP. |

## Glossary

- **Backend:** API Spring Boot 4.1.2 (monolito modular) que valida el JWT, aplica la autorización por rol, valida las entradas, ejecuta las reglas de negocio y persiste los datos. El módulo ERP vive en el paquete `com.arise.corp.erp` y el CRM en `com.arise.corp.crm`.
- **Frontend:** SPA React con Vite, Tailwind CSS y React Router que presenta las vistas CRM y ERP y llama al Backend.
- **Cliente_HTTP:** módulo `frontend/src/services/apiClient.js` que envía las peticiones del Frontend al Backend con el token de acceso y traduce los errores, según `002-crm-clientes-contratos`.
- **Servicio_ERP_Frontend:** módulo `frontend/src/services/erpApi.js` que agrupa las llamadas del Frontend a los endpoints del ERP mediante el Cliente_HTTP.
- **Base_Datos:** Supabase PostgreSQL; las tablas del ERP viven en el esquema privado `app` (DP-ARQ-02).
- **Base_Compartida:** instancia Supabase `arise-corp-dev` usada por el equipo para desarrollo.
- **Migracion_ERP:** archivo SQL nuevo `supabase/migrations/<timestamp>_app_erp_empleados_proveedores_movimientos.sql`, con un timestamp posterior a `20261111120000`, que crea los objetos del ERP (DP-TEC-03).
- **Empleado:** persona vinculada laboralmente a Arise Corp., identificada por tipo y número de documento.
- **Proveedor:** persona natural o jurídica que suministra bienes o servicios a Arise Corp., identificada por tipo y número de documento.
- **Cliente:** persona natural o jurídica registrada en `app.clientes`, cuyo módulo dueño es el CRM de `002-crm-clientes-contratos`.
- **Movimiento_Financiero:** registro de un ingreso o un egreso de Arise Corp. con fecha, concepto, valor COP, referencia opcional, estado y Relacion_Financiera opcional.
- **Tipo_Movimiento:** `INGRESO` o `EGRESO`.
- **Estado_Movimiento:** `PENDIENTE`, `PAGADO` o `ANULADO`.
- **Estado_Final_Movimiento:** `PAGADO` o `ANULADO`; un Movimiento_Financiero en Estado_Final_Movimiento no admite más transiciones ni ediciones.
- **Transicion_Permitida_Movimiento:** cambio de Estado_Movimiento incluido en el conjunto `PENDIENTE→PAGADO`, `PENDIENTE→ANULADO`.
- **Relacion_Financiera:** asociación opcional de un Movimiento_Financiero con un Cliente (solo para `INGRESO`) o con un Proveedor (solo para `EGRESO`) (ERP-05).
- **Servicio_Clientes_CRM:** servicio de la capa `service` del módulo CRM que el ERP usa para consultar la existencia, el estado activo y el nombre de los clientes (DP-ARQ-01).
- **Documento_Normalizado:** número de documento al que se han eliminado espacios, puntos y guiones y cuyas letras se han convertido a mayúsculas.
- **Referencia_Normalizada:** referencia de un Movimiento_Financiero sin espacios iniciales ni finales y con las letras en mayúsculas.
- **Fecha_Actual_Colombia:** fecha calendario actual en la zona horaria `America/Bogota`, calculada con el reloj de la aplicación.
- **Actor:** usuario autenticado que realiza una petición; se identifica por el claim `sub` del JWT validado.
- **Perfil_Activo:** registro de `app.perfiles_usuario` con `activo = true` cuyo `usuario_id` coincide con el `sub` del Actor.
- **Rol_Gestor_ERP:** rol empresarial `ADMINISTRADOR` o `FINANZAS_RRHH`.
- **Rol_Sin_Acceso_ERP:** rol empresarial `COMERCIAL` u `OPERACIONES`.
- **Vista_Completa_Cliente:** representación JSON del Cliente con todos sus campos (`ClienteResponse`), definida en `002-crm-clientes-contratos`.
- **Vista_Reducida_Cliente:** representación JSON del Cliente para FINANZAS_RRHH y OPERACIONES (`ClienteResumenResponse`), definida en `002-crm-clientes-contratos`.
- **Lista_Paginada:** respuesta JSON `{ "content": [], "page", "size", "totalElements", "totalPages" }` definida en DP-API-03.
- **Resumen_Financiero:** respuesta de `GET /api/erp/resumen` con, para cada Tipo_Movimiento, la cantidad y la suma de valores de cada Estado_Movimiento y la cantidad total del tipo, más el Saldo_Pagado (ERP-04).
- **Saldo_Pagado:** suma de los valores de los movimientos `INGRESO` en estado `PAGADO` menos la suma de los valores de los movimientos `EGRESO` en estado `PAGADO`, dentro del rango de fechas solicitado.
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

### Requirement 1: Organización del módulo ERP (NF-04, DP-EST-01, DP-ARQ-01)

**User Story:** Como integrante del equipo, quiero que el ERP siga la estructura por módulo y capas del CRM, para mantener un único patrón en el backend y preparar el SGA.

#### Acceptance Criteria

1. THE Backend SHALL ubicar el código del ERP en el paquete `com.arise.corp.erp` con las capas `controller`, `service`, `repository`, `entity` y `dto`. _(NF-04, DP-EST-01)_
2. THE Backend SHALL recibir y devolver DTO en los controladores del ERP y mantener las entidades JPA dentro de la capa de servicio. _(NF-04)_
3. THE Backend SHALL ubicar las reglas de negocio y las transacciones del ERP en la capa de servicio, con controladores limitados a validar el DTO, delegar en el servicio y construir la respuesta HTTP. _(NF-04)_
4. THE Backend SHALL usar en el ERP los componentes de `com.arise.corp.shared` para el manejo de errores, la normalización, la paginación y las validaciones comunes. _(NF-04)_
5. THE Backend SHALL consultar los datos de clientes desde el módulo ERP únicamente a través del Servicio_Clientes_CRM, sin usar desde `erp` repositorios ni entidades del módulo `crm`. _(DP-ARQ-01)_
6. THE Backend SHALL ofrecer en el Servicio_Clientes_CRM una consulta de solo lectura que, dado el identificador de un Cliente, informe si existe, si está activo y su nombre, sin cambiar el comportamiento de los endpoints del CRM. _(DP-ARQ-01, ERP-05)_
7. THE Backend SHALL representar la Relacion_Financiera con un Cliente mediante el UUID `clienteId`, sin asociaciones JPA entre entidades del ERP y del CRM. _(DP-ARQ-01)_
8. THE Backend SHALL mantener el módulo CRM sin dependencias hacia el módulo `erp`. _(DP-ARQ-01)_

### Requirement 2: Modelo de datos y migración del ERP (NF-03, NF-05, DP-TEC-03, DP-SEG-05)

**User Story:** Como integrante del equipo, quiero que las tablas del ERP se creen con una migración versionada y restricciones en PostgreSQL, para que la integridad no dependa solo del backend.

#### Acceptance Criteria

1. THE Migracion_ERP SHALL crear las tablas `app.empleados`, `app.proveedores` y `app.movimientos_financieros` en un archivo nuevo de `supabase/migrations/` con un timestamp posterior a `20261111120000`, sin modificar las migraciones existentes. _(NF-05, DP-TEC-03)_
2. THE Migracion_ERP SHALL definir en `app.empleados` un identificador UUID como clave primaria, `tipo_documento` obligatorio con una restricción `CHECK` sobre `CC`, `CE` y `PASAPORTE`, `numero_documento` obligatorio, una restricción de unicidad sobre el par (`tipo_documento`, `numero_documento`), `nombre`, `cargo`, `area`, `correo` y `telefono` obligatorios, `fecha_ingreso` de tipo `date` obligatoria, `activo` obligatorio con valor por defecto verdadero, y `creado_en`, `actualizado_en` y `actualizado_por`. _(ERP-01, NF-03)_
3. THE Migracion_ERP SHALL definir en `app.proveedores` un identificador UUID como clave primaria, `tipo_documento` obligatorio con una restricción `CHECK` sobre `NIT`, `CC`, `CE` y `PASAPORTE`, `numero_documento` obligatorio, una restricción de unicidad sobre el par (`tipo_documento`, `numero_documento`), `nombre`, `correo` y `telefono` obligatorios, `nombre_contacto`, `direccion` y `ciudad` opcionales, `activo` obligatorio con valor por defecto verdadero, y `creado_en`, `actualizado_en` y `actualizado_por`. _(ERP-02, NF-03)_
4. THE Migracion_ERP SHALL definir en `app.movimientos_financieros` un identificador UUID como clave primaria, `tipo` obligatorio, `fecha` de tipo `date` obligatoria, `concepto` obligatorio, `valor` de tipo `numeric(15,2)` obligatorio, `referencia` opcional, `estado` obligatorio con valor por defecto `PENDIENTE`, `cliente_id` opcional con clave foránea a `app.clientes`, `proveedor_id` opcional con clave foránea a `app.proveedores`, `estado_actualizado_en`, `estado_actualizado_por`, `creado_en`, `actualizado_en` y `actualizado_por`. _(ERP-03, ERP-05, NF-03, DP-SEG-06)_
5. THE Migracion_ERP SHALL declarar en `app.movimientos_financieros` restricciones `CHECK` que limiten `tipo` a `INGRESO` y `EGRESO`, limiten `estado` a `PENDIENTE`, `PAGADO` y `ANULADO`, exijan `valor > 0`, exijan `cliente_id` nulo cuando `tipo` es `EGRESO` y exijan `proveedor_id` nulo cuando `tipo` es `INGRESO`. _(NF-03, ERP-03, ERP-05, DP-API-04)_
6. THE Migracion_ERP SHALL declarar restricciones `CHECK` con los formatos de documento y los límites de texto de "Supuestos adoptados" en las tres tablas. _(NF-03)_
7. THE Migracion_ERP SHALL usar `timestamptz` para los instantes y `uuid not null` sin clave foránea a `auth.users` para `actualizado_por` y `estado_actualizado_por`. _(DP-API-04, DP-SEG-06)_
8. THE Migracion_ERP SHALL habilitar RLS sin políticas en las tres tablas y revocar sus privilegios a los roles `anon` y `authenticated`. _(DP-SEG-05)_
9. THE Migracion_ERP SHALL contener únicamente estructura, sin datos de ejemplo, UUID personales ni secretos. _(NF-01)_
10. WHEN el Backend arranca contra una base con todas las migraciones aplicadas, THE Backend SHALL superar la validación `spring.jpa.hibernate.ddl-auto=validate` de las entidades del ERP. _(DP-TEC-03)_

### Requirement 3: Normalización de documentos, referencias y textos (ERP-01, ERP-02, ERP-03)

**User Story:** Como usuario de Finanzas / RR. HH., quiero que el sistema reconozca el mismo documento escrito con distintos formatos, para no registrar empleados o proveedores duplicados por error.

#### Acceptance Criteria

1. WHEN el Backend recibe el número de documento de un Empleado o de un Proveedor, THE Backend SHALL calcular el Documento_Normalizado antes de validar, comparar o almacenar el valor, con la misma regla que aplica a los clientes del CRM. _(ERP-01, ERP-02)_
2. THE Backend SHALL tratar como el mismo documento dos entradas que difieren solo en espacios, puntos, guiones o mayúsculas y minúsculas, por ejemplo `1.023.456-7` y `10234567`. _(ERP-01, ERP-02)_
3. WHEN el Backend recibe la referencia de un Movimiento_Financiero, THE Backend SHALL calcular la Referencia_Normalizada antes de validar, comparar o almacenar el valor. _(ERP-03)_
4. THE Backend SHALL producir, al normalizar un Documento_Normalizado o una Referencia_Normalizada, un resultado idéntico a la entrada. _(ERP-01, ERP-02, ERP-03)_
5. WHEN el Backend recibe un campo de texto del ERP, THE Backend SHALL eliminar los espacios iniciales y finales y almacenar como nulo un campo opcional que quede vacío. _(NF-03)_

### Requirement 4: Registro de empleados (ERP-01)

**User Story:** Como usuario de Finanzas / RR. HH., quiero registrar empleados con sus datos laborales y de contacto, para mantener el registro de personal en la plataforma.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_ERP envía `POST /api/erp/empleados` con datos válidos, THE Backend SHALL crear el Empleado con `activo = true`, asignar `creadoEn`, `actualizadoEn` y `actualizadoPor` con el `sub` del Actor, y responder 201 con el Empleado y el encabezado `Location: /api/erp/empleados/{id}`. _(ERP-01, DP-SEG-06)_
2. THE Backend SHALL exigir en el registro de un Empleado los campos `tipoDocumento`, `numeroDocumento`, `nombre`, `cargo`, `area`, `correo`, `telefono` y `fechaIngreso`. _(ERP-01)_
3. IF falta un campo obligatorio o llega vacío, THEN THE Backend SHALL responder 400 con Problem_Details y una entrada en `errors` por cada campo faltante, sin crear el Empleado. _(ERP-01, CA-06)_
4. IF `tipoDocumento` no es `CC`, `CE` ni `PASAPORTE`, THEN THE Backend SHALL responder 400 con un error en el campo `tipoDocumento`. _(ERP-01)_
5. IF el Documento_Normalizado tiene menos de 3 o más de 20 caracteres, contiene caracteres distintos de letras y dígitos, o contiene letras con `tipoDocumento` `CC`, THEN THE Backend SHALL responder 400 con un error en el campo `numeroDocumento`. _(ERP-01)_
6. IF `nombre` supera 200 caracteres, `cargo` o `area` superan 100 caracteres, `correo` tiene un formato inválido o supera 254 caracteres, o `telefono` tiene menos de 7 o más de 20 caracteres o caracteres distintos de dígitos, espacios, `+`, `-` y paréntesis, THEN THE Backend SHALL responder 400 con un error por cada campo inválido. _(ERP-01)_
7. IF `fechaIngreso` no es una fecha ISO-8601 válida o es posterior a la Fecha_Actual_Colombia, THEN THE Backend SHALL responder 400 con un error en el campo `fechaIngreso`. _(ERP-01, DP-API-04)_
8. IF existe un Empleado activo con el mismo `tipoDocumento` y Documento_Normalizado, THEN THE Backend SHALL responder 409 con `conflicto` `EMPLEADO_DUPLICADO` y un mensaje que indique que el empleado ya está registrado, sin crear el Empleado. _(ERP-01)_
9. IF existe un Empleado inactivo con el mismo `tipoDocumento` y Documento_Normalizado, THEN THE Backend SHALL responder 409 con `conflicto` `EMPLEADO_INACTIVO_EXISTENTE`, un mensaje que indique que el empleado existe inactivo y debe reactivarse y la extensión `empleadoId` con el identificador del Empleado existente, sin crear un Empleado nuevo. _(ERP-01)_
10. WHEN el cuerpo de la solicitud incluye `id`, `activo` o campos de auditoría, THE Backend SHALL ignorar esos valores y asignar los valores controlados por el servidor. _(NF-02, `security.md`)_

### Requirement 5: Listado, búsqueda y consulta de empleados (ERP-01, DP-API-03)

**User Story:** Como usuario de Finanzas / RR. HH., quiero listar, buscar y consultar empleados, para encontrar la información de una persona sin recorrer todos los registros.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_ERP envía `GET /api/erp/empleados`, THE Backend SHALL responder 200 con una Lista_Paginada de empleados activos e inactivos. _(ERP-01, DP-API-03)_
2. THE Backend SHALL aceptar en `GET /api/erp/empleados` los parámetros `page` (desde 0, por defecto 0), `size` (de 1 a 100, por defecto 20) y `sort` con los campos `nombre`, `numeroDocumento`, `fechaIngreso` y `creadoEn` en dirección `asc` o `desc`, con `nombre,asc` por defecto. _(DP-API-03)_
3. WHERE la solicitud incluye el parámetro `q`, THE Backend SHALL devolver los empleados cuyo `nombre` contiene `q` sin distinguir mayúsculas y minúsculas o cuyo número de documento contiene el Documento_Normalizado de `q`. _(ERP-01)_
4. WHERE la solicitud incluye el parámetro `activo`, THE Backend SHALL devolver solo los empleados cuyo estado activo coincide con el valor recibido. _(ERP-01)_
5. IF `page` es negativo, `size` está fuera de 1 a 100, `sort` usa un campo o una dirección no permitidos, `q` supera 100 caracteres o `activo` no es booleano, THEN THE Backend SHALL responder 400 con Problem_Details. _(DP-API-03, CA-06)_
6. WHEN un Actor con Rol_Gestor_ERP envía `GET /api/erp/empleados/{id}` para un Empleado existente, THE Backend SHALL responder 200 con `id`, `tipoDocumento`, `numeroDocumento`, `nombre`, `cargo`, `area`, `correo`, `telefono`, `fechaIngreso`, `activo`, `creadoEn`, `actualizadoEn` y `actualizadoPor`, esté el Empleado activo o inactivo. _(ERP-01)_
7. IF el `{id}` de `GET /api/erp/empleados/{id}` no corresponde a ningún Empleado, THEN THE Backend SHALL responder 404 con Problem_Details. _(CA-06)_
8. IF el `{id}` de una ruta del ERP no es un UUID, THEN THE Backend SHALL responder 400 con Problem_Details y `errors[0].field = "id"`. _(CA-06)_

### Requirement 6: Edición de empleados (ERP-01)

**User Story:** Como usuario de Finanzas / RR. HH., quiero editar los datos de un empleado, para mantener actualizados su cargo, su área y su contacto.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_ERP envía `PUT /api/erp/empleados/{id}` con datos válidos, THE Backend SHALL reemplazar `tipoDocumento`, `numeroDocumento`, `nombre`, `cargo`, `area`, `correo`, `telefono` y `fechaIngreso`, actualizar `actualizadoEn` y `actualizadoPor` con el `sub` del Actor, y responder 200 con el Empleado. _(ERP-01, DP-SEG-06)_
2. THE Backend SHALL aplicar en `PUT /api/erp/empleados/{id}` las mismas validaciones de campos que en el registro de empleados. _(ERP-01)_
3. IF el `tipoDocumento` y el Documento_Normalizado enviados pertenecen a otro Empleado, activo o inactivo, THEN THE Backend SHALL responder 409 con `conflicto` `EMPLEADO_DUPLICADO` sin modificar el Empleado. _(ERP-01)_
4. THE Backend SHALL conservar `activo` y `creadoEn` al procesar `PUT /api/erp/empleados/{id}`. _(ERP-01)_
5. IF el `{id}` de `PUT /api/erp/empleados/{id}` no corresponde a ningún Empleado, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 7: Desactivación y reactivación de empleados (ERP-01, DP-API-05)

**User Story:** Como usuario de Finanzas / RR. HH., quiero desactivar y reactivar empleados, para conservar el registro de quienes dejan la empresa sin eliminarlo.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_ERP envía `PATCH /api/erp/empleados/{id}` con `{ "activo": false }` para un Empleado activo, THE Backend SHALL marcar el Empleado como inactivo, actualizar `actualizadoEn` y `actualizadoPor` y responder 200 con el Empleado. _(ERP-01, DP-API-05)_
2. WHEN un Actor con Rol_Gestor_ERP envía `PATCH /api/erp/empleados/{id}` con `{ "activo": true }` para un Empleado inactivo, THE Backend SHALL marcar el Empleado como activo, actualizar `actualizadoEn` y `actualizadoPor` y responder 200 con el Empleado. _(ERP-01)_
3. WHEN el valor de `activo` recibido coincide con el estado actual del Empleado, THE Backend SHALL responder 200 con el Empleado sin modificar sus datos ni su auditoría. _(ERP-01)_
4. IF el cuerpo de `PATCH /api/erp/empleados/{id}` no contiene `activo` o `activo` no es booleano, THEN THE Backend SHALL responder 400 con un error en el campo `activo`. _(CA-06)_
5. IF un Actor con Rol_Gestor_ERP envía `DELETE /api/erp/empleados/{id}`, THEN THE Backend SHALL responder 405 con Problem_Details y el encabezado `Allow`, y conservar el Empleado; sin token o con un Rol_Sin_Acceso_ERP se aplican primero las respuestas 401 y 403 del Requirement 19. _(ERP-01)_
6. IF el `{id}` de `PATCH /api/erp/empleados/{id}` no corresponde a ningún Empleado, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 8: Protección de datos personales de empleados y proveedores (NF-02, CA-06, `security.md`)

**User Story:** Como Administrador, quiero que los datos personales de empleados y proveedores solo lleguen a quien gestiona el ERP y no aparezcan en logs ni mensajes de error, para limitar su exposición.

#### Acceptance Criteria

1. THE Backend SHALL devolver a ADMINISTRADOR y a FINANZAS_RRHH la misma representación del Empleado y del Proveedor, sin aceptar parámetros de la petición que cambien los campos devueltos. _(DP-PRO-01, NF-02)_
2. THE Backend SHALL limitar los datos del Empleado a los campos de "Supuestos adoptados", sin salario, datos de nómina ni datos bancarios. _(ERP-01, MVP §10)_
3. THE Backend SHALL excluir de los logs el nombre, el número de documento, el correo, el teléfono y la dirección de empleados y proveedores. _(`security.md`)_
4. THE Backend SHALL excluir de los mensajes `detail` de los errores del ERP los documentos, los nombres y los datos de contacto de empleados, proveedores y clientes. _(CA-06)_

### Requirement 9: Registro de proveedores (ERP-02)

**User Story:** Como usuario de Finanzas / RR. HH., quiero registrar proveedores con sus datos de identificación y contacto, para asociarlos a los egresos de la empresa.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_ERP envía `POST /api/erp/proveedores` con datos válidos, THE Backend SHALL crear el Proveedor con `activo = true`, asignar `creadoEn`, `actualizadoEn` y `actualizadoPor` con el `sub` del Actor, y responder 201 con el Proveedor y el encabezado `Location: /api/erp/proveedores/{id}`. _(ERP-02, DP-SEG-06)_
2. THE Backend SHALL exigir en el registro de un Proveedor los campos `tipoDocumento`, `numeroDocumento`, `nombre`, `correo` y `telefono`, y aceptar como opcionales `nombreContacto`, `direccion` y `ciudad`. _(ERP-02)_
3. IF falta un campo obligatorio o llega vacío, THEN THE Backend SHALL responder 400 con Problem_Details y una entrada en `errors` por cada campo faltante, sin crear el Proveedor. _(ERP-02, CA-06)_
4. IF `tipoDocumento` no es `NIT`, `CC`, `CE` ni `PASAPORTE`, THEN THE Backend SHALL responder 400 con un error en el campo `tipoDocumento`. _(ERP-02)_
5. IF el Documento_Normalizado tiene menos de 3 o más de 20 caracteres, contiene caracteres distintos de letras y dígitos, o contiene letras con `tipoDocumento` `CC` o `NIT`, THEN THE Backend SHALL responder 400 con un error en el campo `numeroDocumento`. _(ERP-02)_
6. IF `nombre` o `nombreContacto` superan 200 caracteres, `correo` tiene un formato inválido o supera 254 caracteres, `telefono` incumple el formato de teléfono de los supuestos, `direccion` supera 200 caracteres o `ciudad` supera 100 caracteres, THEN THE Backend SHALL responder 400 con un error por cada campo inválido. _(ERP-02)_
7. IF existe un Proveedor activo con el mismo `tipoDocumento` y Documento_Normalizado, THEN THE Backend SHALL responder 409 con `conflicto` `PROVEEDOR_DUPLICADO` y un mensaje que indique que el proveedor ya está registrado, sin crear el Proveedor. _(ERP-02)_
8. IF existe un Proveedor inactivo con el mismo `tipoDocumento` y Documento_Normalizado, THEN THE Backend SHALL responder 409 con `conflicto` `PROVEEDOR_INACTIVO_EXISTENTE`, un mensaje que indique que el proveedor existe inactivo y debe reactivarse y la extensión `proveedorId` con el identificador del Proveedor existente, sin crear un Proveedor nuevo. _(ERP-02)_
9. WHEN el cuerpo de la solicitud incluye `id`, `activo` o campos de auditoría, THE Backend SHALL ignorar esos valores y asignar los valores controlados por el servidor. _(NF-02, `security.md`)_

### Requirement 10: Listado, búsqueda y consulta de proveedores (ERP-02, DP-API-03)

**User Story:** Como usuario de Finanzas / RR. HH., quiero listar, buscar y consultar proveedores, para encontrar rápido al proveedor de un egreso.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_ERP envía `GET /api/erp/proveedores`, THE Backend SHALL responder 200 con una Lista_Paginada de proveedores activos e inactivos. _(ERP-02, DP-API-03)_
2. THE Backend SHALL aceptar en `GET /api/erp/proveedores` los parámetros `page` (desde 0, por defecto 0), `size` (de 1 a 100, por defecto 20) y `sort` con los campos `nombre`, `numeroDocumento` y `creadoEn` en dirección `asc` o `desc`, con `nombre,asc` por defecto. _(DP-API-03)_
3. WHERE la solicitud incluye el parámetro `q`, THE Backend SHALL devolver los proveedores cuyo `nombre` contiene `q` sin distinguir mayúsculas y minúsculas o cuyo número de documento contiene el Documento_Normalizado de `q`. _(ERP-02)_
4. WHERE la solicitud incluye el parámetro `activo`, THE Backend SHALL devolver solo los proveedores cuyo estado activo coincide con el valor recibido. _(ERP-02)_
5. IF `page` es negativo, `size` está fuera de 1 a 100, `sort` usa un campo o una dirección no permitidos, `q` supera 100 caracteres o `activo` no es booleano, THEN THE Backend SHALL responder 400 con Problem_Details. _(DP-API-03, CA-06)_
6. WHEN un Actor con Rol_Gestor_ERP envía `GET /api/erp/proveedores/{id}` para un Proveedor existente, THE Backend SHALL responder 200 con `id`, `tipoDocumento`, `numeroDocumento`, `nombre`, `nombreContacto`, `correo`, `telefono`, `direccion`, `ciudad`, `activo`, `creadoEn`, `actualizadoEn` y `actualizadoPor`, esté el Proveedor activo o inactivo. _(ERP-02)_
7. IF el `{id}` de `GET /api/erp/proveedores/{id}` no corresponde a ningún Proveedor, THEN THE Backend SHALL responder 404 con Problem_Details. _(CA-06)_

### Requirement 11: Edición de proveedores (ERP-02)

**User Story:** Como usuario de Finanzas / RR. HH., quiero editar los datos de un proveedor, para mantener actualizada su información de contacto.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_ERP envía `PUT /api/erp/proveedores/{id}` con datos válidos, THE Backend SHALL reemplazar `tipoDocumento`, `numeroDocumento`, `nombre`, `nombreContacto`, `correo`, `telefono`, `direccion` y `ciudad`, actualizar `actualizadoEn` y `actualizadoPor` con el `sub` del Actor, y responder 200 con el Proveedor. _(ERP-02, DP-SEG-06)_
2. THE Backend SHALL aplicar en `PUT /api/erp/proveedores/{id}` las mismas validaciones de campos que en el registro de proveedores. _(ERP-02)_
3. WHEN `PUT /api/erp/proveedores/{id}` omite un campo opcional o lo envía nulo, THE Backend SHALL dejar ese campo nulo. _(ERP-02)_
4. IF el `tipoDocumento` y el Documento_Normalizado enviados pertenecen a otro Proveedor, activo o inactivo, THEN THE Backend SHALL responder 409 con `conflicto` `PROVEEDOR_DUPLICADO` sin modificar el Proveedor. _(ERP-02)_
5. THE Backend SHALL conservar `activo` y `creadoEn` al procesar `PUT /api/erp/proveedores/{id}`. _(ERP-02)_
6. IF el `{id}` de `PUT /api/erp/proveedores/{id}` no corresponde a ningún Proveedor, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 12: Desactivación y reactivación de proveedores (ERP-02, DP-API-05)

**User Story:** Como usuario de Finanzas / RR. HH., quiero desactivar y reactivar proveedores, para dejar de usarlos en egresos nuevos sin perder los egresos ya registrados.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_ERP envía `PATCH /api/erp/proveedores/{id}` con `activo` booleano distinto del estado actual, THE Backend SHALL fijar el estado del Proveedor, actualizar `actualizadoEn` y `actualizadoPor` y responder 200 con el Proveedor. _(ERP-02, DP-API-05)_
2. WHEN el valor de `activo` recibido coincide con el estado actual del Proveedor, THE Backend SHALL responder 200 con el Proveedor sin modificar sus datos ni su auditoría. _(ERP-02)_
3. IF el cuerpo de `PATCH /api/erp/proveedores/{id}` no contiene `activo` o `activo` no es booleano, THEN THE Backend SHALL responder 400 con un error en el campo `activo`. _(CA-06)_
4. WHEN el Backend desactiva un Proveedor, THE Backend SHALL conservar sin cambios la Relacion_Financiera, el estado y los datos de los movimientos asociados a ese Proveedor. _(ERP-02, ERP-05)_
5. IF un Actor con Rol_Gestor_ERP envía `DELETE /api/erp/proveedores/{id}`, THEN THE Backend SHALL responder 405 con Problem_Details y el encabezado `Allow`, y conservar el Proveedor; sin token o con un Rol_Sin_Acceso_ERP se aplican primero las respuestas 401 y 403 del Requirement 19. _(ERP-02)_
6. IF el `{id}` de `PATCH /api/erp/proveedores/{id}` no corresponde a ningún Proveedor, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 13: Registro de movimientos financieros (ERP-03)

**User Story:** Como usuario de Finanzas / RR. HH., quiero registrar ingresos y egresos con fecha, concepto, valor y estado, para llevar el control básico de las finanzas de la empresa.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_ERP envía `POST /api/erp/movimientos` con datos válidos, THE Backend SHALL crear el Movimiento_Financiero en estado `PENDIENTE`, asignar su auditoría y la auditoría de estado con el `sub` del Actor y el instante de creación, y responder 201 con el Movimiento_Financiero y el encabezado `Location: /api/erp/movimientos/{id}`. _(ERP-03, DP-SEG-06)_
2. THE Backend SHALL exigir en el registro de un Movimiento_Financiero los campos `tipo`, `fecha`, `concepto` y `valor`, y aceptar como opcionales `referencia`, `clienteId` y `proveedorId`. _(ERP-03, ERP-05)_
3. IF falta un campo obligatorio o llega vacío, THEN THE Backend SHALL responder 400 con Problem_Details y una entrada en `errors` por cada campo faltante, sin crear el Movimiento_Financiero. _(ERP-03, CA-06)_
4. IF `tipo` no es un Tipo_Movimiento, THEN THE Backend SHALL responder 400 con un error en el campo `tipo`. _(ERP-03)_
5. IF `fecha` no es una fecha ISO-8601 válida, THEN THE Backend SHALL responder 400 con un error en el campo `fecha`. _(ERP-03, DP-API-04)_
6. IF `concepto` tiene menos de 3 o más de 300 caracteres, THEN THE Backend SHALL responder 400 con un error en el campo `concepto`. _(ERP-03)_
7. IF `valor` es menor o igual a 0, tiene más de 2 decimales o más de 13 dígitos enteros, THEN THE Backend SHALL responder 400 con un error en el campo `valor`. _(ERP-03, DP-API-04)_
8. IF la Referencia_Normalizada supera 50 caracteres o contiene caracteres distintos de letras A–Z, dígitos, espacios, puntos, barras y guiones, THEN THE Backend SHALL responder 400 con un error en el campo `referencia`. _(ERP-03)_
9. WHEN el cuerpo de la solicitud incluye `id`, `estado` o campos de auditoría, THE Backend SHALL ignorar esos valores y crear el Movimiento_Financiero en estado `PENDIENTE`. _(NF-02, `security.md`)_

### Requirement 14: Relación de movimientos con clientes y proveedores (ERP-05, DP-ARQ-01)

**User Story:** Como usuario de Finanzas / RR. HH., quiero asociar un ingreso a un cliente y un egreso a un proveedor, para saber de quién proviene o a quién corresponde cada movimiento.

#### Acceptance Criteria

1. THE Backend SHALL admitir en un Movimiento_Financiero de tipo `INGRESO` un `clienteId` opcional y en uno de tipo `EGRESO` un `proveedorId` opcional. _(ERP-05)_
2. IF `POST /api/erp/movimientos` o `PUT /api/erp/movimientos/{id}` envían `clienteId` con `tipo` `EGRESO`, THEN THE Backend SHALL responder 400 con un error en el campo `clienteId`, sin crear ni modificar el Movimiento_Financiero. _(ERP-05, NF-03)_
3. IF `POST /api/erp/movimientos` o `PUT /api/erp/movimientos/{id}` envían `proveedorId` con `tipo` `INGRESO`, THEN THE Backend SHALL responder 400 con un error en el campo `proveedorId`, sin crear ni modificar el Movimiento_Financiero. _(ERP-05, NF-03)_
4. IF `clienteId` no corresponde a ningún Cliente, THEN THE Backend SHALL responder 404 con la extensión `campo = "clienteId"`, sin crear ni modificar el Movimiento_Financiero. _(ERP-05, CA-06)_
5. IF `proveedorId` no corresponde a ningún Proveedor, THEN THE Backend SHALL responder 404 con la extensión `campo = "proveedorId"`, sin crear ni modificar el Movimiento_Financiero. _(ERP-05, CA-06)_
6. IF `POST /api/erp/movimientos` asocia un Cliente inactivo, o `PUT /api/erp/movimientos/{id}` asigna un Cliente inactivo distinto del Cliente actual del movimiento, THEN THE Backend SHALL responder 409 con `conflicto` `CLIENTE_INACTIVO` y `campo = "clienteId"`, sin crear ni modificar el Movimiento_Financiero. _(ERP-05)_
7. IF `POST /api/erp/movimientos` asocia un Proveedor inactivo, o `PUT /api/erp/movimientos/{id}` asigna un Proveedor inactivo distinto del Proveedor actual del movimiento, THEN THE Backend SHALL responder 409 con `conflicto` `PROVEEDOR_INACTIVO` y `campo = "proveedorId"`, sin crear ni modificar el Movimiento_Financiero. _(ERP-05)_
8. WHEN `PUT /api/erp/movimientos/{id}` conserva el Cliente o el Proveedor actual del movimiento y ese Cliente o Proveedor está inactivo, THE Backend SHALL aplicar la edición. _(ERP-05)_
9. WHEN `PUT /api/erp/movimientos/{id}` omite `clienteId` y `proveedorId` o los envía nulos, THE Backend SHALL eliminar la Relacion_Financiera del movimiento. _(ERP-05)_
10. WHEN un Cliente o un Proveedor asociado a movimientos se desactiva, THE Backend SHALL conservar sin cambios la Relacion_Financiera, el estado y los datos de esos movimientos. _(ERP-05)_
11. THE Backend SHALL comprobar la existencia y el estado activo del Cliente mediante el Servicio_Clientes_CRM dentro de la transacción que crea o modifica el Movimiento_Financiero. _(DP-ARQ-01, ERP-05)_
12. THE Backend SHALL incluir en cada representación del Movimiento_Financiero el identificador, el nombre y el estado activo del Cliente asociado y el identificador, el nombre y el estado activo del Proveedor asociado, con valor nulo cuando no existe la relación. _(ERP-05)_

### Requirement 15: Consulta de movimientos financieros (ERP-03, ERP-05, DP-API-03)

**User Story:** Como usuario de Finanzas / RR. HH., quiero listar y filtrar movimientos por tipo, estado, fechas, cliente o proveedor, para revisar los registros que necesito.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_ERP envía `GET /api/erp/movimientos`, THE Backend SHALL responder 200 con una Lista_Paginada de movimientos que admite `page` (desde 0, por defecto 0), `size` (de 1 a 100, por defecto 20) y `sort` sobre `fecha`, `valor` y `creadoEn` en dirección `asc` o `desc`, con `fecha,desc` por defecto. _(ERP-03, DP-API-03)_
2. WHERE la solicitud incluye `tipo`, `estado`, `clienteId` o `proveedorId`, THE Backend SHALL devolver solo los movimientos cuyo campo correspondiente coincide con el valor recibido. _(ERP-03, ERP-05)_
3. WHERE la solicitud incluye `desde` o `hasta`, THE Backend SHALL devolver solo los movimientos cuya `fecha` es igual o posterior a `desde` e igual o anterior a `hasta`. _(ERP-03)_
4. WHERE la solicitud incluye el parámetro `q`, THE Backend SHALL devolver los movimientos cuyo `concepto` contiene `q` sin distinguir mayúsculas y minúsculas o cuya `referencia` contiene la Referencia_Normalizada de `q`. _(ERP-03)_
5. WHEN la solicitud incluye varios filtros, THE Backend SHALL devolver solo los movimientos que cumplen todos los filtros recibidos. _(DP-API-03)_
6. WHERE la solicitud incluye un `clienteId` o un `proveedorId` sin movimientos o sin registro correspondiente, THE Backend SHALL responder 200 con una Lista_Paginada vacía. _(ERP-05)_
7. IF un parámetro de `GET /api/erp/movimientos` incumple las reglas de paginación u orden, `q` supera 100 caracteres, `tipo` no es un Tipo_Movimiento, `estado` no es un Estado_Movimiento, `clienteId` o `proveedorId` no son UUID, `desde` o `hasta` no son fechas ISO-8601 válidas, o `desde` es posterior a `hasta`, THEN THE Backend SHALL responder 400 con Problem_Details. _(DP-API-03, CA-06)_
8. WHEN un Actor con Rol_Gestor_ERP envía `GET /api/erp/movimientos/{id}` para un Movimiento_Financiero existente, THE Backend SHALL responder 200 con `id`, `tipo`, `fecha`, `concepto`, `valor`, `referencia`, `estado`, el Cliente asociado (identificador, nombre y estado activo, o nulo), el Proveedor asociado (identificador, nombre y estado activo, o nulo), `estadoActualizadoEn`, `estadoActualizadoPor`, `creadoEn`, `actualizadoEn` y `actualizadoPor`. _(ERP-03, ERP-05)_
9. THE Backend SHALL serializar `valor` como texto decimal JSON con dos decimales. _(DP-API-04)_
10. IF el `{id}` de `GET /api/erp/movimientos/{id}` no corresponde a ningún Movimiento_Financiero, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 16: Edición de movimientos pendientes (ERP-03)

**User Story:** Como usuario de Finanzas / RR. HH., quiero corregir un movimiento mientras está pendiente, para no alterar movimientos ya pagados o anulados.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_ERP envía `PUT /api/erp/movimientos/{id}` con datos válidos para un Movimiento_Financiero en estado `PENDIENTE`, THE Backend SHALL reemplazar `tipo`, `fecha`, `concepto`, `valor`, `referencia`, `clienteId` y `proveedorId`, actualizar `actualizadoEn` y `actualizadoPor` con el `sub` del Actor y responder 200 con el Movimiento_Financiero. _(ERP-03, DP-SEG-06)_
2. THE Backend SHALL aplicar en `PUT /api/erp/movimientos/{id}` las mismas validaciones de campos que en el registro de movimientos y las reglas de Relacion_Financiera del Requirement 14. _(ERP-03, ERP-05)_
3. WHEN `PUT /api/erp/movimientos/{id}` omite `referencia` o la envía nula, THE Backend SHALL dejar `referencia` nula. _(ERP-03)_
4. IF el Movimiento_Financiero no está en estado `PENDIENTE`, THEN THE Backend SHALL responder 409 con `conflicto` `MOVIMIENTO_NO_EDITABLE`, la extensión `estadoActual` y un mensaje que indique que solo se editan movimientos pendientes, sin modificar el Movimiento_Financiero. _(ERP-03)_
5. THE Backend SHALL conservar el Estado_Movimiento y su auditoría de estado al procesar `PUT /api/erp/movimientos/{id}`, aunque el cuerpo incluya `estado`. _(ERP-03, DP-API-05)_
6. IF el `{id}` de `PUT /api/erp/movimientos/{id}` no corresponde a ningún Movimiento_Financiero, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 17: Cambio de estado del movimiento (ERP-03, DP-API-05, DP-SEG-06)

**User Story:** Como usuario de Finanzas / RR. HH., quiero marcar un movimiento como pagado o anularlo con transiciones controladas, para que su estado sea coherente y quede registrado quién lo cambió.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_ERP envía `PATCH /api/erp/movimientos/{id}/estado` con un `estado` que forma una Transicion_Permitida_Movimiento desde el estado actual, THE Backend SHALL actualizar `estado`, `estadoActualizadoEn` con el instante UTC y `estadoActualizadoPor` con el `sub` del Actor en una misma transacción y responder 200 con el Movimiento_Financiero. _(ERP-03, DP-API-05, DP-SEG-06)_
2. IF el `estado` solicitado no forma una Transicion_Permitida_Movimiento desde el estado actual, incluido el mismo estado, el paso a `PENDIENTE` o cualquier cambio desde un Estado_Final_Movimiento, THEN THE Backend SHALL responder 409 con `conflicto` `TRANSICION_NO_PERMITIDA`, las extensiones `estadoActual` y `estadoSolicitado` y un mensaje que indique ambos estados, sin modificar el Movimiento_Financiero. _(ERP-03, DP-API-05)_
3. IF el cuerpo no contiene `estado` o `estado` no es un Estado_Movimiento, THEN THE Backend SHALL responder 400 con un error en el campo `estado`. _(CA-06)_
4. IF la actualización del estado o de su auditoría falla, THEN THE Backend SHALL revertir la operación completa y conservar el estado y la auditoría anteriores. _(DP-SEG-06, DP-API-05)_
5. THE Backend SHALL conservar los datos del Movimiento_Financiero, `actualizadoEn` y `actualizadoPor` al procesar `PATCH /api/erp/movimientos/{id}/estado`. _(ERP-03, DP-SEG-06)_
6. WHILE el Cliente o el Proveedor asociado al Movimiento_Financiero está inactivo, THE Backend SHALL aplicar a ese movimiento las mismas Transicion_Permitida_Movimiento que a los demás movimientos. _(ERP-05)_
7. IF un Actor con Rol_Gestor_ERP envía `DELETE /api/erp/movimientos/{id}`, THEN THE Backend SHALL responder 405 con Problem_Details y el encabezado `Allow`, y conservar el Movimiento_Financiero; sin token o con un Rol_Sin_Acceso_ERP se aplican primero las respuestas 401 y 403 del Requirement 19. _(ERP-03)_
8. IF el `{id}` de `PATCH /api/erp/movimientos/{id}/estado` no corresponde a ningún Movimiento_Financiero, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 18: Panel financiero (ERP-04)

**User Story:** Como usuario de Finanzas / RR. HH., quiero ver los totales de ingresos y egresos por estado y el saldo pagado, para conocer de un vistazo la situación financiera.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_ERP envía `GET /api/erp/resumen`, THE Backend SHALL responder 200 con el Resumen_Financiero: para cada Tipo_Movimiento, la cantidad y la suma de valores de cada Estado_Movimiento y la cantidad total del tipo, y el Saldo_Pagado. _(ERP-04)_
2. THE Backend SHALL incluir en el Resumen_Financiero los dos Tipo_Movimiento y los tres Estado_Movimiento, con cantidad 0 y suma `0.00` para las combinaciones sin movimientos. _(ERP-04)_
3. WHERE la solicitud incluye `desde` o `hasta`, THE Backend SHALL calcular el Resumen_Financiero solo con los movimientos cuya `fecha` es igual o posterior a `desde` e igual o anterior a `hasta`. _(ERP-04)_
4. THE Backend SHALL incluir en el Resumen_Financiero los límites `desde` y `hasta` aplicados, con valor nulo para el límite ausente. _(ERP-04)_
5. IF `desde` o `hasta` no son fechas ISO-8601 válidas o `desde` es posterior a `hasta`, THEN THE Backend SHALL responder 400 con Problem_Details y un error en el parámetro afectado. _(ERP-04, CA-06)_
6. THE Backend SHALL calcular el Resumen_Financiero con los datos persistidos en el momento de la solicitud, con la cantidad total de cada Tipo_Movimiento igual a la suma de sus cantidades por estado. _(ERP-04, CA-04)_
7. THE Backend SHALL calcular el Saldo_Pagado como la suma de `INGRESO` en `PAGADO` menos la suma de `EGRESO` en `PAGADO` del mismo Resumen_Financiero. _(ERP-04)_
8. THE Backend SHALL serializar las sumas y el Saldo_Pagado como texto decimal JSON con dos decimales, sin redondeo y con signo negativo cuando el Saldo_Pagado es menor que 0. _(DP-API-04)_

### Requirement 19: Autorización por rol en el ERP (NF-02, CA-03, DP-PRO-01, DP-SEG-02)

**User Story:** Como Administrador, quiero que el backend aplique la matriz de acceso del ERP, para que solo Finanzas / RR. HH. y Administración operen sobre empleados, proveedores y finanzas.

#### Acceptance Criteria

1. THE Backend SHALL aplicar a los endpoints del ERP la matriz de acceso de la Introducción con los patrones y roles actuales de `SecurityConfig`, sin modificarlos. _(NF-02, DP-SEG-02)_
2. IF una petición a un endpoint del ERP llega sin token o con un token inválido o vencido, THEN THE Backend SHALL responder 401 con Problem_Details y el encabezado `WWW-Authenticate`. _(CA-02, CA-06)_
3. IF una petición a un endpoint del ERP llega con un JWT válido sin Perfil_Activo, THEN THE Backend SHALL responder 403 sin ejecutar la operación. _(CA-03, DP-PRO-01)_
4. IF el Perfil_Activo tiene un Rol_Sin_Acceso_ERP, THEN THE Backend SHALL responder 403 a cualquier endpoint del ERP sin ejecutar la operación ni modificar datos. _(CA-03, NF-02)_
5. IF un Actor sin permiso sobre un endpoint del ERP solicita un recurso inexistente, THEN THE Backend SHALL responder 403, sin revelar si el recurso existe. _(CA-06, `security.md`)_
6. THE Backend SHALL tomar el actor de `actualizadoPor` y `estadoActualizadoPor` del claim `sub` del JWT validado. _(DP-SEG-06)_
7. WHILE el Actor tiene el rol `FINANZAS_RRHH`, THE Backend SHALL conservar la Vista_Reducida_Cliente en `GET /api/crm/clientes` y `GET /api/crm/clientes/{id}` y la respuesta 403 en las escrituras del CRM. _(DP-PRO-01, ERP-05)_

### Requirement 20: Respuestas de error del ERP (CA-06, DP-API-02, AUTH-05)

**User Story:** Como usuario, quiero mensajes de error comprensibles cuando una operación del ERP falla, para saber qué corregir.

#### Acceptance Criteria

1. THE Backend SHALL responder los errores del ERP con Problem_Details y mensajes en español. _(DP-API-02, DP-EST-02)_
2. IF la validación de un DTO del ERP falla, THEN THE Backend SHALL responder 400 con una entrada en `errors` por cada campo inválido, con `field` y un `message` comprensible. _(DP-API-02, NF-06)_
3. IF el cuerpo es un JSON mal formado, un campo tiene un tipo incorrecto o un valor de enumeración desconocido, THEN THE Backend SHALL responder 400 sin exponer detalles del deserializador. _(CA-06)_
4. IF el servicio del ERP detecta un conflicto de negocio, THEN THE Backend SHALL responder 409 con un `detail` que describa el conflicto y la extensión `conflicto` con uno de los códigos `EMPLEADO_DUPLICADO`, `EMPLEADO_INACTIVO_EXISTENTE`, `PROVEEDOR_DUPLICADO`, `PROVEEDOR_INACTIVO_EXISTENTE`, `CLIENTE_INACTIVO`, `PROVEEDOR_INACTIVO`, `MOVIMIENTO_NO_EDITABLE` o `TRANSICION_NO_PERMITIDA`. _(ERP-01, ERP-02, ERP-03, ERP-05, CA-06)_
5. THE Backend SHALL incluir en los 409 del ERP la extensión `campo` cuando el conflicto corresponde a un campo (`numeroDocumento`, `clienteId`, `proveedorId` o `estado`) y las extensiones `empleadoId`, `proveedorId`, `estadoActual` y `estadoSolicitado` en los conflictos que las definen en los Requirements 4, 9, 16 y 17. _(DP-API-02)_
6. IF una referencia del cuerpo no existe, THEN THE Backend SHALL responder 404 con la extensión `campo` con el nombre del campo que la originó (`clienteId` o `proveedorId`). _(CA-06, NF-06)_
7. IF la Base_Datos rechaza una operación del ERP por una restricción, por ejemplo por un duplicado concurrente, THEN THE Backend SHALL responder 409 con un mensaje genérico, sin exponer SQL ni nombres de restricciones. _(NF-03, CA-06)_
8. THE Backend SHALL excluir de todas las respuestas de error del ERP las trazas de pila, el SQL, los nombres de clases y los datos internos. _(CA-06)_

### Requirement 21: Servicios y navegación ERP del frontend (NF-02, AUTH-05, DP-PRO-01)

**User Story:** Como usuario autenticado, quiero ver en el menú y en el panel principal solo las opciones del ERP que corresponden a mi rol, para llegar a mis tareas sin encontrar opciones que no puedo usar.

#### Acceptance Criteria

1. THE Servicio_ERP_Frontend SHALL enviar las peticiones a los endpoints del ERP mediante el Cliente_HTTP, con el token de la sesión vigente. _(AUTH-03)_
2. THE Servicio_ERP_Frontend SHALL enviar los valores monetarios como texto decimal y las fechas como texto ISO-8601 `AAAA-MM-DD`. _(DP-API-04)_
3. THE Servicio_ERP_Frontend SHALL incluir en los parámetros de consulta solo los filtros que tienen valor. _(DP-API-03)_
4. THE Frontend SHALL tratar las respuestas 400, 401, 403, 404, 409, 5xx y el tiempo de espera agotado del ERP con el comportamiento del Cliente_HTTP definido en `002-crm-clientes-contratos`. _(AUTH-05, NF-06)_
5. WHILE el usuario tiene Rol_Gestor_ERP, THE Frontend SHALL mostrar en el menú las opciones Empleados, Proveedores, Movimientos financieros y Panel financiero. _(DP-PRO-01)_
6. WHILE el usuario tiene el rol `FINANZAS_RRHH`, THE Frontend SHALL conservar en el menú la opción Clientes en modo consulta y omitir las demás opciones del CRM. _(DP-PRO-01, ERP-05)_
7. WHILE el usuario tiene un Rol_Sin_Acceso_ERP, THE Frontend SHALL omitir del menú y del panel principal las opciones del ERP. _(DP-PRO-01)_
8. WHILE el usuario tiene Rol_Gestor_ERP, THE Frontend SHALL mostrar en el panel principal (`Panel.jsx`) tarjetas de acceso a Empleados, Proveedores, Movimientos financieros y Panel financiero. _(ERP-04, DP-PRO-01)_
9. IF el usuario abre directamente la ruta de una vista ERP sin Rol_Gestor_ERP, THEN THE Frontend SHALL mostrar un mensaje de falta de permisos sin solicitar datos de esa vista. _(AUTH-05)_
10. THE Frontend SHALL tratar la visibilidad de menús y controles como ayuda de interfaz y mostrar el mensaje de falta de permisos cuando el Backend responda 403. _(NF-02, AUTH-05)_
11. THE Frontend SHALL conservar las rutas `/login`, `/perfil` y `/panel`, las vistas CRM y su comportamiento para cada rol. _(AUTH-01, AUTH-02, CRM-01)_

### Requirement 22: Vistas de empleados y proveedores (ERP-01, ERP-02, NF-06)

**User Story:** Como usuario de Finanzas / RR. HH., quiero pantallas para listar, registrar, editar, consultar y activar o desactivar empleados y proveedores, para gestionar el ERP sin usar la API directamente.

#### Acceptance Criteria

1. THE Frontend SHALL mostrar una lista paginada de empleados y otra de proveedores, cada una con búsqueda por nombre o documento, filtro activo/inactivo y controles de página, a partir de `GET /api/erp/empleados` y `GET /api/erp/proveedores`. _(ERP-01, ERP-02, DP-API-03)_
2. WHILE una lista de empleados o proveedores se está cargando, THE Frontend SHALL mostrar un indicador de carga. _(NF-06)_
3. WHEN una lista de empleados o proveedores no tiene resultados, THE Frontend SHALL mostrar un mensaje que indique que no hay registros para los criterios elegidos. _(NF-06)_
4. THE Frontend SHALL presentar un formulario de alta y edición de empleados con los campos obligatorios marcados, un selector de tipo de documento con `CC`, `CE` y `PASAPORTE` y un campo de fecha de ingreso. _(ERP-01, NF-06)_
5. THE Frontend SHALL presentar un formulario de alta y edición de proveedores con los campos obligatorios marcados, un selector de tipo de documento con `NIT`, `CC`, `CE` y `PASAPORTE` y los campos opcionales de contacto, dirección y ciudad. _(ERP-02, NF-06)_
6. IF el usuario envía un formulario de Empleado o Proveedor con campos obligatorios vacíos, un correo con formato inválido, valores que superan los límites de los supuestos o una fecha de ingreso posterior a la fecha actual, THEN THE Frontend SHALL mostrar un mensaje junto a cada campo inválido sin enviar la solicitud. _(NF-06)_
7. WHILE el envío de un formulario está en curso, THE Frontend SHALL deshabilitar el botón de envío y mostrar un indicador de carga. _(NF-06)_
8. WHEN el Backend responde 400 con errores por campo o 409 con `campo`, THE Frontend SHALL mostrar cada mensaje junto a su campo y conservar los valores introducidos. _(NF-06, AUTH-05)_
9. IF el Backend responde 409 `EMPLEADO_INACTIVO_EXISTENTE` o `PROVEEDOR_INACTIVO_EXISTENTE`, THEN THE Frontend SHALL mostrar el mensaje y un enlace al detalle del registro existente para reactivarlo. _(ERP-01, ERP-02)_
10. THE Frontend SHALL mostrar una vista de detalle del Empleado y otra del Proveedor con sus datos, su estado activo y sus fechas de creación y actualización. _(ERP-01, ERP-02)_
11. WHEN el usuario solicita desactivar o reactivar un Empleado o un Proveedor, THE Frontend SHALL pedir confirmación, enviar el `PATCH` correspondiente y mostrar el nuevo estado al recibir 200. _(ERP-01, ERP-02)_
12. THE Frontend SHALL mostrar en el detalle del Proveedor la lista paginada de sus movimientos, obtenida con `GET /api/erp/movimientos?proveedorId={id}`, con fecha, concepto, valor y estado. _(ERP-05)_

### Requirement 23: Vistas de movimientos financieros (ERP-03, ERP-05, NF-06)

**User Story:** Como usuario de Finanzas / RR. HH., quiero registrar movimientos, asociarlos a un cliente o proveedor y cambiar su estado con las opciones válidas, para llevar el control financiero sin errores de transición.

#### Acceptance Criteria

1. THE Frontend SHALL mostrar una lista paginada de movimientos con filtros por tipo, estado, rango de fechas y búsqueda por concepto o referencia, con fecha, tipo, concepto, valor, estado y el nombre del Cliente o Proveedor asociado. _(ERP-03, ERP-05, DP-API-03)_
2. WHILE la lista de movimientos se está cargando, THE Frontend SHALL mostrar un indicador de carga. _(NF-06)_
3. WHEN la lista de movimientos no tiene resultados, THE Frontend SHALL mostrar un mensaje que indique que no hay movimientos para los criterios elegidos. _(NF-06)_
4. IF el usuario elige en los filtros una fecha `desde` posterior a la fecha `hasta`, THEN THE Frontend SHALL mostrar un mensaje junto al filtro sin enviar la solicitud. _(NF-06)_
5. THE Frontend SHALL presentar un formulario de alta y edición de movimientos con tipo, fecha, concepto, valor en COP y referencia opcional, con los campos obligatorios marcados. _(ERP-03, NF-06)_
6. WHILE el tipo elegido en el formulario es `INGRESO`, THE Frontend SHALL ofrecer un selector opcional de clientes activos con búsqueda, obtenido con `GET /api/crm/clientes?activo=true` mediante el servicio CRM existente. _(ERP-05)_
7. WHILE el tipo elegido en el formulario es `EGRESO`, THE Frontend SHALL ofrecer un selector opcional de proveedores activos con búsqueda, obtenido con `GET /api/erp/proveedores?activo=true`. _(ERP-05)_
8. WHEN el usuario cambia el tipo en el formulario, THE Frontend SHALL quitar la selección de Cliente o Proveedor incompatible con el tipo nuevo. _(ERP-05)_
9. WHILE se edita un movimiento asociado a un Cliente o Proveedor inactivo, THE Frontend SHALL mostrar la asociación actual como seleccionada e indicar que el registro asociado está inactivo. _(ERP-05)_
10. IF el usuario envía el formulario de movimiento con campos obligatorios vacíos, un concepto de menos de 3 o más de 300 caracteres, un valor menor o igual a 0 o con más de 2 decimales, o una referencia con caracteres no permitidos, THEN THE Frontend SHALL mostrar un mensaje junto a cada campo inválido sin enviar la solicitud. _(NF-06)_
11. THE Frontend SHALL enviar el valor del movimiento como texto decimal, sin convertirlo a número de coma flotante. _(DP-API-04)_
12. THE Frontend SHALL mostrar el valor del movimiento con formato de pesos colombianos. _(ERP-03)_
13. WHEN el Backend responde 400, 404 o 409 con errores por campo o con `campo`, THE Frontend SHALL mostrar cada mensaje junto a su campo y conservar los valores introducidos. _(NF-06, AUTH-05)_
14. WHILE el movimiento está en estado `PENDIENTE`, THE Frontend SHALL ofrecer la edición y las transiciones a `PAGADO` y `ANULADO`. _(ERP-03, DP-API-05)_
15. WHILE el movimiento está en un Estado_Final_Movimiento, THE Frontend SHALL ocultar la edición y las transiciones. _(ERP-03, DP-API-05)_
16. WHEN el usuario elige una transición, THE Frontend SHALL pedir una confirmación que indique que el cambio es irreversible antes de enviar `PATCH /api/erp/movimientos/{id}/estado`. _(ERP-03, NF-06)_
17. WHEN el Backend responde 409 a una edición o a un cambio de estado, THE Frontend SHALL mostrar el mensaje y volver a cargar el movimiento. _(AUTH-05)_
18. THE Frontend SHALL mostrar una vista de detalle del movimiento con sus datos, su estado, la auditoría del último cambio de estado y un enlace al detalle del Cliente (`/crm/clientes/{id}`) o del Proveedor asociado. _(ERP-03, ERP-05)_

### Requirement 24: Vista del panel financiero (ERP-04, NF-06)

**User Story:** Como usuario de Finanzas / RR. HH., quiero una pantalla con los totales financieros y un filtro de fechas, para revisar ingresos, egresos y saldo de un periodo.

#### Acceptance Criteria

1. WHILE el usuario tiene Rol_Gestor_ERP, THE Frontend SHALL mostrar un panel con la cantidad y la suma de ingresos y egresos por estado y el Saldo_Pagado del Resumen_Financiero, con formato de pesos colombianos. _(ERP-04)_
2. THE Frontend SHALL mostrar en el panel los tres Estado_Movimiento de cada Tipo_Movimiento, incluidos los que tienen cantidad 0. _(ERP-04)_
3. THE Frontend SHALL ofrecer en el panel un filtro opcional de fecha `desde` y fecha `hasta` que vuelve a consultar `GET /api/erp/resumen` con los límites elegidos. _(ERP-04)_
4. IF el usuario elige una fecha `desde` posterior a la fecha `hasta`, THEN THE Frontend SHALL mostrar un mensaje junto al filtro sin enviar la solicitud. _(NF-06)_
5. WHILE la consulta del panel está en curso, THE Frontend SHALL mostrar un indicador de carga. _(NF-06)_
6. IF la consulta del panel falla, THEN THE Frontend SHALL mostrar un mensaje de error con la opción de reintentar. _(NF-06, AUTH-05)_
7. WHEN el Saldo_Pagado es negativo, THE Frontend SHALL mostrarlo con el signo menos. _(ERP-04)_

### Requirement 25: Pruebas unitarias y de seguridad del backend (ENG-02, NF-07, CA-03)

**User Story:** Como integrante del equipo, quiero pruebas automatizadas de las reglas y los permisos del ERP, para detectar regresiones antes de integrar cambios.

#### Acceptance Criteria

1. THE Suite_Backend SHALL incluir pruebas unitarias con Mockito de los servicios del ERP que cubran normalización, duplicados entre activos e inactivos de empleados y proveedores, fecha de ingreso futura, coherencia entre tipo y Relacion_Financiera, Cliente o Proveedor inactivo en una asociación nueva, conservación de la asociación inactiva actual, edición solo en `PENDIENTE` y registro del `sub` del Actor en la auditoría. _(ENG-02, NF-07)_
2. THE Suite_Backend SHALL verificar las 9 combinaciones de estado de origen y destino del Movimiento_Financiero y comprobar que solo se aceptan las dos Transicion_Permitida_Movimiento. _(NF-07, DP-API-05)_
3. THE Suite_Backend SHALL verificar sobre un conjunto variado de entradas que la normalización de documentos y de referencias es idempotente y que las variantes con espacios, puntos, guiones y minúsculas producen el mismo Documento_Normalizado. _(NF-07, ERP-01, ERP-02)_
4. THE Suite_Backend SHALL verificar sobre conjuntos variados de movimientos que la cantidad total de cada Tipo_Movimiento del Resumen_Financiero es igual a la suma de sus cantidades por estado y que el Saldo_Pagado es igual a la suma de `INGRESO` en `PAGADO` menos la suma de `EGRESO` en `PAGADO`. _(NF-07, ERP-04)_
5. THE Suite_Backend SHALL incluir para cada endpoint del ERP pruebas MockMvc de: sin token (401), JWT sin perfil (403), perfil inactivo (403), `COMERCIAL` (403), `OPERACIONES` (403), `FINANZAS_RRHH` (éxito) y `ADMINISTRADOR` (éxito). _(CA-03, NF-07)_
6. THE Suite_Backend SHALL obtener las authorities de las pruebas de seguridad con `PerfilJwtAuthenticationConverter` a partir de perfiles persistidos en H2, con JWT simulados y sin tokens reales de Supabase. _(CA-03, DP-TEC-02)_
7. THE Suite_Backend SHALL verificar las respuestas 400, 404, 405 y 409 de cada regla de validación, referencia y conflicto del ERP, incluidas las extensiones `conflicto` y `campo`. _(NF-07, CA-06)_
8. THE Suite_Backend SHALL incluir una prueba que, al crear y editar empleados y proveedores y al provocar respuestas 409 y 500 con datos personales ficticios conocidos, verifique que la salida de logs no contiene el nombre, el número de documento, el correo, el teléfono ni la dirección. _(`security.md`, NF-07)_
9. THE Suite_Backend SHALL sustituir el endpoint de prueba ERP de `AutorizacionModulosTests` (`GET /api/erp/proveedores`) por pruebas sobre los controladores reales en `ErpAutorizacionTests`, y conservar el endpoint de prueba SGA `GET /api/sga/paquetes`. _(CA-03)_
10. THE Suite_Backend SHALL verificar que `FINANZAS_RRHH` sigue recibiendo la Vista_Reducida_Cliente en la lectura de clientes y 403 en las escrituras del CRM. _(CA-03, DP-PRO-01)_
11. WHEN se ejecuta `.\mvnw.cmd test` desde `backend/`, THE Suite_Backend SHALL pasar sin Docker, sin Supabase y sin el archivo `.env`, incluidas las pruebas existentes de `001-foundation` y `002-crm-clientes-contratos`. _(ENG-02, DP-TEC-02)_

### Requirement 26: Pruebas de integración con PostgreSQL (ENG-03, DP-TEC-04, DP-EST-05)

**User Story:** Como responsable de las pruebas de integración, quiero ejecutar el ERP contra un PostgreSQL efímero con las migraciones reales, para comprobar restricciones, relaciones con el CRM y transacciones sin tocar la base compartida.

#### Acceptance Criteria

1. THE Suite_Integracion SHALL aplicar en orden de timestamp todas las migraciones de `supabase/migrations/`, incluida la Migracion_ERP, sobre el PostgreSQL efímero preparado con el Script_Preparacion_IT, sin copiarlas ni modificarlas. _(ENG-03, DP-TEC-03)_
2. WHEN el contexto de Spring arranca contra el PostgreSQL efímero, THE Suite_Integracion SHALL verificar que `ddl-auto=validate` acepta las entidades del ERP. _(DP-TEC-03)_
3. THE Suite_Integracion SHALL verificar en `MigracionesErpIT` las restricciones de unicidad (documento de Empleado y documento de Proveedor), las restricciones `CHECK` (tipos de documento, formatos, límites, `tipo`, `estado`, `valor > 0` y coherencia de `cliente_id` y `proveedor_id` con `tipo`) y las claves foráneas a `app.clientes` y `app.proveedores`. _(NF-03, ENG-03)_
4. THE Suite_Integracion SHALL verificar que las tablas del ERP tienen RLS habilitado y que `anon` y `authenticated` carecen de privilegios sobre ellas. _(DP-SEG-05)_
5. THE Suite_Integracion SHALL verificar contra PostgreSQL las relaciones de ERP-05: un `INGRESO` asociado a un Cliente activo del CRM, el 409 `CLIENTE_INACTIVO` con un Cliente inactivo, la conservación de la asociación al desactivar el Cliente o el Proveedor después y un `EGRESO` asociado a un Proveedor activo. _(ERP-05, ENG-03)_
6. THE Suite_Integracion SHALL verificar los filtros, la búsqueda, la paginación y el orden de empleados, proveedores y movimientos contra PostgreSQL, incluidos los filtros `desde`, `hasta`, `clienteId` y `proveedorId`. _(ENG-03, DP-API-03)_
7. THE Suite_Integracion SHALL verificar las cantidades, las sumas y el Saldo_Pagado del Resumen_Financiero contra PostgreSQL, con y sin rango de fechas. _(ENG-03, ERP-04)_
8. THE Suite_Integracion SHALL verificar que el cambio de estado de un Movimiento_Financiero y su auditoría se guardan en la misma transacción y que un fallo no deja cambios parciales. _(DP-SEG-06, ENG-03)_
9. THE Suite_Integracion SHALL vaciar las tablas del ERP y del CRM antes de cada prueba, para que el resultado de una prueba no dependa de otra. _(ENG-03)_
10. THE Suite_Integracion SHALL ejecutarse sin credenciales de la Base_Compartida, sin el archivo `.env` y sin conexión a Supabase. _(ENG-03, NF-01)_
11. WHEN se ejecuta `.\mvnw.cmd verify` desde `backend/` con Docker disponible, THE Suite_Integracion SHALL pasar junto con la Suite_Backend y con las pruebas de integración existentes del CRM. _(ENG-03)_

### Requirement 27: Pruebas del frontend (ENG-02, ENG-03)

**User Story:** Como responsable del frontend, quiero pruebas automatizadas de las vistas ERP y de su servicio, para asegurar que la interfaz se comporta según el contrato de la API.

#### Acceptance Criteria

1. THE Suite_Frontend SHALL incluir pruebas unitarias del Servicio_ERP_Frontend para las rutas, los métodos, los parámetros de consulta y los cuerpos JSON, incluido el valor como texto decimal. _(ENG-02)_
2. THE Suite_Frontend SHALL incluir pruebas unitarias de las validaciones locales de los formularios del ERP y de los permisos y opciones de menú ERP para los cuatro roles. _(ENG-02, DP-PRO-01)_
3. THE Suite_Frontend SHALL incluir pruebas de los formularios de empleados, proveedores y movimientos que cubran validación local, errores por campo del Backend, el 409 con enlace al registro inactivo existente, estado de carga y confirmaciones. _(ENG-02, NF-06)_
4. THE Suite_Frontend SHALL incluir pruebas de integración de la navegación por rol, la búsqueda y paginación de las listas ERP, el selector de Cliente o Proveedor según el tipo, las transiciones ofrecidas según el estado y el panel financiero con estados en 0 y saldo negativo. _(ENG-03, ERP-04, ERP-05)_
5. THE Suite_Frontend SHALL simular la API y Supabase Auth, sin llamadas de red reales. _(ENG-03)_
6. WHEN se ejecuta `npm test` desde `frontend/`, THE Suite_Frontend SHALL pasar, incluidas las pruebas existentes de `001-foundation` y `002-crm-clientes-contratos`. _(ENG-02)_
7. WHEN se ejecutan `npm run build` y `npm run check:secrets` desde `frontend/`, THE Frontend SHALL completar ambos comandos sin errores. _(NF-01)_

### Requirement 28: Construcción Docker e integración continua (ENG-01, ENG-04)

**User Story:** Como responsable de CI, quiero que la imagen Docker y los workflows existentes validen el ERP sin cambios de infraestructura, para que `main` solo reciba cambios verificados.

#### Acceptance Criteria

1. WHEN se ejecuta `docker build` sobre `backend/` con el código del ERP, THE Imagen_Backend SHALL construirse sin credenciales, sin conexión a Supabase y sin ejecutar la Suite_Integracion. _(ENG-01, ENG-04)_
2. THE Imagen_Backend SHALL contener cero archivos `.env`, credenciales y argumentos `ARG` o `ENV` con valores secretos. _(NF-01)_
3. WHEN se ejecuta `docker compose up backend` con un `backend/.env` válido y la Migracion_ERP aplicada a la Base_Compartida, THE Backend SHALL atender en `http://localhost:8080` y responder 401 a `GET /api/erp/empleados` sin token. _(ENG-01, CA-01, CA-02)_
4. THE Pipeline_CI SHALL validar esta spec con los workflows `backend-ci.yml`, `integration-tests.yml` y `frontend-ci.yml` existentes, sin workflows nuevos ni GitHub Secrets. _(ENG-04, NF-01)_
5. WHEN se hace push a la rama de la spec o se abre o actualiza el Pull Request hacia `main`, THE Pipeline_CI SHALL ejecutar la Suite_Backend, la Suite_Integracion con las pruebas del ERP, la Suite_Frontend y la construcción de la Imagen_Backend. _(ENG-02, ENG-03, ENG-04)_
6. IF un paso de un workflow falla, THEN THE Pipeline_CI SHALL marcar como fallida la ejecución de ese workflow. _(ENG-04)_
7. THE Equipo SHALL conservar los tres workflows del Pipeline_CI como verificaciones obligatorias en la protección de `main`, de modo que un workflow fallido impida integrar el Pull Request. _(ENG-04, DP-SDD-05)_
8. WHERE la implementación necesita cambiar un workflow, THE Equipo SHALL justificar el cambio en `design.md` y conservar `permissions: contents: read`, las acciones oficiales `actions/*` y la ausencia de secretos. _(ENG-04, `security.md`)_

### Requirement 29: Documentación, steering y tareas de cierre (CA-01, CA-07, NF-05)

**User Story:** Como integrante del equipo, quiero documentación y reglas actualizadas al cerrar la spec, para que cualquiera pueda ejecutar, probar y revisar el ERP.

#### Acceptance Criteria

1. THE Equipo SHALL documentar en `design.md` el contrato de cada endpoint del ERP con requisitos, roles, solicitud, respuesta y errores. _(DP-API-06)_
2. THE Equipo SHALL actualizar el README con el estado del proyecto y, en §11.6, las rutas del frontend ERP y los roles que las usan. _(CA-01)_
3. THE Equipo SHALL actualizar los steering `product.md` ("Estado actual del proyecto"), `architecture.md` (componentes y módulo `erp`), `structure.md` (paquete `erp`, vistas ERP, `erpApi.js` y migración), `api-conventions.md` (endpoints existentes y códigos de conflicto del ERP), `tech.md` (persistencia) y `security.md` (datos personales de empleados y prueba de logs del ERP) para reflejar el ERP como implementado. _(CA-07, `product.md`)_
4. THE Equipo SHALL registrar la Migracion_ERP en la tabla de `supabase/migrations/README.md`. _(NF-05)_
5. IF la implementación requiere una dependencia o una variable de entorno nueva, THEN THE Equipo SHALL justificarla en `design.md` antes de añadirla y, para una variable, añadirla a `.env.example` con un valor ficticio y documentarla en el README. _(NF-01, CA-01, `tech.md`)_
6. THE Equipo SHALL confirmar el trabajo en la rama `feat/erp-empleados-proveedores-finanzas` con commits Conventional Commits y abrir un único Pull Request hacia `main` al completar la spec, con la lista de comprobación de `sdd-workflow.md`. _(DP-SDD-04, DP-SDD-05)_
7. WHEN un integrante distinto del autor ha revisado la Migracion_ERP y `MigracionesErpIT` la ha validado en PostgreSQL efímero, THE Equipo SHALL aplicarla de forma controlada a la Base_Compartida antes de la validación manual del Requirement 30 y del Pull Request final, sin editarla después de aplicada. _(DP-TEC-03)_

### Requirement 30: Validación extremo a extremo del ERP (CA-03, CA-04, AUTH-05)

**User Story:** Como equipo, quiero demostrar el flujo del ERP con Supabase real, para cumplir las operaciones mínimas del módulo y comprobar sus permisos.

#### Acceptance Criteria

1. WHEN la Migracion_ERP está aplicada a la Base_Compartida, THE Equipo SHALL validar manualmente, con el Backend ejecutado mediante Compose_Local y el Frontend en local, que un usuario `FINANZAS_RRHH` registra y edita un Empleado, registra un Proveedor, registra un `EGRESO` asociado al Proveedor y un `INGRESO` asociado a un Cliente activo, marca un movimiento como `PAGADO`, anula otro y ve los datos y los totales del panel financiero persistidos después de recargar la aplicación. _(CA-04, ERP-01 a ERP-05)_
2. WHEN un usuario `COMERCIAL` u `OPERACIONES` intenta una operación del ERP durante la validación manual, THE Backend SHALL responder 403 sin modificar datos. _(CA-03, NF-02)_
3. WHEN un usuario `FINANZAS_RRHH` intenta una escritura del CRM durante la validación manual, THE Backend SHALL responder 403 sin modificar datos. _(CA-03, DP-PRO-01)_
4. WHEN el Backend responde 403 durante la validación manual, THE Frontend SHALL mostrar el mensaje de falta de permisos sin cerrar la sesión. _(AUTH-05)_
5. THE Equipo SHALL registrar la evidencia de la validación manual en `tasks.md` sin credenciales, tokens, UUID personales ni datos personales reales. _(NF-01, CA-07)_
