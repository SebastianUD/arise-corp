import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import DialogoConfirmacion from '../../components/DialogoConfirmacion.jsx'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import EstadoContratoEtiqueta from '../../components/EstadoContratoEtiqueta.jsx'
import { usePerfil } from '../../context/PerfilContext.jsx'
import { formatoCop, formatoFecha, formatoInstante } from '../../lib/formato.js'
import { esEstadoFinal, puedeGestionarCrm, TRANSICIONES } from '../../lib/permisos.js'
import { useConsulta } from '../../lib/useConsulta.js'
import { cambiarEstadoContrato, obtenerContrato } from '../../services/crmApi.js'

const acciones = { ACTIVO: 'Activar', CANCELADO: 'Cancelar contrato', FINALIZADO: 'Finalizar' }

export default function ContratoDetalle() {
  const { id } = useParams()
  return <Detalle key={id} id={id} />
}

function Detalle({ id }) {
  const { rol } = usePerfil()
  const consulta = useConsulta(useCallback(() => obtenerContrato(id), [id]))
  const [actualizado, setActualizado] = useState(null)
  const [destinoFinal, setDestinoFinal] = useState(null)
  const [enCurso, setEnCurso] = useState(false)
  const [error, setError] = useState(null)
  const contrato = actualizado ?? consulta.datos

  async function cambiar(destino) {
    setDestinoFinal(null)
    setEnCurso(true)
    setError(null)
    try {
      setActualizado(await cambiarEstadoContrato(id, destino))
    } catch (fallo) {
      setError(fallo)
      if (fallo.status === 409) {
        setActualizado(null)
        consulta.recargar()
      }
    } finally {
      setEnCurso(false)
    }
  }

  return <section className="crm-panel">
    {error && <p role="alert" className="message">{error.message}</p>}
    <EstadoConsulta {...consulta}>{contrato && <>
      <header className="crm-cabecera"><h1>{contrato.codigo}</h1><EstadoContratoEtiqueta estado={contrato.estado} /></header>
      <dl className="grid gap-4 sm:grid-cols-2">
        <div><dt>Cliente</dt><dd><Link to={'/crm/clientes/' + contrato.cliente.id}>{contrato.cliente.nombre}</Link></dd></div>
        <div><dt>Cuenta corporativa</dt><dd>{contrato.cuentaCorporativa?.razonSocial ?? 'Sin cuenta'}</dd></div>
        <div><dt>Descripción</dt><dd>{contrato.descripcion}</dd></div>
        <div><dt>Valor en COP</dt><dd>{formatoCop(contrato.valor)}</dd></div>
        <div><dt>Fecha de inicio</dt><dd>{formatoFecha(contrato.fechaInicio)}</dd></div>
        <div><dt>Fecha de fin</dt><dd>{contrato.fechaFin ? formatoFecha(contrato.fechaFin) : '—'}</dd></div>
        <div><dt>Último cambio de estado</dt><dd>{formatoInstante(contrato.estadoActualizadoEn)}</dd></div>
        <div><dt>Estado actualizado por</dt><dd>{contrato.estadoActualizadoPor}</dd></div>
      </dl>
      {puedeGestionarCrm(rol) && <div className="flex flex-wrap items-center gap-4">
        {contrato.estado === 'BORRADOR' && !enCurso && <Link to="editar">Editar</Link>}
        {TRANSICIONES[contrato.estado]?.map(destino =>
          <button key={destino} type="button" className="button" disabled={enCurso}
            onClick={() => esEstadoFinal(destino) ? setDestinoFinal(destino) : cambiar(destino)}>{acciones[destino]}</button>)}
        {destinoFinal && <DialogoConfirmacion titulo={acciones[destinoFinal]} texto="Este cambio es irreversible."
          onCancelar={() => setDestinoFinal(null)} onConfirmar={() => cambiar(destinoFinal)} />}
      </div>}
    </>}</EstadoConsulta>
  </section>
}
