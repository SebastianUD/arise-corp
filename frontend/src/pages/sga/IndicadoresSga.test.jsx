import { fireEvent, screen, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { obtenerResumenSga } from '../../services/sgaApi.js'
import { abrirSga } from '../../test/datosSga.jsx'
import IndicadoresSga from './IndicadoresSga.jsx'

vi.mock('../../services/sgaApi.js')
const resumen = {
  paquetes: { porEstado: { REGISTRADO: 2, ASIGNADO: 1, EN_TRANSITO: 1, ENTREGADO: 5, CANCELADO: 0, DEVUELTO: 1 }, total: 10 },
  drones: { porEstado: { DISPONIBLE: 3, EN_MISION: 1, MANTENIMIENTO: 1, FUERA_DE_SERVICIO: 0 }, total: 5 },
  articulos: { bajoStockMinimo: 2, total: 12 },
}
beforeEach(() => vi.resetAllMocks())
it('muestra los estados con cero, los totales y el enlace al inventario filtrado', async () => {
  obtenerResumenSga.mockResolvedValue(resumen)
  abrirSga(IndicadoresSga, '/sga/resumen')
  const paquetes = await screen.findByRole('region', { name: 'Paquetes' })
  expect(within(paquetes).getByText(/Total:/)).toHaveTextContent('10')
  expect(within(paquetes).getByText('Cancelado').nextElementSibling).toHaveTextContent('0')
  expect(within(paquetes).getAllByRole('definition')).toHaveLength(6)
  const drones = screen.getByRole('region', { name: 'Drones' })
  expect(within(drones).getByText(/Total:/)).toHaveTextContent('5')
  expect(within(drones).getByText('Fuera de servicio').nextElementSibling).toHaveTextContent('0')
  expect(within(drones).getAllByRole('definition')).toHaveLength(4)
  expect(screen.getByText('Total de artículos').nextElementSibling).toHaveTextContent('12')
  expect(screen.getByText('Artículos bajo stock mínimo').nextElementSibling).toHaveTextContent('2')
  expect(screen.getByRole('link', { name: 'Ver artículos bajo stock mínimo' })).toHaveAttribute('href', '/sga/inventario?bajoStockMinimo=true')
})
it('muestra carga y permite reintentar un error de conexión', async () => {
  obtenerResumenSga.mockRejectedValueOnce({ message: 'No se pudo conectar.', reintentable: true }).mockResolvedValue(resumen)
  abrirSga(IndicadoresSga, '/sga/resumen')
  expect(screen.getByRole('status')).toHaveTextContent('Cargando…')
  fireEvent.click(await screen.findByRole('button', { name: 'Reintentar' }))
  await screen.findByRole('region', { name: 'Paquetes' })
  expect(obtenerResumenSga).toHaveBeenCalledTimes(2)
})
