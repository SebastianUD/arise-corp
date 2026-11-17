import { useCallback, useState } from 'react'
import Campo from './Campo.jsx'
import EstadoConsulta from './EstadoConsulta.jsx'
import { useConsulta } from '../lib/useConsulta.js'

export default function SelectorRegistro({ etiqueta, buscar, valor, onCambiar, error, deshabilitado }) {
  const [texto, setTexto] = useState('')
  const [q, setQ] = useState('')
  const consulta = useConsulta(useCallback(() => buscar(q), [buscar, q]))
  const resultados = consulta.datos?.content ?? []
  const opciones = valor && !resultados.some(registro => registro.id === valor.id) ? [valor, ...resultados] : resultados

  return <div className="grid gap-3">
    <div className="flex items-end gap-3">
      <Campo etiqueta={'Buscar ' + etiqueta.toLowerCase()}>
        <input type="search" value={texto} maxLength={100} disabled={deshabilitado}
          onChange={evento => setTexto(evento.target.value)} onKeyDown={evento => {
            if (evento.key === 'Enter') { evento.preventDefault(); if (!deshabilitado) buscarAhora() }
          }} />
      </Campo>
      <button type="button" className="secondary" disabled={deshabilitado} onClick={buscarAhora}>Buscar</button>
    </div>
    <EstadoConsulta {...consulta} />
    <Campo etiqueta={etiqueta} error={error}>
      <select value={valor?.id ?? ''} disabled={deshabilitado} onChange={evento =>
        onCambiar(opciones.find(registro => registro.id === evento.target.value) ?? null)}>
        <option value="">Sin asociación</option>
        {opciones.map(registro => <option key={registro.id} value={registro.id}>
          {registro.nombre}{registro.activo === false ? ' (inactivo)' : ''}
        </option>)}
      </select>
    </Campo>
  </div>

  function buscarAhora() {
    const siguiente = texto.trim()
    if (siguiente === q) consulta.recargar()
    else setQ(siguiente)
  }
}
