import { expect, it } from 'vitest'
import { cadena, entero, mulberry32 } from '../test/generadores.js'
import * as v from './validacionSga.js'

const id = '3f2c9a4e-0000-4000-8000-000000000001'
it('normaliza códigos y respeta longitudes y asociaciones', () => {
  const azar = mulberry32(20261125)
  for (let i = 0; i < 100; i++) {
    const codigo = cadena(azar, 'abc012-', entero(azar, 3, 30))
    expect(v.esCodigoValido(' ' + codigo + ' ')).toBe(true)
  }
  for (const codigo of ['', 'AB', 'A'.repeat(31), 'AB C', 'ábc', 'A/B']) expect(v.esCodigoValido(codigo)).toBe(false)
  expect(v.validarDron({ codigo: ' abc ', modelo: 'X'.repeat(100), observaciones: 'X'.repeat(500) })).toEqual({})
  expect(Object.keys(v.validarDron({ codigo: 'X', modelo: 'X'.repeat(101), observaciones: 'X'.repeat(501) }))).toEqual(['codigo', 'modelo', 'observaciones'])
  const paquete = { codigo: 'PAQ-01', descripcion: 'X'.repeat(500), direccionEntrega: 'X'.repeat(200), clienteId: id, contratoId: id }
  expect(v.validarPaquete(paquete)).toEqual({})
  expect(v.validarPaquete({ ...paquete, descripcion: 'X'.repeat(501), direccionEntrega: 'X'.repeat(201), clienteId: '', contratoId: 'x', observacion: 'X'.repeat(501) })).toHaveProperty('clienteId')
  expect(v.validarEdicionPaquete({ descripcion: 'Entrega', direccionEntrega: 'Bogotá' })).toEqual({})
  expect(v.validarTransicion({ estado: 'ASIGNADO' })).toHaveProperty('dronId', 'es obligatorio para asignar el paquete')
  expect(v.validarTransicion({ estado: 'ASIGNADO', dronId: id })).toEqual({})
  expect(v.validarTransicion({ estado: 'ENTREGADO', dronId: id, observacion: 'X'.repeat(501) })).toHaveProperty('observacion')
  expect(v.validarArticulo({ codigo: 'ART-1', nombre: 'X'.repeat(200), categoria: 'BATERIA', unidad: 'UNIDAD', stockMinimo: '0' })).toEqual({})
  expect(v.validarArticulo({ codigo: 'ART-1', nombre: 'X'.repeat(201), categoria: 'x', unidad: 'x', stockMinimo: '1.5' })).toHaveProperty('stockMinimo')
})

it.each(v.TIPOS_MOVIMIENTO_INVENTARIO)('Feature: 004-sga-drones-paquetes-inventario, Property 8: Las cantidades se aceptan solo como enteros dentro del rango de su tipo (%s)', tipo => {
  const azar = mulberry32(20261125)
  const min = tipo === 'AJUSTE' ? 0 : 1
  for (const n of [min, 1000000, ...Array.from({ length: 100 }, () => entero(azar, min, 1000000))]) {
    expect(v.validarMovimientoInventario({ tipo, cantidad: ` ${n} `, observacion: 'Conteo' })).toEqual({})
  }
  for (const cantidad of ['', ' ', '-1', '+1', '1.0', '1e3', '1,000', '1 0', '1000001', '00000000', 'NaN', ...(min ? ['0'] : [])]) {
    expect(v.validarMovimientoInventario({ tipo, cantidad, observacion: 'Conteo' }), cantidad).toHaveProperty('cantidad')
  }
  expect(v.validarMovimientoInventario({ tipo: 'AJUSTE', cantidad: '0', observacion: ' ' })).toHaveProperty('observacion', 'es obligatoria en un ajuste')
})
