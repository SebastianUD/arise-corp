# Producto: Plataforma Arise Corp.

## Qué es
Arise Corp. es una empresa colombiana que desarrolla drones autónomos para el transporte y la entrega de paquetes (logística de última milla). Hoy sus áreas comercial, administrativa y de inventario operan por separado, lo que genera retrasos y dificulta el seguimiento de los procesos.

Este repositorio contiene el MVP académico (curso "Construcción de Software", equipo de tres integrantes) de una plataforma web que integra esos procesos en una sola aplicación.

Fuentes de verdad, en este orden:
1. `docs/def_MVP/MVP_Arise_Corp.md`: alcance del MVP (versión 1.0, documento de trabajo sujeto a validación del equipo).
2. `README.md`: visión general, configuración local y estado del proyecto.
3. `.kiro/steering/`: reglas permanentes de desarrollo (estos archivos).
4. `.kiro/specs/`: especificaciones aprobadas de cada funcionalidad.

## Etiquetas de estado (se usan en todos los steering files)
- **[Implementado]**: existe en el código del repositorio.
- **[Aprobado]**: decisión del equipo (README, documento del MVP o stack aprobado) que todavía no está implementada.
- **[Pendiente de aprobación]**: propuesta o vacío sin decidir. No implementarla como definitiva: plantearla al equipo y resolverla según `sdd-workflow.md`.

Las decisiones pendientes tienen un código `DP-<ÁREA>-NN` y se listan al final del archivo de su tema. Las reglas que se derivan directamente de una decisión aprobada o de la configuración actual se escriben como reglas, acompañadas de su justificación.

## Objetivo y resultado esperado del MVP
Demostrar un flujo administrativo completo, con acceso controlado y datos persistidos en PostgreSQL:

autenticar un usuario → consultar y registrar un cliente → asociar un contrato → registrar un paquete → cambiar su estado manualmente → consultar la información relacionada y su historial.

## Módulos y requisitos
Los identificadores provienen del documento del MVP y deben citarse en las especificaciones, el diseño, las tareas y los Pull Requests.

| Módulo | Alcance | Must | Should |
|---|---|---|---|
| Login y autorización | Inicio y cierre de sesión con Supabase Auth, sesión y rutas protegidas en el frontend, JWT validado en el backend, perfil/rol del usuario, errores claros | AUTH-01 a AUTH-05 | — |
| CRM | Clientes (crear, listar, consultar, editar, desactivar), contratos vinculados con estado, validación de campos y duplicados | CRM-01, CRM-03, CRM-04 | CRM-02 (cuentas corporativas), CRM-05 (resumen comercial) |
| ERP | Empleados, proveedores, facturas o movimientos financieros básicos (fecha, concepto, valor, estado) | ERP-01, ERP-02, ERP-03 | ERP-04 (panel financiero), ERP-05 (relación con clientes/proveedores) |
| SGA | Drones, paquetes, inventario con operaciones controladas, historial de estados del paquete, asociación paquete–cliente/contrato–dron | SGA-01 a SGA-05 | SGA-06 (indicadores) |

Must: imprescindible; el MVP no está listo si falta. Should: se hace si no compromete la autenticación, el flujo integrado ni los Must. Las prioridades son iniciales (DP-PRO-02).

### Requisitos no funcionales (resumen)
- **NF-01** Secretos y claves de servicio fuera del frontend y del repositorio.
- **NF-02** Permisos verificados en Spring Boot; ocultar botones o rutas en React no basta.
- **NF-03** Restricciones en PostgreSQL y validaciones también en el backend.
- **NF-04** Código separado por módulo y por capas; convenciones documentadas.
- **NF-05** Migraciones y especificaciones versionadas en Git.
- **NF-06** Formularios con campos obligatorios, mensajes de validación y estados de carga/error.
- **NF-07** Pruebas para reglas críticas y endpoints protegidos.

### Requisitos de ingeniería complementarios acordados por el equipo
Estos acuerdos complementan el alcance funcional del MVP y deberán reflejarse en sus especificaciones y en la documentación de entrega. No sustituyen los requisitos funcionales ni los NF-01 a NF-07 del documento del MVP.

- **ENG-01 — Contenedorización:** el backend Spring Boot debe poder construirse y ejecutarse como imagen Docker, recibiendo la configuración sensible en tiempo de ejecución.
- **ENG-02 — Pruebas unitarias:** el backend y el frontend deben contar con pruebas unitarias automatizadas para la lógica y componentes relevantes.
- **ENG-03 — Pruebas de integración:** deben verificarse las integraciones relevantes del backend con PostgreSQL y del frontend con sus servicios/API. Las pruebas no deben modificar la base compartida de desarrollo.
- **ENG-04 — Automatización CI:** GitHub Actions debe automatizar las validaciones, pruebas y builds. El proyecto se ejecuta en local (`localhost`): no hay despliegue continuo (CD) ni publicación de imágenes a un registro externo.

