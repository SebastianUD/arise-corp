import { StrictMode, useEffect } from 'react'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import App from './App.jsx'
import { PerfilProvider } from './context/PerfilContext.jsx'
import { puedeGestionarCrm } from './lib/permisos.js'
import RolRoute from './routes/RolRoute.jsx'

const { auth, store } = vi.hoisted(() => ({
  auth: { getSession: vi.fn(), signInWithPassword: vi.fn(), signOut: vi.fn(), onAuthStateChange: vi.fn() },
  store: { session: null, listeners: new Set(), unsubscribe: vi.fn() },
}))
vi.mock('./lib/supabase.js', () => ({ supabase: { auth }, configurationError: null }))

const session = { access_token: 'fake-access-token', expires_at: 4_000_000_000, user: { id: 'test-user' } }
const profile = { id: 'test-user', email: 'paula@example.test', role: 'COMERCIAL' }
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
  expect(screen.getByText('Rol empresarial')).toBeVisible()
  expect(screen.getByText('Comercial')).toBeVisible()
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

it('explica el perfil sin permiso ante un 403 sin cerrar sesión ni repetir la petición', async () => {
  store.session = session
  fetch.mockResolvedValue({ status: 403, ok: false })
  mount('/perfil')
  expect(await screen.findByRole('alert')).toHaveTextContent('Tu cuenta no tiene un perfil activo o permisos para acceder. Contacta al administrador.')
  expect(screen.getByRole('heading', { name: 'Mi perfil' })).toBeVisible()
  expect(screen.queryByText('Rol empresarial')).not.toBeInTheDocument()
  expect(screen.queryByText('paula@example.test')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Volver al inicio de sesión' })).toBeVisible()
  expect(fetch).toHaveBeenCalledTimes(1)
  expect(auth.signOut).not.toHaveBeenCalled()
})

it.each([
  ['ADMINISTRADOR', 'Administrador'],
  ['COMERCIAL', 'Comercial'],
  ['FINANZAS_RRHH', 'Finanzas / RR. HH.'],
  ['OPERACIONES', 'Operaciones'],
])('muestra el rol empresarial %s recibido de la API', async (role, label) => {
  store.session = session
  fetch.mockResolvedValue({ ok: true, status: 200, json: async () => ({ ...profile, role }) })
  mount('/perfil')
  expect(await screen.findByText(label)).toBeVisible()
  expect(screen.getByText('Rol empresarial')).toBeVisible()
  expect(screen.queryByText('authenticated')).not.toBeInTheDocument()
})

it('permite salir de un perfil sin permiso sin volver a la ruta protegida, incluso sin red', async () => {
  store.session = session
  auth.signOut.mockRejectedValue(new Error('offline'))
  fetch.mockResolvedValue({ status: 403, ok: false })
  mount('/perfil')
  await screen.findByRole('alert')
  fireEvent.click(screen.getByRole('button', { name: 'Volver al inicio de sesión' }))
  await screen.findByRole('heading', { name: 'Bienvenido de nuevo' })
  expect(screen.queryByRole('heading', { name: 'Mi perfil' })).not.toBeInTheDocument()
  expect(auth.signOut).toHaveBeenCalledWith({ scope: 'local' })
  await act(async () => emit('TOKEN_REFRESHED', session))
  expect(screen.getByRole('heading', { name: 'Bienvenido de nuevo' })).toBeVisible()
  expect(fetch).toHaveBeenCalledTimes(1)
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

it.each([
  ['ADMINISTRADOR', ['Clientes', 'Cuentas corporativas', 'Resumen comercial', 'Contratos', 'Usuarios y roles']],
  ['COMERCIAL', ['Clientes', 'Cuentas corporativas', 'Resumen comercial', 'Contratos']],
  ['OPERACIONES', ['Clientes', 'Contratos']],
  ['FINANZAS_RRHH', ['Clientes']],
  [null, []],
])('comparte el perfil con el menú de %s bajo StrictMode', async (role, opciones) => {
  store.session = session
  fetch.mockResolvedValue({ ok: true, status: 200, json: async () => ({ ...profile, role }) })
  mount('/perfil', true)
  await screen.findByText(profile.email)
  expect(screen.getAllByRole('link').map(enlace => enlace.textContent)).toEqual(['Perfil', ...opciones])
  const destinos = {
    Contratos: '/crm/contratos', 'Usuarios y roles': '/admin/usuarios', Perfil: '/perfil', Clientes: '/crm/clientes',
    'Cuentas corporativas': '/crm/cuentas-corporativas', 'Resumen comercial': '/crm/resumen',
  }
  for (const nombre of ['Perfil', ...opciones]) {
    expect(screen.getByRole('link', { name: nombre })).toHaveAttribute('href', destinos[nombre])
  }
  expect(screen.getByRole('link', { name: 'Perfil' })).toHaveAttribute('aria-current', 'page')
  expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible()
  expect(fetch).toHaveBeenCalledTimes(1)
  expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/me', expect.anything())
})

it.each([
  ['OPERACIONES', false], [null, false], ['COMERCIAL', true],
])('al abrir una ruta directamente espera al perfil y aplica el permiso de %s', async (role, permitido) => {
  store.session = session
  let responder
  fetch.mockImplementationOnce(() => new Promise(resolve => { responder = resolve }))
  const consultarVista = vi.fn()
  function Vista() {
    useEffect(() => { consultarVista() }, [])
    return <h1>Resumen comercial</h1>
  }
  render(
    <MemoryRouter initialEntries={['/crm/resumen']}>
      <PerfilProvider>
        <Routes>
          <Route element={<RolRoute permite={puedeGestionarCrm} />}>
            <Route path="/crm/resumen" element={<Vista />} />
          </Route>
        </Routes>
      </PerfilProvider>
    </MemoryRouter>,
  )
  expect(screen.getByRole('status')).toHaveTextContent('Cargando…')
  expect(consultarVista).not.toHaveBeenCalled()
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1))
  await act(async () => responder({ ok: true, status: 200, json: async () => ({ ...profile, role }) }))
  if (permitido) {
    expect(screen.getByRole('heading', { name: 'Resumen comercial' })).toBeVisible()
    expect(consultarVista).toHaveBeenCalledTimes(1)
  } else {
    expect(screen.getByRole('alert')).toHaveTextContent('No tienes permisos para acceder a esta sección.')
    expect(consultarVista).not.toHaveBeenCalled()
  }
})

it.each([
  ['FINANZAS_RRHH', '/crm/clientes/nuevo'],
  ['FINANZAS_RRHH', '/crm/contratos/uno'],
  ['OPERACIONES', '/crm/clientes/uno/editar'],
  ['OPERACIONES', '/crm/clientes/uno/contratos/nuevo'],
  ['OPERACIONES', '/crm/contratos/uno/editar'],
  ['OPERACIONES', '/crm/cuentas-corporativas'],
  ['OPERACIONES', '/crm/cuentas-corporativas/nueva'],
  ['OPERACIONES', '/crm/cuentas-corporativas/uno'],
  ['OPERACIONES', '/crm/cuentas-corporativas/uno/editar'],
  ['OPERACIONES', '/crm/resumen'],
  [null, '/crm/clientes'],
  [null, '/crm/clientes/uno'],
])('bloquea el acceso directo de %s a %s antes de consultar datos CRM', async (role, ruta) => {
  store.session = session
  fetch.mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ ...profile, role }) })
  mount(ruta)
  expect(await screen.findByRole('alert')).toHaveTextContent('No tienes permisos para acceder a esta sección.')
  expect(fetch).toHaveBeenCalledExactlyOnceWith('http://localhost:8080/api/me', expect.anything())
})

