import { fireEvent, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import * as api from '../../services/sgaApi.js'
import { abrirSga, articulo, pagina } from '../../test/datosSga.jsx'
import ArticuloDetalle from './ArticuloDetalle.jsx'

vi.mock('../../services/sgaApi.js')
beforeEach(() => {
  vi.resetAllMocks()
  api.obtenerArticulo.mockResolvedValue(articulo)
  api.listarMovimientosInventario.mockResolvedValue(pagina())
})
const escribir = (etiqueta, value) => fireEvent.change(screen.getByLabelText(etiqueta), { target: { value } })
it('impide AJUSTE sin observación y explica la cantidad contada', async () => {
  abrirSga(ArticuloDetalle, '/sga/inventario/' + articulo.id, '/sga/inventario/:id')
  await screen.findByRole('heading', { name: articulo.nombre })
  escribir(/^Tipo/, 'AJUSTE'); escribir(/^Cantidad/, '0')
  expect(screen.getByText('La cantidad es la existencia contada y la observación es obligatoria.')).toBeVisible()
  fireEvent.click(screen.getByRole('button', { name: 'Registrar movimiento' }))
  expect(api.registrarMovimientoInventario).not.toHaveBeenCalled()
  expect(screen.getByLabelText(/^Observación/)).toHaveAttribute('aria-invalid', 'true')
})
it('muestra la existencia resultante y recarga artículo y movimientos tras el 201', async () => {
  api.registrarMovimientoInventario.mockResolvedValue({ existenciaResultante: 16 })
  abrirSga(ArticuloDetalle, '/sga/inventario/' + articulo.id, '/sga/inventario/:id')
  await screen.findByRole('heading', { name: articulo.nombre })
  escribir(/^Cantidad/, '10')
  api.obtenerArticulo.mockResolvedValue({ ...articulo, existencia: 16, bajoStockMinimo: false })
  fireEvent.click(screen.getByRole('button', { name: 'Registrar movimiento' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Existencia resultante: 16')
  await waitFor(() => expect(api.listarMovimientosInventario).toHaveBeenCalledTimes(2))
  expect(api.obtenerArticulo).toHaveBeenCalledTimes(2)
  expect(screen.queryByText('Bajo stock')).not.toBeInTheDocument()
})
it('muestra EXISTENCIA_INSUFICIENTE bajo cantidad y conserva los valores tras recargar', async () => {
  api.registrarMovimientoInventario.mockRejectedValue({ status: 409, conflicto: 'EXISTENCIA_INSUFICIENTE', message: 'Solo hay 6 unidades disponibles.' })
  abrirSga(ArticuloDetalle, '/sga/inventario/' + articulo.id, '/sga/inventario/:id')
  await screen.findByRole('heading', { name: articulo.nombre })
  escribir(/^Tipo/, 'SALIDA'); escribir(/^Cantidad/, '20'); escribir(/^Observación/, 'Entrega ficticia')
  fireEvent.click(screen.getByRole('button', { name: 'Registrar movimiento' }))
  await waitFor(() => expect(api.obtenerArticulo).toHaveBeenCalledTimes(2))
  expect(screen.getByLabelText(/^Cantidad/)).toHaveAttribute('aria-invalid', 'true')
  expect(screen.getByLabelText(/^Cantidad/)).toHaveValue('20')
  expect(screen.getByLabelText(/^Observación/)).toHaveValue('Entrega ficticia')
  expect(screen.getByRole('alert')).toHaveTextContent('Solo hay 6 unidades disponibles.')
})
