const clases = { PENDIENTE: 'borrador', PAGADO: 'activo', ANULADO: 'inactivo' }

export default function EstadoMovimientoEtiqueta({ estado }) {
  return <span className={'badge ' + (clases[estado] ?? '')}>{estado}</span>
}
