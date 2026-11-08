**ARISE CORP.**

Definición del  
Producto Mínimo Viable (MVP)

Plataforma web integrada para la gestión de CRM, ERP y SGA

> **Propósito del documento**
>
> Delimitar el alcance de la primera versión funcional de Arise Corp., acordar qué se desarrollará y qué quedará fuera del MVP, y proporcionar una base común para las especificaciones SDD y la planificación de tareas en Kiro (opcional).

## Equipo del proyecto

| **Integrante**                    | **Identificación** |
|-----------------------------------|--------------------|
| Davidson Sánchez Gordillo         | 20231020183        |
| Paula Isabella Martínez Escalante | 20241020019        |
| Sebastián Pardo Tirado            | 20221020121        |

*Versión 1.0 \| 9 de octubre de 2026 \| Documento de trabajo sujeto a validación del equipo*

# 1. Contexto y problema

Arise Corp. es una empresa colombiana orientada al desarrollo de drones autónomos para el transporte y la entrega de paquetes. Su propuesta busca mejorar la logística de última milla mediante soluciones eficientes, seguras y confiables.

La constitución de la empresa identifica como problema la operación separada de las áreas comerciales, administrativas y de inventario. La falta de información integrada puede generar retrasos y dificulta el seguimiento de los procesos de negocio. La plataforma propuesta busca centralizar la información y articular los módulos CRM, ERP y SGA, complementados por autenticación y autorización de usuarios.

# 2. Objetivo del MVP

Construir una primera versión web funcional que permita a los usuarios autorizados gestionar información básica de clientes y contratos, recursos administrativos y registros de inventario y paquetes, dentro de una plataforma integrada. El MVP debe demostrar el flujo esencial de información entre módulos, con acceso controlado y datos persistidos en una base PostgreSQL.

# 3. Resultado esperado

> **Criterio central**
>
> Al finalizar el MVP, el equipo debe poder demostrar un flujo administrativo completo: autenticar un usuario, consultar y registrar un cliente, asociar un contrato, registrar un paquete, gestionar su estado y consultar la información relacionada desde la aplicación web.

# 4. Principios para delimitar el alcance

- Priorizar funcionalidades demostrables de gestión; no automatizar procesos que todavía no se han especificado.

- Usar una única instancia de PostgreSQL en Supabase; las tablas de negocio y perfiles se alojan en el esquema privado `app`, no expuesto por la Data API, con relaciones e integridad referencial.

- Mantener React separado de Spring Boot. React consume una API REST; Spring Boot concentra las reglas de negocio y accede a los datos.

- Desarrollar un monolito modular: Login/seguridad, CRM, ERP y SGA permanecen dentro de una sola aplicación backend.

- Aplicar SDD: revisar especificaciones, diseño y tareas antes de implementar cada funcionalidad; actualizar la especificación cuando se apruebe un cambio de comportamiento.

# 5. Usuarios y permisos iniciales

Se adoptan roles sencillos para la autorización por módulo del MVP. La matriz concreta y sus lecturas cruzadas se documentan en `.kiro/steering/product.md`.

| **Rol**            | **Acceso inicial**                                                            | **Modulo Asociado** |
|--------------------|-------------------------------------------------------------------------------|---------------------|
| Administrador      | Acceso administrativo a los módulos y gestión de perfiles/roles autorizados.  | Acceso Total        |
| Comercial          | Consulta y gestión de clientes, cuentas y contratos.                          | CRM                 |
| Finanzas / RR. HH. | Consulta y gestión de empleados, proveedores y registros financieros básicos. | ERP                 |
| Operaciones        | Consulta y gestión de drones, paquetes, inventario y trazabilidad.            | SGA                 |

Cada usuario tiene un solo rol activo. El Administrador gestiona los perfiles. Comercial gestiona CRM; Finanzas / RR. HH. gestiona ERP y solo consulta clientes/proveedores del CRM para ERP-05; Operaciones gestiona SGA y solo consulta clientes/contratos necesarios para SGA-05. Las lecturas cruzadas no permiten modificar los datos de otro módulo. Spring Boot aplica la matriz; un perfil ausente o inactivo se deniega.

# 6. Alcance funcional del MVP

Las funciones siguientes constituyen la propuesta de alcance mínimo. Las especificaciones detalladas definirán campos, estados, validaciones, permisos y casos de error antes de iniciar su implementación.

## 6.1. Login y autorización

