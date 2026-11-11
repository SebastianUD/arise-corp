# Stack tecnológico, configuración y comandos

Etiquetas de estado: ver `product.md`. Las reglas de seguridad están en `security.md` y las de Git en `sdd-workflow.md`.

## Stack
| Área | Tecnología | Versión / fuente | Estado |
|---|---|---|---|
| Lenguaje backend | Java | 21 (`java.version` en `pom.xml`) | [Implementado] |
| Framework backend | Spring Boot | 4.1.2 (`spring-boot-starter-parent`) | [Implementado] |
| Build | Maven Wrapper | wrapper 3.3.4, Maven 3.10.0 (`backend/.mvn/wrapper/maven-wrapper.properties`) | [Implementado] |
| API | Spring Web MVC | gestionada por Spring Boot | [Implementado] |
| Persistencia | Spring Data JPA / Hibernate | gestionada por Spring Boot | Dependencia [Implementado: entidad de perfil empresarial] |
| Seguridad | Spring Security + OAuth2 Resource Server | gestionada por Spring Boot | [Implementado] |
| Validación | Bean Validation (`spring-boot-starter-validation`) | gestionada por Spring Boot | Dependencia [Implementado]; aún sin uso |
| Base de datos | Supabase PostgreSQL vía JDBC y Session pooler | proyecto Supabase `arise-corp-dev` | [Implementado] |
| Autenticación | Supabase Auth (JWT firmados con ES256) | — | [Implementado] en backend y frontend |
| Frontend | React, Vite, Tailwind CSS, React Router, `@supabase/supabase-js` | versiones fijadas en `frontend/package.json` y `package-lock.json` (DP-TEC-01) | [Implementado: autenticación y perfil] |
| Contenedores | Docker y Docker Compose para empaquetar/ejecutar el backend | versiones por fijar en el proyecto | [Aprobado; implementación pendiente] |
| CI | GitHub Actions | `backend-ci.yml`, `integration-tests.yml` y `frontend-ci.yml`; `actions/checkout@v7`, `actions/setup-java@v6` y `actions/setup-node@v7` | [Implementado] |
| Herramientas | Kiro, Git, GitHub (`SebastianUD/arise-corp`) | — | [Implementado] |

## Dependencias del backend (`backend/pom.xml`)
Producción:
- `spring-boot-starter-webmvc`: controladores REST.
- `spring-boot-starter-data-jpa`: Spring Data JPA e Hibernate.
- `spring-boot-starter-security`: Spring Security.
- `spring-boot-starter-security-oauth2-resource-server`: validación de JWT.
- `spring-boot-starter-validation`: Bean Validation.
- `spring-boot-devtools` (runtime, opcional): recarga en desarrollo.
- `org.postgresql:postgresql` (runtime): driver JDBC.

Pruebas (scope `test`): `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-security-test`, `spring-boot-starter-security-oauth2-resource-server-test`, `spring-boot-starter-validation-test` y `com.h2database:h2`.

Plugin de build: `spring-boot-maven-plugin`.

### Reglas sobre dependencias
- Usar solo las dependencias declaradas. Toda dependencia nueva se justifica en el `design.md` de una especificación. Testcontainers está adoptado en DP-TEC-04 para las pruebas PostgreSQL; las demás dependencias no incluidas en el stack requieren decisión documentada antes de agregarse.
- No fijar versiones de dependencias que ya gestiona el parent de Spring Boot.

### Spring Boot 4.x
Spring Boot 4 trae Spring Framework 7, Spring Security 7, Hibernate 7 y Jackson 3. No generar código con APIs de Spring Boot 3 o anteriores:
- Usar paquetes `jakarta.*` (no `javax.*`).
- Jackson 3 usa los paquetes `tools.jackson.*`; las anotaciones siguen en `com.fasterxml.jackson.annotation`.
- Spring Security se configura solo con el DSL de lambdas, como en `SecurityConfig`. `WebSecurityConfigurerAdapter` y `.and()` ya no existen.
- En pruebas, usar `@MockitoBean` (`@MockBean` fue eliminado). Los starters de prueba son modulares; comprobar el paquete de cada anotación de prueba en la documentación de 4.1.2.
- Ante dudas, consultar la referencia de Spring Boot 4.1.2 enlazada en `backend/HELP.md`.

