import { Link, Navigate } from 'react-router-dom'
import IndicadoresCrm from '../components/IndicadoresCrm.jsx'
import { usePerfil } from '../context/PerfilContext.jsx'
import { puedeAdministrarUsuarios, puedeGestionarCrm, puedeLeerContratos } from '../lib/permisos.js'

export default function Panel() {
  const { rol, cargando } = usePerfil()
  if (cargando) return <p role="status">Cargando…</p>
  if (!rol) return <Navigate to="/perfil" replace />
  const destinos = [
    ['Clientes', '/crm/clientes', 'Consultar clientes'],
    ...(puedeLeerContratos(rol) ? [['Contratos', '/crm/contratos', 'Ver contratos']] : []),
    ...(puedeGestionarCrm(rol) ? [['Cuentas corporativas', '/crm/cuentas-corporativas', 'Ver cuentas']] : []),
  ]
  return <section className="crm-panel">
    <header><h1>Tu operación, conectada.</h1></header>
    {puedeGestionarCrm(rol) && <IndicadoresCrm vista="panel" />}
    <div className="dashboard-cards">{destinos.map(([titulo, ruta, accion]) =>
      <section className="surface" key={ruta}><small>CRM</small><h2>{titulo}</h2><Link to={ruta}>{accion} →</Link></section>
    )}</div>
    {puedeAdministrarUsuarios(rol) && <section className="surface"><h2>Administración de acceso</h2>
      <Link to="/admin/usuarios">Gestionar usuarios y roles →</Link></section>}
  </section>
}
