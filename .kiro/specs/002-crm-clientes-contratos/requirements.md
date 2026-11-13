# Requirements Document

## Introduction

Esta es la segunda especificación del proyecto Arise Corp. (`002-crm-clientes-contratos`). Construye el módulo CRM como patrón de referencia para ERP y SGA (`architecture.md`, "Orden de construcción", paso 5) e incorpora la contenedorización del backend, las pruebas de integración con PostgreSQL efímero y la integración continua con GitHub Actions.

Requisitos del MVP cubiertos:

- **Must:** CRM-01 (clientes), CRM-03 (contratos), CRM-04 (validación y duplicados).
- **Should incluidos por decisión del equipo:** CRM-02 (cuentas corporativas) y CRM-05 (resumen comercial).
- **No funcionales:** NF-01 a NF-07.
- **Criterios globales:** CA-01, CA-03, CA-04, CA-06, CA-07 y la parte cliente → contrato de CA-05.
- **Ingeniería:** ENG-01 (Docker), ENG-02 (pruebas unitarias), ENG-03 (pruebas de integración) y ENG-04 (CI).
- **Errores de interfaz:** AUTH-05 aplicado a las vistas CRM.

Decisiones aplicadas: DP-PRO-01, DP-ARQ-01, DP-ARQ-02, DP-SEG-02, DP-SEG-05, DP-SEG-06, DP-API-01 a DP-API-06, DP-TEC-03, DP-TEC-04, DP-EST-01 a DP-EST-05 y DP-SDD-03 a DP-SDD-05.

### Contexto de trabajo

- El trabajo se realiza en la rama `feat/crm-clientes-contratos`, creada desde `main`, con un único Pull Request al completar la spec (DP-SDD-05).
- La matriz de `SecurityConfig` ya cubre las rutas del CRM y no cambia. Las lecturas cruzadas solo cubren `GET /api/crm/clientes`, `GET /api/crm/clientes/*`, `GET /api/crm/contratos` y `GET /api/crm/contratos/*`. Por eso los contratos son un recurso de primer nivel (`/api/crm/contratos`) y no un subrecurso de clientes.
- La prueba `AutorizacionModulosTests` define endpoints de prueba bajo `/api/crm/**` que chocarán con los controladores reales; sus casos CRM se migran a pruebas de los controladores reales.

### Fuera de alcance

Eliminación física de clientes, cuentas o contratos; tabla de historial de contratos; automatizaciones comerciales; adjuntos; importación o exportación masiva; monedas distintas de COP; funcionalidades de ERP y SGA; despliegue continuo, publicación de imágenes en registros externos y base de datos dockerizada para desarrollo.

### Endpoints y matriz de acceso del CRM

| Endpoint | ADMINISTRADOR | COMERCIAL | FINANZAS_RRHH | OPERACIONES |
|---|---|---|---|---|
| `GET /api/crm/clientes`, `GET /api/crm/clientes/{id}` | Vista completa | Vista completa | Vista reducida | Vista reducida |
| `POST /api/crm/clientes`, `PUT /api/crm/clientes/{id}`, `PATCH /api/crm/clientes/{id}` | Permitido | Permitido | 403 | 403 |
| `GET`, `POST /api/crm/cuentas-corporativas`; `GET`, `PUT`, `PATCH /api/crm/cuentas-corporativas/{id}` | Permitido | Permitido | 403 | 403 |
| `GET /api/crm/contratos`, `GET /api/crm/contratos/{id}` | Permitido | Permitido | 403 | Permitido |
| `POST /api/crm/contratos`, `PUT /api/crm/contratos/{id}`, `PATCH /api/crm/contratos/{id}/estado` | Permitido | Permitido | 403 | 403 |
| `GET /api/crm/resumen` | Permitido | Permitido | 403 | 403 |

### Supuestos adoptados

| Tema | Supuesto |
|---|---|
| Cuenta corporativa | Empresa identificada por NIT y razón social que agrupa varios clientes. Datos de contacto opcionales. Se desactiva lógicamente. |
| Cliente | Pertenece a una cuenta corporativa o a ninguna. Nombre, correo y teléfono obligatorios; dirección y ciudad opcionales. |
| Contrato | Se vincula siempre a un cliente; la cuenta corporativa se deduce del cliente y no se guarda en el contrato. |
| Tipos de documento | `CC`, `CE`, `NIT`, `PASAPORTE`. |
| Normalización de documentos y NIT | Se eliminan espacios, puntos y guiones y las letras pasan a mayúsculas. `CC` y `NIT` admiten solo dígitos; `CE` y `PASAPORTE` admiten letras y dígitos. Longitud normalizada: 3 a 20 caracteres para clientes y 5 a 15 dígitos para el NIT de una cuenta. |
| Unicidad | El par (tipo de documento, número normalizado) es único entre clientes activos e inactivos. El NIT de la cuenta y el código del contrato son únicos. Un cliente inactivo se reactiva en lugar de duplicarse. |
| Código de contrato | Lo escribe el usuario; se guarda sin espacios iniciales ni finales y en mayúsculas; 3 a 30 caracteres entre letras, dígitos y guiones. |
| Valor del contrato | COP, `numeric(15,2)` / `BigDecimal`, mayor o igual a 0, con hasta 13 dígitos enteros y 2 decimales; viaja como texto decimal JSON (DP-API-04). |
| Límites de texto | Nombre y razón social: 200; correo: 254; teléfono: 7 a 20 caracteres entre dígitos, espacios, `+`, `-` y paréntesis; dirección: 200; ciudad: 100; nombre de contacto: 200; descripción del contrato: 1000; búsqueda `q`: 100. |
| Estados del contrato | Nace en `BORRADOR`. Transiciones: `BORRADOR→ACTIVO`, `ACTIVO→FINALIZADO`, `BORRADOR→CANCELADO`, `ACTIVO→CANCELADO`. `FINALIZADO` y `CANCELADO` son finales. |
| Edición del contrato | Solo en `BORRADOR`; el cliente asociado y el estado no se editan con `PUT`. |
| Cliente inactivo | No recibe contratos nuevos. Su desactivación no modifica los contratos existentes, que conservan sus transiciones permitidas. |
| Cuenta inactiva | No se asigna a un cliente; los clientes ya asociados conservan la asociación y pueden editarse sin cambiar de cuenta. |
| Auditoría | Las tres tablas guardan `creado_en`, `actualizado_en` y `actualizado_por` (`sub` del JWT). Los contratos guardan además el instante y el `sub` del último cambio de estado (DP-SEG-06). |
| Vista reducida del cliente | Para FINANZAS_RRHH y OPERACIONES: identificador, tipo y número de documento, nombre, ciudad, estado activo y cuenta corporativa (identificador y razón social). Excluye correo, teléfono, dirección y campos de auditoría. |

## Glossary

