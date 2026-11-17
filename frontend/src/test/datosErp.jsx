import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'

export const empleado = {
  id: '12345678-0000-4000-8000-000000000001', tipoDocumento: 'CC', numeroDocumento: '123456', nombre: 'Empleado ficticio',
  cargo: 'Analista', area: 'Finanzas', correo: 'empleado@example.test', telefono: '601 000 0000', fechaIngreso: '2025-03-01', activo: true,
  creadoEn: '2026-11-17T15:00:00Z', actualizadoEn: '2026-11-17T16:00:00Z', actualizadoPor: 'actor-ficticio',
}
export const proveedor = { ...empleado, id: '12345678-0000-4000-8000-000000000002', tipoDocumento: 'NIT', nombre: 'Proveedor ficticio', nombreContacto: 'Contacto ficticio', ciudad: 'Bogotá', direccion: 'Dirección ficticia' }
export const cliente = { id: '12345678-0000-4000-8000-000000000003', nombre: 'Cliente ficticio', activo: true }
export const movimiento = {
  id: '12345678-0000-4000-8000-000000000004', tipo: 'INGRESO', fecha: '2026-11-17', concepto: 'Servicio ficticio', valor: '2500000.50', referencia: 'FAC-001',
  estado: 'PENDIENTE', cliente, proveedor: null, creadoEn: empleado.creadoEn, actualizadoEn: empleado.actualizadoEn,
  actualizadoPor: empleado.actualizadoPor, estadoActualizadoEn: empleado.actualizadoEn, estadoActualizadoPor: 'actor-del-estado',
}
export const pagina = (content = [], page = 0, totalPages = 1) => ({ content, page, totalPages, totalElements: content.length, size: 20 })

export function abrirErp(Componente, ruta, patron = ruta.split('?')[0]) {
  return render(<MemoryRouter initialEntries={[ruta]}><Routes>
    <Route path={patron} element={<Componente />} />
    <Route path="*" element={<p>Destino del registro</p>} />
  </Routes></MemoryRouter>)
}
export function escribir(etiqueta, valor) {
  fireEvent.change(screen.getByLabelText(etiqueta), { target: { value: valor } })
}
export function completarRegistro(esEmpleado = true) {
  escribir(/^Nombre \*/, 'Ejemplo ficticio')
  escribir(/^Número de documento/, '123.456')
  escribir(/^Correo electrónico/, 'ejemplo@example.test')
  escribir(/^Teléfono/, '601 000 0000')
  if (esEmpleado) {
    escribir(/^Cargo/, 'Analista')
    escribir(/^Área/, 'Finanzas')
    escribir(/^Fecha de ingreso/, '2025-03-01')
  }
}
export const enviarRegistro = () => fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))
