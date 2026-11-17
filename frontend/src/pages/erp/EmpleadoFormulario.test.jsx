import { fireEvent, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ApiError } from '../../services/apiClient.js'
import * as api from '../../services/erpApi.js'
import { fechaHoyColombia } from '../../lib/validacionErp.js'
import { abrirErp, completarRegistro, empleado, enviarRegistro, escribir } from '../../test/datosErp.jsx'
import EmpleadoFormulario from './EmpleadoFormulario.jsx'

vi.mock('../../services/erpApi.js')
beforeEach(() => vi.resetAllMocks())
async function abrir() {
  abrirErp(EmpleadoFormulario, '/erp/empleados/nuevo')
  await screen.findByRole('button', { name: 'Guardar' })
}

it('valida los campos y la fecha futura sin enviar', async () => {
  await abrir()
  expect(screen.getAllByRole('option').map(o => o.textContent)).toEqual(['CC', 'CE', 'PASAPORTE'])
  expect(screen.getByLabelText(/^Fecha de ingreso/)).toHaveAttribute('max', fechaHoyColombia())
  enviarRegistro()
  expect(screen.getByLabelText(/^Nombre \*/)).toHaveAttribute('aria-invalid', 'true')
  completarRegistro()
  escribir(/^Fecha de ingreso/, '9999-12-31')
  enviarRegistro()
  expect(screen.getByText('no puede ser posterior a la fecha actual')).toBeVisible()
  expect(api.crearEmpleado).not.toHaveBeenCalled()
})

it('normaliza el documento, deshabilita el envío y navega al detalle', async () => {
  let resolver
  api.crearEmpleado.mockImplementation(() => new Promise(r => { resolver = r }))
  await abrir()
  completarRegistro()
  enviarRegistro()
  expect(screen.getByRole('button', { name: 'Guardando…' })).toBeDisabled()
  expect(screen.getByLabelText(/^Cargo/)).toBeDisabled()
  expect(api.crearEmpleado).toHaveBeenCalledWith(expect.objectContaining({ numeroDocumento: '123456', fechaIngreso: '2025-03-01' }))
  fireEvent.submit(screen.getByRole('form'))
  expect(api.crearEmpleado).toHaveBeenCalledTimes(1)
  resolver(empleado)
  expect(await screen.findByText('Destino del registro')).toBeVisible()
})

it.each([
  [400, { errors: [{ field: 'cargo', message: 'Cargo inválido' }] }, /^Cargo/, 'Cargo inválido'],
  [409, { campo: 'numeroDocumento', conflicto: 'EMPLEADO_DOCUMENTO_DUPLICADO' }, /^Número de documento/, 'Documento duplicado'],
  [409, { campo: 'numeroDocumento', conflicto: 'EMPLEADO_INACTIVO_EXISTENTE', empleadoId: empleado.id }, /^Número de documento/, 'Empleado inactivo existente'],
])('conserva los valores y asocia los errores %s', async (status, problem, campo, mensaje) => {
  api.crearEmpleado.mockRejectedValue(new ApiError(mensaje, status, problem))
  await abrir()
  completarRegistro()
  enviarRegistro()
  await screen.findByRole('alert')
  expect(screen.getByLabelText(campo)).toHaveAccessibleDescription(mensaje)
  expect(screen.getByLabelText(/^Nombre \*/)).toHaveValue('Ejemplo ficticio')
  if (problem.empleadoId) expect(screen.getByRole('link', { name: 'Ver empleado' })).toHaveAttribute('href', '/erp/empleados/' + empleado.id)
})

it('carga y actualiza un empleado existente', async () => {
  api.obtenerEmpleado.mockResolvedValue(empleado)
  api.actualizarEmpleado.mockResolvedValue(empleado)
  abrirErp(EmpleadoFormulario, '/erp/empleados/' + empleado.id + '/editar', '/erp/empleados/:id/editar')
  await screen.findByDisplayValue('Analista')
  escribir(/^Cargo/, 'Coordinador')
  enviarRegistro()
  await waitFor(() => expect(api.actualizarEmpleado).toHaveBeenCalledWith(empleado.id, expect.objectContaining({ cargo: 'Coordinador' })))
  expect(api.crearEmpleado).not.toHaveBeenCalled()
})
