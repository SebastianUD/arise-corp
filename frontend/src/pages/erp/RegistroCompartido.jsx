import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import Campo from '../../components/Campo.jsx'
import DialogoConfirmacion from '../../components/DialogoConfirmacion.jsx'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import EstadoMovimientoEtiqueta from '../../components/EstadoMovimientoEtiqueta.jsx'
import Paginacion from '../../components/Paginacion.jsx'
import { formatoCop, formatoFecha, formatoInstante } from '../../lib/formato.js'
import { normalizarDocumento, texto } from '../../lib/validacionCrm.js'
import { fechaHoyColombia, validarEmpleado, validarProveedor } from '../../lib/validacionErp.js'
import { useConsulta } from '../../lib/useConsulta.js'
import * as api from '../../services/erpApi.js'

const comunes = [['nombre', 'Nombre', true], ['numeroDocumento', 'Número de documento', true],
  ['correo', 'Correo electrónico', true, 'email'], ['telefono', 'Teléfono', true, 'tel']]

export const empleados = {
  plural: 'Empleados', singular: 'empleado', ruta: '/erp/empleados', tipos: ['CC', 'CE', 'PASAPORTE'],
  campos: [...comunes, ['cargo', 'Cargo', true], ['area', 'Área', true], ['fechaIngreso', 'Fecha de ingreso', true, 'date']],
  columnas: ['Nombre', 'Documento', 'Cargo', 'Área', 'Fecha de ingreso', 'Estado'],
  listar: api.listarEmpleados, obtener: api.obtenerEmpleado, crear: api.crearEmpleado,
  actualizar: api.actualizarEmpleado, cambiarEstado: api.cambiarEstadoEmpleado, validar: validarEmpleado,
  conflicto: 'EMPLEADO_INACTIVO_EXISTENTE', campoId: 'empleadoId',
}
export const proveedores = {
  plural: 'Proveedores', singular: 'proveedor', ruta: '/erp/proveedores', tipos: ['NIT', 'CC', 'CE', 'PASAPORTE'],
  campos: [...comunes, ['nombreContacto', 'Nombre de contacto'], ['direccion', 'Dirección'], ['ciudad', 'Ciudad']],
  columnas: ['Nombre', 'Documento', 'Contacto', 'Ciudad', 'Estado'],
  listar: api.listarProveedores, obtener: api.obtenerProveedor, crear: api.crearProveedor,
  actualizar: api.actualizarProveedor, cambiarEstado: api.cambiarEstadoProveedor, validar: validarProveedor,
  conflicto: 'PROVEEDOR_INACTIVO_EXISTENTE', campoId: 'proveedorId',
}

export function RegistroLista({ config }) {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const consulta = useConsulta(useCallback(() => config.listar(Object.fromEntries(params)), [config, params]))
  const pagina = consulta.datos
  function cambiar(clave, valor) {
    const siguientes = new URLSearchParams(params)
    if (clave !== 'page') siguientes.delete('page')
    if (valor !== '' && valor != null) siguientes.set(clave, valor)
    else siguientes.delete(clave)
    setParams(siguientes)
  }
  return <section className="crm-panel">
    <header><h1>{config.plural}</h1><p>Consulta y gestiona los {config.plural.toLowerCase()}.</p></header>
    <form className="crm-filtros" onSubmit={evento => {
      evento.preventDefault()
      cambiar('q', new FormData(evento.currentTarget).get('q').trim())
    }}>
      <label className="list-search">Nombre o documento<input key={q} name="q" type="search" defaultValue={q} maxLength={100} /></label>
      <button className="secondary">Buscar</button>
      <label>Estado<select value={params.get('activo') ?? ''} onChange={evento => cambiar('activo', evento.target.value)}>
        <option value="">Todos</option><option value="true">Activos</option><option value="false">Inactivos</option>
      </select></label>
      <Link className="button push-right" to={config.ruta + '/nuevo'}>Nuevo {config.singular}</Link>
    </form>
    <EstadoConsulta {...consulta}>{pagina && <>
      {pagina.content.length === 0 ? <p>No hay {config.plural.toLowerCase()} para los criterios elegidos.</p> :
        <div className="overflow-x-auto"><table aria-label={config.plural}>
          <thead><tr>{config.columnas.map(t => <th key={t}>{t}</th>)}</tr></thead>
          <tbody>{pagina.content.map(registro => <tr key={registro.id}>
            <td><Link to={config.ruta + '/' + registro.id}>{registro.nombre}</Link></td>
            <td>{registro.tipoDocumento} {registro.numeroDocumento}</td>
            {config === empleados ? <><td>{registro.cargo}</td><td>{registro.area}</td><td>{formatoFecha(registro.fechaIngreso)}</td></> :
              <><td>{registro.nombreContacto || '—'}</td><td>{registro.ciudad || '—'}</td></>}
            <td><span className={'badge ' + (registro.activo ? 'activo' : 'inactivo')}>{registro.activo ? 'Activo' : 'Inactivo'}</span></td>
          </tr>)}</tbody>
        </table></div>}
      <Paginacion pagina={pagina} onCambiar={page => cambiar('page', page)} />
    </>}</EstadoConsulta>
  </section>
}

