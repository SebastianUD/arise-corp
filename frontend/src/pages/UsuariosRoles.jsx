import { useState } from 'react'
import Campo from '../components/Campo.jsx'
import { usePerfil } from '../context/PerfilContext.jsx'
import { etiquetasRol } from '../lib/permisos.js'
import { apiRequest } from '../services/apiClient.js'

const uuid = '[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}'

export default function UsuariosRoles() {
  const { perfil, recargar } = usePerfil()
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')
  const [resultado, setResultado] = useState(null)

  async function guardar(evento) {
    evento.preventDefault()
    if (guardando) return
    const datos = new FormData(evento.currentTarget)
    const id = datos.get('usuarioId').trim().toLowerCase()
    setGuardando(true)
    setResultado(null)
    setError('')
    try {
      const respuesta = await apiRequest(`/api/admin/usuarios/${id}/perfil`, {
        method: 'PUT', body: { rol: datos.get('rol') },
      })
      setResultado(respuesta)
      if (id === perfil.id && respuesta.rol !== perfil.role) recargar()
    } catch (fallo) {
      setError(fallo.status === 404 ? 'No encontramos una cuenta con ese identificador.' : fallo.message)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <section className="crm-panel">
      <h1>Usuarios y roles</h1>
      <form className="admin-form" onSubmit={guardar} onChange={() => { setError(''); setResultado(null) }}>
        <h2>Asignar rol</h2>
        <p id="ayuda-usuario">Ingresa el identificador de la cuenta registrada en Supabase Auth para añadir su perfil o cambiar su rol.</p>
        <Campo etiqueta="Identificador del usuario" obligatorio>
          <input name="usuarioId" pattern={uuid} title="Introduce un UUID con guiones" aria-describedby="ayuda-usuario" disabled={guardando} />
        </Campo>
        <Campo etiqueta="Rol empresarial" obligatorio>
          <select name="rol" defaultValue="" disabled={guardando}>
            <option value="" disabled>Selecciona un rol</option>
            {Object.entries(etiquetasRol).map(([valor, nombre]) => <option key={valor} value={valor}>{nombre}</option>)}
          </select>
        </Campo>
        <button className="button justify-self-start" disabled={guardando}>{guardando ? 'Guardando…' : 'Guardar rol'}</button>
        {error && <p role="alert" className="message">{error}</p>}
        {resultado && <p role="status">Rol guardado para {resultado.usuarioId}. {etiquetasRol[resultado.rol]} · {resultado.activo ? 'Perfil activo' : 'Perfil inactivo'}</p>}
      </form>
    </section>
  )
}
