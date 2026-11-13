import { act, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import Campo from './Campo.jsx'
import DialogoConfirmacion from './DialogoConfirmacion.jsx'
import EstadoConsulta from './EstadoConsulta.jsx'
import EstadoContratoEtiqueta from './EstadoContratoEtiqueta.jsx'
import Paginacion from './Paginacion.jsx'
import { useConsulta } from '../lib/useConsulta.js'

it('ignora resultados obsoletos incluso si llegan después de reintentar', async () => {
  let terminar
  const anterior = () => new Promise(resolve => { terminar = resolve })
  const fallo = new Error('Sin conexión')
  const actual = vi.fn().mockRejectedValueOnce(fallo).mockResolvedValue('datos actuales')
  const { result, rerender } = renderHook(consulta => useConsulta(consulta), { initialProps: anterior })
  expect(result.current.cargando).toBe(true)
  rerender(actual)
  await waitFor(() => expect(result.current.error).toBe(fallo))
  act(() => result.current.recargar())
  await waitFor(() => expect(result.current.datos).toBe('datos actuales'))
  await act(async () => terminar('datos anteriores'))
  expect(result.current).toMatchObject({ datos: 'datos actuales', error: null, cargando: false })
})

it('muestra el reintento solo para fallos recuperables', () => {
  const recargar = vi.fn()
  const { rerender } = render(<EstadoConsulta cargando />)
  expect(screen.getByRole('status')).toBeVisible()
  for (const [status, texto] of [[403, 'No tienes permisos'], [404, 'Recurso no encontrado']]) {
    rerender(<EstadoConsulta error={{ status }} recargar={recargar} />)
    expect(screen.getByRole('alert')).toHaveTextContent(texto)
    expect(screen.queryByRole('button')).toBeNull()
  }
  rerender(<EstadoConsulta error={{ reintentable: true, message: 'Sin conexión' }} recargar={recargar} />)
  fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
  expect(recargar).toHaveBeenCalledOnce()
})

it('conecta el control con su etiqueta, ayuda previa y error como texto', () => {
  render(<><p id="ayuda">Solo dígitos</p><Campo etiqueta="Documento" obligatorio error="<b>Inválido</b>">
    <input aria-describedby="ayuda" />
  </Campo></>)
  const input = screen.getByRole('textbox', { name: 'Documento' })
  expect(input).toBeRequired()
  expect(input).toBeInvalid()
  expect(input).toHaveAccessibleDescription('Solo dígitos <b>Inválido</b>')
})

it('traduce los índices de página y deshabilita los extremos', () => {
  const cambiar = vi.fn()
  const { rerender } = render(<Paginacion pagina={{ page: 0, totalPages: 2 }} onCambiar={cambiar} />)
  expect(screen.getByText('Página 1 de 2')).toBeVisible()
  expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled()
  fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
  expect(cambiar).toHaveBeenLastCalledWith(1)
  rerender(<Paginacion pagina={{ page: 1, totalPages: 2 }} onCambiar={cambiar} />)
  expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled()
  fireEvent.click(screen.getByRole('button', { name: 'Anterior' }))
  expect(cambiar).toHaveBeenLastCalledWith(0)
})

it('ofrece confirmación explícita y permite cancelar el diálogo', () => {
  const confirmar = vi.fn()
  const cancelar = vi.fn()
  render(<DialogoConfirmacion titulo="Finalizar contrato" texto="Este cambio es irreversible."
    onConfirmar={confirmar} onCancelar={cancelar} />)
  const dialogo = screen.getByRole('dialog', { name: 'Finalizar contrato' })
  expect(dialogo).toHaveAccessibleDescription('Este cambio es irreversible.')
  expect(dialogo).toHaveAttribute('aria-modal', 'true')
  fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
  expect(cancelar).toHaveBeenCalledOnce()
  expect(confirmar).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }))
  expect(confirmar).toHaveBeenCalledOnce()
})

it.each(['BORRADOR', 'ACTIVO', 'FINALIZADO', 'CANCELADO'])('identifica %s mediante texto', estado => {
  render(<EstadoContratoEtiqueta estado={estado} />)
  expect(screen.getByText(estado)).toBeVisible()
})