- **Backend:** API Spring Boot 4.1.2 (monolito modular) que valida el JWT, aplica la autorización por rol, valida las entradas, ejecuta las reglas de negocio y persiste los datos. El módulo CRM vive en el paquete `com.arise.corp.crm`.
- **Frontend:** SPA React con Vite, Tailwind CSS y React Router que presenta las vistas CRM y llama al Backend.
- **Cliente_HTTP:** módulo `frontend/src/services/apiClient.js` que envía las peticiones del Frontend al Backend con el token de acceso.
- **Base_Datos:** Supabase PostgreSQL; las tablas del CRM viven en el esquema privado `app` (DP-ARQ-02).
- **Base_Compartida:** instancia Supabase `arise-corp-dev` usada por el equipo para desarrollo.
- **Migracion_CRM:** archivo SQL nuevo en `supabase/migrations/` con el nombre `<timestamp>_<nombre>.sql` que crea o modifica objetos del CRM (DP-TEC-03).
- **Cliente:** persona natural o jurídica con relación comercial con Arise Corp., identificada por tipo y número de documento.
- **Cuenta_Corporativa:** empresa identificada por NIT y razón social que agrupa clientes.
- **Contrato:** acuerdo comercial vinculado a un único Cliente.
- **Estado_Contrato:** uno de `BORRADOR`, `ACTIVO`, `FINALIZADO` o `CANCELADO`.
- **Estado_Final:** `FINALIZADO` o `CANCELADO`; un Contrato en Estado_Final no admite más transiciones.
- **Transicion_Permitida:** cambio de Estado_Contrato incluido en el conjunto `BORRADOR→ACTIVO`, `ACTIVO→FINALIZADO`, `BORRADOR→CANCELADO`, `ACTIVO→CANCELADO`.
- **Documento_Normalizado:** número de documento o NIT al que se han eliminado espacios, puntos y guiones y cuyas letras se han convertido a mayúsculas.
- **Codigo_Normalizado:** código de Contrato sin espacios iniciales ni finales y con las letras en mayúsculas.
- **Actor:** usuario autenticado que realiza una petición; se identifica por el claim `sub` del JWT validado.
- **Perfil_Activo:** registro de `app.perfiles_usuario` con `activo = true` cuyo `usuario_id` coincide con el `sub` del Actor.
- **Rol_Gestor_CRM:** rol empresarial `ADMINISTRADOR` o `COMERCIAL`.
- **Rol_Lectura_Cruzada:** rol empresarial `FINANZAS_RRHH` (lee clientes) u `OPERACIONES` (lee clientes y contratos), según DP-PRO-01.
- **Vista_Completa_Cliente:** representación JSON del Cliente con todos sus campos y su auditoría.
- **Vista_Reducida_Cliente:** representación JSON del Cliente definida en "Supuestos adoptados" para los Rol_Lectura_Cruzada.
- **Lista_Paginada:** respuesta JSON `{ "content": [], "page", "size", "totalElements", "totalPages" }` definida en DP-API-03.
- **Resumen_Comercial:** respuesta con los conteos de clientes activos e inactivos y de contratos por Estado_Contrato (CRM-05).
- **Problem_Details:** cuerpo de error RFC 9457 (`application/problem+json`) con la extensión `errors` (`field`, `message`) para errores de campo (DP-API-02).
- **Suite_Backend:** pruebas unitarias, de contexto y de seguridad del Backend con sufijo `Tests`, ejecutadas por Surefire con `.\mvnw.cmd test`.
- **Suite_Integracion:** pruebas del Backend con sufijo `IT` en el paquete `integration`, ejecutadas por Failsafe con `.\mvnw.cmd verify` sobre PostgreSQL efímero con Testcontainers (DP-TEC-04, DP-EST-05).
- **Script_Preparacion_IT:** script SQL de prueba que crea en el PostgreSQL efímero el esquema `auth` con una tabla `auth.users` mínima y los roles `anon` y `authenticated`, requeridos por las migraciones reales.
- **Suite_Frontend:** pruebas Vitest + React Testing Library del Frontend, ejecutadas con `npm test` desde `frontend/`.
- **Imagen_Backend:** imagen Docker construida desde `backend/Dockerfile`.
- **Compose_Local:** archivo `docker-compose.yml` en la raíz del repositorio.
- **Pipeline_CI:** workflows de GitHub Actions `backend-ci.yml`, `integration-tests.yml` y `frontend-ci.yml` en `.github/workflows/`.
- **Equipo:** Davidson Sánchez Gordillo, Paula Isabella Martínez Escalante y Sebastián Pardo Tirado.

## Requirements

### Requirement 1: Organización del módulo CRM (NF-04, DP-EST-01, DP-ARQ-01)

**User Story:** Como integrante del equipo, quiero que el CRM siga la estructura por módulo y capas acordada, para que ERP y SGA puedan copiar el mismo patrón.

#### Acceptance Criteria

1. THE Backend SHALL ubicar el código del CRM en el paquete `com.arise.corp.crm` con las capas `controller`, `service`, `repository`, `entity` y `dto`. _(NF-04, DP-EST-01)_
2. THE Backend SHALL recibir y devolver DTO en los controladores del CRM y mantener las entidades JPA dentro de la capa de servicio. _(NF-04)_
3. THE Backend SHALL ubicar las reglas de negocio y las transacciones del CRM en la capa de servicio, con controladores limitados a validar el DTO, delegar en el servicio y construir la respuesta HTTP. _(NF-04)_
4. THE Backend SHALL mantener el módulo CRM sin dependencias hacia los módulos `erp` y `sga`. _(DP-ARQ-01)_

### Requirement 2: Modelo de datos y migraciones del CRM (NF-03, NF-05, DP-TEC-03, DP-SEG-05)

**User Story:** Como integrante del equipo, quiero que las tablas del CRM se creen con migraciones versionadas y restricciones en PostgreSQL, para que la integridad no dependa solo del backend.

#### Acceptance Criteria

1. THE Migracion_CRM SHALL crear las tablas `app.cuentas_corporativas`, `app.clientes` y `app.contratos` en archivos nuevos de `supabase/migrations/` con un timestamp posterior a `20261108000000`, sin modificar la migración existente. _(NF-05, DP-TEC-03)_
2. THE Migracion_CRM SHALL definir en `app.cuentas_corporativas` un identificador UUID como clave primaria, `nit` obligatorio y único, `razon_social` obligatoria, `nombre_contacto`, `correo_contacto` y `telefono_contacto` opcionales, `activa` obligatoria con valor por defecto verdadero, y `creado_en`, `actualizado_en` y `actualizado_por`. _(CRM-02, NF-03)_
3. THE Migracion_CRM SHALL definir en `app.clientes` un identificador UUID como clave primaria, `tipo_documento` obligatorio con una restricción `CHECK` sobre `CC`, `CE`, `NIT` y `PASAPORTE`, `numero_documento` obligatorio, una restricción de unicidad sobre el par (`tipo_documento`, `numero_documento`), `nombre`, `correo` y `telefono` obligatorios, `direccion` y `ciudad` opcionales, `cuenta_corporativa_id` opcional con clave foránea a `app.cuentas_corporativas`, `activo` obligatorio con valor por defecto verdadero, y `creado_en`, `actualizado_en` y `actualizado_por`. _(CRM-01, CRM-04, NF-03)_
4. THE Migracion_CRM SHALL definir en `app.contratos` un identificador UUID como clave primaria, `cliente_id` obligatorio con clave foránea a `app.clientes`, `codigo` obligatorio y único, `descripcion` obligatoria, `fecha_inicio` de tipo `date` obligatoria, `fecha_fin` de tipo `date` opcional, `valor` de tipo `numeric(15,2)` obligatorio, `estado` obligatorio con valor por defecto `BORRADOR`, `estado_actualizado_en`, `estado_actualizado_por`, `creado_en`, `actualizado_en` y `actualizado_por`. _(CRM-03, NF-03, DP-SEG-06)_
5. THE Migracion_CRM SHALL declarar en `app.contratos` restricciones `CHECK` que limiten `estado` a `BORRADOR`, `ACTIVO`, `FINALIZADO` y `CANCELADO`, exijan `valor >= 0` y exijan que `fecha_fin` sea nula o igual o posterior a `fecha_inicio`. _(NF-03, DP-API-04)_
6. THE Migracion_CRM SHALL relacionar `app.contratos` con la Cuenta_Corporativa únicamente a través de `cliente_id`, sin columna propia de cuenta corporativa. _(CRM-03)_
7. THE Migracion_CRM SHALL usar `timestamptz` para los instantes y `uuid` para `actualizado_por` y `estado_actualizado_por`. _(DP-API-04, DP-SEG-06)_
8. THE Migracion_CRM SHALL habilitar RLS sin políticas en las tres tablas y revocar sus privilegios a los roles `anon` y `authenticated`. _(DP-SEG-05)_
9. THE Migracion_CRM SHALL contener únicamente estructura, sin datos de ejemplo, UUID personales ni secretos. _(NF-01)_
10. WHEN el Backend arranca contra una base con todas las migraciones aplicadas, THE Backend SHALL superar la validación `spring.jpa.hibernate.ddl-auto=validate` de las entidades del CRM. _(DP-TEC-03)_

### Requirement 3: Normalización de identificadores (CRM-04)

**User Story:** Como usuario Comercial, quiero que el sistema reconozca el mismo documento escrito con distintos formatos, para no registrar clientes o cuentas duplicados por error.

#### Acceptance Criteria

1. WHEN el Backend recibe el número de documento de un Cliente o el NIT de una Cuenta_Corporativa, THE Backend SHALL calcular el Documento_Normalizado antes de validar, comparar o almacenar el valor. _(CRM-04)_
2. THE Backend SHALL producir, al normalizar un Documento_Normalizado, un resultado idéntico a la entrada. _(CRM-04)_
3. THE Backend SHALL tratar como el mismo documento dos entradas que difieren solo en espacios, puntos, guiones o mayúsculas y minúsculas, por ejemplo `1.023.456-7` y `10234567`. _(CRM-04)_
4. WHEN el Backend recibe el código de un Contrato, THE Backend SHALL calcular el Codigo_Normalizado antes de validar, comparar o almacenar el valor. _(CRM-04)_
5. WHEN el Backend recibe un campo de texto, THE Backend SHALL eliminar los espacios iniciales y finales y almacenar como nulo un campo opcional que quede vacío. _(NF-03)_

### Requirement 4: Registro de clientes (CRM-01, CRM-04)

