import { Suspense } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { PerfilProvider, usePerfil } from '../context/PerfilContext.jsx'
import { opcionesMenu } from '../lib/permisos.js'

function Cabecera() {
  const { rol } = usePerfil()
  const { signOut } = useAuth()
  const enlaces = [{ texto: 'Perfil', ruta: '/perfil' }, ...opcionesMenu(rol)]
  return (
    <header className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-700 px-6 py-4">
      <nav aria-label="Navegación principal" className="flex flex-wrap gap-4">
        {enlaces.map(({ texto, ruta }) => (
          <NavLink key={ruta} to={ruta} className="hover:underline aria-[current=page]:underline">
            {texto}
          </NavLink>
        ))}
      </nav>
      <button className="button" onClick={() => void signOut()}>Cerrar sesión</button>
    </header>
  )
}

export default function AppLayout() {
  const { session } = useAuth()
  return (
    <PerfilProvider key={session.user.id}>
      <div className="flex min-h-svh flex-col bg-slate-950 text-slate-100">
        <Cabecera />
        <div className="profile-workspace">
          <Suspense fallback={<p role="status">Cargando…</p>}><Outlet /></Suspense>
        </div>
      </div>
    </PerfilProvider>
  )
}
