import { expect, it } from 'vitest'
import { esEstadoFinal, opcionesMenu, puedeGestionarCrm, puedeLeerClientes, puedeLeerContratos, TRANSICIONES } from './permisos.js'
import { esMovimientoEditable, opcionesMenuErp, OPCIONES_ERP, puedeGestionarErp, TRANSICIONES_MOVIMIENTO } from './permisos.js'

it.each(['PENDIENTE', 'PAGADO', 'ANULADO'])('Feature: 003-erp-empleados-proveedores-finanzas, Property 12: Solo se aceptan las dos transiciones permitidas (%s)', estado => {
  expect(TRANSICIONES_MOVIMIENTO[estado]).toEqual(estado === 'PENDIENTE' ? ['PAGADO', 'ANULADO'] : [])
  expect(esMovimientoEditable(estado)).toBe(estado === 'PENDIENTE')
})

it.each(['ADMINISTRADOR', 'FINANZAS_RRHH', 'COMERCIAL', 'OPERACIONES', null, 'DESCONOCIDO'])('Feature: 003-erp-empleados-proveedores-finanzas, Property 21: El menú y el panel ERP derivan del rol (%s)', rol => {
  const permitido = ['ADMINISTRADOR', 'FINANZAS_RRHH'].includes(rol)
  expect(puedeGestionarErp(rol)).toBe(permitido)
  expect(opcionesMenuErp(rol)).toEqual(permitido ? OPCIONES_ERP : [])
  expect(OPCIONES_ERP.map(opcion => opcion.ruta)).toEqual(['/erp/empleados', '/erp/proveedores', '/erp/movimientos', '/erp/resumen'])
})

it.each([
  ['BORRADOR', ['ACTIVO', 'CANCELADO'], false],
  ['ACTIVO', ['FINALIZADO', 'CANCELADO'], false],
  ['FINALIZADO', [], true],
  ['CANCELADO', [], true],
])('Property 11: destinos y carácter final de %s', (estado, destinos, final) => {
  expect(TRANSICIONES[estado]).toEqual(destinos)
  expect(esEstadoFinal(estado)).toBe(final)
})

const clientes = { texto: 'Clientes', ruta: '/crm/clientes' }
const contratos = { texto: 'Contratos', ruta: '/crm/contratos' }
const menuGestor = [clientes,
  { texto: 'Cuentas corporativas', ruta: '/crm/cuentas-corporativas' },
  { texto: 'Resumen comercial', ruta: '/crm/resumen' }, contratos,
]
it.each([
  ['ADMINISTRADOR', true, true, true, [...menuGestor, { texto: 'Usuarios y roles', ruta: '/admin/usuarios' }]],
  ['COMERCIAL', true, true, true, menuGestor],
  ['OPERACIONES', false, true, true, [clientes, contratos]],
  ['FINANZAS_RRHH', false, true, false, [clientes]],
  [null, false, false, false, []],
  ['DESCONOCIDO', false, false, false, []],
])('Property 19: menú y permisos para %s', (rol, gestiona, leeClientes, leeContratos, menu) => {
  expect(puedeGestionarCrm(rol)).toBe(gestiona)
  expect(puedeLeerClientes(rol)).toBe(leeClientes)
  expect(puedeLeerContratos(rol)).toBe(leeContratos)
  expect(opcionesMenu(rol)).toEqual(menu)
})
