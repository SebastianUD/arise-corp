import { act, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterAll, expect, it } from 'vitest'
import App from '../src/App.jsx'
import { supabase } from '../src/lib/supabase.js'
import { listarClientes, listarContratos } from '../src/services/crmApi.js'
import { listarDrones, listarPaquetes, obtenerHistorialPaquete, obtenerDron, obtenerResumenSga,
  crearArticulo, registrarMovimientoInventario, obtenerArticulo, listarMovimientosInventario } from '../src/services/sgaApi.js'

// Validación manual con las vistas React, Auth, API y PostgreSQL reales.
// jsdom comprueba comportamiento y persistencia; no sustituye la revisión visual.
// No volcar el DOM del login a los logs si falla la autenticación.
configure({ asyncUtilTimeout: 20_000, getElementError: mensaje => new Error(mensaje) })
const abrir = ruta => render(<MemoryRouter initialEntries={[ruta]}><App /></MemoryRouter>)
const escribir = (etiqueta, value) => fireEvent.change(screen.getByLabelText(etiqueta), { target: { value } })
const guardar = () => fireEvent.click(screen.getByRole('button', { name: 'Guardar', exact: true }))
const nombre = 'Distribuciones Alto Bosque S.A.S.'
const sufijo = Date.now().toString().slice(-8)
let contratoCodigo = 'CTR-AB-' + sufijo
let dronCodigo = 'DRN-AB-' + sufijo
const paqueteCodigo = 'ENV-AB-' + sufijo

afterAll(async () => { if (supabase) { await supabase.auth.signOut({ scope: 'local' }); supabase.auth.stopAutoRefresh() } })

