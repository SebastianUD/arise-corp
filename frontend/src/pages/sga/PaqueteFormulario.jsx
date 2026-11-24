import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Campo from '../../components/Campo.jsx'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import SelectorRegistro from '../../components/SelectorRegistro.jsx'
import { esPaqueteEditable } from '../../lib/permisos.js'
import { normalizarCodigo, texto } from '../../lib/validacionCrm.js'
import { validarPaquete, validarEdicionPaquete } from '../../lib/validacionSga.js'
import { useConsulta } from '../../lib/useConsulta.js'
import { listarClientes, listarContratos } from '../../services/crmApi.js'
import { actualizarPaquete, crearPaquete, obtenerPaquete } from '../../services/sgaApi.js'
import { erroresCampos } from './VistaListaSga.jsx'

const buscarClientes = q => listarClientes({ activo: true, q, size: 20, sort: 'nombre,asc' })
export default function PaqueteFormulario() {
  const { id } = useParams()
  const [aviso, setAviso] = useState(null)
  const consulta = useConsulta(useCallback(() => id ? obtenerPaquete(id) : Promise.resolve({}), [id]))
  const paquete = consulta.datos
  return <section className="crm-panel sga-panel">
    <header><p className="sga-eyebrow">SGA · Entregas</p><h1>{id ? 'Editar paquete' : 'Nuevo paquete'}</h1><p>Los campos con * son obligatorios. El contrato es opcional.</p></header>
    {aviso && <p role="alert" className="message">{aviso.message}</p>}
    <EstadoConsulta {...consulta}>{paquete && (id && !esPaqueteEditable(paquete.estado)
      ? <p className="surface">Solo se editan paquetes registrados o asignados. <Link to={'/sga/paquetes/' + id}>Ver paquete</Link></p>
      : <Formulario key={id ?? 'nuevo'} paquete={paquete} onConflicto={error => { setAviso(error); consulta.recargar() }} />)}</EstadoConsulta>
  </section>
}

function Formulario({ paquete, onConflicto }) {
  const navegar = useNavigate()
  const [cliente, setCliente] = useState(paquete.cliente ?? null)
  const [datos, setDatos] = useState(() => ({ codigo: paquete.codigo ?? '', descripcion: paquete.descripcion ?? '', direccionEntrega: paquete.direccionEntrega ?? '', clienteId: paquete.cliente?.id ?? null, contratoId: paquete.contrato?.id ?? '', observacion: '' }))
  const [errores, setErrores] = useState({})
  const [fallo, setFallo] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const envio = useRef(false)
  const montado = useRef(false)
  useEffect(() => { montado.current = true; return () => { montado.current = false } }, [])
  const clienteId = datos.clienteId
  const contratos = useConsulta(useCallback(() => clienteId ? listarContratos({ clienteId, estado: 'ACTIVO', size: 100, sort: 'codigo,asc' }) : Promise.resolve({ content: [] }), [clienteId]))
  const actuales = contratos.datos?.content ?? []
  const conservar = paquete.contrato && clienteId === paquete.cliente?.id && !actuales.some(c => c.id === paquete.contrato.id)
  const opciones = conservar ? [paquete.contrato, ...actuales] : actuales
  const contratoSeleccionado = opciones.find(c => c.id === datos.contratoId)
  const control = campo => ({ name: campo, value: datos[campo], onChange: e => setDatos({ ...datos, [campo]: e.target.value }) })
  async function guardar(evento) {
    evento.preventDefault()
    if (envio.current) return
    const locales = paquete.id ? validarEdicionPaquete(datos) : validarPaquete(datos)
    setErrores(locales); setFallo(null)
    if (Object.keys(locales).length) return
    envio.current = true; setGuardando(true)
    const edicion = { descripcion: texto(datos.descripcion), direccionEntrega: texto(datos.direccionEntrega), contratoId: datos.contratoId || null }
    try {
      const resultado = paquete.id ? await actualizarPaquete(paquete.id, edicion) : await crearPaquete({ ...edicion, codigo: normalizarCodigo(datos.codigo), clienteId: datos.clienteId, observacion: texto(datos.observacion) })
      if (montado.current) navegar('/sga/paquetes/' + resultado.id)
    } catch (error) {
      if (!montado.current) return
      setErrores(erroresCampos(error)); setFallo(error)
      if (error.status === 409 && !error.campo && paquete.id) onConflicto(error)
    } finally { envio.current = false; if (montado.current) setGuardando(false) }
  }
  return <form aria-label="Datos del paquete" noValidate onSubmit={guardar}>
    <fieldset disabled={guardando} className="grid gap-5 sm:grid-cols-2">
      <Campo etiqueta="Código" obligatorio error={errores.codigo}><input {...control('codigo')} readOnly={Boolean(paquete.id)} /></Campo>
      <Campo etiqueta="Descripción" obligatorio error={errores.descripcion}><textarea rows={3} {...control('descripcion')} /></Campo>
      <Campo etiqueta="Dirección de entrega" obligatorio error={errores.direccionEntrega}><input {...control('direccionEntrega')} /></Campo>
      {paquete.id ? <Campo etiqueta="Cliente"><input readOnly value={cliente?.nombre ?? ''} /></Campo>
        : <SelectorRegistro etiqueta="Cliente" textoVacio="Selecciona un cliente" buscar={buscarClientes} valor={cliente} deshabilitado={guardando} error={errores.clienteId}
          onCambiar={registro => { setCliente(registro); setDatos({ ...datos, clienteId: registro?.id ?? null, contratoId: '' }) }} />}
      <div><Campo etiqueta="Contrato" error={errores.contratoId}><select {...control('contratoId')} disabled={guardando || !clienteId || contratos.cargando || Boolean(contratos.error)}>
        <option value="">Sin contrato</option>{opciones.map(c => <option key={c.id} value={c.id}>{c.codigo}{c.estado !== 'ACTIVO' ? ` (${c.estado})` : ''}</option>)}
      </select></Campo><EstadoConsulta {...contratos} />
      {contratoSeleccionado && contratoSeleccionado.estado !== 'ACTIVO' && <p className="field-help">El contrato asociado ya no está activo; puedes conservarlo</p>}</div>
      {!paquete.id && <Campo etiqueta="Observación inicial" error={errores.observacion}><textarea rows={3} {...control('observacion')} /></Campo>}
    </fieldset>
    {fallo && <p role="alert" className="message mt-4">{fallo.message}</p>}
    <div className="sga-acciones"><button className="button" disabled={guardando}>{guardando ? 'Guardando…' : 'Guardar'}</button><Link to={paquete.id ? '/sga/paquetes/' + paquete.id : '/sga/paquetes'}>Cancelar</Link></div>
  </form>
}