export function RegistroFormulario({ config }) {
  const { id } = useParams()
  const consulta = useConsulta(useCallback(async () => id ? config.obtener(id) : {}, [config, id]))
  return <section className="crm-panel"><h1>{id ? 'Editar' : 'Nuevo'} {config.singular}</h1>
    <EstadoConsulta {...consulta}>{consulta.datos && <Formulario key={id ?? 'nuevo'} config={config} registro={consulta.datos} />}</EstadoConsulta>
  </section>
}

function Formulario({ config, registro }) {
  const navegar = useNavigate()
  const [datos, setDatos] = useState(() => ({
    ...Object.fromEntries(config.campos.map(([campo]) => [campo, registro[campo] ?? ''])),
    tipoDocumento: registro.tipoDocumento ?? config.tipos[0],
  }))
  const [errores, setErrores] = useState({})
  const [fallo, setFallo] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const montado = useRef(false)
  useEffect(() => { montado.current = true; return () => { montado.current = false } }, [])
  const control = campo => ({ name: campo, value: datos[campo], onChange: e => setDatos({ ...datos, [campo]: e.target.value }) })

  async function guardar(evento) {
    evento.preventDefault()
    if (guardando) return
    const locales = config.validar(datos)
    setErrores(locales)
    setFallo(null)
    if (Object.keys(locales).length) return
    setGuardando(true)
    const cuerpo = Object.fromEntries(Object.entries(datos).map(([campo, valor]) => [campo, texto(valor)]))
    cuerpo.numeroDocumento = normalizarDocumento(datos.numeroDocumento)
    try {
      const resultado = registro.id ? await config.actualizar(registro.id, cuerpo) : await config.crear(cuerpo)
      if (montado.current) navegar(config.ruta + '/' + resultado.id)
    } catch (error) {
      if (!montado.current) return
      const campos = Object.fromEntries((error.errors ?? []).map(({ field, message }) => [field, message]))
      if (error.campo) campos[error.campo] = error.message
      setErrores(campos)
      setFallo(error)
    } finally {
      if (montado.current) setGuardando(false)
    }
  }

  return <form noValidate aria-label={'Datos del ' + config.singular} onSubmit={guardar}>
    <p className="field-help">Los campos con * son obligatorios.</p>
    <fieldset disabled={guardando} className="grid gap-4 sm:grid-cols-2">
      <Campo etiqueta="Tipo de documento" obligatorio error={errores.tipoDocumento}>
        <select {...control('tipoDocumento')}>{config.tipos.map(tipo => <option key={tipo}>{tipo}</option>)}</select>
      </Campo>
      {config.campos.map(([campo, etiqueta, obligatorio, tipo = 'text']) =>
        <Campo key={campo} etiqueta={etiqueta} obligatorio={obligatorio} error={errores[campo]}>
          <input type={tipo} max={campo === 'fechaIngreso' ? fechaHoyColombia() : undefined} {...control(campo)} />
        </Campo>)}
    </fieldset>
    {fallo && <p role="alert" className="message mt-4">{fallo.message}{' '}
      {fallo.conflicto === config.conflicto && fallo.problem?.[config.campoId] &&
        <Link to={config.ruta + '/' + fallo.problem[config.campoId]}>Ver {config.singular}</Link>}
    </p>}
    <div className="mt-6 flex items-center gap-4">
      <button className="button" disabled={guardando}>{guardando ? 'Guardando…' : 'Guardar'}</button>
      <Link to={registro.id ? config.ruta + '/' + registro.id : config.ruta}>Cancelar</Link>
    </div>
  </form>
}

