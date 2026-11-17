export const etiquetasRol = {
  ADMINISTRADOR: 'Administrador',
  COMERCIAL: 'Comercial',
  FINANZAS_RRHH: 'Finanzas / RR. HH.',
  OPERACIONES: 'Operaciones',
}

export const puedeAdministrarUsuarios = rol => rol === 'ADMINISTRADOR'
export const puedeGestionarCrm = rol => rol === 'ADMINISTRADOR' || rol === 'COMERCIAL'
export const puedeLeerClientes = rol => puedeGestionarCrm(rol) || rol === 'FINANZAS_RRHH' || rol === 'OPERACIONES'
export const puedeLeerContratos = rol => puedeGestionarCrm(rol) || rol === 'OPERACIONES'

export const puedeGestionarErp = rol => rol === 'ADMINISTRADOR' || rol === 'FINANZAS_RRHH'
export const TRANSICIONES_MOVIMIENTO = { PENDIENTE: ['PAGADO', 'ANULADO'], PAGADO: [], ANULADO: [] }
export const esMovimientoEditable = estado => estado === 'PENDIENTE'
export const OPCIONES_ERP = [
  { texto: 'Empleados', ruta: '/erp/empleados', accion: 'Gestionar empleados' },
  { texto: 'Proveedores', ruta: '/erp/proveedores', accion: 'Gestionar proveedores' },
  { texto: 'Movimientos financieros', ruta: '/erp/movimientos', accion: 'Registrar movimientos' },
  { texto: 'Panel financiero', ruta: '/erp/resumen', accion: 'Ver totales' },
]
export const opcionesMenuErp = rol => puedeGestionarErp(rol) ? OPCIONES_ERP : []

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
  if (puedeLeerContratos(rol)) opciones.push({ texto: 'Contratos', ruta: '/crm/contratos' })
  if (puedeAdministrarUsuarios(rol)) opciones.push({ texto: 'Usuarios y roles', ruta: '/admin/usuarios' })
  return opciones
}
