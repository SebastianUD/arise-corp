# Requirements Document

## Introduction

Esta es la primera especificación del proyecto Arise Corp. (`001-foundation`). Su objetivo es la integración vertical de autenticación e integración técnica: un usuario autorizado inicia sesión en el frontend React mediante Supabase Auth, obtiene un JWT firmado con ES256, lo envía al backend Spring Boot en el encabezado `Authorization: Bearer <token>`, Spring Security valida el token y el usuario consume el endpoint protegido `GET /api/me`, con manejo claro de errores de autenticación y autorización.

Esta funcionalidad es la base técnica sobre la que se construirán los módulos CRM, ERP y SGA. Cubre los requisitos AUTH-01 a AUTH-05 del MVP, con los requisitos no funcionales NF-01, NF-02, NF-06 y NF-07, y los criterios globales de aceptación CA-01, CA-02, CA-03 y CA-06.

Alcance: integración de sesión, perfil empresarial, autorización por rol, manejo de errores, pruebas y validación manual controlada. Las decisiones DP que afectan esta spec están adoptadas en los steering files y concretadas en `design.md`; el código ya implementado se conserva y las tareas abiertas completan la fase.

Parte del backend ya está implementada (OAuth2 Resource Server, endpoint `GET /api/me`, CORS, sesiones STATELESS). Esta especificación no re-implementa esos componentes, pero sí define los requisitos de comportamiento e integración que deben verificarse sobre ellos.

## Glossary

- **Sistema_Autenticacion (Supabase_Auth):** servicio gestionado de Supabase que inicia y cierra sesión, emite y renueva los JWT firmados con ES256 y publica las claves públicas en el endpoint JWKS.
- **Frontend (SPA_React):** aplicación de página única construida con React que presenta la vista de inicio de sesión, mantiene el estado de la sesión, protege las rutas privadas y llama a la API con el token de acceso.
- **Backend (API_Spring_Boot):** aplicación Spring Boot que actúa como OAuth2 Resource Server, valida el JWT en cada petición, autoriza y responde JSON con códigos HTTP coherentes.
- **JWT:** JSON Web Token de acceso emitido por Supabase_Auth, firmado con el algoritmo ES256.
- **Token de acceso:** el JWT que el Frontend envía al Backend en el encabezado `Authorization: Bearer <token>`.
- **Claim sub:** identificador único del usuario (UUID de Supabase Auth); es el identificador autoritativo del usuario en el Backend.
- **Claim role (técnico):** claim `role` de Supabase cuyo valor es el rol de PostgreSQL (normalmente `authenticated`); no representa el rol empresarial del usuario.
- **Rol empresarial:** rol de negocio del usuario (por ejemplo, Administrador, Comercial, Finanzas / RR. HH., Operaciones). Se almacena en `app.perfiles_usuario`; cada usuario tiene un solo rol de los definidos en `product.md`. Spring Boot aplica los permisos de backend y rechaza perfiles ausentes o inactivos.
- **Endpoint /api/me:** endpoint protegido del Backend que devuelve la identidad del usuario autenticado a partir del JWT validado.
- **Ruta privada:** ruta del Frontend que solo puede visitarse con una sesión activa.
- **Sesion expirada:** estado en el que el token de acceso del usuario ha superado su tiempo de expiración (`exp`).

## Requirements

### Requirement 1: Inicio de sesión del usuario (AUTH-01)

**User Story:** Como usuario autorizado de Arise Corp., quiero iniciar sesión con mi correo y contraseña, para acceder a las funciones protegidas de la plataforma.

#### Acceptance Criteria

