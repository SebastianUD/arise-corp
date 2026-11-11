import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import * as api from '../../services/crmApi.js'
import CuentaFormulario from './CuentaFormulario.jsx'

vi.mock('../../services/crmApi.js', () => ({ crearCuenta: vi.fn(), actualizarCuenta: vi.fn(), obtenerCuenta: vi.fn() }))
beforeEach(() => vi.resetAllMocks())
function abrir(edicion = false) {
  render(<MemoryRouter initialEntries={[edicion ? '/crm/cuentas-corporativas/uno/editar' : '/crm/cuentas-corporativas/nueva']}>
    <Routes>
      <Route path="/crm/cuentas-corporativas/nueva" element={<CuentaFormulario />} />
      <Route path="/crm/cuentas-corporativas/:id/editar" element={<CuentaFormulario />} />
      <Route path="/crm/cuentas-corporativas/:id" element={<h1>Detalle guardado</h1>} />
    </Routes>
  </MemoryRouter>)
}
const campo = name => screen.getByRole('textbox', { name })
const escribir = (name, value) => fireEvent.change(campo(name), { target: { value } })
const guardar = () => fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))
const cuerpo = { nit: '123456', razonSocial: 'Empresa demo', nombreContacto: null, correoContacto: null, telefonoContacto: null }

it('valida obligatorios y formato de contacto antes de enviar', () => {
  abrir()
  escribir('Correo de contacto', 'inválido')
  guardar()
  expect(campo('NIT')).toBeRequired()
  expect(campo('Razón social')).toHaveAccessibleDescription('es obligatorio')
  expect(campo('Correo de contacto')).toHaveAccessibleDescription(/correo válido/)
  expect(api.crearCuenta).not.toHaveBeenCalled()
})

it.each([
  [{ status: 400, message: 'Campos inválidos', errors: [{ field: 'razonSocial', message: 'Revisa la razón social' }] }, 'Razón social', 'Revisa la razón social'],
  [{ status: 409, message: 'Este NIT ya existe', conflicto: 'CUENTA_NIT_DUPLICADO', campo: 'nit' }, 'NIT', 'Este NIT ya existe'],
])('conserva los datos tras el error de la API y lo asocia a %s', async (error, etiqueta, mensaje) => {
  api.crearCuenta.mockRejectedValue(error)
  abrir()
  escribir('Razón social', 'Empresa demo')
  escribir('NIT', '12.345-6')
  guardar()
  await screen.findByRole('alert')
  expect(campo(etiqueta)).toHaveAccessibleDescription(mensaje)
  expect(campo('NIT')).toHaveValue('12.345-6')
  expect(campo('Razón social')).toHaveValue('Empresa demo')
})

it.each([false, true])('envía solo los campos editables normalizados (edición %s)', async edicion => {
  api.obtenerCuenta.mockResolvedValue({ ...cuerpo, id: 'uno', activa: false })
  const guardarCuenta = edicion ? api.actualizarCuenta : api.crearCuenta
  guardarCuenta.mockResolvedValue({ id: 'uno' })
  abrir(edicion)
  await screen.findByRole('form')
  if (edicion) expect(campo('NIT')).toHaveValue(cuerpo.nit)
  escribir('Razón social', ' Empresa demo ')
  escribir('NIT', '12.345-6')
  guardar()
  await screen.findByText('Detalle guardado')
  expect(guardarCuenta.mock.calls).toEqual([edicion ? ['uno', cuerpo] : [cuerpo]])
})
