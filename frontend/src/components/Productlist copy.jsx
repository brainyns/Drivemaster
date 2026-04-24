import ProductCard from "./ProductCard";
import "../css/product-list.css";

const ProductList = ({ productos, titulo = "PRODUCTOS", onProductoClick }) => {
  return (
    <section className="product-list">
      <div className="product-list__header">
        <h2 className="product-list__title">{titulo}</h2>
        <span className="product-list__subtitle">{productos.length} resultados</span>
      </div>

      <div className="product-list__grid">
        {productos.length === 0 ? (
          <div className="product-list__empty">
            <div className="product-list__empty-icon">🔍</div>
            <div className="product-list__empty-title">Sin resultados</div>
            <div className="product-list__empty-text">
              Intenta con otro término o cambia los filtros
            </div>
          </div>
        ) : (
          productos.map((producto, index) => (
            <ProductCard
              key={producto.id}
              producto={producto}
              onClick={onProductoClick}
              animDelay={index * 50}
            />
          ))
        )}
      </div>
    </section>
  );
};

export default ProductList;