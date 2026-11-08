# Convenciones de la API REST

Etiquetas de estado: ver `product.md`. La protección de endpoints y los roles están en `security.md`.

## Endpoints existentes [Implementado]
| Método | Ruta | Autenticación | Respuesta 200 |
|---|---|---|---|
| GET | `/api/me` | JWT obligatorio | `{ "id": "<sub>", "email": "<email>", "role": "<role>" }` |

- Sin token, o con un token inválido o vencido, el backend responderá 401 Problem Details con el encabezado `WWW-Authenticate` (DP-API-02); la configuración actual aún está pendiente de alineación.
- En el código actual el campo `role` refleja el claim técnico `role` de Supabase. La tarea 16 de `001-foundation` lo cambiará al rol empresarial del perfil activo; el claim técnico no autoriza operaciones.
- No existen endpoints públicos. La ruta `/api/public/**` está reservada para ellos en `SecurityConfig`.
- No existe manejo global de errores.

## Rutas
- [Implementado] Todas las rutas cuelgan de `/api`. Las rutas sin autenticación van exclusivamente bajo `/api/public/**`.
- Estructura y versionado [DP-API-01]:
  - `/api/{modulo}/{recurso}`, con `modulo` ∈ `crm`, `erp`, `sga`. Ejemplos: `/api/crm/clientes`, `/api/crm/clientes/{id}`, `/api/sga/paquetes/{id}/estados`.
  - El prefijo de módulo permite aplicar la autorización por módulo (ver `security.md`).
  - `/api/me` se mantiene tal como está.
- Reglas generales para cualquier opción:
  - Recursos como sustantivos en plural, en minúsculas y con kebab-case (`cuentas-corporativas`). El idioma depende de DP-EST-02.
  - Sin verbos en la ruta, salvo acciones de estado definidas en DP-API-05.
  - Subrecursos para relaciones de pertenencia: `/api/crm/clientes/{id}/contratos`.
  - Filtros, búsqueda, orden y paginación como parámetros de consulta, nunca en la ruta.

## Métodos HTTP
| Método | Uso | Respuesta de éxito |
|---|---|---|
| GET | Consultar un recurso o una lista. Nunca modifica datos | 200 |
| POST | Crear un recurso o registrar una operación (p. ej., un cambio de estado) | 201 con el recurso creado y el encabezado `Location` |
| PUT | Actualizar todos los campos editables de un recurso | 200 con el recurso actualizado |
| PATCH | Actualización parcial | 200 con el recurso actualizado |
| DELETE | Eliminar, solo si la especificación lo permite | 204 sin cuerpo |

- CRM-01 pide desactivar clientes, no eliminarlos: la desactivación es lógica.
- Los estados de contratos y paquetes, y las cantidades de inventario, no se modifican con un PUT genérico. Cambian mediante operaciones explícitas que validan la transición y, para los paquetes, registran el historial en la misma transacción (SGA-03, SGA-04). Las operaciones siguen DP-API-05.

## Códigos de respuesta
| Código | Cuándo |
|---|---|
| 200 OK | Consulta o actualización correcta |
| 201 Created | Recurso u operación creada |
| 204 No Content | Operación correcta sin cuerpo de respuesta |
| 400 Bad Request | JSON mal formado, validación de Bean Validation fallida o parámetros inválidos |
| 401 Unauthorized | Sin token, o token inválido, vencido o con emisor o audiencia incorrectos |
| 403 Forbidden | Usuario autenticado sin permiso para el módulo o la operación |
| 404 Not Found | El recurso no existe |
| 409 Conflict | Conflicto con el estado actual: duplicado (CRM-04), transición de estado no permitida, inventario insuficiente, violación de una restricción de la base |
| 500 Internal Server Error | Error inesperado; se responde con un mensaje genérico |

- No responder 200 con un indicador de error en el cuerpo.
- Las excepciones no controladas no deben mostrar trazas, SQL, nombres de clases ni datos internos (CA-06).

## Formato JSON
- `Content-Type: application/json` en UTF-8 para solicitudes y respuestas (salvo las respuestas Problem Details de DP-API-02).
- Nombres de campos en camelCase, la convención por defecto de Jackson y de JavaScript; `/api/me` ya la cumple.
- Las solicitudes y respuestas usan DTO, nunca entidades JPA (justificación en `architecture.md`).
- Los DTO de entrada solo incluyen los campos que el cliente puede fijar. El identificador, las fechas de creación y modificación, el estado actual, los totales calculados y el usuario que realiza la operación los asigna el servidor.
- Las fechas y horas se envían como texto ISO-8601. Se usan UUID para identificadores, `date`/`LocalDate` para fechas de calendario e instantes ISO-8601 UTC (`timestamptz`, `Instant`) para marcas temporales; importes COP como `numeric`/`BigDecimal` y texto decimal JSON para no perder precisión en JavaScript; enumeraciones `UPPER_SNAKE_CASE` en JSON y texto en PostgreSQL con `CHECK` y `EnumType.STRING` (DP-API-04).
- Las listas usan `page` desde 0 (por defecto 0), `size` por defecto 20 y máximo 100, y `sort=campo,asc|desc`. La respuesta usa `{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }`; no se serializa directamente `Page` de Spring Data (DP-API-03).

