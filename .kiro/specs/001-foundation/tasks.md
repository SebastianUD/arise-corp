# Implementation Plan: 001-foundation

## Overview

Este plan convierte el diseño aprobado de `001-foundation` (integración vertical de autenticación) en tareas de código pequeñas e incrementales. Las tareas 1–19 implementan el flujo frontend, la autenticación JWT, el perfil empresarial y su administración, la autorización por rol y los errores RFC 9457. La validación con una cuenta real de Supabase y el cierre de fase siguen pendientes (tareas 20–21).

Cada tarea indica **área**, **responsable** y **requisitos**. Las tareas abiertas reflejan trabajo necesario tras las decisiones ya adoptadas.

### Notas de alcance (del diseño aprobado)

- **No se aplican pruebas basadas en propiedades (PBT).** El diseño justifica que la validación de JWT está delegada a Spring Security, el mapeo de claims a JSON es simple y el resto es comportamiento de UI; se usan pruebas por ejemplo (unitarias) e integración.
- **Perfil empresarial y autorización por rol son Must** (AUTH-04); se implementaron en las tareas 15–17. La tarea 18 uniforma errores. La tarea 19 completa la presentación del rol; las tareas 20–21 cubren la validación manual y el checkpoint final.
- **No se añaden dependencias de backend nuevas**: lo necesario ya está en `backend/pom.xml` (scope `test`: `spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`, `spring-boot-starter-security-oauth2-resource-server-test`, `h2`).
- **DP-TEC-01 aprobada por Paula el 2026-11-07.** Se confirma la propuesta de JavaScript, Vite, React, Tailwind CSS, React Router, Supabase JS, fetch nativo y Vitest + React Testing Library. Versiones concretas en D5 de `design.md` y `frontend/package-lock.json`.

## Tasks

- [x] 1. Configurar el perfil de pruebas aislado del backend (DP-TEC-02, aprobada)
  - Área: backend · Responsable: Sebastián
  - Crear `backend/src/test/resources/application.properties` (o `application-test.properties` con perfil `test` activo en las pruebas) con datasource H2 en memoria y `spring.jpa.hibernate.ddl-auto` adecuado para H2
  - Definir valores ficticios para las propiedades JWT (`issuer-uri`, `jwk-set-uri`, `jws-algorithms=ES256`, `audiences[0]=authenticated`) para que el contexto arranque sin conectarse a Supabase ni a `arise-corp-dev`
  - Verificar que `AriseBackendApplicationTests.contextLoads()` arranca con el perfil de pruebas sin credenciales reales
  - _Requisitos: 9.1, 9.4, NF-07, CA-03_

- [x] 2. Verificar `GET /api/me` con pruebas de controlador/seguridad (MockMvc)
  - [x] 2.1 Escribir prueba: `GET /api/me` sin token → 401 sin cuerpo JSON
    - Área: backend · Responsable: Sebastián
    - Usar MockMvc contra el contexto de Spring Security real; afirmar status 401 y ausencia de cuerpo JSON
    - _Requisitos: 9.1, 4.6, 5.6, CA-02, CA-03_

  - [x] 2.2 Escribir prueba: `GET /api/me` con JWT simulado (claim `sub`) → 200
    - Área: backend · Responsable: Sebastián
    - Usar `SecurityMockMvcRequestPostProcessors.jwt()` con el claim `sub`; afirmar status 200
    - _Requisitos: 9.2, 5.1, CA-02, CA-03_

  - [x] 2.3 Escribir prueba: el cuerpo 200 contiene `id`, `email` y `role`
    - Área: backend · Responsable: Sebastián
    - JWT simulado con claims `sub`, `email` y `role`; afirmar que el JSON de respuesta contiene `id`, `email` y `role` derivados del token
    - _Requisitos: 9.3, 5.1, 5.2, 5.4_

  - [x] 2.4 Escribir prueba: token inválido o vencido → 401
    - Área: backend · Responsable: Sebastián
    - Afirmar que una petición con token manipulado/expirado recibe 401
    - _Requisitos: 9.5, 4.7, 4.8, CA-02_

  - [x] 2.5 Verificar que ninguna prueba usa tokens reales de Supabase ni la base compartida
    - Área: backend · Responsable: Sebastián
    - Revisar que todas las pruebas de endpoints usan `jwt()` simulado y el perfil H2; sin credenciales de `arise-corp-dev`
    - _Requisitos: 9.4, NF-07_

