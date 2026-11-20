import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import * as api from '../../services/erpApi.js'
import { abrirErp, empleado } from '../../test/datosErp.jsx'
import EmpleadoDetalle from './EmpleadoDetalle.jsx'

vi.mock('../../services/erpApi.js')
beforeEach(() => vi.resetAllMocks())

it.each([true, false])('confirma el cambio de estado desde activo=%s y muestra el resultado 200', async activo => {
  api.obtenerEmpleado.mockResolvedValue({ ...empleado, activo })
  api.cambiarEstadoEmpleado.mockResolvedValue({ ...empleado, activo: !activo })
  abrirErp(EmpleadoDetalle, '/erp/empleados/' + empleado.id, '/erp/empleados/:id')
  await screen.findByRole('heading', { name: empleado.nombre })
  expect(screen.getByText('Analista')).toBeVisible()
  expect(screen.getByText('Creado el')).toBeVisible()
  expect(screen.getByText('Actualizado el')).toBeVisible()
  expect(screen.getByRole('link', { name: 'Editar' })).toHaveAttribute('href', '/erp/empleados/' + empleado.id + '/editar')
  fireEvent.click(screen.getByRole('button', { name: activo ? 'Desactivar' : 'Reactivar' }))
  expect(api.cambiarEstadoEmpleado).not.toHaveBeenCalled()
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar' }))
  expect(api.cambiarEstadoEmpleado).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: activo ? 'Desactivar' : 'Reactivar' }))
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar' }))
  await waitFor(() => expect(api.cambiarEstadoEmpleado).toHaveBeenCalledWith(empleado.id, !activo))
  expect(await screen.findByText(activo ? 'Inactivo' : 'Activo', { exact: true })).toBeVisible()
})
