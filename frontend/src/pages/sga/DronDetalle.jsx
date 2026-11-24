import { useCallback, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import DialogoConfirmacion from '../../components/DialogoConfirmacion.jsx'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import EstadoDronEtiqueta from '../../components/EstadoDronEtiqueta.jsx'
import { formatoInstante } from '../../lib/formato.js'
import { transicionesManualesDron } from '../../lib/permisos.js'
import { useConsulta } from '../../lib/useConsulta.js'
import { cambiarEstadoDron, obtenerDron } from '../../services/sgaApi.js'

const acciones = { DISPONIBLE: 'Marcar como disponible', MANTENIMIENTO: 'Enviar a mantenimiento', FUERA_DE_SERVICIO: 'Poner fuera de servicio' }
export default function DronDetalle() {
  const { id } = useParams()
  const [aviso, setAviso] = useState(null)
  const consulta = useConsulta(useCallback(() => obtenerDron(id), [id]))
  return <section className="crm-panel sga-panel">
    {aviso && <p role="alert" className="message">{aviso.message}</p>}
    <EstadoConsulta {...consulta}>{consulta.datos && <Detalle key={id} inicial={consulta.datos} onConflicto={error => { setAviso(error); consulta.recargar() }} />}</EstadoConsulta>
  </section>
}

function Detalle({ inicial, onConflicto }) {
  const [dron, setDron] = useState(inicial)
  const [destino, setDestino] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)
  const envio = useRef(false)
  async function confirmar() {
    if (envio.current) return
    envio.current = true; setDestino(null); setGuardando(true); setError(null)
    try { setDron(await cambiarEstadoDron(dron.id, destino)) }
    catch (fallo) { if (fallo.status === 409) onConflicto(fallo); else setError(fallo) }
    finally { envio.current = false; setGuardando(false) }
  }
  const filas = [
    ['Modelo', dron.modelo], ['Observaciones', dron.observaciones],
    ['Último cambio de estado', dron.estadoActualizadoEn && formatoInstante(dron.estadoActualizadoEn)], ['Estado actualizado por', dron.estadoActualizadoPor],
    ['Creado el', dron.creadoEn && formatoInstante(dron.creadoEn)], ['Actualizado el', dron.actualizadoEn && formatoInstante(dron.actualizadoEn)], ['Actualizado por', dron.actualizadoPor],
  ]
  return <>
    <header className="crm-cabecera"><div><p className="sga-eyebrow">SGA · Flota</p><h1>{dron.codigo}</h1><p><EstadoDronEtiqueta estado={dron.estado} /></p></div><Link to="/sga/drones">Volver a drones</Link></header>
    <dl className="grid gap-5 sm:grid-cols-2">{filas.map(([etiqueta, valor]) => <div key={etiqueta}><dt>{etiqueta}</dt><dd>{valor || '—'}</dd></div>)}</dl>
    {dron.paqueteEnCurso ? <section className="surface"><h2>Entrega en curso</h2><p>El dron tiene el paquete <Link to={'/sga/paquetes/' + dron.paqueteEnCurso.id}>{dron.paqueteEnCurso.codigo}</Link> en curso.</p><p className="field-help">Su disponibilidad se actualiza con el estado del paquete.</p></section>
      : dron.estado === 'EN_MISION' && <p className="surface">El dron está en misión. Su estado se gestiona desde el paquete en curso.</p>}
    <div className="sga-acciones"><Link className="secondary" to={'/sga/drones/' + dron.id + '/editar'}>Editar</Link>
      {transicionesManualesDron(dron).map(estado => <button key={estado} type="button" className="button" disabled={guardando} onClick={() => setDestino(estado)}>{acciones[estado]}</button>)}
    </div>
    {error && <p role="alert" className="message">{error.message}</p>}
    {destino && <DialogoConfirmacion titulo={acciones[destino]} texto={'Se actualizará el estado del dron ' + dron.codigo + '.'} onCancelar={() => setDestino(null)} onConfirmar={confirmar} />}
  </>
}
