import { fireEvent, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import * as api from '../../services/erpApi.js'
import { abrirErp, proveedor, pagina, escribir } from '../../test/datosErp.jsx'
import ProveedoresLista from './ProveedoresLista.jsx'

vi.mock('../../services/erpApi.js')
beforeEach(() => vi.resetAllMocks())

it('carga proveedores con contacto y ciudad, busca, filtra y pagina', async () => {
  api.listarProveedores.mockImplementation(async filtros => pagina([proveedor], Number(filtros.page ?? 0), 3))
  abrirErp(ProveedoresLista, '/erp/proveedores?page=1')
  expect(screen.getByRole('status')).toBeVisible()
  await screen.findByText('Página 2 de 3')
  expect(screen.getByText('Contacto ficticio')).toBeVisible()
  expect(screen.getByText('Bogotá')).toBeVisible()
  expect(screen.getByRole('link', { name: 'Nuevo proveedor' })).toHaveAttribute('href', '/erp/proveedores/nuevo')
  escribir('Nombre o documento', 'Baterías')
  expect(api.listarProveedores).toHaveBeenCalledTimes(1)
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  await waitFor(() => expect(api.listarProveedores).toHaveBeenLastCalledWith({ q: 'Baterías' }))
  escribir('Estado', 'true')
  await waitFor(() => expect(api.listarProveedores).toHaveBeenLastCalledWith({ q: 'Baterías', activo: 'true' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Siguiente' }))
  await waitFor(() => expect(api.listarProveedores).toHaveBeenLastCalledWith({ q: 'Baterías', activo: 'true', page: '1' }))
})

it('explica una lista vacía', async () => {
  api.listarProveedores.mockResolvedValue(pagina([], 0, 0))
  abrirErp(ProveedoresLista, '/erp/proveedores')
  expect(await screen.findByText('No hay proveedores para los criterios elegidos.')).toBeVisible()
})
