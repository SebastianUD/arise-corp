import { expect, it } from 'vitest'
import { esEstadoFinal, opcionesMenu, puedeGestionarCrm, puedeLeerClientes, puedeLeerContratos, TRANSICIONES } from './permisos.js'

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
const menuGestor = [clientes,
  { texto: 'Cuentas corporativas', ruta: '/crm/cuentas-corporativas' },
  { texto: 'Resumen comercial', ruta: '/crm/resumen' },
]
it.each([
  ['ADMINISTRADOR', true, true, true, menuGestor],
  ['COMERCIAL', true, true, true, menuGestor],
  ['OPERACIONES', false, true, true, [clientes]],
  ['FINANZAS_RRHH', false, true, false, [clientes]],
  [null, false, false, false, []],
  ['DESCONOCIDO', false, false, false, []],
])('Property 19: menú y permisos para %s', (rol, gestiona, leeClientes, leeContratos, menu) => {
  expect(puedeGestionarCrm(rol)).toBe(gestiona)
  expect(puedeLeerClientes(rol)).toBe(leeClientes)
  expect(puedeLeerContratos(rol)).toBe(leeContratos)
  expect(opcionesMenu(rol)).toEqual(menu)
})
