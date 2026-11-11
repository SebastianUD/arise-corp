import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import * as api from '../../services/crmApi.js'
import ClienteFormulario from './ClienteFormulario.jsx'

vi.mock('../../services/crmApi.js', () => ({
  crearCliente: vi.fn(), actualizarCliente: vi.fn(), obtenerCliente: vi.fn(), listarCuentas: vi.fn(),
}))
const cliente = { id: 'uno', tipoDocumento: 'CC', numeroDocumento: '1234', correo: 'a@b.co',
  telefono: '1234567', nombre: 'Cliente Demo' }
beforeEach(() => {
  vi.resetAllMocks()
  api.listarCuentas.mockResolvedValue({ content: [], totalPages: 0 })
})

async function abrir(ruta = '/crm/clientes/nuevo') {
  render(<MemoryRouter initialEntries={[ruta]}><Routes>
    <Route path="/crm/clientes/nuevo" element={<ClienteFormulario />} />
    <Route path="/crm/clientes/:id/editar" element={<ClienteFormulario />} />
    <Route path="/crm/clientes/:id" element={<h1>Detalle guardado</h1>} />
  </Routes></MemoryRouter>)
  await screen.findByRole('textbox', { name: 'Nombre' })
}
function completar() {
  for (const [label, valor] of [['Nombre', cliente.nombre], ['Número de documento', '1.234'],
    ['Correo electrónico', cliente.correo], ['Teléfono', cliente.telefono]]) {
    fireEvent.change(screen.getByRole('textbox', { name: label }), { target: { value: valor } })
  }
}
const guardar = () => fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

it('valida los campos antes de enviar', async () => {
  await abrir()
  guardar()
  expect(screen.getByRole('textbox', { name: 'Nombre' })).toHaveAccessibleDescription('es obligatorio')
  expect(screen.getByRole('textbox', { name: 'Correo electrónico' })).toBeInvalid()
  expect(api.crearCliente).not.toHaveBeenCalled()
})

it('bloquea otro envío mientras guarda y abre el detalle al terminar', async () => {
  let terminar
  api.crearCliente.mockImplementation(() => new Promise(resolve => { terminar = resolve }))
  await abrir()
  completar()
  guardar()
  expect(screen.getByRole('button', { name: 'Guardando…' })).toBeDisabled()
  expect(api.crearCliente).toHaveBeenCalledExactlyOnceWith({
    nombre: cliente.nombre, numeroDocumento: '1234', tipoDocumento: 'CC', correo: cliente.correo,
    telefono: cliente.telefono, ciudad: null, direccion: null, cuentaCorporativaId: null,
  })
  await act(async () => terminar(cliente))
  expect(screen.getByRole('heading', { name: 'Detalle guardado' })).toBeVisible()
})

it('presenta errores 400 junto a los campos y conserva lo escrito', async () => {
  api.crearCliente.mockRejectedValue({ status: 400, message: 'Solicitud inválida',
    errors: [{ field: 'nombre', message: 'Revisa el nombre' }, { field: 'correo', message: 'Revisa el correo' }] })
  await abrir()
  completar()
  guardar()
  await screen.findByRole('alert')
  expect(screen.getByRole('textbox', { name: 'Nombre' })).toHaveAccessibleDescription('Revisa el nombre')
  expect(screen.getByRole('textbox', { name: 'Correo electrónico' })).toHaveAccessibleDescription('Revisa el correo')
  expect(screen.getByRole('textbox', { name: 'Número de documento' })).toHaveValue('1.234')
})

it('identifica el documento duplicado y enlaza al cliente inactivo', async () => {
  api.crearCliente.mockRejectedValue({ status: 409, campo: 'numeroDocumento', message: 'El cliente está inactivo.',
    conflicto: 'CLIENTE_INACTIVO_EXISTENTE', problem: { clienteId: 'existente' } })
  await abrir()
  completar()
  guardar()
  expect(await screen.findByRole('link', { name: 'Ver cliente' })).toHaveAttribute('href', '/crm/clientes/existente')
  expect(screen.getByRole('textbox', { name: 'Número de documento' })).toHaveAccessibleDescription('El cliente está inactivo.')
  expect(screen.getByRole('textbox', { name: 'Nombre' })).toHaveValue(cliente.nombre)
})

it('conserva la cuenta actual inactiva y carga todas las páginas de cuentas activas', async () => {
  const cuenta = { id: '00000000-0000-0000-0000-000000000001', razonSocial: 'Cuenta anterior' }
  api.obtenerCliente.mockResolvedValue({ ...cliente, cuentaCorporativa: cuenta })
  api.listarCuentas.mockResolvedValueOnce({ content: [], totalPages: 2 })
    .mockResolvedValueOnce({ content: [{ id: 'otra', razonSocial: 'Otra cuenta' }], totalPages: 2 })
  api.actualizarCliente.mockResolvedValue(cliente)
  await abrir('/crm/clientes/uno/editar')
  expect(screen.getByRole('option', { name: 'Cuenta anterior (inactiva)' }).selected).toBe(true)
  expect(screen.getByRole('option', { name: 'Otra cuenta' })).toBeInTheDocument()
  guardar()
  await screen.findByText('Detalle guardado')
  expect(api.actualizarCliente).toHaveBeenCalledWith(cliente.id, expect.objectContaining({ cuentaCorporativaId: cuenta.id }))
})
