import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import App from './App.jsx'
import { cliente, contrato, dron, paquete, pagina } from './test/datosSga.jsx'

const { auth } = vi.hoisted(() => ({ auth: { getSession: vi.fn(), onAuthStateChange: vi.fn(), signOut: vi.fn() } }))
vi.mock('./lib/supabase.js', () => ({ supabase: { auth }, configurationError: null }))
beforeEach(() => {
  vi.resetAllMocks()
  vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080')
  auth.getSession.mockResolvedValue({ data: { session: { access_token: 'token-ficticio', expires_at: 4000000000, user: { id: 'usuario-ficticio' } } } })
  auth.onAuthStateChange.mockReturnValue({ data: { subscription: { unsubscribe() {} } } })
})
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

const abrir = ruta => render(<MemoryRouter initialEntries={[ruta]}><App /></MemoryRouter>)
const escribir = (etiqueta, value) => fireEvent.change(screen.getByLabelText(etiqueta), { target: { value } })
const guardar = () => fireEvent.click(screen.getByRole('button', { name: 'Guardar', exact: true }))

it('CA-05 simulado: Comercial crea cliente y contrato; Operaciones entrega el paquete y consulta historial y dron tras recargar', async () => {
  // Solo sustituye autenticación y transporte HTTP; usa App, rutas, formularios,
  // permisos y servicios reales. No conecta ni escribe en Supabase ni Spring Boot.
  let rol = 'COMERCIAL'
  let clienteActual = null
  let contratoActual = null
  let dronActual = null
  let paqueteActual = null
  const historial = []
  const fecha = '2026-11-24T15:00:00Z'
  function registrar(estadoAnterior, estadoNuevo, observacion) {
    const registro = { id: 'historial-' + historial.length, estadoAnterior, estadoNuevo, observacion,
      dron: estadoNuevo === 'ASIGNADO' ? { id: dronActual.id, codigo: dronActual.codigo } : null,
      registradoEn: fecha, registradoPor: 'actor-ficticio' }
    historial.push(registro)
    return registro
  }
  vi.stubGlobal('fetch', vi.fn(async (url, options) => {
    const path = new URL(url).pathname
    const cuerpo = options.body ? JSON.parse(options.body) : null
    const metodo = options.method
    let datos
    let status = 200
    if (path === '/api/me') datos = { id: 'usuario-ficticio', email: 'prueba@example.test', role: rol }
    else if (path === '/api/crm/cuentas-corporativas') datos = pagina([])
    else if (path === '/api/crm/clientes' && metodo === 'POST') {
      clienteActual = { ...cliente, ...cuerpo }; datos = clienteActual; status = 201
    } else if (path === '/api/crm/clientes/' + cliente.id) datos = clienteActual
    else if (path === '/api/crm/clientes') datos = pagina(clienteActual ? [clienteActual] : [])
    else if (path === '/api/crm/contratos' && metodo === 'POST') {
      contratoActual = { ...contrato, ...cuerpo, estado: 'BORRADOR', cliente: clienteActual, estadoActualizadoEn: fecha }; datos = contratoActual; status = 201
    } else if (path === '/api/crm/contratos/' + contrato.id + '/estado') {
      contratoActual = { ...contratoActual, estado: cuerpo.estado }; datos = contratoActual
    } else if (path === '/api/crm/contratos/' + contrato.id) datos = contratoActual
    else if (path === '/api/crm/contratos') datos = pagina(contratoActual ? [contratoActual] : [])
    else if (path === '/api/sga/drones' && metodo === 'POST') {
      dronActual = { ...dron, ...cuerpo }; datos = dronActual; status = 201
    } else if (path === '/api/sga/drones/' + dron.id) datos = dronActual
    else if (path === '/api/sga/drones') datos = pagina(dronActual ? [dronActual] : [])
    else if (path === '/api/sga/paquetes' && metodo === 'POST') {
      paqueteActual = { ...paquete, ...cuerpo, cliente: clienteActual, contrato: contratoActual }
      registrar(null, 'REGISTRADO', cuerpo.observacion); datos = paqueteActual; status = 201
    } else if (path === '/api/sga/paquetes/' + paquete.id + '/estados' && metodo === 'POST') {
      datos = registrar(paqueteActual.estado, cuerpo.estado, cuerpo.observacion)
      if (cuerpo.estado === 'ASIGNADO') {
        expect(cuerpo.dronId).toBe(dron.id)
        dronActual = { ...dronActual, paqueteEnCurso: { id: paquete.id, codigo: paquete.codigo, estado: cuerpo.estado } }
      } else {
        expect(cuerpo).not.toHaveProperty('dronId')
        dronActual = { ...dronActual, estado: cuerpo.estado === 'EN_TRANSITO' ? 'EN_MISION' : 'DISPONIBLE', paqueteEnCurso: cuerpo.estado === 'ENTREGADO' ? null : dronActual.paqueteEnCurso }
      }
      paqueteActual = { ...paqueteActual, estado: cuerpo.estado, dron: dronActual }; status = 201
    } else if (path === '/api/sga/paquetes/' + paquete.id + '/estados') datos = historial
    else if (path === '/api/sga/paquetes/' + paquete.id) datos = paqueteActual
    else if (path === '/api/sga/paquetes') datos = pagina(paqueteActual ? [paqueteActual] : [])
    else throw new Error('Petición inesperada en la simulación: ' + metodo + ' ' + path)
    const copia = structuredClone(datos)
    return { ok: true, status, json: async () => copia }
  }))

  let vista = abrir('/crm/clientes/nuevo')
  await screen.findByLabelText(/^Nombre/)
  escribir(/^Nombre/, cliente.nombre); escribir(/^Número de documento/, '000123')
  escribir(/^Correo electrónico/, 'cliente@example.test'); escribir(/^Teléfono/, '601 000 0000')
  guardar()
  fireEvent.click(await screen.findByRole('link', { name: 'Nuevo contrato' }))
  await screen.findByLabelText(/^Código/)
  escribir(/^Código/, contrato.codigo); escribir(/^Descripción/, 'Contrato ficticio de entregas')
  escribir(/^Fecha de inicio/, '2026-11-24'); escribir(/^Valor en COP/, '1000.00')
  guardar()
  fireEvent.click(await screen.findByRole('button', { name: 'Activar', exact: true }))
  await waitFor(() => expect(screen.queryByRole('button', { name: 'Activar', exact: true })).toBeNull())
  expect(contratoActual.estado).toBe('ACTIVO')

  vista.unmount(); rol = 'OPERACIONES'
  vista = abrir('/sga/drones/nuevo')
  await screen.findByLabelText(/^Código/)
  escribir(/^Código/, dron.codigo); escribir(/^Modelo/, dron.modelo); guardar()
  await screen.findByRole('heading', { name: dron.codigo })
  fireEvent.click(screen.getByRole('button', { name: 'SGA', exact: true }))
  fireEvent.click(screen.getByRole('link', { name: 'Paquetes', exact: true }))
  fireEvent.click(await screen.findByRole('link', { name: 'Nuevo paquete' }))
  await screen.findByRole('option', { name: cliente.nombre })
  escribir(/^Código/, paquete.codigo); escribir(/^Descripción/, paquete.descripcion)
  escribir(/^Dirección de entrega/, paquete.direccionEntrega); escribir('Cliente', cliente.id)
  await screen.findByRole('option', { name: contrato.codigo })
  escribir('Contrato', contrato.id); escribir('Observación inicial', 'Recibido en bodega'); guardar()
  await screen.findByRole('heading', { name: paquete.codigo })

  escribir(/^Nuevo estado/, 'ASIGNADO')
  await screen.findByRole('option', { name: new RegExp(dron.codigo) })
  escribir(/^Dron asignable/, dron.id)
  fireEvent.click(screen.getByRole('button', { name: 'Actualizar estado' }))
  await screen.findByRole('option', { name: 'Marcar en tránsito' })
  escribir(/^Nuevo estado/, 'EN_TRANSITO')
  fireEvent.click(screen.getByRole('button', { name: 'Actualizar estado' }))
  await screen.findByRole('option', { name: 'Marcar como entregado' })
  escribir(/^Nuevo estado/, 'ENTREGADO')
  fireEvent.click(screen.getByRole('button', { name: 'Actualizar estado' }))
  expect(screen.getByText('Este cambio es irreversible.')).toBeVisible()
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar' }))
  await waitFor(() => expect(screen.queryByRole('heading', { name: 'Cambiar estado', exact: true })).toBeNull())
  await screen.findByRole('table', { name: 'Historial de estados' })

  vista.unmount(); vista = abrir('/sga/paquetes/' + paquete.id)
  const tabla = await screen.findByRole('table', { name: 'Historial de estados' })
  expect(within(tabla).getAllByRole('row')).toHaveLength(5)
  expect(historial.map(r => r.estadoNuevo)).toEqual(['REGISTRADO', 'ASIGNADO', 'EN_TRANSITO', 'ENTREGADO'])
  expect(historial.map(r => r.estadoAnterior)).toEqual([null, 'REGISTRADO', 'ASIGNADO', 'EN_TRANSITO'])
  expect(screen.queryByRole('link', { name: 'Editar', exact: true })).toBeNull()
  fireEvent.click(screen.getByRole('link', { name: cliente.nombre, exact: true }))
  await screen.findByRole('heading', { name: cliente.nombre })
  expect(screen.queryByRole('link', { name: 'Editar', exact: true })).toBeNull()
  fireEvent.click(await screen.findByRole('link', { name: contrato.codigo, exact: true }))
  await screen.findByRole('heading', { name: contrato.codigo })
  expect(screen.queryByRole('button', { name: 'Finalizar' })).toBeNull()

  vista.unmount(); vista = abrir('/sga/paquetes/' + paquete.id)
  await screen.findByRole('heading', { name: paquete.codigo })
  fireEvent.click(screen.getAllByRole('link', { name: dron.codigo, exact: true })[0])
  await screen.findByRole('heading', { name: dron.codigo })
  expect(screen.getByText('Disponible', { exact: true })).toBeVisible()
  expect(dronActual.paqueteEnCurso).toBeNull()
  expect(auth.signOut).not.toHaveBeenCalled()
  expect(fetch.mock.calls.filter(([url, o]) => o.method === 'POST' && new URL(url).pathname.endsWith('/estados'))).toHaveLength(3)
})
