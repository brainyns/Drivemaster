import ProductCard from "./ProductCard";
import Icon from "./Icon";
import "../../css/product-list.css";

/**
 * Grid de resultados del catálogo. Incluye el encabezado con conteo, el estado
 * vacío con acción de restablecimiento y el aviso de datos.
 */
export default function ProductList({
  productos,
  titulo = "CATÁLOGO",
  subtitulo,
  vehiculo,
  idsCompatibles,
  soloCompatibles,
  onProductoClick,
  onRestablecer,
}) {
  const hayFiltro = soloCompatibles && vehiculo;

  return (
    <>
      <div className="dm-results">
        <span>
          <strong>{productos.length}</strong> repuestos{" "}
          {hayFiltro ? "compatibles" : "disponibles"}
        </span>
        <span>{subtitulo || "Precios en COP"}</span>
      </div>

      <div className="dm-grid">
        {productos.map((p) => (
          <ProductCard
            key={p.id}
            producto={p}
            vehiculo={vehiculo}
            idsCompatibles={idsCompatibles}
            onClick={onProductoClick}
          />
        ))}
      </div>

      {!productos.length && (
        <div className="dm-empty">
          <Icon name="search" size={35} />
          <h3>{titulo}</h3>
          <p>Prueba con otra referencia, otra categoría o amplía el rango de precio.</p>
          <button className="dm-btn dm-btn--secondary" onClick={onRestablecer}>
            Restablecer filtros
          </button>
        </div>
      )}
    </>
  );
}