import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Campo from '../../components/Campo.jsx'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import { useConsulta } from '../../lib/useConsulta.js'
import { normalizarCodigo, validarContrato } from '../../lib/validacionCrm.js'
import { actualizarContrato, crearContrato, obtenerCliente, obtenerContrato } from '../../services/crmApi.js'

export default function ContratoFormulario() {
  const { clienteId, id } = useParams()
  const consulta = useConsulta(useCallback(() => id ? obtenerContrato(id) : obtenerCliente(clienteId), [id, clienteId]))
  const recurso = consulta.datos
  const volver = id ? '/crm/contratos/' + id : '/crm/clientes/' + clienteId
  const bloqueo = id ? 'Solo se pueden editar contratos en borrador.' : 'Un cliente inactivo no admite contratos nuevos.'
  return <section className="crm-panel">
    <h1>{id ? 'Editar contrato' : 'Nuevo contrato'}</h1>
    <EstadoConsulta {...consulta}>{recurso && (
      (id ? recurso.estado === 'BORRADOR' : recurso.activo)
        ? <Formulario contrato={id ? recurso : null} cliente={id ? recurso.cliente : recurso} volver={volver} />
        : <><p role="alert">{bloqueo}</p><Link to={volver}>Volver al detalle</Link></>
    )}</EstadoConsulta>
  </section>
}

function Formulario({ contrato, cliente, volver }) {
  const navegar = useNavigate()
  const [datos, setDatos] = useState(() => ({
    codigo: contrato?.codigo ?? '', descripcion: contrato?.descripcion ?? '',
    fechaInicio: contrato?.fechaInicio ?? '', fechaFin: contrato?.fechaFin ?? '', valor: contrato?.valor ?? '',
  }))
  const [errores, setErrores] = useState({})
  const [mensaje, setMensaje] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const montado = useRef(false)
  useEffect(() => {
    montado.current = true
    return () => { montado.current = false }
  }, [])

  function control(campo) {
    return { name: campo, value: datos[campo], onChange: evento => setDatos({ ...datos, [campo]: evento.target.value }) }
  }

  async function guardar(evento) {
    evento.preventDefault()
    if (guardando) return
    const cuerpo = { ...datos, codigo: normalizarCodigo(datos.codigo), descripcion: datos.descripcion.trim(),
      fechaFin: datos.fechaFin || null }
    if (!contrato) cuerpo.clienteId = cliente.id
    const locales = validarContrato(cuerpo, { edicion: Boolean(contrato) })
    setErrores(locales)
    setMensaje(null)
    if (Object.keys(locales).length) return
    setGuardando(true)
    try {
      const resultado = contrato ? await actualizarContrato(contrato.id, cuerpo) : await crearContrato(cuerpo)
      if (montado.current) navegar('/crm/contratos/' + resultado.id)
    } catch (error) {
      if (!montado.current) return
      const campos = Object.fromEntries((error.errors ?? []).map(({ field, message }) => [field, message]))
      if (error.conflicto === 'CONTRATO_CODIGO_DUPLICADO') campos.codigo = error.message
      else if (error.campo) campos[error.campo] = error.message
      setErrores(campos)
      setMensaje(error.message)
    } finally {
      if (montado.current) setGuardando(false)
    }
  }

  return <form aria-label="Datos del contrato" noValidate onSubmit={guardar}>
    <p className="mb-4">Cliente: {cliente.nombre}</p>
    <fieldset disabled={guardando} className="grid gap-4 sm:grid-cols-2">
      <Campo etiqueta="Código" obligatorio error={errores.codigo}><input {...control('codigo')} /></Campo>
      <Campo etiqueta="Descripción" obligatorio error={errores.descripcion}>
        <textarea className="rounded-lg border border-slate-300 bg-white p-3" {...control('descripcion')} />
      </Campo>
      <Campo etiqueta="Fecha de inicio" obligatorio error={errores.fechaInicio}><input type="date" {...control('fechaInicio')} /></Campo>
      <Campo etiqueta="Fecha de fin" error={errores.fechaFin}><input type="date" {...control('fechaFin')} /></Campo>
      <Campo etiqueta="Valor en COP" obligatorio error={errores.valor}>
        <input type="text" inputMode="decimal" placeholder="1500000.50" {...control('valor')} />
      </Campo>
    </fieldset>
    {mensaje && <p className="message mt-4" role="alert">{mensaje}</p>}
    <div className="mt-6 flex items-center gap-4">
      <button className="button" disabled={guardando}>{guardando ? 'Guardando…' : 'Guardar'}</button>
      <Link to={volver}>Cancelar</Link>
    </div>
  </form>
}
