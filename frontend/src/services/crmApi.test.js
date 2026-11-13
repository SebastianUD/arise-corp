import { expect, it, vi } from 'vitest'
import { mulberry32 } from '../test/generadores.js'
import { apiRequest } from './apiClient.js'
import * as crm from './crmApi.js'

vi.mock('./apiClient.js', () => ({ apiRequest: vi.fn() }))

it.each([
  [crm.listarClientes, 'clientes'],
  [crm.listarCuentas, 'cuentas-corporativas'],
  [crm.listarContratos, 'contratos'],
])('construye la consulta del listado %s', (listar, recurso) => {
  const ruta = '/api/crm/' + recurso
  listar()
  expect(apiRequest).toHaveBeenLastCalledWith(ruta)
  listar({ q: 'A & B', page: 0, activo: false, desde: '', hasta: null, orden: undefined })
  expect(apiRequest).toHaveBeenLastCalledWith(ruta + '?q=A+%26+B&page=0&activo=false')
})

it.each([
  [crm.obtenerCliente, ['abc'], 'clientes/abc'],
  [crm.obtenerCuenta, ['abc'], 'cuentas-corporativas/abc'],
  [crm.obtenerContrato, ['abc'], 'contratos/abc'],
  [crm.obtenerResumen, [], 'resumen'],
])('devuelve el resultado de la lectura %s', (leer, args, ruta) => {
  const resultado = Promise.resolve({ id: 'abc' })
  apiRequest.mockReturnValueOnce(resultado)
  expect(leer(...args)).toBe(resultado)
  expect(apiRequest).toHaveBeenLastCalledWith('/api/crm/' + ruta)
})

const datos = { nombre: 'Cliente' }
it.each([
  [crm.crearCliente, [datos], 'POST', 'clientes', datos],
  [crm.actualizarCliente, ['abc', datos], 'PUT', 'clientes/abc', datos],
  [crm.cambiarEstadoCliente, ['abc', false], 'PATCH', 'clientes/abc', { activo: false }],
  [crm.crearCuenta, [datos], 'POST', 'cuentas-corporativas', datos],
  [crm.actualizarCuenta, ['abc', datos], 'PUT', 'cuentas-corporativas/abc', datos],
  [crm.cambiarEstadoCuenta, ['abc', false], 'PATCH', 'cuentas-corporativas/abc', { activa: false }],
  [crm.cambiarEstadoContrato, ['abc', 'ACTIVO'], 'PATCH', 'contratos/abc/estado', { estado: 'ACTIVO' }],
])('envía la escritura %s', (escribir, args, method, ruta, body) => {
  escribir(...args)
  expect(apiRequest).toHaveBeenLastCalledWith('/api/crm/' + ruta, { method, body })
})

it('Property 5: conserva el texto decimal al crear y editar un contrato', () => {
  const azar = mulberry32(21)
  const valores = ['0', '0.00', '0.01', '1500000.5', '9999999999999.99']
  for (let i = 0; i < 100; i++) {
    const entero = Math.floor(azar() * 1e13)
    const decimales = i % 3
    const fraccion = Math.floor(azar() * 10 ** decimales).toString().padStart(decimales, '0')
    valores.push(decimales ? `${entero}.${fraccion}` : String(entero))
  }
  for (const valor of valores) {
    const body = { nombre: 'Contrato', valor }
    crm.crearContrato(body)
    expect(apiRequest, valor).toHaveBeenLastCalledWith('/api/crm/contratos', { method: 'POST', body })
    crm.actualizarContrato('abc', body)
    expect(apiRequest, valor).toHaveBeenLastCalledWith('/api/crm/contratos/abc', { method: 'PUT', body })
  }
})