1. THE Frontend SHALL presentar un formulario de inicio de sesión con un campo de correo electrónico y un campo de contraseña, ambos marcados como obligatorios. _(AUTH-01, NF-06)_
2. WHEN el usuario envía el formulario con un correo con formato válido y una contraseña no vacía, THE Frontend SHALL solicitar el inicio de sesión a Supabase_Auth mediante `@supabase/supabase-js`. _(AUTH-01)_
3. WHEN Supabase_Auth confirma el inicio de sesión, THE Frontend SHALL recibir y conservar el token de acceso JWT y el refresh token mediante la sesión de `supabase-js`. _(AUTH-01, AUTH-02)_
4. WHILE una operación de inicio de sesión está en curso, THE Frontend SHALL mostrar un indicador de carga y deshabilitar el botón de envío hasta que Supabase_Auth responda o se agote un tiempo de espera de 30 segundos. _(NF-06)_
5. IF el usuario envía el formulario con el campo de correo o el de contraseña vacíos, THEN THE Frontend SHALL mostrar junto a cada campo vacío un mensaje de validación que indique que el campo es obligatorio y no enviar la solicitud a Supabase_Auth. _(NF-06)_
6. IF el usuario envía el formulario con un correo cuyo formato es inválido, THEN THE Frontend SHALL mostrar junto al campo de correo un mensaje de validación que indique que el formato es inválido y no enviar la solicitud a Supabase_Auth. _(NF-06)_
7. IF Supabase_Auth rechaza las credenciales o la solicitud de inicio de sesión falla por error de red o por agotarse el tiempo de espera de 30 segundos, THEN THE Frontend SHALL mostrar un mensaje de error comprensible para el usuario, sin exponer detalles técnicos, códigos internos ni trazas, y SHALL mantener los valores ya introducidos en el campo de correo. _(AUTH-05, NF-06, CA-06)_

### Requirement 2: Cierre de sesión del usuario (AUTH-01)

**User Story:** Como usuario autenticado, quiero cerrar sesión, para proteger mi cuenta cuando termino de usar la plataforma.

#### Acceptance Criteria

1. WHEN el usuario autenticado solicita cerrar sesión, THE Frontend SHALL invocar `signOut` de Supabase_Auth y eliminar el estado de sesión local. _(AUTH-01)_
2. WHEN `signOut` de Supabase_Auth se completa correctamente, THE Frontend SHALL redirigir al usuario a la vista de inicio de sesión. _(AUTH-01, AUTH-02)_
3. IF la invocación a `signOut` de Supabase_Auth falla, THEN THE Frontend SHALL eliminar de todas formas el estado de sesión local y redirigir al usuario a la vista de inicio de sesión. _(AUTH-01)_
4. WHEN el estado de sesión local se ha eliminado tras un cierre de sesión, IF el usuario intenta acceder a una ruta privada, THEN THE Frontend SHALL redirigirlo a la vista de inicio de sesión. _(AUTH-01, AUTH-02)_

### Requirement 3: Estado de sesión y rutas privadas en el frontend (AUTH-02)

**User Story:** Como usuario, quiero que la aplicación recuerde mi sesión y proteja las rutas privadas, para no tener que volver a autenticarme en cada navegación y para que las vistas internas solo sean accesibles con una sesión activa.

#### Acceptance Criteria

1. THE Frontend SHALL mantener el estado de la sesión del usuario a través de la gestión de sesión de `supabase-js`, conservando la sesión entre recargas de página y navegaciones mientras el token de acceso no haya expirado. _(AUTH-02)_
2. WHILE existe una sesión activa con un token de acceso válido y no expirado, THE Frontend SHALL permitir el acceso a las rutas privadas sin solicitar nuevas credenciales. _(AUTH-02)_
3. IF un usuario sin sesión activa intenta acceder a una ruta privada, THEN THE Frontend SHALL redirigirlo a la vista de inicio de sesión sin renderizar el contenido de la ruta privada. _(AUTH-02)_
4. WHEN Supabase_Auth renueva el token de acceso, THE Frontend SHALL utilizar el token renovado en todas las llamadas al Backend posteriores a la renovación, en el encabezado `Authorization: Bearer <token>`. _(AUTH-02, AUTH-03)_
5. IF la sesión expira o se invalida durante el uso de una ruta privada y una llamada al Backend es rechazada con 401, THEN THE Frontend SHALL redirigir al usuario a la vista de inicio de sesión e indicar que la sesión expiró. _(AUTH-02)_

