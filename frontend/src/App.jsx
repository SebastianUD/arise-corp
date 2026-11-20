import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext.jsx'
import ProtectedRoute from './routes/ProtectedRoute.jsx'
import AppLayout from './components/AppLayout.jsx'
import Login from './pages/Login.jsx'
import Perfil from './pages/Perfil.jsx'
import RolRoute from './routes/RolRoute.jsx'
import { puedeAdministrarUsuarios, puedeGestionarCrm, puedeGestionarErp, puedeLeerClientes, puedeLeerContratos } from './lib/permisos.js'

const Inicio = lazy(() => import('./pages/Inicio.jsx'))
const Panel = lazy(() => import('./pages/Panel.jsx'))
const UsuariosRoles = lazy(() => import('./pages/UsuariosRoles.jsx'))
const ClientesLista = lazy(() => import('./pages/crm/ClientesLista.jsx'))
const ClienteFormulario = lazy(() => import('./pages/crm/ClienteFormulario.jsx'))
const ClienteDetalle = lazy(() => import('./pages/crm/ClienteDetalle.jsx'))
const ContratoFormulario = lazy(() => import('./pages/crm/ContratoFormulario.jsx'))
const ContratosLista = lazy(() => import('./pages/crm/ContratosLista.jsx'))
const ContratoDetalle = lazy(() => import('./pages/crm/ContratoDetalle.jsx'))
const CuentasLista = lazy(() => import('./pages/crm/CuentasLista.jsx'))
const CuentaFormulario = lazy(() => import('./pages/crm/CuentaFormulario.jsx'))
const CuentaDetalle = lazy(() => import('./pages/crm/CuentaDetalle.jsx'))
const ResumenComercial = lazy(() => import('./pages/crm/ResumenComercial.jsx'))
const EmpleadosLista = lazy(() => import('./pages/erp/EmpleadosLista.jsx'))
const EmpleadoFormulario = lazy(() => import('./pages/erp/EmpleadoFormulario.jsx'))
const EmpleadoDetalle = lazy(() => import('./pages/erp/EmpleadoDetalle.jsx'))
const ProveedoresLista = lazy(() => import('./pages/erp/ProveedoresLista.jsx'))
const ProveedorFormulario = lazy(() => import('./pages/erp/ProveedorFormulario.jsx'))
const ProveedorDetalle = lazy(() => import('./pages/erp/ProveedorDetalle.jsx'))
const MovimientosLista = lazy(() => import('./pages/erp/MovimientosLista.jsx'))
const MovimientoFormulario = lazy(() => import('./pages/erp/MovimientoFormulario.jsx'))
const MovimientoDetalle = lazy(() => import('./pages/erp/MovimientoDetalle.jsx'))
const PanelFinanciero = lazy(() => import('./pages/erp/PanelFinanciero.jsx'))

export default function App() {
  return (
    <AuthProvider>
      <main className="app-root">
        <Routes>
          <Route path="/" element={<Suspense fallback={<p className="access-loading" role="status">Cargando inicio…</p>}><Inicio /></Suspense>} />
          <Route path="/login" element={<Login />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route path="/panel" element={<Panel />} />
              <Route path="/perfil" element={<Perfil />} />
              <Route element={<RolRoute permite={puedeGestionarErp} />}>
                <Route path="/erp/empleados" element={<EmpleadosLista />} />
                <Route path="/erp/empleados/nuevo" element={<EmpleadoFormulario />} />
                <Route path="/erp/empleados/:id" element={<EmpleadoDetalle />} />
                <Route path="/erp/empleados/:id/editar" element={<EmpleadoFormulario />} />
                <Route path="/erp/proveedores" element={<ProveedoresLista />} />
                <Route path="/erp/proveedores/nuevo" element={<ProveedorFormulario />} />
                <Route path="/erp/proveedores/:id" element={<ProveedorDetalle />} />
                <Route path="/erp/proveedores/:id/editar" element={<ProveedorFormulario />} />
                <Route path="/erp/movimientos" element={<MovimientosLista />} />
                <Route path="/erp/movimientos/nuevo" element={<MovimientoFormulario />} />
                <Route path="/erp/movimientos/:id" element={<MovimientoDetalle />} />
                <Route path="/erp/movimientos/:id/editar" element={<MovimientoFormulario />} />
                <Route path="/erp/resumen" element={<PanelFinanciero />} />
              </Route>
              <Route element={<RolRoute permite={puedeAdministrarUsuarios} />}>
                <Route path="/admin/usuarios" element={<UsuariosRoles />} />
              </Route>
              <Route element={<RolRoute permite={puedeLeerClientes} />}>
                <Route path="/crm/clientes" element={<ClientesLista />} />
                <Route path="/crm/clientes/:id" element={<ClienteDetalle />} />
              </Route>
              <Route element={<RolRoute permite={puedeLeerContratos} />}>
                <Route path="/crm/contratos" element={<ContratosLista />} />
                <Route path="/crm/contratos/:id" element={<ContratoDetalle />} />
              </Route>
              <Route element={<RolRoute permite={puedeGestionarCrm} />}>
                <Route path="/crm/cuentas-corporativas" element={<CuentasLista />} />
                <Route path="/crm/cuentas-corporativas/nueva" element={<CuentaFormulario />} />
                <Route path="/crm/cuentas-corporativas/:id" element={<CuentaDetalle />} />
                <Route path="/crm/cuentas-corporativas/:id/editar" element={<CuentaFormulario />} />
                <Route path="/crm/resumen" element={<ResumenComercial />} />
                <Route path="/crm/clientes/:clienteId/contratos/nuevo" element={<ContratoFormulario />} />
                <Route path="/crm/contratos/:id/editar" element={<ContratoFormulario />} />
                <Route path="/crm/clientes/nuevo" element={<ClienteFormulario />} />
                <Route path="/crm/clientes/:id/editar" element={<ClienteFormulario />} />
              </Route>
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/perfil" replace />} />
        </Routes>
      </main>
    </AuthProvider>
  )
}
