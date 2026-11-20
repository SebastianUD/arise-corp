import { texto, revisar, maximo, correo, telefono, fecha, normalizarDocumento, normalizarCodigo } from './validacionCrm.js'

export const normalizarReferencia = normalizarCodigo

export function fechaHoyColombia(ahora = new Date()) {
  const partes = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Bogota', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(ahora)
  const valores = Object.fromEntries(partes.map(({ type, value }) => [type, value]))
  return `${valores.year}-${valores.month}-${valores.day}`
}

function validarRegistro(datos, tipos, numericos) {
  const errores = {}
  revisar(errores, 'tipoDocumento', datos.tipoDocumento, true, tipo =>
    !tipos.includes(tipo) && `debe ser uno de: ${tipos.join(', ')}`)
  revisar(errores, 'numeroDocumento', normalizarDocumento(datos.numeroDocumento), true, numero => {
    if (!/^[A-Z0-9]{3,20}$/.test(numero)) return 'debe tener entre 3 y 20 letras o dígitos'
    if (numericos.includes(datos.tipoDocumento) && !/^\d+$/.test(numero)) {
      return `solo admite dígitos para el tipo ${numericos.join(' o ')}`
    }
  })
  revisar(errores, 'nombre', texto(datos.nombre), true, maximo(200))
  revisar(errores, 'correo', texto(datos.correo), true, correo)
  revisar(errores, 'telefono', texto(datos.telefono), true, telefono)
  return errores
}

export function validarEmpleado(datos, hoy = fechaHoyColombia()) {
  const errores = validarRegistro(datos, ['CC', 'CE', 'PASAPORTE'], ['CC'])
  for (const campo of ['cargo', 'area']) revisar(errores, campo, texto(datos[campo]), true, maximo(100))
  revisar(errores, 'fechaIngreso', datos.fechaIngreso, true, fecha)
  if (!errores.fechaIngreso && datos.fechaIngreso > hoy) errores.fechaIngreso = 'no puede ser posterior a la fecha actual'
  return errores
}

export function validarProveedor(datos) {
  const errores = validarRegistro(datos, ['NIT', 'CC', 'CE', 'PASAPORTE'], ['CC', 'NIT'])
  for (const [campo, limite] of [['nombreContacto', 200], ['direccion', 200], ['ciudad', 100]]) {
    revisar(errores, campo, texto(datos[campo]), false, maximo(limite))
  }
  return errores
}

export function validarMovimiento(datos) {
  const errores = {}
  revisar(errores, 'tipo', datos.tipo, true, tipo => !['INGRESO', 'EGRESO'].includes(tipo) && 'debe ser uno de: INGRESO, EGRESO')
  revisar(errores, 'fecha', datos.fecha, true, fecha)
  revisar(errores, 'concepto', texto(datos.concepto), true, concepto =>
    (concepto.length < 3 || concepto.length > 300) && 'debe tener entre 3 y 300 caracteres')
  revisar(errores, 'valor', datos.valor, true, valor =>
    (typeof valor !== 'string' || !/^\d{1,13}(\.\d{1,2})?$/.test(valor) || !/[1-9]/.test(valor))
      && 'debe ser mayor que 0, con hasta 13 dígitos enteros y 2 decimales')
  revisar(errores, 'referencia', normalizarReferencia(datos.referencia), false, referencia =>
    !/^[A-Z0-9 ./-]{1,50}$/.test(referencia)
      && 'debe tener como máximo 50 caracteres: letras A-Z, dígitos, espacios, puntos, barras o guiones')
  for (const [campo, tipo] of [['clienteId', 'INGRESO'], ['proveedorId', 'EGRESO']]) {
    revisar(errores, campo, datos[campo], false, id => {
      if (!/^[\da-f]{8}(-[\da-f]{4}){3}-[\da-f]{12}$/i.test(id)) return 'debe ser un identificador UUID válido'
      if (datos.tipo !== tipo) return `solo se admite en movimientos de tipo ${tipo}`
    })
  }
  return errores
}

export function validarRangoFechas(desde, hasta) {
  const errores = {}
  revisar(errores, 'desde', desde, false, fecha)
  revisar(errores, 'hasta', hasta, false, fecha)
  if (!errores.desde && !errores.hasta && desde && hasta && desde > hasta) {
    errores.hasta = 'debe ser igual o posterior a la fecha desde'
  }
  return errores
}

export function cuerpoMovimiento(datos) {
  return {
    tipo: datos.tipo, fecha: datos.fecha, concepto: texto(datos.concepto), valor: datos.valor,
    referencia: normalizarReferencia(datos.referencia),
    clienteId: datos.tipo === 'INGRESO' ? datos.clienteId || null : null,
    proveedorId: datos.tipo === 'EGRESO' ? datos.proveedorId || null : null,
  }
}
