import { useCallback } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import EstadoContratoEtiqueta from '../../components/EstadoContratoEtiqueta.jsx'
import Paginacion from '../../components/Paginacion.jsx'
import { TRANSICIONES } from '../../lib/permisos.js'
import { formatoFecha } from '../../lib/formato.js'
import { useConsulta } from '../../lib/useConsulta.js'
import { listarContratos } from '../../services/crmApi.js'

export default function ContratosLista() {
  const [params, setParams] = useSearchParams()
  const estado = params.get('estado') || ''
  const page = params.get('page') || '0'
  const consulta = useConsulta(useCallback(() => listarContratos({ estado, page }), [estado, page]))
  const pagina = consulta.datos
  return <section className="crm-panel">
    <h1>Contratos</h1>
    <div className="crm-filtros">
      <label>Estado<select value={estado} onChange={evento => setParams(evento.target.value ? { estado: evento.target.value } : {})}>
        <option value="">Todos</option>
        {Object.keys(TRANSICIONES).map(valor => <option key={valor}>{valor}</option>)}
      </select></label>
    </div>
    <EstadoConsulta {...consulta}>{pagina && <>
      {pagina.content.length === 0 ? <p>No hay contratos para los criterios elegidos.</p> :
        <div className="overflow-x-auto"><table aria-label="Contratos">
          <thead><tr><th>Código</th><th>Cliente</th><th>Inicio</th><th>Estado</th></tr></thead>
          <tbody>{pagina.content.map(contrato => <tr key={contrato.id}>
            <td><Link to={contrato.id}>{contrato.codigo}</Link></td><td>{contrato.cliente?.nombre || '—'}</td>
            <td>{formatoFecha(contrato.fechaInicio)}</td><td><EstadoContratoEtiqueta estado={contrato.estado} /></td>
          </tr>)}</tbody>
        </table></div>}
      <Paginacion pagina={pagina} onCambiar={siguiente => setParams({ ...(estado && { estado }), page: String(siguiente) })} />
    </>}</EstadoConsulta>
  </section>
}
