import { useCallback } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import Campo from '../../components/Campo.jsx'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import EstadoMovimientoEtiqueta from '../../components/EstadoMovimientoEtiqueta.jsx'
import Paginacion from '../../components/Paginacion.jsx'
import { formatoCop, formatoFecha } from '../../lib/formato.js'
import { validarRangoFechas } from '../../lib/validacionErp.js'
import { useConsulta } from '../../lib/useConsulta.js'
import { listarMovimientos } from '../../services/erpApi.js'

export default function MovimientosLista() {
  const [params, setParams] = useSearchParams()
  const desde = params.get('desde') ?? ''
  const hasta = params.get('hasta') ?? ''
  const errores = validarRangoFechas(desde, hasta)
  const invalido = Object.keys(errores).length > 0
  const consulta = useConsulta(useCallback(() => invalido ? Promise.resolve(null) : listarMovimientos(Object.fromEntries(params)), [params, invalido]))
  const pagina = consulta.datos
  function filtrar(evento) {
    evento.preventDefault()
    const siguientes = new URLSearchParams(params)
    siguientes.delete('page')
    for (const [campo, valor] of new FormData(evento.currentTarget)) {
      if (valor.trim()) siguientes.set(campo, valor.trim())
      else siguientes.delete(campo)
    }
    setParams(siguientes)
  }
  return <section className="crm-panel">
    <header className="crm-cabecera"><h1>Movimientos financieros</h1><Link className="button" to="/erp/movimientos/nuevo">Nuevo movimiento</Link></header>
    <form key={params.toString()} className="crm-filtros" onSubmit={filtrar} noValidate>
      <Campo etiqueta="Concepto o referencia"><input name="q" type="search" defaultValue={params.get('q') ?? ''} maxLength={100} /></Campo>
      <Campo etiqueta="Tipo"><select name="tipo" defaultValue={params.get('tipo') ?? ''}>
        <option value="">Todos</option><option>INGRESO</option><option>EGRESO</option>
      </select></Campo>
      <Campo etiqueta="Estado"><select name="estado" defaultValue={params.get('estado') ?? ''}>
        <option value="">Todos</option>{['PENDIENTE', 'PAGADO', 'ANULADO'].map(e => <option key={e}>{e}</option>)}
      </select></Campo>
      <Campo etiqueta="Desde" error={errores.desde}><input name="desde" type="date" defaultValue={desde} /></Campo>
      <Campo etiqueta="Hasta" error={errores.hasta}><input name="hasta" type="date" defaultValue={hasta} /></Campo>
      <button className="secondary">Buscar</button>
    </form>
    {!invalido && <EstadoConsulta {...consulta}>{pagina && <>
      {pagina.content.length === 0 ? <p>No hay movimientos para los criterios elegidos.</p> :
        <div className="overflow-x-auto"><table aria-label="Movimientos financieros"><thead><tr>
          {['Fecha', 'Tipo', 'Concepto', 'Valor', 'Estado', 'Cliente o proveedor'].map(t => <th key={t}>{t}</th>)}
        </tr></thead><tbody>{pagina.content.map(m => <tr key={m.id}>
          <td>{formatoFecha(m.fecha)}</td><td>{m.tipo}</td><td><Link to={'/erp/movimientos/' + m.id}>{m.concepto}</Link></td>
          <td>{formatoCop(m.valor)}</td><td><EstadoMovimientoEtiqueta estado={m.estado} /></td><td>{m.cliente?.nombre ?? m.proveedor?.nombre ?? '—'}</td>
        </tr>)}</tbody></table></div>}
      <Paginacion pagina={pagina} onCambiar={page => { const siguiente = new URLSearchParams(params); siguiente.set('page', page); setParams(siguiente) }} />
    </>}</EstadoConsulta>}
  </section>
}