## Configuración del backend (`application.properties`) [Implementado]
- `spring.config.import=optional:file:./.env[.properties]`: carga `backend/.env` relativo al directorio de trabajo. Por eso Maven se ejecuta desde `backend/`.
- `spring.application.name=arise-backend`.
- Datasource: `jdbc:postgresql://${SUPABASE_DB_HOST}:${SUPABASE_DB_PORT}/${SUPABASE_DB_NAME}?sslmode=require` con `org.postgresql.Driver`.
- `spring.jpa.hibernate.ddl-auto=validate`: Hibernate valida la entidad contra las migraciones y nunca crea ni modifica el esquema (DP-TEC-03). `spring.jpa.properties.hibernate.default_schema=app` sitúa las entidades en el esquema privado (DP-ARQ-02).
- `spring.jpa.open-in-view=false`: la sesión de Hibernate no sigue abierta durante la serialización de la respuesta. Las relaciones perezosas se cargan dentro del servicio, en la transacción.
- JWT: issuer, JWKS, algoritmo ES256 y audiencia `authenticated` (detalle en `security.md`).
- Puerto: no configurado; Spring Boot usa 8080.

### Variables de entorno del backend
Plantilla versionada: `backend/.env.example`. Formato `CLAVE=valor`, sin comillas y sin `export`.

| Variable | Uso |
|---|---|
| `SUPABASE_DB_HOST` | Host del Session pooler. Copiarlo de Supabase Dashboard → Connect → Session pooler → JDBC; no construirlo a mano |
| `SUPABASE_DB_PORT` | Puerto (5432 en la plantilla) |
| `SUPABASE_DB_NAME` | Base de datos (`postgres`) |
| `SUPABASE_DB_USERNAME` | Usuario del Session pooler |
| `SUPABASE_DB_PASSWORD` | Contraseña de la base de datos (no la de la cuenta Supabase) |
| `SUPABASE_URL` | URL base del proyecto; se usa para el issuer y el JWKS |
| `CORS_ALLOWED_ORIGINS` | Orígenes frontend permitidos, separados por coma; valor local por defecto `http://localhost:5173` (DP-SEG-04) |

Toda variable nueva se agrega a `.env.example` con un valor ficticio/default y se documenta en el README. Frontend (DP-TEC-01 aprobada): `VITE_SUPABASE_URL`, `VITE_SUPABASE_PUBLISHABLE_KEY` y `VITE_API_BASE_URL`, con valores ficticios en `frontend/.env.example` y documentación en README §11.6.

## Base de datos
- Una única instancia de Supabase PostgreSQL; datos de negocio y perfiles en el esquema privado `app` (DP-ARQ-02).
- El esquema cambia solo mediante migraciones SQL versionadas con Supabase CLI en `supabase/migrations/` (DP-TEC-03). El perfil empresarial (`app.perfiles_usuario`) se creó en `001-foundation`; las tablas de CRM, ERP y SGA llegan en sus propias specs.
- Las restricciones (PK, FK, UNIQUE, NOT NULL, CHECK) se definen en PostgreSQL y las validaciones se repiten en el backend (NF-03).
- Migraciones versionadas con Supabase CLI, `<timestamp>_<nombre>.sql`; cada cambio se revisa, se aplica primero de forma controlada a `arise-corp-dev` y se conserva como historial. `spring.jpa.hibernate.ddl-auto=validate` detecta discrepancias, nunca modifica el esquema (DP-TEC-03).

## Pruebas [Aprobado]

### Backend
- Deben existir pruebas unitarias para servicios, reglas de negocio, validaciones y componentes aislables.
- Deben existir pruebas de seguridad/controlador para verificar al menos acceso sin token (401), token válido sin permiso (403) y token/autoridad autorizados (éxito), según `security.md`.
- Sebastián es responsable principal de las pruebas de integración con PostgreSQL: deben comprobar persistencia JPA, relaciones, restricciones, migraciones y transacciones con una instancia PostgreSQL aislada o efímera.
- No ejecutar pruebas destructivas sobre la base compartida `arise-corp-dev`, ni utilizar sus credenciales en CI.
- La estrategia aprobada DP-TEC-02 mantiene un perfil de pruebas aislado con H2 para pruebas de contexto/rápidas. H2 no reemplaza las pruebas de integración contra PostgreSQL real.
- Las pruebas de integración automatizadas usan PostgreSQL efímero con Testcontainers; H2 se mantiene para pruebas rápidas/contexto y no sustituye PostgreSQL (DP-TEC-04).

