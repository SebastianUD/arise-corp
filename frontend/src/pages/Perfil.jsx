import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../context/AuthContext.jsx'
import { apiRequest } from '../services/apiClient.js'

export default function Perfil() {
  const { signOut } = useAuth()
  const [profile, setProfile] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [attempt, setAttempt] = useState(0)
  const inFlight = useRef(null)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    // Comparte la petición entre los dos montajes de efectos de StrictMode.
    if (!inFlight.current) {
      inFlight.current = apiRequest('/api/me').finally(() => { inFlight.current = null })
    }
    inFlight.current.then(data => {
      if (active) setProfile(data)
    }).catch(failure => {
      if (active) setError(failure.message)
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => { active = false }
  }, [attempt])

  return (
    <section className="mx-auto w-full max-w-2xl rounded-2xl border border-slate-700 bg-slate-900 p-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl font-semibold">Mi perfil</h1>
        <button className="button" onClick={() => void signOut()}>Cerrar sesión</button>
      </div>
      {loading && <p role="status" className="mt-6">Cargando tu información…</p>}
      {error && <div className="mt-6">
        <p role="alert" className="message">{error}</p>
        <button className="button" disabled={loading} onClick={() => setAttempt(value => value + 1)}>Reintentar</button>
      </div>}
      {!loading && !error && profile && <dl className="mt-8 space-y-5 break-words">
        <div><dt className="text-sm text-slate-400">Identificador</dt><dd className="mt-1">{profile.id}</dd></div>
        <div><dt className="text-sm text-slate-400">Correo electrónico</dt><dd className="mt-1">{profile.email || 'Sin correo registrado'}</dd></div>
      </dl>}
    </section>
  )
}
