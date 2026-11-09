-- Migracion: esquema privado `app` y tabla `perfiles_usuario`
-- Spec: 001-foundation, tarea 15
-- Decisiones: DP-ARQ-02 (esquema privado app), DP-SEG-01 (perfil y rol),
--             DP-SEG-05 (RLS + revocacion de grants), DP-TEC-03 (migracion versionada)
-- Requisitos: AUTH-04, NF-01, NF-03, NF-05
--
-- Esta migracion NO inserta perfiles ni UUID personales (D1, NF-01).
-- El primer Administrador se aprovisiona manualmente despues de crear su
-- cuenta en Supabase Auth (ver seccion de aprovisionamiento al final).

-- ====================================
-- Esquema privado de negocio
-- ====================================
-- El esquema `app` queda fuera de los esquemas expuestos por la Data API
-- (DP-ARQ-02). Confirmar en la configuracion de Supabase que `app` no esta
-- en la lista de "Exposed schemas" al aplicar esta migracion (DP-SEG-05).
create schema if not exists app;

-- ====================================
-- Tabla de perfiles de usuario
-- ====================================
-- `usuario_id` es el UUID `sub` de Supabase Auth; es la PK y referencia a
-- auth.users. Un unico rol empresarial activo por usuario (DP-SEG-01, DP-PRO-01).
create table if not exists app.perfiles_usuario (
    usuario_id       uuid primary key
                         references auth.users (id) on delete cascade,
    rol              text        not null
                         check (rol in ('ADMINISTRADOR', 'COMERCIAL', 'FINANZAS_RRHH', 'OPERACIONES')),
    activo           boolean     not null default true,
    creado_en        timestamptz not null default now(),
    actualizado_en   timestamptz not null default now(),
    -- UUID `sub` del administrador que creo o modifico el perfil (DP-SEG-06).
    actualizado_por  uuid        references auth.users (id)
);

comment on table app.perfiles_usuario is
    'Perfil empresarial del usuario autenticado. Fuente de autorizacion del backend (DP-SEG-01).';
comment on column app.perfiles_usuario.usuario_id is
    'UUID sub de Supabase Auth; identidad autoritativa del usuario.';
comment on column app.perfiles_usuario.rol is
    'Rol empresarial unico: ADMINISTRADOR, COMERCIAL, FINANZAS_RRHH u OPERACIONES.';
comment on column app.perfiles_usuario.activo is
    'Perfil desactivado => el backend deniega el acceso con 403.';
comment on column app.perfiles_usuario.actualizado_por is
    'UUID sub del administrador que realizo el ultimo cambio (auditoria DP-SEG-06).';

-- ====================================
-- Seguridad: RLS y revocacion de grants (DP-SEG-05)
-- ====================================
-- Se habilita RLS sin politicas para los roles de cliente y se revoca todo
-- acceso de `anon` y `authenticated`. El backend se conecta con el rol del
-- Session pooler, que conserva los privilegios necesarios y no depende de RLS.
alter table app.perfiles_usuario enable row level security;

-- Denegar por defecto: sin politicas, ninguna fila es visible via Data API.
revoke all on schema app from anon, authenticated;
revoke all on all tables in schema app from anon, authenticated;

-- Asegurar que futuros objetos del esquema tampoco queden expuestos a
-- los roles de cliente por privilegios por defecto.
alter default privileges in schema app revoke all on tables from anon, authenticated;
