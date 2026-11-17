import { expect, it } from 'vitest'
import { normalizarDocumento } from './validacionCrm.js'
import { cuerpoMovimiento, fechaHoyColombia, normalizarReferencia, validarEmpleado, validarMovimiento, validarProveedor, validarRangoFechas } from './validacionErp.js'
import { cadena, elegir, entero, mulberry32 } from '../test/generadores.js'

const propiedad = n => `Feature: 003-erp-empleados-proveedores-finanzas, Property ${n}: `
const registro = { tipoDocumento: 'CC', numeroDocumento: '123456', nombre: 'Ejemplo', cargo: 'Analista', area: 'Finanzas', correo: 'ejemplo@example.test', telefono: '601 000 0000', fechaIngreso: '2025-01-01' }
const movimiento = { tipo: 'INGRESO', fecha: '2026-11-17', concepto: 'Servicio ficticio', valor: '2500000.50' }

it.each([
  ['2026-11-17T04:59:59Z', '2026-11-16'], ['2026-11-17T05:00:00Z', '2026-11-17'],
  ['2027-01-01T03:00:00Z', '2026-12-31'],
])('calcula la fecha de Colombia para %s', (instante, esperado) => expect(fechaHoyColombia(new Date(instante))).toBe(esperado))

it(propiedad(1) + 'La normalización es idempotente', () => {
  const azar = mulberry32(20261113)
  for (const valor of [null, '', '   ', ' a.b-1 ', ...Array.from({ length: 100 }, () => cadena(azar, 'abXY01 .-/\t', entero(azar, 0, 60)))]) {
    for (const normalizar of [normalizarDocumento, normalizarReferencia]) expect(normalizar(normalizar(valor)), String(valor)).toBe(normalizar(valor))
  }
})

it(propiedad(2) + 'Las variantes de formato de un documento o de una referencia son el mismo valor', () => {
  const azar = mulberry32(20261113)
  for (let i = 0; i < 100; i++) {
    const base = cadena(azar, 'ABC0123456789', entero(azar, 3, 20))
    expect(normalizarDocumento(' ' + [...base.toLowerCase()].join(elegir(azar, ['.', '-', ' '])) + ' '), base).toBe(base)
    expect(normalizarReferencia('  ' + base.toLowerCase() + '  '), base).toBe(base)
  }
})

it(propiedad(3) + 'Las validaciones de formato coinciden con la regla de referencia', () => {
  const azar = mulberry32(20261113)
  const valores = ['0', '0.00', '0.01', '-1', '1e3', '1,2', '1.234', '9999999999999.99', '10000000000000', 12, '', '00.10']
  for (let i = 0; i < 100; i++) valores.push(cadena(azar, '0123456789.e- ', entero(azar, 1, 20)))
  for (const valor of valores) {
    const valido = typeof valor === 'string' && /^\d{1,13}(\.\d{1,2})?$/.test(valor) && /[1-9]/.test(valor)
    expect(!validarMovimiento({ ...movimiento, valor }).valor, String(valor)).toBe(valido)
  }
  for (let i = 0; i < 110; i++) {
    const numero = cadena(azar, 'AB12 .-!_', entero(azar, 0, 25))
    const tipoDocumento = elegir(azar, ['CC', 'CE', 'PASAPORTE', 'NIT'])
    const limpio = numero.replace(/[\s.-]/gu, '').toUpperCase()
    for (const [validar, soloDigitos] of [[validarEmpleado, tipoDocumento === 'CC'], [validarProveedor, ['CC', 'NIT'].includes(tipoDocumento)]]) {
      const valido = /^[A-Z0-9]{3,20}$/.test(limpio) && (!soloDigitos || /^\d+$/.test(limpio))
      expect(!validar({ ...registro, tipoDocumento, numeroDocumento: numero }).numeroDocumento, JSON.stringify({ numero, tipoDocumento })).toBe(valido)
    }
    const referencia = cadena(azar, 'abAB12 ./-!_', entero(azar, 0, 55))
    const ref = referencia.trim().toUpperCase()
    expect(!validarMovimiento({ ...movimiento, referencia }).referencia, referencia).toBe(!ref || /^[A-Z0-9 ./-]{1,50}$/.test(ref))
  }
})

it('valida obligatorios, límites, fechas, correo y teléfono', () => {
  expect(validarEmpleado(registro, '2026-11-17')).toEqual({})
  expect(validarProveedor({ ...registro, nombreContacto: '', direccion: '', ciudad: '' })).toEqual({})
  expect(Object.keys(validarEmpleado({}))).toHaveLength(8)
  expect(validarEmpleado({ ...registro, fechaIngreso: '2026-11-18' }, '2026-11-17').fechaIngreso).toMatch(/posterior/)
  expect(validarEmpleado({ ...registro, fechaIngreso: '2026-02-30' }).fechaIngreso).toMatch(/fecha válida/)
  expect(validarEmpleado({ ...registro, tipoDocumento: 'NIT', cargo: 'x'.repeat(101), area: 'x'.repeat(101), correo: 'a@b', telefono: '12' })).toMatchObject({ tipoDocumento: expect.any(String), cargo: expect.any(String), area: expect.any(String), correo: expect.any(String), telefono: expect.any(String) })
  expect(validarProveedor({ ...registro, nombre: 'x'.repeat(201), nombreContacto: 'x'.repeat(201), direccion: 'x'.repeat(201), ciudad: 'x'.repeat(101) })).toMatchObject({ nombre: expect.any(String), nombreContacto: expect.any(String), direccion: expect.any(String), ciudad: expect.any(String) })
  expect(validarMovimiento({ ...movimiento, concepto: '  ab  ', referencia: 'Á', clienteId: 'incorrecto' })).toMatchObject({ concepto: expect.any(String), referencia: expect.any(String), clienteId: expect.any(String) })
  expect(validarMovimiento({ ...movimiento, proveedorId: '12345678-0000-4000-8000-000000000001' }).proveedorId).toMatch(/EGRESO/)
})

it(propiedad(9) + 'La relación es coherente con el tipo', () => {
  const azar = mulberry32(20261113)
  let datos = { ...movimiento, clienteId: null, proveedorId: null }
  for (let i = 0; i < 120; i++) {
    datos = { ...datos, tipo: elegir(azar, ['INGRESO', 'EGRESO']), clienteId: elegir(azar, ['', null, 'cliente']), proveedorId: elegir(azar, ['', null, 'proveedor']) }
    const cuerpo = cuerpoMovimiento(datos)
    expect(cuerpo.clienteId, JSON.stringify(datos)).toBe(datos.tipo === 'INGRESO' ? datos.clienteId || null : null)
    expect(cuerpo.proveedorId, JSON.stringify(datos)).toBe(datos.tipo === 'EGRESO' ? datos.proveedorId || null : null)
    expect(cuerpo.valor).toBe('2500000.50')
  }
})

it(propiedad(15) + 'Los parámetros de lista y de rango se aceptan o rechazan según la regla', () => {
  const azar = mulberry32(20261113)
  for (let i = 0; i < 120; i++) {
    const desde = elegir(azar, ['', `2026-11-${entero(azar, 10, 29)}`])
    const hasta = elegir(azar, [null, `2026-11-${entero(azar, 10, 29)}`])
    expect(Boolean(validarRangoFechas(desde, hasta).hasta), JSON.stringify({ desde, hasta })).toBe(Boolean(desde && hasta && desde > hasta))
  }
  expect(validarRangoFechas('2026-02-30', 'ayer')).toMatchObject({ desde: expect.any(String), hasta: expect.any(String) })
})