- [x] 3. Checkpoint del backend
  - Ejecutar `.\mvnw.cmd test` desde `backend/` y asegurar que todas las pruebas pasan.

- [x] 4. Inicializar el proyecto frontend (DP-TEC-01 aprobada)
  - Área: frontend · Responsable: Paula · **DP-TEC-01 aprobada**
  - Inicializar Vite + React en `frontend/`; configurar Tailwind CSS, React Router y `@supabase/supabase-js`
  - Crear la estructura propuesta: `src/lib/`, `src/context/`, `src/routes/`, `src/services/`, `src/components/`, `src/pages/`
  - Crear `frontend/.env.example` con `VITE_SUPABASE_URL`, `VITE_SUPABASE_PUBLISHABLE_KEY`, `VITE_API_BASE_URL` (solo valores públicos/ficticios, ningún secreto)
  - _Requisitos: 8.1, 8.4, 10.4, NF-01, CA-01, DP-TEC-01_

- [x] 5. Implementar el cliente de Supabase (DEPENDE DE DP-TEC-01)
  - Área: frontend · Responsable: Paula · **DP-TEC-01 aprobada**
  - [x] 5.1 Implementar `src/lib/supabase.js`
    - `createClient(import.meta.env.VITE_SUPABASE_URL, import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY)`; no incluir claves `service_role`/`secret` ni el JWT secret
    - _Requisitos: 3.1, 8.1, 8.2, NF-01_

- [x] 6. Implementar el estado de sesión (DEPENDE DE DP-TEC-01)
  - Área: frontend · Responsable: Paula · **DP-TEC-01 aprobada**
  - [x] 6.1 Implementar `src/context/AuthContext.jsx`
    - Exponer `session`, `loading`, `signIn(email, password)` y `signOut()`
    - Suscribirse a `onAuthStateChange` para reflejar renovaciones e invalidaciones del token; usar el token renovado en llamadas posteriores
    - En `signOut`, eliminar el estado local aunque la llamada a Supabase falle, y redirigir a login
    - _Requisitos: 1.2, 1.3, 2.1, 2.2, 2.3, 2.4, 3.1, 3.4_

  - [x]* 6.2 Escribir pruebas del AuthContext (Vitest + RTL, DP-TEC-01)
    - Con Supabase simulado: `signIn` guarda la sesión; `signOut` limpia el estado aunque la API falle
    - _Requisitos: 1.3, 2.1, 2.3, NF-07_

- [x] 7. Implementar la vista de inicio de sesión (DEPENDE DE DP-TEC-01)
  - Área: frontend · Responsable: Paula · **DP-TEC-01 aprobada**
  - [x] 7.1 Implementar `src/pages/Login.jsx`
    - Formulario con correo y contraseña obligatorios; validar campos vacíos y formato de correo en cliente sin llamar a Supabase cuando son inválidos
    - Indicador de carga y botón deshabilitado durante la petición (timeout 30 s)
    - Mensaje de error comprensible que conserva el correo y no revela si falló correo o contraseña, sin exponer detalles técnicos
    - _Requisitos: 1.1, 1.4, 1.5, 1.6, 1.7, 7.1, NF-06, AUTH-05_

  - [x]* 7.2 Escribir pruebas de Login (Vitest + RTL, DP-TEC-01)
    - Campos obligatorios y validación de formato; estado de carga; mensaje de credenciales incorrectas que conserva el correo
    - _Requisitos: 1.1, 1.4, 1.5, 1.6, 1.7, 7.1, NF-07_

- [x] 8. Implementar la protección de rutas (DEPENDE DE DP-TEC-01)
  - Área: frontend · Responsable: Paula · **DP-TEC-01 aprobada**
  - [x] 8.1 Implementar `src/routes/ProtectedRoute.jsx`
    - Si no hay sesión activa, redirigir a `/login` sin renderizar el contenido privado; con sesión activa, permitir el acceso
    - _Requisitos: 3.2, 3.3_

  - [x]* 8.2 Escribir pruebas de ProtectedRoute (Vitest + RTL, DP-TEC-01)
    - Sin sesión redirige y no renderiza contenido privado; con sesión permite el acceso
    - _Requisitos: 3.2, 3.3, NF-07_

