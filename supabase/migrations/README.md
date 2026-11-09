# Migraciones Supabase (esquema `app`)

Migraciones SQL versionadas del proyecto Arise Corp. Se gestionan con Supabase
CLI y siguen el nombre `<timestamp>_<nombre>.sql` (DP-TEC-03).

## Reglas (DP-TEC-03, sdd-workflow.md)

- Las migraciones se **revisan en PR** por un integrante distinto del autor
  antes de aplicarse.
- Davidson prepara y aplica las migraciones de forma controlada en
  `arise-corp-dev` **tras el merge**.
- **Nunca** se edita una migración ya aplicada: ante un cambio, se crea una
  migracion nueva.
- Ninguna migracion contiene secretos, UUID personales ni datos reales (NF-01).

## Migraciones

| Archivo | Descripcion | Spec |
|---|---|---|
| `20261108000000_app_perfiles_usuario.sql` | Esquema privado `app` y tabla `perfiles_usuario` (perfil empresarial, RLS, revocacion de grants a `anon`/`authenticated`). | 001-foundation, tarea 15 |

## Aprovisionamiento del primer Administrador

La migracion crea la estructura, pero **no inserta ningun perfil** (D1, NF-01).
El primer Administrador se aprovisiona manualmente, una sola vez, despues de
aplicar la migracion:

1. Un integrante autorizado crea la cuenta del Administrador en **Supabase
   Auth** (panel de Supabase o invitacion por correo). De ahi se obtiene el
   UUID `sub` del usuario (columna `id` de `auth.users`).
2. Con ese UUID, se inserta el perfil ejecutando SQL controlado contra
   `arise-corp-dev` (SQL Editor del panel o `psql`), **sin** versionar el UUID
   en el repositorio:

   ```sql
   -- Reemplazar <UUID-DEL-ADMIN> por el id real de auth.users.
   -- No pegar este UUID en commits, PR, issues ni capturas (NF-01).
   insert into app.perfiles_usuario (usuario_id, rol, activo, actualizado_por)
   values ('<UUID-DEL-ADMIN>', 'ADMINISTRADOR', true, '<UUID-DEL-ADMIN>');
   ```

3. A partir de ese Administrador, el resto de perfiles se crean mediante el
   endpoint administrativo del backend (`PUT /api/admin/usuarios/{id}/perfil`),
   que llega en la tarea 17. No se insertan perfiles adicionales a mano.

> El UUID del Administrador y cualquier credencial quedan **fuera del repositorio**.
> Esta operacion es parte de la validacion controlada de la tarea 20, no de CI.