**User Story:** Como usuario Comercial, quiero registrar clientes con sus datos de contacto, para gestionar la relación comercial desde un solo lugar.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_CRM envía `POST /api/crm/clientes` con datos válidos, THE Backend SHALL crear el Cliente con `activo = true`, asignar `creadoEn`, `actualizadoEn` y `actualizadoPor` con el `sub` del Actor, y responder 201 con la Vista_Completa_Cliente y el encabezado `Location: /api/crm/clientes/{id}`. _(CRM-01, DP-SEG-06)_
2. THE Backend SHALL exigir en el registro de un Cliente los campos `tipoDocumento`, `numeroDocumento`, `nombre`, `correo` y `telefono`, y aceptar como opcionales `direccion`, `ciudad` y `cuentaCorporativaId`. _(CRM-01, CRM-04)_
3. IF falta un campo obligatorio o llega vacío, THEN THE Backend SHALL responder 400 con Problem_Details y una entrada en `errors` por cada campo faltante, sin crear el Cliente. _(CRM-04, CA-06)_
4. IF `tipoDocumento` no es `CC`, `CE`, `NIT` ni `PASAPORTE`, THEN THE Backend SHALL responder 400 con un error en el campo `tipoDocumento`. _(CRM-04)_
5. IF el Documento_Normalizado tiene menos de 3 o más de 20 caracteres, contiene caracteres distintos de letras y dígitos, o contiene letras con `tipoDocumento` `CC` o `NIT`, THEN THE Backend SHALL responder 400 con un error en el campo `numeroDocumento`. _(CRM-04)_
6. IF `nombre` supera 200 caracteres, `correo` tiene un formato inválido o supera 254 caracteres, `telefono` tiene menos de 7 o más de 20 caracteres o caracteres distintos de dígitos, espacios, `+`, `-` y paréntesis, `direccion` supera 200 caracteres o `ciudad` supera 100 caracteres, THEN THE Backend SHALL responder 400 con un error por cada campo inválido. _(CRM-04)_
7. IF existe un Cliente activo con el mismo `tipoDocumento` y Documento_Normalizado, THEN THE Backend SHALL responder 409 con un mensaje que indique que el cliente ya está registrado, sin crear el Cliente. _(CRM-04)_
8. IF existe un Cliente inactivo con el mismo `tipoDocumento` y Documento_Normalizado, THEN THE Backend SHALL responder 409 con un mensaje que indique que el cliente existe inactivo y debe reactivarse, e incluir el identificador del Cliente existente, sin crear un Cliente nuevo. _(CRM-04)_
9. WHEN el cuerpo de la solicitud incluye `id`, `activo` o campos de auditoría, THE Backend SHALL ignorar esos valores y asignar los valores controlados por el servidor. _(NF-02, `security.md`)_

### Requirement 5: Listado, búsqueda y consulta de clientes (CRM-01, DP-API-03)

**User Story:** Como usuario con acceso al CRM, quiero listar, buscar y consultar clientes, para encontrar la información que necesito sin recorrer todos los registros.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_CRM o Rol_Lectura_Cruzada envía `GET /api/crm/clientes`, THE Backend SHALL responder 200 con una Lista_Paginada de clientes activos e inactivos. _(CRM-01, DP-API-03)_
2. THE Backend SHALL aceptar en `GET /api/crm/clientes` los parámetros `page` (desde 0, por defecto 0), `size` (de 1 a 100, por defecto 20) y `sort` con los campos `nombre`, `numeroDocumento` y `creadoEn` en dirección `asc` o `desc`, con `nombre,asc` por defecto. _(DP-API-03)_
3. WHERE la solicitud incluye el parámetro `q`, THE Backend SHALL devolver los clientes cuyo `nombre` contiene `q` sin distinguir mayúsculas y minúsculas o cuyo número de documento contiene el Documento_Normalizado de `q`. _(CRM-01)_
4. WHERE la solicitud incluye el parámetro `activo`, THE Backend SHALL devolver solo los clientes cuyo estado activo coincide con el valor recibido. _(CRM-01)_
5. WHERE la solicitud incluye el parámetro `cuentaCorporativaId`, THE Backend SHALL devolver solo los clientes asociados a esa Cuenta_Corporativa. _(CRM-02)_
6. IF `page` es negativo, `size` está fuera de 1 a 100, `sort` usa un campo o una dirección no permitidos, `q` supera 100 caracteres, `activo` no es booleano o `cuentaCorporativaId` no es un UUID, THEN THE Backend SHALL responder 400 con Problem_Details. _(DP-API-03, CA-06)_
7. WHEN un Actor con Rol_Gestor_CRM o Rol_Lectura_Cruzada envía `GET /api/crm/clientes/{id}` para un Cliente existente, THE Backend SHALL responder 200 con el Cliente, esté activo o inactivo. _(CRM-01)_
8. IF el `{id}` de `GET /api/crm/clientes/{id}` no corresponde a ningún Cliente, THEN THE Backend SHALL responder 404 con Problem_Details. _(CA-06)_
9. IF el `{id}` de una ruta del CRM no es un UUID, THEN THE Backend SHALL responder 400 con Problem_Details. _(CA-06)_

### Requirement 6: Vista del cliente según el rol (DP-PRO-01, NF-02)

**User Story:** Como Administrador, quiero que los roles con lectura cruzada vean solo los datos del cliente que necesitan, para limitar la exposición de datos personales.

#### Acceptance Criteria

1. WHILE el Actor tiene Rol_Gestor_CRM, THE Backend SHALL devolver la Vista_Completa_Cliente en `GET /api/crm/clientes` y `GET /api/crm/clientes/{id}`. _(DP-PRO-01)_
2. WHILE el Actor tiene Rol_Lectura_Cruzada, THE Backend SHALL devolver la Vista_Reducida_Cliente en `GET /api/crm/clientes` y `GET /api/crm/clientes/{id}`. _(DP-PRO-01, `security.md`)_
3. THE Backend SHALL elegir la vista del Cliente a partir del rol del Perfil_Activo, sin aceptar parámetros del cliente que cambien la vista. _(NF-02)_
4. THE Backend SHALL excluir de los logs el correo, el teléfono, la dirección y el número de documento de los clientes. _(`security.md`)_

### Requirement 7: Edición de clientes (CRM-01, CRM-04)

**User Story:** Como usuario Comercial, quiero editar los datos de un cliente, para mantener su información actualizada.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_CRM envía `PUT /api/crm/clientes/{id}` con datos válidos, THE Backend SHALL reemplazar `tipoDocumento`, `numeroDocumento`, `nombre`, `correo`, `telefono`, `direccion`, `ciudad` y `cuentaCorporativaId`, actualizar `actualizadoEn` y `actualizadoPor` con el `sub` del Actor, y responder 200 con la Vista_Completa_Cliente. _(CRM-01, DP-SEG-06)_
2. THE Backend SHALL aplicar en `PUT /api/crm/clientes/{id}` las mismas validaciones de campos que en el registro de clientes. _(CRM-04)_
3. WHEN `PUT /api/crm/clientes/{id}` omite un campo opcional o lo envía nulo, THE Backend SHALL dejar ese campo nulo. _(CRM-01)_
4. IF el `tipoDocumento` y el Documento_Normalizado enviados pertenecen a otro Cliente, activo o inactivo, THEN THE Backend SHALL responder 409 sin modificar el Cliente. _(CRM-04)_
5. THE Backend SHALL conservar `activo` y `creadoEn` al procesar `PUT /api/crm/clientes/{id}`. _(CRM-01)_
6. IF el `{id}` de `PUT /api/crm/clientes/{id}` no corresponde a ningún Cliente, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 8: Desactivación y reactivación de clientes (CRM-01, DP-API-05)

**User Story:** Como usuario Comercial, quiero desactivar y reactivar clientes, para conservar su historial sin eliminarlos.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_CRM envía `PATCH /api/crm/clientes/{id}` con `{ "activo": false }` para un Cliente activo, THE Backend SHALL marcar el Cliente como inactivo, actualizar `actualizadoEn` y `actualizadoPor` y responder 200 con la Vista_Completa_Cliente. _(CRM-01, DP-API-05)_
2. WHEN un Actor con Rol_Gestor_CRM envía `PATCH /api/crm/clientes/{id}` con `{ "activo": true }` para un Cliente inactivo, THE Backend SHALL marcar el Cliente como activo, actualizar `actualizadoEn` y `actualizadoPor` y responder 200 con la Vista_Completa_Cliente. _(CRM-01, CRM-04)_
3. WHEN el valor de `activo` recibido coincide con el estado actual del Cliente, THE Backend SHALL responder 200 con el Cliente sin modificar sus datos ni su auditoría. _(CRM-01)_
4. IF el cuerpo de `PATCH /api/crm/clientes/{id}` no contiene `activo` o `activo` no es booleano, THEN THE Backend SHALL responder 400 con un error en el campo `activo`. _(CA-06)_
5. WHEN el Backend desactiva un Cliente, THE Backend SHALL conservar sin cambios el estado y los datos de los contratos de ese Cliente. _(CRM-01, CRM-03)_
6. IF un Actor con Rol_Gestor_CRM envía `DELETE /api/crm/clientes/{id}`, THEN THE Backend SHALL responder 405 con Problem_Details y conservar el Cliente; sin token o con otro rol se aplican primero las respuestas 401 y 403 del Requirement 16. _(CRM-01)_
7. IF el `{id}` de `PATCH /api/crm/clientes/{id}` no corresponde a ningún Cliente, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 9: Gestión de cuentas corporativas (CRM-02)