## Validaciones
- Los DTO de entrada se validan con Bean Validation: `@Valid` en el `@RequestBody` y anotaciones como `@NotNull`, `@NotBlank`, `@Size`, `@Email` o `@Positive` en los campos. Los parámetros de ruta y de consulta se validan con `@Validated` en el controlador.
- Las reglas que dependen de datos (duplicados, existencia y estado de los recursos referenciados, transiciones válidas, existencias de inventario) se comprueban en el servicio y producen 404 o 409.
- No confiar en identificadores enviados por el cliente: comprobar que el recurso referenciado existe y está activo antes de usarlo (por ejemplo, el cliente al que se asocia un paquete).
- Las restricciones de PostgreSQL son la última defensa (NF-03). Una `DataIntegrityViolationException` se traduce a 409 sin exponer el mensaje de la base.
- El frontend valida para mejorar la usabilidad (NF-06), pero la validación que vale es la del backend.
- Los mensajes de validación deben ser comprensibles para el usuario (AUTH-05, NF-06). Su idioma depende de DP-EST-02.

## Manejo uniforme de errores
- [Aprobado] Todas las respuestas de error tienen el mismo formato, con un mensaje comprensible y sin información sensible (CA-06, AUTH-05). La traducción de excepciones a respuestas se centraliza en un único manejador global (`@RestControllerAdvice`), no en cada controlador.
- Los 401 y 403 de Spring Security usan el mismo formato mediante un `AuthenticationEntryPoint` y un `AccessDeniedHandler` propios, que conservan el encabezado `WWW-Authenticate`.
- Formato adoptado DP-API-02: RFC 9457 Problem Details (`application/problem+json`), soportado de forma nativa por Spring con `ProblemDetail`, `ErrorResponseException` y `ResponseEntityExceptionHandler`. Ejemplo:

```json
{
  "type": "about:blank",
  "title": "Solicitud inválida",
  "status": 400,
  "detail": "La solicitud contiene campos inválidos.",
  "instance": "/api/crm/clientes",
  "errors": [
    { "field": "documento", "message": "es obligatorio" }
  ]
}
```

## Versionamiento
- [Implementado] Las rutas no tienen versión (`/api/me`).
- Decisión: DP-API-01.

## Documentación de la API
- [Aprobado, README §13] El `design.md` de cada especificación documenta el contrato de cada endpoint antes de implementarlo. El contrato aprobado es la referencia común para el frontend y el backend. Plantilla:

```markdown
### POST /api/crm/clientes
- Requisitos: CRM-01, CRM-04
- Roles permitidos: según la matriz aprobada (DP-PRO-01)
- Solicitud: campos, tipos, obligatoriedad y validaciones
- Respuesta 201: campos devueltos
- Errores: 400 (validación), 401, 403, 409 (identificador de cliente duplicado)
```

  Los nombres del ejemplo siguen el vocabulario español del MVP.
- Si el código y el contrato difieren, se corrige uno de los dos en el mismo Pull Request (ver `sdd-workflow.md`).
- Los contratos mantenidos en los `design.md` son la documentación de API del MVP (DP-API-06).

## Decisiones adoptadas
- **DP-API-01:** rutas `/api/{modulo}/{recurso}` y sin prefijo de versión durante el MVP; `/api/me` se conserva.
- **DP-API-02:** todos los errores de API usan RFC 9457 Problem Details (`application/problem+json`); errores de campo usan una extensión `errors` con `field` y `message`. 401/403 también usan el formato común y conservan `WWW-Authenticate` en 401.
- **DP-API-03:** listas aceptan `page` desde 0 (por defecto 0), `size` por defecto 20 y máximo 100, `sort=campo,asc|desc`; responden `content`, `page`, `size`, `totalElements`, `totalPages`. Cada especificación lista los filtros permitidos; nunca se acepta ordenar por campos no permitidos.
- **DP-API-04:** identificadores UUID; fechas calendario ISO-8601 con `LocalDate`; instantes ISO-8601 en UTC con `Instant`/`timestamptz`; dinero COP con `BigDecimal`/`numeric` serializado como texto decimal JSON y moneda explícita si el recurso admite más de una; enumeraciones `UPPER_SNAKE_CASE` serializadas y persistidas como texto con restricción `CHECK`.
- **DP-API-05:** desactivar cliente con `PATCH /api/crm/clientes/{id}` y `activo: false`; cambio de contrato mediante operación explícita `PATCH /api/crm/contratos/{id}/estado`; transición de paquete mediante `POST /api/sga/paquetes/{id}/estados` y consulta por `GET` a la misma ruta; inventario solo mediante `POST .../movimientos`. Las operaciones validan transiciones y registran auditoría en la misma transacción.
- **DP-API-06:** no se añade springdoc al MVP. Cada `design.md` mantiene el contrato aprobado y la API se documenta allí; reevaluar OpenAPI si se incorporan más clientes o se requiere publicar documentación navegable.
