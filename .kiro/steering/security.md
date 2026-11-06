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
- **Autenticación**: comprobar quién es el usuario. Supabase Auth inicia la sesión y emite el JWT; Spring Security verifica ese JWT en cada petición. Estado: [Implementado] en el backend; inicio de sesión en React [Aprobado], pendiente.
- **Autorización**: decidir qué puede hacer un usuario ya autenticado según su rol empresarial (Administrador, Comercial, Finanzas / RR. HH., Operaciones). La aplica Spring Boot. Estado: pendiente (AUTH-04, DP-SEG-01, DP-SEG-02).
- Situación actual: cualquier usuario con un JWT válido del proyecto puede llamar a cualquier endpoint protegido, porque todavía no existe autorización por rol.

## Flujo de autenticación
1. React inicia sesión contra Supabase Auth con `@supabase/supabase-js` (AUTH-01).
2. Supabase Auth devuelve un access token JWT firmado con ES256 y un refresh token. `supabase-js` mantiene y renueva la sesión (AUTH-02).
3. React envía `Authorization: Bearer <access_token>` en cada llamada a la API (AUTH-03).
4. Spring Security valida el token y, cuando exista, aplica la autorización por rol (AUTH-04).
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
- La conversión por defecto de Spring toma las authorities de los claims `scope` o `scp`, que los tokens de Supabase no incluyen. Por eso hoy no hay roles empresariales en el contexto de seguridad.
- La rotación de claves de firma en Supabase no requiere cambios de código mientras la validación use el JWKS.

