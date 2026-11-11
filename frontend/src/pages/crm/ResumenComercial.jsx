import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import { useConsulta } from '../../lib/useConsulta.js'
import { obtenerResumen } from '../../services/crmApi.js'

function Conteos({ titulo, filas }) {
  return <section aria-label={titulo} className="grid gap-3">
    <h2>{titulo}</h2>
    <dl className="grid gap-3 sm:grid-cols-3">{filas.map(([etiqueta, valor]) =>
      <div key={etiqueta} className="rounded-xl border border-slate-700 bg-slate-950 p-4">
        <dt className="text-sm text-slate-300">{etiqueta}</dt><dd className="mt-1 text-2xl font-semibold tabular-nums">{valor}</dd>
      </div>)}</dl>
  </section>
}

export default function ResumenComercial() {
  const consulta = useConsulta(obtenerResumen)
  const datos = consulta.datos
  return <section className="crm-panel">
    <h1>Resumen comercial</h1>
    <EstadoConsulta {...consulta}>{datos && <>
      <Conteos titulo="Clientes" filas={[
        ['Activos', datos.clientes.activos], ['Inactivos', datos.clientes.inactivos], ['Total', datos.clientes.total],
      ]} />
      <Conteos titulo="Contratos" filas={[
        ['Borrador', datos.contratos.porEstado.BORRADOR], ['Activo', datos.contratos.porEstado.ACTIVO],
        ['Finalizado', datos.contratos.porEstado.FINALIZADO], ['Cancelado', datos.contratos.porEstado.CANCELADO],
        ['Total', datos.contratos.total],
      ]} />
    </>}</EstadoConsulta>
  </section>
}
