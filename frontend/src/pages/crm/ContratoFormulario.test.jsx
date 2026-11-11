import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import * as api from '../../services/crmApi.js'
import ContratoFormulario from './ContratoFormulario.jsx'

vi.mock('../../services/crmApi.js', () => ({
  obtenerCliente: vi.fn(), obtenerContrato: vi.fn(), crearContrato: vi.fn(), actualizarContrato: vi.fn(),
}))
const cliente = { id: '00000000-0000-0000-0000-000000000001', nombre: 'Cliente demo', activo: true }
const campos = { codigo: 'CTR-001', descripcion: 'Servicio', fechaInicio: '2026-12-01', fechaFin: null, valor: '1500000.50' }
const contrato = { ...campos, id: 'contrato', estado: 'BORRADOR', cliente }
beforeEach(() => {
  vi.resetAllMocks()
  api.obtenerCliente.mockResolvedValue(cliente)
  api.obtenerContrato.mockResolvedValue(contrato)
})

function abrir(edicion = false) {
  const ruta = edicion ? '/crm/contratos/contrato/editar' : '/crm/clientes/' + cliente.id + '/contratos/nuevo'
  render(<MemoryRouter initialEntries={[ruta]}><Routes>
    <Route path="/crm/clientes/:clienteId/contratos/nuevo" element={<ContratoFormulario />} />
    <Route path="/crm/contratos/:id/editar" element={<ContratoFormulario />} />
    <Route path="/crm/contratos/:id" element={<h1>Detalle guardado</h1>} />
  </Routes></MemoryRouter>)
}

async function completar() {
  await screen.findByRole('form')
  for (const [etiqueta, valor] of [['Código', 'ctr-001'], ['Descripción', campos.descripcion],
    ['Fecha de inicio', campos.fechaInicio], ['Valor en COP', campos.valor]]) {
    fireEvent.change(screen.getByLabelText(etiqueta, { exact: false }), { target: { value: valor } })
  }
}
const guardar = () => fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

it.each(['-1', '1.123'])('valida fechas y el importe %s antes del envío', async valor => {
  abrir()
  await completar()
  fireEvent.change(screen.getByLabelText('Fecha de fin'), { target: { value: '2026-11-30' } })
  const importe = screen.getByRole('textbox', { name: 'Valor en COP' })
  fireEvent.change(importe, { target: { value: valor } })
  guardar()
  expect(screen.getByLabelText('Fecha de fin')).toHaveAccessibleDescription('debe ser igual o posterior a la fecha de inicio')
  expect(importe).toHaveAccessibleDescription(/hasta 13 dígitos enteros y 2 decimales/)
  expect(api.crearContrato).not.toHaveBeenCalled()
})

it('envía el importe con su escala original y abre el contrato creado', async () => {
  api.crearContrato.mockResolvedValue(contrato)
  abrir()
  await completar()
  expect(screen.getByRole('textbox', { name: 'Valor en COP' })).toHaveAttribute('inputmode', 'decimal')
  guardar()
  await screen.findByText('Detalle guardado')
  expect(api.crearContrato).toHaveBeenCalledExactlyOnceWith({ ...campos, clienteId: cliente.id })
})

it('conserva lo escrito y asocia el conflicto de duplicado al código', async () => {
  api.crearContrato.mockRejectedValue({ status: 409, conflicto: 'CONTRATO_CODIGO_DUPLICADO', message: 'Ya existe un contrato con este código.' })
  abrir()
  await completar()
  guardar()
  await screen.findByRole('alert')
  const codigo = screen.getByRole('textbox', { name: 'Código' })
  expect(codigo).toHaveValue('ctr-001')
  expect(codigo).toHaveAccessibleDescription('Ya existe un contrato con este código.')
  expect(screen.getByRole('textbox', { name: 'Valor en COP' })).toHaveValue(campos.valor)
})

it('edita los campos del borrador sin reenviar cliente ni estado', async () => {
  api.actualizarContrato.mockResolvedValue(contrato)
  abrir(true)
  expect(await screen.findByRole('textbox', { name: 'Valor en COP' })).toHaveValue(campos.valor)
  guardar()
  await screen.findByText('Detalle guardado')
  expect(api.actualizarContrato).toHaveBeenCalledExactlyOnceWith(contrato.id, campos)
})

it.each([false, true])('bloquea el acceso directo cuando el recurso no admite escritura (edición %s)', async edicion => {
  api.obtenerCliente.mockResolvedValue({ ...cliente, activo: false })
  api.obtenerContrato.mockResolvedValue({ ...contrato, estado: 'ACTIVO' })
  abrir(edicion)
  expect(await screen.findByRole('alert')).toHaveTextContent(edicion ? 'borrador' : 'inactivo')
  expect(screen.queryByRole('form')).toBeNull()
})
