import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Campo from '../../components/Campo.jsx'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import { useConsulta } from '../../lib/useConsulta.js'
import { normalizarDocumento, validarCliente } from '../../lib/validacionCrm.js'
import { actualizarCliente, crearCliente, listarCuentas, obtenerCliente } from '../../services/crmApi.js'

const camposTexto = [
  ['nombre', 'Nombre', true], ['numeroDocumento', 'Número de documento', true],
  ['correo', 'Correo electrónico', true, 'email'], ['telefono', 'Teléfono', true, 'tel'],
  ['direccion', 'Dirección'], ['ciudad', 'Ciudad'],
]

async function cuentasActivas() {
  const filtros = { activa: true, size: 100, sort: 'razonSocial,asc' }
  const primera = await listarCuentas(filtros)
  const cuentas = [...primera.content]
  for (let page = 1; page < primera.totalPages; page++) {
    const siguiente = await listarCuentas({ ...filtros, page })
    cuentas.push(...siguiente.content)
  }
  return cuentas
}

export default function ClienteFormulario() {
  const { id } = useParams()
  const consulta = useConsulta(useCallback(async () => {
    const [cliente, cuentas] = await Promise.all([id ? obtenerCliente(id) : null, cuentasActivas()])
    return { cliente, cuentas }
  }, [id]))
  return <section className="crm-panel">
    <h1>{id ? 'Editar cliente' : 'Nuevo cliente'}</h1>
    <EstadoConsulta {...consulta}>
      {consulta.datos && <Formulario key={id ?? 'nuevo'} {...consulta.datos} />}
    </EstadoConsulta>
  </section>
}

function Formulario({ cliente, cuentas }) {
  const navegar = useNavigate()
  const [datos, setDatos] = useState(() => ({
    ...Object.fromEntries(camposTexto.map(([campo]) => [campo, cliente?.[campo] ?? ''])),
    tipoDocumento: cliente?.tipoDocumento ?? 'CC', cuentaCorporativaId: cliente?.cuentaCorporativa?.id ?? '',
  }))
  const [errores, setErrores] = useState({})
  const [fallo, setFallo] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const montado = useRef(false)
  useEffect(() => {
    montado.current = true
    return () => { montado.current = false }
  }, [])
  const actual = cliente?.cuentaCorporativa
  const mostrarInactiva = actual && !cuentas.some(c => c.id === actual.id)

  function control(campo) {
    return { name: campo, value: datos[campo], onChange: evento => {
      setDatos({ ...datos, [campo]: evento.target.value })
    } }
  }

  async function guardar(evento) {
    evento.preventDefault()
    if (guardando) return
    const locales = validarCliente(datos)
    setErrores(locales)
    setFallo(null)
    if (Object.keys(locales).length) return
    setGuardando(true)
    const cuerpo = Object.fromEntries(Object.entries(datos).map(([campo, valor]) => [campo, valor.trim() || null]))
    cuerpo.numeroDocumento = normalizarDocumento(datos.numeroDocumento)
    try {
      const resultado = cliente ? await actualizarCliente(cliente.id, cuerpo) : await crearCliente(cuerpo)
      if (montado.current) navegar('/crm/clientes/' + resultado.id)
    } catch (error) {
      if (!montado.current) return
      const porCampo = Object.fromEntries((error.errors ?? []).map(({ field, message }) => [field, message]))
      if (error.campo) porCampo[error.campo] = error.message
      setErrores(porCampo)
      setFallo(error)
    } finally {
      if (montado.current) setGuardando(false)
    }
  }

  return <form noValidate onSubmit={guardar} aria-label="Datos del cliente">
    <fieldset disabled={guardando} className="grid gap-4 sm:grid-cols-2">
      <Campo etiqueta="Tipo de documento" obligatorio error={errores.tipoDocumento}>
        <select {...control('tipoDocumento')}>{['CC', 'CE', 'NIT', 'PASAPORTE'].map(t => <option key={t}>{t}</option>)}</select>
      </Campo>
      {camposTexto.map(([campo, etiqueta, obligatorio, tipo = 'text']) =>
        <Campo key={campo} etiqueta={etiqueta} obligatorio={obligatorio} error={errores[campo]}>
          <input type={tipo} {...control(campo)} />
        </Campo>)}
      <Campo etiqueta="Cuenta corporativa" error={errores.cuentaCorporativaId}>
        <select {...control('cuentaCorporativaId')}>
          <option value="">Sin cuenta</option>
          {cuentas.map(c => <option key={c.id} value={c.id}>{c.razonSocial}</option>)}
          {mostrarInactiva && <option value={actual.id}>{actual.razonSocial} (inactiva)</option>}
        </select>
      </Campo>
    </fieldset>
    {fallo && <p role="alert" className="message mt-4">{fallo.message}{' '}
      {fallo.conflicto === 'CLIENTE_INACTIVO_EXISTENTE' && fallo.problem?.clienteId &&
        <Link to={'/crm/clientes/' + fallo.problem.clienteId}>Ver cliente</Link>}
    </p>}
    <div className="mt-6 flex items-center gap-4">
      <button className="button" disabled={guardando}>{guardando ? 'Guardando…' : 'Guardar'}</button>
      <Link to={cliente ? '/crm/clientes/' + cliente.id : '/crm/clientes'}>Cancelar</Link>
    </div>
  </form>
}