| **ID**  | **Funcionalidad mínima**                                                                   | **Prioridad** |
|---------|--------------------------------------------------------------------------------------------|---------------|
| AUTH-01 | Iniciar y cerrar sesión mediante Supabase Auth.                                            | Must          |
| AUTH-02 | Mantener el estado de sesión en el frontend y proteger las rutas privadas.                 | Must          |
| AUTH-03 | Enviar el token de acceso a Spring Boot y validar el JWT en el backend.                    | Must          |
| AUTH-04 | Consultar el perfil/rol del usuario y aplicar autorización en endpoints protegidos.        | Must          |
| AUTH-05 | Mostrar errores claros para credenciales incorrectas, sesión expirada o falta de permisos. | Must          |

## 6.2. CRM: clientes, cuentas y contratos

| **ID** | **Funcionalidad mínima**                                                                           | **Prioridad** |
|--------|----------------------------------------------------------------------------------------------------|---------------|
| CRM-01 | Crear, listar, consultar, editar y desactivar clientes.                                            | Must          |
| CRM-02 | Registrar cuentas corporativas y relacionarlas con los clientes cuando aplique.                    | Should        |
| CRM-03 | Registrar contratos vinculados a un cliente/cuenta y consultar su estado.                          | Must          |
| CRM-04 | Validar campos obligatorios y evitar duplicados evidentes, como el mismo identificador de cliente. | Must          |
| CRM-05 | Mostrar un resumen comercial con conteos básicos de clientes y contratos por estado.               | Should        |

## 6.3. Reglas de alcance del CRM

- La primera versión se centrará en operaciones CRUD y seguimiento básico de estado; no incluirá automatizaciones comerciales avanzadas.

- Los contratos tendrán estados simples definidos en la especificación, por ejemplo: borrador, activo, finalizado o cancelado.

- Las búsquedas, filtros y paginación se limitarán a opciones necesarias para que las listas sean utilizables.

> **Criterio de aceptación del CRM**
>
> Un usuario con permisos puede registrar un cliente, encontrarlo en el listado, editar sus datos, vincular un contrato y consultar el resultado después de recargar la aplicación. Un usuario sin permiso no puede ejecutar estas operaciones.

## 6.4. ERP: gestión administrativa básica

| **ID** | **Funcionalidad mínima**                                                                        | **Prioridad** |
|--------|-------------------------------------------------------------------------------------------------|---------------|
| ERP-01 | Crear, listar, consultar y editar registros de empleados.                                       | Must          |
| ERP-02 | Crear, listar, consultar y editar proveedores.                                                  | Must          |
| ERP-03 | Registrar facturas o movimientos financieros básicos con fecha, concepto, valor y estado.       | Must          |
| ERP-04 | Consultar un panel financiero simple con totales básicos a partir de los registros disponibles. | Should        |
| ERP-05 | Relacionar los registros financieros con clientes o proveedores cuando corresponda.             | Should        |

**Límite funcional:** la gestión contable será básica y orientada a registros y consultas. No se plantea implementar un sistema contable integral con libro mayor, impuestos, conciliación bancaria o cierres contables en el MVP.

## 6.5. SGA: inventario, drones, paquetes y trazabilidad

| **ID** | **Funcionalidad mínima**                                                                    | **Prioridad** |
|--------|---------------------------------------------------------------------------------------------|---------------|
| SGA-01 | Registrar y consultar drones con identificador, estado operativo y observaciones básicas.   | Must          |
| SGA-02 | Registrar y consultar paquetes con identificador, descripción, cliente y estado.            | Must          |
| SGA-03 | Gestionar registros de inventario y actualizar cantidades mediante operaciones controladas. | Must          |
| SGA-04 | Registrar un historial de cambios de estado del paquete para permitir su trazabilidad.      | Must          |
| SGA-05 | Asociar un paquete con un cliente/contrato y, cuando aplique, con un dron registrado.       | Must          |
| SGA-06 | Consultar indicadores básicos de paquetes e inventario por estado.                          | Should        |

## 6.6. Flujo integrado demostrable

1.  Un usuario autorizado inicia sesión y accede a los módulos permitidos.

2.  El área comercial registra un cliente y un contrato asociado.

3.  Operaciones registra un paquete relacionado con el cliente y/o contrato.

4.  El paquete avanza manualmente por estados definidos; cada cambio relevante queda registrado para consulta.