**User Story:** Como usuario Comercial, quiero registrar, consultar, editar y desactivar cuentas corporativas, para agrupar a los clientes que pertenecen a una misma empresa.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_CRM envía `POST /api/crm/cuentas-corporativas` con datos válidos, THE Backend SHALL crear la Cuenta_Corporativa con `activa = true`, asignar su auditoría con el `sub` del Actor y responder 201 con la cuenta y el encabezado `Location: /api/crm/cuentas-corporativas/{id}`. _(CRM-02, DP-SEG-06)_
2. THE Backend SHALL exigir `nit` y `razonSocial` en el registro y la edición de una Cuenta_Corporativa, y aceptar como opcionales `nombreContacto`, `correoContacto` y `telefonoContacto`. _(CRM-02, CRM-04)_
3. IF el Documento_Normalizado del `nit` contiene caracteres distintos de dígitos o tiene menos de 5 o más de 15 dígitos, THEN THE Backend SHALL responder 400 con un error en el campo `nit`. _(CRM-04)_
4. IF `razonSocial` o `nombreContacto` superan 200 caracteres, `correoContacto` tiene un formato inválido o supera 254 caracteres, o `telefonoContacto` incumple el formato de teléfono de los supuestos, THEN THE Backend SHALL responder 400 con un error por cada campo inválido. _(CRM-04)_
5. IF el Documento_Normalizado del `nit` pertenece a otra Cuenta_Corporativa, activa o inactiva, THEN THE Backend SHALL responder 409 sin crear ni modificar la cuenta. _(CRM-04)_
6. WHEN un Actor con Rol_Gestor_CRM envía `GET /api/crm/cuentas-corporativas`, THE Backend SHALL responder 200 con una Lista_Paginada que admite `q` (razón social sin distinguir mayúsculas o NIT normalizado), `activa`, `page`, `size` y `sort` sobre `razonSocial`, `nit` y `creadoEn`, con `razonSocial,asc` por defecto. _(CRM-02, DP-API-03)_
7. IF un parámetro de `GET /api/crm/cuentas-corporativas` incumple las reglas de paginación, orden o tipo, THEN THE Backend SHALL responder 400 con Problem_Details. _(DP-API-03)_
8. WHEN un Actor con Rol_Gestor_CRM envía `GET /api/crm/cuentas-corporativas/{id}` para una cuenta existente, THE Backend SHALL responder 200 con la Cuenta_Corporativa. _(CRM-02)_
9. WHEN un Actor con Rol_Gestor_CRM envía `PUT /api/crm/cuentas-corporativas/{id}` con datos válidos, THE Backend SHALL reemplazar `nit`, `razonSocial`, `nombreContacto`, `correoContacto` y `telefonoContacto`, actualizar su auditoría y responder 200. _(CRM-02)_
10. WHEN un Actor con Rol_Gestor_CRM envía `PATCH /api/crm/cuentas-corporativas/{id}` con `activa` booleano, THE Backend SHALL fijar el estado de la cuenta, actualizar su auditoría si el estado cambia y responder 200. _(CRM-02, DP-API-05)_
11. IF el cuerpo de `PATCH /api/crm/cuentas-corporativas/{id}` no contiene `activa` booleano, THEN THE Backend SHALL responder 400 con un error en el campo `activa`. _(CA-06)_
12. WHEN el Backend desactiva una Cuenta_Corporativa, THE Backend SHALL conservar la asociación de los clientes ya vinculados a esa cuenta. _(CRM-02)_
13. IF el `{id}` de una ruta de cuentas corporativas no corresponde a ninguna Cuenta_Corporativa, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 10: Relación entre cliente y cuenta corporativa (CRM-02)

**User Story:** Como usuario Comercial, quiero asociar un cliente a una cuenta corporativa o dejarlo sin cuenta, para reflejar a qué empresa pertenece.

#### Acceptance Criteria

1. THE Backend SHALL asociar cada Cliente a una Cuenta_Corporativa o a ninguna mediante `cuentaCorporativaId`. _(CRM-02)_
2. WHEN `PUT /api/crm/clientes/{id}` envía `cuentaCorporativaId` nulo, THE Backend SHALL eliminar la asociación del Cliente con su Cuenta_Corporativa. _(CRM-02)_
3. IF `POST /api/crm/clientes` o `PUT /api/crm/clientes/{id}` envían un `cuentaCorporativaId` que no corresponde a ninguna Cuenta_Corporativa, THEN THE Backend SHALL responder 404 sin crear ni modificar el Cliente. _(CRM-02, CA-06)_
4. IF `POST /api/crm/clientes` o `PUT /api/crm/clientes/{id}` asignan una Cuenta_Corporativa inactiva distinta de la cuenta actual del Cliente, THEN THE Backend SHALL responder 409 sin crear ni modificar el Cliente. _(CRM-02)_
5. WHEN `PUT /api/crm/clientes/{id}` conserva la Cuenta_Corporativa actual del Cliente y esa cuenta está inactiva, THE Backend SHALL aplicar la edición. _(CRM-02)_
6. THE Backend SHALL incluir en la Vista_Completa_Cliente y en la Vista_Reducida_Cliente el identificador y la razón social de la Cuenta_Corporativa asociada, o un valor nulo si el Cliente no tiene cuenta. _(CRM-02)_

### Requirement 11: Registro de contratos (CRM-03, CRM-04)

**User Story:** Como usuario Comercial, quiero registrar contratos vinculados a un cliente, para formalizar y seguir los acuerdos comerciales.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_CRM envía `POST /api/crm/contratos` con datos válidos, THE Backend SHALL crear el Contrato en estado `BORRADOR`, asignar su auditoría y la auditoría de estado con el `sub` del Actor y el instante de creación, y responder 201 con el Contrato y el encabezado `Location: /api/crm/contratos/{id}`. _(CRM-03, DP-SEG-06)_
2. THE Backend SHALL exigir en el registro de un Contrato los campos `clienteId`, `codigo`, `descripcion`, `fechaInicio` y `valor`, y aceptar `fechaFin` como opcional. _(CRM-03, CRM-04)_
3. IF falta un campo obligatorio o llega vacío, THEN THE Backend SHALL responder 400 con un error por cada campo faltante, sin crear el Contrato. _(CRM-04)_
4. IF el Codigo_Normalizado tiene menos de 3 o más de 30 caracteres o contiene caracteres distintos de letras, dígitos y guiones, THEN THE Backend SHALL responder 400 con un error en el campo `codigo`. _(CRM-04)_
5. IF `descripcion` supera 1000 caracteres, THEN THE Backend SHALL responder 400 con un error en el campo `descripcion`. _(CRM-04)_
6. IF `fechaInicio` o `fechaFin` no son fechas ISO-8601 válidas, THEN THE Backend SHALL responder 400 con un error en el campo correspondiente. _(DP-API-04)_
7. IF `fechaFin` es anterior a `fechaInicio`, THEN THE Backend SHALL responder 400 con un error en el campo `fechaFin`. _(CRM-04, NF-03)_
8. IF `valor` es negativo, tiene más de 2 decimales o más de 13 dígitos enteros, THEN THE Backend SHALL responder 400 con un error en el campo `valor`. _(CRM-04, DP-API-04)_
9. IF el Codigo_Normalizado pertenece a otro Contrato, THEN THE Backend SHALL responder 409 sin crear el Contrato. _(CRM-04)_
10. IF `clienteId` no corresponde a ningún Cliente, THEN THE Backend SHALL responder 404 sin crear el Contrato. _(CRM-03, CA-06)_
11. IF `clienteId` corresponde a un Cliente inactivo, THEN THE Backend SHALL responder 409 con un mensaje que indique que un cliente inactivo no admite contratos nuevos, sin crear el Contrato. _(CRM-03)_
12. WHEN el cuerpo de la solicitud incluye `id`, `estado` o campos de auditoría, THE Backend SHALL ignorar esos valores y crear el Contrato en estado `BORRADOR`. _(NF-02, `security.md`)_

### Requirement 12: Consulta de contratos (CRM-03)

