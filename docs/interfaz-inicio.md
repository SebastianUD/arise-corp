# Página de llegada y navegación por acordeones

## Requisitos

Cambio solicitado por Paula: ocultar las opciones CRM y ERP hasta pulsar su encabezado y añadir una página de llegada con la misión y visión del documento de constitución de Arise Corp.

- La ruta pública `/` presenta la empresa, la misión completa de la página 2 y la visión completa de la página 3, incluido el horizonte 2035.
- La página permite iniciar sesión o volver al panel si existe una sesión vigente. El acceso privado continúa protegido y el login conserva su destino `/panel`.
- Los grupos CRM y ERP empiezan cerrados. Pulsar uno lo abre y cierra el otro; volver a pulsarlo lo cierra. Los enlaces conservan los permisos actuales y el estado de la ruta activa.
- Los controles funcionan con teclado, anuncian su estado y no exponen enlaces ocultos al foco. La interfaz se adapta a móvil y respeta el tema del espacio privado.

## Diseño

Página `Inicio.jsx` con carga diferida, ruta pública explícita antes de las rutas protegidas y enlace desde el login y el menú privado. Se reutilizan el logo, la fuente Inter y la paleta azul locales. Los enlaces internos permiten llegar a misión y visión.

Componente `GrupoMenu.jsx` con botón nativo, `aria-expanded`, `aria-controls` y contenedor `hidden`. `AppLayout` mantiene un único identificador de grupo abierto. No se añaden dependencias ni se cambian contratos de API.

## Validación

- [x] Implementar página y enlaces de entrada/salida.
- [x] Implementar acordeón y conservar permisos por rol.
- [x] Adaptar pruebas de navegación y comprobar la página pública y la interacción.
- [x] Ejecutar pruebas frontend, build y comprobación de secretos; revisar la vista adaptable.

Verificación local: 268 pruebas en 31 archivos; compilación de producción y comprobación de secretos correctas. Revisión visual de la llegada en escritorio y a 390 × 844, con navegación a misión, visión, login y vuelta al inicio. La interacción del acordeón y los permisos se verifican mediante las pruebas de integración de `App.test.jsx`, sin requerir credenciales reales.
