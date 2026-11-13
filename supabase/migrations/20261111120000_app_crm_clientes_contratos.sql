-- Migracion: modulo CRM (cuentas corporativas, clientes y contratos)
-- Spec: 002-crm-clientes-contratos
-- Decisiones: DP-ARQ-02, DP-SEG-05, DP-SEG-06, DP-TEC-03, DP-API-04
-- Requisitos: CRM-01 a CRM-05, NF-01, NF-03, NF-05
-- Solo estructura: no inserta datos, UUID personales ni secretos (NF-01).

-- ====================================
-- Cuentas corporativas (CRM-02)
-- ====================================
create table app.cuentas_corporativas (
    id                 uuid        primary key default gen_random_uuid(),
    nit                text        not null,
    razon_social       text        not null,
    nombre_contacto    text,
    correo_contacto    text,
    telefono_contacto  text,
    activa             boolean     not null default true,
    creado_en          timestamptz not null default now(),
    actualizado_en     timestamptz not null default now(),
    -- sub del JWT del actor; sin FK para conservar la auditoria (DP-SEG-06)
    actualizado_por    uuid        not null,
    constraint cuentas_corporativas_nit_uk unique (nit),
    constraint cuentas_corporativas_nit_ck check (nit ~ '^[0-9]{5,15}$'),
    constraint cuentas_corporativas_razon_social_ck
        check (char_length(razon_social) between 1 and 200),
    constraint cuentas_corporativas_nombre_contacto_ck
        check (nombre_contacto is null or char_length(nombre_contacto) between 1 and 200),
    constraint cuentas_corporativas_correo_contacto_ck
        check (correo_contacto is null or char_length(correo_contacto) between 3 and 254),
    constraint cuentas_corporativas_telefono_contacto_ck
        check (telefono_contacto is null or telefono_contacto ~ '^[0-9 +()-]{7,20}$')
);

-- ====================================
-- Clientes (CRM-01, CRM-04)
-- ====================================
create table app.clientes (
    id                     uuid        primary key default gen_random_uuid(),
    tipo_documento         text        not null,
    numero_documento       text        not null,
    nombre                 text        not null,
    correo                 text        not null,
    telefono               text        not null,
    direccion              text,
    ciudad                 text,
    cuenta_corporativa_id  uuid        references app.cuentas_corporativas (id),
    activo                 boolean     not null default true,
    creado_en              timestamptz not null default now(),
    actualizado_en         timestamptz not null default now(),
    actualizado_por        uuid        not null,
    constraint clientes_documento_uk unique (tipo_documento, numero_documento),
    constraint clientes_tipo_documento_ck
        check (tipo_documento in ('CC', 'CE', 'NIT', 'PASAPORTE')),
    constraint clientes_numero_documento_ck
        check (numero_documento ~ '^[A-Z0-9]{3,20}$'
               and (tipo_documento not in ('CC', 'NIT') or numero_documento ~ '^[0-9]+$')),
    constraint clientes_nombre_ck check (char_length(nombre) between 1 and 200),
    constraint clientes_correo_ck check (char_length(correo) between 3 and 254),
    constraint clientes_telefono_ck check (telefono ~ '^[0-9 +()-]{7,20}$'),
    constraint clientes_direccion_ck
        check (direccion is null or char_length(direccion) between 1 and 200),
    constraint clientes_ciudad_ck
        check (ciudad is null or char_length(ciudad) between 1 and 100)
);

create index clientes_cuenta_corporativa_id_idx on app.clientes (cuenta_corporativa_id);

-- ====================================
-- Contratos (CRM-03)
-- ====================================
create table app.contratos (
    id                      uuid          primary key default gen_random_uuid(),
    cliente_id              uuid          not null references app.clientes (id),
    codigo                  text          not null,
    descripcion             text          not null,
    fecha_inicio            date          not null,
    fecha_fin               date,
    valor                   numeric(15,2) not null,
    estado                  text          not null default 'BORRADOR',
    estado_actualizado_en   timestamptz   not null default now(),
    estado_actualizado_por  uuid          not null,
    creado_en               timestamptz   not null default now(),
    actualizado_en          timestamptz   not null default now(),
    actualizado_por         uuid          not null,
    constraint contratos_codigo_uk unique (codigo),
    constraint contratos_codigo_ck check (codigo ~ '^[A-Z0-9-]{3,30}$'),
    constraint contratos_descripcion_ck check (char_length(descripcion) between 1 and 1000),
    constraint contratos_estado_ck
        check (estado in ('BORRADOR', 'ACTIVO', 'FINALIZADO', 'CANCELADO')),
    constraint contratos_valor_ck check (valor >= 0),
    constraint contratos_fechas_ck check (fecha_fin is null or fecha_fin >= fecha_inicio)
);

create index contratos_cliente_id_idx on app.contratos (cliente_id);
create index contratos_estado_idx on app.contratos (estado);

-- ====================================
-- Comentarios (resumen)
-- ====================================
comment on table app.cuentas_corporativas is 'Empresas que agrupan clientes (CRM-02).';
comment on table app.clientes is 'Clientes de Arise Corp.; se desactivan, no se eliminan (CRM-01).';
comment on table app.contratos is 'Contratos vinculados a un cliente; estados controlados (CRM-03).';

-- ====================================
-- Seguridad: RLS y revocacion de grants (DP-SEG-05)
-- ====================================
alter table app.cuentas_corporativas enable row level security;
alter table app.clientes enable row level security;
alter table app.contratos enable row level security;

-- La revocacion explicita no depende de los privilegios por defecto del esquema.
revoke all on table app.cuentas_corporativas, app.clientes, app.contratos
    from anon, authenticated;
