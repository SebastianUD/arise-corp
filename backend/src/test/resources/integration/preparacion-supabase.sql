-- Preparacion del PostgreSQL efimero de las IT. No se aplica nunca a Supabase.
-- Reproduce los objetos de Supabase de los que dependen las migraciones reales.
create role anon nologin;
create role authenticated nologin;

create schema auth;
create table auth.users (
    id uuid primary key
);

-- Caso mas desfavorable: privilegios por defecto globales para los roles de
-- cliente. Las migraciones deben revocarlos de forma explicita en sus tablas.
alter default privileges grant all on tables to anon, authenticated;
