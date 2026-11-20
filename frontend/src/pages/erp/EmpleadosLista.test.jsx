import { fireEvent, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import * as api from '../../services/erpApi.js'
import { abrirErp, empleado, pagina, escribir } from '../../test/datosErp.jsx'
import EmpleadosLista from './EmpleadosLista.jsx'

vi.mock('../../services/erpApi.js')
beforeEach(() => vi.resetAllMocks())

it('muestra carga, columnas, búsqueda por botón, filtro y paginación en la URL', async () => {
  api.listarEmpleados.mockImplementation(async filtros => pagina([empleado], Number(filtros.page ?? 0), 3))
  abrirErp(EmpleadosLista, '/erp/empleados?q=inicial&page=1')
  expect(screen.getByRole('status')).toHaveTextContent('Cargando')
  expect(await screen.findByText('Página 2 de 3')).toBeVisible()
  expect(screen.getByRole('link', { name: empleado.nombre })).toHaveAttribute('href', '/erp/empleados/' + empleado.id)
  expect(screen.getByText('Analista')).toBeVisible()
  expect(screen.getByRole('columnheader', { name: 'Fecha de ingreso' })).toBeVisible()
  expect(screen.getByRole('link', { name: 'Nuevo empleado' })).toHaveAttribute('href', '/erp/empleados/nuevo')
  escribir('Nombre o documento', '  Ana & Co  ')
  expect(api.listarEmpleados).toHaveBeenCalledTimes(1)
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  await waitFor(() => expect(api.listarEmpleados).toHaveBeenLastCalledWith({ q: 'Ana & Co' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Siguiente' }))
  await screen.findByText('Página 2 de 3')
  escribir('Estado', 'false')
  await waitFor(() => expect(api.listarEmpleados).toHaveBeenLastCalledWith({ q: 'Ana & Co', activo: 'false' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Siguiente' }))
  await waitFor(() => expect(api.listarEmpleados).toHaveBeenLastCalledWith({ q: 'Ana & Co', activo: 'false', page: '1' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Anterior' }))
  await waitFor(() => expect(api.listarEmpleados).toHaveBeenLastCalledWith({ q: 'Ana & Co', activo: 'false', page: '0' }))
})

it('explica una lista vacía', async () => {
  api.listarEmpleados.mockResolvedValue(pagina([], 0, 0))
  abrirErp(EmpleadosLista, '/erp/empleados')
  expect(await screen.findByText('No hay empleados para los criterios elegidos.')).toBeVisible()
})
