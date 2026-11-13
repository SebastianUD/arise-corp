export default function EstadoConsulta({ cargando, error, recargar, children }) {
  if (cargando) return <p role="status">Cargando…</p>
  if (!error) return children ?? null

  let mensaje = error.message || 'No pudimos cargar la información.'
  if (error.status === 403) mensaje = 'No tienes permisos para consultar esta información.'
  if (error.status === 404 && !error.detail) mensaje = 'Recurso no encontrado.'
  return (
    <div>
      <p role="alert" className="message">{mensaje}</p>
      {error.reintentable && recargar && (
        <button type="button" className="button" onClick={recargar}>Reintentar</button>
      )}
    </div>
  )
}
