import { fireEvent, render, screen, within } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { obtenerResumen } from '../../services/crmApi.js'
import ResumenComercial from './ResumenComercial.jsx'

vi.mock('../../services/crmApi.js', () => ({ obtenerResumen: vi.fn() }))
it('indica carga, permite reintentar y presenta todos los conteos, incluido cero', async () => {
  obtenerResumen.mockRejectedValueOnce({ message: 'Sin conexión', reintentable: true }).mockResolvedValueOnce({
    clientes: { activos: 11, inactivos: 3, total: 14 },
    contratos: { porEstado: { BORRADOR: 4, ACTIVO: 6, FINALIZADO: 2, CANCELADO: 0 }, total: 12 },
  })
  render(<ResumenComercial />)
  expect(screen.getByRole('status')).toBeVisible()
  expect(await screen.findByRole('alert')).toHaveTextContent('Sin conexión')
  fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
  const clientes = await screen.findByRole('region', { name: 'Clientes' })
  const contratos = screen.getByRole('region', { name: 'Contratos' })
  for (const [region, etiquetas] of [
    [clientes, { Activos: 11, Inactivos: 3, Total: 14 }],
    [contratos, { Borrador: 4, Activo: 6, Finalizado: 2, Cancelado: 0, Total: 12 }],
  ]) {
    for (const [etiqueta, valor] of Object.entries(etiquetas)) {
      expect(within(region).getByText(etiqueta).nextElementSibling).toHaveTextContent(String(valor))
    }
  }
  expect(obtenerResumen).toHaveBeenCalledTimes(2)
})
