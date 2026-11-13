import { apiRequest } from './apiClient.js'

function listar(ruta, filtros = {}) {
  const consulta = new URLSearchParams()
  for (const [clave, valor] of Object.entries(filtros)) {
    if (valor != null && String(valor).trim() !== '') consulta.set(clave, valor)
  }
  return apiRequest(consulta.size ? `${ruta}?${consulta}` : ruta)
}

const clientes = '/api/crm/clientes'

export function listarClientes(filtros) {
  return listar(clientes, filtros)
}

export function obtenerCliente(id) {
  return apiRequest(`${clientes}/${id}`)
}

export function crearCliente(datos) {
  return apiRequest(clientes, { method: 'POST', body: datos })
}

export function actualizarCliente(id, datos) {
  return apiRequest(`${clientes}/${id}`, { method: 'PUT', body: datos })
}

export function cambiarEstadoCliente(id, activo) {
  return apiRequest(`${clientes}/${id}`, { method: 'PATCH', body: { activo } })
}

const cuentas = '/api/crm/cuentas-corporativas'

export function listarCuentas(filtros) {
  return listar(cuentas, filtros)
}

export function obtenerCuenta(id) {
  return apiRequest(`${cuentas}/${id}`)
}

export function crearCuenta(datos) {
  return apiRequest(cuentas, { method: 'POST', body: datos })
}

export function actualizarCuenta(id, datos) {
  return apiRequest(`${cuentas}/${id}`, { method: 'PUT', body: datos })
}

export function cambiarEstadoCuenta(id, activa) {
  return apiRequest(`${cuentas}/${id}`, { method: 'PATCH', body: { activa } })
}

const contratos = '/api/crm/contratos'

export function listarContratos(filtros) {
  return listar(contratos, filtros)
}

export function obtenerContrato(id) {
  return apiRequest(`${contratos}/${id}`)
}

export function crearContrato(datos) {
  return apiRequest(contratos, { method: 'POST', body: { ...datos, valor: String(datos.valor) } })
}

export function actualizarContrato(id, datos) {
  return apiRequest(`${contratos}/${id}`, { method: 'PUT', body: { ...datos, valor: String(datos.valor) } })
}

export function cambiarEstadoContrato(id, estado) {
  return apiRequest(`${contratos}/${id}/estado`, { method: 'PATCH', body: { estado } })
}

export function obtenerResumen() {
  return apiRequest('/api/crm/resumen')
}
