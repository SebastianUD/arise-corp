import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import * as api from '../../services/crmApi.js'
import CuentaDetalle from './CuentaDetalle.jsx'

vi.mock('../../services/crmApi.js', () => ({ obtenerCuenta: vi.fn(), cambiarEstadoCuenta: vi.fn(), listarClientes: vi.fn() }))
const cuenta = { id: 'uno', nit: '12345', razonSocial: 'Empresa demo', nombreContacto: 'Contacto demo' }
beforeEach(() => {
  vi.resetAllMocks()
  api.listarClientes.mockImplementation(async ({ page }) => ({
    page, totalPages: 2, content: [{ id: 'cliente-' + page, nombre: 'Cliente ' + page, activo: true, tipoDocumento: 'CC', numeroDocumento: '1234' }],
  }))
})
async function abrir(activa) {
  api.obtenerCuenta.mockResolvedValue({ ...cuenta, activa })
  render(<MemoryRouter initialEntries={['/crm/cuentas-corporativas/uno']}><Routes>
    <Route path="/crm/cuentas-corporativas/:id" element={<CuentaDetalle />} />
  </Routes></MemoryRouter>)
  await screen.findByRole('link', { name: 'Cliente 0' })
}

it('muestra la cuenta y pagina únicamente sus clientes asociados', async () => {
  await abrir(true)
  expect(screen.getByText(cuenta.nombreContacto)).toBeVisible()
  expect(screen.getByRole('link', { name: 'Editar' })).toHaveAttribute('href', '/crm/cuentas-corporativas/uno/editar')
  expect(api.listarClientes).toHaveBeenCalledWith({ cuentaCorporativaId: 'uno', page: 0 })
  fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
  expect(await screen.findByRole('link', { name: 'Cliente 1' })).toHaveAttribute('href', '/crm/clientes/cliente-1')
  expect(api.listarClientes).toHaveBeenLastCalledWith({ cuentaCorporativaId: 'uno', page: 1 })
})

it.each([true, false])('permite cancelar y solo cambia el estado al confirmar (activa %s)', async activa => {
  await abrir(activa)
  api.cambiarEstadoCuenta.mockResolvedValue({ ...cuenta, activa: !activa })
  const boton = screen.getByRole('button', { name: activa ? 'Desactivar' : 'Activar' })
  fireEvent.click(boton)
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar' }))
  expect(api.cambiarEstadoCuenta).not.toHaveBeenCalled()
  fireEvent.click(boton)
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar' }))
  expect(await screen.findByText(activa ? 'Inactiva' : 'Activa')).toBeVisible()
  expect(api.cambiarEstadoCuenta).toHaveBeenCalledExactlyOnceWith('uno', !activa)
  expect(screen.getByRole('link', { name: 'Cliente 0' })).toBeVisible()
})
