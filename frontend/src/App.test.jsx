import { StrictMode } from 'react'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import App from './App.jsx'

const { auth, store } = vi.hoisted(() => ({
  auth: { getSession: vi.fn(), signInWithPassword: vi.fn(), signOut: vi.fn(), onAuthStateChange: vi.fn() },
  store: { session: null, listeners: new Set(), unsubscribe: vi.fn() },
}))
vi.mock('./lib/supabase.js', () => ({ supabase: { auth }, configurationError: null }))

const session = { access_token: 'fake-access-token', expires_at: 4_000_000_000, user: { id: 'test-user' } }
const profile = { id: 'test-user', email: 'paula@example.test', role: 'authenticated' }
const emit = (event, value) => {
  store.session = value
  store.listeners.forEach(listener => listener(event, value))
}

beforeEach(() => {
  vi.clearAllMocks()
  store.session = null
  store.listeners.clear()
  vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080')
  auth.getSession.mockImplementation(async () => ({ data: { session: store.session }, error: null }))
  auth.onAuthStateChange.mockImplementation(listener => {
    store.listeners.add(listener)
    return { data: { subscription: { unsubscribe: () => {
      store.unsubscribe()
      store.listeners.delete(listener)
    } } } }
  })
  auth.signInWithPassword.mockImplementation(async () => {
    emit('SIGNED_IN', session)
    return { data: { session }, error: null }
  })
  auth.signOut.mockImplementation(async () => {
    emit('SIGNED_OUT', null)
    return { error: null }
  })
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => profile }))
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

function mount(path = '/login', strict = false) {
  const application = <MemoryRouter initialEntries={[path]}><App /></MemoryRouter>
  return render(strict ? <StrictMode>{application}</StrictMode> : application)
}

async function loginForm() {
  await screen.findByRole('heading', { name: 'Bienvenido de nuevo' })
  fireEvent.change(screen.getByLabelText(/^Correo electrónico/), { target: { value: 'paula@example.test' } })
  fireEvent.change(screen.getByLabelText(/^Contraseña/), { target: { value: 'fake-password' } })
}

const submit = () => fireEvent.submit(screen.getByRole('form', { name: 'Inicio de sesión' }))

it('redirige una ruta privada sin sesión sin mostrar el perfil', async () => {
  mount('/perfil')
  expect(screen.queryByText('Mi perfil')).not.toBeInTheDocument()
  await screen.findByRole('heading', { name: 'Bienvenido de nuevo' })
  expect(fetch).not.toHaveBeenCalled()
})

it('mantiene el contenido privado oculto mientras restaura la sesión', async () => {
  auth.getSession.mockReturnValue(new Promise(() => {}))
  mount('/perfil')
  expect(screen.getByRole('status')).toHaveTextContent('Comprobando sesión')
  expect(screen.queryByText('Mi perfil')).not.toBeInTheDocument()
})

it('valida campos obligatorios sin solicitar autenticación', async () => {
  mount()
  await screen.findByRole('heading', { name: 'Bienvenido de nuevo' })
  submit()
  expect(screen.getByText('El correo es obligatorio.')).toBeVisible()
  expect(screen.getByText('La contraseña es obligatoria.')).toBeVisible()
  expect(auth.signInWithPassword).not.toHaveBeenCalled()
})

it('valida el formato del correo antes de llamar a Supabase', async () => {
  mount()
  await loginForm()
  fireEvent.change(screen.getByLabelText(/^Correo electrónico/), { target: { value: 'correo-invalido' } })
  submit()
  expect(screen.getByText('Introduce un correo válido.')).toBeVisible()
  expect(auth.signInWithPassword).not.toHaveBeenCalled()
})

it('completa Login → sesión → perfil y envía el Bearer a la API', async () => {
  mount()
  await loginForm()
  submit()
  await screen.findByText('paula@example.test')
  expect(screen.getByText('test-user')).toBeVisible()
  expect(auth.signInWithPassword).toHaveBeenCalledWith({ email: 'paula@example.test', password: 'fake-password' })
  expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/me', expect.objectContaining({
    headers: { Authorization: 'Bearer fake-access-token' },
  }))
})

it('deshabilita el envío y no duplica solicitudes de login pendientes', async () => {
  auth.signInWithPassword.mockReturnValue(new Promise(() => {}))
  mount()
  await loginForm()
  submit()
  submit()
  expect(screen.getByRole('button', { name: 'Iniciando sesión…' })).toBeDisabled()
  expect(screen.getByRole('status')).toHaveTextContent('Comprobando tus credenciales')
  await waitFor(() => expect(auth.signInWithPassword).toHaveBeenCalledTimes(1))
})

it('muestra un error genérico y conserva el correo ante credenciales rechazadas', async () => {
  auth.signInWithPassword.mockResolvedValue({ data: {}, error: new Error('internal-detail') })
  mount()
  await loginForm()
  submit()
  expect(await screen.findByRole('alert')).toHaveTextContent('Revisa tus credenciales y tu conexión')
  expect(screen.getByLabelText(/^Correo electrónico/)).toHaveValue('paula@example.test')
  expect(screen.queryByText(/internal-detail/)).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Iniciar sesión' })).toBeEnabled()
})

