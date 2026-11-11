const texto = valor => valor?.trim() || null
export const normalizarDocumento = valor => texto(valor)?.replace(/[\s.-]/gu, '').toUpperCase() || null
export const normalizarCodigo = valor => texto(valor)?.toUpperCase() || null

const tiposDocumento = ['CC', 'CE', 'NIT', 'PASAPORTE']
const maximo = limite => valor => valor.length > limite && `debe tener como máximo ${limite} caracteres`
const uuid = valor => !/^[\da-f]{8}(-[\da-f]{4}){3}-[\da-f]{12}$/i.test(valor)
  && 'debe ser un identificador UUID válido'
const correo = valor => (valor.length > 254 || !/^[^\s@.]+(?:\.[^\s@.]+)*@[^\s@.]+(?:\.[^\s@.]+)+$/u.test(valor))
  && 'debe ser un correo válido de máximo 254 caracteres'
const telefono = valor => !/^[\d +()-]{7,20}$/.test(valor)
  && 'debe tener entre 7 y 20 caracteres: dígitos, espacios, +, - o paréntesis'

function fecha(valor) {
  if (/^\d{4}-\d{2}-\d{2}$/.test(valor)) {
    const dia = new Date(valor + 'T00:00:00Z')
    if (Number.isFinite(dia.getTime()) && dia.toISOString().startsWith(valor + 'T')) return
  }
  return 'debe ser una fecha válida con formato AAAA-MM-DD'
}

function revisar(errores, campo, valor, obligatorio, regla) {
  const mensaje = valor == null || valor === ''
    ? obligatorio && 'es obligatorio'
    : regla(valor)
  if (mensaje) errores[campo] = mensaje
}

export function validarCliente(datos) {
  const errores = {}
  revisar(errores, 'tipoDocumento', datos.tipoDocumento, true, tipo =>
    !tiposDocumento.includes(tipo) && 'debe ser uno de: CC, CE, NIT, PASAPORTE')
  revisar(errores, 'numeroDocumento', normalizarDocumento(datos.numeroDocumento), true, numero => {
    if (!/^[A-Z0-9]{3,20}$/.test(numero)) return 'debe tener entre 3 y 20 letras o dígitos'
    if (['CC', 'NIT'].includes(datos.tipoDocumento) && !/^\d+$/.test(numero)) {
      return 'solo admite dígitos para el tipo CC o NIT'
    }
  })
  revisar(errores, 'nombre', texto(datos.nombre), true, maximo(200))
  revisar(errores, 'correo', texto(datos.correo), true, correo)
  revisar(errores, 'telefono', texto(datos.telefono), true, telefono)
  revisar(errores, 'direccion', texto(datos.direccion), false, maximo(200))
  revisar(errores, 'ciudad', texto(datos.ciudad), false, maximo(100))
  revisar(errores, 'cuentaCorporativaId', datos.cuentaCorporativaId, false, uuid)
  return errores
}

export function validarCuenta(datos) {
  const errores = {}
  revisar(errores, 'nit', normalizarDocumento(datos.nit), true, nit =>
    !/^\d{5,15}$/.test(nit) && 'debe tener entre 5 y 15 dígitos')
  revisar(errores, 'razonSocial', texto(datos.razonSocial), true, maximo(200))
  revisar(errores, 'nombreContacto', texto(datos.nombreContacto), false, maximo(200))
  revisar(errores, 'correoContacto', texto(datos.correoContacto), false, correo)
  revisar(errores, 'telefonoContacto', texto(datos.telefonoContacto), false, telefono)
  return errores
}

export function validarContrato(datos, { edicion = false } = {}) {
  const errores = {}
  if (!edicion) revisar(errores, 'clienteId', datos.clienteId, true, uuid)
  revisar(errores, 'codigo', normalizarCodigo(datos.codigo), true, codigo =>
    !/^[A-Z0-9-]{3,30}$/.test(codigo) && 'debe tener entre 3 y 30 caracteres: letras, dígitos o guiones')
  revisar(errores, 'descripcion', texto(datos.descripcion), true, maximo(1000))
  revisar(errores, 'valor', datos.valor, true, valor =>
    (typeof valor !== 'string' || !/^\d{1,13}(\.\d{1,2})?$/.test(valor))
      && 'debe ser mayor o igual a 0, con hasta 13 dígitos enteros y 2 decimales')
  revisar(errores, 'fechaInicio', datos.fechaInicio, true, fecha)
  revisar(errores, 'fechaFin', datos.fechaFin, false, fecha)
  if (!errores.fechaInicio && !errores.fechaFin && datos.fechaFin && datos.fechaFin < datos.fechaInicio) {
    errores.fechaFin = 'debe ser igual o posterior a la fecha de inicio'
  }
  return errores
}
