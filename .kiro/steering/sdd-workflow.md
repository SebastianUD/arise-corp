# Flujo de trabajo SDD y colaboración

Etiquetas de estado: ver `product.md`.

## Equipo
Integrantes: Davidson Sánchez Gordillo, Paula Isabella Martínez Escalante y Sebastián Pardo Tirado.

Responsabilidades acordadas por el equipo.

| Integrante | Área principal | Responsabilidades |
|---|---|---|
| Sebastián Pardo Tirado | Backend / seguridad / CRM / Docker | Estructura Spring Boot, JWT y Spring Security, convenciones de API, manejo global de errores, CRM, facturación básica del ERP, Dockerfile y Compose del backend, pruebas de integración con PostgreSQL |
| Davidson Sánchez Gordillo | Backend / datos / ERP y SGA / CI | Modelo de datos y migraciones, entidades y repositorios, ERP (empleados y proveedores), SGA, pruebas unitarias de sus servicios y configuración/mantenimiento de GitHub Actions para CI |
| Paula Isabella Martínez Escalante | Frontend | React, Vite y Tailwind CSS, rutas, componentes compartidos, Supabase Auth, cliente HTTP, interfaces de los módulos, pruebas unitarias y de integración del frontend |

### Responsabilidades transversales de calidad y entrega [Aprobado]
- **Sebastián:** responsable principal de dockerizar el backend y de diseñar/ejecutar las pruebas de integración contra PostgreSQL. Estas pruebas deben utilizar una instancia aislada o efímera; no deben modificar la base compartida `arise-corp-dev`.
- **Davidson:** responsable principal de los workflows de GitHub Actions: CI del backend y frontend, ejecución automatizada de pruebas y comprobación de la construcción de la imagen Docker del backend. El proyecto se ejecuta en local; no hay proceso de CD ni publicación de imágenes a un registro externo.
- **Paula:** responsable de las pruebas unitarias y de integración del frontend y de asegurar que sus comandos de validación se ejecuten en GitHub Actions.
- **Los tres:** acuerdan criterios de aceptación, contratos de API y escenarios de extremo a extremo; cada responsable entrega comandos reproducibles y documentación para que Davidson los incorpore al pipeline.

## Principios [Aprobado]
- No se programa una funcionalidad sin una especificación con requisitos, diseño y tareas revisados y aprobados.
- Las especificaciones aprobadas son la referencia común para frontend y backend.
- Un cambio de alcance o de comportamiento actualiza primero la especificación y después el código.
- Se crea una rama por especificación. Los requisitos, diseño, tareas, steering relacionado y código se registran con commits en esa rama durante el trabajo. No se abre PR por tarea, commit o actualización intermedia; se abre un único PR cuando la spec está completa, para revisión e integración en `main`.
- La especificación y el código se mantienen sincronizados en la misma rama: los cambios de comportamiento actualizan primero los documentos pertinentes y se incluyen en el PR final de la spec.
- Los steering files son las reglas permanentes del proyecto. Cualquier integrante puede proponer y registrar los ajustes necesarios en la rama de la spec a la que afectan; no se abre un PR separado para cambios de steering. Se revisan e integran junto con la spec terminada.

## Ciclo de cada funcionalidad [Aprobado, README §13]
| Paso | Actividad | Resultado |
|---|---|---|
| 1. Requisitos | Historias de usuario y criterios de aceptación verificables, citando los identificadores del MVP (AUTH-xx, CRM-xx, ERP-xx, SGA-xx, NF-xx, CA-xx). Incluir casos de validación, error y permisos | `requirements.md` |
| 2. Aclaración | Resolver con el equipo ambigüedades y casos límite. Registrar lo decidido en el documento | `requirements.md` actualizado |
| 3. Diseño | Contratos de la API (`api-conventions.md`), modelo de datos y migraciones, roles por endpoint (`security.md`), vistas y estados de la interfaz, nuevas dependencias y resolución de las decisiones `DP-*` que afecten a la funcionalidad | `design.md` |
| 4. Tareas | Tareas pequeñas, con responsable, área (backend, frontend o base de datos) y requisitos que cubren | `tasks.md` |
| 5. Implementación | Ejecutar las tareas con Kiro y revisar el código generado | Código y pruebas |
| 6. Validación | Ejecutar las pruebas y comprobar cada criterio de aceptación | Pruebas en verde y criterios verificados |
| 7. Actualización | Ajustar la especificación si cambiaron los requisitos o el diseño durante el desarrollo | Especificación coherente con el código |

Cada fase (requisitos, diseño, tareas) se revisa y aprueba antes de pasar a la siguiente. Esa revisión puede ocurrir dentro de la rama; el PR se reserva para cuando la spec y sus tareas estén completas.

