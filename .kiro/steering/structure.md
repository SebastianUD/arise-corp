# Estructura del proyecto

Etiquetas de estado: ver `product.md`. Las responsabilidades de cada capa se describen en `architecture.md`.

## Monorepo
```text
arise-corp/
├── backend/                                 # API REST Spring Boot (Maven)            [Implementado]
│   ├── .env                                 # secretos locales; ignorado por Git; no leer ni mostrar
│   ├── .env.example                         # plantilla versionada con valores ficticios
│   ├── .gitattributes                       # LF para mvnw, CRLF para *.cmd
│   ├── .mvn/wrapper/                        # configuración del Maven Wrapper
│   ├── HELP.md                              # enlaces de referencia generados por Spring Initializr
│   ├── pom.xml, mvnw, mvnw.cmd
│   ├── Dockerfile                            # imagen multi-stage del backend [Implementado]
│   ├── .dockerignore                        # excluye secretos y archivos innecesarios [Implementado]
│   ├── target/                              # salida del build; ignorado por Git
│   └── src/
│       ├── main/java/com/arise/corp/
│       │   ├── AriseBackendApplication.java     # clase principal (@SpringBootApplication)
│       │   ├── config/
│       │   │   ├── SecurityConfig.java          # cadena de filtros, matriz de roles y CORS
│       │   │   ├── PerfilJwtAuthenticationConverter.java # JWT → ROLE_<ROL> del perfil activo
│       │   │   └── ClockConfig.java             # Clock inyectable para auditoría
│       │   ├── login/                           # perfil y rol del usuario autenticado
│       │   │   ├── controller/
│       │   │   │   ├── AuthController.java      # GET /api/me
│       │   │   │   └── AdminPerfilController.java # PUT/PATCH /api/admin/usuarios/{id}
│       │   │   ├── entity/                      # PerfilUsuario y RolUsuario
│       │   │   ├── repository/                  # PerfilUsuarioRepository y UsuarioAuthRepository
│       │   │   ├── service/                     # PerfilUsuarioService y ResultadoPerfil
│       │   │   └── dto/                         # MeResponse, PerfilRequest, EstadoPerfilRequest, PerfilUsuarioResponse
│       │   ├── crm/                             # clientes, contratos y cuentas corporativas
│       │   │   ├── controller/                  # Cliente, Contrato, CuentaCorporativa y ResumenComercial
│       │   │   ├── service/                     # reglas, transacciones y auditoría de estado
│       │   │   ├── repository/                  # repositorios JPA y especificaciones de filtros
│       │   │   ├── entity/                      # Cliente, Contrato, CuentaCorporativa y enums
│       │   │   └── dto/                         # requests, responses y vistas por rol
│       │   └── shared/
│       │       ├── error/                       # Problem Details y handlers comunes
│       │       ├── normalizacion/               # NormalizadorTexto
│       │       ├── paginacion/                  # PaginaResponse y validación de parámetros
│       │       └── validacion/                  # validadores compartidos
│       ├── main/resources/
│       │   ├── application.properties
│       │   ├── static/                          # vacío; no se usa
│       │   └── templates/                       # vacío; no se usa
│       └── test/java/com/arise/corp/
│           ├── AriseBackendApplicationTests.java
│           ├── config/                       # pruebas del converter de autorización
│           ├── login/                        # pruebas de /api/me, admin y matriz de módulos
│           ├── crm/                          # pruebas *Tests en el paquete de la clase probada
│           └── integration/                  # pruebas PostgreSQL *IT (Failsafe)
├── frontend/                                # SPA React con acceso, perfil y vistas CRM
├── supabase/migrations/                     # migraciones de perfiles y CRM                [Implementado]
│   ├── 20261108000000_app_perfiles_usuario.sql
│   └── 20261111120000_app_crm_clientes_contratos.sql
├── .github/workflows/                       # workflows CI de GitHub Actions           [Implementado]
│   ├── backend-ci.yml                       # CI backend y build de Docker
│   ├── frontend-ci.yml                      # CI frontend
│   └── integration-tests.yml                # pruebas de integración con PostgreSQL efímero
├── docker-compose.yml                       # ejecución local del backend               [Implementado]
├── docs/def_MVP/MVP_Arise_Corp.md           # definición del MVP
├── .kiro/steering/                          # reglas permanentes (estos archivos)
├── .kiro/specs/                             # especificaciones SDD versionadas
├── .gitignore
└── README.md
```

