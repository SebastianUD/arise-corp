import { supabase } from '../lib/supabase.js'
import { endSession } from '../lib/session.js'
import { withDeadline } from '../lib/deadline.js'

const tiposDeError = {
  0: 'red',
  400: 'validacion',
  401: 'sesion',
  403: 'sin-permiso',
  404: 'no-encontrado',
  409: 'conflicto',
}

export class ApiError extends Error {
  constructor(message, status = 0, problem = null) {
    super(message)
    this.status = status
    this.problem = problem
    this.title = problem?.title
    this.detail = problem?.detail
    this.errors = Array.isArray(problem?.errors) ? problem.errors : undefined
    this.kind = tiposDeError[status] ?? (status >= 500 ? 'servidor' : 'cliente')
    this.conflicto = problem?.conflicto
    this.campo = problem?.campo
    this.reintentable = this.kind === 'servidor' || this.kind === 'red'
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

export async function apiRequest(path, { method = 'GET', body } = {}) {
  method = method.toUpperCase()
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
      const headers = { Authorization: `Bearer ${data.session.access_token}` }
      const options = { method, headers, signal }
      if (body !== undefined) {
        headers['Content-Type'] = 'application/json'
        options.body = JSON.stringify(body)
      }
      const response = await fetch(`${baseUrl.replace(/\/$/, '')}${path}`, options)
      if (response.status === 401) {
        void endSession('expired')
        throw new ApiError('Tu sesión expiró o ya no es válida. Inicia sesión nuevamente.', 401)
      }
      if (response.status === 403) {
        throw new ApiError(method === 'GET'
          ? 'No tienes permisos para consultar esta información.'
          : 'No tienes permisos para realizar esta operación.', 403)
      }
      if (!response.ok) {
        // Solo se leen detalles de errores 4xx; los 5xx nunca exponen su cuerpo.
        const problem = response.status >= 400 && response.status < 500
          ? await readProblemDetails(response)
          : null
        throw new ApiError(
          safeDetail(problem) || (method === 'GET'
            ? 'No pudimos cargar tu información. Intenta nuevamente.'
            : 'No pudimos completar la operación. Intenta nuevamente.'),
          response.status,
          problem,
        )
      }
      return response.status === 204 ? null : await response.json()
    })
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError('No pudimos conectar con el servicio. Revisa tu conexión e intenta nuevamente.')
  }
}