### Requirement 4: Envío del token y validación del JWT en el backend (AUTH-03)

**User Story:** Como integrante del equipo, quiero que el frontend envíe el token de acceso al backend y que el backend valide el JWT, para que solo las peticiones autenticadas accedan a los recursos protegidos.

#### Acceptance Criteria

1. WHEN el Frontend llama a un endpoint protegido del Backend, THE Frontend SHALL incluir el token de acceso en el encabezado `Authorization: Bearer <token>`. _(AUTH-03)_
2. WHEN el Backend recibe una petición con un token de acceso, THE Backend SHALL validar la firma del JWT con las claves públicas del JWKS de Supabase_Auth. _(AUTH-03, CA-02)_
3. THE Backend SHALL aceptar únicamente tokens firmados con el algoritmo ES256. _(AUTH-03, CA-02)_
4. WHEN el Backend valida un JWT, THE Backend SHALL verificar el emisor (`iss` igual a `${SUPABASE_URL}/auth/v1`), la audiencia (`aud` igual a `authenticated`), la expiración (`exp`) y el momento de validez (`nbf`), aplicando una tolerancia de reloj de 60 segundos. _(AUTH-03, CA-02)_
5. WHEN el Backend recibe una petición a un endpoint protegido con un token válido y vigente, THE Backend SHALL permitir el acceso al recurso solicitado. _(AUTH-03, CA-02)_
6. IF una petición a un endpoint protegido llega sin token de acceso, THEN THE Backend SHALL responder con el código HTTP 401, con el encabezado `WWW-Authenticate` y un cuerpo RFC 9457 Problem Details. _(AUTH-03, CA-02, CA-06)_
7. IF una petición presenta un token con firma inválida, con emisor o audiencia incorrectos o mal formado, THEN THE Backend SHALL responder con el código HTTP 401. _(AUTH-03, CA-02, CA-06)_
8. IF una petición presenta un token expirado, THEN THE Backend SHALL responder con el código HTTP 401. _(AUTH-03, CA-02, CA-06)_

### Requirement 5: Consulta de identidad del usuario autenticado en /api/me (AUTH-03, AUTH-04)

**User Story:** Como usuario autenticado, quiero consultar mi identidad a través de un endpoint protegido, para verificar que la integración de autenticación extremo a extremo funciona.

#### Acceptance Criteria

1. WHEN el Backend recibe una petición a `GET /api/me` con un JWT válido y vigente en el encabezado `Authorization: Bearer <token>`, THE Backend SHALL responder con el código HTTP 200 y un cuerpo JSON con los campos `id`, `email` y `role`. _(AUTH-03, CA-02)_
2. THE Backend SHALL obtener el campo `id` del claim `sub` del JWT validado. _(AUTH-03, AUTH-04)_
3. THE Backend SHALL derivar la identidad del usuario a partir del JWT ya validado por Spring Security, sin decodificar el token manualmente. _(AUTH-03)_
4. THE Backend SHALL devolver en `role` el rol empresarial activo obtenido de `app.perfiles_usuario`, nunca el claim técnico `role` de Supabase. Si no existe perfil activo, SHALL responder 403 con Problem Details. _(AUTH-04, NF-02)_
5. IF el JWT validado no contiene el claim `email`, THEN THE Backend SHALL devolver el campo `email` con valor nulo o vacío sin provocar un error. _(AUTH-03)_
6. IF una petición a `GET /api/me` llega sin token o con un token inválido o vencido, THEN THE Backend SHALL responder con el código HTTP 401, con Problem Details y el encabezado `WWW-Authenticate`. _(AUTH-03, CA-02, CA-06)_
7. WHEN el Frontend recibe la respuesta 200 de `GET /api/me`, THE Frontend SHALL mostrar la identidad del usuario en una vista protegida. _(AUTH-03)_

### Requirement 6: Perfil y autorización por rol empresarial (AUTH-04)

**User Story:** Como Administrador, quiero que los permisos por rol empresarial se apliquen en el backend, para controlar qué puede hacer cada usuario según su rol.