**User Story:** Como usuario Comercial u Operaciones, quiero consultar los contratos y su estado, para saber qué acuerdos están vigentes con cada cliente.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_CRM u `OPERACIONES` envía `GET /api/crm/contratos`, THE Backend SHALL responder 200 con una Lista_Paginada que admite los filtros `clienteId` y `estado` y el orden `sort` sobre `codigo`, `fechaInicio` y `creadoEn`, con `creadoEn,desc` por defecto. _(CRM-03, DP-API-03)_
2. WHERE la solicitud incluye un `clienteId` sin contratos o sin Cliente correspondiente, THE Backend SHALL responder 200 con una Lista_Paginada vacía. _(CRM-03)_
3. IF un parámetro de `GET /api/crm/contratos` incumple las reglas de paginación u orden, `clienteId` no es un UUID o `estado` no es un Estado_Contrato, THEN THE Backend SHALL responder 400 con Problem_Details. _(DP-API-03)_
4. WHEN un Actor con Rol_Gestor_CRM u `OPERACIONES` envía `GET /api/crm/contratos/{id}` para un Contrato existente, THE Backend SHALL responder 200 con `id`, `codigo`, `descripcion`, `fechaInicio`, `fechaFin`, `valor`, `estado`, el identificador y el nombre del Cliente, la Cuenta_Corporativa actual del Cliente (identificador y razón social, o nulo), la auditoría de estado y las fechas de creación y actualización. _(CRM-03)_
5. THE Backend SHALL serializar `valor` como texto decimal JSON con dos decimales. _(DP-API-04)_
6. IF el `{id}` de `GET /api/crm/contratos/{id}` no corresponde a ningún Contrato, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 13: Edición de contratos en borrador (CRM-03, CRM-04)

**User Story:** Como usuario Comercial, quiero corregir un contrato mientras está en borrador, para no alterar acuerdos ya activos.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_CRM envía `PUT /api/crm/contratos/{id}` con datos válidos para un Contrato en estado `BORRADOR`, THE Backend SHALL reemplazar `codigo`, `descripcion`, `fechaInicio`, `fechaFin` y `valor`, actualizar `actualizadoEn` y `actualizadoPor` y responder 200 con el Contrato. _(CRM-03)_
2. THE Backend SHALL aplicar en `PUT /api/crm/contratos/{id}` las mismas validaciones de campos que en el registro de contratos. _(CRM-04)_
3. IF el Contrato no está en estado `BORRADOR`, THEN THE Backend SHALL responder 409 con un mensaje que indique que solo se editan contratos en borrador, sin modificar el Contrato. _(CRM-03)_
4. THE Backend SHALL conservar el Cliente asociado y el Estado_Contrato al procesar `PUT /api/crm/contratos/{id}`, aunque el cuerpo incluya `clienteId` o `estado`. _(CRM-03, DP-API-05)_
5. IF el Codigo_Normalizado enviado pertenece a otro Contrato, THEN THE Backend SHALL responder 409 sin modificar el Contrato. _(CRM-04)_
6. IF el `{id}` de `PUT /api/crm/contratos/{id}` no corresponde a ningún Contrato, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 14: Cambio de estado del contrato (CRM-03, DP-API-05, DP-SEG-06)

**User Story:** Como usuario Comercial, quiero cambiar el estado de un contrato con transiciones controladas, para que su ciclo de vida sea coherente y quede registrado quién lo cambió.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_CRM envía `PATCH /api/crm/contratos/{id}/estado` con un `estado` que forma una Transicion_Permitida desde el estado actual, THE Backend SHALL actualizar `estado`, `estadoActualizadoEn` con el instante UTC y `estadoActualizadoPor` con el `sub` del Actor en una misma transacción y responder 200 con el Contrato. _(CRM-03, DP-API-05, DP-SEG-06)_
2. IF el `estado` solicitado no forma una Transicion_Permitida desde el estado actual, incluido el mismo estado, el paso a `BORRADOR` o cualquier cambio desde un Estado_Final, THEN THE Backend SHALL responder 409 con un mensaje que indique el estado actual y el solicitado, sin modificar el Contrato. _(CRM-03, DP-API-05)_
3. IF el cuerpo no contiene `estado` o `estado` no es un Estado_Contrato, THEN THE Backend SHALL responder 400 con un error en el campo `estado`. _(CA-06)_
4. IF la actualización del estado o de su auditoría falla, THEN THE Backend SHALL revertir la operación completa y conservar el estado y la auditoría anteriores. _(DP-SEG-06, DP-API-05)_
5. WHILE el Cliente del Contrato está inactivo, THE Backend SHALL aplicar a ese Contrato las mismas Transicion_Permitida que a los contratos de clientes activos. _(CRM-03)_
6. IF el `{id}` de `PATCH /api/crm/contratos/{id}/estado` no corresponde a ningún Contrato, THEN THE Backend SHALL responder 404. _(CA-06)_

### Requirement 15: Resumen comercial (CRM-05)

**User Story:** Como usuario Comercial, quiero ver un resumen con los conteos de clientes y contratos por estado, para conocer de un vistazo la situación comercial.

#### Acceptance Criteria

1. WHEN un Actor con Rol_Gestor_CRM envía `GET /api/crm/resumen`, THE Backend SHALL responder 200 con el Resumen_Comercial: número de clientes activos, número de clientes inactivos, total de clientes, número de contratos en cada Estado_Contrato y total de contratos. _(CRM-05)_
2. THE Backend SHALL incluir en el Resumen_Comercial los cuatro Estado_Contrato, con valor 0 para los estados sin contratos. _(CRM-05)_
3. THE Backend SHALL calcular el Resumen_Comercial con los datos persistidos en el momento de la solicitud, con totales iguales a la suma de sus componentes. _(CRM-05, CA-04)_

### Requirement 16: Autorización por rol en el CRM (NF-02, CA-03, DP-PRO-01, DP-SEG-02)

**User Story:** Como Administrador, quiero que el backend aplique la matriz de acceso del CRM, para que cada rol opere solo sobre lo que le corresponde.

#### Acceptance Criteria

1. THE Backend SHALL aplicar a los endpoints del CRM la matriz de acceso de la Introducción con los patrones y roles actuales de `SecurityConfig`, sin modificarlos. _(NF-02, DP-SEG-02)_
2. IF una petición a un endpoint del CRM llega sin token o con un token inválido o vencido, THEN THE Backend SHALL responder 401 con Problem_Details y el encabezado `WWW-Authenticate`. _(CA-02, CA-06)_
3. IF una petición a un endpoint del CRM llega con un JWT válido sin Perfil_Activo, THEN THE Backend SHALL responder 403 sin ejecutar la operación. _(CA-03, DP-PRO-01)_
4. IF el rol del Perfil_Activo no está permitido para el endpoint según la matriz de acceso, THEN THE Backend SHALL responder 403 sin ejecutar la operación ni modificar datos. _(CA-03, NF-02)_
5. IF un Actor sin permiso sobre un endpoint del CRM solicita un recurso inexistente, THEN THE Backend SHALL responder 403, sin revelar si el recurso existe. _(CA-06, `security.md`)_
6. THE Backend SHALL tomar el actor de `actualizadoPor` y `estadoActualizadoPor` del claim `sub` del JWT validado. _(DP-SEG-06)_

### Requirement 17: Respuestas de error del CRM (CA-06, DP-API-02, AUTH-05)

**User Story:** Como usuario, quiero mensajes de error comprensibles cuando una operación del CRM falla, para saber qué corregir.

#### Acceptance Criteria

1. THE Backend SHALL responder los errores del CRM con Problem_Details y mensajes en español. _(DP-API-02, DP-EST-02)_
2. IF la validación de un DTO falla, THEN THE Backend SHALL responder 400 con una entrada en `errors` por cada campo inválido, con `field` y un `message` comprensible. _(DP-API-02, NF-06)_
3. IF el cuerpo es un JSON mal formado, un campo tiene un tipo incorrecto o un valor de enumeración desconocido, THEN THE Backend SHALL responder 400 sin exponer detalles del deserializador. _(CA-06)_
4. IF el servicio detecta un conflicto de negocio (documento, NIT o código duplicado, transición no permitida, cliente inactivo, cuenta inactiva o contrato no editable), THEN THE Backend SHALL responder 409 con un `detail` que describa el conflicto concreto. _(CRM-04, CA-06)_
5. IF la Base_Datos rechaza una operación por una restricción, por ejemplo por un duplicado concurrente, THEN THE Backend SHALL responder 409 con un mensaje genérico, sin exponer SQL ni nombres de restricciones. _(NF-03, CA-06)_
6. THE Backend SHALL excluir de todas las respuestas de error del CRM las trazas de pila, el SQL, los nombres de clases y los datos internos. _(CA-06)_

### Requirement 18: Cliente HTTP del frontend (AUTH-05, NF-06)

