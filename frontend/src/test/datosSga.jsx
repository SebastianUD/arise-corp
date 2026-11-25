import { render } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'

export const cliente = { id: '12345678-0000-4000-8000-000000000001', nombre: 'Cliente ficticio', activo: true }
export const contrato = { id: '12345678-0000-4000-8000-000000000002', codigo: 'CTR-001', estado: 'ACTIVO' }
export const dron = {
  id: '12345678-0000-4000-8000-000000000003', codigo: 'DRN-001', modelo: 'Carga ligera', estado: 'DISPONIBLE', paqueteEnCurso: null,
  creadoEn: '2026-11-24T15:00:00Z', actualizadoEn: '2026-11-24T15:00:00Z', actualizadoPor: 'actor-ficticio',
  estadoActualizadoEn: '2026-11-24T15:00:00Z', estadoActualizadoPor: 'actor-ficticio',
}
export const paquete = { ...dron, id: '12345678-0000-4000-8000-000000000004', codigo: 'PAQ-001', descripcion: 'Entrega ficticia', direccionEntrega: 'Dirección ficticia', estado: 'REGISTRADO', cliente, contrato, dron: null }
export const articulo = { id: '12345678-0000-4000-8000-000000000005', codigo: 'BAT-001', nombre: 'Batería ficticia', categoria: 'BATERIA', unidad: 'UNIDAD', stockMinimo: 10, existencia: 6, bajoStockMinimo: true }
export const pagina = (content = [], page = 0, totalPages = 1) => ({ content, page, totalPages, totalElements: content.length, size: 20 })
export function abrirSga(Componente, ruta, patron = ruta.split('?')[0]) {
  return render(<MemoryRouter initialEntries={[ruta]}><Routes><Route path={patron} element={<Componente />} /><Route path="*" element={<p>Destino del registro</p>} /></Routes></MemoryRouter>)
}
