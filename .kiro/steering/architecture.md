# Arquitectura

Etiquetas de estado: ver `product.md`. El diagrama general de componentes está en `README.md` §2; este archivo define responsabilidades y reglas.

## Estilo arquitectónico [Aprobado]
- Cliente-servidor de tres capas: presentación (SPA en React), lógica de negocio (API REST en Spring Boot) y datos (Supabase PostgreSQL).
- El frontend es una SPA independiente del backend. Solo se comunican mediante HTTP y JSON.
- El backend es un monolito modular: una sola aplicación Spring Boot, desplegada como una unidad, con los módulos `login`, `crm`, `erp` y `sga` separados lógicamente.
- Supabase aporta dos servicios: Auth (identidad y JWT) y PostgreSQL (persistencia).
- El backend Spring Boot se empaqueta y ejecuta en un contenedor Docker; Docker Compose facilita su ejecución local. Supabase permanece como servicio administrado externo y no se dockeriza.
- GitHub Actions automatiza CI: pruebas, compilación y construcción de la imagen. El CD/publicación/despliegue se completa cuando se aprueben el registro de imágenes y los entornos de destino.
- Quedan excluidos del MVP los microservicios, las colas de eventos, Kubernetes y cualquier arquitectura distribuida.

## Componentes y responsabilidades
| Componente | Responsabilidades | No debe | Estado |
|---|---|---|---|
| SPA React (Vite, Tailwind CSS, React Router) | Vistas (Login, Dashboard, CRM, ERP, SGA), navegación y rutas protegidas, estado de la sesión, formularios con validación y estados de carga/error, llamadas a la API con el token | Leer o escribir tablas de negocio en Supabase; decidir permisos por sí sola; contener las reglas de negocio | [Aprobado]; `frontend/` vacío |
| Supabase Auth | Inicio y cierre de sesión, emisión y renovación de JWT (ES256), publicación de las claves públicas (JWKS) | Almacenar datos de negocio | [Implementado] en el proyecto; integración con el frontend pendiente |
| API Spring Boot | Validar el JWT, autorizar por rol, validar entradas, aplicar reglas de negocio, gestionar transacciones, acceder a PostgreSQL, responder JSON con códigos HTTP coherentes | Renderizar vistas; delegar permisos al frontend | [Implementado] la base de seguridad; módulos pendientes |
| Supabase PostgreSQL | Persistencia e integridad (PK, FK, UNIQUE, NOT NULL, CHECK) | Ser modificado por Hibernate; exponer tablas de negocio al frontend | [Implementado] la conexión; sin tablas de negocio |

## Flujo de una petición autenticada
```mermaid
sequenceDiagram
    actor U as Usuario
    participant FE as SPA React
    participant AUTH as Supabase Auth
    participant API as Spring Boot
    participant DB as Supabase PostgreSQL
    U->>FE: credenciales
    FE->>AUTH: inicio de sesión (supabase-js)
    AUTH-->>FE: access token (JWT ES256) y refresh token
    FE->>API: petición HTTP/JSON con Authorization: Bearer <JWT>
    API->>AUTH: descarga de JWKS (se cachea)
    API->>API: valida firma, iss, aud y exp; autoriza; valida el DTO
    API->>DB: consulta JPA/JDBC (Session pooler, SSL)
    DB-->>API: datos
    API-->>FE: JSON y código HTTP
```

- Estado: [Implementado] desde la recepción de la petición hasta la validación del JWT (`GET /api/me`). Inicio de sesión en React, autorización por rol y acceso a tablas de negocio: [Aprobado], pendientes.
- El backend no guarda sesión (`SessionCreationPolicy.STATELESS`): cada petición se autentica solo con su JWT.

## Capas del backend [Aprobado, MVP §9]
Orden de una petición: filtro de Spring Security → Controller → Service → Repository → PostgreSQL.

| Capa | Responsabilidad | Reglas |
|---|---|---|
| Seguridad (`config`) | Autenticación JWT, CORS y reglas de acceso por ruta | Ver `security.md` |
| Controller | Recibir HTTP, validar el DTO de entrada con `@Valid`, delegar en el servicio y devolver el código HTTP y el DTO de respuesta | Sin reglas de negocio ni acceso directo a repositorios |
| Service | Reglas de negocio, autorización fina, transacciones (`@Transactional`), conversión entre entidades y DTO | No depende de tipos HTTP; lanza excepciones de dominio que el manejador global traduce a respuestas |
| Repository | Interfaces de Spring Data JPA | Sin reglas de negocio |
| Entity | Mapeo JPA de las tablas creadas por migraciones | No se expone en la API |

Justificación de que las entidades no salgan de la capa de servicio: con `spring.jpa.open-in-view=false`, serializar una entidad con relaciones perezosas fuera de la transacción falla. Además, exponerla publicaría campos internos y acoplaría el contrato de la API al esquema de la base.

