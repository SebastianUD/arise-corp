import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import DialogoConfirmacion from '../../components/DialogoConfirmacion.jsx'
import Paginacion from '../../components/Paginacion.jsx'
import { formatoInstante } from '../../lib/formato.js'
import { useConsulta } from '../../lib/useConsulta.js'
import { cambiarEstadoCuenta, listarClientes, obtenerCuenta } from '../../services/crmApi.js'

function ClientesAsociados({ id }) {
  const [page, setPage] = useState(0)
  const consulta = useConsulta(useCallback(() => listarClientes({ cuentaCorporativaId: id, page }), [id, page]))
  const pagina = consulta.datos
  return <section className="grid gap-4">
    <h2>Clientes asociados</h2>
    <EstadoConsulta {...consulta}>{pagina && <>
      {pagina.content.length === 0 ? <p>No hay clientes en esta página.</p> :
        <div className="overflow-x-auto"><table aria-label="Clientes asociados">
          <thead><tr><th>Nombre</th><th>Documento</th><th>Estado</th></tr></thead>
          <tbody>{pagina.content.map(cliente => <tr key={cliente.id}>
            <td><Link to={'/crm/clientes/' + cliente.id}>{cliente.nombre}</Link></td>
            <td>{cliente.tipoDocumento} {cliente.numeroDocumento}</td><td>{cliente.activo ? 'Activo' : 'Inactivo'}</td>
          </tr>)}</tbody>
        </table></div>}
      <Paginacion pagina={pagina} onCambiar={setPage} />
    </>}</EstadoConsulta>
  </section>
}

export default function CuentaDetalle() {
  const { id } = useParams()
  const consulta = useConsulta(useCallback(() => obtenerCuenta(id), [id]))
  return <section className="crm-panel"><EstadoConsulta {...consulta}>
    {consulta.datos && <Detalle key={id} inicial={consulta.datos} />}
  </EstadoConsulta></section>
}

function Detalle({ inicial }) {
  const [cuenta, setCuenta] = useState(inicial)
  const [confirmando, setConfirmando] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)
  const accion = cuenta.activa ? 'Desactivar' : 'Activar'

  async function confirmar() {
    setConfirmando(false)
    setGuardando(true)
    setError(null)
    try {
      setCuenta(await cambiarEstadoCuenta(cuenta.id, !cuenta.activa))
    } catch (fallo) {
      setError(fallo)
    } finally {
      setGuardando(false)
    }
  }

  const filas = [
    ['NIT', cuenta.nit], ['Estado', cuenta.activa ? 'Activa' : 'Inactiva'],
    ['Nombre de contacto', cuenta.nombreContacto], ['Correo de contacto', cuenta.correoContacto],
    ['Teléfono de contacto', cuenta.telefonoContacto], ['Creada el', cuenta.creadoEn && formatoInstante(cuenta.creadoEn)],
    ['Actualizada el', cuenta.actualizadoEn && formatoInstante(cuenta.actualizadoEn)], ['Actualizada por', cuenta.actualizadoPor],
  ]
  return <>
    <header className="crm-cabecera"><h1>{cuenta.razonSocial}</h1><Link to="/crm/cuentas-corporativas">Volver a cuentas</Link></header>
    <dl className="grid gap-4 sm:grid-cols-2">{filas.map(([etiqueta, valor]) =>
      <div key={etiqueta}><dt className="text-sm text-slate-400">{etiqueta}</dt><dd>{valor || '—'}</dd></div>)}</dl>
    <div className="flex items-center gap-4">
      <Link to="editar">Editar</Link>
      <button type="button" className="button" disabled={guardando} onClick={() => setConfirmando(true)}>{accion}</button>
    </div>
    {error && <p role="alert" className="message">{error.message}</p>}
    {confirmando && <DialogoConfirmacion titulo={accion + ' cuenta'} texto={`¿Quieres ${accion.toLowerCase()} la cuenta ${cuenta.razonSocial}?`}
      onCancelar={() => setConfirmando(false)} onConfirmar={confirmar} />}
    <ClientesAsociados id={cuenta.id} />
  </>
}
