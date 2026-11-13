import { usePerfil } from '../context/PerfilContext.jsx'
import { useAuth } from '../context/AuthContext.jsx'

import { etiquetasRol } from '../lib/permisos.js'

export default function Perfil() {
  const { signOut } = useAuth()
  const { perfil: profile, cargando: loading, error, recargar } = usePerfil()
  const forbidden = error?.status === 403

  return (
    <section className="mx-auto w-full max-w-2xl rounded-2xl border border-slate-200 bg-white p-8">
      <h1 className="text-3xl font-semibold">Mi perfil</h1>
      {loading && <p role="status" className="mt-6">Cargando tu información…</p>}
      {error && <div className="mt-6">
        <p role="alert" className="message">
          {forbidden
            ? 'Tu cuenta no tiene un perfil activo o permisos para acceder. Contacta al administrador.'
            : error.message}
        </p>
        {forbidden
          ? <button className="button" onClick={() => void signOut()}>Volver al inicio de sesión</button>
          : <button className="button" disabled={loading} onClick={recargar}>Reintentar</button>}
      </div>}
      {!loading && !error && profile && <dl className="mt-8 space-y-5 break-words">
        <div><dt className="text-sm text-slate-600">Identificador</dt><dd className="mt-1">{profile.id}</dd></div>
        <div><dt className="text-sm text-slate-600">Correo electrónico</dt><dd className="mt-1">{profile.email || 'Sin correo registrado'}</dd></div>
        <div><dt className="text-sm text-slate-600">Rol empresarial</dt><dd className="mt-1">{etiquetasRol[profile.role] || 'No disponible'}</dd></div>
      </dl>}
    </section>
  )
}
