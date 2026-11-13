import { usePerfil } from '../context/PerfilContext.jsx'
import { useAuth } from '../context/AuthContext.jsx'

import { etiquetasRol } from '../lib/permisos.js'

export default function Perfil() {
  const { signOut } = useAuth()
  const { perfil: profile, cargando: loading, error, recargar } = usePerfil()
  const forbidden = error?.status === 403

  return (
    <section className="crm-panel" aria-labelledby="perfil-titulo">
      <header><h1 id="perfil-titulo">Mi perfil</h1></header>
      {loading && <p role="status" className="surface">Cargando tu información…</p>}
      {error && <div className="surface">
        <p role="alert" className="message">
          {forbidden
            ? 'Tu cuenta no tiene un perfil activo o permisos para acceder. Contacta al administrador.'
            : error.message}
        </p>
        {forbidden
          ? <button className="button" onClick={() => void signOut()}>Volver al inicio de sesión</button>
          : <button className="button" disabled={loading} onClick={recargar}>Reintentar</button>}
      </div>}
      {!loading && !error && profile && <article className="surface account-card">
        <div className="account-cover" aria-hidden="true" />
        <div className="account-content">
          <div className="account-avatar" aria-hidden="true">
            {profile.email?.trim().charAt(0).toLocaleUpperCase('es') || <img src="/assets/usuarios.svg" alt="" />}
          </div>
          <h2>Cuenta corporativa</h2>
          <dl className="account-details">
            <div><dt>Correo electrónico</dt><dd>{profile.email || 'Sin correo registrado'}</dd></div>
            <div><dt>Rol empresarial</dt><dd className="account-role">{etiquetasRol[profile.role] || 'No disponible'}</dd></div>
            <div className="account-id"><dt>Identificador</dt><dd>{profile.id}</dd></div>
          </dl>
        </div>
      </article>}
    </section>
  )
}
