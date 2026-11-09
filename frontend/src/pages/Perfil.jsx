import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../context/AuthContext.jsx'
import { apiRequest } from '../services/apiClient.js'

const roleLabels = {
  ADMINISTRADOR: 'Administrador',
  COMERCIAL: 'Comercial',
  FINANZAS_RRHH: 'Finanzas / RR. HH.',
  OPERACIONES: 'Operaciones',
}

export default function Perfil() {
  const { signOut } = useAuth()
  const [profile, setProfile] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [attempt, setAttempt] = useState(0)
  const inFlight = useRef(null)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)
    // Comparte la petición entre los dos montajes de efectos de StrictMode.
    if (!inFlight.current) {
      inFlight.current = apiRequest('/api/me').finally(() => { inFlight.current = null })
    }
    inFlight.current.then(data => {
      if (active) setProfile(data)
    }).catch(failure => {
      if (active) setError(failure)
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => { active = false }
  }, [attempt])

  const forbidden = error?.status === 403

  return (
    <section className="mx-auto w-full max-w-2xl rounded-2xl border border-slate-700 bg-slate-900 p-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl font-semibold">Mi perfil</h1>
        <button className="button" onClick={() => void signOut()}>Cerrar sesión</button>
      </div>
      {loading && <p role="status" className="mt-6">Cargando tu información…</p>}
      {error && <div className="mt-6">
        <p role="alert" className="message">
          {forbidden
            ? 'Tu cuenta no tiene un perfil activo o permisos para acceder. Contacta al administrador.'
            : error.message}
        </p>
        {forbidden
          ? <button className="button" onClick={() => void signOut()}>Volver al inicio de sesión</button>
          : <button className="button" disabled={loading} onClick={() => setAttempt(value => value + 1)}>Reintentar</button>}
      </div>}
      {!loading && !error && profile && <dl className="mt-8 space-y-5 break-words">
        <div><dt className="text-sm text-slate-400">Identificador</dt><dd className="mt-1">{profile.id}</dd></div>
        <div><dt className="text-sm text-slate-400">Correo electrónico</dt><dd className="mt-1">{profile.email || 'Sin correo registrado'}</dd></div>
        <div><dt className="text-sm text-slate-400">Rol empresarial</dt><dd className="mt-1">{roleLabels[profile.role] || 'No disponible'}</dd></div>
      </dl>}
    </section>
  )
}
