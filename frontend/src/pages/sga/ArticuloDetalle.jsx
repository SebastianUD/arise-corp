import { useCallback, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import Campo from '../../components/Campo.jsx'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import Paginacion from '../../components/Paginacion.jsx'
import { formatoInstante } from '../../lib/formato.js'
import { TIPOS_MOVIMIENTO_INVENTARIO, validarMovimientoInventario } from '../../lib/validacionSga.js'
import { useConsulta } from '../../lib/useConsulta.js'
import { listarMovimientosInventario, obtenerArticulo, registrarMovimientoInventario } from '../../services/sgaApi.js'
import { erroresCampos, etiquetaSga, numeroSga } from './VistaListaSga.jsx'

export default function ArticuloDetalle() {
  const { id } = useParams()
  const consulta = useConsulta(useCallback(() => obtenerArticulo(id), [id]))
  return <section className="crm-panel sga-panel"><EstadoConsulta {...consulta}>{consulta.datos && <Detalle key={id} inicial={consulta.datos} />}</EstadoConsulta></section>
}

function Detalle({ inicial }) {
  const [articulo, setArticulo] = useState(inicial)
  const [params, setParams] = useSearchParams()
  const [datos, setDatos] = useState({ tipo: 'ENTRADA', cantidad: '', observacion: '' })
  const [errores, setErrores] = useState({})
  const [fallo, setFallo] = useState(null)
  const [actualizacion, setActualizacion] = useState(null)
  const [resultado, setResultado] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const envio = useRef(false)
  const movimientos = useConsulta(useCallback(() => listarMovimientosInventario(inicial.id, { ...Object.fromEntries(params), size: 20, sort: 'registradoEn,desc' }), [inicial.id, params]))
  const pagina = movimientos.datos
  async function refrescarArticulo() {
    try { setArticulo(await obtenerArticulo(inicial.id)); setActualizacion(null) }
    catch (error) { setActualizacion(error) }
  }
  async function guardar(evento) {
    evento.preventDefault()
    if (envio.current) return
    const locales = validarMovimientoInventario(datos)
    setErrores(locales); setFallo(null); setResultado(null)
    if (Object.keys(locales).length) return
    envio.current = true; setGuardando(true)
    try {
      const movimiento = await registrarMovimientoInventario(articulo.id, datos)
      setResultado(movimiento.existenciaResultante)
      movimientos.recargar()
      await refrescarArticulo()
    } catch (error) {
      const campos = erroresCampos(error)
      if (error.status === 409 && error.conflicto === 'EXISTENCIA_INSUFICIENTE') campos.cantidad = error.message
      setErrores(campos); setFallo(error)
      if (error.status === 409) await refrescarArticulo()
    } finally { envio.current = false; setGuardando(false) }
  }
  return <>
    <header className="crm-cabecera"><div><p className="sga-eyebrow">SGA · Inventario</p><h1>{articulo.nombre}</h1><p>{articulo.codigo}</p></div><Link to="/sga/inventario">Volver a inventario</Link></header>
    <dl className="grid gap-5 sm:grid-cols-2">
      <div><dt>Existencia</dt><dd>{numeroSga(articulo.existencia)} {etiquetaSga(articulo.unidad)} {articulo.bajoStockMinimo && <span className="badge borrador">Bajo stock</span>}</dd></div>
      <div><dt>Stock mínimo</dt><dd>{numeroSga(articulo.stockMinimo)}</dd></div><div><dt>Categoría</dt><dd>{etiquetaSga(articulo.categoria)}</dd></div><div><dt>Unidad</dt><dd>{etiquetaSga(articulo.unidad)}</dd></div>
    </dl>
    {actualizacion && <EstadoConsulta error={actualizacion} recargar={refrescarArticulo} />}
    <div><Link className="secondary" to={'/sga/inventario/' + articulo.id + '/editar'}>Editar</Link></div>
    <section className="surface"><h2>Registrar movimiento</h2><p className="field-help">La existencia se actualiza a través de entradas, salidas y ajustes.</p>
      <form aria-label="Movimiento de inventario" noValidate onSubmit={guardar}>
        <fieldset disabled={guardando} className="grid gap-5 sm:grid-cols-2">
          <Campo etiqueta="Tipo" obligatorio error={errores.tipo}><select value={datos.tipo} onChange={e => { setDatos({ ...datos, tipo: e.target.value }); setErrores({}) }}>
            {TIPOS_MOVIMIENTO_INVENTARIO.map(tipo => <option key={tipo} value={tipo}>{etiquetaSga(tipo)}</option>)}
          </select></Campo>
          <Campo etiqueta="Cantidad" obligatorio error={errores.cantidad}><input inputMode="numeric" value={datos.cantidad} onChange={e => setDatos({ ...datos, cantidad: e.target.value })} /></Campo>
          <Campo etiqueta="Observación" obligatorio={datos.tipo === 'AJUSTE'} error={errores.observacion}><textarea rows={3} value={datos.observacion} onChange={e => setDatos({ ...datos, observacion: e.target.value })} /></Campo>
        </fieldset>
        {datos.tipo === 'AJUSTE' && <p className="field-help">La cantidad es la existencia contada y la observación es obligatoria.</p>}
        {fallo && <p role="alert" className="message mt-4">{fallo.message}</p>}
        {resultado !== null && <p role="status" className="surface mt-4">Existencia resultante: {numeroSga(resultado)}</p>}
        <div className="sga-acciones"><button className="button" disabled={guardando}>{guardando ? 'Guardando…' : 'Registrar movimiento'}</button></div>
      </form>
    </section>
    <section className="surface"><h2>Movimientos de inventario</h2>
      <EstadoConsulta {...movimientos}>{pagina && <>
        {pagina.content.length === 0 ? <p>No hay movimientos de inventario.</p> : <div className="overflow-x-auto"><table aria-label="Movimientos de inventario"><thead><tr>
          {['Fecha y hora', 'Tipo', 'Cantidad', 'Existencia anterior', 'Existencia resultante', 'Observación'].map(t => <th key={t} scope="col">{t}</th>)}
        </tr></thead><tbody>{pagina.content.map(m => <tr key={m.id}><td>{formatoInstante(m.registradoEn)}</td><td>{etiquetaSga(m.tipo)}</td><td>{numeroSga(m.cantidad)}</td><td>{numeroSga(m.existenciaAnterior)}</td><td>{numeroSga(m.existenciaResultante)}</td><td>{m.observacion ?? '—'}</td></tr>)}</tbody></table></div>}
        <Paginacion pagina={pagina} onCambiar={page => { const siguientes = new URLSearchParams(params); siguientes.set('page', page); setParams(siguientes) }} />
      </>}</EstadoConsulta>
    </section>
  </>
}