#### Acceptance Criteria

1. WHEN se recibe una petición protegida con JWT válido, THE Backend SHALL buscar el perfil usando el `sub` UUID del JWT en `app.perfiles_usuario`.
2. IF el perfil no existe, está inactivo o no tiene un rol permitido, THEN THE Backend SHALL rechazar la petición con HTTP 403 sin ejecutar la operación.
3. THE Backend SHALL aplicar en Spring Boot la matriz de roles y lecturas cruzadas de `.kiro/steering/product.md`; el frontend no es autoridad de permisos.
4. WHEN un usuario sin el rol requerido invoca una operación, THEN THE Backend SHALL responder 403 en Problem Details y no modificar datos.
5. THE Backend SHALL permitir únicamente al Administrador crear o actualizar perfiles mediante `PUT /api/admin/usuarios/{id}/perfil` y activar/desactivar perfiles mediante `PATCH /api/admin/usuarios/{id}/estado`. Al crear devuelve 201 y al actualizar 200.
6. THE Backend SHALL obtener el rol empresarial de una fuente no modificable por el usuario, sin utilizar el claim técnico `role`, `user_metadata` ni confiar en un rol enviado por el cliente. _(NF-02)_

### Requirement 7: Manejo de errores claros para el usuario (AUTH-05)

**User Story:** Como usuario, quiero recibir mensajes de error claros cuando mis credenciales son incorrectas, mi sesión expira o no tengo permisos, para entender qué ocurrió y cómo continuar.

#### Acceptance Criteria

1. IF el inicio de sesión falla por credenciales incorrectas, THEN THE Frontend SHALL mostrar, en un plazo máximo de 2 segundos desde la respuesta de Supabase_Auth, un mensaje comprensible indicando que las credenciales no son válidas, sin revelar si el fallo corresponde al correo o a la contraseña, y conservar el correo introducido en el formulario. _(AUTH-05, NF-06)_
2. WHEN el Backend responde con el código HTTP 401 a una petición del Frontend, THE Frontend SHALL informar al usuario, mediante un mensaje comprensible, que la sesión expiró o no es válida y redirigirlo a la vista de inicio de sesión. _(AUTH-05)_
3. WHEN el Backend responde con el código HTTP 403 a una petición del Frontend, THE Frontend SHALL mostrar un mensaje comprensible indicando que el usuario no tiene permisos para la operación y mantener al usuario en la vista actual sin cerrar su sesión. _(AUTH-05)_
4. THE Backend SHALL responder los errores con RFC 9457 Problem Details, con mensajes comprensibles y sin exponer trazas de pila, sentencias SQL, nombres de clases, rutas de archivos ni datos internos del sistema. _(AUTH-05, CA-06)_
5. IF el Frontend no recibe respuesta del Backend dentro de un plazo máximo de 30 segundos o la conexión falla, THEN THE Frontend SHALL mostrar un mensaje comprensible indicando que no se pudo completar la operación y permitir al usuario reintentarla. _(AUTH-05, NF-06)_

### Requirement 8: Gestión de secretos y configuración (NF-01, CA-01)

**User Story:** Como integrante del equipo, quiero que los secretos se mantengan fuera del frontend y del repositorio y que la ejecución esté documentada, para que la aplicación sea reproducible de forma segura.

#### Acceptance Criteria

1. THE Frontend SHALL incluir en sus variables `VITE_*` únicamente valores públicos (URL del proyecto Supabase, clave publishable y URL base de la API) y ningún otro tipo de valor. _(NF-01)_
2. THE Frontend SHALL excluir de su código fuente y de su bundle de build la contraseña de la base de datos, las claves `service_role` y `secret` de Supabase y el JWT secret. _(NF-01)_
3. IF el código fuente o el bundle del Frontend contiene la contraseña de la base de datos, una clave `service_role` o `secret` de Supabase, o el JWT secret, THEN THE proyecto SHALL tratar la condición como un fallo de verificación de secretos que impide considerar el requisito cumplido hasta que el valor se elimine. _(NF-01)_
4. THE proyecto SHALL versionar un archivo `.env.example` por cada componente que requiera variables de entorno (frontend y backend), conteniendo cada archivo todas las variables requeridas por ese componente con valores ficticios y sin ningún valor secreto real. _(NF-01, CA-01)_
5. THE proyecto SHALL documentar, en el README versionado, las instrucciones para ejecutar el frontend y el backend en local, incluyendo los comandos de instalación, de arranque y las variables de entorno requeridas por cada componente. _(CA-01)_

