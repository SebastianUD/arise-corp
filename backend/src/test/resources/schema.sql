-- Esquema minimo de Supabase Auth para las pruebas backend.
--
-- La tabla `auth.users` existe en el proyecto Supabase real. En H2 se crea
-- solo para poder validar la regla de negocio de la tarea 17: no se permite
-- crear un perfil para una identidad que no existe en Auth. No contiene
-- usuarios ni datos personales.

create schema if not exists auth;

create table if not exists auth.users (
    id uuid primary key
);
