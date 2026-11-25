import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import * as api from '../../services/sgaApi.js'
import { abrirSga, cliente, contrato, dron, paquete, pagina } from '../../test/datosSga.jsx'
import PaqueteDetalle from './PaqueteDetalle.jsx'

vi.mock('../../services/sgaApi.js')
const registro = { id: 'h1', estadoAnterior: null, estadoNuevo: 'REGISTRADO', dron: null, observacion: 'Recibido', registradoEn: '2026-11-24T15:00:00Z', registradoPor: 'actor-ficticio' }
beforeEach(() => {
  vi.resetAllMocks()
  api.obtenerPaquete.mockResolvedValue(paquete)
  api.obtenerHistorialPaquete.mockResolvedValue([registro])
  api.listarDrones.mockResolvedValue(pagina([dron]))
})
const destinos = { REGISTRADO: ['Asignar dron', 'Cancelar paquete'], ASIGNADO: ['Marcar en tránsito', 'Cancelar paquete'], EN_TRANSITO: ['Marcar como entregado', 'Marcar como devuelto'], ENTREGADO: [], CANCELADO: [], DEVUELTO: [] }
it.each(Object.keys(destinos))('Feature: 004-sga-drones-paquetes-inventario, Property 1: Solo se aplican las transiciones permitidas del paquete (%s)', async estado => {
  api.obtenerPaquete.mockResolvedValue({ ...paquete, estado })
  abrirSga(PaqueteDetalle, '/sga/paquetes/' + paquete.id, '/sga/paquetes/:id')
  await screen.findByRole('heading', { name: paquete.codigo })
  const editable = ['REGISTRADO', 'ASIGNADO'].includes(estado)
  expect(Boolean(screen.queryByRole('link', { name: 'Editar' }))).toBe(editable)
  expect(Boolean(screen.queryByRole('heading', { name: 'Cambiar estado' }))).toBe(destinos[estado].length > 0)
  if (destinos[estado].length) {
    expect(within(screen.getByLabelText(/^Nuevo estado/)).getAllByRole('option').slice(1).map(o => o.textContent)).toEqual(destinos[estado])
  }
})
it('exige un dron asignable y recarga paquete e historial después del 201', async () => {
  api.cambiarEstadoPaquete.mockResolvedValue({ ...registro, estadoNuevo: 'ASIGNADO' })
  abrirSga(PaqueteDetalle, '/sga/paquetes/' + paquete.id, '/sga/paquetes/:id')
  fireEvent.change(await screen.findByLabelText(/^Nuevo estado/), { target: { value: 'ASIGNADO' } })
  await screen.findByRole('option', { name: /DRN-001/ })
  expect(api.listarDrones).toHaveBeenCalledWith({ asignable: true, size: 100, sort: 'codigo,asc' })
  fireEvent.click(screen.getByRole('button', { name: 'Actualizar estado' }))
  expect(api.cambiarEstadoPaquete).not.toHaveBeenCalled()
  expect(screen.getByLabelText(/^Dron asignable/)).toHaveAttribute('aria-invalid', 'true')
  fireEvent.change(screen.getByLabelText(/^Dron asignable/), { target: { value: dron.id } })
  api.obtenerPaquete.mockResolvedValue({ ...paquete, estado: 'ASIGNADO', dron })
  fireEvent.click(screen.getByRole('button', { name: 'Actualizar estado' }))
  await waitFor(() => expect(api.obtenerPaquete).toHaveBeenCalledTimes(2))
  expect(api.obtenerHistorialPaquete).toHaveBeenCalledTimes(2)
  expect(api.cambiarEstadoPaquete).toHaveBeenCalledWith(paquete.id, { estado: 'ASIGNADO', dronId: dron.id, observacion: '' })
})
it.each(['ENTREGADO', 'DEVUELTO', 'CANCELADO'])('confirma el destino final %s y recarga tras un 409', async estado => {
  api.obtenerPaquete.mockResolvedValue({ ...paquete, estado: estado === 'CANCELADO' ? 'REGISTRADO' : 'EN_TRANSITO', dron })
  api.cambiarEstadoPaquete.mockRejectedValue({ status: 409, message: 'El paquete cambió de estado.' })
  abrirSga(PaqueteDetalle, '/sga/paquetes/' + paquete.id, '/sga/paquetes/:id')
  fireEvent.change(await screen.findByLabelText(/^Nuevo estado/), { target: { value: estado } })
  fireEvent.click(screen.getByRole('button', { name: 'Actualizar estado' }))
  expect(screen.getByText('Este cambio es irreversible.')).toBeVisible()
  expect(api.cambiarEstadoPaquete).not.toHaveBeenCalled()
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('El paquete cambió de estado.')
  await waitFor(() => expect(api.obtenerHistorialPaquete).toHaveBeenCalledTimes(2))
})
it('muestra historial en orden y enlaces a cliente, contrato y dron', async () => {
  api.obtenerPaquete.mockResolvedValue({ ...paquete, estado: 'ASIGNADO', dron })
  api.obtenerHistorialPaquete.mockResolvedValue([registro, { ...registro, id: 'h2', estadoAnterior: 'REGISTRADO', estadoNuevo: 'ASIGNADO', dron, observacion: 'Salida', registradoEn: '2026-11-24T16:00:00Z' }])
  abrirSga(PaqueteDetalle, '/sga/paquetes/' + paquete.id, '/sga/paquetes/:id')
  await screen.findByRole('heading', { name: paquete.codigo })
  expect(screen.getByRole('link', { name: cliente.nombre })).toHaveAttribute('href', '/crm/clientes/' + cliente.id)
  expect(screen.getByRole('link', { name: contrato.codigo })).toHaveAttribute('href', '/crm/contratos/' + contrato.id)
  expect(screen.getAllByRole('link', { name: dron.codigo })[0]).toHaveAttribute('href', '/sga/drones/' + dron.id)
  const filas = within(screen.getByRole('table')).getAllByRole('row')
  expect(filas[1]).toHaveTextContent('Recibido'); expect(filas[2]).toHaveTextContent('Salida')
})
