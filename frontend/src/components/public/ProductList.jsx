import ProductCard from "./ProductCard";
import Icon from "./Icon";
import "../../css/product-list.css";

/**
 * Grid de resultados del catálogo global. Incluye el encabezado con conteo y el estado vacío con
 * acción de restablecimiento.
 */
export default function ProductList({
  productos,
  titulo = "CATÁLOGO",
  subtitulo,
  onProductoClick,
  onRestablecer,
}) {
  return (
    <>
      <div className="dm-results">
        <span>
          <strong>{productos.length}</strong> repuestos disponibles
        </span>
        <span>{subtitulo || "Precios en COP"}</span>
      </div>

      <div className="dm-grid">
        {productos.map((p) => (
          <ProductCard key={p.id} producto={p} onClick={onProductoClick} />
        ))}
      </div>

      {!productos.length && (
        <div className="dm-empty">
          <Icon name="search" size={35} />
          <h3>{titulo}</h3>
          <p>Prueba con otra referencia, otra ciudad o amplía el rango de precio. Las tiendas que aún no completan sus datos no aparecen.</p>
          <button className="dm-btn dm-btn--secondary" onClick={onRestablecer}>
            Restablecer filtros
          </button>
        </div>
      )}
    </>
  );
}
