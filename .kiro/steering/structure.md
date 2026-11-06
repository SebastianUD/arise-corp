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
│       │   ├── config/SecurityConfig.java       # cadena de filtros, validación JWT y CORS
│       │   └── controller/AuthController.java   # GET /api/me
│       ├── main/resources/
│       │   ├── application.properties
│       │   ├── static/                          # vacío; no se usa
│       │   └── templates/                       # vacío; no se usa
│       └── test/java/com/arise/corp/
│           ├── AriseBackendApplicationTests.java
│           ├── unit/                         # pruebas unitarias (organización propuesta)
│           └── integration/                  # pruebas PostgreSQL (organización propuesta)
├── frontend/                                # React; inicialización y tests pendientes [Aprobado]
├── supabase/migrations/                     # migraciones SQL versionadas              [Aprobado]
├── .github/workflows/                       # workflows CI/CD de GitHub Actions        [Aprobado; por crear]
│   ├── backend-ci.yml                       # CI backend y build de Docker (propuesto)
│   ├── frontend-ci.yml                      # CI frontend (propuesto)
│   ├── integration-tests.yml                # pruebas de integración (propuesto)
│   └── cd.yml                               # publicación/despliegue (propuesto)
├── docker-compose.yml                       # ejecución local de servicios del proyecto [Aprobado; por crear]
├── docs/def_MVP/MVP_Arise_Corp.md           # definición del MVP
├── .kiro/steering/                          # reglas permanentes (estos archivos)
├── .kiro/specs/                             # especificaciones SDD; aún no existe       [Aprobado]
├── .gitignore
└── README.md
```

- `.vscode/` e `.idea/` están ignorados por Git; no versionar configuración personal del IDE.
- `backend/src/main/resources/static/` y `templates/` no se usan: el frontend es una SPA independiente y el backend no renderiza vistas.

## Backend
### Organización de paquetes
- Actual [Implementado]: paquetes por capa técnica en la raíz: `com.arise.corp.config` y `com.arise.corp.controller`.
- Objetivo [Aprobado, MVP §9 y NF-04]: monolito modular con los módulos `login`, `crm`, `erp` y `sga`, y separación Controller → Service → Repository dentro de cada módulo.
- Distribución concreta de paquetes: DP-EST-01. Propuesta:

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

- No mover `SecurityConfig` ni `AuthController` sin aprobación. La especificación que cree el primer módulo debe resolver DP-EST-01 en su `design.md`.

### Nombres de clases y archivos
- Observado [Implementado]: PascalCase con sufijo de rol (`AuthController`, `SecurityConfig`). Las pruebas usan el sufijo `Tests` (`AriseBackendApplicationTests`) y están en el mismo paquete que la clase probada.
- Sufijos para servicios, repositorios y DTO: DP-EST-01. Idioma de los nombres de dominio: DP-EST-02.

### Estilo de código observado
- Indentación inconsistente: `AriseBackendApplication.java` y `AriseBackendApplicationTests.java` usan tabulaciones (generadas por Spring Initializr); `SecurityConfig.java` y `AuthController.java` usan 4 espacios. No hay `.editorconfig` ni formateador configurado (DP-EST-03).
- `@Bean` en clases `@Configuration` y DSL de lambdas para `HttpSecurity`.
- Comentarios y encabezados de sección de `application.properties` en español.

## Frontend [Aprobado, MVP §9]
React organizará el código en páginas, componentes compartidos, rutas y servicios HTTP dentro de `frontend/src/` (README §4). Las vistas previstas son Login, Dashboard, CRM, ERP y SGA. Distribución concreta de carpetas: DP-EST-04. Propuesta:

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
- `.github/workflows/`: workflows de GitHub Actions para CI de backend/frontend, pruebas de integración, construcción de imagen y CD cuando se apruebe el destino.
- No incluir archivos `.env` ni secretos dentro de la imagen Docker, del contexto de construcción ni en archivos YAML versionados.

## Pruebas
- Backend: pruebas unitarias en `backend/src/test/java/`; pruebas de integración con PostgreSQL aislado/efímero. La estructura exacta de paquetes se define en DP-EST-05.
- Frontend: pruebas unitarias y de integración junto a los componentes o bajo una carpeta `tests/`, según se apruebe al inicializar el frontend (DP-EST-04).
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
| Prueba de integración PostgreSQL | `backend/src/test/java/com/arise/corp/...` (paquetes por definir en DP-EST-05) |
| Prueba unitaria/de integración frontend | `frontend/src/` o `frontend/tests/` (DP-EST-04) |
| Dockerfile y exclusiones | `backend/Dockerfile`, `backend/.dockerignore` |
| Orquestación local de contenedores | `docker-compose.yml` |
| Automatización CI/CD | `.github/workflows/` |
| Documentación funcional | `docs/` |

## Decisiones pendientes de aprobación
- **DP-EST-01** Organización de paquetes del backend: confirmar la propuesta por módulo, la ubicación del código transversal (`config/`, `shared/`), si `AuthController` pasa al módulo `login`, y los sufijos de clases (propuesta: `*Controller`, `*Service`, `*Repository`; DTO `*Request` y `*Response`; entidades sin sufijo).
- **DP-EST-02** Idioma de los nombres: clases de dominio, tablas y columnas, rutas REST, campos JSON, valores de enumeraciones y textos de la interfaz. El código actual usa inglés para lo técnico (`AuthController`, `/api/me`), y los ejemplos de ramas del README usan español (`feat/crm-clientes`). Recomendación: sufijos técnicos en inglés (convención de Spring) y nombres de dominio en español, iguales al vocabulario del MVP (`Cliente`, `Contrato`, `Paquete`, `Dron`; `ClienteService`; `/api/crm/clientes`). Textos de la interfaz y mensajes de error dirigidos al usuario en español.
- **DP-EST-03** Estándar de estilo de código: indentación (recomendación: 4 espacios en Java, unificando los archivos con tabulaciones), archivo `.editorconfig`, inyección de dependencias por constructor (sin `@Autowired` en campos) y DTO como `record` de Java.
- **DP-EST-04** Estructura de carpetas del frontend: confirmar la propuesta anterior al inicializar `frontend/`, incluida la ubicación de pruebas unitarias y de integración.
- **DP-EST-05** Organización de pruebas backend: confirmar si se separan paquetes `unit/` e `integration/`, convenciones de nombres (`*Test`/`*IT`) y uso de perfiles de prueba. Propuesta: pruebas unitarias separadas de pruebas PostgreSQL de integración para permitir que CI ejecute cada suite de forma independiente.
