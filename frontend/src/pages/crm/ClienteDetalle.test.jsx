import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { usePerfil } from '../../context/PerfilContext.jsx'
import * as api from '../../services/crmApi.js'
import ClienteDetalle from './ClienteDetalle.jsx'

vi.mock('../../context/PerfilContext.jsx', () => ({ usePerfil: vi.fn() }))
vi.mock('../../services/crmApi.js', () => ({
  obtenerCliente: vi.fn(), cambiarEstadoCliente: vi.fn(), listarContratos: vi.fn(),
}))
const cliente = { id: 'uno', tipoDocumento: 'CE', numeroDocumento: 'ABC', nombre: 'Cliente Demo',
  activo: true, ciudad: 'Bogotá', correo: 'cliente@demo.co', cuentaCorporativa: { razonSocial: 'Empresa Demo' } }
beforeEach(() => {
  vi.resetAllMocks()
  api.obtenerCliente.mockResolvedValue(cliente)
  api.listarContratos.mockResolvedValue({ content: [{ id: 'dos', codigo: 'CTR-1', estado: 'ACTIVO',
    fechaInicio: '2026-11-01', fechaFin: null, valor: '1500000.00' }], page: 0, totalPages: 1 })
})
async function abrir(rol) {
  usePerfil.mockReturnValue({ rol })
  render(<MemoryRouter initialEntries={['/crm/clientes/uno']}><Routes>
    <Route path="/crm/clientes/:id" element={<ClienteDetalle />} />
  </Routes></MemoryRouter>)
  await screen.findByRole('heading', { name: cliente.nombre })
}

it.each(['COMERCIAL', 'ADMINISTRADOR', 'OPERACIONES'])('consulta los contratos del cliente para %s', async rol => {
  await abrir(rol)
  expect(await screen.findByRole('link', { name: 'CTR-1' })).toHaveAttribute('href', '/crm/contratos/dos')
  expect(api.listarContratos).toHaveBeenCalledWith({ clienteId: 'uno', page: 0 })
  expect(screen.getByText(/1\.500\.000,00/)).toBeVisible()
  if (rol === 'OPERACIONES') {
    expect(screen.queryByText(cliente.correo)).toBeNull()
    expect(screen.queryByRole('link', { name: 'Editar' })).toBeNull()
    expect(screen.queryByRole('link', { name: 'Nuevo contrato' })).toBeNull()
  } else {
    expect(screen.getByRole('link', { name: 'Editar' })).toHaveAttribute('href', '/crm/clientes/uno/editar')
    expect(screen.getByRole('link', { name: 'Nuevo contrato' })).toHaveAttribute('href', '/crm/clientes/uno/contratos/nuevo')
  }
})

it('muestra el detalle reducido sin pedir contratos para Finanzas', async () => {
  await abrir('FINANZAS_RRHH')
  expect(screen.getByText('Empresa Demo')).toBeVisible()
  expect(api.listarContratos).not.toHaveBeenCalled()
  expect(screen.queryByText('Contratos')).toBeNull()
  expect(screen.queryByText(cliente.correo)).toBeNull()
  expect(screen.queryByRole('button', { name: 'Desactivar' })).toBeNull()
})

it.each([true, false])('pide confirmación y refleja el nuevo estado (activo inicial %s)', async activo => {
  api.obtenerCliente.mockResolvedValue({ ...cliente, activo })
  api.cambiarEstadoCliente.mockResolvedValue({ ...cliente, activo: !activo })
  await abrir('COMERCIAL')
  const boton = screen.getByRole('button', { name: activo ? 'Desactivar' : 'Reactivar' })
  fireEvent.click(boton)
  fireEvent.click(within(screen.getByRole('dialog')).getByText('Cancelar'))
  expect(api.cambiarEstadoCliente).not.toHaveBeenCalled()
  fireEvent.click(boton)
  fireEvent.click(within(screen.getByRole('dialog')).getByText('Confirmar'))
  expect(api.cambiarEstadoCliente).toHaveBeenCalledExactlyOnceWith('uno', !activo)
  await screen.findByRole('button', { name: activo ? 'Reactivar' : 'Desactivar' })
  if (activo) {
    expect(screen.getByText('Un cliente inactivo no admite contratos nuevos.')).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Nuevo contrato' })).toBeNull()
  } else {
    expect(screen.getByRole('link', { name: 'Nuevo contrato' })).toBeVisible()
  }
})
