import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Campo from '../../components/Campo.jsx'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import { normalizarCodigo, texto } from '../../lib/validacionCrm.js'
import { useConsulta } from '../../lib/useConsulta.js'
import { erroresCampos, etiquetaSga } from './VistaListaSga.jsx'

export default function FormularioRegistroSga({ nombre, ruta, campos, validar, obtener, crear, actualizar }) {
  const { id } = useParams()
  const consulta = useConsulta(useCallback(() => id ? obtener(id) : Promise.resolve({}), [id, obtener]))
  return <section className="crm-panel sga-panel">
    <header><p className="sga-eyebrow">SGA · Operaciones</p><h1>{id ? 'Editar ' : 'Nuevo '}{nombre}</h1><p>Los campos con * son obligatorios.</p></header>
    <EstadoConsulta {...consulta}>{consulta.datos && <Formulario key={id ?? 'nuevo'} inicial={consulta.datos} {...{ nombre, ruta, campos, validar, crear, actualizar }} />}</EstadoConsulta>
  </section>
}

function Formulario({ inicial, nombre, ruta, campos, validar, crear, actualizar }) {
  const navegar = useNavigate()
  const [datos, setDatos] = useState(() => Object.fromEntries(campos.map(campo => [campo.nombre, String(inicial[campo.nombre] ?? campo.inicial ?? '')])))
  const [errores, setErrores] = useState({})
  const [fallo, setFallo] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const envio = useRef(false)
  const montado = useRef(false)
  useEffect(() => { montado.current = true; return () => { montado.current = false } }, [])
  async function guardar(evento) {
    evento.preventDefault()
    if (envio.current) return
    const locales = validar(datos)
    setErrores(locales); setFallo(null)
    if (Object.keys(locales).length) return
    envio.current = true; setGuardando(true)
    const cuerpo = Object.fromEntries(Object.entries(datos).map(([campo, valor]) => [campo, campo === 'codigo' ? normalizarCodigo(valor) : texto(valor)]))
    try {
      const resultado = inicial.id ? await actualizar(inicial.id, cuerpo) : await crear(cuerpo)
      if (montado.current) navegar(ruta + '/' + resultado.id)
    } catch (error) {
      if (montado.current) { setErrores(erroresCampos(error)); setFallo(error) }
    } finally { envio.current = false; if (montado.current) setGuardando(false) }
  }
  return <form aria-label={'Datos del ' + nombre} noValidate onSubmit={guardar}>
    <fieldset disabled={guardando} className="grid gap-5 sm:grid-cols-2">
      {campos.map(campo => {
        const control = { name: campo.nombre, value: datos[campo.nombre], onChange: e => setDatos({ ...datos, [campo.nombre]: e.target.value }) }
        return <Campo key={campo.nombre} etiqueta={campo.etiqueta} obligatorio={campo.obligatorio !== false} error={errores[campo.nombre]}>
          {campo.opciones ? <select {...control}>{campo.opciones.map(opcion => <option key={opcion} value={opcion}>{etiquetaSga(opcion)}</option>)}</select>
            : campo.multilinea ? <textarea rows={4} {...control} /> : <input inputMode={campo.numerico ? 'numeric' : undefined} {...control} />}
        </Campo>
      })}
    </fieldset>
    {fallo && <p role="alert" className="message mt-4">{fallo.message}</p>}
    <div className="sga-acciones"><button className="button" disabled={guardando}>{guardando ? 'Guardando…' : 'Guardar'}</button>
      <Link to={inicial.id ? ruta + '/' + inicial.id : ruta}>Cancelar</Link></div>
  </form>
}
