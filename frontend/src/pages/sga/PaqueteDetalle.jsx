import { useCallback, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Campo from '../../components/Campo.jsx'
import DialogoConfirmacion from '../../components/DialogoConfirmacion.jsx'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import EstadoPaqueteEtiqueta from '../../components/EstadoPaqueteEtiqueta.jsx'
import { formatoInstante } from '../../lib/formato.js'
import { esPaqueteEditable, esPaqueteFinal, TRANSICIONES_PAQUETE } from '../../lib/permisos.js'
import { validarTransicion } from '../../lib/validacionSga.js'
import { useConsulta } from '../../lib/useConsulta.js'
import { cambiarEstadoPaquete, listarDrones, obtenerHistorialPaquete, obtenerPaquete } from '../../services/sgaApi.js'
import { erroresCampos } from './VistaListaSga.jsx'

export const accionesPaquete = { ASIGNADO: 'Asignar dron', EN_TRANSITO: 'Marcar en tránsito', ENTREGADO: 'Marcar como entregado', CANCELADO: 'Cancelar paquete', DEVUELTO: 'Marcar como devuelto' }
export default function PaqueteDetalle() {
  const { id } = useParams()
  const [aviso, setAviso] = useState(null)
  const consulta = useConsulta(useCallback(async () => {
    const [paquete, historial] = await Promise.all([obtenerPaquete(id), obtenerHistorialPaquete(id)])
    return { paquete, historial }
  }, [id]))
  return <section className="crm-panel sga-panel">
    {aviso && <p role="alert" className="message">{aviso.message}</p>}
    <EstadoConsulta {...consulta}>{consulta.datos && <Detalle key={id} {...consulta.datos} onRecargar={error => { setAviso(error ?? null); consulta.recargar() }} />}</EstadoConsulta>
  </section>
}

function Detalle({ paquete, historial, onRecargar }) {
  return <>
    <header className="crm-cabecera"><div><p className="sga-eyebrow">SGA · Trazabilidad</p><h1>{paquete.codigo}</h1><p><EstadoPaqueteEtiqueta estado={paquete.estado} /></p></div><Link to="/sga/paquetes">Volver a paquetes</Link></header>
    <dl className="grid gap-5 sm:grid-cols-2">
      <div><dt>Descripción</dt><dd>{paquete.descripcion}</dd></div><div><dt>Dirección de entrega</dt><dd>{paquete.direccionEntrega}</dd></div>
      <div><dt>Cliente</dt><dd><Link to={'/crm/clientes/' + paquete.cliente.id}>{paquete.cliente.nombre}</Link></dd></div>
      <div><dt>Contrato</dt><dd>{paquete.contrato ? <Link to={'/crm/contratos/' + paquete.contrato.id}>{paquete.contrato.codigo}</Link> : 'Sin contrato'}</dd></div>
      <div><dt>Dron</dt><dd>{paquete.dron ? <Link to={'/sga/drones/' + paquete.dron.id}>{paquete.dron.codigo}</Link> : 'Sin asignar'}</dd></div>
      <div><dt>Último cambio de estado</dt><dd>{paquete.estadoActualizadoEn ? formatoInstante(paquete.estadoActualizadoEn) : '—'}</dd></div>
      <div><dt>Estado actualizado por</dt><dd>{paquete.estadoActualizadoPor ?? '—'}</dd></div>
    </dl>
    {esPaqueteEditable(paquete.estado) && <div><Link className="secondary" to={'/sga/paquetes/' + paquete.id + '/editar'}>Editar</Link></div>}
    {!esPaqueteFinal(paquete.estado) && <Transicion paquete={paquete} onRecargar={onRecargar} />}
    <section className="surface"><h2>Historial de estados</h2><p className="field-help">Cada cambio conserva su fecha, responsable y observación.</p>
      <div className="overflow-x-auto"><table aria-label="Historial de estados"><thead><tr>{['Estado anterior', 'Estado nuevo', 'Dron', 'Observación', 'Fecha y hora', 'Actor'].map(t => <th key={t} scope="col">{t}</th>)}</tr></thead>
        <tbody>{historial.map(registro => <tr key={registro.id}>
          <td>{registro.estadoAnterior ? <EstadoPaqueteEtiqueta estado={registro.estadoAnterior} /> : '—'}</td><td><EstadoPaqueteEtiqueta estado={registro.estadoNuevo} /></td>
          <td>{registro.dron ? <Link to={'/sga/drones/' + registro.dron.id}>{registro.dron.codigo}</Link> : '—'}</td><td>{registro.observacion ?? '—'}</td>
          <td>{formatoInstante(registro.registradoEn)}</td><td>{registro.registradoPor}</td>
        </tr>)}</tbody></table></div>
    </section>
  </>
}

function Transicion({ paquete, onRecargar }) {
  const [datos, setDatos] = useState({ estado: '', dronId: '', observacion: '' })
  const [errores, setErrores] = useState({})
  const [fallo, setFallo] = useState(null)
  const [confirmacion, setConfirmacion] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const envio = useRef(false)
  const asignar = datos.estado === 'ASIGNADO'
  const drones = useConsulta(useCallback(() => asignar ? listarDrones({ asignable: true, size: 100, sort: 'codigo,asc' }) : Promise.resolve({ content: [] }), [asignar]))
  function enviar(evento) {
    evento.preventDefault()
    const locales = validarTransicion(datos)
    setErrores(locales); setFallo(null)
    if (Object.keys(locales).length || guardando) return
    if (esPaqueteFinal(datos.estado)) setConfirmacion(true)
    else void guardar()
  }
  async function guardar() {
    if (envio.current) return
    envio.current = true; setConfirmacion(false); setGuardando(true)
    try { await cambiarEstadoPaquete(paquete.id, datos); onRecargar() }
    catch (error) {
      if (error.status === 409) onRecargar(error)
      else { setErrores(erroresCampos(error)); setFallo(error) }
    } finally { envio.current = false; setGuardando(false) }
  }
  return <section className="surface"><h2>Cambiar estado</h2>
    <form aria-label="Cambiar estado del paquete" noValidate onSubmit={enviar}>
      <fieldset disabled={guardando} className="grid gap-5 sm:grid-cols-2">
        <Campo etiqueta="Nuevo estado" obligatorio error={errores.estado}><select value={datos.estado} onChange={e => { setDatos({ ...datos, estado: e.target.value, dronId: '' }); setErrores({}) }}>
          <option value="">Selecciona un estado</option>{(TRANSICIONES_PAQUETE[paquete.estado] ?? []).map(estado => <option key={estado} value={estado}>{accionesPaquete[estado]}</option>)}
        </select></Campo>
        {asignar && <div><Campo etiqueta="Dron asignable" obligatorio error={errores.dronId}><select value={datos.dronId} disabled={guardando || drones.cargando || Boolean(drones.error)} onChange={e => setDatos({ ...datos, dronId: e.target.value })}>
          <option value="">Selecciona un dron</option>{(drones.datos?.content ?? []).map(dron => <option key={dron.id} value={dron.id}>{dron.codigo} · {dron.modelo}</option>)}
        </select></Campo><EstadoConsulta {...drones} />{drones.datos?.content.length === 0 && <p className="field-help">No hay drones asignables disponibles.</p>}</div>}
        <Campo etiqueta="Observación" error={errores.observacion}><textarea rows={3} value={datos.observacion} onChange={e => setDatos({ ...datos, observacion: e.target.value })} /></Campo>
      </fieldset>
      {fallo && <p role="alert" className="message mt-4">{fallo.message}</p>}
      <div className="sga-acciones"><button className="button" disabled={guardando}>{guardando ? 'Guardando…' : 'Actualizar estado'}</button></div>
    </form>
    {confirmacion && <DialogoConfirmacion titulo={accionesPaquete[datos.estado]} texto="Este cambio es irreversible." onCancelar={() => setConfirmacion(false)} onConfirmar={guardar} />}
  </section>
}
