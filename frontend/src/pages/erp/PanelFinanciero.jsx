import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import Campo from '../../components/Campo.jsx'
import EstadoConsulta from '../../components/EstadoConsulta.jsx'
import EstadoMovimientoEtiqueta from '../../components/EstadoMovimientoEtiqueta.jsx'
import { formatoCop, formatoFecha } from '../../lib/formato.js'
import { validarRangoFechas } from '../../lib/validacionErp.js'
import { useConsulta } from '../../lib/useConsulta.js'
import { obtenerResumenFinanciero } from '../../services/erpApi.js'

export default function PanelFinanciero() {
  const [params, setParams] = useSearchParams()
  const desde = params.get('desde') ?? ''
  const hasta = params.get('hasta') ?? ''
  const errores = validarRangoFechas(desde, hasta)
  const invalido = Object.keys(errores).length > 0
  const consulta = useConsulta(useCallback(() => invalido ? Promise.resolve(null) : obtenerResumenFinanciero({ desde, hasta }), [desde, hasta, invalido]))
  const resumen = consulta.datos
  return <section className="crm-panel"><header><h1>Panel financiero</h1><p>Ingresos, egresos y saldo del periodo.</p></header>
    <form key={params.toString()} className="crm-filtros" noValidate onSubmit={evento => {
      evento.preventDefault()
      const siguientes = new URLSearchParams()
      for (const [campo, valor] of new FormData(evento.currentTarget)) if (valor) siguientes.set(campo, valor)
      setParams(siguientes)
    }}>
      <Campo etiqueta="Desde" error={errores.desde}><input type="date" name="desde" defaultValue={desde} /></Campo>
      <Campo etiqueta="Hasta" error={errores.hasta}><input type="date" name="hasta" defaultValue={hasta} /></Campo>
      <button className="secondary">Filtrar</button>
    </form>
    {!invalido && <EstadoConsulta {...consulta}>{resumen && <>
      <p>{!resumen.desde && !resumen.hasta ? 'Todo el periodo' :
        `Desde ${resumen.desde ? formatoFecha(resumen.desde) : 'el inicio'} · Hasta ${resumen.hasta ? formatoFecha(resumen.hasta) : 'la última fecha'}`}</p>
      <section className="surface"><h2>Saldo pagado</h2><p className="text-3xl">{formatoCop(resumen.saldoPagado)}</p></section>
      {Object.entries({ INGRESO: 'Ingresos', EGRESO: 'Egresos' }).map(([tipo, titulo]) => <section className="grid gap-4" key={tipo} aria-label={titulo}>
        <h2>{titulo}</h2><p>Cantidad total: {resumen.porTipo[tipo].cantidad}</p>
        <div className="overflow-x-auto"><table aria-label={titulo}><thead><tr><th>Estado</th><th>Cantidad</th><th>Suma</th></tr></thead>
          <tbody>{['PENDIENTE', 'PAGADO', 'ANULADO'].map(estado => {
            const fila = resumen.porTipo[tipo].porEstado[estado]
            return <tr key={estado}><td><EstadoMovimientoEtiqueta estado={estado} /></td><td>{fila.cantidad}</td><td>{formatoCop(fila.suma)}</td></tr>
          })}</tbody></table></div>
      </section>)}
    </>}</EstadoConsulta>}
  </section>
}
