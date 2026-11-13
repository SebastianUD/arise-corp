# Seguridad

Etiquetas de estado: ver `product.md`. Los códigos HTTP y el formato de errores están en `api-conventions.md`.

## Principios [Aprobado]
- **NF-01** Secretos, credenciales y claves de servicio nunca en el código del frontend ni en el repositorio.
- **NF-02** Los permisos se verifican en Spring Boot. Ocultar botones o rutas en React no protege nada.
- **CA-02** El backend rechaza tokens inválidos o vencidos.
- **CA-03** Los permisos por rol se aplican en el backend y tienen pruebas de acceso permitido y denegado.
- **CA-06** Los errores no exponen información sensible.
- El frontend no accede a las tablas de negocio; todos los datos pasan por la API de Spring Boot.

## Autenticación y autorización
- **Autenticación**: comprobar quién es el usuario. Supabase Auth inicia la sesión y emite el JWT; Spring Security verifica ese JWT en cada petición. Estado: [Implementado] en backend y frontend; flujo validado manualmente con Supabase real en tarea 20 de `001-foundation`.
- **Autorización**: decidir qué puede hacer un usuario ya autenticado según el rol empresarial activo de su perfil. La aplica Spring Boot conforme a la matriz en `product.md`. Estado: [Implementado] en `SecurityConfig` con un converter que deriva `ROLE_<ROL>` del perfil activo (tarea 17).

## Flujo de autenticación
1. React inicia sesión contra Supabase Auth con `@supabase/supabase-js` (AUTH-01).
2. Supabase Auth devuelve un access token JWT firmado con ES256 y un refresh token. `supabase-js` mantiene y renueva la sesión (AUTH-02).
3. React envía `Authorization: Bearer <access_token>` en cada llamada a la API (AUTH-03).
4. Spring Security valida el token, resuelve el perfil activo y aplica la autorización por rol (AUTH-04).
5. Al cerrar sesión, React llama a `signOut`. El backend no guarda sesión ni revoca tokens: un access token ya emitido sigue siendo válido hasta su `exp` (en Supabase, una hora por defecto; es configurable).

## Validación de JWT [Implementado]
Configuración en `application.properties`:

| Propiedad (`spring.security.oauth2.resourceserver.jwt.*`) | Valor | Efecto |
|---|---|---|
| `issuer-uri` | `${SUPABASE_URL}/auth/v1` | Valida el claim `iss` |
| `jwk-set-uri` | `${SUPABASE_URL}/auth/v1/.well-known/jwks.json` | Descarga las claves públicas para verificar la firma |
| `jws-algorithms` | `ES256` | Solo acepta tokens firmados con ES256 |
| `audiences[0]` | `authenticated` | Valida el claim `aud` |

Spring Security valida además `exp` y `nbf` por defecto, con una tolerancia de 60 segundos.

Reglas:
- Verificar la firma solo con las claves públicas del JWKS. No usar el JWT secret compartido (HS256) de Supabase ni copiarlo al backend. No aceptar algoritmos distintos de ES256 sin aprobación.
- No decodificar el payload a mano para tomar decisiones; usar el `Jwt` ya validado (`@AuthenticationPrincipal Jwt`).
- El identificador del usuario es `sub` (UUID de Supabase Auth). `email` es solo informativo.
- El claim `role` de Supabase es el rol de PostgreSQL (`authenticated`, `anon`, `service_role`), no el rol empresarial. No usarlo para autorizar operaciones de negocio.
- Nunca autorizar con `user_metadata`: el propio usuario puede modificarlo desde el cliente. `app_metadata` solo se puede modificar con privilegios de servidor.
- La conversión por defecto de Spring toma las authorities de los claims `scope` o `scp`, que los tokens de Supabase no incluyen. Por eso el backend registra `PerfilJwtAuthenticationConverter` como converter del resource server: consulta `app.perfiles_usuario` y asigna `ROLE_<ROL>` desde el perfil activo. Un JWT válido sin perfil activo queda autenticado pero sin authorities, de modo que las rutas con rol responden 403 y `/api/me` rechaza en el servicio.
- La rotación de claves de firma en Supabase no requiere cambios de código mientras la validación use el JWKS.

