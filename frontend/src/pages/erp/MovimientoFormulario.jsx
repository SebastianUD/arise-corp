import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Campo from '../../components/Campo.jsx'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import SelectorRegistro from '../../components/SelectorRegistro.jsx'
import { esMovimientoEditable } from '../../lib/permisos.js'
import { cuerpoMovimiento, fechaHoyColombia, validarMovimiento } from '../../lib/validacionErp.js'
import { useConsulta } from '../../lib/useConsulta.js'
import { listarClientes } from '../../services/crmApi.js'
import { actualizarMovimiento, crearMovimiento, listarProveedores, obtenerMovimiento } from '../../services/erpApi.js'

const buscarClientes = q => listarClientes({ activo: true, q, size: 20, sort: 'nombre,asc' })
const buscarProveedores = q => listarProveedores({ activo: true, q, size: 20, sort: 'nombre,asc' })

export default function MovimientoFormulario() {
  const { id } = useParams()
  const [aviso, setAviso] = useState(null)
  const consulta = useConsulta(useCallback(async () => id ? obtenerMovimiento(id) : {}, [id]))
  const movimiento = consulta.datos
  return <section className="crm-panel"><h1>{id ? 'Editar movimiento' : 'Nuevo movimiento'}</h1>
    {aviso && <p role="alert" className="message">{aviso.message}</p>}
    <EstadoConsulta {...consulta}>{movimiento && (
      id && !esMovimientoEditable(movimiento.estado)
        ? <p>Solo se editan movimientos pendientes. <Link to={'/erp/movimientos/' + id}>Ver movimiento</Link></p>
        : <Formulario key={id ?? 'nuevo'} movimiento={movimiento} onConflicto={error => { setAviso(error); consulta.recargar() }} />
    )}</EstadoConsulta>
  </section>
}

function Formulario({ movimiento, onConflicto }) {
  const navegar = useNavigate()
  const [datos, setDatos] = useState(() => ({
    tipo: movimiento.tipo ?? 'INGRESO', fecha: movimiento.fecha ?? fechaHoyColombia(), concepto: movimiento.concepto ?? '',
    valor: movimiento.valor ?? '', referencia: movimiento.referencia ?? '',
    clienteId: movimiento.cliente?.id ?? null, proveedorId: movimiento.proveedor?.id ?? null,
  }))
  const [cliente, setCliente] = useState(movimiento.cliente ?? null)
  const [proveedor, setProveedor] = useState(movimiento.proveedor ?? null)
  const [errores, setErrores] = useState({})
  const [fallo, setFallo] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const montado = useRef(false)
  useEffect(() => { montado.current = true; return () => { montado.current = false } }, [])
  const control = campo => ({ name: campo, value: datos[campo], onChange: e => setDatos({ ...datos, [campo]: e.target.value }) })
  const esIngreso = datos.tipo === 'INGRESO'
  const asociado = esIngreso ? cliente : proveedor

  async function guardar(evento) {
    evento.preventDefault()
    if (guardando) return
    const locales = validarMovimiento(datos)
    setErrores(locales)
    setFallo(null)
    if (Object.keys(locales).length) return
    setGuardando(true)
    try {
      const cuerpo = cuerpoMovimiento(datos)
      const resultado = movimiento.id ? await actualizarMovimiento(movimiento.id, cuerpo) : await crearMovimiento(cuerpo)
      if (montado.current) navegar('/erp/movimientos/' + resultado.id)
    } catch (error) {
      if (!montado.current) return
      const campos = Object.fromEntries((error.errors ?? []).map(({ field, message }) => [field, message]))
      if (error.campo) campos[error.campo] = error.message
      setErrores(campos)
      setFallo(error)
      if (error.status === 409 && !error.campo && movimiento.id) onConflicto(error)
    } finally {
      if (montado.current) setGuardando(false)
    }
  }

  return <form aria-label="Datos del movimiento" noValidate onSubmit={guardar}>
    <p className="field-help">Los campos con * son obligatorios.</p>
    <fieldset disabled={guardando} className="grid gap-4 sm:grid-cols-2">
      <Campo etiqueta="Tipo" obligatorio error={errores.tipo}>
        <select name="tipo" value={datos.tipo} onChange={evento => {
          setDatos({ ...datos, tipo: evento.target.value, clienteId: null, proveedorId: null })
          setCliente(null); setProveedor(null)
          setErrores(({ clienteId, proveedorId, ...resto }) => resto)
        }}><option>INGRESO</option><option>EGRESO</option></select>
      </Campo>
      <Campo etiqueta="Fecha" obligatorio error={errores.fecha}><input type="date" {...control('fecha')} /></Campo>
      <Campo etiqueta="Concepto" obligatorio error={errores.concepto}><input {...control('concepto')} /></Campo>
      <Campo etiqueta="Valor en COP" obligatorio error={errores.valor}><input type="text" inputMode="decimal" placeholder="2500000.50" {...control('valor')} /></Campo>
      <Campo etiqueta="Referencia" error={errores.referencia}><input {...control('referencia')} /></Campo>
      <SelectorRegistro key={datos.tipo} etiqueta={esIngreso ? 'Cliente' : 'Proveedor'} buscar={esIngreso ? buscarClientes : buscarProveedores}
        valor={asociado} deshabilitado={guardando} error={errores[esIngreso ? 'clienteId' : 'proveedorId']} onCambiar={registro => {
          if (esIngreso) setCliente(registro)
          else setProveedor(registro)
          setDatos({ ...datos, [esIngreso ? 'clienteId' : 'proveedorId']: registro?.id ?? null })
        }} />
      {asociado?.activo === false && <p className="field-help">El registro asociado está inactivo; puedes conservarlo.</p>}
    </fieldset>
    {fallo && <p role="alert" className="message mt-4">{fallo.message}</p>}
    <div className="mt-6 flex items-center gap-4">
      <button className="button" disabled={guardando}>{guardando ? 'Guardando…' : 'Guardar'}</button>
      <Link to={movimiento.id ? '/erp/movimientos/' + movimiento.id : '/erp/movimientos'}>Cancelar</Link>
    </div>
  </form>
}
