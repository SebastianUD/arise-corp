import { createClient } from '@supabase/supabase-js'
import { withDeadline } from './deadline.js'

const url = import.meta.env.VITE_SUPABASE_URL?.trim()
const key = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY?.trim()

export const configurationError = !url || !key
  ? 'El inicio de sesión aún no está disponible. Contacta al equipo de Arise Corp.'
  : null

// La sesión y su almacenamiento son responsabilidad exclusiva del SDK.
export const supabase = configurationError ? null : createClient(url, key, {
  global: {
    fetch: (input, options = {}) => withDeadline(signal => fetch(input, {
      ...options,
      signal: options.signal ? AbortSignal.any([options.signal, signal]) : signal,
    })),
  },
})
