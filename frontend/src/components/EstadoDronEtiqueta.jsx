const clases = { DISPONIBLE: 'activo', EN_MISION: 'borrador', MANTENIMIENTO: 'finalizado', FUERA_DE_SERVICIO: 'inactivo' }
const etiquetas = { DISPONIBLE: 'Disponible', EN_MISION: 'En misión', MANTENIMIENTO: 'Mantenimiento', FUERA_DE_SERVICIO: 'Fuera de servicio' }

export default function EstadoDronEtiqueta({ estado }) {
  return <span className={'badge ' + (clases[estado] ?? '')}>{etiquetas[estado] ?? estado}</span>
}
