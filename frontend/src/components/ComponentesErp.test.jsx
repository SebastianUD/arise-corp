import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import SelectorRegistro from './SelectorRegistro.jsx'
import EstadoMovimientoEtiqueta from './EstadoMovimientoEtiqueta.jsx'

it('busca, selecciona, conserva la asociación inactiva y permite quitarla', async () => {
  const actual = { id: 'actual', nombre: 'Anterior', activo: false }
  const nuevo = { id: 'nuevo', nombre: 'Disponible', activo: true }
  const buscar = vi.fn().mockResolvedValue({ content: [nuevo] })
  const onCambiar = vi.fn()
  const vista = render(<SelectorRegistro etiqueta="Cliente" buscar={buscar} valor={actual} onCambiar={onCambiar} error="Revisa el cliente" />)
  expect(screen.getByLabelText('Cliente')).toHaveValue('actual')
  expect(screen.getByRole('option', { name: 'Anterior (inactivo)' })).toBeInTheDocument()
  expect(screen.getByLabelText('Cliente')).toHaveAccessibleDescription('Revisa el cliente')
  await screen.findByRole('option', { name: 'Disponible' })
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: '  Ana  ' } })
  expect(buscar).toHaveBeenCalledTimes(1)
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  await waitFor(() => expect(buscar).toHaveBeenLastCalledWith('Ana'))
  fireEvent.change(screen.getByLabelText('Cliente'), { target: { value: 'nuevo' } })
  expect(onCambiar).toHaveBeenLastCalledWith(nuevo)
  fireEvent.change(screen.getByLabelText('Cliente'), { target: { value: '' } })
  expect(onCambiar).toHaveBeenLastCalledWith(null)
  vista.rerender(<SelectorRegistro etiqueta="Cliente" buscar={buscar} valor={actual} onCambiar={onCambiar} deshabilitado />)
  expect(screen.getByLabelText('Cliente')).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Buscar' })).toBeDisabled()
})

it('muestra carga y permite reintentar una búsqueda fallida', async () => {
  const buscar = vi.fn().mockRejectedValueOnce(Object.assign(new Error('Sin conexión'), { reintentable: true })).mockResolvedValue({ content: [] })
  render(<SelectorRegistro etiqueta="Proveedor" buscar={buscar} valor={null} onCambiar={vi.fn()} />)
  expect(screen.getByRole('status')).toHaveTextContent('Cargando')
  expect(await screen.findByRole('alert')).toHaveTextContent('Sin conexión')
  fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
  await waitFor(() => expect(screen.queryByRole('alert')).toBeNull())
  expect(buscar).toHaveBeenCalledTimes(2)
})

it.each([['PENDIENTE', 'borrador'], ['PAGADO', 'activo'], ['ANULADO', 'inactivo']])('muestra la etiqueta %s', (estado, clase) => {
  render(<EstadoMovimientoEtiqueta estado={estado} />)
  expect(screen.getByText(estado)).toHaveClass('badge', clase)
})
