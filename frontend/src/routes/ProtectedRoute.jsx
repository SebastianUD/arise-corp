import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

export default function ProtectedRoute() {
  const { session, loading, reason } = useAuth()
  if (loading) return <p role="status">Comprobando sesión…</p>
  const expired = session?.expires_at && session.expires_at * 1000 <= Date.now()
  if (!session || expired) {
    return <Navigate to="/login" replace state={{ reason: expired ? 'expired' : reason }} />
  }
  return <Outlet />
}
