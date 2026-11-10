import { supabase } from './supabase.js'
import { withDeadline } from './deadline.js'

export const SESSION_ENDED = 'arise:session-ended'

export async function endSession(reason = null) {
  // La interfaz se bloquea antes de esperar a la red. El SDK elimina su sesión
  // local con scope local incluso si la revocación remota falla.
  window.dispatchEvent(new CustomEvent(SESSION_ENDED, { detail: reason }))
  try {
    await withDeadline(() => supabase?.auth.signOut({ scope: 'local' }))
  } catch {
    // Nunca conservar la vista privada por un error de red al cerrar sesión.
  }
}
