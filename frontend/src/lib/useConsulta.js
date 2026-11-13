import { useCallback, useEffect, useState } from 'react'

const inicial = { datos: null, cargando: true, error: null }

// Pasar una función estable (useCallback si depende de filtros o de un id).
export function useConsulta(consultar) {
  const [intento, setIntento] = useState(0)
  const [resultado, setResultado] = useState(null)
  const recargar = useCallback(() => setIntento(anterior => anterior + 1), [])

  useEffect(() => {
    let activo = true
    async function cargar() {
      try {
        const datos = await consultar()
        if (activo) setResultado({ consultar, intento, datos, error: null })
      } catch (error) {
        if (activo) setResultado({ consultar, intento, datos: null, error })
      }
    }
    void cargar()
    return () => { activo = false }
  }, [consultar, intento])

  if (resultado?.consultar !== consultar || resultado.intento !== intento) return { ...inicial, recargar }
  return { datos: resultado.datos, cargando: false, error: resultado.error, recargar }
}
