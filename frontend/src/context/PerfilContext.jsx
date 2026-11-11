import { createContext, useContext, useEffect, useRef, useState } from 'react'
import { apiRequest } from '../services/apiClient.js'

const PerfilContext = createContext(null)

export function PerfilProvider({ children }) {
  const [perfil, setPerfil] = useState(null)
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [intento, setIntento] = useState(0)
  const pendiente = useRef(null)

  useEffect(() => {
    let activo = true
    // Los dos efectos de StrictMode comparten la misma petición.
    pendiente.current ??= apiRequest('/api/me').finally(() => { pendiente.current = null })
    pendiente.current.then(datos => {
      if (activo) setPerfil(datos)
    }).catch(fallo => {
      if (activo) setError(fallo)
    }).finally(() => {
      if (activo) setCargando(false)
    })
    return () => { activo = false }
  }, [intento])

  function recargar() {
    setPerfil(null)
    setError(null)
    setCargando(true)
    setIntento(valor => valor + 1)
  }

  return (
    <PerfilContext.Provider value={{ perfil, rol: perfil?.role ?? null, cargando, error, recargar }}>
      {children}
    </PerfilContext.Provider>
  )
}

export const usePerfil = () => useContext(PerfilContext)
