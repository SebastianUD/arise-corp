import { apiRequest } from './apiClient.js'

function listar(ruta, filtros = {}) {
  const consulta = new URLSearchParams()
  for (const [clave, valor] of Object.entries(filtros)) {
    if (valor != null && String(valor).trim() !== '') consulta.set(clave, valor)
  }
  return apiRequest(consulta.size ? `${ruta}?${consulta}` : ruta)
}

const empleados = '/api/erp/empleados'
const proveedores = '/api/erp/proveedores'
const movimientos = '/api/erp/movimientos'

export const listarEmpleados = filtros => listar(empleados, filtros)
export const obtenerEmpleado = id => apiRequest(`${empleados}/${id}`)
export const crearEmpleado = datos => apiRequest(empleados, { method: 'POST', body: datos })
export const actualizarEmpleado = (id, datos) => apiRequest(`${empleados}/${id}`, { method: 'PUT', body: datos })
export const cambiarEstadoEmpleado = (id, activo) => apiRequest(`${empleados}/${id}`, { method: 'PATCH', body: { activo } })

export const listarProveedores = filtros => listar(proveedores, filtros)
export const obtenerProveedor = id => apiRequest(`${proveedores}/${id}`)
export const crearProveedor = datos => apiRequest(proveedores, { method: 'POST', body: datos })
export const actualizarProveedor = (id, datos) => apiRequest(`${proveedores}/${id}`, { method: 'PUT', body: datos })
export const cambiarEstadoProveedor = (id, activo) => apiRequest(`${proveedores}/${id}`, { method: 'PATCH', body: { activo } })

export const listarMovimientos = filtros => listar(movimientos, filtros)
export const obtenerMovimiento = id => apiRequest(`${movimientos}/${id}`)
export const crearMovimiento = datos => apiRequest(movimientos, { method: 'POST', body: { ...datos, valor: String(datos.valor) } })
export const actualizarMovimiento = (id, datos) => apiRequest(`${movimientos}/${id}`, { method: 'PUT', body: { ...datos, valor: String(datos.valor) } })
export const cambiarEstadoMovimiento = (id, estado) => apiRequest(`${movimientos}/${id}/estado`, { method: 'PATCH', body: { estado } })
export const obtenerResumenFinanciero = filtros => listar('/api/erp/resumen', filtros)
