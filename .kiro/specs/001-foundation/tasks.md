# Implementation Plan: 001-foundation

## Overview

Este plan convierte el diseño aprobado de `001-foundation` (integración vertical de autenticación) en tareas de código pequeñas e incrementales. El backend ya tiene implementados `SecurityConfig`, `AuthController` (`GET /api/me`), la configuración JWT de `application.properties` y `backend/.env.example`; por eso **no se re-implementan**, solo se verifican con pruebas. El frontend React está pendiente de inicializar.

Cada tarea indica su **área** (backend / frontend / base de datos / documentación), un **responsable sugerido** (según `sdd-workflow.md`) y los **requisitos** que cubre. Las tareas que dependen de aprobar una decisión pendiente lo señalan explícitamente.

### Notas de alcance (del diseño aprobado)

- **No se aplican pruebas basadas en propiedades (PBT).** El diseño justifica que la validación de JWT está delegada a Spring Security, el mapeo de claims a JSON es simple y el resto es comportamiento de UI; se usan pruebas por ejemplo (unitarias) e integración.
- **Rol empresarial y 403 por rol quedan diferidos** a DP-SEG-01/DP-SEG-02. No hay tareas para la matriz de roles ni para el criterio 9.6 (JWT válido sin rol → 403).
- **No se añaden dependencias de backend nuevas**: lo necesario ya está en `backend/pom.xml` (scope `test`: `spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`, `spring-boot-starter-security-oauth2-resource-server-test`, `h2`).
- **DP-TEC-01 (inicialización del frontend) está pendiente de aprobación.** Las tareas 4–11 asumen la propuesta del diseño (Vite + React + Tailwind CSS + React Router + `@supabase/supabase-js`, JavaScript, `fetch` nativo, Vitest + React Testing Library) y **no deben ejecutarse hasta que el equipo apruebe DP-TEC-01**.

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
  - Ejecutar `.\mvnw.cmd test` desde `backend/` y asegurar que todas las pruebas pasan; ask the user if questions arise.

- [ ] 4. Inicializar el proyecto frontend (DEPENDE DE APROBAR DP-TEC-01)
  - Área: frontend · Responsable: Paula · **Bloqueada por DP-TEC-01**
  - Inicializar Vite + React en `frontend/`; configurar Tailwind CSS, React Router y `@supabase/supabase-js`
  - Crear la estructura propuesta: `src/lib/`, `src/context/`, `src/routes/`, `src/services/`, `src/components/`, `src/pages/`
  - Crear `frontend/.env.example` con `VITE_SUPABASE_URL`, `VITE_SUPABASE_PUBLISHABLE_KEY`, `VITE_API_BASE_URL` (solo valores públicos/ficticios, ningún secreto)
  - _Requisitos: 8.1, 8.4, 10.4, NF-01, CA-01, DP-TEC-01_

- [ ] 5. Implementar el cliente de Supabase (DEPENDE DE DP-TEC-01)
  - Área: frontend · Responsable: Paula · **Bloqueada por DP-TEC-01**
  - [ ] 5.1 Implementar `src/lib/supabase.js`
    - `createClient(import.meta.env.VITE_SUPABASE_URL, import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY)`; no incluir claves `service_role`/`secret` ni el JWT secret
    - _Requisitos: 3.1, 8.1, 8.2, NF-01_

- [ ] 6. Implementar el estado de sesión (DEPENDE DE DP-TEC-01)
  - Área: frontend · Responsable: Paula · **Bloqueada por DP-TEC-01**
  - [ ] 6.1 Implementar `src/context/AuthContext.jsx`
    - Exponer `session`, `loading`, `signIn(email, password)` y `signOut()`
    - Suscribirse a `onAuthStateChange` para reflejar renovaciones e invalidaciones del token; usar el token renovado en llamadas posteriores
    - En `signOut`, eliminar el estado local aunque la llamada a Supabase falle, y redirigir a login
    - _Requisitos: 1.2, 1.3, 2.1, 2.2, 2.3, 2.4, 3.1, 3.4_

  - [ ]* 6.2 Escribir pruebas del AuthContext (Vitest + RTL, DP-TEC-01)
    - Con Supabase simulado: `signIn` guarda la sesión; `signOut` limpia el estado aunque la API falle
    - _Requisitos: 1.3, 2.1, 2.3, NF-07_

- [ ] 7. Implementar la vista de inicio de sesión (DEPENDE DE DP-TEC-01)
  - Área: frontend · Responsable: Paula · **Bloqueada por DP-TEC-01**
  - [ ] 7.1 Implementar `src/pages/Login.jsx`
    - Formulario con correo y contraseña obligatorios; validar campos vacíos y formato de correo en cliente sin llamar a Supabase cuando son inválidos
    - Indicador de carga y botón deshabilitado durante la petición (timeout 30 s)
    - Mensaje de error comprensible que conserva el correo y no revela si falló correo o contraseña, sin exponer detalles técnicos
    - _Requisitos: 1.1, 1.4, 1.5, 1.6, 1.7, 7.1, NF-06, AUTH-05_

  - [ ]* 7.2 Escribir pruebas de Login (Vitest + RTL, DP-TEC-01)
    - Campos obligatorios y validación de formato; estado de carga; mensaje de credenciales incorrectas que conserva el correo
    - _Requisitos: 1.1, 1.4, 1.5, 1.6, 1.7, 7.1, NF-07_

