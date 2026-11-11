const estilos = {
  BORRADOR: 'bg-slate-700 text-slate-100',
  ACTIVO: 'bg-emerald-950 text-emerald-200',
  FINALIZADO: 'bg-blue-950 text-blue-200',
  CANCELADO: 'bg-rose-950 text-rose-200',
}

export default function EstadoContratoEtiqueta({ estado }) {
  return <span className={`rounded px-2 py-1 text-sm font-semibold ${estilos[estado] ?? ''}`}>{estado}</span>
}
