import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import * as sga from './sgaApi.js'
import { ESTADOS_PAQUETE } from '../lib/validacionSga.js'
import { entero, mulberry32 } from '../test/generadores.js'

const { auth } = vi.hoisted(() => ({ auth: { getSession: vi.fn(), signOut: vi.fn() } }))
vi.mock('../lib/supabase.js', () => ({ supabase: { auth } }))
beforeEach(() => {
  vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080')
  auth.getSession.mockResolvedValue({ data: { session: { access_token: 'token-ficticio' } } })
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ id: 'ejemplo' }) }))
})
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

it.each([
  [sga.listarDrones, [], 'drones'], [sga.listarPaquetes, [], 'paquetes'],
  [sga.listarArticulos, [], 'articulos'], [sga.listarMovimientosInventario, ['id'], 'articulos/id/movimientos'],
])('omite filtros vacíos y conserva ceros y booleanos (%s)', async (listar, args, ruta) => {
  await listar(...args, { q: 'A & B', estado: ' ', page: 0, asignable: true, bajoStockMinimo: false, size: null })
  const [url, opciones] = fetch.mock.lastCall
  expect(new URL(url).pathname).toBe('/api/sga/' + ruta)
  expect(Object.fromEntries(new URL(url).searchParams)).toEqual({ q: 'A & B', page: '0', asignable: 'true', bajoStockMinimo: 'false' })
  expect(opciones.method).toBe('GET')
  expect(opciones.headers.Authorization).toBe('Bearer token-ficticio')
})

const datos = { codigo: 'DRN-001', modelo: 'Carga' }
it.each([
  [sga.obtenerDron, ['id'], 'GET', 'drones/id'],
  [sga.crearDron, [datos], 'POST', 'drones', datos],
  [sga.actualizarDron, ['id', datos], 'PUT', 'drones/id', datos],
  [sga.cambiarEstadoDron, ['id', 'MANTENIMIENTO'], 'PATCH', 'drones/id/estado', { estado: 'MANTENIMIENTO' }],
  [sga.obtenerPaquete, ['id'], 'GET', 'paquetes/id'],
  [sga.crearPaquete, [datos], 'POST', 'paquetes', datos],
  [sga.actualizarPaquete, ['id', datos], 'PUT', 'paquetes/id', datos],
  [sga.obtenerHistorialPaquete, ['id'], 'GET', 'paquetes/id/estados'],
  [sga.cambiarEstadoPaquete, ['id', { estado: 'ENTREGADO', dronId: 'descartado', observacion: '  Listo ' }], 'POST', 'paquetes/id/estados', { estado: 'ENTREGADO', observacion: 'Listo' }],
  [sga.obtenerArticulo, ['id'], 'GET', 'articulos/id'],
  [sga.crearArticulo, [{ stockMinimo: '0' }], 'POST', 'articulos', { stockMinimo: 0 }],
  [sga.actualizarArticulo, ['id', { stockMinimo: '1000000' }], 'PUT', 'articulos/id', { stockMinimo: 1000000 }],
  [sga.registrarMovimientoInventario, ['id', { tipo: 'AJUSTE', cantidad: '0', observacion: ' Conteo ' }], 'POST', 'articulos/id/movimientos', { tipo: 'AJUSTE', cantidad: 0, observacion: 'Conteo' }],
  [sga.obtenerResumenSga, [], 'GET', 'resumen'],
])('respeta método, ruta y cuerpo (%s)', async (accion, args, method, ruta, body) => {
  expect(await accion(...args)).toEqual({ id: 'ejemplo' })
  expect(fetch.mock.lastCall[0]).toBe('http://localhost:8080/api/sga/' + ruta)
  expect(fetch.mock.lastCall[1].method).toBe(method)
  expect(fetch.mock.lastCall[1].body).toBe(body ? JSON.stringify(body) : undefined)
})

it.each(ESTADOS_PAQUETE)('Feature: 004-sga-drones-paquetes-inventario, Property 3: Solo se asigna un dron asignable (%s)', estado => {
  const cuerpo = sga.cuerpoTransicion({ estado, dronId: 'id' })
  expect(Object.hasOwn(cuerpo, 'dronId')).toBe(estado === 'ASIGNADO')
})

it('Feature: 004-sga-drones-paquetes-inventario, Property 8: Las cantidades se aceptan solo como enteros dentro del rango de su tipo', () => {
  const azar = mulberry32(20261125)
  for (const tipo of ['ENTRADA', 'SALIDA', 'AJUSTE']) {
    const cantidades = [tipo === 'AJUSTE' ? 0 : 1, 1000000, ...Array.from({ length: 100 }, () => entero(azar, 1, 1000000))]
    for (const cantidad of cantidades) {
      const cuerpo = sga.cuerpoMovimientoInventario({ tipo, cantidad: String(cantidad), observacion: 'Conteo' })
      expect(cuerpo.cantidad).toBe(cantidad)
      expect(Number.isInteger(JSON.parse(JSON.stringify(cuerpo)).cantidad)).toBe(true)
    }
  }
})