- [ ] 8. Implementar la protección de rutas (DEPENDE DE DP-TEC-01)
  - Área: frontend · Responsable: Paula · **Bloqueada por DP-TEC-01**
  - [ ] 8.1 Implementar `src/routes/ProtectedRoute.jsx`
    - Si no hay sesión activa, redirigir a `/login` sin renderizar el contenido privado; con sesión activa, permitir el acceso
    - _Requisitos: 3.2, 3.3_

  - [ ]* 8.2 Escribir pruebas de ProtectedRoute (Vitest + RTL, DP-TEC-01)
    - Sin sesión redirige y no renderiza contenido privado; con sesión permite el acceso
    - _Requisitos: 3.2, 3.3, NF-07_

- [ ] 9. Implementar el cliente HTTP hacia la API (DEPENDE DE DP-TEC-01)
  - Área: frontend · Responsable: Paula · **Bloqueada por DP-TEC-01**
  - [ ] 9.1 Implementar `src/services/apiClient.js`
    - Obtener el access token vigente de la sesión de `supabase-js` y adjuntarlo como `Authorization: Bearer <token>`
    - Aplicar timeout de 30 s; ante 401 descartar la sesión y redirigir a login indicando sesión expirada/inválida; ante 403 mostrar "sin permisos" sin cerrar sesión; ante timeout/sin respuesta permitir reintentar
    - _Requisitos: 4.1, 3.5, 7.2, 7.3, 7.5, 10.2_

  - [ ]* 9.2 Escribir pruebas de apiClient (Vitest + RTL, DP-TEC-01)
    - Adjunta el Bearer; 401 dispara descarte de sesión y redirección; 403 muestra mensaje sin cerrar sesión; timeout permite reintentar
    - _Requisitos: 4.1, 3.5, 7.2, 7.3, 7.5, 10.2, NF-07_

- [ ] 10. Implementar la vista protegida de perfil (DEPENDE DE DP-TEC-01)
  - Área: frontend · Responsable: Paula · **Bloqueada por DP-TEC-01**
  - [ ] 10.1 Implementar `src/pages/Perfil.jsx`
    - Al montar, llamar a `GET /api/me` vía `apiClient`; mostrar `id` y `email`
    - Mostrar indicador de carga y evitar una segunda solicitud simultánea a `/api/me`
    - Ofrecer botón de cierre de sesión que invoca `signOut`
    - _Requisitos: 5.7, 10.1, 10.3, 2.1, 2.2_

  - [ ]* 10.2 Escribir pruebas de Perfil (Vitest + RTL, DP-TEC-01)
    - 200 muestra `id` y `email`; indicador de carga; sin doble solicitud simultánea; botón de cierre de sesión
    - _Requisitos: 5.7, 10.1, 10.3, 2.1, NF-07_

- [ ] 11. Integrar el enrutado del frontend (DEPENDE DE DP-TEC-01)
  - Área: frontend · Responsable: Paula · **Bloqueada por DP-TEC-01**
  - [ ] 11.1 Conectar `App.jsx` y `main.jsx`
    - Envolver la app con `AuthContext`; definir rutas con React Router: `/login` pública y la ruta de perfil protegida con `ProtectedRoute`
    - Verificar el flujo completo en memoria: Login → sesión → Perfil consume `/api/me`
    - _Requisitos: 3.2, 3.3, 5.7, 10.1_

- [ ]* 12. Verificar ausencia de secretos en el frontend
  - Área: frontend · Responsable: Paula · **Bloqueada por DP-TEC-01**
  - Comprobar que el código fuente y el bundle de build no contienen la contraseña de la base ni claves `service_role`/`secret`/JWT secret; tratar cualquier aparición como fallo de verificación
  - _Requisitos: 8.2, 8.3, NF-01_

- [ ] 13. Actualizar la documentación de ejecución
  - Área: documentación · Responsable: Paula (frontend) y Sebastián (backend)
  - Documentar en el `README` versionado los comandos de instalación y arranque del frontend y del backend, y las variables de entorno requeridas por cada componente
  - Asegurar que `frontend/.env.example` y `backend/.env.example` listan todas las variables requeridas con valores ficticios
  - _Requisitos: 8.4, 8.5, 10.4, CA-01_

- [ ] 14. Checkpoint final
  - Ejecutar `.\mvnw.cmd test` (backend) y la suite de Vitest (frontend); asegurar que todas las pruebas pasan; ask the user if questions arise.

## Notes

- Las tareas marcadas con `*` son opcionales (pruebas unitarias/integración y verificaciones) y pueden omitirse para un MVP más rápido, aunque NF-07/CA-03 las exigen para la Definition of Done.
- Las tareas 4–12 están **bloqueadas por la aprobación de DP-TEC-01**; no se implementan hasta que el equipo confirme el stack y los nombres de las variables `VITE_*`.
- El backend (tareas 1–3) puede avanzar en paralelo al frontend una vez aprobado DP-TEC-01.
- No se incluyen tareas de PBT (el diseño justifica que no aplica), ni de rol empresarial/403 por rol (diferido a DP-SEG-01/DP-SEG-02).
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
    { "id": 6, "tasks": ["12", "13"] }
  ]
}
```
