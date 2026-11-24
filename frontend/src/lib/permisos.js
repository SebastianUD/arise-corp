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

export const puedeGestionarSga = rol => rol === 'ADMINISTRADOR' || rol === 'OPERACIONES'
export const TRANSICIONES_PAQUETE = {
  REGISTRADO: ['ASIGNADO', 'CANCELADO'], ASIGNADO: ['EN_TRANSITO', 'CANCELADO'],
  EN_TRANSITO: ['ENTREGADO', 'DEVUELTO'], ENTREGADO: [], CANCELADO: [], DEVUELTO: [],
}
export const esPaqueteFinal = estado => (TRANSICIONES_PAQUETE[estado] ?? []).length === 0
export const esPaqueteEditable = estado => estado === 'REGISTRADO' || estado === 'ASIGNADO'
export const TRANSICIONES_MANUALES_DRON = {
  DISPONIBLE: ['MANTENIMIENTO', 'FUERA_DE_SERVICIO'], MANTENIMIENTO: ['DISPONIBLE', 'FUERA_DE_SERVICIO'],
  FUERA_DE_SERVICIO: ['DISPONIBLE', 'MANTENIMIENTO'], EN_MISION: [],
}
export const transicionesManualesDron = dron => dron.paqueteEnCurso ? [] : TRANSICIONES_MANUALES_DRON[dron.estado] ?? []
export const OPCIONES_SGA = [
  { texto: 'Drones', ruta: '/sga/drones', accion: 'Gestionar drones' },
  { texto: 'Paquetes', ruta: '/sga/paquetes', accion: 'Registrar y seguir paquetes' },
  { texto: 'Inventario', ruta: '/sga/inventario', accion: 'Controlar inventario' },
  { texto: 'Indicadores SGA', ruta: '/sga/resumen', accion: 'Ver indicadores' },
]
export const opcionesMenuSga = rol => puedeGestionarSga(rol) ? OPCIONES_SGA : []

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
