import { useRef, useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import AccessLayout from '../components/AccessLayout.jsx'

export default function Login() {
  const { session, loading, signIn, reason, configurationError } = useAuth()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const pending = useRef(false)

  async function handleSubmit(event) {
    event.preventDefault()
    if (pending.current) return
    const validation = {}
    if (!email.trim()) validation.email = 'El correo es obligatorio.'
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) validation.email = 'Introduce un correo válido.'
    if (!password) validation.password = 'La contraseña es obligatoria.'
    setErrors(validation)
    setError('')
    if (Object.keys(validation).length) return
    pending.current = true
    setSubmitting(true)
    try {
      await signIn(email.trim(), password)
    } catch {
      setError('No pudimos iniciar sesión. Revisa tus credenciales y tu conexión e intenta nuevamente.')
    } finally {
      pending.current = false
      setSubmitting(false)
    }
  }

  if (loading) return <p role="status" className="access-loading">Comprobando sesión…</p>
  if (session && (!session.expires_at || session.expires_at * 1000 > Date.now())) {
    return <Navigate to="/perfil" replace />
  }

  return (
    <AccessLayout>
    <section className="access-card" aria-labelledby="login-title">
      <h1 id="login-title" className="access-title">Bienvenido de nuevo</h1>
      <p className="access-subtitle">Ingresa con tu cuenta corporativa.</p>
      <form noValidate onSubmit={handleSubmit} className="access-form" aria-label="Inicio de sesión">
      {(reason === 'expired' || location.state?.reason === 'expired') && (
        <p role="alert" className="access-alert">Tu sesión expiró o ya no es válida. Inicia sesión nuevamente.</p>
      )}
      {configurationError && <p role="alert" className="access-alert">{configurationError}</p>}
      {error && <p role="alert" className="access-alert">{error}</p>}
        <div className="access-field access-email">
          <label htmlFor="email">Correo electrónico <span aria-hidden="true">*</span></label>
          <input id="email" type="email" autoComplete="username" required value={email}
            placeholder="nombre@arisecorp.co"
            onChange={event => setEmail(event.target.value)} disabled={submitting}
            aria-invalid={!!errors.email} aria-describedby={errors.email ? 'email-error' : undefined} />
          {errors.email && <p id="email-error" className="access-field-error">{errors.email}</p>}
        </div>
        <div className="access-field">
          <label htmlFor="password">Contraseña <span aria-hidden="true">*</span></label>
          <input id="password" type="password" autoComplete="current-password" required value={password}
            placeholder="Ingresa tu contraseña"
            onChange={event => setPassword(event.target.value)} disabled={submitting}
            aria-invalid={!!errors.password} aria-describedby={errors.password ? 'password-error' : undefined} />
          {errors.password && <p id="password-error" className="access-field-error">{errors.password}</p>}
        </div>
        <button type="submit" className="access-submit" disabled={submitting || !!configurationError}>
          {submitting ? 'Iniciando sesión…' : 'Iniciar sesión'}
          {!submitting && <span aria-hidden="true">→</span>}
        </button>
        {submitting && <p role="status">Comprobando tus credenciales…</p>}
      </form>
      <p className="access-help">¿Necesitas acceso? Contacta al administrador.</p>
    </section>
    </AccessLayout>
  )
}