5.  El equipo puede consultar la información relacionada sin duplicar manualmente los datos maestros del cliente.

> **Supuesto explícito**
>
> La asignación de drones y la actualización de estados serán manuales en la primera versión. El MVP no controla vuelos reales ni recibe telemetría de hardware.

# 7. Arquitectura y tecnologías

La solución tendrá una arquitectura cliente-servidor con frontend SPA y backend REST. El backend se organizará como monolito modular para mantener bajo el costo de desarrollo y despliegue sin perder separación lógica entre áreas funcionales.

| **Componente**         | **Tecnología / decisión**                      | **Responsabilidad**                                               |
|------------------------|------------------------------------------------|-------------------------------------------------------------------|
| Frontend               | React + Vite                                   | Interfaz de usuario, vistas y navegación.                         |
| Estilos                | Tailwind CSS                                   | Diseño visual consistente y adaptable.                            |
| Autenticación          | Supabase Auth                                  | Inicio/cierre de sesión y emisión de tokens de acceso.            |
| Backend                | Java + Spring Boot                             | API REST, validaciones y reglas de negocio.                       |
| Seguridad              | Spring Security + validación JWT               | Validar tokens y aplicar autorización en cada endpoint protegido. |
| Persistencia           | Spring Data JPA / Hibernate                    | Mapeo de entidades y acceso a datos.                              |
| Base de datos          | PostgreSQL en Supabase                         | Almacenamiento relacional centralizado.                           |
| Pruebas y colaboración | Git, GitHub y herramientas de prueba acordadas | Control de versiones, revisión y validación.                      |
| SDD                    | Kiro Specs                                     | Mantener requirements.md, design.md y tasks.md por funcionalidad. |

# 8. Requisitos no funcionales mínimos

| **ID** | **Requisito**                                                                                                                  |
|--------|--------------------------------------------------------------------------------------------------------------------------------|
| NF-01  | Seguridad: secretos y claves de servicio no se almacenan en el código del frontend ni se suben al repositorio.                 |
| NF-02  | Autorización: los permisos se verifican en Spring Boot; ocultar botones o rutas en React no es suficiente.                     |
| NF-03  | Integridad: relaciones y restricciones básicas se definen en PostgreSQL; las validaciones también se comprueban en el backend. |
| NF-04  | Mantenibilidad: el código se separa por módulo y capas; las convenciones compartidas se documentan.                            |
| NF-05  | Trazabilidad de cambios: las migraciones de base de datos y las especificaciones se versionan en Git.                          |
| NF-06  | Usabilidad: formularios muestran campos obligatorios, mensajes de validación y estados de carga/error.                         |
| NF-07  | Pruebas: las reglas críticas y los endpoints protegidos cuentan con pruebas acordes al alcance del MVP.                        |

# 9. Organización recomendada del código

El repositorio será un monorepo con carpetas independientes para frontend y backend, migraciones de base de datos y documentación. En Spring Boot se mantendrá la separación Controller → Service → Repository dentro de los módulos login, crm, erp y sga. React concentrará páginas, componentes compartidos, rutas y servicios HTTP.

# 10. Fuera del alcance del MVP

Para evitar que la primera versión crezca de forma incontrolada, las siguientes capacidades se dejan para una fase posterior, salvo que el profesor o el equipo acuerden modificar el alcance:

- Control autónomo de vuelo, navegación, telemetría o conexión directa con drones físicos.

- Modelos de inteligencia artificial para optimización de rutas, predicción de demanda o toma autónoma de decisiones.

- Integraciones con pasarelas de pago, plataformas de comercio electrónico, mensajería externa o sistemas de terceros.

- Contabilidad completa, nómina, impuestos, conciliación bancaria y cierres financieros.

- Aplicación móvil nativa, microservicios, Kubernetes, colas de eventos o arquitectura distribuida.

- Reportes analíticos avanzados, notificaciones multicanal y automatizaciones complejas.

- Administración de múltiples empresas o sucursales con aislamiento multitenant.

# 11. Criterios globales de aceptación

