import { expect, it } from 'vitest'
import { mulberry32 } from '../test/generadores.js'
import { normalizarCodigo, normalizarDocumento, validarCliente, validarContrato, validarCuenta } from './validacionCrm.js'

function muestras(alfabeto, longitud, azar) {
  return Array.from({ length: longitud }, () => alfabeto[Math.floor(azar() * alfabeto.length)]).join('')
}

it('Property 1: normalizar dos veces da el mismo resultado', () => {
  const azar = mulberry32(2201)
  const casos = [null, '', '  ', '- .', ' Ñandú ß ']
  for (let i = 0; i < 150; i++) casos.push(muestras('a5ÉßK水.- \t', i % 35, azar))
  for (const entrada of casos) {
    for (const normalizar of [normalizarDocumento, normalizarCodigo]) {
      const resultado = normalizar(entrada)
      expect(normalizar(resultado), JSON.stringify(entrada)).toBe(resultado)
    }
    expect(normalizarCodigo(entrada)).toBe(entrada?.trim().toUpperCase() || null)
  }
  expect(normalizarDocumento(' - . ')).toBeNull()
})

it('Property 2: separadores y mayúsculas conservan la identidad del documento', () => {
  const azar = mulberry32(2202)
  for (let i = 0; i < 150; i++) {
    const base = muestras('0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ', 3 + i % 18, azar)
    const variante = [...base].map(c =>
      muestras(' .-', 2, azar) + (azar() < 0.5 ? c.toLowerCase() : c)).join('') + ' -'
    expect(normalizarDocumento(variante), variante).toBe(base)
  }
})

it('Property 3: formatos aceptados para documentos, contacto y contrato', () => {
  const azar = mulberry32(2203)
  const entero = limite => Math.floor(azar() * limite)
  for (let i = 0; i < 200; i++) {
    const tipoDocumento = ['CC', 'CE', 'NIT', 'PASAPORTE'][i % 4]
    const largo = entero(34)
    const dato = muestras(i % 3 === 0 ? '0123456789' : 'a1B2', largo, azar)
    const numerico = [...dato].every(c => '0123456789'.includes(c))
    const permiteLetras = ['CE', 'PASAPORTE'].includes(tipoDocumento)
    const telefono = muestras(i % 3 ? '012+()-' : '0a1', largo, azar)
    const telValido = largo >= 7 && largo <= 20 && !telefono.includes('a')
    const ciudad = 'A'.repeat(entero(205))
    const dominio = i % 3 ? '@correo.co' : '@correo'
    const correo = 'a@' + 'b.'.repeat(1 + entero(127)) + 'co'
    const correoContacto = 'contacto' + dominio
    const cliente = validarCliente({ tipoDocumento, numeroDocumento: dato, telefono, ciudad, nombre: ciudad, correo })
    expect(!cliente.numeroDocumento, tipoDocumento + ' ' + dato).toBe(largo >= 3 && largo <= 20 && (permiteLetras || numerico))
    expect(!cliente.telefono, telefono).toBe(telValido)
    expect(!cliente.ciudad, String(ciudad.length)).toBe(ciudad.length <= 100)
    expect(!cliente.nombre, String(ciudad.length)).toBe(ciudad.length > 0 && ciudad.length <= 200)
    expect(!cliente.correo, correo).toBe(correo.length <= 254)
    const cuenta = validarCuenta({ nit: dato, telefonoContacto: telefono, correoContacto })
    expect(!cuenta.nit, dato).toBe(numerico && largo >= 5 && largo <= 15)
    expect(!cuenta.telefonoContacto, telefono).toBe(!telefono || telValido)
    expect(!cuenta.correoContacto, correoContacto).toBe(dominio.endsWith('.co'))

    const codigo = i % 3 ? dato : dato + '_'
    const digitos = entero(16)
    const decimales = entero(4)
    const valor = '9'.repeat(digitos) + (decimales ? '.' + '0'.repeat(decimales) : '')
    const dia = 1 + entero(34)
    const fechaFin = '2028-02-' + String(dia).padStart(2, '0')
    const contrato = validarContrato({ codigo, valor, fechaInicio: '2028-02-10', fechaFin })
    expect(!contrato.codigo, codigo).toBe(i % 3 !== 0 && largo >= 3 && largo <= 30)
    expect(!contrato.valor, valor).toBe(digitos >= 1 && digitos <= 13 && decimales <= 2)
    expect(!contrato.fechaFin, fechaFin).toBe(dia >= 10 && dia <= 29)
  }
})

it('distingue los campos obligatorios y opcionales en formularios vacíos', () => {
  const obligatorios = campos => Object.fromEntries(campos.map(campo => [campo, 'es obligatorio']))
  expect(validarCliente({})).toEqual(obligatorios(['tipoDocumento', 'numeroDocumento', 'nombre', 'correo', 'telefono']))
  expect(validarCuenta({ nombreContacto: ' ' })).toEqual(obligatorios(['nit', 'razonSocial']))
  expect(validarContrato({})).toEqual(obligatorios(['clienteId', 'codigo', 'descripcion', 'fechaInicio', 'valor']))
})

it('normaliza campos antes de validar y conserva los datos del formulario', () => {
  const cliente = Object.freeze({
    tipoDocumento: 'CE', numeroDocumento: ' ab.1-23 ', nombre: ' A ',
    correo: ' persona@correo.co ', telefono: ' +57 (1) 123-4567 ',
  })
  expect(validarCliente(cliente)).toEqual({})
  expect(validarCuenta({ nit: ' 12.345-6 ', razonSocial: ' A ' })).toEqual({})
  expect(validarCliente({ ...cliente, tipoDocumento: 'CC' }).numeroDocumento).toBe('solo admite dígitos para el tipo CC o NIT')
  expect(validarCliente({ ...cliente, cuentaCorporativaId: 'x' }).cuentaCorporativaId).toBe('debe ser un identificador UUID válido')
})

it('valida los límites de textos y las fechas sin corregir días inexistentes', () => {
  expect(validarCuenta({ nit: '12345', razonSocial: 'a'.repeat(200), nombreContacto: 'a'.repeat(201) }))
    .toEqual({ nombreContacto: 'debe tener como máximo 200 caracteres' })
  const contrato = { codigo: ' ab-1 ', valor: '9999999999999.99', descripcion: 'a'.repeat(1000), fechaInicio: '2028-02-29' }
  expect(validarContrato(contrato, { edicion: true })).toEqual({})
  expect(validarContrato({ ...contrato, clienteId: '00000000-0000-0000-0000-000000000001' })).toEqual({})
  expect(validarContrato({ ...contrato, descripcion: 'a'.repeat(1001), fechaInicio: '2027-02-29' }, { edicion: true }))
    .toEqual({ descripcion: 'debe tener como máximo 1000 caracteres', fechaInicio: 'debe ser una fecha válida con formato AAAA-MM-DD' })
})

it.each(['-1', '+1', '1e3', '.50', '1.', '0,50', ' 12 ', 12])('rechaza el valor %j', valor => {
  expect(validarContrato({ valor }).valor).toBe('debe ser mayor o igual a 0, con hasta 13 dígitos enteros y 2 decimales')
})