**User Story:** Como integrante del equipo frontend, quiero un cliente HTTP que envíe datos y traduzca los errores del backend, para que todas las vistas CRM manejen los errores de la misma manera.

#### Acceptance Criteria

1. THE Cliente_HTTP SHALL enviar peticiones `GET`, `POST`, `PUT` y `PATCH` con el encabezado `Authorization: Bearer <token>` de la sesión vigente. _(AUTH-03)_
2. WHEN una petición lleva cuerpo, THE Cliente_HTTP SHALL serializar el cuerpo como JSON y enviar `Content-Type: application/json`. _(DP-API-04)_
3. WHEN el Backend responde 400 con la extensión `errors`, THE Cliente_HTTP SHALL entregar a la vista la lista de errores por campo. _(NF-06, AUTH-05)_
4. WHEN el Backend responde 409, THE Cliente_HTTP SHALL entregar a la vista el `detail` y las extensiones del Problem_Details. _(CRM-04, AUTH-05)_
5. WHEN el Backend responde 404, THE Cliente_HTTP SHALL entregar a la vista un error identificable como recurso no encontrado. _(AUTH-05)_
6. WHEN el Backend responde 401, THE Cliente_HTTP SHALL conservar el comportamiento de `001-foundation`: redirigir a la vista de inicio de sesión e indicar que la sesión expiró. _(AUTH-02, AUTH-05)_
7. WHEN el Backend responde 403, THE Cliente_HTTP SHALL entregar un mensaje de falta de permisos sin cerrar la sesión. _(AUTH-05)_
8. IF el Backend responde 5xx, la conexión falla o no hay respuesta en 30 segundos, THEN THE Cliente_HTTP SHALL entregar un mensaje genérico que permita reintentar, sin detalles técnicos. _(AUTH-05, NF-06)_
9. THE Cliente_HTTP SHALL conservar el funcionamiento de las llamadas existentes a `GET /api/me`. _(AUTH-04)_

### Requirement 19: Navegación CRM según el rol (NF-02, AUTH-05)

**User Story:** Como usuario autenticado, quiero ver en el menú solo las opciones del CRM que corresponden a mi rol, para no encontrar opciones que no puedo usar.

#### Acceptance Criteria

1. WHILE existe una sesión activa, THE Frontend SHALL mostrar en las rutas privadas un menú de navegación construido con el rol devuelto por `GET /api/me`. _(AUTH-04)_
2. WHILE el usuario tiene Rol_Gestor_CRM, THE Frontend SHALL mostrar en el menú las opciones Clientes, Cuentas corporativas y Resumen comercial. _(DP-PRO-01)_
3. WHILE el usuario tiene Rol_Lectura_Cruzada, THE Frontend SHALL mostrar en el menú solo la opción Clientes, en modo consulta. _(DP-PRO-01)_
4. WHILE el usuario tiene Rol_Lectura_Cruzada, THE Frontend SHALL ocultar los controles de creación, edición, activación, desactivación y cambio de estado. _(DP-PRO-01)_
5. IF el usuario abre directamente la ruta de una vista CRM no permitida para su rol, THEN THE Frontend SHALL mostrar un mensaje de falta de permisos sin solicitar datos de esa vista. _(AUTH-05)_
6. THE Frontend SHALL tratar la visibilidad de menús y controles como ayuda de interfaz y mostrar el mensaje de falta de permisos cuando el Backend responda 403. _(NF-02, AUTH-05)_
7. THE Frontend SHALL conservar las rutas `/login` y `/perfil` y su comportamiento. _(AUTH-01, AUTH-02)_

### Requirement 20: Vistas de clientes (CRM-01, CRM-04, NF-06)

**User Story:** Como usuario Comercial, quiero pantallas para listar, registrar, editar, consultar y activar o desactivar clientes, para gestionar el CRM sin usar la API directamente.

#### Acceptance Criteria

1. THE Frontend SHALL mostrar una lista paginada de clientes con búsqueda por nombre o documento, filtro activo/inactivo y controles de página, a partir de `GET /api/crm/clientes`. _(CRM-01, DP-API-03)_
2. WHILE la lista de clientes se está cargando, THE Frontend SHALL mostrar un indicador de carga. _(NF-06)_
3. WHEN la lista de clientes no tiene resultados, THE Frontend SHALL mostrar un mensaje que indique que no hay clientes para los criterios elegidos. _(NF-06)_
4. THE Frontend SHALL presentar un formulario de alta y edición de clientes con los campos obligatorios marcados, un selector de tipo de documento y un selector opcional de Cuenta_Corporativa activa. _(CRM-01, CRM-02, NF-06)_
5. IF el usuario envía el formulario con campos obligatorios vacíos, un correo con formato inválido o valores que superan los límites de los supuestos, THEN THE Frontend SHALL mostrar un mensaje junto a cada campo inválido sin enviar la solicitud. _(NF-06)_
6. WHILE el envío del formulario está en curso, THE Frontend SHALL deshabilitar el botón de envío y mostrar un indicador de carga. _(NF-06)_
7. WHEN el Backend responde 400 con errores por campo, THE Frontend SHALL mostrar cada mensaje junto a su campo y conservar los valores introducidos. _(NF-06, AUTH-05)_
8. IF el Backend responde 409 porque el documento pertenece a un Cliente inactivo, THEN THE Frontend SHALL mostrar el mensaje y un enlace al detalle de ese Cliente para reactivarlo. _(CRM-04)_
9. THE Frontend SHALL mostrar una vista de detalle del Cliente con sus datos, su estado y su Cuenta_Corporativa. _(CRM-01)_
10. WHILE el usuario tiene Rol_Gestor_CRM u `OPERACIONES`, THE Frontend SHALL mostrar en el detalle del Cliente la lista de sus contratos con código, estado, fechas y valor. _(CRM-03)_
11. WHEN un usuario con Rol_Gestor_CRM solicita desactivar o reactivar un Cliente, THE Frontend SHALL pedir confirmación, enviar `PATCH /api/crm/clientes/{id}` y mostrar el nuevo estado al recibir 200. _(CRM-01)_

### Requirement 21: Vistas de contratos (CRM-03, NF-06)

**User Story:** Como usuario Comercial, quiero registrar contratos desde el cliente y cambiar su estado con las opciones válidas, para seguir cada acuerdo sin errores de transición.

#### Acceptance Criteria

1. WHILE el Cliente está activo y el usuario tiene Rol_Gestor_CRM, THE Frontend SHALL ofrecer desde el detalle del Cliente un formulario de alta de Contrato con código, descripción, fecha de inicio, fecha de fin opcional y valor en COP. _(CRM-03)_
2. WHILE el Cliente está inactivo, THE Frontend SHALL ocultar el alta de contratos e indicar que un cliente inactivo no admite contratos nuevos. _(CRM-03)_
3. IF el usuario envía el formulario de Contrato con campos obligatorios vacíos, una fecha de fin anterior a la de inicio o un valor negativo o con más de 2 decimales, THEN THE Frontend SHALL mostrar un mensaje junto a cada campo inválido sin enviar la solicitud. _(NF-06)_
4. THE Frontend SHALL enviar el valor del Contrato como texto decimal, sin convertirlo a número de coma flotante. _(DP-API-04)_
5. THE Frontend SHALL mostrar el valor del Contrato con formato de pesos colombianos. _(CRM-03)_
6. WHEN el Backend responde 409 por código duplicado, THE Frontend SHALL mostrar el mensaje junto al campo código y conservar los valores introducidos. _(CRM-04)_
7. WHILE el usuario tiene Rol_Gestor_CRM, THE Frontend SHALL ofrecer para cada Contrato solo las Transicion_Permitida desde su estado actual. _(CRM-03, DP-API-05)_
8. WHEN el usuario elige una transición hacia un Estado_Final, THE Frontend SHALL pedir una confirmación que indique que el cambio es irreversible antes de enviar `PATCH /api/crm/contratos/{id}/estado`. _(CRM-03, NF-06)_
9. WHEN el Backend responde 409 a un cambio de estado, THE Frontend SHALL mostrar el mensaje y volver a cargar el Contrato. _(AUTH-05)_
10. WHILE el Contrato está en estado `BORRADOR` y el usuario tiene Rol_Gestor_CRM, THE Frontend SHALL ofrecer la edición del Contrato. _(CRM-03)_

### Requirement 22: Vistas de cuentas corporativas y resumen comercial (CRM-02, CRM-05, NF-06)

**User Story:** Como usuario Comercial, quiero pantallas para gestionar cuentas corporativas y un panel de resumen, para agrupar clientes y conocer la situación comercial.

#### Acceptance Criteria