export function RegistroDetalle({ config }) {
  const { id } = useParams()
  const consulta = useConsulta(useCallback(() => config.obtener(id), [config, id]))
  return <section className="crm-panel"><EstadoConsulta {...consulta}>
    {consulta.datos && <Detalle key={id} config={config} inicial={consulta.datos} />}
  </EstadoConsulta></section>
}

function Detalle({ config, inicial }) {
  const [registro, setRegistro] = useState(inicial)
  const [confirmando, setConfirmando] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)
  const accion = registro.activo ? 'Desactivar' : 'Reactivar'
  async function confirmar() {
    setConfirmando(false)
    setGuardando(true)
    setError(null)
    try { setRegistro(await config.cambiarEstado(registro.id, !registro.activo)) }
    catch (fallo) { setError(fallo) }
    finally { setGuardando(false) }
  }
  const filas = [
    ['Tipo de documento', registro.tipoDocumento],
    ...config.campos.map(([campo, etiqueta]) => [etiqueta, campo === 'fechaIngreso' ? formatoFecha(registro[campo]) : registro[campo]]),
    ['Estado', registro.activo ? 'Activo' : 'Inactivo'],
    ['Creado el', registro.creadoEn && formatoInstante(registro.creadoEn)],
    ['Actualizado el', registro.actualizadoEn && formatoInstante(registro.actualizadoEn)], ['Actualizado por', registro.actualizadoPor],
  ]
  return <>
    <header className="crm-cabecera"><h1>{registro.nombre}</h1><Link to={config.ruta}>Volver a {config.plural.toLowerCase()}</Link></header>
    <dl className="grid gap-4 sm:grid-cols-2">{filas.map(([etiqueta, valor]) =>
      <div key={etiqueta}><dt className="text-sm text-slate-600">{etiqueta}</dt><dd>{valor || '—'}</dd></div>)}</dl>
    <div className="flex items-center gap-4"><Link to={config.ruta + '/' + registro.id + '/editar'}>Editar</Link>
      <button type="button" className="button" disabled={guardando} onClick={() => setConfirmando(true)}>{accion}</button></div>
    {error && <p role="alert" className="message">{error.message}</p>}
    {confirmando && <DialogoConfirmacion titulo={accion + ' ' + config.singular}
      texto={`¿Quieres ${accion.toLowerCase()} a ${registro.nombre}?`} onCancelar={() => setConfirmando(false)} onConfirmar={confirmar} />}
    {config === proveedores && <MovimientosProveedor key={registro.id} id={registro.id} />}
  </>
}

function MovimientosProveedor({ id }) {
  const [page, setPage] = useState(0)
  const consulta = useConsulta(useCallback(() => api.listarMovimientos({ proveedorId: id, page }), [id, page]))
  const pagina = consulta.datos
  return <section className="grid gap-4"><h2>Movimientos del proveedor</h2><EstadoConsulta {...consulta}>{pagina && <>
    {pagina.content.length === 0 ? <p>No hay movimientos para este proveedor.</p> :
      <div className="overflow-x-auto"><table aria-label="Movimientos del proveedor"><thead><tr>
        <th>Fecha</th><th>Concepto</th><th>Valor</th><th>Estado</th>
      </tr></thead><tbody>{pagina.content.map(m => <tr key={m.id}>
        <td>{formatoFecha(m.fecha)}</td><td><Link to={'/erp/movimientos/' + m.id}>{m.concepto}</Link></td>
        <td>{formatoCop(m.valor)}</td><td><EstadoMovimientoEtiqueta estado={m.estado} /></td>
      </tr>)}</tbody></table></div>}
    <Paginacion pagina={pagina} onCambiar={setPage} />
  </>}</EstadoConsulta></section>
}
