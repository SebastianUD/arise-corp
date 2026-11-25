import { Link } from 'react-router-dom'
import { CATEGORIAS_ARTICULO } from '../../lib/validacionSga.js'
import { listarArticulos } from '../../services/sgaApi.js'
import VistaListaSga, { etiquetaSga, numeroSga } from './VistaListaSga.jsx'

const columnas = [
  { titulo: 'Código', valor: a => <Link to={'/sga/inventario/' + a.id}>{a.codigo}</Link> },
  { titulo: 'Nombre', valor: a => a.nombre }, { titulo: 'Categoría', valor: a => etiquetaSga(a.categoria) },
  { titulo: 'Existencia', valor: a => <>{numeroSga(a.existencia)} {etiquetaSga(a.unidad)}{a.bajoStockMinimo && <span className="badge borrador ml-2">Bajo stock</span>}</> },
  { titulo: 'Stock mínimo', valor: a => numeroSga(a.stockMinimo) },
]
export default function ArticulosLista() {
  return <VistaListaSga titulo="Inventario" descripcion="Controla las existencias y anticipa las necesidades de tu operación." ruta="/sga/inventario" nuevo="Nuevo artículo" etiquetaBusqueda="Buscar por código o nombre"
    consultar={listarArticulos} filtro={{ etiqueta: 'Categoría', campo: 'categoria', valores: CATEGORIAS_ARTICULO }} columnas={columnas} vacio="No hay artículos para los criterios elegidos." inventario />
}
