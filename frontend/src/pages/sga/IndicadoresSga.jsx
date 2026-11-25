import { Link } from 'react-router-dom'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import { ESTADOS_DRON, ESTADOS_PAQUETE } from '../../lib/validacionSga.js'
import { useConsulta } from '../../lib/useConsulta.js'
import { obtenerResumenSga } from '../../services/sgaApi.js'
import { etiquetaSga, numeroSga } from './VistaListaSga.jsx'

function ResumenEstados({ titulo, resumen, estados }) {
  return <section className="surface" aria-label={titulo}><header className="crm-cabecera"><h2>{titulo}</h2><p>Total: <strong>{numeroSga(resumen.total)}</strong></p></header>
    <dl className="metrics mt-5">{estados.map(estado => <div key={estado}><dt>{etiquetaSga(estado)}</dt><dd>{numeroSga(resumen.porEstado[estado] ?? 0)}</dd></div>)}</dl>
  </section>
}
export default function IndicadoresSga() {
  const consulta = useConsulta(obtenerResumenSga)
  const resumen = consulta.datos
  return <section className="crm-panel sga-panel">
    <header><p className="sga-eyebrow">SGA · Visión general</p><h1>Indicadores SGA</h1><p>Una vista de las entregas, la disponibilidad de la flota y las necesidades de inventario.</p></header>
    <EstadoConsulta {...consulta}>{resumen && <>
      <ResumenEstados titulo="Paquetes" resumen={resumen.paquetes} estados={ESTADOS_PAQUETE} />
      <ResumenEstados titulo="Drones" resumen={resumen.drones} estados={ESTADOS_DRON} />
      <section className="surface" aria-label="Inventario"><h2>Inventario</h2><dl className="metrics"><div><dt>Artículos bajo stock mínimo</dt><dd>{numeroSga(resumen.articulos.bajoStockMinimo)}</dd></div><div><dt>Total de artículos</dt><dd>{numeroSga(resumen.articulos.total)}</dd></div></dl>
        <div className="sga-acciones"><Link to="/sga/inventario?bajoStockMinimo=true">Ver artículos bajo stock mínimo</Link></div>
      </section>
    </>}</EstadoConsulta>
  </section>
}
