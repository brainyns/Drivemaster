import Icon from "./Icon";
import "../../css/catalog.css";

/**
 * Panel lateral de filtros del catálogo. En móvil se despliega debajo de la
 * barra de herramientas.
 */
export default function CatalogFilters({
  abierto,
  categorias = [],
  marcas = [],
  categoriaActiva,
  onCategoriaChange,
  marcaActiva,
  onMarcaChange,
  precioMax,
  onPrecioMaxChange,
  precioTecho,
  soloMasVendidos,
  onMasVendidosChange,
  soloCompatibles,
  onSoloCompatiblesChange,
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

      {categorias.length > 0 && (
        <fieldset>
          <legend>Categorías</legend>
          {categorias.map((c) => (
            <label className="dm-filters__option" key={c.nombre}>
              <input
                type="radio"
                name="categoria"
                checked={categoriaActiva === c.nombre}
                onChange={() => onCategoriaChange(c.nombre)}
              />
              <span>{c.nombre}</span>
              <small>{c.count}</small>
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
        <legend>Disponibilidad y compatibilidad</legend>
        <label className="dm-filters__option">
          <input
            type="checkbox"
            checked={soloMasVendidos}
            onChange={(e) => onMasVendidosChange(e.target.checked)}
          />
          <span>Solo más vendidos</span>
        </label>
        <label className="dm-filters__option">
          <input
            type="checkbox"
            checked={soloCompatibles}
            disabled={!hayVehiculo}
            onChange={(e) => onSoloCompatiblesChange(e.target.checked)}
          />
          <span>Solo compatibles con mi vehículo</span>
        </label>
        {!hayVehiculo && (
          <button className="dm-filters__vehicle-link" onClick={onIrVehiculo}>
            Identifica tu vehículo <Icon name="arrow" size={13} />
          </button>
        )}
      </fieldset>

      <div className="dm-filters__help">
        <Icon name="shield" size={25} />
        <strong>Elige con confianza.</strong>
        <p>Cada ficha reúne las especificaciones para una decisión informada.</p>
      </div>
    </aside>
  );
}