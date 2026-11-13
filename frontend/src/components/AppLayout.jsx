import { Suspense } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { PerfilProvider, usePerfil } from '../context/PerfilContext.jsx'
import { opcionesMenu } from '../lib/permisos.js'

function Navegacion() {
  const { rol } = usePerfil()
  const { signOut } = useAuth()
  const enlaces = [{ texto: 'Perfil', ruta: '/perfil' }, ...opcionesMenu(rol)]
  return (
    <aside className="app-sidebar">
      <img className="sidebar-logo" src="/assets/arise-logo.png" alt="Arise Corp." width="176" height="68" />
      <nav aria-label="Navegación principal" className="app-nav">
        {enlaces.map(({ texto, ruta }) => (
          <NavLink key={ruta} to={ruta} className="nav-item">
            {ruta === '/perfil' && <img src="/assets/inicio.svg" alt="" />}
            {ruta === '/admin/usuarios' && <img src="/assets/usuarios.svg" alt="" />}
            {texto}
          </NavLink>
        ))}
      </nav>
      <button className="sign-out" onClick={() => void signOut()}><img src="/assets/logout.svg" alt="" />Cerrar sesión</button>
    </aside>
  )
}

export default function AppLayout() {
  const { session } = useAuth()
  return (
    <PerfilProvider key={session.user.id}>
      <div className="app-shell">
        <Navegacion />
        <div className="profile-workspace">
          <Suspense fallback={<p role="status">Cargando…</p>}><Outlet /></Suspense>
        </div>
      </div>
    </PerfilProvider>
  )
}
