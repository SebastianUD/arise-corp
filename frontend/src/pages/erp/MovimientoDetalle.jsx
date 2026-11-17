import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import DialogoConfirmacion from '../../components/DialogoConfirmacion.jsx'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import EstadoMovimientoEtiqueta from '../../components/EstadoMovimientoEtiqueta.jsx'
import { formatoCop, formatoFecha, formatoInstante } from '../../lib/formato.js'
import { esMovimientoEditable, TRANSICIONES_MOVIMIENTO } from '../../lib/permisos.js'
import { useConsulta } from '../../lib/useConsulta.js'
import { cambiarEstadoMovimiento, obtenerMovimiento } from '../../services/erpApi.js'

const acciones = { PAGADO: 'Marcar como pagado', ANULADO: 'Anular' }

export default function MovimientoDetalle() {
  const { id } = useParams()
  const [aviso, setAviso] = useState(null)
  const consulta = useConsulta(useCallback(() => obtenerMovimiento(id), [id]))
  return <section className="crm-panel">
    {aviso && <p role="alert" className="message">{aviso.message}</p>}
    <EstadoConsulta {...consulta}>{consulta.datos && <Detalle key={id} inicial={consulta.datos}
      onConflicto={error => { setAviso(error); consulta.recargar() }} />}</EstadoConsulta>
  </section>
}

function Detalle({ inicial, onConflicto }) {
  const [movimiento, setMovimiento] = useState(inicial)
  const [destino, setDestino] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)
  async function confirmar() {
    setDestino(null)
    setGuardando(true)
    setError(null)
    try { setMovimiento(await cambiarEstadoMovimiento(movimiento.id, destino)) }
    catch (fallo) {
      if (fallo.status === 409) onConflicto(fallo)
      else setError(fallo)
    } finally { setGuardando(false) }
  }
  const filas = [
    ['Tipo', movimiento.tipo], ['Fecha', formatoFecha(movimiento.fecha)], ['Concepto', movimiento.concepto],
    ['Valor', formatoCop(movimiento.valor)], ['Referencia', movimiento.referencia],
    ['Estado', <EstadoMovimientoEtiqueta estado={movimiento.estado} />],
    ['Creado el', movimiento.creadoEn && formatoInstante(movimiento.creadoEn)],
    ['Actualizado el', movimiento.actualizadoEn && formatoInstante(movimiento.actualizadoEn)], ['Actualizado por', movimiento.actualizadoPor],
    ['Último cambio de estado', movimiento.estadoActualizadoEn && formatoInstante(movimiento.estadoActualizadoEn)], ['Estado actualizado por', movimiento.estadoActualizadoPor],
  ]
  return <>
    <header className="crm-cabecera"><h1>Detalle del movimiento</h1><Link to="/erp/movimientos">Volver a movimientos</Link></header>
    <dl className="grid gap-4 sm:grid-cols-2">{filas.map(([etiqueta, valor]) =>
      <div key={etiqueta}><dt className="text-sm text-slate-600">{etiqueta}</dt><dd>{valor || '—'}</dd></div>)}
      {movimiento.cliente && <div><dt>Cliente</dt><dd><Link to={'/crm/clientes/' + movimiento.cliente.id}>{movimiento.cliente.nombre}</Link></dd></div>}
      {movimiento.proveedor && <div><dt>Proveedor</dt><dd><Link to={'/erp/proveedores/' + movimiento.proveedor.id}>{movimiento.proveedor.nombre}</Link></dd></div>}
    </dl>
    <div className="flex flex-wrap items-center gap-4">
      {esMovimientoEditable(movimiento.estado) && <Link to={'/erp/movimientos/' + movimiento.id + '/editar'}>Editar</Link>}
      {(TRANSICIONES_MOVIMIENTO[movimiento.estado] ?? []).map(estado => <button key={estado} type="button" className="button" disabled={guardando}
        onClick={() => setDestino(estado)}>{acciones[estado]}</button>)}
    </div>
    {error && <p role="alert" className="message">{error.message}</p>}
    {destino && <DialogoConfirmacion titulo={acciones[destino]} texto="Este cambio es irreversible."
      onCancelar={() => setDestino(null)} onConfirmar={confirmar} />}
  </>
}
