const clases = { REGISTRADO: 'borrador', ASIGNADO: 'borrador', EN_TRANSITO: 'activo', ENTREGADO: 'activo', CANCELADO: 'inactivo', DEVUELTO: 'inactivo' }
const etiquetas = { REGISTRADO: 'Registrado', ASIGNADO: 'Asignado', EN_TRANSITO: 'En tránsito', ENTREGADO: 'Entregado', CANCELADO: 'Cancelado', DEVUELTO: 'Devuelto' }

export default function EstadoPaqueteEtiqueta({ estado }) {
  return <span className={'badge ' + (clases[estado] ?? '')}>{etiquetas[estado] ?? estado}</span>
}