### Criterios globales de aceptación (resumen)
- **CA-01** Ejecutable con instrucciones documentadas y variables de entorno.
- **CA-02** Inicio/cierre de sesión; el backend rechaza tokens inválidos o vencidos.
- **CA-03** Permisos por rol aplicados en el backend, con pruebas de acceso permitido y denegado.
- **CA-04** Operaciones mínimas de cada módulo con datos persistidos.
- **CA-05** Flujo cliente → contrato → paquete → cambio de estado/trazabilidad demostrable.
- **CA-06** Códigos HTTP coherentes y errores comprensibles sin información sensible.
- **CA-07** Especificación, diseño, tareas, pruebas y documentación revisados por el equipo.

## Roles iniciales [Pendiente de aprobación, DP-PRO-01]
| Rol propuesto | Acceso inicial | Módulo |
|---|---|---|
| Administrador | Acceso administrativo y gestión de perfiles/roles autorizados | Todos |
| Comercial | Clientes, cuentas y contratos | CRM |
| Finanzas / RR. HH. | Empleados, proveedores y registros financieros básicos | ERP |
| Operaciones | Drones, paquetes, inventario y trazabilidad | SGA |

Cómo se almacenan y aplican los roles: ver `security.md`.

## Reglas de alcance [Aprobado]
- CRM: operaciones CRUD y seguimiento básico de estado; sin automatizaciones comerciales avanzadas. Los clientes se desactivan, no se eliminan (CRM-01).
- Los estados de contratos son simples y se definen en su especificación (ejemplos del MVP: borrador, activo, finalizado, cancelado).
- Búsquedas, filtros y paginación: solo lo necesario para que las listas sean utilizables.
- ERP: gestión básica de registros y consultas. Sin libro mayor, impuestos, nómina, conciliación bancaria ni cierres contables.
- SGA: la asignación de drones y los cambios de estado son manuales. Cada cambio relevante de estado del paquete queda registrado (SGA-04).
- Los datos maestros del cliente no se duplican entre módulos; se relacionan.
- Campos, estados, validaciones, permisos y casos de error de cada funcionalidad se definen en su especificación. No suponerlos.

## Fuera del alcance del MVP [Aprobado]
Control autónomo de vuelo, navegación, telemetría o conexión con drones físicos; IA para rutas o demanda; pasarelas de pago, comercio electrónico, mensajería externa u otras integraciones con terceros; contabilidad completa, nómina e impuestos; aplicación móvil nativa; microservicios, Kubernetes, colas de eventos o arquitectura distribuida; analítica avanzada y notificaciones multicanal; multiempresa o multitenant.

No construir nada de esta lista sin aprobación explícita del equipo.

## Estado actual del proyecto
Esta sección es la referencia única del avance. Actualizarla cuando una funcionalidad cambie de estado.

[Implementado]
- Backend Spring Boot 4.1.2 con Java 21 y Maven Wrapper.
- Conexión a Supabase PostgreSQL mediante Session pooler (verificada según el README).
- `.env` local, plantilla `backend/.env.example` y reglas de `.gitignore`.
- Spring Security como OAuth2 Resource Server que valida JWT de Supabase Auth (ES256, JWKS, issuer y audiencia).
- Endpoint protegido de prueba `GET /api/me`.
- CORS para el frontend de desarrollo en `http://localhost:5173`.
- Validación JWT configurada en Spring Security.

[Aprobado, pendiente de implementar]
- Especificación `001-foundation` (autenticación e integración técnica).
- Prueba de autenticación extremo a extremo con un usuario real.
- Migraciones iniciales del esquema en `supabase/migrations/`.
- Inicialización del frontend React y su conexión con Supabase Auth y la API.
- Perfil y rol del usuario, y autorización por rol (AUTH-04).
- Manejo global de errores de la API.
- Módulos CRM, ERP y SGA, y vistas de resumen.

## Idioma
- Documentación, especificaciones y steering files: español (práctica de todo el repositorio).
- Identificadores de código, base de datos, rutas, JSON y textos de la interfaz: ver DP-EST-02 en `structure.md`.

## Decisiones pendientes de aprobación
- **DP-PRO-01** Validar los roles propuestos y definir la matriz de permisos por módulo y operación (MVP §5). La matriz debe resolver las lecturas entre módulos; por ejemplo, Operaciones necesita consultar clientes y contratos para asociarlos a un paquete (SGA-05), y Finanzas puede necesitar clientes para ERP-05. Definir también si un usuario puede tener más de un rol.
- **DP-PRO-02** Confirmar las prioridades Must/Should en la primera sesión de definición del MVP (MVP §12).