Referencia: [JWT Claims Reference de Supabase](https://supabase.com/docs/guides/auth/jwt-fields).

## Roles y permisos empresariales
- Roles propuestos y su alcance por módulo: `product.md` (DP-PRO-01).
- Almacenamiento de roles: DP-SEG-01. Mecanismo de autorización en Spring: DP-SEG-02.
- Reglas para cualquier opción:
  - Denegar por defecto: un usuario sin perfil, sin rol o con el perfil desactivado recibe 403, aunque su JWT sea válido.
  - El rol se obtiene de una fuente que el usuario no puede modificar.
  - Solo el Administrador asigna o cambia roles (MVP §5).
  - Cada endpoint declara en el `design.md` de su especificación qué roles pueden usarlo.

## Protección de endpoints
`SecurityConfig` [Implementado]:
- CSRF deshabilitado.
- CORS con el bean `corsConfigurationSource`.
- Sesiones `STATELESS`.
- `/api/public/**` con `permitAll()`; cualquier otra ruta requiere autenticación.
- Resource server JWT con la configuración por defecto.

Reglas:
- Todo endpoint nuevo queda protegido por defecto. Solo se agrega una ruta bajo `/api/public/**` si su especificación lo aprueba, y nunca para devolver datos de negocio.
- No ampliar los patrones de `permitAll()` (por ejemplo, a `/**` o `/api/**`).
- Deshabilitar CSRF es correcto solo porque la autenticación usa el encabezado `Authorization` y no cookies. Si alguna vez se usan cookies de sesión, hay que volver a evaluar CSRF.
- No usar `HttpSession` ni guardar estado de sesión en el servidor.
- La autorización se comprueba en el backend aunque el frontend oculte la opción.
- Pruebas obligatorias por endpoint protegido (CA-03, NF-07), con MockMvc y `SecurityMockMvcRequestPostProcessors.jwt()` de `spring-security-test` para simular tokens con los claims o authorities necesarios:
  - sin token → 401;
  - token válido sin el rol requerido → 403;
  - token con el rol requerido → respuesta de éxito.
  Estas pruebas no usan tokens reales de Supabase.

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
- Los orígenes están escritos en el código de `SecurityConfig`. Orígenes de producción y su paso a configuración: DP-SEG-04.

## Variables de entorno y secretos [Aprobado, README §7]
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
- **GitHub Actions:** secretos de despliegue, si llegan a ser necesarios, se guardan en GitHub Actions Secrets o Environments; nunca en archivos YAML, variables de repositorio sin protección, logs o artefactos. Conceder al `GITHUB_TOKEN` solo los permisos mínimos necesarios.
- Los workflows de pruebas no deben necesitar la contraseña de la base compartida `arise-corp-dev`; las pruebas PostgreSQL usarán una instancia aislada/efímera.
- No exponer secretos a workflows que se ejecuten sobre código no confiable de contribuciones externas. La estrategia exacta de permisos y ambientes se define en `tech.md` y en la configuración de GitHub.
- La imagen Docker publicada no debe contener `.env`, tokens, claves Supabase ni archivos de configuración local.

## Base de datos
- [Implementado] Spring Boot se conecta con las credenciales del Session pooler y `sslmode=require`.
- Riesgo: una tabla en un esquema expuesto por la Data API de Supabase (por defecto, `public`) puede ser accesible con la clave publishable si tiene grants para `anon` o `authenticated` y no tiene RLS. Eso permitiría saltarse Spring Boot y contradice la arquitectura aprobada.
- Supabase está cambiando el comportamiento por defecto para que la exposición de tablas nuevas sea opcional. Comprobar la configuración del proyecto ("Automatically expose new tables" en los ajustes de la Data API) en lugar de suponerla.
- Medidas concretas: DP-SEG-05.

Referencia: [Securing your API, documentación de Supabase](https://supabase.com/docs/guides/database/data-api).

## Reglas para CRM, ERP y SGA
- Todo endpoint de CRM, ERP o SGA requiere autenticación y autorización por rol. No cumple la Definition of Done sin sus pruebas de acceso permitido y denegado (CA-03).
- Los campos controlados por el servidor (identificador, fechas, estado actual, totales, usuario que opera) nunca se toman del cuerpo de la solicitud.
- Antes de usar un identificador recibido, comprobar que el recurso existe y que el usuario puede usarlo (por ejemplo, el cliente o contrato asociado a un paquete).
- Estados de contratos y paquetes, y cantidades de inventario: solo cambian mediante las operaciones definidas en la especificación, que validan la transición (SGA-03, SGA-04).
- Datos personales de clientes y empleados: devolver solo los campos que cada rol necesita y no escribirlos en logs.
- Los errores no revelan si un recurso existe para usuarios sin permiso sobre el módulo, ni muestran trazas, SQL ni datos internos (CA-06).
- Auditoría de cambios: DP-SEG-06.

## Frontend
- Las rutas protegidas de React (AUTH-02) son solo experiencia de usuario; la protección real está en el backend.
- `supabase-js` gestiona el token. No copiarlo a otros almacenamientos, URLs ni logs.
- No renderizar HTML no confiable (`dangerouslySetInnerHTML`).
- Mostrar mensajes claros para credenciales incorrectas, sesión expirada (401, volver al inicio de sesión) y falta de permisos (403) (AUTH-05).

## Decisiones pendientes de aprobación
- **DP-SEG-01** Dónde se almacena el rol empresarial:
  - (a) Tabla de perfiles en PostgreSQL vinculada al `sub`, con rol y estado activo. Spring la consulta en cada petición (o con caché). Los cambios de rol se aplican de inmediato y el Administrador los gestiona desde la API. Es la opción más directa para AUTH-04 ("consultar el perfil/rol").
  - (b) Claim propio agregado por un Custom Access Token Hook de Supabase y leído con un `JwtAuthenticationConverter`. Evita una consulta por petición, pero el cambio de rol solo se aplica al renovar el token, y la función del hook se mantiene en Supabase.
  - (c) `app_metadata` de Supabase Auth. Modificarlo exige la clave `service_role` en el backend.
  - Recomendación: (a).
- **DP-SEG-02** Mecanismo de autorización: reglas por prefijo de ruta en `SecurityFilterChain` (por ejemplo, `/api/crm/**`), seguridad por método (`@EnableMethodSecurity` y `@PreAuthorize`) o ambas. Recomendación: reglas por módulo en la cadena de filtros y `@PreAuthorize` para las operaciones con permisos más finos. Depende de DP-API-01.
- **DP-SEG-04** Orígenes CORS de producción y paso de la lista de orígenes a una propiedad o variable de entorno. Depende de DP-ARQ-03.
- **DP-SEG-05** Bloqueo del acceso directo a las tablas de negocio:
  - (a) Habilitar RLS sin políticas para `anon` y `authenticated` en cada tabla de negocio, dentro de su migración, y revocar los grants que use la Data API.
  - (b) Usar un esquema no expuesto (DP-ARQ-02).
  - (c) Deshabilitar la Data API, ya que el frontend solo usa Auth.
  - En cualquier caso, verificar que el rol con el que se conecta Spring Boot (propietario de las tablas) no queda bloqueado por RLS.
- **DP-SEG-06** Auditoría: guardar el `sub` del usuario y la fecha en los cambios críticos (estados de paquetes y contratos, movimientos de inventario, registros financieros). El historial de SGA-04 es el primer candidato.