it('abre las tres vistas desde el menú comercial compartiendo una sola consulta de perfil', async () => {
  store.session = session
  mount('/perfil')
  await screen.findByText(profile.email)
  const pagina = { content: [], page: 0, totalPages: 0 }
  const resumen = {
    clientes: { activos: 1, inactivos: 2, total: 3 },
    contratos: { porEstado: { BORRADOR: 3, ACTIVO: 2, FINALIZADO: 1, CANCELADO: 0 }, total: 6 },
  }
  for (const [nombre, ruta, datos, texto] of [
    ['Clientes', '/crm/clientes', pagina, 'No hay clientes para los criterios elegidos.'],
    ['Cuentas corporativas', '/crm/cuentas-corporativas', pagina, 'No hay cuentas para los criterios elegidos.'],
    ['Resumen comercial', '/crm/resumen', resumen, 'Finalizado'],
  ]) {
    fetch.mockResolvedValueOnce({ ok: true, status: 200, json: async () => datos })
    fireEvent.click(screen.getByRole('link', { name: nombre }))
    expect(await screen.findByText(texto)).toBeVisible()
    expect(screen.getByRole('link', { name: nombre })).toHaveAttribute('aria-current', 'page')
    expect(fetch).toHaveBeenLastCalledWith('http://localhost:8080/api' + ruta, expect.anything())
  }
  expect(fetch).toHaveBeenCalledTimes(4)
})

it('muestra el 403 del backend en una vista permitida y conserva la sesión al volver al perfil', async () => {
  store.session = session
  const json = vi.fn()
  fetch.mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ ...profile, role: 'OPERACIONES' }) })
    .mockResolvedValueOnce({ ok: false, status: 403, json })
  mount('/crm/contratos/uno')
  expect(await screen.findByRole('alert')).toHaveTextContent('No tienes permisos para consultar esta información.')
  expect(fetch).toHaveBeenLastCalledWith('http://localhost:8080/api/crm/contratos/uno', expect.anything())
  expect(json).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('link', { name: 'Perfil' }))
  expect(await screen.findByText(profile.email)).toBeVisible()
  expect(auth.signOut).not.toHaveBeenCalled()
  expect(fetch).toHaveBeenCalledTimes(2)
})

it('guarda el perfil y el rol desde la cuenta de administrador', async () => {
  const id = '00000000-0000-4000-8000-000000000002'
  store.session = session
  fetch.mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ ...profile, role: 'ADMINISTRADOR' }) })
  fetch.mockResolvedValueOnce({ ok: true, status: 201, json: async () => ({ usuarioId: id, rol: 'OPERACIONES', activo: true }) })
  mount('/admin/usuarios')
  fireEvent.change(await screen.findByLabelText(/Identificador del usuario/), { target: { value: id } })
  fireEvent.change(screen.getByLabelText(/Rol empresarial/), { target: { value: 'OPERACIONES' } })
  fireEvent.click(screen.getByRole('button', { name: 'Guardar rol' }))
  await screen.findByText(/Rol guardado/)
  expect(fetch).toHaveBeenLastCalledWith('http://localhost:8080/api/admin/usuarios/' + id + '/perfil', expect.objectContaining({
    method: 'PUT', body: JSON.stringify({ rol: 'OPERACIONES' }),
  }))
})

it('deniega el acceso directo a usuarios y roles para una cuenta comercial', async () => {
  store.session = session
  mount('/admin/usuarios')
  expect(await screen.findByRole('alert')).toHaveTextContent('No tienes permisos')
  expect(screen.queryByRole('button', { name: 'Guardar rol' })).toBeNull()
  expect(fetch).toHaveBeenCalledTimes(1)
})