1. WHILE el usuario tiene Rol_Gestor_CRM, THE Frontend SHALL mostrar una lista paginada de cuentas corporativas con búsqueda por razón social o NIT y filtro activa/inactiva. _(CRM-02, DP-API-03)_
2. WHILE el usuario tiene Rol_Gestor_CRM, THE Frontend SHALL presentar un formulario de alta y edición de cuentas con `nit` y `razonSocial` marcados como obligatorios y los datos de contacto opcionales. _(CRM-02, NF-06)_
3. IF el usuario envía el formulario de cuenta con campos obligatorios vacíos o con formatos inválidos, THEN THE Frontend SHALL mostrar un mensaje junto a cada campo inválido sin enviar la solicitud. _(NF-06)_
4. WHEN el Backend responde 400 o 409 al formulario de cuenta, THE Frontend SHALL mostrar los mensajes recibidos y conservar los valores introducidos. _(CRM-04, AUTH-05)_
5. THE Frontend SHALL mostrar una vista de detalle de la Cuenta_Corporativa con sus datos y los clientes asociados, obtenidos con `GET /api/crm/clientes?cuentaCorporativaId={id}`. _(CRM-02)_
6. WHEN un usuario con Rol_Gestor_CRM solicita activar o desactivar una Cuenta_Corporativa, THE Frontend SHALL pedir confirmación y enviar `PATCH /api/crm/cuentas-corporativas/{id}`. _(CRM-02)_
7. WHILE el usuario tiene Rol_Gestor_CRM, THE Frontend SHALL mostrar un panel con los conteos del Resumen_Comercial, con indicador de carga y mensaje de error si la consulta falla. _(CRM-05, NF-06)_

### Requirement 23: Pruebas unitarias y de seguridad del backend (ENG-02, NF-07, CA-03)

**User Story:** Como integrante del equipo, quiero pruebas automatizadas de las reglas y los permisos del CRM, para detectar regresiones antes de integrar cambios.

#### Acceptance Criteria

1. THE Suite_Backend SHALL incluir pruebas unitarias con Mockito de los servicios del CRM que cubran normalización, duplicados entre activos e inactivos, cliente inactivo, cuenta inactiva, edición solo en `BORRADOR` y registro del `sub` del Actor en la auditoría. _(ENG-02, NF-07)_
2. THE Suite_Backend SHALL verificar las 16 combinaciones de estado de origen y destino del Contrato y comprobar que solo se aceptan las cuatro Transicion_Permitida. _(NF-07, DP-API-05)_
3. THE Suite_Backend SHALL verificar sobre un conjunto variado de entradas que la normalización de documentos es idempotente y que las variantes con espacios, puntos, guiones y minúsculas producen el mismo Documento_Normalizado. _(NF-07, CRM-04)_
4. THE Suite_Backend SHALL incluir para cada endpoint del CRM pruebas MockMvc de: sin token (401), JWT sin perfil (403), perfil inactivo (403), rol no permitido (403) y rol permitido (éxito). _(CA-03, NF-07)_
5. THE Suite_Backend SHALL obtener las authorities de las pruebas de seguridad con `PerfilJwtAuthenticationConverter` a partir de perfiles persistidos en H2, con JWT simulados y sin tokens reales de Supabase. _(CA-03, DP-TEC-02)_
6. THE Suite_Backend SHALL verificar las lecturas cruzadas: `FINANZAS_RRHH` y `OPERACIONES` reciben la Vista_Reducida_Cliente sin correo, teléfono ni dirección; `OPERACIONES` lee contratos; `FINANZAS_RRHH` recibe 403 en contratos; ambos reciben 403 en escrituras, cuentas corporativas y resumen. _(CA-03, DP-PRO-01)_
7. THE Suite_Backend SHALL verificar las respuestas 400, 404 y 409 de cada regla de validación y conflicto del CRM. _(NF-07, CA-06)_
8. THE Suite_Backend SHALL sustituir los endpoints de prueba CRM de `AutorizacionModulosTests` (`GET` y `POST /api/crm/clientes`, `GET /api/crm/clientes/{id}` y `GET /api/crm/contratos/{id}`) por pruebas sobre los controladores reales, y conservar los endpoints de prueba de ERP y SGA. _(CA-03)_
9. WHEN se ejecuta `.\mvnw.cmd test` desde `backend/`, THE Suite_Backend SHALL pasar sin Docker, sin Supabase y sin el archivo `.env`. _(ENG-02, DP-TEC-02)_

### Requirement 24: Pruebas de integración con PostgreSQL (ENG-03, DP-TEC-04, DP-EST-05)

**User Story:** Como responsable de las pruebas de integración, quiero ejecutar el CRM contra un PostgreSQL efímero con las migraciones reales, para comprobar restricciones, consultas y transacciones sin tocar la base compartida.

#### Acceptance Criteria

1. THE Backend SHALL declarar en `backend/pom.xml` las dependencias de prueba `org.testcontainers:testcontainers-postgresql` y `org.springframework.boot:spring-boot-testcontainers` y el plugin `maven-failsafe-plugin`, sin fijar versiones gestionadas por el parent de Spring Boot 4.1.2. _(DP-TEC-04, `tech.md`)_
2. THE Suite_Integracion SHALL ubicar sus clases en `com.arise.corp.integration` con sufijo `IT`, ejecutarse con Failsafe en la fase `verify` y quedar excluida de Surefire. _(DP-EST-05)_
3. WHEN la Suite_Integracion arranca, THE Suite_Integracion SHALL iniciar un PostgreSQL efímero con Testcontainers, ejecutar el Script_Preparacion_IT y aplicar en orden de timestamp todas las migraciones de `supabase/migrations/`, sin copiarlas ni modificarlas. _(ENG-03, DP-TEC-03)_
4. THE Script_Preparacion_IT SHALL residir en los recursos de prueba del Backend y aplicarse solo al PostgreSQL efímero. _(ENG-03, DP-TEC-04)_
5. WHEN el contexto de Spring arranca contra el PostgreSQL efímero, THE Suite_Integracion SHALL verificar que `ddl-auto=validate` acepta las entidades del CRM. _(DP-TEC-03)_
6. THE Suite_Integracion SHALL verificar las restricciones de unicidad (documento de cliente, NIT, código de contrato), las restricciones `CHECK` (tipo de documento, estado, valor, fechas) y las claves foráneas del CRM. _(NF-03, ENG-03)_
7. THE Suite_Integracion SHALL verificar los filtros, la búsqueda, la paginación, el orden y los conteos del Resumen_Comercial contra PostgreSQL. _(ENG-03, CRM-05)_
8. THE Suite_Integracion SHALL verificar que el cambio de estado de un Contrato y su auditoría se guardan en la misma transacción y que un fallo no deja cambios parciales. _(DP-SEG-06, ENG-03)_
9. THE Suite_Integracion SHALL verificar que las tablas del CRM tienen RLS habilitado y que `anon` y `authenticated` carecen de privilegios sobre ellas. _(DP-SEG-05)_
10. THE Suite_Integracion SHALL ejecutarse sin credenciales de la Base_Compartida, sin el archivo `.env` y sin conexión a Supabase. _(ENG-03, NF-01)_
11. WHEN se ejecuta `.\mvnw.cmd verify` desde `backend/` con Docker disponible, THE Suite_Integracion SHALL pasar junto con la Suite_Backend. _(ENG-03)_

### Requirement 25: Pruebas del frontend (ENG-02, ENG-03)

**User Story:** Como responsable del frontend, quiero pruebas automatizadas de las vistas CRM y del cliente HTTP, para asegurar que la interfaz se comporta según el contrato de la API.

#### Acceptance Criteria

1. THE Suite_Frontend SHALL incluir pruebas unitarias del Cliente_HTTP para los métodos, el cuerpo JSON, los encabezados y las respuestas 400, 401, 403, 404, 409, 5xx y tiempo de espera agotado. _(ENG-02)_
2. THE Suite_Frontend SHALL incluir pruebas de los formularios de clientes, contratos y cuentas que cubran validación local, errores por campo del Backend, estado de carga y confirmaciones. _(ENG-02, NF-06)_
3. THE Suite_Frontend SHALL incluir pruebas de integración de la navegación por rol, la búsqueda y paginación de clientes, el detalle del Cliente con sus contratos, las transiciones ofrecidas y el modo consulta de los Rol_Lectura_Cruzada. _(ENG-03, DP-PRO-01)_
4. THE Suite_Frontend SHALL simular la API y Supabase Auth, sin llamadas de red reales. _(ENG-03)_
5. WHEN se ejecuta `npm test` desde `frontend/`, THE Suite_Frontend SHALL pasar, incluidas las pruebas existentes de `001-foundation`. _(ENG-02)_
6. WHEN se ejecutan `npm run build` y `npm run check:secrets` desde `frontend/`, THE Frontend SHALL completar ambos comandos sin errores. _(NF-01)_

