import { Outlet } from 'react-router-dom'
import { usePerfil } from '../context/PerfilContext.jsx'

export default function RolRoute({ permite }) {
  const { rol, cargando } = usePerfil()
  if (cargando) return <p role="status">Cargando…</p>
  if (!permite(rol)) return <p role="alert">No tienes permisos para acceder a esta sección.</p>
  return <Outlet />
}
