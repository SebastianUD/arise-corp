import { texto, revisar, maximo, normalizarCodigo } from './validacionCrm.js'

export const ESTADOS_PAQUETE = ['REGISTRADO', 'ASIGNADO', 'EN_TRANSITO', 'ENTREGADO', 'CANCELADO', 'DEVUELTO']
export const ESTADOS_DRON = ['DISPONIBLE', 'EN_MISION', 'MANTENIMIENTO', 'FUERA_DE_SERVICIO']
export const CATEGORIAS_ARTICULO = ['REPUESTO', 'BATERIA', 'EMPAQUE', 'OTRO']
export const UNIDADES_ARTICULO = ['UNIDAD', 'CAJA', 'JUEGO', 'ROLLO']
export const TIPOS_MOVIMIENTO_INVENTARIO = ['ENTRADA', 'SALIDA', 'AJUSTE']
export const esCodigoValido = valor => /^[A-Z0-9-]{3,30}$/.test(normalizarCodigo(valor) ?? '')
export const esEnteroEnRango = (valor, min, max) =>
  typeof valor === 'string' && /^\d{1,7}$/.test(valor.trim()) && Number(valor.trim()) >= min && Number(valor.trim()) <= max

const uuid = valor => !/^[\da-f]{8}(-[\da-f]{4}){3}-[\da-f]{12}$/i.test(valor)
  && 'debe ser un identificador UUID válido'
const opcion = opciones => valor => !opciones.includes(valor) && `debe ser uno de: ${opciones.join(', ')}`
function codigo(errores, valor) {
  revisar(errores, 'codigo', normalizarCodigo(valor), true, valor =>
    !esCodigoValido(valor) && 'debe tener entre 3 y 30 caracteres: letras, dígitos o guiones')
}
function entero(errores, campo, valor, min) {
  revisar(errores, campo, typeof valor === 'string' ? valor.trim() : valor, true, valor =>
    !esEnteroEnRango(valor, min, 1000000) && `debe ser un número entero entre ${min} y 1000000`)
}

export function validarDron(datos) {
  const errores = {}
  codigo(errores, datos.codigo)
  revisar(errores, 'modelo', texto(datos.modelo), true, maximo(100))
  revisar(errores, 'observaciones', texto(datos.observaciones), false, maximo(500))
  return errores
}

export function validarEdicionPaquete(datos) {
  const errores = {}
  revisar(errores, 'descripcion', texto(datos.descripcion), true, maximo(500))
  revisar(errores, 'direccionEntrega', texto(datos.direccionEntrega), true, maximo(200))
  revisar(errores, 'contratoId', datos.contratoId, false, uuid)
  return errores
}

export function validarPaquete(datos) {
  const errores = validarEdicionPaquete(datos)
  codigo(errores, datos.codigo)
  revisar(errores, 'clienteId', datos.clienteId, true, uuid)
  revisar(errores, 'observacion', texto(datos.observacion), false, maximo(500))
  return errores
}

export function validarTransicion(datos) {
  const errores = {}
  revisar(errores, 'estado', datos.estado, true, opcion(ESTADOS_PAQUETE))
  if (datos.estado === 'ASIGNADO' && !datos.dronId) errores.dronId = 'es obligatorio para asignar el paquete'
  else revisar(errores, 'dronId', datos.dronId, false, valor =>
    datos.estado !== 'ASIGNADO' ? 'solo se admite en la transición a ASIGNADO' : uuid(valor))
  revisar(errores, 'observacion', texto(datos.observacion), false, maximo(500))
  return errores
}

export function validarArticulo(datos) {
  const errores = {}
  codigo(errores, datos.codigo)
  revisar(errores, 'nombre', texto(datos.nombre), true, maximo(200))
  revisar(errores, 'categoria', datos.categoria, true, opcion(CATEGORIAS_ARTICULO))
  revisar(errores, 'unidad', datos.unidad, true, opcion(UNIDADES_ARTICULO))
  entero(errores, 'stockMinimo', datos.stockMinimo, 0)
  return errores
}

export function validarMovimientoInventario(datos) {
  const errores = {}
  revisar(errores, 'tipo', datos.tipo, true, opcion(TIPOS_MOVIMIENTO_INVENTARIO))
  entero(errores, 'cantidad', datos.cantidad, datos.tipo === 'AJUSTE' ? 0 : 1)
  revisar(errores, 'observacion', texto(datos.observacion), false, maximo(500))
  if (datos.tipo === 'AJUSTE' && !texto(datos.observacion)) errores.observacion = 'es obligatoria en un ajuste'
  return errores
}
