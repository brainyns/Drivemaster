import "../css/product-detail.css";

const formatPrecio = (precio) => {
  if (!precio && precio !== 0) return "Consultar";
  return new Intl.NumberFormat("es-CO", {
    style: "currency",
    currency: "COP",
    minimumFractionDigits: 0,
  }).format(precio);
};

const ProductDetail = ({ producto, onClose }) => {
  if (!producto) return null;

  // Campos reales del backend
  const nombre      = producto.nombre      || "Sin nombre";
  const categoria   = producto.categoria   || "Sin categoría";
  const marca       = producto.marca       || null;
  const precio      = producto.precioVenta;
  const stock       = producto.stockActual ?? 0;
  const stockMinimo = producto.stockMinimo ?? 0;
  const imagen      = producto.imagenUrl   || producto.imagen || null;
  const masVendido  = producto.masVendido  || false;
  const stockBajo   = stock <= stockMinimo && stock > 0;
  const sinStock    = stock === 0;

  const whatsappUrl = `https://wa.me/573015335263?text=Hola,%20buenas%20tardes,%20estoy%20interesado%20en%20el%20producto%20${encodeURIComponent(nombre)}`;

  const handleOverlayClick = (e) => {
    if (e.target === e.currentTarget) onClose();
  };

  return (
    <div className="product-detail-overlay" onClick={handleOverlayClick}>
      <div className="product-detail">
        <button className="product-detail__back" onClick={onClose}>
          ← Volver al catálogo
        </button>

        <div className="product-detail__grid">
          {/* Imagen */}
          <div className="product-detail__image-col">
            {imagen ? (
              <img className="product-detail__image" src={imagen} alt={nombre} />
            ) : (
              <div className="product-detail__image-placeholder">
                <span>⚙️</span>
                <p>Sin imagen</p>
              </div>
            )}
            {masVendido && (
              <div className="product-detail__badges">
                <span className="product-detail__badge">🔥 Más vendido</span>
              </div>
            )}
          </div>

          {/* Info */}
          <div className="product-detail__info">
            <span className="product-detail__categoria">{categoria}</span>

            <h1 className="product-detail__nombre">{nombre}</h1>

            {marca && (
              <div className="product-detail__marca">Marca: <strong>{marca}</strong></div>
            )}

            {producto.codigo && (
              <div className="product-detail__codigo">Código: {producto.codigo}</div>
            )}

            <div className="product-detail__precio-row">
              <div className="product-detail__precio">{formatPrecio(precio)}</div>
              <div className="product-detail__precio-label">
                Precio<br />referencial
              </div>
            </div>

            {/* Stock */}
            <div className={`product-detail__stock ${sinStock ? "out" : stockBajo ? "low" : ""}`}>
              <span className="product-detail__stock-dot" />
              {sinStock
                ? "Sin stock disponible"
                : stockBajo
                ? `Stock bajo — ${stock} unidades`
                : `En stock — ${stock} unidades disponibles`}
            </div>

            {/* Modelos compatibles */}
            {producto.modelosCompatibles?.length > 0 && (
              <div className="product-detail__specs">
                <div className="product-detail__specs-title">Modelos compatibles</div>
                {producto.modelosCompatibles.map((comp, i) => (
                  <div key={i} className="product-detail__spec-row">
                    <span className="product-detail__spec-key">{comp.marca} {comp.modelo}</span>
                    <span className="product-detail__spec-val">{comp.anio || ""}</span>
                  </div>
                ))}
              </div>
            )}

            {/* Especificaciones extras si las hay */}
            {producto.especificaciones && Object.keys(producto.especificaciones).length > 0 && (
              <div className="product-detail__specs">
                <div className="product-detail__specs-title">Especificaciones técnicas</div>
                {Object.entries(producto.especificaciones).map(([key, val]) => (
                  <div key={key} className="product-detail__spec-row">
                    <span className="product-detail__spec-key">{key}</span>
                    <span className="product-detail__spec-val">{val}</span>
                  </div>
                ))}
              </div>
            )}

            {/* Botón WhatsApp */}
            <a
              className="product-detail__wsp-btn"
              href={whatsappUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              <span className="product-detail__wsp-icon">📱</span>
              Comprar por WhatsApp
            </a>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProductDetail;