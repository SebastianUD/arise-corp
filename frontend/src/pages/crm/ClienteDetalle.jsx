import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import DialogoConfirmacion from '../../components/DialogoConfirmacion.jsx'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import EstadoContratoEtiqueta from '../../components/EstadoContratoEtiqueta.jsx'
import Paginacion from '../../components/Paginacion.jsx'
import { usePerfil } from '../../context/PerfilContext.jsx'
import { formatoCop, formatoFecha, formatoInstante } from '../../lib/formato.js'
import { puedeGestionarCrm, puedeLeerContratos } from '../../lib/permisos.js'
import { useConsulta } from '../../lib/useConsulta.js'
import { cambiarEstadoCliente, listarContratos, obtenerCliente } from '../../services/crmApi.js'

function Contratos({ clienteId }) {
  const [page, setPage] = useState(0)
  const consulta = useConsulta(useCallback(() => listarContratos({ clienteId, page }), [clienteId, page]))
  const pagina = consulta.datos
  return <section className="grid gap-4">
    <h2>Contratos</h2>
    <EstadoConsulta {...consulta}>{pagina && <>
      {pagina.content.length === 0 ? <p>Este cliente no tiene contratos en esta página.</p> :
        <div className="overflow-x-auto"><table aria-label="Contratos del cliente"><thead><tr>
          {['Código', 'Estado', 'Fecha de inicio', 'Fecha de fin', 'Valor'].map(t => <th key={t}>{t}</th>)}
        </tr></thead><tbody>{pagina.content.map(c => <tr key={c.id}>
          <td><Link to={'/crm/contratos/' + c.id}>{c.codigo}</Link></td><td><EstadoContratoEtiqueta estado={c.estado} /></td>
          <td>{formatoFecha(c.fechaInicio)}</td><td>{c.fechaFin ? formatoFecha(c.fechaFin) : '—'}</td><td>{formatoCop(c.valor)}</td>
        </tr>)}</tbody></table></div>}
      <Paginacion pagina={pagina} onCambiar={setPage} />
    </>}</EstadoConsulta>
  </section>
}

function Detalle({ inicial, rol }) {
  const [cliente, setCliente] = useState(inicial)
  const [confirmando, setConfirmando] = useState(false)
  const [enCurso, setEnCurso] = useState(false)
  const [error, setError] = useState(null)
  const gestor = puedeGestionarCrm(rol)
  const accion = cliente.activo ? 'Desactivar' : 'Reactivar'
  async function confirmar() {
    setConfirmando(false)
    setEnCurso(true)
    setError(null)
    try {
      setCliente(await cambiarEstadoCliente(cliente.id, !cliente.activo))
    } catch (fallo) {
      setError(fallo)
    } finally {
      setEnCurso(false)
    }
  }

  const filas = [
    ['Documento', cliente.tipoDocumento + ' ' + cliente.numeroDocumento],
    ['Ciudad', cliente.ciudad], ['Cuenta corporativa', cliente.cuentaCorporativa?.razonSocial ?? 'Sin cuenta'],
    ['Estado', cliente.activo ? 'Activo' : 'Inactivo'],
  ]
  if (gestor) {
    for (const [campo, titulo] of [['correo', 'Correo electrónico'], ['telefono', 'Teléfono'], ['direccion', 'Dirección'],
      ['creadoEn', 'Creado el'], ['actualizadoEn', 'Actualizado el'], ['actualizadoPor', 'Actualizado por']]) {
      if (campo in cliente) {
        const valor = cliente[campo]
        filas.push([titulo, valor && campo.endsWith('En') ? formatoInstante(valor) : valor])
      }
    }
  }
  return <>
    <header className="crm-cabecera"><h1>{cliente.nombre}</h1><Link to="/crm/clientes">Volver a clientes</Link></header>
    <dl className="grid gap-4 sm:grid-cols-2">{filas.map(([titulo, valor]) =>
      <div key={titulo}><dt className="text-sm text-slate-400">{titulo}</dt><dd>{valor || '—'}</dd></div>)}</dl>
    {gestor && <>
      <div className="flex flex-wrap items-center gap-4">
        <Link to="editar">Editar</Link>
        <button type="button" className="button" disabled={enCurso} onClick={() => setConfirmando(true)}>{accion}</button>
        {cliente.activo && <Link to="contratos/nuevo">Nuevo contrato</Link>}
      </div>
      {error && <p role="alert" className="message">{error.message}</p>}
      {confirmando && <DialogoConfirmacion titulo={accion + ' cliente'}
        texto={`¿Quieres ${accion.toLowerCase()} a ${cliente.nombre}?`} onConfirmar={confirmar} onCancelar={() => setConfirmando(false)} />}
    </>}
    {!cliente.activo && <p>Un cliente inactivo no admite contratos nuevos.</p>}
    {puedeLeerContratos(rol) && <Contratos clienteId={cliente.id} />}
  </>
}

export default function ClienteDetalle() {
  const { id } = useParams()
  const { rol } = usePerfil()
  const consulta = useConsulta(useCallback(() => obtenerCliente(id), [id]))
  return <section className="crm-panel"><EstadoConsulta {...consulta}>
    {consulta.datos && <Detalle key={id} inicial={consulta.datos} rol={rol} />}
  </EstadoConsulta></section>
}