- `.vscode/` e `.idea/` están ignorados por Git; no versionar configuración personal del IDE.
- `backend/src/main/resources/static/` y `templates/` no se usan: el frontend es una SPA independiente y el backend no renderiza vistas.

## Backend
### Organización de paquetes
- Actual [Implementado]: `com.arise.corp.config` para seguridad transversal, `com.arise.corp.login` con capas `controller`, `entity`, `repository`, `service` y `dto`, `com.arise.corp.crm` con la misma separación y `com.arise.corp.shared` para error, normalización, paginación y validación.
- Objetivo [Aprobado, MVP §9 y NF-04]: monolito modular con los módulos `login`, `crm`, `erp` y `sga`, y separación Controller → Service → Repository dentro de cada módulo.
- Distribución adoptada (DP-EST-01):

```text
com.arise.corp
├── AriseBackendApplication.java
├── config/           # configuración transversal: seguridad, CORS
├── shared/           # manejo global de errores y utilidades comunes
├── login/            # perfil y rol del usuario autenticado
├── crm/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── dto/
├── erp/              # misma estructura que crm
└── sga/              # misma estructura que crm
```

- La decisión DP-EST-01 sitúa `AuthController` y `AdminPerfilController` en `login.controller`; `SecurityConfig` y `PerfilJwtAuthenticationConverter` permanecen en `config`. El perfil empresarial, la autorización por rol y la administración de perfiles ya están implementados (tareas 16–17 de `001-foundation`). `UsuarioAuthRepository` consulta `auth.users` para validar que la identidad exista antes de crear un perfil.

### Nombres de clases y archivos
- Observado [Implementado]: PascalCase con sufijo de rol (`AuthController`, `SecurityConfig`). Las pruebas usan el sufijo `Tests` (`AriseBackendApplicationTests`) y están en el mismo paquete que la clase probada.
- Sufijos: `*Controller`, `*Service`, `*Repository`, `*Request`, `*Response`; entidades sin sufijo. Los nombres de dominio y de API usan español (DP-EST-01/02).

### Estilo de código observado
- El código nuevo usa 4 espacios, inyección por constructor y DTO `record` cuando corresponda. Se añade `.editorconfig`; código existente se normaliza al tocarlo, sin refactor ajeno al alcance (DP-EST-03).
- `@Bean` en clases `@Configuration` y DSL de lambdas para `HttpSecurity`.
- Comentarios y encabezados de sección de `application.properties` en español.

## Frontend [Aprobado, MVP §9]
La SPA organiza las vistas de acceso, perfil y CRM en `frontend/src/`. La sección 11.6 del README enumera las rutas disponibles por rol.

```text
frontend/
├── .env.example      # solo variables VITE_* públicas
└── src/
    ├── App.jsx                    # registro de rutas y agrupación por permiso
    ├── pages/                     # Login.jsx y Perfil.jsx
    │   └── crm/                   # clientes, contratos, cuentas corporativas y resumen
    ├── context/
    │   ├── AuthContext.jsx        # sesión de Supabase
    │   └── PerfilContext.jsx      # perfil y rol compartidos de GET /api/me
    ├── components/
    │   └── AppLayout.jsx          # cabecera privada, menú por rol y salida de sesión
    ├── routes/
    │   ├── ProtectedRoute.jsx     # comprueba la sesión
    │   └── RolRoute.jsx           # comprueba el rol antes de montar la vista
    ├── services/
    │   ├── apiClient.js           # cliente HTTP autenticado y errores de la API
    │   └── crmApi.js              # consultas y escrituras del CRM
    └── lib/
        ├── supabase.js            # cliente de autenticación
        ├── permisos.js            # menú, permisos y transiciones visibles
        ├── validacionCrm.js       # normalización y validación de formularios
        └── formato.js             # importes en COP, fechas e instantes en es-CO
```