- [x] 9. Implementar el cliente HTTP hacia la API (DEPENDE DE DP-TEC-01)
  - Área: frontend · Responsable: Paula · **DP-TEC-01 aprobada**
  - [x] 9.1 Implementar `src/services/apiClient.js`
    - Obtener el access token vigente de la sesión de `supabase-js` y adjuntarlo como `Authorization: Bearer <token>`
    - Aplicar timeout de 30 s; ante 401 descartar la sesión y redirigir a login indicando sesión expirada/inválida; ante 403 mostrar "sin permisos" sin cerrar sesión; ante timeout/sin respuesta permitir reintentar
    - _Requisitos: 4.1, 3.5, 7.2, 7.3, 7.5, 10.2_

  - [x]* 9.2 Escribir pruebas de apiClient (Vitest + RTL, DP-TEC-01)
    - Adjunta el Bearer; 401 dispara descarte de sesión y redirección; 403 muestra mensaje sin cerrar sesión; timeout permite reintentar
    - _Requisitos: 4.1, 3.5, 7.2, 7.3, 7.5, 10.2, NF-07_

- [x] 10. Implementar la vista protegida de perfil (DEPENDE DE DP-TEC-01)
  - Área: frontend · Responsable: Paula · **DP-TEC-01 aprobada**
  - [x] 10.1 Implementar `src/pages/Perfil.jsx`
    - Al montar, llamar a `GET /api/me` vía `apiClient`; mostrar `id` y `email`
    - Mostrar indicador de carga y evitar una segunda solicitud simultánea a `/api/me`
    - Ofrecer botón de cierre de sesión que invoca `signOut`
    - _Requisitos: 5.7, 10.1, 10.3, 2.1, 2.2_

  - [x]* 10.2 Escribir pruebas de Perfil (Vitest + RTL, DP-TEC-01)
    - 200 muestra `id` y `email`; indicador de carga; sin doble solicitud simultánea; botón de cierre de sesión
    - _Requisitos: 5.7, 10.1, 10.3, 2.1, NF-07_

- [x] 11. Integrar el enrutado del frontend (DEPENDE DE DP-TEC-01)
  - Área: frontend · Responsable: Paula · **DP-TEC-01 aprobada**
  - [x] 11.1 Conectar `App.jsx` y `main.jsx`
    - Envolver la app con `AuthContext`; definir rutas con React Router: `/login` pública y la ruta de perfil protegida con `ProtectedRoute`
    - Verificar el flujo completo en memoria: Login → sesión → Perfil consume `/api/me`
    - _Requisitos: 3.2, 3.3, 5.7, 10.1_

- [x]* 12. Verificar ausencia de secretos en el frontend
  - Área: frontend · Responsable: Paula · **DP-TEC-01 aprobada**
  - Comprobar que el código fuente y el bundle de build no contienen la contraseña de la base ni claves `service_role`/`secret`/JWT secret; tratar cualquier aparición como fallo de verificación
  - _Requisitos: 8.2, 8.3, NF-01_

- [x] 13. Actualizar la documentación de ejecución
  - Área: documentación · Responsable: Paula (frontend) y Sebastián (backend)
  - Documentar en el `README` versionado los comandos de instalación y arranque del frontend y del backend, y las variables de entorno requeridas por cada componente
  - Asegurar que `frontend/.env.example` y `backend/.env.example` listan todas las variables requeridas con valores ficticios
  - _Requisitos: 8.4, 8.5, 10.4, CA-01_

- [x] 14. Checkpoint automatizado de la integración base
  - Ejecutar `.\mvnw.cmd test` (backend) y la suite de Vitest (frontend); asegurar que todas las pruebas pasan.

- [x] 15. Crear el esquema privado y el perfil empresarial (DP-ARQ-02, DP-SEG-01, DP-SEG-05, DP-TEC-03)
  - Área: base de datos · Responsable: Davidson; revisión de Sebastián
  - Crear migración Supabase CLI en `supabase/migrations/` para esquema `app` y tabla `perfiles_usuario` (`usuario_id` UUID con FK a `auth.users`, `rol` con CHECK y valores `ADMINISTRADOR`, `COMERCIAL`, `FINANZAS_RRHH`, `OPERACIONES`, `activo`, marcas temporales y `actualizado_por`). Habilitar RLS y revocar acceso a `anon`/`authenticated`; conceder al rol de backend los permisos necesarios.
  - Configurar Hibernate para `app` y `ddl-auto=validate`. No insertar perfiles de usuario ni UUID personales en la migración.
  - Documentar que el primer Administrador se aprovisiona manualmente por un integrante autorizado después de crear su cuenta Auth; no guardar secretos en el repo.
  - _Requisitos: AUTH-04, NF-01, NF-03, NF-05_

