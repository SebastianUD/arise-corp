import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { apiRequest } from './apiClient.js'

const { getSession, signOut } = vi.hoisted(() => ({ getSession: vi.fn(), signOut: vi.fn() }))
vi.mock('../lib/supabase.js', () => ({ supabase: { auth: { getSession, signOut } } }))

beforeEach(() => {
  vi.clearAllMocks()
  vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080/')
  getSession.mockResolvedValue({ data: { session: { access_token: 'fake-token' } }, error: null })
  signOut.mockResolvedValue({ error: null })
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ id: 'test' }) }))
})
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

it('consulta la sesión vigente en cada petición y normaliza la URL base', async () => {
  await apiRequest('/api/me')
  getSession.mockResolvedValue({ data: { session: { access_token: 'fake-renewed-token' } } })
  await apiRequest('/api/me')
  expect(fetch).toHaveBeenLastCalledWith('http://localhost:8080/api/me', expect.objectContaining({
    headers: { Authorization: 'Bearer fake-renewed-token' },
  }))
})

it('descarta la sesión ausente sin enviar una petición anónima', async () => {
  getSession.mockResolvedValue({ data: { session: null }, error: null })
  await expect(apiRequest('/api/me')).rejects.toMatchObject({ status: 401 })
  expect(fetch).not.toHaveBeenCalled()
  expect(signOut).toHaveBeenCalledWith({ scope: 'local' })
})

it('limita las peticiones a 30 segundos, aborta la red y permite reintentar', async () => {
  vi.useFakeTimers()
  let signal
  fetch.mockImplementationOnce((_, options) => {
    signal = options.signal
    return new Promise(() => {})
  })
  const failure = expect(apiRequest('/api/me')).rejects.toThrow('Revisa tu conexión')
  await vi.advanceTimersByTimeAsync(30_001)
  await failure
  expect(signal.aborted).toBe(true)
  expect(signOut).not.toHaveBeenCalled()
  await expect(apiRequest('/api/me')).resolves.toEqual({ id: 'test' })
})

it('limita también una lectura de sesión bloqueada y no envía el token después', async () => {
  vi.useFakeTimers()
  let resolveSession
  getSession.mockImplementationOnce(() => new Promise(resolve => { resolveSession = resolve }))
  const failure = expect(apiRequest('/api/me')).rejects.toThrow('Revisa tu conexión')
  await vi.advanceTimersByTimeAsync(30_001)
  await failure
  resolveSession({ data: { session: { access_token: 'fake-late-token' } } })
  await vi.advanceTimersByTimeAsync(1)
  expect(fetch).not.toHaveBeenCalled()
})

it('no expone el cuerpo interno de errores del backend', async () => {
  fetch.mockResolvedValue({ ok: false, status: 500, json: async () => ({ trace: 'private details' }) })
  await expect(apiRequest('/api/me')).rejects.toThrow('No pudimos cargar tu información')
})
