import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext.jsx'
import ProtectedRoute from './routes/ProtectedRoute.jsx'
import AppLayout from './components/AppLayout.jsx'
import Login from './pages/Login.jsx'
import Perfil from './pages/Perfil.jsx'

export default function App() {
  return (
    <AuthProvider>
      <main className="app-root">
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route path="/perfil" element={<Perfil />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/perfil" replace />} />
        </Routes>
      </main>
    </AuthProvider>
  )
}
