import { supabase } from '../lib/supabase.js'
import { endSession } from '../lib/session.js'
import { withDeadline } from '../lib/deadline.js'

export class ApiError extends Error {
  constructor(message, status = 0, problem = null) {
    super(message)
    this.status = status
    this.problem = problem
    this.title = problem?.title
    this.detail = problem?.detail
    this.errors = Array.isArray(problem?.errors) ? problem.errors : undefined
  }
}

async function readProblemDetails(response) {
  if (typeof response.json !== 'function') return null
  try {
    const problem = await response.json()
    return problem && typeof problem === 'object' ? problem : null
  } catch {
    return null
  }
}

function safeDetail(problem) {
  const detail = problem?.detail
  return typeof detail === 'string' && detail.length > 0 && detail.length <= 300 ? detail : null
}

export async function apiRequest(path) {
  try {
    return await withDeadline(async signal => {
      const { data, error } = await supabase.auth.getSession()
      if (signal.aborted) throw new DOMException('Aborted', 'AbortError')
      if (error || !data.session?.access_token) {
        void endSession('expired')
        throw new ApiError('Tu sesión expiró o ya no es válida. Inicia sesión nuevamente.', 401)
      }
      const baseUrl = import.meta.env.VITE_API_BASE_URL?.trim()
      if (!baseUrl) throw new ApiError('El servicio aún no está disponible. Contacta al equipo de Arise Corp.')
      const response = await fetch(`${baseUrl.replace(/\/$/, '')}${path}`, {
        headers: { Authorization: `Bearer ${data.session.access_token}` },
        signal,
      })
      if (response.status === 401) {
        void endSession('expired')
        throw new ApiError('Tu sesión expiró o ya no es válida. Inicia sesión nuevamente.', 401)
      }
      if (response.status === 403) throw new ApiError('No tienes permisos para consultar esta información.', 403)
      if (!response.ok) {
        // Solo se leen detalles de errores 4xx; los 5xx nunca exponen su cuerpo.
        const problem = response.status >= 400 && response.status < 500
          ? await readProblemDetails(response)
          : null
        throw new ApiError(
          safeDetail(problem) || 'No pudimos cargar tu información. Intenta nuevamente.',
          response.status,
          problem,
        )
      }
      return await response.json()
    })
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError('No pudimos conectar con el servicio. Revisa tu conexión e intenta nuevamente.')
  }
}
