import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ApiError } from '../../services/apiClient.js'
import { obtenerResumenFinanciero } from '../../services/erpApi.js'
import { abrirErp, escribir } from '../../test/datosErp.jsx'
import PanelFinanciero from './PanelFinanciero.jsx'

vi.mock('../../services/erpApi.js')
const resumen = {
  desde: null, hasta: null, saldoPagado: '-1200000.50',
  porTipo: Object.fromEntries(['INGRESO', 'EGRESO'].map(tipo => [tipo, {
    cantidad: 0, porEstado: Object.fromEntries(['PENDIENTE', 'PAGADO', 'ANULADO'].map(estado => [estado, { cantidad: 0, suma: '0.00' }])),
  }])),
}
beforeEach(() => vi.resetAllMocks())

it('muestra todos los estados en cero y el saldo negativo en COP', async () => {
  obtenerResumenFinanciero.mockResolvedValue(resumen)
  abrirErp(PanelFinanciero, '/erp/resumen')
  expect(screen.getByRole('status')).toBeVisible()
  expect(await screen.findByText('Todo el periodo')).toBeVisible()
  expect(screen.getByText(/-\$\s*1\.200\.000,50/)).toBeVisible()
  for (const nombre of ['Ingresos', 'Egresos']) {
    const tabla = screen.getByRole('table', { name: nombre })
    expect(within(tabla).getAllByRole('row')).toHaveLength(4)
    for (const estado of ['PENDIENTE', 'PAGADO', 'ANULADO']) expect(within(tabla).getByText(estado)).toBeVisible()
    expect(within(tabla).getAllByText(/\$\s*0,00/)).toHaveLength(3)
  }
})

it('filtra fechas, indica los límites aplicados y bloquea un rango invertido', async () => {
  obtenerResumenFinanciero.mockImplementation(async filtros => ({ ...resumen, desde: filtros.desde || null, hasta: filtros.hasta || null }))
  abrirErp(PanelFinanciero, '/erp/resumen')
  await screen.findByText('Todo el periodo')
  escribir('Desde', '2026-01-01')
  escribir('Hasta', '2026-11-17')
  fireEvent.click(screen.getByRole('button', { name: 'Filtrar' }))
  await waitFor(() => expect(obtenerResumenFinanciero).toHaveBeenLastCalledWith({ desde: '2026-01-01', hasta: '2026-11-17' }))
  expect(await screen.findByText(/Desde .* · Hasta/)).toBeVisible()
  escribir('Desde', '2026-11-18')
  fireEvent.click(screen.getByRole('button', { name: 'Filtrar' }))
  expect(screen.getByLabelText('Hasta')).toHaveAccessibleDescription('debe ser igual o posterior a la fecha desde')
  expect(obtenerResumenFinanciero).toHaveBeenCalledTimes(2)
})

it('no consulta un rango inválido de la URL', () => {
  abrirErp(PanelFinanciero, '/erp/resumen?desde=2026-11-18&hasta=2026-11-17')
  expect(screen.getByLabelText('Hasta')).toHaveAttribute('aria-invalid', 'true')
  expect(obtenerResumenFinanciero).not.toHaveBeenCalled()
})

it('permite reintentar un error de servidor', async () => {
  obtenerResumenFinanciero.mockRejectedValueOnce(new ApiError('No pudimos cargar la información.', 500)).mockResolvedValue(resumen)
  abrirErp(PanelFinanciero, '/erp/resumen')
  expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar')
  fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
  expect(await screen.findByText('Todo el periodo')).toBeVisible()
  expect(obtenerResumenFinanciero).toHaveBeenCalledTimes(2)
})
