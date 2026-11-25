# Ajustes de presentación · 2026-11-24

Solicitud de Paula: completar la verificación pendiente de sus vistas y aplicar los comentarios del equipo.

## Requisitos y diseño

- Quitar únicamente el enlace «Inicio» del menú privado. Conservar la página pública y «Panel general».
- Unificar los iconos CRM, ERP y SGA con máscaras SVG y `currentColor`: mismo color de texto en reposo, selección y ambos temas.
- Mejorar el login con ilustración vectorial local de un dron, fondo azul, tarjetas de módulos, jerarquía visual y control accesible de contraseña. Conservar autenticación, validación y estados de error/carga.
- Adaptar Clientes a la referencia: búsqueda, estado, registrar cliente, columnas Cliente/Identificación/Cuenta/Estado/Acción, enlace explícito al detalle y pie con conteos y paginación. Mantener los permisos y filtros en URL.
- Sustituir los registros de demostración de CRM, ERP y SGA por datos ficticios verosímiles. Contactos e identificaciones son de demostración; no se atribuyen contratos a empresas reales ni se incorporan datos personales reales.
- Preparación de datos separada de las migraciones de estructura, en transacción, con copia local previa de las once tablas de negocio y comprobaciones de relaciones, historial y existencias. No modificar Auth, perfiles ni permisos.
- La tarea SGA 30.1 solo se da por validada con el flujo contra el backend y Supabase reales. La prueba simulada existente conserva su alcance.

## Tareas

- [x] Navegación e iconos en ambos temas.
- [x] Login adaptable y accesible.
- [x] Listado de clientes según la referencia, cinco registros por página.
- [x] Copia y sustitución controlada de datos de presentación.
- [x] Build y comprobación de secretos.
- [x] Pruebas frontend: 40 archivos y 377 pruebas aprobadas.
- [x] Validación del flujo SGA real pendiente de Paula (004, tarea 30.1).

## Evidencia

Preparación aplicada en Supabase el 2026-11-24, con copia previa privada en `supabase/.temp/`. Conjunto inicial: cuatro cuentas, 27 clientes, diez contratos, ocho empleados, seis proveedores, 16 movimientos financieros, ocho drones, 12 paquetes con 32 registros de historial, ocho artículos y 16 movimientos de inventario. Se conservaron los cuatro perfiles activos de negocio y Auth.

La validación real autorizada añadió un cliente empresarial, un contrato activo, un dron disponible, un envío entregado con cuatro registros de historial y un artículo con dos movimientos. El ejecutor manual usa las vistas React en jsdom, Supabase Auth, Spring Boot y PostgreSQL reales. Comprobó recuperación al remontar la aplicación, lectura cruzada de cliente y contrato, dron disponible y sin paquete en curso y rechazo 409 de una salida de inventario superior a la existencia (`10 - 4 = 6`). No se guardan contraseñas ni tokens. Instrucciones reproducibles en `supabase/demo/README.md`.

Conteo final confirmado por consultas de solo lectura: cuatro cuentas, 28 clientes, 11 contratos, ocho empleados, seis proveedores, 16 movimientos financieros, nueve drones, 13 paquetes, 36 registros de historial, nueve artículos y 18 movimientos de inventario. Los cuatro roles activos se conservaron. Revisión de los `tasks.md`: cero subtareas pendientes asignadas a Paula; las verificaciones y la entrega asignadas a otros integrantes conservan su estado.

La compilación de producción y `npm run check:secrets` aprobaron. La revisión visual no pudo completarse: Computer Use detuvo el acceso porque no pudo verificar la URL de Brave. Los cambios de diseño conservan las reglas adaptables existentes; esta entrega no afirma una inspección visual en navegador.
