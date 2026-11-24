import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import * as api from '../../services/sgaApi.js'
import * as crm from '../../services/crmApi.js'
import { abrirSga, cliente, contrato, paquete, pagina } from '../../test/datosSga.jsx'
import PaqueteFormulario from './PaqueteFormulario.jsx'

vi.mock('../../services/sgaApi.js')
vi.mock('../../services/crmApi.js')
beforeEach(() => {
  vi.resetAllMocks()
  crm.listarClientes.mockResolvedValue(pagina([cliente, { ...cliente, id: '12345678-0000-4000-8000-000000000011', nombre: 'Otro cliente' }]))
  crm.listarContratos.mockResolvedValue(pagina([contrato]))
})
const escribir = (etiqueta, value) => fireEvent.change(screen.getByLabelText(etiqueta), { target: { value } })
async function completarAlta() {
  await screen.findByRole('option', { name: cliente.nombre })
  escribir(/^Código/, 'PAQ-001'); escribir(/^Descripción/, 'Entrega ficticia'); escribir(/^Dirección/, 'Dirección ficticia')
  escribir('Cliente', cliente.id)
  await screen.findByRole('option', { name: contrato.codigo })
  escribir('Contrato', contrato.id)
}
it('consulta contratos activos del cliente y limpia el contrato al cambiar de cliente', async () => {
  abrirSga(PaqueteFormulario, '/sga/paquetes/nuevo')
  await completarAlta()
  expect(crm.listarClientes).toHaveBeenCalledWith({ activo: true, q: '', size: 20, sort: 'nombre,asc' })
  expect(crm.listarContratos).toHaveBeenCalledWith({ clienteId: cliente.id, estado: 'ACTIVO', size: 100, sort: 'codigo,asc' })
  escribir('Cliente', '12345678-0000-4000-8000-000000000011')
  await waitFor(() => expect(screen.getByLabelText('Contrato')).toHaveValue(''))
  expect(crm.listarContratos).toHaveBeenLastCalledWith(expect.objectContaining({ clienteId: '12345678-0000-4000-8000-000000000011' }))
})
it('conserva el contrato actual inactivo y envía solo campos editables', async () => {
  api.obtenerPaquete.mockResolvedValue({ ...paquete, contrato: { ...contrato, estado: 'FINALIZADO' } })
  crm.listarContratos.mockResolvedValue(pagina())
  api.actualizarPaquete.mockResolvedValue(paquete)
  abrirSga(PaqueteFormulario, '/sga/paquetes/' + paquete.id + '/editar', '/sga/paquetes/:id/editar')
  expect(await screen.findByText('El contrato asociado ya no está activo; puedes conservarlo')).toBeVisible()
  expect(screen.getByRole('option', { name: 'CTR-001 (FINALIZADO)' }).selected).toBe(true)
  expect(screen.getByLabelText(/^Código/)).toHaveAttribute('readonly')
  fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))
  await waitFor(() => expect(api.actualizarPaquete).toHaveBeenCalledWith(paquete.id, { descripcion: paquete.descripcion, direccionEntrega: paquete.direccionEntrega, contratoId: contrato.id }))
})
it('bloquea localmente un alta incompleta', async () => {
  abrirSga(PaqueteFormulario, '/sga/paquetes/nuevo')
  fireEvent.click(await screen.findByRole('button', { name: 'Guardar' }))
  expect(api.crearPaquete).not.toHaveBeenCalled()
  expect(screen.getByLabelText('Cliente')).toHaveAttribute('aria-invalid', 'true')
})
it.each([400, 404, 409])('conserva valores y muestra el error %s bajo el selector', async status => {
  api.crearPaquete.mockRejectedValue({ status, message: 'Contrato no disponible', ...(status === 400 ? { errors: [{ field: 'contratoId', message: 'Contrato no disponible' }] } : { campo: 'contratoId' }) })
  abrirSga(PaqueteFormulario, '/sga/paquetes/nuevo')
  await completarAlta()
  fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))
  await waitFor(() => expect(screen.getByLabelText('Contrato')).toHaveAttribute('aria-invalid', 'true'))
  expect(screen.getByLabelText('Contrato')).toHaveValue(contrato.id)
  expect(screen.getByLabelText(/^Descripción/)).toHaveValue('Entrega ficticia')
  expect(within(screen.getByRole('form', { name: 'Datos del paquete' })).getByRole('alert')).toHaveTextContent('Contrato no disponible')
})
it('recarga un paquete que dejó de ser editable tras un 409 sin campo', async () => {
  api.obtenerPaquete.mockResolvedValueOnce(paquete).mockResolvedValue({ ...paquete, estado: 'EN_TRANSITO' })
  api.actualizarPaquete.mockRejectedValue({ status: 409, message: 'El paquete ya no es editable.' })
  abrirSga(PaqueteFormulario, '/sga/paquetes/' + paquete.id + '/editar', '/sga/paquetes/:id/editar')
  fireEvent.click(await screen.findByRole('button', { name: 'Guardar' }))
  expect(await screen.findByText(/Solo se editan paquetes registrados/)).toBeVisible()
  expect(api.obtenerPaquete).toHaveBeenCalledTimes(2)
})