## Docker e infraestructura de entrega [Implementado]
- `backend/Dockerfile`: construcción multi-stage (`eclipse-temurin:21-jdk` → `eclipse-temurin:21-jre`), usuario `arise` sin privilegios, solo `app.jar`, `EXPOSE 8080` y sin `ARG` ni `ENV` de configuración.
- `backend/.dockerignore`: excluye `.env`, `.env.*`, `target/`, `src/test/`, archivos del IDE, `*.log`, `HELP.md` y `.gitattributes`.
- `docker-compose.yml`: proyecto `arise-corp` con el servicio `backend` desde `./backend`, imagen `arise-backend:local`, `env_file` obligatorio con `./backend/.env` y puerto `127.0.0.1:8080:8080`; Supabase PostgreSQL sigue siendo externo y administrado.
- `.github/workflows/`: workflows de GitHub Actions para CI de backend/frontend, pruebas de integración y comprobación de la construcción de imagen. El proyecto se ejecuta en local; no hay CD.
- No incluir archivos `.env` ni secretos dentro de la imagen Docker, del contexto de construcción ni en archivos YAML versionados.

## Pruebas
- Backend: pruebas unitarias en `backend/src/test/java/`; pruebas de integración con PostgreSQL aislado/efímero. Las pruebas unitarias y de contexto usan el sufijo `Tests` y van en el paquete de la clase probada (práctica de `001-foundation`); el paquete `unit` no se usa. Las pruebas PostgreSQL usan el paquete `integration`, el sufijo `IT` y Maven Failsafe (DP-EST-05, D19).
- Frontend: pruebas unitarias y de integración junto a los componentes o bajo una carpeta `tests/`, junto a los módulos bajo `frontend/src/`.
- Los workflows de GitHub Actions ejecutan los comandos de prueba documentados por cada proyecto.

## Dónde va cada cosa
| Necesidad | Ubicación |
|---|---|
| Nueva funcionalidad | Primero la especificación en `.kiro/specs/<spec>/` (ver `sdd-workflow.md`); después el código |
| Endpoint REST | Controller del módulo; convenciones en `api-conventions.md` |
| Regla de negocio o transacción | Service del módulo |
| Acceso a datos | Repository del módulo (interfaz de Spring Data JPA) |
| Tabla, columna o restricción | Nueva migración en `supabase/migrations/`. Nunca mediante Hibernate. No editar una migración ya aplicada: crear una nueva |
| Seguridad, CORS u otra configuración transversal | `com.arise.corp.config` |
| Nueva variable de entorno | `backend/.env.example` con valor ficticio y documentación en el README |
| Prueba unitaria del backend | `backend/src/test/java/com/arise/corp/...` |
| Prueba de integración PostgreSQL | `backend/src/test/java/com/arise/corp/...` (`integration/*IT`) |
| Prueba unitaria/de integración frontend | `frontend/src/` o `frontend/tests/` |
| Dockerfile y exclusiones | `backend/Dockerfile`, `backend/.dockerignore` |
| Orquestación local de contenedores | `docker-compose.yml` |
| Automatización CI | `.github/workflows/` |
| Documentación funcional | `docs/` |

## Decisiones adoptadas
- **DP-EST-01:** paquetes por módulo (`login`, `crm`, `erp`, `sga`), cada uno con capas `controller`, `service`, `repository`, `entity`, `dto`; `config` para seguridad transversal y `shared` para errores/utilidades comunes. `AuthController` pasa a `login.controller`; `SecurityConfig` sigue en `config`. Sufijos: `*Controller`, `*Service`, `*Repository`, `*Request`, `*Response`; entidades sin sufijo.
- **DP-EST-02:** documentación y textos de interfaz en español. Nombres de dominio, tablas, columnas, rutas, JSON y enums en español y con términos del MVP; nombres técnicos de framework/clases utilitarias en inglés según convención (`SecurityConfig`, `Controller`). JSON camelCase; tablas/columnas snake_case.
- **DP-EST-03:** 4 espacios, `.editorconfig`, inyección por constructor y DTO `record` en Java 21 cuando sean DTO de datos; no reformatear archivos completos salvo que sea parte de la tarea.
- **DP-EST-04:** frontend organizado en `src/pages`, `components`, `routes`, `services`, `context`, `lib` y `test`; pruebas junto al módulo o archivo bajo `src/` con Vitest y Testing Library, siguiendo la estructura existente.
- **DP-EST-05:** pruebas backend con sufijo `Tests` en el paquete de la clase probada (práctica de `001-foundation`; el paquete `unit` no se usa) se ejecutan en Surefire; las pruebas de integración PostgreSQL van en `src/test/java/com/arise/corp/integration` con sufijo `IT`, se ejecutan en Failsafe y perfil aislado, con Testcontainers según DP-TEC-04 (D19).
