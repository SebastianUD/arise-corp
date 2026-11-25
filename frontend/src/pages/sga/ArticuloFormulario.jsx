import { CATEGORIAS_ARTICULO, UNIDADES_ARTICULO, validarArticulo } from '../../lib/validacionSga.js'
import { obtenerArticulo, crearArticulo, actualizarArticulo } from '../../services/sgaApi.js'
import FormularioRegistroSga from './FormularioRegistroSga.jsx'

const campos = [
  { nombre: 'codigo', etiqueta: 'Código' }, { nombre: 'nombre', etiqueta: 'Nombre' },
  { nombre: 'categoria', etiqueta: 'Categoría', opciones: CATEGORIAS_ARTICULO, inicial: 'REPUESTO' },
  { nombre: 'unidad', etiqueta: 'Unidad', opciones: UNIDADES_ARTICULO, inicial: 'UNIDAD' },
  { nombre: 'stockMinimo', etiqueta: 'Stock mínimo', numerico: true, inicial: '0' },
]
export default function ArticuloFormulario() {
  return <FormularioRegistroSga nombre="artículo" ruta="/sga/inventario" campos={campos} validar={validarArticulo} obtener={obtenerArticulo} crear={crearArticulo} actualizar={actualizarArticulo} />
}
