const cop = new Intl.NumberFormat('es-CO', {
  style: 'currency', currency: 'COP', minimumFractionDigits: 2,
})
const fecha = new Intl.DateTimeFormat('es-CO', { dateStyle: 'short', timeZone: 'UTC' })
const instante = new Intl.DateTimeFormat('es-CO', { dateStyle: 'short', timeStyle: 'short' })

export const formatoCop = valorTexto => cop.format(valorTexto)
// Una fecha contractual es un día del calendario, independiente del huso del navegador.
export const formatoFecha = fechaIso => fecha.format(new Date(fechaIso + 'T00:00:00Z'))
export const formatoInstante = instanteIso => instante.format(new Date(instanteIso))