### Requirement 26: Contenedorización del backend (ENG-01)

**User Story:** Como integrante del equipo, quiero construir el backend como una imagen Docker, para ejecutarlo de forma reproducible sin instalar Java en cada equipo.

#### Acceptance Criteria

1. THE Imagen_Backend SHALL construirse desde un `backend/Dockerfile` multi-stage con una etapa de compilación con JDK 21 y Maven Wrapper y una etapa de ejecución con JRE 21 que contiene solo el JAR de la aplicación. _(ENG-01)_
2. THE Imagen_Backend SHALL ejecutar el proceso Java con un usuario sin privilegios de administrador. _(ENG-01, `security.md`)_
3. THE Imagen_Backend SHALL exponer el puerto 8080. _(ENG-01)_
4. THE Imagen_Backend SHALL recibir las variables `SUPABASE_*` y `CORS_ALLOWED_ORIGINS` como variables de entorno en tiempo de ejecución. _(ENG-01, NF-01)_
5. THE Imagen_Backend SHALL contener cero archivos `.env`, credenciales y argumentos `ARG` o `ENV` con valores secretos. _(NF-01)_
6. THE Imagen_Backend SHALL construirse con un `backend/.dockerignore` que excluya del contexto `.env`, `.env.*`, `target/` y los archivos de configuración de IDE. _(NF-01)_
7. WHEN se ejecuta `docker build` sobre `backend/`, THE Imagen_Backend SHALL construirse sin credenciales, sin conexión a Supabase y sin ejecutar la Suite_Integracion. _(ENG-01, ENG-04)_

### Requirement 27: Ejecución local con Docker Compose (ENG-01, CA-01)

**User Story:** Como integrante del equipo, quiero arrancar el backend localmente con un solo comando, para probar el sistema sin configurar Maven ni Java.

#### Acceptance Criteria

1. THE Compose_Local SHALL definir un servicio `backend` construido desde `./backend` que publica el puerto 8080 en `localhost:8080`. _(ENG-01, CA-01)_
2. THE Compose_Local SHALL inyectar `./backend/.env` al servicio `backend` mediante `env_file` en tiempo de ejecución. _(ENG-01, NF-01)_
3. THE Compose_Local SHALL definir únicamente el servicio `backend`, con Supabase PostgreSQL como servicio externo. _(`architecture.md`)_
4. THE Compose_Local SHALL contener cero credenciales y valores secretos. _(NF-01)_
5. WHEN se ejecuta `docker compose up backend` con un `backend/.env` válido, THE Backend SHALL atender en `http://localhost:8080` y responder 401 a `GET /api/me` sin token. _(CA-01, CA-02)_

### Requirement 28: Integración continua con GitHub Actions (ENG-04)

**User Story:** Como responsable de CI, quiero workflows que validen backend, frontend, integración e imagen Docker en cada cambio, para que `main` solo reciba cambios verificados.

#### Acceptance Criteria

1. THE Pipeline_CI SHALL estar formado por los workflows `backend-ci.yml`, `integration-tests.yml` y `frontend-ci.yml` en `.github/workflows/`, ejecutados en `ubuntu-latest`. _(ENG-04)_
2. WHEN se hace push a cualquier rama, se abre o actualiza un Pull Request hacia `main` o se lanza una ejecución manual (`workflow_dispatch`), THE Pipeline_CI SHALL ejecutar los tres workflows. _(ENG-04)_
3. WHEN comienza una ejecución de un workflow para una rama con otra ejecución del mismo workflow en curso, THE Pipeline_CI SHALL cancelar la ejecución anterior. _(ENG-04)_
4. THE Pipeline_CI SHALL declarar `permissions: contents: read`. _(`security.md`)_
5. THE Pipeline_CI SHALL usar solo acciones oficiales del espacio `actions/*`. _(`security.md`)_
6. THE Pipeline_CI SHALL ejecutarse sin GitHub Secrets ni credenciales de la Base_Compartida. _(NF-01, ENG-03)_
7. THE workflow `backend-ci.yml` SHALL configurar Temurin 21 con caché de Maven, ejecutar `./mvnw test` desde `backend/` y después construir la Imagen_Backend con `docker build` sin publicarla ni iniciar sesión en registros. _(ENG-01, ENG-02, ENG-04)_
8. THE workflow `integration-tests.yml` SHALL ejecutar `./mvnw verify` desde `backend/` con el Docker del runner para Testcontainers. _(ENG-03, DP-TEC-04)_
9. THE workflow `frontend-ci.yml` SHALL configurar Node.js con la versión de `frontend/.nvmrc` y ejecutar desde `frontend/` `npm ci`, `npm test`, `npm run build` con variables `VITE_*` ficticias declaradas en el YAML y `npm run check:secrets`. _(ENG-02, NF-01)_
10. IF un paso de un workflow falla, THEN THE Pipeline_CI SHALL marcar como fallida la ejecución de ese workflow. _(ENG-04)_
11. THE Pipeline_CI SHALL limitarse a validación, pruebas y construcción de la imagen, sin despliegue ni publicación de artefactos en registros externos. _(ENG-04)_

### Requirement 29: Documentación, steering y tareas de cierre (CA-01, CA-07, NF-05)

**User Story:** Como integrante del equipo, quiero documentación y reglas actualizadas al cerrar la spec, para que cualquiera pueda ejecutar, probar y revisar el CRM.

#### Acceptance Criteria

1. THE Equipo SHALL documentar en `design.md` el contrato de cada endpoint del CRM con requisitos, roles, solicitud, respuesta y errores. _(DP-API-06)_
2. THE Equipo SHALL actualizar el README §9 con los comandos de construcción y ejecución con Docker y la inyección de `backend/.env` en tiempo de ejecución. _(CA-01, ENG-01)_
3. THE Equipo SHALL actualizar el README §10 con los workflows, sus eventos de ejecución y sus comandos. _(ENG-04)_
4. THE Equipo SHALL documentar en el README la diferencia entre `.\mvnw.cmd test` (sin Docker) y `.\mvnw.cmd verify` (requiere Docker). _(CA-01, ENG-03)_
5. THE Equipo SHALL actualizar los steering `tech.md`, `structure.md`, `product.md` y `api-conventions.md` para reflejar como implementados el CRM, sus endpoints, Testcontainers, Failsafe, Docker y CI. _(CA-07, `product.md`)_
6. THE Equipo SHALL registrar las Migracion_CRM en la tabla de `supabase/migrations/README.md`. _(NF-05)_
7. WHERE la spec introduce variables de entorno nuevas, THE Equipo SHALL añadirlas a `.env.example` con valores ficticios y documentarlas en el README. _(NF-01, CA-01)_
8. THE Equipo SHALL confirmar el trabajo en la rama `feat/crm-clientes-contratos` y abrir un único Pull Request hacia `main` al completar la spec, con commits Conventional Commits. _(DP-SDD-04, DP-SDD-05)_
9. THE Equipo SHALL configurar en la protección de `main` los tres workflows del Pipeline_CI como verificaciones obligatorias. _(DP-SDD-05)_
10. WHEN un integrante distinto del autor ha revisado las Migracion_CRM, THE Equipo SHALL aplicarlas de forma controlada a la Base_Compartida antes de la validación manual del Requirement 30 y del Pull Request final, y SHALL abstenerse de editarlas después de aplicadas. _(DP-TEC-03, aclaración registrada en `design.md`)_

### Requirement 30: Validación extremo a extremo del CRM (CA-04, CA-05, criterio de aceptación del CRM)

**User Story:** Como equipo, quiero demostrar el flujo del CRM con Supabase real, para cumplir el criterio de aceptación del módulo y preparar el flujo cliente → contrato → paquete.

#### Acceptance Criteria

1. WHEN las Migracion_CRM están aplicadas a la Base_Compartida, THE Equipo SHALL validar manualmente, con el Backend ejecutado mediante Compose_Local y el Frontend en local, que un usuario Comercial registra un Cliente, lo encuentra en la lista, edita sus datos, vincula un Contrato y ve los datos persistidos después de recargar la aplicación. _(CA-04, CA-05, CRM-01, CRM-03)_
2. WHEN un usuario `FINANZAS_RRHH` u `OPERACIONES` intenta una escritura del CRM durante la validación manual, THE Backend SHALL responder 403 sin modificar datos. _(CA-03, NF-02)_
3. WHEN el Backend responde 403 a una escritura durante la validación manual, THE Frontend SHALL mostrar el mensaje de falta de permisos sin cerrar la sesión. _(AUTH-05)_
4. THE Equipo SHALL registrar la evidencia de la validación manual en `tasks.md` sin credenciales, tokens, UUID personales ni datos personales reales. _(NF-01, CA-07)_
