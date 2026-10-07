import Icon from "./Icon";
import "../../css/catalog.css";

/**
 * Barra de herramientas del catálogo: buscador, apertura de filtros (móvil) y
 * orden. El buscador vive aquí (y no en el header) para no duplicar el campo,
 * siguiendo la composición del mockup.
 */
export default function CatalogToolbar({
  busqueda,
  onBusquedaChange,
  sort,
  onSortChange,
  filtrosAbiertos,
  onToggleFiltros,
  hayFiltrosActivos = 0,
}) {
  return (
    <div className="dm-toolbar">
      <div className="dm-search">
        <Icon name="search" />
        <input
          placeholder="Buscar por repuesto, marca o referencia..."
          value={busqueda}
          onChange={(e) => onBusquedaChange(e.target.value)}
          aria-label="Buscar repuestos"
        />
        {busqueda && (
          <button
            className="dm-icon-btn"
            onClick={() => onBusquedaChange("")}
            aria-label="Limpiar búsqueda"
          >
            <Icon name="close" size={16} />
          </button>
        )}
      </div>

      <button
        className="dm-btn dm-btn--secondary dm-toolbar__filters"
        onClick={onToggleFiltros}
        aria-expanded={filtrosAbiertos}
      >
        <Icon name="sliders" />
        Filtros
        {hayFiltrosActivos > 0 && <span className="dm-toolbar__badge">{hayFiltrosActivos}</span>}
      </button>

      <select
        className="dm-sort"
        value={sort}
        onChange={(e) => onSortChange(e.target.value)}
        aria-label="Ordenar repuestos"
      >
        <option value="recommended">Recomendados</option>
        <option value="low">Menor precio</option>
        <option value="high">Mayor precio</option>
      </select>
    </div>
  );
}