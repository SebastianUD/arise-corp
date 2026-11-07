# Stack tecnológico, configuración y comandos

Etiquetas de estado: ver `product.md`. Las reglas de seguridad están en `security.md` y las de Git en `sdd-workflow.md`.

## Stack
| Área | Tecnología | Versión / fuente | Estado |
|---|---|---|---|
| Lenguaje backend | Java | 21 (`java.version` en `pom.xml`) | [Implementado] |
| Framework backend | Spring Boot | 4.1.2 (`spring-boot-starter-parent`) | [Implementado] |
| Build | Maven Wrapper | wrapper 3.3.4, Maven 3.10.0 (`backend/.mvn/wrapper/maven-wrapper.properties`) | [Implementado] |
| API | Spring Web MVC | gestionada por Spring Boot | [Implementado] |
| Persistencia | Spring Data JPA / Hibernate | gestionada por Spring Boot | Dependencia [Implementado]; aún sin entidades |
| Seguridad | Spring Security + OAuth2 Resource Server | gestionada por Spring Boot | [Implementado] |
| Validación | Bean Validation (`spring-boot-starter-validation`) | gestionada por Spring Boot | Dependencia [Implementado]; aún sin uso |
| Base de datos | Supabase PostgreSQL vía JDBC y Session pooler | proyecto Supabase `arise-corp-dev` | [Implementado] |
| Autenticación | Supabase Auth (JWT firmados con ES256) | — | [Implementado] en el backend |
| Frontend | React, Vite, Tailwind CSS, React Router, `@supabase/supabase-js` | versiones por definir (DP-TEC-01) | [Aprobado] |
| Contenedores | Docker y Docker Compose para empaquetar/ejecutar el backend | versiones por fijar en el proyecto | [Aprobado; implementación pendiente] |
| CI | GitHub Actions | workflows por crear | [Aprobado; implementación pendiente] |
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
- Usar solo las dependencias declaradas. Toda dependencia nueva (backend o frontend) se propone en el `design.md` de una especificación y se agrega solo después de que el equipo la apruebe. Esto incluye librerías como Lombok, MapStruct, springdoc-openapi, Flyway, Liquibase o Testcontainers. Testcontainers es una propuesta para las pruebas de integración PostgreSQL, no una dependencia aprobada todavía.
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
- `spring.jpa.hibernate.ddl-auto=none`: Hibernate nunca crea ni modifica el esquema.
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

Toda variable nueva se agrega a `.env.example` con un valor ficticio y se documenta en el README. Las variables del frontend todavía no están definidas (DP-TEC-01).

## Base de datos
- Una única instancia de Supabase PostgreSQL, con separación lógica por tablas y relaciones [Aprobado].
- El esquema cambia solo mediante migraciones SQL versionadas en `supabase/migrations/` [Aprobado]. La carpeta está vacía; todavía no hay tablas de negocio.
- Las restricciones (PK, FK, UNIQUE, NOT NULL, CHECK) se definen en PostgreSQL y las validaciones se repiten en el backend (NF-03).
- Herramienta y nomenclatura de migraciones: DP-TEC-03.

## Pruebas [Aprobado como requisito; herramientas específicas parcialmente pendientes]

### Backend
- Deben existir pruebas unitarias para servicios, reglas de negocio, validaciones y componentes aislables.
- Deben existir pruebas de seguridad/controlador para verificar al menos acceso sin token (401), token válido sin permiso (403) y token/autoridad autorizados (éxito), según `security.md`.
- Sebastián es responsable principal de las pruebas de integración con PostgreSQL: deben comprobar persistencia JPA, relaciones, restricciones, migraciones y transacciones con una instancia PostgreSQL aislada o efímera.
- No ejecutar pruebas destructivas sobre la base compartida `arise-corp-dev`, ni utilizar sus credenciales en CI.
- La estrategia aprobada DP-TEC-02 mantiene un perfil de pruebas aislado con H2 para pruebas de contexto/rápidas. H2 no reemplaza las pruebas de integración contra PostgreSQL real.
- Testcontainers con PostgreSQL es la opción recomendada para las pruebas de integración automatizadas, pero su incorporación requiere resolver DP-TEC-04 y aprobar la dependencia.

### Frontend
- Deben existir pruebas unitarias de componentes, hooks, validaciones y servicios según corresponda.
- Deben existir pruebas de integración del frontend para flujos de autenticación, rutas, estado y comunicación con la API.
- Paula es responsable de los tests y de documentar los comandos reproducibles.
- Runner y librerías de pruebas frontend se deciden en DP-TEC-01; propuesta inicial: Vitest y React Testing Library.

