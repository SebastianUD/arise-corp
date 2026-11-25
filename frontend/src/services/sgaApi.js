import { apiRequest } from './apiClient.js'
import { texto } from '../lib/validacionCrm.js'

function listar(ruta, filtros = {}) {
  const consulta = new URLSearchParams()
  for (const [clave, valor] of Object.entries(filtros)) {
    if (valor != null && String(valor).trim() !== '') consulta.set(clave, valor)
  }
  return apiRequest(consulta.size ? `${ruta}?${consulta}` : ruta)
}

export const cuerpoTransicion = ({ estado, dronId, observacion }) =>
  ({ estado, observacion: texto(observacion), ...(estado === 'ASIGNADO' ? { dronId } : {}) })
export const cuerpoMovimientoInventario = ({ tipo, cantidad, observacion }) =>
  ({ tipo, cantidad: Number(String(cantidad).trim()), observacion: texto(observacion) })

const drones = '/api/sga/drones'
const paquetes = '/api/sga/paquetes'
const articulos = '/api/sga/articulos'

export const listarDrones = filtros => listar(drones, filtros)
export const obtenerDron = id => apiRequest(`${drones}/${id}`)
export const crearDron = datos => apiRequest(drones, { method: 'POST', body: datos })
export const actualizarDron = (id, datos) => apiRequest(`${drones}/${id}`, { method: 'PUT', body: datos })
export const cambiarEstadoDron = (id, estado) => apiRequest(`${drones}/${id}/estado`, { method: 'PATCH', body: { estado } })
export const listarPaquetes = filtros => listar(paquetes, filtros)
export const obtenerPaquete = id => apiRequest(`${paquetes}/${id}`)
export const crearPaquete = datos => apiRequest(paquetes, { method: 'POST', body: datos })
export const actualizarPaquete = (id, datos) => apiRequest(`${paquetes}/${id}`, { method: 'PUT', body: datos })
export const obtenerHistorialPaquete = id => apiRequest(`${paquetes}/${id}/estados`)
export const cambiarEstadoPaquete = (id, datos) => apiRequest(`${paquetes}/${id}/estados`, { method: 'POST', body: cuerpoTransicion(datos) })
export const listarArticulos = filtros => listar(articulos, filtros)
export const obtenerArticulo = id => apiRequest(`${articulos}/${id}`)
export const crearArticulo = datos => apiRequest(articulos, { method: 'POST', body: { ...datos, stockMinimo: Number(datos.stockMinimo) } })
export const actualizarArticulo = (id, datos) => apiRequest(`${articulos}/${id}`, { method: 'PUT', body: { ...datos, stockMinimo: Number(datos.stockMinimo) } })
export const listarMovimientosInventario = (id, filtros) => listar(`${articulos}/${id}/movimientos`, filtros)
export const registrarMovimientoInventario = (id, datos) => apiRequest(`${articulos}/${id}/movimientos`, { method: 'POST', body: cuerpoMovimientoInventario(datos) })
export const obtenerResumenSga = () => apiRequest('/api/sga/resumen')