Referencia: [JWT Claims Reference de Supabase](https://supabase.com/docs/guides/auth/jwt-fields).

## Roles y permisos empresariales
- Roles y su alcance por módulo: `product.md`.
- Almacenamiento y mecanismo: decisiones DP-SEG-01 y DP-SEG-02 al final de este documento.
- Implementación [Implementado]:
  - `PerfilJwtAuthenticationConverter` convierte el `Jwt` validado en `JwtAuthenticationToken` con una authority `ROLE_<ROL>` tomada del perfil activo. No usa el claim técnico `role` de Supabase.
  - `SecurityConfig` aplica la matriz con `hasRole`/`hasAnyRole` por módulo y método; `anyRequest().authenticated()` mantiene la denegación por defecto para lo no declarado.
  - Lecturas cruzadas mínimas (ERP-05, SGA-05): Finanzas/RRHH y Operaciones pueden `GET` clientes del CRM; Operaciones también `GET` contratos. Las escrituras de CRM siguen restringidas a Comercial y Administrador.
  - Vista reducida implementada: Administrador y Comercial reciben la vista completa del cliente; Finanzas/RRHH y Operaciones reciben `ClienteResumenResponse`, sin `correo`, `telefono`, `direccion` ni auditoría. La elección se resuelve en el controlador de clientes a partir de las authorities.
  - Solo el Administrador accede a `/api/admin/**`, que expone la gestión de perfiles.
- Reglas para cualquier opción:
  - Denegar por defecto: un usuario sin perfil, sin rol o con el perfil desactivado recibe 403, aunque su JWT sea válido.
  - El rol se obtiene de una fuente que el usuario no puede modificar.
  - Solo el Administrador asigna o cambia roles (MVP §5).
  - Cada endpoint declara en el `design.md` de su especificación qué roles pueden usarlo.

## Administración de perfiles
[Implementado] Rutas exclusivas del Administrador:
- `PUT /api/admin/usuarios/{id}/perfil`: crea (201 con `Location`) o actualiza el rol (200) de un perfil. El cuerpo solo admite `rol`; `activo` y los campos de auditoría los controla el servidor. El `id` debe existir en `auth.users` para crear; si no existe, responde 404.
- `PATCH /api/admin/usuarios/{id}/estado`: activa o desactiva un perfil existente (200). Perfil inexistente → 404.
- Los cambios registran como `actualizadoPor` el `sub` del Administrador autenticado. Un rol distinto de Administrador, un perfil inactivo o un perfil ausente responden 403 sin ejecutar la operación.
- La creación en `auth.users` y la lectura de esa tabla por el rol de base del backend se validarán contra Supabase real en la tarea 20.

## Protección de endpoints
`SecurityConfig` [Implementado]:
- CSRF deshabilitado.
- CORS con el bean `corsConfigurationSource`.
- Sesiones `STATELESS`.
- `/api/public/**` con `permitAll()`.
- `/api/admin/**` solo Administrador.
- Matriz por módulo: CRM (Administrador, Comercial), ERP (Administrador, Finanzas/RRHH), SGA (Administrador, Operaciones), con las lecturas cruzadas indicadas arriba.
- `anyRequest().authenticated()` como denegación por defecto.
- Resource server JWT con `PerfilJwtAuthenticationConverter`.

Reglas:
- Todo endpoint nuevo queda protegido por defecto. Solo se agrega una ruta bajo `/api/public/**` si su especificación lo aprueba, y nunca para devolver datos de negocio.
- No ampliar los patrones de `permitAll()` (por ejemplo, a `/**` o `/api/**`).
- Deshabilitar CSRF es correcto solo porque la autenticación usa el encabezado `Authorization` y no cookies. Si alguna vez se usan cookies de sesión, hay que volver a evaluar CSRF.
- No usar `HttpSession` ni guardar estado de sesión en el servidor.
- La autorización se comprueba en el backend aunque el frontend oculte la opción.
- Pruebas obligatorias por endpoint protegido (CA-03, NF-07). En backend se usa MockMvc; el JWT simulado se convierte con `PerfilJwtAuthenticationConverter` a partir de un perfil persistido en H2, de modo que se prueba el mismo camino de autorización que en producción:
  - sin token → 401;
  - token válido sin perfil activo, o con perfil inactivo, o con rol distinto del requerido → 403;
  - token con perfil activo y rol requerido → respuesta de éxito.
  Estas pruebas no usan tokens reales de Supabase. `SecurityMockMvcRequestPostProcessors.jwt()` sigue siendo válido para pruebas que solo necesiten claims técnicos, pero no reemplaza la prueba del rol empresarial.

## CORS [Implementado]
| Parámetro | Valor actual |
|---|---|
| Orígenes permitidos | `http://localhost:5173` |
| Métodos | GET, POST, PUT, PATCH, DELETE, OPTIONS |
| Encabezados | `Authorization`, `Content-Type` |
| Credenciales | `false` |
| Rutas | `/**` |

Reglas:
- Nunca usar `*` como origen ni `allowedOriginPatterns("*")`.
- Mantener `allowCredentials(false)`: el token viaja en un encabezado, no en cookies.
- Agregar encabezados o métodos solo si una especificación los necesita.
- El origen local se configura por `CORS_ALLOWED_ORIGINS`, con valor por defecto `http://localhost:5173`. No hay origen de producción dentro del alcance del proyecto.

## Variables de entorno y secretos [Aprobado, README §12]
- `backend/.env` es local y personal de cada integrante. Nunca se versiona; `.gitignore` excluye `.env` y `.env.*`, salvo `.env.example`.
- `.env.example` contiene solo marcadores o valores ficticios.
- No escribir credenciales en `application.properties`, código Java, pruebas, scripts, migraciones, documentación, especificaciones, issues, Pull Requests, capturas de pantalla ni mensajes a Kiro.
- Kiro y cualquier asistente: no abrir, leer, mostrar ni resumir `backend/.env` ni ningún otro `.env`. Para saber qué variables existen, leer `.env.example`.
- Secretos que nunca deben exponerse: contraseña de PostgreSQL, claves `service_role` o `secret` de Supabase, el JWT secret y tokens de acceso.
- Frontend: todas las variables `VITE_*` terminan en el bundle del navegador. Solo pueden contener valores públicos, como la URL del proyecto Supabase, la clave publishable y la URL de la API.
- La clave publishable es pública por diseño. La protección de los datos depende de los permisos de la base (DP-SEG-05), no de ocultar la clave.
- No registrar en logs tokens, el encabezado `Authorization`, contraseñas ni valores del `.env`.
- Si un secreto se expone (commit, captura, mensaje), se rota de inmediato en Supabase. Borrar el commit no es suficiente.
- **Docker:** `.env` y `.env.*` deben estar excluidos en `backend/.dockerignore`; nunca copiar secretos a la imagen ni pasarlos como `ARG` de construcción. La configuración sensible se inyecta al contenedor en tiempo de ejecución.
- **GitHub Actions:** si algún workflow de CI necesitara un secreto, se guarda en GitHub Actions Secrets; nunca en archivos YAML, variables de repositorio sin protección, logs o artefactos. Conceder al `GITHUB_TOKEN` solo los permisos mínimos necesarios. El proyecto se ejecuta en local: no hay despliegue continuo (CD) y, por tanto, no se gestionan secretos de despliegue.
- Los workflows de pruebas no deben necesitar la contraseña de la base compartida `arise-corp-dev`; las pruebas PostgreSQL usarán una instancia aislada/efímera.
- No exponer secretos a workflows que se ejecuten sobre código no confiable de contribuciones externas. La estrategia exacta de permisos se define en `tech.md` y en la configuración de GitHub.
- La imagen Docker del backend no debe contener `.env`, tokens, claves Supabase ni archivos de configuración local.

## Base de datos
- [Implementado] Spring Boot se conecta con las credenciales del Session pooler y `sslmode=require`.
- Las tablas de negocio se alojan en el esquema privado `app`, que no se expone en la Data API. Las migraciones habilitan RLS y no conceden acceso a `anon` ni `authenticated`; el rol de conexión del backend conserva los privilegios necesarios.
- Verificar la lista de esquemas expuestos en la configuración Supabase y los grants efectivos al aplicar cada migración. La clave publishable nunca otorga acceso directo al negocio.

Referencia: [Securing your API, documentación de Supabase](https://supabase.com/docs/guides/database/data-api).

## Reglas para CRM, ERP y SGA
- Todo endpoint de CRM, ERP o SGA requiere autenticación y autorización por rol. No cumple la Definition of Done sin sus pruebas de acceso permitido y denegado (CA-03).
- Los campos controlados por el servidor (identificador, fechas, estado actual, totales, usuario que opera) nunca se toman del cuerpo de la solicitud.
- Antes de usar un identificador recibido, comprobar que el recurso existe y que el usuario puede usarlo (por ejemplo, el cliente o contrato asociado a un paquete).
- Estados de contratos y paquetes, y cantidades de inventario: solo cambian mediante las operaciones definidas en la especificación, que validan la transición (SGA-03, SGA-04).
- Datos personales de clientes y empleados: devolver solo los campos que cada rol necesita y no escribirlos en logs. En el CRM, los DTO no llevan datos personales a los mensajes de log y `CrmLogsTests` verifica que crear, editar y provocar 409 o 500 no escribe nombre, documento, correo ni teléfono en la salida.
- Los errores no revelan si un recurso existe para usuarios sin permiso sobre el módulo, ni muestran trazas, SQL ni datos internos (CA-06).
- Auditoría de cambios: DP-SEG-06.

## Frontend
- Las rutas protegidas de React (AUTH-02) son solo experiencia de usuario; la protección real está en el backend.
- `supabase-js` gestiona el token. No copiarlo a otros almacenamientos, URLs ni logs.
- No renderizar HTML no confiable (`dangerouslySetInnerHTML`).
- Mostrar mensajes claros para credenciales incorrectas, sesión expirada (401, volver al inicio de sesión) y falta de permisos (403) (AUTH-05).

## Decisiones adoptadas
- **DP-SEG-01:** el rol se almacena en `app.perfiles_usuario`, con `usuario_id` igual al `sub` UUID de Supabase, un único rol y estado activo. El backend consulta el perfil en cada petición protegida para aplicar inmediatamente cambios y denegar perfiles inexistentes o inactivos. Un endpoint limitado al Administrador gestiona asignaciones y cambios de rol. No se usa `user_metadata`, `app_metadata` ni una clave `service_role`.
- **DP-SEG-02:** se usan reglas de autorización por módulo en `SecurityFilterChain` y controles de método/servicio para operaciones y lecturas cruzadas que requieren permisos finos. Las rutas son `/api/{modulo}/...` sin versión durante el MVP.
- **DP-SEG-04:** CORS recibe `CORS_ALLOWED_ORIGINS` como propiedad configurable; el valor local por defecto es `http://localhost:5173`. Se enumeran orígenes, sin comodines.
- **DP-SEG-05:** se usan conjuntamente el esquema privado `app` y RLS habilitado sin políticas para roles de cliente, además de revocar grants de `anon`/`authenticated`. La Data API permanece disponible para Auth; no se expone `app`. Se verifica que el backend tenga los privilegios necesarios.
- **DP-SEG-06:** los cambios de estados de paquetes/contratos, inventario y registros financieros guardan `actor_sub` (UUID del JWT validado) y marca temporal en la misma transacción que la operación. El historial de paquetes registra cada transición. No se construye un sistema de auditoría distribuido.
