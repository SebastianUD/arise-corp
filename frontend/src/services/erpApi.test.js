import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { mulberry32, elegir, entero } from '../test/generadores.js'
import * as erp from './erpApi.js'

const { auth } = vi.hoisted(() => ({ auth: { getSession: vi.fn(), signOut: vi.fn() } }))
vi.mock('../lib/supabase.js', () => ({ supabase: { auth } }))

beforeEach(() => {
  vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080')
  auth.getSession.mockResolvedValue({ data: { session: { access_token: 'token-ficticio' } } })
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ id: 'ejemplo' }) }))
})
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

it.each([
  [erp.listarEmpleados, 'empleados'], [erp.listarProveedores, 'proveedores'],
  [erp.listarMovimientos, 'movimientos'], [erp.obtenerResumenFinanciero, 'resumen'],
])('Feature: 003-erp-empleados-proveedores-finanzas, Property 20: El servicio ERP del frontend construye las peticiones del contrato (%s)', async (listar, ruta) => {
  const azar = mulberry32(20261113)
  const casos = [{}, { q: 'A & B', activo: false, page: 0, size: 20 }, { q: '  ', desde: null, hasta: undefined }]
  for (let i = 0; i < 100; i++) casos.push({
    q: elegir(azar, [null, '', '  ', 'Ana & Co', 'a/b?c']), activo: elegir(azar, [true, false, undefined]),
    page: entero(azar, 0, 20), desde: elegir(azar, ['', null, '2026-01-01']),
  })
  for (const filtros of casos) {
    await listar(filtros)
    const [url, opciones] = fetch.mock.lastCall
    const leida = new URL(url)
    expect(leida.pathname, JSON.stringify(filtros)).toBe('/api/erp/' + ruta)
    expect(Object.fromEntries(leida.searchParams), JSON.stringify(filtros)).toEqual(Object.fromEntries(
      Object.entries(filtros).filter(([, v]) => v != null && String(v).trim() !== '').map(([k, v]) => [k, String(v)]),
    ))
    expect(opciones.method).toBe('GET')
    expect(opciones.headers.Authorization).toBe('Bearer token-ficticio')
  }
})

const datos = { nombre: 'Ejemplo ficticio', fechaIngreso: '2025-03-01' }
it.each([
  [erp.obtenerEmpleado, ['ejemplo'], 'GET', 'empleados/ejemplo'],
  [erp.obtenerProveedor, ['ejemplo'], 'GET', 'proveedores/ejemplo'],
  [erp.obtenerMovimiento, ['ejemplo'], 'GET', 'movimientos/ejemplo'],
  [erp.crearEmpleado, [datos], 'POST', 'empleados', datos],
  [erp.actualizarEmpleado, ['ejemplo', datos], 'PUT', 'empleados/ejemplo', datos],
  [erp.cambiarEstadoEmpleado, ['ejemplo', false], 'PATCH', 'empleados/ejemplo', { activo: false }],
  [erp.crearProveedor, [datos], 'POST', 'proveedores', datos],
  [erp.actualizarProveedor, ['ejemplo', datos], 'PUT', 'proveedores/ejemplo', datos],
  [erp.cambiarEstadoProveedor, ['ejemplo', true], 'PATCH', 'proveedores/ejemplo', { activo: true }],
  [erp.cambiarEstadoMovimiento, ['ejemplo', 'PAGADO'], 'PATCH', 'movimientos/ejemplo/estado', { estado: 'PAGADO' }],
])('respeta método, ruta y cuerpo de %s', async (accion, args, method, ruta, body) => {
  expect(await accion(...args)).toEqual({ id: 'ejemplo' })
  expect(fetch).toHaveBeenLastCalledWith('http://localhost:8080/api/erp/' + ruta, expect.objectContaining({ method }))
  expect(fetch.mock.lastCall[1].body).toBe(body ? JSON.stringify(body) : undefined)
})

it('Feature: 003-erp-empleados-proveedores-finanzas, Property 5: Los importes viajan como texto sin perder precisión', async () => {
  const azar = mulberry32(20261113)
  const valores = ['0.01', '1', '2500000.50', '9999999999999.99']
  for (let i = 0; i < 100; i++) valores.push(`${entero(azar, 1, 9999999999999)}.${entero(azar, 0, 99).toString().padStart(2, '0')}`)
  for (const valor of valores) {
    for (const [accion, args, method, ruta] of [
      [erp.crearMovimiento, [], 'POST', 'movimientos'],
      [erp.actualizarMovimiento, ['ejemplo'], 'PUT', 'movimientos/ejemplo'],
    ]) {
      const cuerpo = { tipo: 'INGRESO', fecha: '2026-11-17', valor }
      await accion(...args, cuerpo)
      expect(fetch.mock.lastCall[0]).toBe('http://localhost:8080/api/erp/' + ruta)
      expect(fetch.mock.lastCall[1].method).toBe(method)
      expect(JSON.parse(fetch.mock.lastCall[1].body), valor).toEqual(cuerpo)
    }
  }
})
