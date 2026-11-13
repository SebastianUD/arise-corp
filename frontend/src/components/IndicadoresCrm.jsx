import EstadoConsulta from './EstadoConsulta.jsx'
import { useConsulta } from '../lib/useConsulta.js'
import { listarCuentas, obtenerResumen } from '../services/crmApi.js'

async function resumenConCuentas() {
  const [resumen, cuentas] = await Promise.all([obtenerResumen(), listarCuentas({ size: 1 })])
  return { ...resumen, cuentas: cuentas.totalElements }
}

export default function IndicadoresCrm({ vista }) {
  const consulta = useConsulta(vista === 'clientes' ? resumenConCuentas : obtenerResumen)
  const datos = consulta.datos
  const filas = []
  if (datos) {
    const { clientes, contratos } = datos
    const estados = contratos.porEstado
    if (vista !== 'contratos') filas.push(['Clientes activos', clientes.activos], ['Clientes inactivos', clientes.inactivos])
    if (vista !== 'clientes') filas.push(['Contratos activos', estados.ACTIVO], ['Borradores', estados.BORRADOR])
    if (vista === 'clientes') filas.push(['Cuentas corporativas', datos.cuentas])
    if (vista === 'contratos') filas.push(['Finalizados / cancelados', estados.FINALIZADO + ' / ' + estados.CANCELADO])
  }
  return <EstadoConsulta {...consulta}>{datos && <dl className="metrics">
    {filas.map(([nombre, valor]) => <div key={nombre}><dt>{nombre}</dt><dd>{valor}</dd></div>)}
  </dl>}</EstadoConsulta>
}
