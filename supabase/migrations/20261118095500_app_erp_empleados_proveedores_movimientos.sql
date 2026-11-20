-- Migracion: modulo ERP (empleados, proveedores y movimientos financieros)
-- Spec: 003-erp-empleados-proveedores-finanzas
-- Decisiones: DP-ARQ-01, DP-ARQ-02, DP-SEG-05, DP-SEG-06, DP-TEC-03, DP-API-04
-- Requisitos: ERP-01 a ERP-05, NF-01, NF-03, NF-05
-- Solo estructura: no inserta datos, UUID personales ni secretos (NF-01).

-- ====================================
-- Empleados (ERP-01)
-- ====================================
create table app.empleados (
    id                uuid        primary key default gen_random_uuid(),
    tipo_documento    text        not null,
    numero_documento  text        not null,
    nombre            text        not null,
    cargo             text        not null,
    area              text        not null,
    correo            text        not null,
    telefono          text        not null,
    -- La regla "no posterior a la fecha actual en America/Bogota" la aplica el backend:
    -- un CHECK con current_date no es inmutable y usaria la zona de la sesion.
    fecha_ingreso     date        not null,
    activo            boolean     not null default true,
    creado_en         timestamptz not null default now(),
    actualizado_en    timestamptz not null default now(),
    -- sub del JWT del actor; sin FK para conservar la auditoria (DP-SEG-06)
    actualizado_por   uuid        not null,
    constraint empleados_documento_uk unique (tipo_documento, numero_documento),
    constraint empleados_tipo_documento_ck
        check (tipo_documento in ('CC', 'CE', 'PASAPORTE')),
    constraint empleados_numero_documento_ck
        check (numero_documento ~ '^[A-Z0-9]{3,20}$'
               and (tipo_documento <> 'CC' or numero_documento ~ '^[0-9]+$')),
    constraint empleados_nombre_ck check (char_length(nombre) between 1 and 200),
    constraint empleados_cargo_ck check (char_length(cargo) between 1 and 100),
    constraint empleados_area_ck check (char_length(area) between 1 and 100),
    constraint empleados_correo_ck check (char_length(correo) between 3 and 254),
    constraint empleados_telefono_ck check (telefono ~ '^[0-9 +()-]{7,20}$')
);

-- ====================================
-- Proveedores (ERP-02)
-- ====================================
create table app.proveedores (
    id                uuid        primary key default gen_random_uuid(),
    tipo_documento    text        not null,
    numero_documento  text        not null,
    nombre            text        not null,
    nombre_contacto   text,
    correo            text        not null,
    telefono          text        not null,
    direccion         text,
    ciudad            text,
    activo            boolean     not null default true,
    creado_en         timestamptz not null default now(),
    actualizado_en    timestamptz not null default now(),
    actualizado_por   uuid        not null,
    constraint proveedores_documento_uk unique (tipo_documento, numero_documento),
    constraint proveedores_tipo_documento_ck
        check (tipo_documento in ('NIT', 'CC', 'CE', 'PASAPORTE')),
    constraint proveedores_numero_documento_ck
        check (numero_documento ~ '^[A-Z0-9]{3,20}$'
               and (tipo_documento not in ('CC', 'NIT') or numero_documento ~ '^[0-9]+$')),
    constraint proveedores_nombre_ck check (char_length(nombre) between 1 and 200),
    constraint proveedores_nombre_contacto_ck
        check (nombre_contacto is null or char_length(nombre_contacto) between 1 and 200),
    constraint proveedores_correo_ck check (char_length(correo) between 3 and 254),
    constraint proveedores_telefono_ck check (telefono ~ '^[0-9 +()-]{7,20}$'),
    constraint proveedores_direccion_ck
        check (direccion is null or char_length(direccion) between 1 and 200),
    constraint proveedores_ciudad_ck
        check (ciudad is null or char_length(ciudad) between 1 and 100)
);

-- ====================================
-- Movimientos financieros (ERP-03, ERP-05)
-- ====================================
create table app.movimientos_financieros (
    id                      uuid          primary key default gen_random_uuid(),
    tipo                    text          not null,
    fecha                   date          not null,
    concepto                text          not null,
    valor                   numeric(15,2) not null,
    referencia              text,
    estado                  text          not null default 'PENDIENTE',
    -- Relacion con el CRM por UUID; sin asociacion JPA entre modulos (DP-ARQ-01)
    cliente_id              uuid          references app.clientes (id),
    proveedor_id            uuid          references app.proveedores (id),
    estado_actualizado_en   timestamptz   not null default now(),
    estado_actualizado_por  uuid          not null,
    creado_en               timestamptz   not null default now(),
    actualizado_en          timestamptz   not null default now(),
    actualizado_por         uuid          not null,
    constraint movimientos_financieros_tipo_ck
        check (tipo in ('INGRESO', 'EGRESO')),
    constraint movimientos_financieros_estado_ck
        check (estado in ('PENDIENTE', 'PAGADO', 'ANULADO')),
    constraint movimientos_financieros_concepto_ck
        check (char_length(concepto) between 3 and 300),
    constraint movimientos_financieros_valor_ck check (valor > 0),
    constraint movimientos_financieros_referencia_ck
        check (referencia is null or referencia ~ '^[A-Z0-9 ./-]{1,50}$'),
    -- Un EGRESO no se asocia a clientes y un INGRESO no se asocia a proveedores (ERP-05)
    constraint movimientos_financieros_cliente_tipo_ck
        check (cliente_id is null or tipo = 'INGRESO'),
    constraint movimientos_financieros_proveedor_tipo_ck
        check (proveedor_id is null or tipo = 'EGRESO')
);

create index movimientos_financieros_fecha_idx on app.movimientos_financieros (fecha);
create index movimientos_financieros_cliente_id_idx on app.movimientos_financieros (cliente_id);
create index movimientos_financieros_proveedor_id_idx on app.movimientos_financieros (proveedor_id);

-- ====================================
-- Comentarios (resumen)
-- ====================================
comment on table app.empleados is 'Empleados de Arise Corp.; se desactivan, no se eliminan (ERP-01).';
comment on table app.proveedores is 'Proveedores de Arise Corp.; se desactivan, no se eliminan (ERP-02).';
comment on table app.movimientos_financieros is
    'Ingresos y egresos con estado controlado; se anulan, no se eliminan (ERP-03, ERP-05).';

-- ====================================
-- Seguridad: RLS y revocacion de grants (DP-SEG-05)
-- ====================================
alter table app.empleados enable row level security;
alter table app.proveedores enable row level security;
alter table app.movimientos_financieros enable row level security;

-- La revocacion explicita no depende de los privilegios por defecto del esquema.
revoke all on table app.empleados, app.proveedores, app.movimientos_financieros
    from anon, authenticated;
