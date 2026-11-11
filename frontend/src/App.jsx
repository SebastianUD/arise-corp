import { lazy } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext.jsx'
import ProtectedRoute from './routes/ProtectedRoute.jsx'
import AppLayout from './components/AppLayout.jsx'
import Login from './pages/Login.jsx'
import Perfil from './pages/Perfil.jsx'
import RolRoute from './routes/RolRoute.jsx'
import { puedeGestionarCrm, puedeLeerClientes } from './lib/permisos.js'

const ClientesLista = lazy(() => import('./pages/crm/ClientesLista.jsx'))
const ClienteFormulario = lazy(() => import('./pages/crm/ClienteFormulario.jsx'))
const ClienteDetalle = lazy(() => import('./pages/crm/ClienteDetalle.jsx'))

export default function App() {
  return (
    <AuthProvider>
      <main className="app-root">
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route path="/perfil" element={<Perfil />} />
              <Route element={<RolRoute permite={puedeLeerClientes} />}>
                <Route path="/crm/clientes" element={<ClientesLista />} />
                <Route path="/crm/clientes/:id" element={<ClienteDetalle />} />
              </Route>
              <Route element={<RolRoute permite={puedeGestionarCrm} />}>
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