| **ID** | **Se considerará cumplido cuando…**                                                                          |
|--------|--------------------------------------------------------------------------------------------------------------|
| CA-01  | La aplicación pueda ejecutarse con instrucciones documentadas y configuración mediante variables de entorno. |
| CA-02  | Un usuario pueda iniciar/cerrar sesión y el backend rechace tokens inválidos o vencidos.                     |
| CA-03  | Los permisos por rol se apliquen en el backend y existan pruebas para accesos permitidos y denegados.        |
| CA-04  | Los módulos incluidos permitan las operaciones mínimas descritas en este documento y persistan los datos.    |
| CA-05  | Se pueda demostrar el flujo integrado cliente → contrato → paquete → cambio de estado/trazabilidad.          |
| CA-06  | La API responda con códigos HTTP coherentes y errores comprensibles, sin exponer información sensible.       |
| CA-07  | El equipo haya revisado la especificación, el diseño, las tareas, las pruebas y la documentación de uso.     |

# 12. Priorización

| **Prioridad**     | **Significado en el MVP**                                                                                     |
|-------------------|---------------------------------------------------------------------------------------------------------------|
| Must have         | Imprescindible para demostrar una versión funcional e integrada; no se considera listo el MVP si falta.       |
| Should have       | Aporta valor y se realiza si no compromete autenticación, flujo integrado ni funcionalidades imprescindibles. |
| Could / posterior | Mejoras o automatizaciones que pueden incorporarse después de validar el MVP.                                 |

Las prioridades Must/Should de las tablas funcionales constituyen el alcance acordado del MVP y se conservan en sus especificaciones.

# 13. Plan de trabajo con SDD y Kiro

| **Etapa**                  | **Actividad y entregable**                                                                                                                      |
|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| 1\. Constitución           | Consolidar alcance, stack, arquitectura, convenciones y reglas en los steering files de Kiro, que cumplen la función de constitución del proyecto. |
| 2\. Especificación inicial | Crear una Feature Spec para autenticación e integración técnica; definir requisitos, diseño, riesgos y criterios de aceptación.                 |
| 3\. Integración vertical   | Validar inicio de sesión en React, JWT validado en Spring Boot y respuesta de un endpoint protegido.                                            |
| 4\. Datos y API            | Acordar entidades, migraciones, contrato REST, códigos de respuesta y validaciones compartidas.                                                 |
| 5\. Funcionalidades CRUD   | Implementar CRM primero como patrón de referencia; después replicar convenciones acordadas en ERP y SGA.                                        |
| 6\. Flujo integrado        | Relacionar cliente, contrato y paquete; verificar estados, trazabilidad, permisos y persistencia.                                               |
| 7\. Validación final       | Ejecutar pruebas, corregir fallos, revisar criterios de aceptación y actualizar documentación.                                                  |

# 14. Responsabilidades

La asignación vigente por integrante se mantiene en `.kiro/steering/sdd-workflow.md`; las tareas específicas registran responsable y requisitos en `tasks.md` para evitar mantener copias divergentes aquí.

# 15. Definition of Done (DoD)

Una tarea o funcionalidad se considera terminada cuando se cumple lo siguiente:

- Requisitos, criterios de aceptación y diseño aprobados en la especificación; los cambios de alcance actualizan primero esa especificación.

- Código conforme a las convenciones acordadas, revisado por otro integrante e integrado en Git.

- Pruebas acordadas aprobadas, incluidos casos relevantes de validación, errores y permisos.

- Flujo de extremo a extremo funcional con API y datos reales; documentación y configuración actualizadas, sin secretos en el repositorio.

**Criterio de cierre:** El MVP debe demostrar autenticación, permisos básicos, operaciones esenciales de CRM, ERP y SGA, y el flujo cliente → paquete con trazabilidad. El control autónomo de vuelo, la analítica avanzada y la contabilidad integral quedan para fases posteriores.

# 16. Decisiones técnicas consolidadas

Las decisiones detalladas se mantienen en `.kiro/steering/`, que cumple la función de constitución técnica mencionada en §13. Al 2026-11-08 no quedan decisiones pendientes de aprobación. Los acuerdos principales son:

- Roles empresariales en perfiles PostgreSQL; autorización backend con denegación por defecto y permisos mínimos según §5.
- Datos de negocio en esquema privado `app`, protegido por RLS y sin acceso directo desde la Data API.
- Migraciones versionadas con Supabase CLI; Hibernate solo valida el esquema; pruebas PostgreSQL automatizadas con Testcontainers.
- API sin versión durante el MVP, errores RFC 9457 Problem Details y contratos definidos en cada `design.md`.
- Las prioridades de alcance son las tablas Must/Should de §6. El avance y tareas abiertas se mantienen en README y en la spec activa.