> **Decisión adoptada (DP-TEC-01):** stack, comandos y variables `VITE_*` se concretan en `design.md` y `.kiro/steering/tech.md`.

### Requirement 9: Pruebas de los endpoints protegidos (NF-07, CA-03)

**User Story:** Como integrante del equipo, quiero pruebas automatizadas de los endpoints protegidos, para garantizar que la autenticación y la autorización por defecto funcionan y que los accesos no autorizados se rechazan.

#### Acceptance Criteria

1. WHEN se ejecuta la suite de pruebas del Backend, THE Backend SHALL incluir una prueba que verifique que una petición a `GET /api/me` sin token recibe el código HTTP 401 y un cuerpo RFC 9457 Problem Details. _(NF-07, CA-02, CA-03)_
2. WHEN se ejecuta la suite de pruebas del Backend, THE Backend SHALL incluir una prueba que verifique que una petición a `GET /api/me` con un JWT válido simulado que incluye el claim `sub` recibe el código HTTP 200. _(NF-07, CA-02, CA-03)_
3. THE Backend SHALL incluir una prueba que verifique que el cuerpo de la respuesta 200 de `GET /api/me` contiene los campos `id`, `email` y `role`. _(NF-07, CA-02, CA-03)_
4. THE Backend SHALL utilizar JWT simulados con `SecurityMockMvcRequestPostProcessors.jwt()` en todas las pruebas de endpoints protegidos, sin usar tokens reales de Supabase_Auth. _(NF-07)_
5. WHEN se ejecuta la suite de pruebas del Backend, THE Backend SHALL incluir una prueba que verifique que una petición a `GET /api/me` con un token inválido o vencido recibe el código HTTP 401. _(NF-07, CA-02)_
6. THE Backend SHALL incluir pruebas de acceso autorizado y denegado que verifiquen que un JWT válido sin perfil/rol permitido recibe el código HTTP 403. _(NF-07, CA-03)_


### Requirement 10: Integración vertical extremo a extremo

**User Story:** Como integrante del equipo, quiero demostrar el flujo completo de autenticación desde el frontend hasta el backend, para validar que la base técnica de la plataforma funciona de extremo a extremo.

#### Acceptance Criteria

1. WHEN un usuario autorizado inicia sesión en el Frontend con credenciales válidas y navega a la vista protegida, THE Frontend SHALL obtener el token de acceso, llamar a `GET /api/me` del Backend incluyendo el token en el encabezado `Authorization: Bearer <token>` y mostrar los campos de identidad devueltos (identificador y correo) en un plazo máximo de 3 segundos. _(AUTH-01, AUTH-02, AUTH-03, CA-02)_
2. IF el Backend responde a `GET /api/me` con un estado de autenticación fallida (token ausente, inválido o vencido), THEN THE Frontend SHALL descartar la sesión local, redirigir al usuario a la vista de inicio de sesión y mostrar un mensaje de error indicando que la sesión no es válida o ha expirado, sin exponer el contenido del token. _(AUTH-02, AUTH-03, CA-02, CA-06)_
3. WHILE el Frontend espera la respuesta de `GET /api/me`, THE Frontend SHALL mostrar un indicador de carga y no permitir una segunda solicitud simultánea a `GET /api/me`. _(AUTH-03)_
4. WHILE el frontend React no esté inicializado, THE equipo SHALL inicializar el frontend con el alcance mínimo necesario para el inicio de sesión y el consumo de `GET /api/me`, documentando el comando de arranque y las variables de entorno requeridas. _(AUTH-01, CA-01)_
