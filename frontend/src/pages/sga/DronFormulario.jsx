import { validarDron } from '../../lib/validacionSga.js'
import { obtenerDron, crearDron, actualizarDron } from '../../services/sgaApi.js'
import FormularioRegistroSga from './FormularioRegistroSga.jsx'

const campos = [
  { nombre: 'codigo', etiqueta: 'Código' }, { nombre: 'modelo', etiqueta: 'Modelo' },
  { nombre: 'observaciones', etiqueta: 'Observaciones', obligatorio: false, multilinea: true },
]
export default function DronFormulario() {
  return <FormularioRegistroSga nombre="dron" ruta="/sga/drones" campos={campos} validar={validarDron} obtener={obtenerDron} crear={crearDron} actualizar={actualizarDron} />
}
