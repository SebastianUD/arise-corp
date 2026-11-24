import { Link } from 'react-router-dom'
import EstadoDronEtiqueta from '../../components/EstadoDronEtiqueta.jsx'
import { ESTADOS_DRON } from '../../lib/validacionSga.js'
import { listarDrones } from '../../services/sgaApi.js'
import VistaListaSga from './VistaListaSga.jsx'

const columnas = [
  { titulo: 'Código', valor: dron => <Link to={'/sga/drones/' + dron.id}>{dron.codigo}</Link> },
  { titulo: 'Modelo', valor: dron => dron.modelo },
  { titulo: 'Estado', valor: dron => <EstadoDronEtiqueta estado={dron.estado} /> },
]
export default function DronesLista() {
  return <VistaListaSga titulo="Drones" descripcion="Consulta tu flota y su disponibilidad para las entregas." ruta="/sga/drones" nuevo="Nuevo dron"
    consultar={listarDrones} filtro={{ etiqueta: 'Estado', campo: 'estado', valores: ESTADOS_DRON }} columnas={columnas} vacio="No hay drones para los criterios elegidos." />
}
