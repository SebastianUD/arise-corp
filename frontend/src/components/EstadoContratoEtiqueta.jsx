const estilos = {
  BORRADOR: 'borrador', ACTIVO: 'activo', FINALIZADO: 'finalizado', CANCELADO: 'inactivo',
}

export default function EstadoContratoEtiqueta({ estado }) {
  return <span className={`badge ${estilos[estado] ?? ''}`}>{estado}</span>
}
