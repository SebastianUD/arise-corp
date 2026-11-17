import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ApiError } from '../../services/apiClient.js'
import * as api from '../../services/erpApi.js'
import { abrirErp, cliente, movimiento, proveedor } from '../../test/datosErp.jsx'
import MovimientoDetalle from './MovimientoDetalle.jsx'

vi.mock('../../services/erpApi.js')
beforeEach(() => vi.resetAllMocks())
const abrir = () => abrirErp(MovimientoDetalle, '/erp/movimientos/' + movimiento.id, '/erp/movimientos/:id')

it.each(['PENDIENTE', 'PAGADO', 'ANULADO'])('Feature: 003-erp-empleados-proveedores-finanzas, Property 12: Solo se aceptan las dos transiciones permitidas (%s)', async estado => {
  api.obtenerMovimiento.mockResolvedValue({ ...movimiento, estado })
  abrir()
  await screen.findByRole('heading', { name: 'Detalle del movimiento' })
  expect(Boolean(screen.queryByRole('link', { name: 'Editar' }))).toBe(estado === 'PENDIENTE')
  for (const name of ['Marcar como pagado', 'Anular']) expect(Boolean(screen.queryByRole('button', { name }))).toBe(estado === 'PENDIENTE')
  expect(screen.getByText('actor-del-estado')).toBeVisible()
})

it.each([['PAGADO', 'Marcar como pagado'], ['ANULADO', 'Anular']])('confirma que la transición a %s es irreversible y muestra el 200', async (estado, accion) => {
  api.obtenerMovimiento.mockResolvedValue(movimiento)
  api.cambiarEstadoMovimiento.mockResolvedValue({ ...movimiento, estado })
  abrir()
  fireEvent.click(await screen.findByRole('button', { name: accion }))
  const dialogo = screen.getByRole('dialog')
  expect(within(dialogo).getByText('Este cambio es irreversible.')).toBeVisible()
  expect(api.cambiarEstadoMovimiento).not.toHaveBeenCalled()
  fireEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar' }))
  await waitFor(() => expect(api.cambiarEstadoMovimiento).toHaveBeenCalledWith(movimiento.id, estado))
  await screen.findByText(estado, { exact: true })
  expect(screen.queryByRole('link', { name: 'Editar' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Anular' })).toBeNull()
})

it('muestra el 409 y recarga el estado vigente', async () => {
  api.obtenerMovimiento.mockResolvedValueOnce(movimiento).mockResolvedValue({ ...movimiento, estado: 'ANULADO' })
  api.cambiarEstadoMovimiento.mockRejectedValue(new ApiError('El estado ya cambió.', 409))
  abrir()
  fireEvent.click(await screen.findByRole('button', { name: 'Marcar como pagado' }))
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('El estado ya cambió.')
  await screen.findByText('ANULADO', { exact: true })
  expect(api.obtenerMovimiento).toHaveBeenCalledTimes(2)
  expect(screen.queryByRole('link', { name: 'Editar' })).toBeNull()
})

it.each([
  [{ cliente, proveedor: null }, cliente.nombre, '/crm/clientes/' + cliente.id],
  [{ cliente: null, proveedor }, proveedor.nombre, '/erp/proveedores/' + proveedor.id],
])('enlaza el registro relacionado', async (relacion, nombre, ruta) => {
  api.obtenerMovimiento.mockResolvedValue({ ...movimiento, ...relacion })
  abrir()
  expect(await screen.findByRole('link', { name: nombre })).toHaveAttribute('href', ruta)
})
