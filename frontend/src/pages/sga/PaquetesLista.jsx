import { Link } from 'react-router-dom'
import EstadoPaqueteEtiqueta from '../../components/EstadoPaqueteEtiqueta.jsx'
import { ESTADOS_PAQUETE } from '../../lib/validacionSga.js'
import { listarPaquetes } from '../../services/sgaApi.js'
import VistaListaSga from './VistaListaSga.jsx'

const columnas = [
  { titulo: 'Código', valor: p => <Link to={'/sga/paquetes/' + p.id}>{p.codigo}</Link> },
  { titulo: 'Descripción', valor: p => p.descripcion }, { titulo: 'Cliente', valor: p => p.cliente.nombre },
  { titulo: 'Dron', valor: p => p.dron?.codigo ?? '—' }, { titulo: 'Estado', valor: p => <EstadoPaqueteEtiqueta estado={p.estado} /> },
]
export default function PaquetesLista() {
  return <VistaListaSga titulo="Paquetes" descripcion="Sigue cada entrega, desde su registro hasta el destino final." ruta="/sga/paquetes" nuevo="Nuevo paquete"
    consultar={listarPaquetes} filtro={{ etiqueta: 'Estado', campo: 'estado', valores: ESTADOS_PAQUETE }} columnas={columnas} vacio="No hay paquetes para los criterios elegidos." />
}
