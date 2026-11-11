import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { usePerfil } from '../../context/PerfilContext.jsx'
import { formatoInstante } from '../../lib/formato.js'
import * as api from '../../services/crmApi.js'
import ContratoDetalle from './ContratoDetalle.jsx'

vi.mock('../../context/PerfilContext.jsx', () => ({ usePerfil: vi.fn() }))
vi.mock('../../services/crmApi.js', () => ({ obtenerContrato: vi.fn(), cambiarEstadoContrato: vi.fn() }))
const contrato = { id: 'uno', codigo: 'CTR-001', descripcion: 'Servicio', valor: '1500000.50',
  cliente: { id: 'cliente', nombre: 'Cliente demo' }, fechaInicio: '2026-12-01', fechaFin: null,
  estadoActualizadoEn: '2026-11-11T12:00:00Z', estadoActualizadoPor: 'actor-original' }
beforeEach(() => { vi.resetAllMocks() })

async function abrir(estado = 'BORRADOR', rol = 'COMERCIAL') {
  api.obtenerContrato.mockResolvedValueOnce({ ...contrato, estado })
  usePerfil.mockReturnValue({ rol })
  render(<MemoryRouter initialEntries={['/crm/contratos/uno']}><Routes>
    <Route path="/crm/contratos/:id" element={<ContratoDetalle />} />
  </Routes></MemoryRouter>)
  await screen.findByRole('heading', { name: contrato.codigo })
}

it.each([
  ['BORRADOR', ['Activar', 'Cancelar contrato']], ['ACTIVO', ['Finalizar', 'Cancelar contrato']],
  ['CANCELADO', []], ['FINALIZADO', []],
])('ofrece solo las transiciones válidas en %s', async (estado, botones) => {
  await abrir(estado)
  expect(screen.queryAllByRole('button').map(boton => boton.textContent)).toEqual(botones)
  expect(Boolean(screen.queryByRole('link', { name: 'Editar' }))).toBe(estado === 'BORRADOR')
})

it('muestra los datos y la auditoría a Operaciones en modo consulta', async () => {
  await abrir('BORRADOR', 'OPERACIONES')
  expect(screen.getByText(/1\.500\.000,50/)).toBeVisible()
  expect(screen.getByText(formatoInstante(contrato.estadoActualizadoEn))).toBeVisible()
  expect(screen.getByText(contrato.estadoActualizadoPor)).toBeVisible()
  expect(screen.queryByRole('button')).toBeNull()
  expect(screen.queryByRole('link', { name: 'Editar' })).toBeNull()
})

it.each([['Finalizar', 'FINALIZADO'], ['Cancelar contrato', 'CANCELADO']])('confirma %s antes del PATCH', async (accion, estado) => {
  await abrir('ACTIVO')
  api.cambiarEstadoContrato.mockResolvedValue({ ...contrato, estado, estadoActualizadoPor: 'actor-nuevo' })
  const boton = screen.getByRole('button', { name: accion })
  fireEvent.click(boton)
  const dialogo = screen.getByRole('dialog')
  expect(dialogo).toHaveAccessibleDescription('Este cambio es irreversible.')
  fireEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar' }))
  expect(api.cambiarEstadoContrato).not.toHaveBeenCalled()
  fireEvent.click(boton)
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar' }))
  expect(await screen.findByText(estado)).toBeVisible()
  expect(api.cambiarEstadoContrato).toHaveBeenCalledExactlyOnceWith('uno', estado)
  expect(screen.getByText('actor-nuevo')).toBeVisible()
  expect(screen.queryByRole('button')).toBeNull()
})

it('ante un 409 conserva el mensaje y recarga el estado y las acciones vigentes', async () => {
  await abrir()
  api.cambiarEstadoContrato.mockRejectedValue({ status: 409, message: 'La transición ya no está permitida.' })
  api.obtenerContrato.mockResolvedValueOnce({ ...contrato, estado: 'FINALIZADO' })
  fireEvent.click(screen.getByRole('button', { name: 'Activar' }))
  await screen.findByText('FINALIZADO')
  expect(screen.getByRole('alert')).toHaveTextContent('La transición ya no está permitida.')
  expect(api.obtenerContrato).toHaveBeenCalledTimes(2)
  expect(screen.queryByRole('link', { name: 'Editar' })).toBeNull()
  expect(screen.queryByRole('button')).toBeNull()
})