- [x] 16. Implementar el perfil en backend y completar `GET /api/me` (DP-EST-01, DP-EST-03)
  - Área: backend · Responsable: Sebastián
  - Mover `AuthController` a `login.controller`; crear entidad, repositorio, servicio y DTO `MeResponse` record bajo `login`. Añadir `.editorconfig` según DP-EST-03.
  - Obtener el perfil por `sub`; responder el rol empresarial, nunca el claim técnico Supabase. Perfil inexistente/inactivo → 403. Hacer configurable CORS por `CORS_ALLOWED_ORIGINS`, valor local `http://localhost:5173` (DP-SEG-04).
  - Actualizar pruebas de `/api/me` para identidad y rol real simulado.
  - _Requisitos: AUTH-04, CA-02_

- [x] 17. Aplicar autorización y administración de perfiles (DP-PRO-01, DP-SEG-02)
  - Área: backend · Responsable: Sebastián
  - Aplicar denegación por defecto y matriz definida en `.kiro/steering/product.md` en `SecurityFilterChain` y servicios; Admin crea/actualiza perfiles mediante `PUT /api/admin/usuarios/{id}/perfil` (201/200) y activa/desactiva mediante `PATCH /api/admin/usuarios/{id}/estado`.
  - Permitir solo las lecturas cruzadas mínimas de ERP-05 y SGA-05. Un rol distinto del requerido recibe 403 sin ejecutar la operación.
  - Añadir pruebas de 401, 403 (sin perfil, inactivo y rol incorrecto) y acceso permitido; incluir perfiles simulados y nunca tokens Supabase reales.
  - _Requisitos: AUTH-04, NF-02, NF-07, CA-03_

- [x] 18. Uniformar errores de API y seguridad (DP-API-02)
  - Área: backend · Responsable: Sebastián
  - Implementar `@RestControllerAdvice`, `AuthenticationEntryPoint` y `AccessDeniedHandler` con RFC 9457 Problem Details; conservar `WWW-Authenticate` en 401 y extensión `errors` para validaciones por campo.
  - Actualizar pruebas backend y manejo de `apiClient`/pruebas frontend para formato acordado, conservando el tratamiento actual de 401 y 403.
  - _Requisitos: AUTH-05, CA-06_

- [x] 19. Mostrar y verificar el rol en el perfil frontend
  - Área: frontend · Responsable: Paula
  - Mostrar el rol empresarial devuelto por `/api/me`; manejar el 403 por perfil no asignado con mensaje y salida segura, sin bucle de redirección.
  - Añadir pruebas de rol y perfil sin permiso.
  - _Requisitos: AUTH-04, AUTH-05, NF-06, NF-07_

- [x] 20. Validar integración con Supabase real y aprovisionamiento inicial
  - Área: integración/documentación · Responsable: Sebastián; apoyo de Paula
  - Aplicar la migración revisada de forma controlada en `arise-corp-dev`, aprovisionar una cuenta Administrador por procedimiento documentado, y verificar login, `GET /api/me`, rol y un caso 403.
  - Registrar solo resultado, fecha y entorno (sin UUID, token, correo ni credenciales); no usar esta cuenta en CI.
  - _Requisitos: AUTH-01 a AUTH-05, CA-02, CA-03_

  - **Verificación — 2026-11-09, `arise-corp-dev`:** el MCP confirmó que `app.perfiles_usuario` existe con la estructura esperada, RLS activo y sin privilegios de esquema/lectura para `anon` ni `authenticated`; el historial remoto contiene la migración `app_perfiles_usuario`. No se volvió a ejecutar una migración ya aplicada. Se aprovisionó el perfil Administrador y se verificó mediante login real que `/api/me` entrega identidad y rol empresarial. Una segunda cuenta Auth sin perfil recibió 403; la interfaz mostró el mensaje de permisos y la salida segura al inicio de sesión. No se registraron datos personales ni credenciales.

- [ ] 21. Checkpoint final de `001-foundation`
  - Área: backend/frontend/documentación · Responsables: los tres
  - Ejecutar suites backend y frontend, build frontend, comprobación de secretos, validación de migración y pruebas de integración PostgreSQL aislada; revisar trazabilidad requisito–tarea y README.
  - Cerrar solo cuando tareas 15–20 estén completadas y CA-01 a CA-03 y CA-06 se hayan verificado.
  - _Requisitos: NF-01, NF-03, NF-05, NF-07, CA-01, CA-02, CA-03, CA-06_

