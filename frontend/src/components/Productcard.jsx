import "../css/product-card.css";

const formatPrecio = (precio) => {
  if (!precio && precio !== 0) return "Consultar";
  return new Intl.NumberFormat("es-CO", {
    style: "currency",
    currency: "COP",
    minimumFractionDigits: 0,
  }).format(precio);
};

const ProductCard = ({ producto, onClick, animDelay = 0 }) => {
  // Campos reales del backend
  const nombre      = producto.nombre      || "Sin nombre";
  const categoria   = producto.categoria   || "Sin categoría";
  const precio      = producto.precioVenta;
  const stock       = producto.stockActual ?? 0;
  const stockMinimo = producto.stockMinimo ?? 0;
  const imagen      = producto.imagenUrl   || producto.imagen || null;
  const masVendido  = producto.masVendido  || false;
  const stockBajo   = stock <= stockMinimo && stock > 0;
  const sinStock    = stock === 0;

  return (
    <div
      className="product-card"
      style={{ animationDelay: `${animDelay}ms` }}
      onClick={() => onClick(producto)}
    >
      <div className="product-card__image-wrap">
        {imagen ? (
          <img
            className="product-card__image"
            src={imagen}
            alt={nombre}
            loading="lazy"
          />
        ) : (
          <div className="product-card__image-placeholder">
            <span>⚙️</span>
          </div>
        )}

        {masVendido && (
          <span className="product-card__badge">🔥 Top ventas</span>
        )}

        <span className={`product-card__stock-badge ${stockBajo ? "low" : ""} ${sinStock ? "out" : ""}`}>
          {sinStock ? "✗ Sin stock" : stockBajo ? `⚠️ Stock: ${stock}` : `✓ En stock`}
        </span>
      </div>

      <div className="product-card__body">
        <span className="product-card__categoria">{categoria}</span>
        <h3 className="product-card__nombre">{nombre}</h3>
        {producto.marca && (
          <span className="product-card__marca">{producto.marca}</span>
        )}

        <div className="product-card__footer">
          <div>
            <div className="product-card__precio">{formatPrecio(precio)}</div>
            <div className="product-card__precio-label">Precio referencial</div>
          </div>
          <div className="product-card__arrow">→</div>
        </div>
      </div>
    </div>
  );
};

export default ProductCard;