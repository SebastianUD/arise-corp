import { expect, it } from 'vitest'
import { formatoCop, formatoFecha, formatoInstante } from './formato.js'

it.each([
  ['0.00', '$0,00'],
  ['1500000.5', '$1.500.000,50'],
  ['9999999999999.99', '$9.999.999.999.999,99'],
])('muestra %s en pesos colombianos con dos decimales', (valor, esperado) => {
  expect(formatoCop(valor).replace(/\s/g, '')).toBe(esperado)
})

it('conserva el día contractual incluso al oeste de UTC', () => {
  const opciones = { timeZone: 'America/Bogota', dateStyle: 'short' }
  const mismoDia = new Date('2028-02-29T12:00:00Z')
  expect(formatoFecha('2028-02-29')).toBe(mismoDia.toLocaleDateString('es-CO', opciones))
})

it('muestra la fecha y la hora locales del mismo instante con distintos offsets', () => {
  const fecha = '2026-11-11T02:30:00Z'
  expect(formatoInstante(fecha)).toBe(new Date(fecha).toLocaleString('es-CO', {
    dateStyle: 'short', timeStyle: 'short',
  }))
  expect(formatoInstante('2026-11-10T21:30:00-05:00')).toBe(formatoInstante(fecha))
})
