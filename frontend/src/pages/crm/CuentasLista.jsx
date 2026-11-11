import { useCallback } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import Paginacion from '../../components/Paginacion.jsx'
import { useConsulta } from '../../lib/useConsulta.js'
import { listarCuentas } from '../../services/crmApi.js'

export default function CuentasLista() {
  const [params, setParams] = useSearchParams()
  const consulta = useConsulta(useCallback(() => listarCuentas(Object.fromEntries(params)), [params]))
  const pagina = consulta.datos
  const q = params.get('q') ?? ''

  function cambiar(campo, valor) {
    const siguientes = new URLSearchParams(params)
    if (campo !== 'page') siguientes.delete('page')
    if (valor) siguientes.set(campo, valor)
    else siguientes.delete(campo)
    setParams(siguientes)
  }

  return <section className="crm-panel">
    <header className="crm-cabecera"><h1>Cuentas corporativas</h1><Link className="button" to="nueva">Nueva cuenta</Link></header>
    <form className="crm-filtros" onSubmit={evento => {
      evento.preventDefault()
      cambiar('q', new FormData(evento.currentTarget).get('q').trim())
    }}>
      <label>Razón social o NIT<input key={q} name="q" type="search" maxLength={100} defaultValue={q} /></label>
      <button className="button">Buscar</button>
      <label>Estado<select value={params.get('activa') ?? ''} onChange={evento => cambiar('activa', evento.target.value)}>
        <option value="">Todas</option><option value="true">Activas</option><option value="false">Inactivas</option>
      </select></label>
    </form>
    <EstadoConsulta {...consulta}>{pagina && <>
      {pagina.content.length === 0 ? <p>No hay cuentas para los criterios elegidos.</p> :
        <div className="overflow-x-auto"><table aria-label="Cuentas corporativas">
          <thead><tr><th>Razón social</th><th>NIT</th><th>Estado</th></tr></thead>
          <tbody>{pagina.content.map(cuenta => <tr key={cuenta.id}>
            <td><Link to={cuenta.id}>{cuenta.razonSocial}</Link></td><td>{cuenta.nit}</td><td>{cuenta.activa ? 'Activa' : 'Inactiva'}</td>
          </tr>)}</tbody>
        </table></div>}
      <Paginacion pagina={pagina} onCambiar={page => cambiar('page', page)} />
    </>}</EstadoConsulta>
  </section>
}