## Especificaciones en Kiro
- Cada especificación vive en `.kiro/specs/<nombre>/` con `requirements.md`, `design.md` y `tasks.md`, redactados en español.
- La primera especificación es `001-foundation` [Aprobado, README §13]: autenticación e integración técnica. Su objetivo es la integración vertical: inicio de sesión en React, JWT validado en Spring Boot y respuesta de un endpoint protegido (MVP §13).
- Orden de las especificaciones siguientes: `architecture.md`, sección "Orden de construcción".
- Las carpetas usan `NNN-nombre-en-kebab-case` (DP-SDD-03).
- No crear ni modificar especificaciones fuera de este flujo. Todo cambio de la spec y su implementación se confirma en su rama; el PR se abre una sola vez al completar el alcance de esa spec.

## División del trabajo entre frontend y backend
- El `design.md` fija el contrato de la API antes de que cualquiera de los dos lados lo implemente. Así, frontend y backend pueden avanzar en paralelo sobre el mismo contrato.
- Cada tarea de `tasks.md` indica su área (backend, frontend o base de datos), su responsable y los requisitos que cubre.
- Backend: migraciones, entidades, repositorios, servicios, controladores, seguridad y pruebas unitarias. Sebastián coordina las pruebas de integración con PostgreSQL.
- Frontend: vistas, rutas, formularios, estados de carga y error, cliente HTTP, integración con Supabase Auth y pruebas unitarias/de integración de interfaz.
- Infraestructura: Sebastián mantiene Dockerfile, `.dockerignore` y Compose del backend; Davidson mantiene GitHub Actions y la automatización CI.
- Base de datos: los cambios de esquema llegan solo como migraciones versionadas, revisadas y aplicadas de forma controlada.
- Un cambio de contrato durante la implementación se acuerda entre frontend y backend y se registra en el `design.md` antes de programarlo. Se confirma en la rama actual; no genera un PR aparte.

## Uso de Kiro
- Kiro carga estos steering files en cada interacción. Si una regla cambia, se actualiza el archivo correspondiente.
- Ejecutar las tareas de `tasks.md` de una en una y revisar el diff antes de aceptarlo. Quien acepta el código generado por Kiro es responsable de él como si lo hubiera escrito (README §13, paso 5).
- Pedir a Kiro que cite los requisitos que implementa cada cambio y que ejecute las pruebas aplicables (`.\mvnw.cmd test` desde `backend/` y los comandos npm definidos para `frontend/`).
- Kiro no debe:
  - leer ni mostrar archivos `.env` ni recibir secretos en los mensajes;
  - implementar una decisión `DP-*` pendiente como si fuera definitiva; debe preguntar;
  - hacer cambios fuera del alcance de la tarea (refactorizaciones, nuevas funcionalidades o nuevas dependencias) sin aprobación;
  - modificar el esquema de la base fuera de una migración.
- Kiro puede ayudar a revisar Pull Requests, pero no sustituye la revisión de otro integrante.

## Git y GitHub
[Aprobado, README §14]
- Repositorio: `https://github.com/SebastianUD/arise-corp`.
- `main` es la rama estable e integrada.
- **Especificaciones:** cada spec tiene una rama propia. Todos sus cambios se confirman ahí y el PR se abre únicamente al completar la spec. Los cambios de steering se confirman en la rama de la spec activa a la que afectan; no generan PR independiente.
- **Implementación:** los commits de código, documentación, pruebas y migraciones de una funcionalidad van a la rama de su spec, creada desde `main`; no se crean ramas ni PR por tarea. Se usan prefijos `feat/`, `fix/`, `docs/`, `chore/`, `refactor/` o `test/` según el tipo de spec. Ejemplos del README: `feat/auth-foundation`, `feat/crm-clientes`, `fix/db-config`.
- No incluir `.env` ni credenciales en los commits.
- Mantener sincronizadas las especificaciones SDD y el código en la rama de la spec; abrir el PR de integración solo al completar sus tareas.

Las convenciones de ramas, commits, merge y protección de `main` están adoptadas en DP-SDD-03–05 al final de este documento.

## Pull Requests
Cada spec genera un único Pull Request al completarse. Ese PR incluye:
- Enlace a la especificación y a las tareas que implementa, con los identificadores de requisitos.
- Descripción del cambio y de cómo probarlo.
- Pruebas unitarias pertinentes aprobadas para el backend o frontend.
- Pruebas de integración aprobadas cuando el cambio toque persistencia, API, autenticación o comunicación entre componentes.
- Construcción Docker correcta cuando el cambio afecte al backend o a su imagen.
- Workflows CI correspondientes en estado exitoso.
- Pruebas de backend (`.\mvnw.cmd test`) y comandos npm documentados para el frontend.
- `.env.example` y README actualizados si hay variables de entorno nuevas.
- La migración correspondiente si cambia el esquema.
- La especificación o el steering file actualizados si cambian el comportamiento o las reglas.
- Ningún secreto en el código, los commits, la descripción ni las capturas.