### Frontend
- Deben existir pruebas unitarias de componentes, hooks, validaciones y servicios según corresponda.
- Deben existir pruebas de integración del frontend para flujos de autenticación, rutas, estado y comunicación con la API.
- Paula es responsable de los tests y de documentar los comandos reproducibles.
- DP-TEC-01 aprobada: Vitest + React Testing Library, jsdom y jest-dom. Ejecutar `npm test` desde `frontend/`; `npm run test:watch` para modo observación.

### Integración y extremo a extremo
- Las pruebas de integración de backend con PostgreSQL son distintas de las pruebas unitarias con H2 y deben ejecutarse con datos aislados.
- Las pruebas de integración frontend/API pueden simular servicios externos para validar el comportamiento de la interfaz; los escenarios E2E con Supabase real se ejecutan aparte y con un entorno de prueba controlado.
- La suite automática no debe depender de la instancia compartida `arise-corp-dev` ni modificar sus datos.

### Estado observado al crear este documento
- Existe `AriseBackendApplicationTests.contextLoads()` con `@SpringBootTest`.
- H2 está declarado en alcance `test`; `backend/src/test/resources/application.properties` reemplaza la configuración principal sin importar `.env`. Las pruebas de contexto y `/api/me` se ejecutan con datos ficticios (DP-TEC-02).
- La cobertura unitaria e integración de los módulos aún está pendiente.

## Docker del backend [Aprobado; implementación pendiente]
- El backend debe distribuirse como una imagen Docker.
- Recomendación de construcción: Dockerfile multi-stage; compilar con Maven/JDK 21 y ejecutar el JAR con una imagen JRE 21.
- El contenedor ejecuta Spring Boot en el puerto 8080 y recibe la configuración de Supabase en tiempo de ejecución mediante variables de entorno.
- `backend/.env` no se copia a la imagen ni se incorpora al contexto Docker; excluirlo en `.dockerignore`. No pasar secretos como `ARG` ni escribirlos en capas.
- `docker-compose.yml` se utilizará para facilitar el arranque local del backend. Supabase PostgreSQL permanece como servicio administrado externo; no se dockeriza la base de datos de desarrollo.
- Sebastián es responsable del Dockerfile, `.dockerignore` y configuración Compose del backend.

## GitHub Actions y CI [Implementado]
- Davidson es responsable principal de crear y mantener los workflows de GitHub Actions.
- Los tres workflows viven en `.github/workflows/`: `backend-ci.yml` (job `backend-ci`), `integration-tests.yml` (job `integration-tests`) y `frontend-ci.yml` (job `frontend-ci`). Se ejecutan con `push` a cualquier rama, `pull_request` hacia `main` y `workflow_dispatch`, con `concurrency` y `cancel-in-progress: true` por workflow y ref, y `permissions: contents: read`.
- Acciones oficiales fijadas por versión mayor, verificadas en sus páginas de releases: `actions/checkout@v7`, `actions/setup-java@v6` (Temurin 21 con caché de Maven) y `actions/setup-node@v7` (versión de `frontend/.nvmrc` con caché de npm). `checkout` usa `persist-credentials: false`.
- Comandos: `backend-ci` ejecuta `./mvnw -B test` y `docker build --pull -t arise-backend:ci .` desde `backend/`; `integration-tests` ejecuta `./mvnw -B verify` desde `backend/`; `frontend-ci` ejecuta `npm ci`, `npm test`, `npm run build` (con `VITE_*` ficticias declaradas solo en ese paso) y `npm run check:secrets` desde `frontend/`.
- `backend/mvnw` está versionado con modo `100755` (`git update-index --chmod=+x`), para que `./mvnw` funcione en los runners Linux. El bit no se conserva al construir desde Windows, por eso el Dockerfile ejecuta `chmod +x mvnw`.
- Los workflows no necesitan secretos de la instancia compartida Supabase ni credenciales de `arise-corp-dev` para las pruebas unitarias o de integración.
- El proyecto se ejecuta en local (`localhost`): no hay despliegue continuo (CD), publicación de imágenes a un registro externo ni entornos de destino. El alcance de GitHub Actions se limita a CI (validación, pruebas y comprobación del build de la imagen Docker). Los checks `backend-ci`, `integration-tests` y `frontend-ci` son obligatorios en la protección de `main`.
- No incluir credenciales en YAML, Dockerfile, argumentos de build, logs ni artefactos.

