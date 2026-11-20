import { screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ApiError } from '../../services/apiClient.js'
import * as api from '../../services/erpApi.js'
import { abrirErp, completarRegistro, proveedor, enviarRegistro, escribir } from '../../test/datosErp.jsx'
import ProveedorFormulario from './ProveedorFormulario.jsx'

vi.mock('../../services/erpApi.js')
beforeEach(() => vi.resetAllMocks())
async function abrir() {
  abrirErp(ProveedorFormulario, '/erp/proveedores/nuevo')
  await screen.findByRole('button', { name: 'Guardar' })
}

it('valida sin petición y acepta opcionales vacíos como null', async () => {
  api.crearProveedor.mockReturnValue(new Promise(() => {}))
  await abrir()
  expect(screen.getAllByRole('option').map(o => o.textContent)).toEqual(['NIT', 'CC', 'CE', 'PASAPORTE'])
  enviarRegistro()
  expect(api.crearProveedor).not.toHaveBeenCalled()
  expect(screen.getByLabelText(/^Correo electrónico/)).toHaveAttribute('aria-invalid', 'true')
  completarRegistro(false)
  enviarRegistro()
  expect(api.crearProveedor).toHaveBeenCalledWith(expect.objectContaining({ numeroDocumento: '123456', nombreContacto: null, direccion: null, ciudad: null }))
  expect(screen.getByRole('button', { name: 'Guardando…' })).toBeDisabled()
  expect(screen.getByLabelText('Nombre de contacto')).toBeDisabled()
})

it.each([
  [400, { errors: [{ field: 'correo', message: 'Correo inválido' }] }, /^Correo electrónico/, 'Correo inválido'],
  [409, { campo: 'numeroDocumento', conflicto: 'PROVEEDOR_DOCUMENTO_DUPLICADO' }, /^Número de documento/, 'Documento duplicado'],
  [409, { campo: 'numeroDocumento', conflicto: 'PROVEEDOR_INACTIVO_EXISTENTE', proveedorId: proveedor.id }, /^Número de documento/, 'Proveedor inactivo existente'],
])('muestra los errores %s junto al campo y conserva los datos', async (status, problem, campo, mensaje) => {
  api.crearProveedor.mockRejectedValue(new ApiError(mensaje, status, problem))
  await abrir()
  completarRegistro(false)
  enviarRegistro()
  await screen.findByRole('alert')
  expect(screen.getByLabelText(campo)).toHaveAccessibleDescription(mensaje)
  expect(screen.getByLabelText(/^Nombre \*/)).toHaveValue('Ejemplo ficticio')
  if (problem.proveedorId) expect(screen.getByRole('link', { name: 'Ver proveedor' })).toHaveAttribute('href', '/erp/proveedores/' + proveedor.id)
})

it('carga, edita y guarda un proveedor', async () => {
  api.obtenerProveedor.mockResolvedValue(proveedor)
  api.actualizarProveedor.mockResolvedValue(proveedor)
  abrirErp(ProveedorFormulario, '/erp/proveedores/' + proveedor.id + '/editar', '/erp/proveedores/:id/editar')
  await screen.findByDisplayValue('Bogotá')
  escribir('Ciudad', 'Medellín')
  enviarRegistro()
  await waitFor(() => expect(api.actualizarProveedor).toHaveBeenCalledWith(proveedor.id, expect.objectContaining({ ciudad: 'Medellín' })))
  expect(await screen.findByText('Destino del registro')).toBeVisible()
})
