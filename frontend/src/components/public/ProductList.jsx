import ProductCard from "./ProductCard";
import Icon from "./Icon";
import "../../css/product-list.css";

/**
 * Grid de resultados del catálogo global. Incluye el encabezado con conteo y el estado vacío con
 * acción de restablecimiento.
 */
export default function ProductList({
  productos,
  total,
  titulo = "CATÁLOGO",
  subtitulo,
  onProductoClick,
  onRestablecer,
}) {
  const totalItems = total !== undefined ? total : productos.length;
  return (
    <>
      <div className="dm-results">
        <span>
          <strong>{totalItems}</strong> {totalItems === 1 ? "repuesto" : "repuestos"}
        </span>
        <span>{subtitulo || "Precios en COP"}</span>
      </div>

      <div className="dm-grid">
        {productos.map((p) => (
          <ProductCard key={p.id} producto={p} onClick={onProductoClick} />
        ))}
      </div>

      {!totalItems && (
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