## Comandos
Backend, en PowerShell y desde `backend/`:
```powershell
.\mvnw.cmd test                              # compila y ejecuta todas las pruebas
.\mvnw.cmd -Dtest=NombreDeLaClaseTests test  # ejecuta una sola clase de prueba
.\mvnw.cmd clean package                     # genera el jar en backend/target/ (también ejecuta las pruebas)
.\mvnw.cmd spring-boot:run                   # inicia la API en http://localhost:8080
```
- `spring-boot:run` es un proceso largo: lo ejecuta el desarrollador en su propia terminal y se detiene con `Ctrl + C`.
- En macOS/Linux se usa `./mvnw` con los mismos argumentos.
- `backend/.gitattributes` fija LF para `mvnw` y CRLF para `*.cmd`; no cambiar esos finales de línea.

Docker, desde la raíz del repositorio (cuando existan los archivos):
```powershell
docker compose build backend
docker compose up backend
```

Frontend, desde `frontend/` (Node.js 24 LTS >=24.14.0 y <25, npm 11): `npm ci`, `npm run dev` (puerto 5173 estricto), `npm test`, `npm run build`, `npm run check:secrets` y `npm run preview`. Detalle y variables públicas en README §11.6.

## Requisitos del entorno de desarrollo
Git, JDK 21, Docker Engine o Docker Desktop con Docker Compose, acceso al repositorio de GitHub y al proyecto Supabase `arise-corp-dev`, y PowerShell en Windows (README §11.1). Para el frontend, Node.js en una versión LTS compatible con Vite.

## Decisiones aprobadas
- **DP-TEC-01** Inicialización del frontend aprobada por Paula el 2026-11-07: JavaScript, npm 11, Node.js 24 LTS, React/Vite/Tailwind CSS, React Router DOM, Supabase JS, `fetch` nativo y Vitest/React Testing Library. Versiones en `frontend/package.json` y `package-lock.json`, configuración pública en `frontend/.env.example`; decisión concreta en `001-foundation/design.md`, D5. Las tareas 4–14 de `001-foundation` ya implementaron y verificaron la base, login y perfil; la comprobación con una cuenta real sigue como tarea manual abierta.
- **DP-TEC-02** Estrategia de pruebas unitarias/contexto del backend: perfil de pruebas en `src/test/resources` con H2 (ya declarado) y valores ficticios para las propiedades de JWT, más pruebas de controladores con JWT simulado. Esta decisión no sustituye las pruebas de integración PostgreSQL.

## Decisiones adoptadas
- **DP-TEC-03:** Supabase CLI crea migraciones SQL con nombre `<timestamp>_<nombre>.sql`. Davidson prepara las migraciones y otro integrante las revisa antes de su aplicación. Davidson las aplica de forma controlada en `arise-corp-dev`: como regla general después del merge, y antes del PR final cuando la validación extremo a extremo de una spec necesita la migración y la migración es aditiva (solo crea objetos nuevos y ajusta la seguridad de esos objetos), una vez revisada y validada en PostgreSQL efímero. Nunca se editan migraciones aplicadas. `ddl-auto=validate`; no se usa `update` ni `create`.
- **DP-TEC-04:** Testcontainers con PostgreSQL para la suite de integración automatizada en local y CI. H2 queda para contexto/pruebas rápidas. No conectar las pruebas a `arise-corp-dev` ni usar sus credenciales.