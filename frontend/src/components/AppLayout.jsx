import { Suspense, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { PerfilProvider, usePerfil } from '../context/PerfilContext.jsx'
import { etiquetasRol, opcionesMenu, puedeLeerClientes } from '../lib/permisos.js'

function EspacioTrabajo() {
  const { rol } = usePerfil()
  const { signOut } = useAuth()
  const { pathname } = useLocation()
  const navegar = useNavigate()
  const [oscuro, setOscuro] = useState(() => localStorage.getItem('arise-tema') === 'oscuro')
  const opciones = opcionesMenu(rol)
  const crm = opciones.filter(opcion => opcion.ruta.startsWith('/crm/'))
  const usuarios = opciones.find(opcion => opcion.ruta === '/admin/usuarios')
  const seccion = pathname.startsWith('/crm/') ? 'CRM' : pathname.startsWith('/admin/') ? 'Seguridad' : 'Inicio'
  const actual = opciones.find(opcion => pathname === opcion.ruta || pathname.startsWith(opcion.ruta + '/'))
  const titulo = actual?.texto ?? (pathname === '/panel' ? 'Panel general' : 'Perfil')

  function cambiarTema() {
    localStorage.setItem('arise-tema', oscuro ? 'claro' : 'oscuro')
    setOscuro(!oscuro)
  }

  return <div className="app-shell" data-tema={oscuro ? 'oscuro' : 'claro'}>
    <aside className="app-sidebar">
      <img className="sidebar-logo" src="/assets/arise-logo.png" alt="Arise Corp." width="176" height="68" />
      <div className="workspace-id">Arise Corp. <span>/</span> Colombia</div>
      <nav className="app-nav" aria-label="Navegación principal">
        {rol && <NavLink to="/panel" className="nav-item"><img src="/assets/inicio.svg" alt="" />Panel general</NavLink>}
        {crm.length > 0 && <>
          <div className={'nav-item nav-group' + (seccion === 'CRM' ? ' selected' : '')}><img src="/assets/crm.svg" alt="" />CRM</div>
          <div className="nav-sub">{crm.map(({ texto, ruta }) => <NavLink key={ruta} to={ruta}>{texto}</NavLink>)}</div>
        </>}
      </nav>
      <div className="nav-tools">
        {usuarios && <NavLink to={usuarios.ruta} className="nav-item"><img src="/assets/usuarios.svg" alt="" />{usuarios.texto}</NavLink>}
        <button className="nav-item" aria-pressed={oscuro} onClick={cambiarTema}><img src="/assets/tema.svg" alt="" />Tema {oscuro ? 'oscuro' : 'claro'}</button>
        <button className="nav-item" onClick={() => void signOut()}><img src="/assets/logout.svg" alt="" />Cerrar sesión</button>
      </div>
    </aside>
    <div className="app-main">
      <header className="app-top">
        <p className="breadcrumbs">{seccion}<span>/</span>{titulo}</p>
        {puedeLeerClientes(rol) && <form className="top-search" role="search" onSubmit={evento => {
          evento.preventDefault()
          const q = new FormData(evento.currentTarget).get('q').trim()
          navegar('/crm/clientes' + (q ? '?' + new URLSearchParams({ q }) : ''))
        }}>
          <button aria-label="Buscar cliente"><img src="/assets/buscar.svg" alt="" /></button>
          <input name="q" type="search" aria-label="Buscar cliente" placeholder="Buscar cliente por nombre o documento" maxLength={100} />
        </form>}
        <NavLink className="top-profile" aria-label="Perfil" to="/perfil">{etiquetasRol[rol] ?? 'Perfil'}<small>Mi cuenta</small></NavLink>
      </header>
      <div className="profile-workspace">
        <Suspense fallback={<p role="status">Cargando…</p>}><Outlet /></Suspense>
      </div>
    </div>
  </div>
}

export default function AppLayout() {
  const { session } = useAuth()
  return <PerfilProvider key={session.user.id}><EspacioTrabajo /></PerfilProvider>
}
