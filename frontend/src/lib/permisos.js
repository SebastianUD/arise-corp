export const puedeGestionarCrm = rol => rol === 'ADMINISTRADOR' || rol === 'COMERCIAL'
export const puedeLeerClientes = rol => puedeGestionarCrm(rol) || rol === 'FINANZAS_RRHH' || rol === 'OPERACIONES'
export const puedeLeerContratos = rol => puedeGestionarCrm(rol) || rol === 'OPERACIONES'

export const TRANSICIONES = {
  BORRADOR: ['ACTIVO', 'CANCELADO'],
  ACTIVO: ['FINALIZADO', 'CANCELADO'],
  FINALIZADO: [],
  CANCELADO: [],
}

export const esEstadoFinal = estado => estado === 'FINALIZADO' || estado === 'CANCELADO'

export function opcionesMenu(rol) {
  if (!puedeLeerClientes(rol)) return []
  const opciones = [{ texto: 'Clientes', ruta: '/crm/clientes' }]
  if (puedeGestionarCrm(rol)) {
    opciones.push(
      { texto: 'Cuentas corporativas', ruta: '/crm/cuentas-corporativas' },
      { texto: 'Resumen comercial', ruta: '/crm/resumen' },
    )
  }
  return opciones
}
