import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Campo from '../../components/Campo.jsx'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import { useConsulta } from '../../lib/useConsulta.js'
import { normalizarDocumento, validarCuenta } from '../../lib/validacionCrm.js'
import { actualizarCuenta, crearCuenta, obtenerCuenta } from '../../services/crmApi.js'

const campos = [
  ['razonSocial', 'Razón social', true], ['nit', 'NIT', true],
  ['nombreContacto', 'Nombre de contacto'], ['correoContacto', 'Correo de contacto', false, 'email'],
  ['telefonoContacto', 'Teléfono de contacto', false, 'tel'],
]
const ruta = '/crm/cuentas-corporativas'

export default function CuentaFormulario() {
  const { id } = useParams()
  return <section className="crm-panel">
    <h1>{id ? 'Editar cuenta' : 'Nueva cuenta'}</h1>
    {id ? <Edicion key={id} id={id} /> : <FormularioCuenta />}
  </section>
}

function Edicion({ id }) {
  const consulta = useConsulta(useCallback(() => obtenerCuenta(id), [id]))
  return <EstadoConsulta {...consulta}>{consulta.datos && <FormularioCuenta cuenta={consulta.datos} />}</EstadoConsulta>
}

export function FormularioCuenta({ cuenta, integrado = false }) {
  const navegar = useNavigate()
  const [errores, setErrores] = useState({})
  const [mensaje, setMensaje] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const montado = useRef(false)
  useEffect(() => {
    montado.current = true
    return () => { montado.current = false }
  }, [])
  async function guardar(evento) {
    evento.preventDefault()
    if (guardando) return
    const datos = Object.fromEntries(new FormData(evento.currentTarget))
    const locales = validarCuenta(datos)
    setErrores(locales)
    setMensaje(null)
    if (Object.keys(locales).length) return
    const cuerpo = Object.fromEntries(Object.entries(datos).map(([campo, valor]) => [campo, valor.trim() || null]))
    cuerpo.nit = normalizarDocumento(datos.nit)
    setGuardando(true)
    try {
      const resultado = cuenta ? await actualizarCuenta(cuenta.id, cuerpo) : await crearCuenta(cuerpo)
      if (montado.current) navegar(ruta + '/' + resultado.id)
    } catch (error) {
      if (!montado.current) return
      const porCampo = Object.fromEntries((error.errors ?? []).map(({ field, message }) => [field, message]))
      if (error.conflicto === 'CUENTA_NIT_DUPLICADO') porCampo.nit = error.message
      else if (error.campo) porCampo[error.campo] = error.message
      setErrores(porCampo)
      setMensaje(error.message)
    } finally {
      if (montado.current) setGuardando(false)
    }
  }

  return <form noValidate aria-label="Datos de la cuenta" onSubmit={guardar}>
    <fieldset disabled={guardando} className="grid gap-4 sm:grid-cols-2">
      {campos.map(([campo, etiqueta, obligatorio, tipo = 'text']) =>
        <Campo key={campo} etiqueta={etiqueta} obligatorio={obligatorio} error={errores[campo]}>
          <input name={campo} type={tipo} defaultValue={cuenta?.[campo] ?? ''} />
        </Campo>)}
    </fieldset>
    {mensaje && <p role="alert" className="message mt-4">{mensaje}</p>}
    <div className="mt-6 flex items-center gap-4">
      <button className="button" disabled={guardando}>{guardando ? 'Guardando…' : 'Guardar'}</button>
      {integrado ? <button type="reset" className="secondary" disabled={guardando} onClick={() => { setErrores({}); setMensaje(null) }}>Limpiar</button> :
        <Link to={cuenta ? ruta + '/' + cuenta.id : ruta}>Cancelar</Link>}
    </div>
  </form>
}
