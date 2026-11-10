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
│   ├── Dockerfile                            # imagen multi-stage del backend [Aprobado; por crear]
│   ├── .dockerignore                        # excluye secretos y archivos innecesarios [Aprobado; por crear]
│   ├── target/                              # salida del build; ignorado por Git
│   └── src/
│       ├── main/java/com/arise/corp/
│       │   ├── AriseBackendApplication.java     # clase principal (@SpringBootApplication)
│       │   ├── config/
│       │   │   ├── SecurityConfig.java          # cadena de filtros, matriz de roles y CORS
│       │   │   └── PerfilJwtAuthenticationConverter.java # JWT → ROLE_<ROL> del perfil activo
│       │   └── login/                           # perfil y rol del usuario autenticado
│       │       ├── controller/
│       │       │   ├── AuthController.java      # GET /api/me
│       │       │   └── AdminPerfilController.java # PUT/PATCH /api/admin/usuarios/{id}
│       │       ├── entity/                      # PerfilUsuario y RolUsuario
│       │       ├── repository/                  # PerfilUsuarioRepository y UsuarioAuthRepository
│       │       ├── service/                     # PerfilUsuarioService y ResultadoPerfil
│       │       └── dto/                         # MeResponse, PerfilRequest, EstadoPerfilRequest, PerfilUsuarioResponse
│       └── shared/error/                        # Problem Details y handlers comunes
│       ├── main/resources/
│       │   ├── application.properties
│       │   ├── static/                          # vacío; no se usa
│       │   └── templates/                       # vacío; no se usa
│       └── test/java/com/arise/corp/
│           ├── AriseBackendApplicationTests.java
│           ├── config/                       # pruebas del converter de autorización
│           ├── login/                        # pruebas de /api/me, admin y matriz de módulos
│           ├── unit/                         # pruebas unitarias
│           └── integration/                  # pruebas PostgreSQL
├── frontend/                                # React + Vite; login/perfil y pruebas implementados
├── supabase/migrations/                     # migración app.perfiles_usuario           [Implementado]
├── .github/workflows/                       # workflows CI de GitHub Actions           [Aprobado; por crear]
│   ├── backend-ci.yml                       # CI backend y build de Docker (propuesto)
│   ├── frontend-ci.yml                      # CI frontend (propuesto)
│   └── integration-tests.yml                # pruebas de integración (propuesto)
├── docker-compose.yml                       # ejecución local de servicios del proyecto [Aprobado; por crear]
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
- Actual [Implementado]: `com.arise.corp.config` para seguridad transversal y el módulo `com.arise.corp.login` con capas `controller`, `entity`, `repository`, `service` y `dto`.
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
React organizará el código en páginas, componentes compartidos, rutas y servicios HTTP dentro de `frontend/src/` (README §5). Las vistas previstas son Login, Dashboard, CRM, ERP y SGA. Distribución adoptada y ya inicializada:

```text
frontend/
├── .env.example      # solo variables VITE_* públicas
└── src/
    ├── pages/        # vistas: Login, Dashboard, CRM, ERP, SGA
    ├── components/   # componentes compartidos
    ├── routes/       # configuración de React Router y rutas protegidas
    ├── services/     # cliente HTTP hacia la API de Spring Boot
    └── lib/          # inicialización del cliente de Supabase (solo autenticación)
```

## Docker e infraestructura de entrega
- `backend/Dockerfile`: construcción multi-stage y ejecución de Spring Boot en un contenedor.
- `backend/.dockerignore`: excluye `.env`, `.env.*`, `target/`, archivos del IDE y otros elementos que no deben incorporarse a la imagen.
- `docker-compose.yml`: orquestación local del backend; Supabase PostgreSQL sigue siendo externo y administrado.
- `.github/workflows/`: workflows de GitHub Actions para CI de backend/frontend, pruebas de integración y comprobación de la construcción de imagen. El proyecto se ejecuta en local; no hay CD.
- No incluir archivos `.env` ni secretos dentro de la imagen Docker, del contexto de construcción ni en archivos YAML versionados.

## Pruebas
- Backend: pruebas unitarias en `backend/src/test/java/`; pruebas de integración con PostgreSQL aislado/efímero. Las pruebas unitarias usan el paquete `unit` y sufijo `Tests`; las pruebas PostgreSQL usan `integration`, sufijo `IT` y Maven Failsafe (DP-EST-05).
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
- **DP-EST-05:** pruebas backend bajo `src/test/java/com/arise/corp/unit` y `integration`; unitarias/contexto con sufijo `Tests` se ejecutan en Surefire, integración PostgreSQL con sufijo `IT` se ejecuta en Failsafe y perfil aislado, con Testcontainers según DP-TEC-04.
