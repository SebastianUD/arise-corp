export default function Paginacion({ pagina, onCambiar }) {
  const { page, totalPages } = pagina
  if (totalPages === 0 && page === 0) return null
  const anterior = Math.max(0, Math.min(page - 1, totalPages - 1))
  return (
    <nav aria-label="Paginación" className="flex flex-wrap items-center gap-3">
      <button type="button" className="secondary" disabled={page === 0}
        onClick={() => onCambiar(anterior)}>Anterior</button>
      <span aria-live="polite">{page < totalPages ? `Página ${page + 1} de ${totalPages}` : 'Página sin resultados'}</span>
      <button type="button" className="secondary" disabled={page + 1 >= totalPages}
        onClick={() => onCambiar(page + 1)}>Siguiente</button>
    </nav>
  )
}