## Notes

- Las tareas marcadas con `*` identifican pruebas/verificaciones de detalle; no se omiten cuando cubren NF-07, CA-03 o el Definition of Done.
- Las tareas 1–19 están implementadas; quedan 20–21 para validar Supabase real y cerrar la primera fase.
- El esquema/perfil (15), backend `/api/me` (16), autorización/administración (17) y Problem Details (18) están implementados; el perfil frontend muestra el rol empresarial y ofrece salida segura ante un 403 (19).
- No se incluyen pruebas basadas en propiedades (PBT): la validación JWT se delega a Spring Security y el mapeo/reglas se cubren con pruebas por ejemplo.
- La validación extremo a extremo con un usuario real contra Supabase se realiza de forma manual y controlada (fuera de estas tareas de código y de CI, para no depender de `arise-corp-dev`), según el diseño y el Requirement 10.
- Cada tarea referencia los requisitos de `requirements.md` para trazabilidad.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1", "4"] },
    { "id": 1, "tasks": ["2.1", "2.2", "2.3", "2.4", "5.1"] },
    { "id": 2, "tasks": ["2.5", "6.1", "8.1", "9.1"] },
    { "id": 3, "tasks": ["6.2", "7.1", "8.2", "9.2"] },
    { "id": 4, "tasks": ["7.2", "10.1"] },
    { "id": 5, "tasks": ["10.2", "11.1"] },
    { "id": 6, "tasks": ["12", "13"] },
    { "id": 7, "tasks": ["15"] },
    { "id": 8, "tasks": ["16"] },
    { "id": 9, "tasks": ["17", "18"] },
    { "id": 10, "tasks": ["19"] },
    { "id": 11, "tasks": ["20"] },
    { "id": 12, "tasks": ["21"] }
  ]
}
```

## Verificación de implementación — 2026-11-07

- Backend: `.\mvnw.cmd test` — 6 pruebas aprobadas, sin errores ni omisiones, con H2 y propiedades ficticias. Ejecutado con el JDK 24 instalado, compilando para Java 21 según `pom.xml`.
- Frontend: `npm test` — 25 pruebas aprobadas (4 archivos). Incluyen el flujo Login → Perfil, validación de formulario, estados de carga, timeouts, token renovado, 401, 403, reintento, limpieza local aun sin red y deduplicación de peticiones en StrictMode.
- `npm run build` — compilación de producción correcta con valores públicos ficticios.
- `npm run check:secrets` — sin patrones de credenciales privadas en fuente, plantilla y bundle; no abre archivos de secretos locales.
- `git diff --check` — sin errores de espacios en los cambios.
- Validación manual con una cuenta real de Supabase: no ejecutada; está fuera de las tareas automáticas de este plan y se completa en la tarea 20. Revisión por otro integrante, CI e integración en Git siguen el proceso del repositorio.

### Ajuste visual según Figma — acceso claro

- Pantalla implementada desde el nodo 9:2 del archivo Figma proporcionado por Paula, manteniendo autenticación y rutas protegidas.
- Logo original descargado a `frontend/public/assets/arise-logo.png`; fuentes Inter locales con licencia OFL. Sin dependencias nuevas.
- Verificación en navegador: referencia 1440 × 960 (formulario x=818, y=226, 508 × 555), tablet 768 y móvil 390 sin desbordamiento horizontal. Logo cargado en su posición y proporción; validación de campos vacíos visible.
- `npm test`: 25 pruebas aprobadas. `npm run build` y `npm run check:secrets`: correctos. Build y revisión visual con configuración pública ficticia; no se prueba una cuenta real ni se modifica el perfil para el que el nodo no proporciona diseño.

### Verificación de la tarea 16 — 2026-11-08

- `AuthController` se movió a `login.controller`; se añadieron `PerfilUsuario`, `RolUsuario`, `PerfilUsuarioRepository`, `PerfilUsuarioService` y el DTO `MeResponse` bajo `login`, con `.editorconfig` en la raíz.
- `GET /api/me` resuelve el perfil por el `sub` del JWT y devuelve el rol empresarial del perfil activo; el claim técnico `role` se ignora. Perfil ausente o inactivo → 403.
- CORS lee `CORS_ALLOWED_ORIGINS` (valor local por defecto `http://localhost:5173`) y se documentó en `.env.example` y README.
- `.\mvnw.cmd test`: 21 pruebas aprobadas, incluidas identidad/rol simulado, 403 por perfil ausente/inactivo y CORS permitido/denegado, sin tokens reales ni la base compartida.

