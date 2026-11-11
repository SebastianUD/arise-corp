import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { expect, it, vi } from 'vitest'
import { listarCuentas } from '../../services/crmApi.js'
import CuentasLista from './CuentasLista.jsx'

vi.mock('../../services/crmApi.js', () => ({ listarCuentas: vi.fn() }))

it('restaura filtros y página de la URL, busca, pagina y muestra resultados vacíos', async () => {
  listarCuentas.mockImplementation(async ({ page, activa }) => ({
    content: activa === 'false' ? [] : [{ id: 'uno', razonSocial: 'Empresa demo', nit: '12345', activa: true }],
    page: Number(page || 0), totalPages: activa === 'false' ? 0 : 3,
  }))
  render(<MemoryRouter initialEntries={['/crm/cuentas-corporativas?q=Empresa&activa=true&page=1']}>
    <Routes><Route path="/crm/cuentas-corporativas" element={<CuentasLista />} /></Routes>
  </MemoryRouter>)
  expect(await screen.findByText('Página 2 de 3')).toBeVisible()
  expect(screen.getByRole('link', { name: 'Empresa demo' })).toHaveAttribute('href', '/crm/cuentas-corporativas/uno')
  expect(screen.getByLabelText('Estado')).toHaveValue('true')
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: '12345' } })
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  await screen.findByText('Página 1 de 3')
  expect(listarCuentas).toHaveBeenLastCalledWith({ q: '12345', activa: 'true' })
  fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
  await waitFor(() => expect(listarCuentas).toHaveBeenLastCalledWith({ q: '12345', activa: 'true', page: '1' }))
  fireEvent.change(screen.getByLabelText('Estado'), { target: { value: 'false' } })
  expect(await screen.findByText('No hay cuentas para los criterios elegidos.')).toBeVisible()
  expect(listarCuentas).toHaveBeenLastCalledWith({ q: '12345', activa: 'false' })
})
