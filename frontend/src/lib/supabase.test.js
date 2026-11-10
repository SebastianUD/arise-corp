import { afterEach, expect, it, vi } from 'vitest'

const { createClient } = vi.hoisted(() => ({ createClient: vi.fn(() => ({ auth: {} })) }))
vi.mock('@supabase/supabase-js', () => ({ createClient }))

afterEach(() => { vi.unstubAllEnvs(); vi.resetModules(); vi.clearAllMocks() })

it('permite abrir la aplicación sin configuración ni solicitudes externas', async () => {
  vi.stubEnv('VITE_SUPABASE_URL', '')
  vi.stubEnv('VITE_SUPABASE_PUBLISHABLE_KEY', '')
  const { supabase, configurationError } = await import('./supabase.js')
  expect(supabase).toBeNull()
  expect(configurationError).toMatch('El inicio de sesión aún no está disponible')
  expect(createClient).not.toHaveBeenCalled()
})

it('inicializa Supabase únicamente con su configuración pública', async () => {
  vi.stubEnv('VITE_SUPABASE_URL', 'https://example.supabase.co')
  vi.stubEnv('VITE_SUPABASE_PUBLISHABLE_KEY', 'sb_publishable_TEST_PUBLIC_KEY')
  const { configurationError } = await import('./supabase.js')
  expect(configurationError).toBeNull()
  expect(createClient).toHaveBeenCalledWith('https://example.supabase.co', 'sb_publishable_TEST_PUBLIC_KEY', {
    global: { fetch: expect.any(Function) },
  })
})