### Integración y extremo a extremo
- Las pruebas de integración de backend con PostgreSQL son distintas de las pruebas unitarias con H2 y deben ejecutarse con datos aislados.
- Las pruebas de integración frontend/API pueden simular servicios externos para validar el comportamiento de la interfaz; los escenarios E2E con Supabase real se ejecutan aparte y con un entorno de prueba controlado.
- La suite automática no debe depender de la instancia compartida `arise-corp-dev` ni modificar sus datos.

### Estado observado al crear este documento
- Existe `AriseBackendApplicationTests.contextLoads()` con `@SpringBootTest`.
- H2 está declarado en alcance `test`; la separación de perfiles/configuración de pruebas debe completarse según DP-TEC-02.
- La cobertura unitaria e integración de los módulos aún está pendiente.

## Docker del backend [Aprobado; implementación pendiente]
- El backend debe distribuirse como una imagen Docker.
- Recomendación de construcción: Dockerfile multi-stage; compilar con Maven/JDK 21 y ejecutar el JAR con una imagen JRE 21.
- El contenedor ejecuta Spring Boot en el puerto 8080 y recibe la configuración de Supabase en tiempo de ejecución mediante variables de entorno.
- `backend/.env` no se copia a la imagen ni se incorpora al contexto Docker; excluirlo en `.dockerignore`. No pasar secretos como `ARG` ni escribirlos en capas.
- `docker-compose.yml` se utilizará para facilitar el arranque local del backend. Supabase PostgreSQL permanece como servicio administrado externo; no se dockeriza la base de datos de desarrollo.
- Sebastián es responsable del Dockerfile, `.dockerignore` y configuración Compose del backend.

## GitHub Actions y CI [Aprobado; implementación pendiente]
- Davidson es responsable principal de crear y mantener los workflows de GitHub Actions.
- El CI debe instalar dependencias, ejecutar pruebas unitarias de backend y frontend, ejecutar las pruebas de integración automatizables, compilar ambos proyectos y comprobar que la imagen Docker del backend se construye correctamente.
- Los workflows no deben necesitar secretos de la instancia compartida Supabase para pruebas unitarias o de integración.
- El proyecto se ejecuta en local (`localhost`): no hay despliegue continuo (CD), publicación de imágenes a un registro externo ni entornos de destino. El alcance de GitHub Actions se limita a CI (validación, pruebas y comprobación del build de la imagen Docker).
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

Frontend: los comandos de instalación, pruebas y build se documentarán cuando se inicialice `frontend/` (DP-TEC-01).

## Requisitos del entorno de desarrollo
Git, JDK 21, Docker Engine o Docker Desktop con Docker Compose, acceso al repositorio de GitHub y al proyecto Supabase `arise-corp-dev`, y PowerShell en Windows (README §5). Para el frontend, Node.js en una versión LTS compatible con Vite.

## Decisiones pendientes de aprobación
- **DP-TEC-01** Inicialización del frontend: versiones de Node.js, React, Vite, Tailwind CSS, React Router y `@supabase/supabase-js`; gestor de paquetes; JavaScript o TypeScript; cliente HTTP (`fetch` nativo o una librería); herramientas de lint, formato y pruebas (propuesta: Vitest y React Testing Library); nombres de las variables `VITE_*` (URL de Supabase, clave publishable, URL base de la API) y su archivo `frontend/.env.example`. Se resuelve en la especificación que inicialice el frontend.
- **DP-TEC-03** Migraciones: (a) Supabase CLI (`supabase migration new <nombre>`, archivos `<timestamp>_<nombre>.sql`; el `.gitignore` ya excluye `supabase/.temp/`, que genera la CLI); (b) archivos SQL aplicados manualmente con una nomenclatura acordada. Definir también quién aplica las migraciones en `arise-corp-dev` y si `spring.jpa.hibernate.ddl-auto` pasa de `none` a `validate` para detectar al arrancar diferencias entre las entidades y el esquema.

## Decisiones aprobadas
- **DP-TEC-02** Estrategia de pruebas unitarias/contexto del backend: perfil de pruebas en `src/test/resources` con H2 (ya declarado) y valores ficticios para las propiedades de JWT, más pruebas de controladores con JWT simulado. Esta decisión no sustituye las pruebas de integración PostgreSQL.

## Decisiones pendientes de aprobación
- **DP-TEC-04** Tecnología para pruebas de integración PostgreSQL: propuesta principal Testcontainers con PostgreSQL efímero para que las pruebas sean reproducibles en local y GitHub Actions. Alternativa: una base PostgreSQL aislada y dedicada a pruebas. No utilizar `arise-corp-dev` compartida para pruebas automáticas.