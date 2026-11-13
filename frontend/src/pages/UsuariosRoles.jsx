import { useState } from 'react'
import Campo from '../components/Campo.jsx'
import { usePerfil } from '../context/PerfilContext.jsx'
import { etiquetasRol, puedeAdministrarUsuarios, puedeGestionarCrm, puedeLeerContratos } from '../lib/permisos.js'
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
      <header><h1>Usuarios y permisos</h1><p>Acceso por rol a los módulos de la plataforma.</p></header>
      <section className="surface permissions"><h2>Permisos por módulo</h2>
        <div className="overflow-x-auto"><table><thead><tr><th>Rol</th><th>CRM</th><th>Usuarios y roles</th></tr></thead>
          <tbody>{Object.entries(etiquetasRol).map(([rol, etiqueta]) => <tr key={rol}><td>{etiqueta}</td>
            <td>{puedeGestionarCrm(rol) ? 'Gestionar' : puedeLeerContratos(rol) ? 'Consultar clientes y contratos' : 'Consultar clientes'}</td>
            <td>{puedeAdministrarUsuarios(rol) ? 'Gestionar' : 'Sin acceso'}</td></tr>)}</tbody>
        </table></div>
      </section>
      <div className="split-view">
      <section className="surface"><h2>Perfil de usuario</h2>
        <dl className="profile-summary"><dt>Cuenta conectada</dt><dd>{perfil.email}</dd>
          <dt>Identificador</dt><dd>{perfil.id}</dd><dt>Rol</dt><dd>{etiquetasRol[perfil.role]}</dd></dl>
      </section>
      <form className="admin-form" onSubmit={guardar} onChange={() => { setError(''); setResultado(null) }}>
        <h2>Editar rol autorizado</h2>
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
        <button className="button justify-self-end" disabled={guardando}>{guardando ? 'Guardando…' : 'Guardar rol'}</button>
        {error && <p role="alert" className="message">{error}</p>}
        {resultado && <p role="status">Rol guardado para {resultado.usuarioId}. {etiquetasRol[resultado.rol]} · {resultado.activo ? 'Perfil activo' : 'Perfil inactivo'}</p>}
      </form>
      </div>
    </section>
  )
}