## Módulos [Aprobado]
| Módulo | Contenido | Relaciones con otros módulos |
|---|---|---|
| `login` | Perfil y rol del usuario autenticado (AUTH-04); hoy, `GET /api/me` | Usa el `sub` del JWT emitido por Supabase Auth |
| `crm` | Clientes, cuentas corporativas, contratos | Es el dueño de los datos maestros del cliente |
| `erp` | Empleados, proveedores, facturas o movimientos financieros | Puede relacionar registros financieros con clientes del CRM (ERP-05) |
| `sga` | Drones, paquetes, inventario, historial de estados | Asocia paquetes a clientes y contratos del CRM (SGA-05) |

Todos los módulos dependen de la autorización por rol. La forma en que un módulo usa los datos de otro se define en DP-ARQ-01.

### Orden de construcción [Aprobado, MVP §13]
1. Constitución del proyecto: alcance, stack, arquitectura y convenciones (estos steering files).
2. Especificación `001-foundation`: autenticación e integración técnica.
3. Integración vertical: inicio de sesión en React, JWT validado en Spring Boot y respuesta de un endpoint protegido.
4. Datos y API: entidades, migraciones, contrato REST, códigos de respuesta y validaciones compartidas.
5. CRM primero, como patrón de referencia; después ERP y SGA con las mismas convenciones.
6. Flujo integrado cliente → contrato → paquete → estado/trazabilidad.
7. Validación final: pruebas, criterios de aceptación y documentación.

## Datos [Aprobado]
- Una única instancia de Supabase PostgreSQL con separación lógica por tablas y relaciones. La separación en esquemas puede evaluarse más adelante (DP-ARQ-02).
- Spring Boot es el único componente que lee y escribe tablas de negocio, mediante Spring Data JPA/Hibernate sobre JDBC, con el Session pooler y `sslmode=require`.
- React usa Supabase solo para autenticación.
- Supabase Auth gestiona los usuarios en su propio esquema (`auth`). No crear tablas ni modificar objetos en ese esquema. El backend identifica al usuario por el claim `sub` del JWT (UUID).
- El esquema se crea y modifica solo con migraciones (ver `tech.md`).
- Los cambios de estado del paquete generan un registro de historial (SGA-04). Las cantidades de inventario cambian solo mediante operaciones controladas (SGA-03).

## Contenedorización y entrega [Aprobado]
- La unidad contenedorizada inicial es el backend Spring Boot. La imagen se construye desde `backend/Dockerfile`; el proceso y las variables de entorno se describen en `tech.md`.
- El contenedor no incluye `.env`, credenciales, claves privadas ni archivos locales de configuración. Los secretos se inyectan en tiempo de ejecución.
- `docker-compose.yml` facilita la ejecución local del backend. No debe iniciar una base local como sustituto de Supabase, salvo que una especificación de pruebas defina explícitamente una PostgreSQL efímera o aislada.
- GitHub Actions valida el backend y frontend, ejecuta pruebas unitarias y de integración, y comprueba la construcción Docker. La publicación de imagen y despliegue pertenecen al CD y dependen de la aprobación del registro y hosting (DP-ARQ-03, DP-TEC-05).

## Estrategia de pruebas [Aprobado]
- Las pruebas unitarias verifican reglas y componentes aislados en backend y frontend.
- Las pruebas de integración backend deben verificar la interacción de repositorios/JPA con PostgreSQL real en una instancia aislada o efímera; no se ejecutan contra la base compartida de desarrollo.
- Las pruebas de integración frontend comprueban la colaboración entre componentes, estado de autenticación, cliente HTTP y contrato de API.
- Las pruebas de extremo a extremo validan los flujos críticos completos; su herramienta y automatización se definirán en la especificación correspondiente.
- GitHub Actions ejecuta las suites aplicables y bloquea la promoción/publicación si fallan los controles requeridos.

## Comunicación frontend–backend [Aprobado]
- API REST sobre HTTP con cuerpos JSON. Convenciones en `api-conventions.md`.
- El token de acceso de Supabase viaja en el encabezado `Authorization: Bearer <token>`. No se usan cookies de sesión.
- Desarrollo local: frontend en `http://localhost:5173` y API en `http://localhost:8080`, con CORS configurado en el backend (ver `security.md`).
- La URL base de la API se configurará con una variable `VITE_*` (DP-TEC-01).

## Decisiones pendientes de aprobación
- **DP-ARQ-01** Dependencias entre módulos. Propuesta: dependencias en un solo sentido (`erp` → `crm`, `sga` → `crm`; `crm` no depende de `erp` ni de `sga`); un módulo usa los datos de otro a través de su servicio, nunca de su repositorio. Decidir también si las entidades de módulos distintos se relacionan con asociaciones JPA (`@ManyToOne`) o solo por identificador con una FK en la base. Lo segundo mantiene los módulos más independientes.
- **DP-ARQ-02** Esquema de PostgreSQL para las tablas de negocio: `public` (expuesto por defecto por la Data API de Supabase) o un esquema propio no expuesto. Afecta a la seguridad; ver DP-SEG-05.
- **DP-ARQ-03** Despliegue y ambientes: hoy solo existe el entorno local con el proyecto Supabase `arise-corp-dev`. Faltan por definir el hosting del frontend y del backend, un proyecto Supabase para producción o demostración, los perfiles de Spring y los entornos y aprobaciones del CD. GitHub Actions para CI/CD y la dockerización del backend están aprobadas; el destino final de despliegue sigue pendiente.