it('CA-05 real: Comercial registra cliente y contrato; Operaciones entrega y recupera historial persistido', async () => {
  expect(process.env.ARISE_QA_PASSWORD, 'Se requiere contraseña por stdin').toBeTruthy()
  let vista = abrir('/login')
  await screen.findByRole('heading', { name: 'Bienvenido de nuevo' })
  escribir(/^Correo electrónico/, 'comercial@gmail.com')
  escribir(/^Contraseña/, process.env.ARISE_QA_PASSWORD)
  fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión', exact: true }))
  await screen.findByRole('heading', { name: 'Tu operación, conectada.' })
  let cliente = (await listarClientes({ q: nombre })).content.find(c => c.nombre === nombre)
  let contrato = cliente && (await listarContratos({ clienteId: cliente.id, estado: 'ACTIVO' })).content.find(c => c.codigo.startsWith('CTR-AB-'))
  // Permite recuperar un intento interrumpido sin duplicar cliente o contrato.
  if (!cliente) {
    vista.unmount(); vista = abrir('/crm/clientes/nuevo')
    await screen.findByLabelText(/^Nombre/)
    escribir(/^Nombre/, nombre); escribir(/^Tipo de documento/, 'NIT'); escribir(/^Número de documento/, '901' + sufijo)
    escribir(/^Correo electrónico/, 'operaciones.altobosque@example.com'); escribir(/^Teléfono/, '6015550199')
    escribir(/^Dirección/, 'Carrera 45 # 93-20'); escribir(/^Ciudad/, 'Bogotá'); guardar()
    await screen.findByRole('heading', { name: nombre })
    cliente = (await listarClientes({ q: nombre })).content.find(c => c.nombre === nombre)
  }
  if (!contrato) {
    vista.unmount(); vista = abrir('/crm/clientes/' + cliente.id)
    fireEvent.click(await screen.findByRole('link', { name: 'Nuevo contrato', exact: true }))
    await screen.findByLabelText(/^Código/)
    escribir(/^Código/, contratoCodigo); escribir(/^Descripción/, 'Distribución urbana de suministros empresariales')
    escribir(/^Fecha de inicio/, new Date().toLocaleDateString('en-CA', { timeZone: 'America/Bogota' }))
    escribir(/^Valor en COP/, '2400000'); guardar()
    fireEvent.click(await screen.findByRole('button', { name: 'Activar', exact: true }))
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Activar', exact: true })).toBeNull())
    contrato = (await listarContratos({ q: contratoCodigo })).content.find(c => c.codigo === contratoCodigo)
  }
  contratoCodigo = contrato.codigo
  expect(contrato.estado).toBe('ACTIVO')
  vista.unmount()
  await act(async () => {
    await supabase.auth.signOut({ scope: 'local' })
    const { error } = await supabase.auth.signInWithPassword({ email: 'operaciones@gmail.com', password: process.env.ARISE_QA_PASSWORD })
    if (error) throw new Error('No fue posible autenticar Operaciones')
  })
  let dron = (await listarDrones({ q: 'DRN-AB-', asignable: true })).content[0]
  if (!dron) {
    vista = abrir('/sga/drones/nuevo')
    await screen.findByLabelText(/^Código/)
    escribir(/^Código/, dronCodigo); escribir(/^Modelo/, 'Arise Courier X1'); guardar()
    await screen.findByRole('heading', { name: dronCodigo })
    dron = (await listarDrones({ q: dronCodigo })).content.find(d => d.codigo === dronCodigo)
    vista.unmount()
  }
  dronCodigo = dron.codigo
  vista = abrir('/sga/paquetes/nuevo')
  await screen.findByLabelText(/^Código/)
  // El selector tiene página de 20: buscar el cliente registrado.
  fireEvent.change(within(screen.getByRole('form', { name: 'Datos del paquete' })).getByLabelText('Buscar cliente'), { target: { value: nombre } })
  fireEvent.click(screen.getByRole('button', { name: 'Buscar', exact: true }))
  await screen.findByRole('option', { name: nombre })
  escribir(/^Código/, paqueteCodigo); escribir(/^Descripción/, 'Suministros para oficina y distribución urbana')
  escribir(/^Dirección de entrega/, 'Carrera 45 # 93-20, Bogotá'); escribir('Cliente', cliente.id)
  await screen.findByRole('option', { name: contratoCodigo }); escribir('Contrato', contrato.id)
  escribir('Observación inicial', 'Recibido en el centro de distribución'); guardar()
  await screen.findByRole('heading', { name: paqueteCodigo })
  escribir(/^Nuevo estado/, 'ASIGNADO')
  await screen.findByRole('option', { name: new RegExp(dronCodigo) }); escribir(/^Dron asignable/, dron.id)
  fireEvent.click(screen.getByRole('button', { name: 'Actualizar estado' }))
  await screen.findByRole('option', { name: 'Marcar en tránsito' })
  escribir(/^Nuevo estado/, 'EN_TRANSITO'); fireEvent.click(screen.getByRole('button', { name: 'Actualizar estado' }))
  await screen.findByRole('option', { name: 'Marcar como entregado' })
  expect((await obtenerDron(dron.id)).estado).toBe('EN_MISION')
  escribir(/^Nuevo estado/, 'ENTREGADO'); fireEvent.click(screen.getByRole('button', { name: 'Actualizar estado' }))
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar' }))
  await waitFor(() => expect(screen.queryByRole('heading', { name: 'Cambiar estado', exact: true })).toBeNull())
  const paquete = (await listarPaquetes({ q: paqueteCodigo })).content.find(p => p.codigo === paqueteCodigo)
  vista.unmount(); vista = abrir('/sga/paquetes/' + paquete.id)
  const tabla = await screen.findByRole('table', { name: 'Historial de estados' })
  expect(within(tabla).getAllByRole('row')).toHaveLength(5)
  const historial = await obtenerHistorialPaquete(paquete.id)
  expect(historial.map(h => h.estadoNuevo)).toEqual(['REGISTRADO','ASIGNADO','EN_TRANSITO','ENTREGADO'])
  expect(historial.map(h => h.estadoAnterior)).toEqual([null,'REGISTRADO','ASIGNADO','EN_TRANSITO'])
  expect((await obtenerDron(dron.id)).estado).toBe('DISPONIBLE')
  expect((await obtenerDron(dron.id)).paqueteEnCurso).toBeNull()
  fireEvent.click(screen.getByRole('link', { name: nombre, exact: true }))
  await screen.findByRole('heading', { name: nombre })
  expect(screen.queryByRole('link', { name: 'Editar', exact: true })).toBeNull()
  fireEvent.click(await screen.findByRole('link', { name: contratoCodigo, exact: true }))
  await screen.findByRole('heading', { name: contratoCodigo })
  expect(screen.queryByRole('button', { name: 'Finalizar' })).toBeNull()
  vista.unmount()

  const articulo = await crearArticulo({ codigo: 'INV-AB-' + sufijo, nombre: 'Bolsa de seguridad para documentos', categoria: 'EMPAQUE', unidad: 'UNIDAD', stockMinimo: 8 })
  await registrarMovimientoInventario(articulo.id, { tipo:'ENTRADA', cantidad:10, observacion:'Recepción de empaques de seguridad' })
  await registrarMovimientoInventario(articulo.id, { tipo:'SALIDA', cantidad:4, observacion:'Preparación de envíos empresariales' })
  await expect(registrarMovimientoInventario(articulo.id, { tipo:'SALIDA', cantidad:20 })).rejects.toMatchObject({ status:409 })
  expect((await obtenerArticulo(articulo.id)).existencia).toBe(6)
  expect((await listarMovimientosInventario(articulo.id)).totalElements).toBe(2)
  const resumen = await obtenerResumenSga()
  expect(resumen).toBeTruthy()
  console.log('CA-05 real aprobado: vistas React, Supabase Auth, backend, historial persistido y dron disponible. Inventario: 10-4=6, salida de 20 rechazada con 409.')
})
