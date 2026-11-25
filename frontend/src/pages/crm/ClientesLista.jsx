import { useCallback } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import IndicadoresCrm from '../../components/IndicadoresCrm.jsx'
import Paginacion from '../../components/Paginacion.jsx'
import { usePerfil } from '../../context/PerfilContext.jsx'
import { puedeGestionarCrm } from '../../lib/permisos.js'
import { useConsulta } from '../../lib/useConsulta.js'
import { listarClientes } from '../../services/crmApi.js'

export default function ClientesLista() {
  const { rol } = usePerfil()
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const activo = params.get('activo') ?? ''
  const consulta = useConsulta(useCallback(() => listarClientes({ size: 5, ...Object.fromEntries(params) }), [params]))
  const pagina = consulta.datos

  function cambiar(clave, valor) {
    const siguientes = new URLSearchParams(params)
    if (clave !== 'page') siguientes.delete('page')
    if (valor) siguientes.set(clave, valor)
    else siguientes.delete(clave)
    setParams(siguientes)
  }

  return (
    <section className="crm-panel">
      <header><h1>Clientes</h1><p>Consulta los clientes y sus cuentas corporativas.</p></header>
      {puedeGestionarCrm(rol) && <IndicadoresCrm vista="clientes" />}
      <form className="crm-filtros" onSubmit={evento => {
        evento.preventDefault()
        cambiar('q', new FormData(evento.currentTarget).get('q').trim())
      }}>
        <label className="list-search"><span className="sr-only">Nombre o documento</span><input placeholder="Buscar por nombre o identificación" key={q} name="q" type="search" defaultValue={q} maxLength={100} /></label>
        <button className="secondary">Buscar</button>
        <label>Estado<select value={activo} onChange={evento => cambiar('activo', evento.target.value)}>
          <option value="">Todos</option><option value="true">Activos</option><option value="false">Inactivos</option>
        </select></label>
        {puedeGestionarCrm(rol) && <Link className="button push-right" to="nuevo"><span aria-hidden="true">+</span> Registrar cliente</Link>}
      </form>
      <EstadoConsulta {...consulta}>
        {pagina && <div className="clientes-resultados">
          {pagina.content.length === 0 ? <p>No hay clientes para los criterios elegidos.</p> :
            <div className="overflow-x-auto"><table aria-label="Clientes"><thead><tr>
              {['Cliente', 'Identificación', 'Cuenta', 'Estado', 'Acción'].map(t => <th key={t}>{t}</th>)}
            </tr></thead><tbody>{pagina.content.map(cliente => <tr key={cliente.id}>
              <td><Link to={cliente.id}>{cliente.nombre}</Link></td>
              <td>{cliente.tipoDocumento} {cliente.numeroDocumento}</td>
              <td>{cliente.cuentaCorporativa?.razonSocial || 'Sin cuenta'}</td><td><span className={cliente.activo ? 'badge activo' : 'badge inactivo'}>{cliente.activo ? 'Activo' : 'Inactivo'}</span></td>
              <td><Link to={cliente.id} aria-label={`Ver detalle de ${cliente.nombre}`}>Ver detalle <span aria-hidden="true">→</span></Link></td>
            </tr>)}</tbody></table></div>}
          <div className="list-footer">
            <p>Mostrando {pagina.content.length} de {pagina.totalElements ?? pagina.content.length} registros</p>
            <Paginacion pagina={pagina} onCambiar={page => cambiar('page', page)} />
          </div>
        </div>}
      </EstadoConsulta>
    </section>
  )
}
