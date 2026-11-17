import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import * as api from '../../services/erpApi.js'
import { abrirErp, movimiento, pagina, proveedor, escribir } from '../../test/datosErp.jsx'
import MovimientosLista from './MovimientosLista.jsx'

vi.mock('../../services/erpApi.js')
beforeEach(() => vi.resetAllMocks())

it('aplica filtros de URL, muestra la relación y pagina conservando criterios', async () => {
  api.listarMovimientos.mockImplementation(async filtros => pagina([
    movimiento, { ...movimiento, id: 'segundo', tipo: 'EGRESO', cliente: null, proveedor },
    { ...movimiento, id: 'tercero', cliente: null },
  ], Number(filtros.page ?? 0), 3))
  abrirErp(MovimientosLista, '/erp/movimientos?tipo=INGRESO&estado=PENDIENTE&desde=2026-01-01&hasta=2026-11-17&q=servicio')
  expect(screen.getByRole('status')).toBeVisible()
  const tabla = await screen.findByRole('table')
  expect(within(tabla).getByText('Cliente ficticio')).toBeVisible()
  expect(within(tabla).getByText('Proveedor ficticio')).toBeVisible()
  expect(within(tabla).getByText('—')).toBeVisible()
  expect(api.listarMovimientos).toHaveBeenCalledWith({ tipo: 'INGRESO', estado: 'PENDIENTE', desde: '2026-01-01', hasta: '2026-11-17', q: 'servicio' })
  fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
  await waitFor(() => expect(api.listarMovimientos).toHaveBeenLastCalledWith(expect.objectContaining({ page: '1', q: 'servicio' })))
  escribir('Tipo', 'EGRESO')
  escribir('Estado', 'PAGADO')
  escribir('Concepto o referencia', 'FAC-001')
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  await waitFor(() => expect(api.listarMovimientos).toHaveBeenLastCalledWith({ tipo: 'EGRESO', estado: 'PAGADO', desde: '2026-01-01', hasta: '2026-11-17', q: 'FAC-001' }))
})

it('no consulta un rango inválido y permite corregirlo', async () => {
  api.listarMovimientos.mockResolvedValue(pagina([], 0, 0))
  abrirErp(MovimientosLista, '/erp/movimientos?desde=2026-11-18&hasta=2026-11-17')
  expect(screen.getByLabelText('Hasta')).toHaveAccessibleDescription('debe ser igual o posterior a la fecha desde')
  expect(api.listarMovimientos).not.toHaveBeenCalled()
  escribir('Hasta', '2026-11-20')
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  expect(await screen.findByText('No hay movimientos para los criterios elegidos.')).toBeVisible()
  expect(api.listarMovimientos).toHaveBeenLastCalledWith({ desde: '2026-11-18', hasta: '2026-11-20' })
})
