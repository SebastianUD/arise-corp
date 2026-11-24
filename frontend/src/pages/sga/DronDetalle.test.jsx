import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import * as api from '../../services/sgaApi.js'
import { abrirSga, dron, paquete } from '../../test/datosSga.jsx'
import DronDetalle from './DronDetalle.jsx'

vi.mock('../../services/sgaApi.js')
beforeEach(() => vi.resetAllMocks())
it.each(['DISPONIBLE', 'EN_MISION', 'MANTENIMIENTO', 'FUERA_DE_SERVICIO'].flatMap(estado => [false, true].map(ocupado => [estado, ocupado])))('Feature: 004-sga-drones-paquetes-inventario, Property 2: El dron solo cambia a mano entre sus estados manuales y sin paquete en curso (%s, %s)', async (estado, ocupado) => {
  api.obtenerDron.mockResolvedValue({ ...dron, estado, paqueteEnCurso: ocupado ? paquete : null })
  abrirSga(DronDetalle, '/sga/drones/' + dron.id, '/sga/drones/:id')
  await screen.findByRole('heading', { name: dron.codigo })
  expect(screen.queryAllByRole('button')).toHaveLength(ocupado || estado === 'EN_MISION' ? 0 : 2)
  if (ocupado) expect(screen.getByRole('link', { name: paquete.codigo })).toHaveAttribute('href', '/sga/paquetes/' + paquete.id)
})
it('confirma el PATCH y muestra el estado del 200', async () => {
  api.obtenerDron.mockResolvedValue(dron)
  api.cambiarEstadoDron.mockResolvedValue({ ...dron, estado: 'MANTENIMIENTO' })
  abrirSga(DronDetalle, '/sga/drones/' + dron.id, '/sga/drones/:id')
  fireEvent.click(await screen.findByRole('button', { name: 'Enviar a mantenimiento' }))
  expect(api.cambiarEstadoDron).not.toHaveBeenCalled()
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar' }))
  expect(api.cambiarEstadoDron).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Enviar a mantenimiento' }))
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar' }))
  expect(await screen.findByText('Mantenimiento', { exact: true })).toBeVisible()
  expect(api.cambiarEstadoDron).toHaveBeenCalledWith(dron.id, 'MANTENIMIENTO')
})
it('muestra un 409 y recarga el dron', async () => {
  api.obtenerDron.mockResolvedValueOnce(dron).mockResolvedValue({ ...dron, paqueteEnCurso: paquete })
  api.cambiarEstadoDron.mockRejectedValue({ status: 409, message: 'El dron tiene un paquete en curso.' })
  abrirSga(DronDetalle, '/sga/drones/' + dron.id, '/sga/drones/:id')
  fireEvent.click(await screen.findByRole('button', { name: 'Enviar a mantenimiento' }))
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('El dron tiene un paquete en curso.')
  await waitFor(() => expect(api.obtenerDron).toHaveBeenCalledTimes(2))
  expect(await screen.findByRole('link', { name: paquete.codigo })).toBeVisible()
})