### Verificación de la tarea 17 — 2026-11-08

- `PerfilJwtAuthenticationConverter` deriva `ROLE_<ROL>` del perfil activo y nunca del claim técnico de Supabase; un JWT válido sin perfil activo queda sin authorities y recibe 403 en rutas con rol.
- `SecurityConfig` aplica la matriz: Administrador en todos los módulos; Comercial en CRM; Finanzas/RRHH en ERP; Operaciones en SGA. Las lecturas cruzadas se limitan a clientes del CRM para Finanzas y Operaciones, y contratos del CRM para Operaciones (ERP-05, SGA-05).
- `PUT /api/admin/usuarios/{id}/perfil` crea (201 + `Location`) o actualiza el rol (200); `PATCH /api/admin/usuarios/{id}/estado` activa/desactiva (200). Identidad inexistente en Auth → 404; solicitudes inválidas → 400; roles distintos de Administrador → 403.
- `.\mvnw.cmd test`: 51 pruebas aprobadas, con 401, 403 por perfil ausente/inactivo/rol incorrecto y accesos permitidos para los cuatro roles, usando perfiles persistidos en H2 y JWT simulados.

### Verificación de la tarea 18 — 2026-11-08

- `shared/error` incorpora `GlobalExceptionHandler`, `ProblemDetailsWriter`, `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler` y `RecursoNoEncontradoException`. Todos los errores de API y seguridad responden `application/problem+json` con `type`, `title`, `status`, `detail` e `instance`.
- El 401 conserva `WWW-Authenticate` y se aplica tanto en la cadena de seguridad como en el `BearerTokenAuthenticationFilter` mediante `oauth2ResourceServer().authenticationEntryPoint(...)`; el 403 cubre denegaciones de ruta y de servicio. Las validaciones por campo añaden la extensión `errors` con `field` y `message`.
- Los 400 por JSON inválido, 404 de recursos de negocio, 405 y 409 usan el mismo formato. Los 500 devuelven un mensaje genérico y no exponen trazas, SQL ni detalles internos.
- `apiClient` expone `problem`, `title`, `detail` y `errors` de los 4xx, descarta detalles no confiables, mantiene el cierre de sesión en 401 y el mensaje fijo en 403, y nunca lee el cuerpo de un 5xx.
- `.\mvnw.cmd test`: 58 pruebas aprobadas. `npm test` en `frontend/`: 30 pruebas aprobadas, sin tokens reales de Supabase ni acceso a la base compartida.


### Ajuste responsive del acceso

- Formulario primero en móvil/tablet y en el orden del documento; dos columnas conservadas en escritorio. Encabezado adaptable, inputs móviles de 16 px y controles de 48 px.
- Verificado en navegador a 320, 375, 390, 768, 900, 1024 y 1440 px, y 844 × 390 horizontal: sin desbordamiento lateral. En 1440 × 960 se conserva el formulario de 508 px en x=818, y=226. Validación de campos vacíos revisada en móvil.

### Verificación de la tarea 19 — 2026-11-09

- El perfil muestra el rol empresarial recibido de `/api/me`, con etiquetas Administrador, Comercial, Finanzas / RR. HH. y Operaciones. No consulta el claim técnico del token ni realiza autorización por rol en el frontend.
- Ante 403 se ocultan los datos del perfil y se indica que falta un perfil activo o permisos, con orientación a contactar al administrador. La sesión se conserva hasta que el usuario elige salir; el botón Volver al inicio de sesión limpia la sesión local aun si falla la red. No hay reintentos ni redirecciones automáticas en 403. Los errores de conexión mantienen Reintentar.
- Pruebas: cuatro roles, rol visible en el flujo Login → Perfil, perfil sin permiso y salida sin bucle incluso ante fallo de signOut y eventos tardíos. `npm test`: 35 pruebas aprobadas. `npm run build` y `npm run check:secrets`: correctos.
- Tareas 20 y 21 continúan pendientes. No se valida una cuenta real ni se aplican migraciones en este cambio.
