import { useCallback } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import Campo from '../../components/Campo.jsx'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import Paginacion from '../../components/Paginacion.jsx'
import { useConsulta } from '../../lib/useConsulta.js'

export const etiquetaSga = valor => ({ EN_TRANSITO: 'En tránsito', EN_MISION: 'En misión', FUERA_DE_SERVICIO: 'Fuera de servicio', BATERIA: 'Batería' })[valor]
  ?? (valor ? valor[0] + valor.slice(1).toLowerCase().replaceAll('_', ' ') : '—')
export const numeroSga = valor => Number(valor ?? 0).toLocaleString('es-CO')
export const erroresCampos = error => ({
  ...Object.fromEntries((error.errors ?? []).map(({ field, message }) => [field, message])),
  ...(error.campo ? { [error.campo]: error.message } : {}),
})

export default function VistaListaSga({ titulo, descripcion, ruta, nuevo, consultar, filtro, columnas, vacio, inventario = false, etiquetaBusqueda = 'Buscar por código o descripción' }) {
  const [params, setParams] = useSearchParams()
  const consulta = useConsulta(useCallback(() => consultar(Object.fromEntries(params)), [consultar, params]))
  const pagina = consulta.datos
  function filtrar(evento) {
    evento.preventDefault()
    const siguientes = new URLSearchParams(params)
    siguientes.delete('page')
    if (inventario) siguientes.delete('bajoStockMinimo')
    for (const [campo, valor] of new FormData(evento.currentTarget)) {
      if (valor.trim()) siguientes.set(campo, valor.trim())
      else siguientes.delete(campo)
    }
    setParams(siguientes)
  }
  return <section className="crm-panel sga-panel">
    <header className="crm-cabecera"><div><p className="sga-eyebrow">SGA · Operaciones</p><h1>{titulo}</h1><p>{descripcion}</p></div>
      <Link className="button" to={ruta + '/nuevo'}>{nuevo}</Link></header>
    <form className="crm-filtros" key={params.toString()} onSubmit={filtrar}>
      <Campo etiqueta={etiquetaBusqueda}><input type="search" name="q" maxLength={100} defaultValue={params.get('q') ?? ''} placeholder="Escribe para buscar" /></Campo>
      <Campo etiqueta={filtro.etiqueta}><select name={filtro.campo} defaultValue={params.get(filtro.campo) ?? ''}>
        <option value="">Todos</option>{filtro.valores.map(valor => <option key={valor} value={valor}>{etiquetaSga(valor)}</option>)}
      </select></Campo>
      {inventario && <label className="sga-checkbox"><input type="checkbox" name="bajoStockMinimo" value="true" defaultChecked={params.get('bajoStockMinimo') === 'true'} />Solo bajo stock mínimo</label>}
      <button className="secondary">Buscar</button>
    </form>
    <EstadoConsulta {...consulta}>{pagina && <>
      {pagina.content.length === 0 ? <div className="surface sga-vacio"><h2>Sin resultados</h2><p>{vacio}</p></div> : <div className="overflow-x-auto"><table aria-label={titulo}>
        <thead><tr>{columnas.map(({ titulo }) => <th scope="col" key={titulo}>{titulo}</th>)}</tr></thead>
        <tbody>{pagina.content.map(registro => <tr key={registro.id}>{columnas.map(({ titulo, valor }) => <td key={titulo}>{valor(registro)}</td>)}</tr>)}</tbody>
      </table></div>}
      <Paginacion pagina={pagina} onCambiar={page => { const siguiente = new URLSearchParams(params); siguiente.set('page', page); setParams(siguiente) }} />
    </>}</EstadoConsulta>
  </section>
}
