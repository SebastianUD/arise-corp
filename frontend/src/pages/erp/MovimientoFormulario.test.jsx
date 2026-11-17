import { fireEvent, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ApiError } from '../../services/apiClient.js'
import * as api from '../../services/erpApi.js'
import { listarClientes } from '../../services/crmApi.js'
import { abrirErp, cliente, movimiento, pagina, proveedor, enviarRegistro, escribir } from '../../test/datosErp.jsx'
import MovimientoFormulario from './MovimientoFormulario.jsx'

vi.mock('../../services/erpApi.js')
vi.mock('../../services/crmApi.js')
beforeEach(() => {
  vi.resetAllMocks()
  listarClientes.mockResolvedValue(pagina([cliente]))
  api.listarProveedores.mockResolvedValue(pagina([proveedor]))
})
async function abrir() {
  abrirErp(MovimientoFormulario, '/erp/movimientos/nuevo')
  await screen.findByRole('option', { name: cliente.nombre })
}
function completar() {
  escribir(/^Concepto/, 'Servicio ficticio')
  escribir(/^Valor en COP/, '9999999999999.99')
}

it('busca activos según el tipo, limpia selecciones y envía el importe como texto', async () => {
  api.crearMovimiento.mockResolvedValue(movimiento)
  await abrir()
  expect(listarClientes).toHaveBeenCalledWith({ activo: true, q: '', size: 20, sort: 'nombre,asc' })
  escribir('Cliente', cliente.id)
  escribir(/^Tipo/, 'EGRESO')
  await screen.findByRole('option', { name: proveedor.nombre })
  expect(api.listarProveedores).toHaveBeenCalledWith({ activo: true, q: '', size: 20, sort: 'nombre,asc' })
  expect(screen.getByLabelText('Proveedor')).toHaveValue('')
  escribir('Proveedor', proveedor.id)
  escribir(/^Tipo/, 'INGRESO')
  await screen.findByRole('option', { name: cliente.nombre })
  expect(screen.getByLabelText('Cliente')).toHaveValue('')
  escribir('Cliente', cliente.id)
  escribir('Buscar cliente', 'Ana')
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  await waitFor(() => expect(listarClientes).toHaveBeenLastCalledWith({ activo: true, q: 'Ana', size: 20, sort: 'nombre,asc' }))
  completar()
  escribir('Referencia', ' fac/001 ')
  expect(screen.getByLabelText(/^Valor en COP/)).toHaveAttribute('inputmode', 'decimal')
  enviarRegistro()
  expect(await screen.findByText('Destino del registro')).toBeVisible()
  expect(api.crearMovimiento).toHaveBeenCalledWith(expect.objectContaining({ tipo: 'INGRESO', clienteId: cliente.id, proveedorId: null, valor: '9999999999999.99', referencia: 'FAC/001' }))
})

it('valida concepto, importe y referencia antes de enviar', async () => {
  await abrir()
  completar()
  escribir(/^Concepto/, 'ab')
  escribir(/^Valor en COP/, '0.00')
  escribir('Referencia', 'no_permitido')
  enviarRegistro()
  expect(api.crearMovimiento).not.toHaveBeenCalled()
  for (const etiqueta of [/^Concepto/, /^Valor en COP/, 'Referencia']) expect(screen.getByLabelText(etiqueta)).toHaveAttribute('aria-invalid', 'true')
})

it('conserva la relación inactiva al editar', async () => {
  api.obtenerMovimiento.mockResolvedValue({ ...movimiento, cliente: { ...cliente, activo: false } })
  listarClientes.mockResolvedValue(pagina([]))
  api.actualizarMovimiento.mockResolvedValue(movimiento)
  abrirErp(MovimientoFormulario, '/erp/movimientos/' + movimiento.id + '/editar', '/erp/movimientos/:id/editar')
  await screen.findByText('El registro asociado está inactivo; puedes conservarlo.')
  expect(screen.getByRole('option', { name: 'Cliente ficticio (inactivo)' })).toBeInTheDocument()
  expect(screen.getByLabelText('Cliente')).toHaveValue(cliente.id)
  enviarRegistro()
  await waitFor(() => expect(api.actualizarMovimiento).toHaveBeenCalledWith(movimiento.id, expect.objectContaining({ clienteId: cliente.id })))
})

it.each([
  [400, 'INGRESO', 'Cliente', { errors: [{ field: 'clienteId', message: 'Cliente inválido' }] }, 'Cliente inválido'],
  [404, 'INGRESO', 'Cliente', { campo: 'clienteId' }, 'Cliente inexistente'],
  [409, 'EGRESO', 'Proveedor', { campo: 'proveedorId', conflicto: 'PROVEEDOR_INACTIVO' }, 'Proveedor inactivo'],
])('presenta el error %s bajo el selector sin perder los campos', async (status, tipo, etiqueta, problem, mensaje) => {
  api.crearMovimiento.mockRejectedValue(new ApiError(mensaje, status, problem))
  await abrir()
  if (tipo === 'EGRESO') { escribir(/^Tipo/, tipo); await screen.findByRole('option', { name: proveedor.nombre }) }
  completar()
  escribir(etiqueta, tipo === 'INGRESO' ? cliente.id : proveedor.id)
  enviarRegistro()
  await screen.findByRole('alert')
  expect(screen.getByLabelText(etiqueta)).toHaveAccessibleDescription(mensaje)
  expect(screen.getByLabelText(/^Valor en COP/)).toHaveValue('9999999999999.99')
  expect(screen.getByLabelText(/^Concepto/)).toHaveValue('Servicio ficticio')
})

it('recarga un movimiento cuando la edición encuentra un estado final', async () => {
  api.obtenerMovimiento.mockResolvedValueOnce(movimiento).mockResolvedValue({ ...movimiento, estado: 'PAGADO' })
  api.actualizarMovimiento.mockRejectedValue(new ApiError('El movimiento ya no es editable.', 409, { conflicto: 'MOVIMIENTO_NO_EDITABLE' }))
  abrirErp(MovimientoFormulario, '/erp/movimientos/' + movimiento.id + '/editar', '/erp/movimientos/:id/editar')
  await screen.findByRole('button', { name: 'Guardar' })
  enviarRegistro()
  expect(await screen.findByRole('alert')).toHaveTextContent('El movimiento ya no es editable.')
  await screen.findByText(/Solo se editan movimientos pendientes/)
  expect(api.obtenerMovimiento).toHaveBeenCalledTimes(2)
  expect(screen.queryByRole('button', { name: 'Guardar' })).toBeNull()
})

it.each(['PAGADO', 'ANULADO'])('bloquea la edición de un movimiento %s sin pedir selectores', async estado => {
  api.obtenerMovimiento.mockResolvedValue({ ...movimiento, estado })
  abrirErp(MovimientoFormulario, '/erp/movimientos/' + movimiento.id + '/editar', '/erp/movimientos/:id/editar')
  await screen.findByText(/Solo se editan movimientos pendientes/)
  expect(listarClientes).not.toHaveBeenCalled()
  expect(api.listarProveedores).not.toHaveBeenCalled()
})
