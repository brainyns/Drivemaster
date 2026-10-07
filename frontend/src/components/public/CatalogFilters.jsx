import Icon from "./Icon";
import "../../css/catalog.css";

/**
 * Panel lateral de filtros del catálogo global. En móvil se despliega debajo de la barra de
 * herramientas. Ciudad, tienda y categoría se consultan al servidor; marca y precio se filtran en el
 * navegador. La compatibilidad con el vehículo solo avisa: no hay filtro que oculte productos.
 */
export default function CatalogFilters({
  abierto,
  categorias = [],
  ciudades = [],
  tiendas = [],
  marcas = [],
  categoriaActiva,
  onCategoriaChange,
  ciudadActiva,
  onCiudadChange,
  tiendaActiva,
  onTiendaChange,
  marcaActiva,
  onMarcaChange,
  precioMax,
  onPrecioMaxChange,
  precioTecho,
  hayVehiculo,
  onIrVehiculo,
  onLimpiar,
}) {
  return (
    <aside className={`dm-filters ${abierto ? "is-open" : ""}`} aria-label="Filtros">
      <div className="dm-filters__title">
        <strong>
          <Icon name="sliders" size={18} /> Filtros
        </strong>
        <button onClick={onLimpiar}>Limpiar</button>
      </div>

      {ciudades.length > 0 && (
        <fieldset>
          <legend>Ciudad</legend>
          <select value={ciudadActiva} onChange={(e) => onCiudadChange(e.target.value)} aria-label="Filtrar por ciudad">
            <option value="">Todas</option>
            {ciudades.map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
        </fieldset>
      )}

      {tiendas.length > 0 && (
        <fieldset>
          <legend>Tienda</legend>
          <select value={tiendaActiva} onChange={(e) => onTiendaChange(e.target.value)} aria-label="Filtrar por tienda">
            <option value="">Todas</option>
            {tiendas.map((t) => (
              <option key={t.id} value={t.id}>
                {t.esPlataforma ? "DriveMaster" : t.nombre} · {t.ciudad}
              </option>
            ))}
          </select>
        </fieldset>
      )}

      {categorias.length > 0 && (
        <fieldset>
          <legend>Categorías</legend>
          <label className="dm-filters__option">
            <input type="radio" name="categoria" checked={!categoriaActiva} onChange={() => onCategoriaChange("")} />
            <span>Todas</span>
          </label>
          {categorias.map((c) => (
            <label className="dm-filters__option" key={c}>
              <input
                type="radio"
                name="categoria"
                checked={categoriaActiva === c}
                onChange={() => onCategoriaChange(c)}
              />
              <span>{c}</span>
            </label>
          ))}
        </fieldset>
      )}

      {marcas.length > 0 && (
        <fieldset>
          <legend>Marca</legend>
          <select
            value={marcaActiva}
            onChange={(e) => onMarcaChange(e.target.value)}
            aria-label="Filtrar por marca"
          >
            <option value="">Todas</option>
            {marcas.map((m) => (
              <option key={m} value={m}>{m}</option>
            ))}
          </select>
        </fieldset>
      )}

      <fieldset>
        <legend>Precio máximo</legend>
        <input
          type="range"
          min="0"
          max={precioTecho}
          step="10000"
          value={precioMax}
          onChange={(e) => onPrecioMaxChange(Number(e.target.value))}
          aria-label="Precio máximo"
        />
        <div className="dm-filters__range">
          <span>$0</span>
          <strong>
            {new Intl.NumberFormat("es-CO", { maximumFractionDigits: 0 }).format(precioMax)}
          </strong>
        </div>
      </fieldset>

      <fieldset>
        <legend>Compatibilidad</legend>
        <p className="dm-filters__note">
          {hayVehiculo
            ? "Los repuestos compatibles con tu vehículo salen primero. Avisamos si uno no le sirve; ninguno se oculta."
            : "Identifica tu vehículo y te avisamos qué repuestos le sirven."}
        </p>
        {!hayVehiculo && (
          <button className="dm-filters__vehicle-link" onClick={onIrVehiculo}>
            Identifica tu vehículo <Icon name="arrow" size={13} />
          </button>
        )}
      </fieldset>

      <div className="dm-filters__help">
        <Icon name="shield" size={25} />
        <strong>Elige con confianza.</strong>
        <p>Compara el mismo repuesto entre tiendas y elige envío o retiro.</p>
      </div>
    </aside>
  );
}