it('libera el formulario al cumplir 30 segundos y bloquea un login tardío', async () => {
  let finish
  auth.signInWithPassword.mockImplementation(() => new Promise(resolve => { finish = resolve }))
  mount()
  await loginForm()
  vi.useFakeTimers()
  submit()
  await act(async () => { await vi.advanceTimersByTimeAsync(30_001) })
  expect(screen.getByRole('alert')).toHaveTextContent('No pudimos iniciar sesión')
  expect(screen.getByRole('button', { name: 'Iniciar sesión' })).toBeEnabled()
  await act(async () => {
    emit('SIGNED_IN', session)
    finish({ data: { session }, error: null })
  })
  expect(screen.queryByText('Mi perfil')).not.toBeInTheDocument()
})

it('restaura la sesión sin duplicar /api/me bajo StrictMode', async () => {
  store.session = session
  let resolveResponse
  fetch.mockImplementation(() => new Promise(resolve => { resolveResponse = resolve }))
  mount('/perfil', true)
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1))
  expect(screen.getByRole('status')).toHaveTextContent('Cargando tu información')
  await act(async () => resolveResponse({ ok: true, status: 200, json: async () => profile }))
  expect(await screen.findByText('paula@example.test')).toBeVisible()
  expect(fetch).toHaveBeenCalledTimes(1)
})

it('cierra sesión y elimina la vista privada aunque Supabase falle', async () => {
  store.session = session
  auth.signOut.mockRejectedValue(new Error('offline'))
  mount('/perfil')
  await screen.findByText('paula@example.test')
  fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))
  await screen.findByRole('heading', { name: 'Bienvenido de nuevo' })
  expect(screen.queryByText('Mi perfil')).not.toBeInTheDocument()
  expect(auth.signOut).toHaveBeenCalledWith({ scope: 'local' })
  await act(async () => emit('TOKEN_REFRESHED', session))
  expect(screen.queryByText('Mi perfil')).not.toBeInTheDocument()
})

it('descarta la sesión y explica su expiración ante un 401', async () => {
  store.session = session
  fetch.mockResolvedValue({ status: 401, ok: false })
  mount('/perfil')
  await screen.findByRole('heading', { name: 'Bienvenido de nuevo' })
  expect(screen.getByRole('alert')).toHaveTextContent('Tu sesión expiró')
  expect(auth.signOut).toHaveBeenCalledWith({ scope: 'local' })
})

it('muestra falta de permisos ante un 403 sin cerrar sesión', async () => {
  store.session = session
  fetch.mockResolvedValue({ status: 403, ok: false })
  mount('/perfil')
  expect(await screen.findByRole('alert')).toHaveTextContent('No tienes permisos')
  expect(screen.getByRole('heading', { name: 'Mi perfil' })).toBeVisible()
  expect(auth.signOut).not.toHaveBeenCalled()
})

it('permite reintentar después de un fallo de red usando el token renovado', async () => {
  store.session = session
  fetch.mockRejectedValueOnce(new Error('offline'))
  mount('/perfil')
  expect(await screen.findByRole('alert')).toHaveTextContent('Revisa tu conexión')
  await act(async () => emit('TOKEN_REFRESHED', { ...session, access_token: 'fake-renewed-token' }))
  fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
  expect(await screen.findByText('paula@example.test')).toBeVisible()
  expect(fetch).toHaveBeenLastCalledWith('http://localhost:8080/api/me', expect.objectContaining({
    headers: { Authorization: 'Bearer fake-renewed-token' },
  }))
})

it('respeta una invalidación de sesión mientras la ruta está abierta', async () => {
  store.session = session
  mount('/perfil')
  await screen.findByText('paula@example.test')
  await act(async () => emit('SIGNED_OUT', null))
  expect(await screen.findByRole('heading', { name: 'Bienvenido de nuevo' })).toBeVisible()
})

it('no restaura una sesión obsoleta después de un evento de cierre', async () => {
  let restore
  auth.getSession.mockImplementation(() => new Promise(resolve => { restore = resolve }))
  mount('/perfil')
  await act(async () => {})
  await act(async () => emit('SIGNED_OUT', null))
  await act(async () => restore({ data: { session }, error: null }))
  expect(screen.getByRole('heading', { name: 'Bienvenido de nuevo' })).toBeVisible()
  expect(fetch).not.toHaveBeenCalled()
})

it('redirige una sesión vencida sin un bucle de navegación', async () => {
  store.session = { ...session, expires_at: 1 }
  mount('/perfil')
  await screen.findByRole('heading', { name: 'Bienvenido de nuevo' })
  expect(screen.getByRole('alert')).toHaveTextContent('Tu sesión expiró')
  expect(fetch).not.toHaveBeenCalled()
})

it('retira la suscripción de autenticación al desmontar', async () => {
  const view = mount()
  await screen.findByRole('heading', { name: 'Bienvenido de nuevo' })
  view.unmount()
  expect(store.unsubscribe).toHaveBeenCalledTimes(1)
})
