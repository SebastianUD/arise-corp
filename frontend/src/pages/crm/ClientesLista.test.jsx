import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { usePerfil } from '../../context/PerfilContext.jsx'
import { listarClientes } from '../../services/crmApi.js'
import ClientesLista from './ClientesLista.jsx'

vi.mock('../../context/PerfilContext.jsx', () => ({ usePerfil: vi.fn() }))
vi.mock('../../services/crmApi.js', () => ({ listarClientes: vi.fn() }))
const cliente = { id: 'uno', nombre: 'Cliente Demo', tipoDocumento: 'CC', numeroDocumento: '123', activo: true }

function Vista() {
  const { search } = useLocation()
  return <><output aria-label="URL">{search}</output><ClientesLista /></>
}
function abrir(url = '/crm/clientes') {
  render(<MemoryRouter initialEntries={[url]}><Routes><Route path="/crm/clientes" element={<Vista />} /></Routes></MemoryRouter>)
}
beforeEach(() => {
  vi.resetAllMocks()
  usePerfil.mockReturnValue({ rol: 'COMERCIAL' })
})

it('carga los criterios de la URL y reinicia la página al buscar o filtrar', async () => {
  listarClientes.mockImplementation(async ({ page }) => ({ page: Number(page || 0), totalPages: 3, content: [cliente] }))
  abrir('/crm/clientes?q=inicial&page=1')
  expect(screen.getByText('Cargando…')).toBeVisible()
  expect(await screen.findByText('Página 2 de 3')).toBeVisible()
  expect(screen.getByRole('link', { name: 'Cliente Demo' })).toHaveAttribute('href', '/crm/clientes/uno')
  expect(screen.getByRole('link', { name: 'Nuevo cliente' })).toBeVisible()
  const busqueda = screen.getByRole('searchbox')
  expect(busqueda).toHaveValue('inicial')
  fireEvent.change(busqueda, { target: { value: 'Ana & Co' } })
  expect(listarClientes).toHaveBeenCalledTimes(1)
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  await waitFor(() => expect(listarClientes).toHaveBeenLastCalledWith({ q: 'Ana & Co' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Siguiente' }))
  await screen.findByText('Página 2 de 3')
  fireEvent.change(screen.getByLabelText('Estado'), { target: { value: 'false' } })
  await waitFor(() => expect(listarClientes).toHaveBeenLastCalledWith({ q: 'Ana & Co', activo: 'false' }))
  expect(screen.getByLabelText('URL')).toHaveTextContent('?q=Ana+%26+Co&activo=false')
})

it.each(['OPERACIONES', 'FINANZAS_RRHH'])('presenta la lista vacía en modo consulta para %s', async rol => {
  usePerfil.mockReturnValue({ rol })
  listarClientes.mockResolvedValue({ content: [], page: 0, totalPages: 0 })
  abrir()
  expect(await screen.findByText('No hay clientes para los criterios elegidos.')).toBeVisible()
  expect(screen.queryByRole('link', { name: 'Nuevo cliente' })).toBeNull()
})
