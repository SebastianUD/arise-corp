import { fireEvent, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import * as api from '../../services/sgaApi.js'
import { abrirSga, dron } from '../../test/datosSga.jsx'
import DronFormulario from './DronFormulario.jsx'

vi.mock('../../services/sgaApi.js')
beforeEach(() => vi.resetAllMocks())
it('valida antes de enviar y bloquea los controles durante el guardado', async () => {
  let resolver
  api.crearDron.mockReturnValue(new Promise(resolve => { resolver = resolve }))
  abrirSga(DronFormulario, '/sga/drones/nuevo')
  fireEvent.click(await screen.findByRole('button', { name: 'Guardar' }))
  expect(api.crearDron).not.toHaveBeenCalled()
  expect(screen.getByLabelText(/^Código/)).toHaveAttribute('aria-invalid', 'true')
  fireEvent.change(screen.getByLabelText(/^Código/), { target: { value: ' drn-001 ' } })
  fireEvent.change(screen.getByLabelText(/^Modelo/), { target: { value: 'Carga ligera' } })
  fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))
  expect(screen.getByRole('button', { name: 'Guardando…' })).toBeDisabled()
  expect(screen.getByLabelText(/^Modelo/)).toBeDisabled()
  resolver(dron)
  await screen.findByText('Destino del registro')
  expect(api.crearDron).toHaveBeenCalledWith({ codigo: 'DRN-001', modelo: 'Carga ligera', observaciones: null })
})
it.each([400, 409])('conserva el formulario y muestra los errores por campo (%s)', async status => {
  api.obtenerDron.mockResolvedValue(dron)
  api.actualizarDron.mockRejectedValue({ status, message: 'Código repetido', ...(status === 400 ? { errors: [{ field: 'codigo', message: 'Código repetido' }] } : { campo: 'codigo' }) })
  abrirSga(DronFormulario, '/sga/drones/' + dron.id + '/editar', '/sga/drones/:id/editar')
  fireEvent.click(await screen.findByRole('button', { name: 'Guardar' }))
  await waitFor(() => expect(screen.getByLabelText(/^Código/)).toHaveAttribute('aria-invalid', 'true'))
  expect(screen.getByLabelText(/^Modelo/)).toHaveValue(dron.modelo)
})
