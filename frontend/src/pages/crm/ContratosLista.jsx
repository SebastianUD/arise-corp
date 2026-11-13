import { useCallback, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import EstadoContratoEtiqueta from '../../components/EstadoContratoEtiqueta.jsx'
import Paginacion from '../../components/Paginacion.jsx'
import IndicadoresCrm from '../../components/IndicadoresCrm.jsx'
import { usePerfil } from '../../context/PerfilContext.jsx'
import { FormularioContrato } from './ContratoFormulario.jsx'
import { puedeGestionarCrm, TRANSICIONES } from '../../lib/permisos.js'
import { formatoFecha } from '../../lib/formato.js'
import { useConsulta } from '../../lib/useConsulta.js'
import { listarClientes, listarContratos } from '../../services/crmApi.js'

function RegistroContrato() {
  const [pagina, setPagina] = useState(0)
  const [cliente, setCliente] = useState(null)
  const consulta = useConsulta(useCallback(() => listarClientes({ activo: true, page: pagina, sort: 'nombre,asc' }), [pagina]))
  return <section className="surface side-form"><h2>Registrar contrato</h2>
    <EstadoConsulta {...consulta}>{consulta.datos && <>
      <label>Cliente<select value={cliente?.id ?? ''} onChange={evento => setCliente(consulta.datos.content.find(c => c.id === evento.target.value))}>
        <option value="">Selecciona un cliente</option>
        {consulta.datos.content.map(c => <option value={c.id} key={c.id}>{c.nombre}</option>)}
      </select></label>
      {consulta.datos.totalPages > 1 && <Paginacion pagina={consulta.datos} onCambiar={page => { setCliente(null); setPagina(page) }} />}
    </>}</EstadoConsulta>
    {cliente && <FormularioContrato key={cliente.id} cliente={cliente} volver={'/crm/clientes/' + cliente.id} />}
  </section>
}

export default function ContratosLista() {
  const { rol } = usePerfil()
  const gestor = puedeGestionarCrm(rol)
  const [params, setParams] = useSearchParams()
  const estado = params.get('estado') || ''
  const page = params.get('page') || '0'
  const consulta = useConsulta(useCallback(() => listarContratos({ estado, page }), [estado, page]))
  const pagina = consulta.datos
  return <section className="crm-panel">
    <header><h1>Contratos</h1><p>Acuerdos comerciales vinculados a tus clientes.</p></header>
    {gestor && <IndicadoresCrm vista="contratos" />}
    <div className={gestor ? "split-view" : undefined}><div className="list-stack">
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
    </div>{gestor && <RegistroContrato />}</div>
  </section>
}
