import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import * as api from '../../services/erpApi.js'
import { abrirErp, movimiento, pagina, proveedor } from '../../test/datosErp.jsx'
import { formatoCop } from '../../lib/formato.js'
import ProveedorDetalle from './ProveedorDetalle.jsx'

vi.mock('../../services/erpApi.js')
beforeEach(() => vi.resetAllMocks())

it.each([true, false])('confirma el cambio de estado del proveedor activo=%s', async activo => {
  api.obtenerProveedor.mockResolvedValue({ ...proveedor, activo })
  api.cambiarEstadoProveedor.mockResolvedValue({ ...proveedor, activo: !activo })
  api.listarMovimientos.mockResolvedValue(pagina([]))
  abrirErp(ProveedorDetalle, '/erp/proveedores/' + proveedor.id, '/erp/proveedores/:id')
  await screen.findByRole('heading', { name: proveedor.nombre })
  expect(screen.getByText('Creado el')).toBeVisible()
  fireEvent.click(screen.getByRole('button', { name: activo ? 'Desactivar' : 'Reactivar' }))
  expect(api.cambiarEstadoProveedor).not.toHaveBeenCalled()
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar' }))
  await waitFor(() => expect(api.cambiarEstadoProveedor).toHaveBeenCalledWith(proveedor.id, !activo))
  expect(await screen.findByText(activo ? 'Inactivo' : 'Activo', { exact: true })).toBeVisible()
})

it('consulta y pagina los movimientos del proveedor con importes en COP', async () => {
  api.obtenerProveedor.mockResolvedValue(proveedor)
  api.listarMovimientos.mockImplementation(async ({ page }) => pagina([movimiento], page, 2))
  abrirErp(ProveedorDetalle, '/erp/proveedores/' + proveedor.id, '/erp/proveedores/:id')
  const tabla = await screen.findByRole('table', { name: 'Movimientos del proveedor' })
  expect(api.listarMovimientos).toHaveBeenCalledWith({ proveedorId: proveedor.id, page: 0 })
  expect(within(tabla).getByText(formatoCop(movimiento.valor).replace(/\s/g, ' '))).toBeVisible()
  expect(within(tabla).getByRole('link', { name: movimiento.concepto })).toHaveAttribute('href', '/erp/movimientos/' + movimiento.id)
  fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
  await waitFor(() => expect(api.listarMovimientos).toHaveBeenLastCalledWith({ proveedorId: proveedor.id, page: 1 }))
})