## Revisión de código
- La revisión la hace al menos un integrante distinto del autor (README §14). El autor no aprueba su propio Pull Request.
- Quien revisa comprueba:
  - Coherencia con la especificación aprobada y con los steering files.
  - Seguridad: autenticación y roles en cada endpoint nuevo, validación de entradas, ausencia de secretos y errores sin información sensible.
  - Pruebas, incluidos los casos de error y de acceso denegado.
  - Separación por capas y módulos (`architecture.md`).
  - Ausencia de cambios fuera del alcance.
- Recomendación: cuando un cambio afecta al contrato de la API, que lo revise también el responsable del otro lado (frontend o backend).

## Definition of Done [Aprobado, MVP §15]
Una tarea o funcionalidad está terminada cuando:
1. Sus requisitos, criterios de aceptación y diseño están aprobados en la especificación, y los cambios de alcance se reflejaron primero en ella.
2. El código cumple las convenciones acordadas, fue revisado por otro integrante como parte del PR final de la spec y está integrado en Git.
3. Las pruebas unitarias del área afectada pasan (backend o frontend).
4. Las pruebas de integración definidas para la funcionalidad pasan; las que prueban PostgreSQL usan una base aislada/efímera y no la base compartida de desarrollo.
5. La construcción Docker del backend sigue funcionando cuando el cambio afecta al backend o a su configuración.
6. Los workflows CI aplicables de GitHub Actions terminan correctamente.
7. El flujo funciona de extremo a extremo cuando corresponda, la documentación y la configuración están actualizadas, y no hay secretos en el repositorio.

## Pruebas automatizadas y CI [Aprobado]
- Toda nueva funcionalidad backend incorpora pruebas unitarias de servicios/reglas y pruebas de controlador/seguridad cuando aplique.
- Toda nueva funcionalidad frontend incorpora pruebas unitarias de componentes, hooks o servicios según aplique, además de pruebas de integración entre interfaz, estado y cliente HTTP cuando corresponda.
- Las pruebas de integración con PostgreSQL son responsabilidad principal de Sebastián. Deben usar una instancia aislada o efímera y validar repositorios, relaciones, restricciones y transacciones; nunca escribir en `arise-corp-dev` desde CI.
- Davidson mantiene los workflows de GitHub Actions y conecta en ellos los comandos de backend, frontend, pruebas de integración y construcción Docker.
- Paula mantiene los comandos de pruebas frontend y colabora con Davidson para integrarlos al pipeline.
- Sebastián mantiene la construcción local Docker del backend y facilita a Davidson los comandos y requisitos para automatizarla.
- CI se ejecuta en los eventos de GitHub que el equipo configure y debe validar antes de considerar integrado un cambio. El proyecto se ejecuta en local (`localhost`); no hay despliegue continuo (CD) ni publicación de imágenes a un registro externo.

## Registro de decisiones
- Cada steering file registra al final las decisiones `DP-*` ya adoptadas y sus reglas resultantes.
- Una decisión nueva o cambio de alcance se propone en el `design.md` de la spec afectada y se registra en el steering correspondiente antes de implementarse.
- Si el equipo deja explícitamente un punto sin resolver, se conserva como pendiente y no se implementa como definitivo.

## Decisiones adoptadas
- **DP-SDD-03:** las specs usan directorios `NNN-nombre-en-kebab-case`; las ramas de código usan `feat/`, `fix/`, `docs/`, `chore/`, `refactor/` o `test/`.
- **DP-SDD-04:** commits Conventional Commits `tipo(alcance): descripción`, con los tipos `feat`, `fix`, `docs`, `chore`, `refactor`, `test`, alcances `auth`, `crm`, `erp`, `sga`, `db`, `frontend`, `docs`, `ci` y descripción en español en imperativo.
- **DP-SDD-05:** se trabaja con una rama por spec y commits durante su desarrollo. Solo se abre un PR al completar toda la spec; se integra mediante squash merge, con una aprobación de otro integrante y CI verde. `main` queda protegido con esos requisitos. El PR final usa la lista de comprobación de este documento; steering y documentación de esa spec se incluyen en la misma rama y PR.
- **DP-SDD-06:** estos steering files cumplen la función de constitución del MVP; no se crea `docs/constitution.md` duplicado.
- **DP-SDD-07:** `tasks.md` es la fuente de verdad de tareas. No se exige duplicarlas en GitHub Issues/Projects; si se usa tablero auxiliar, enlaza a la spec.
