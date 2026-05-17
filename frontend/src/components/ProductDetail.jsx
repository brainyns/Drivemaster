import { useState } from "react";
import { getToken, getUser } from "../services/authService";
import "../css/product-detail.css";

const formatPrecio = (precio) => {
  if (!precio && precio !== 0) return "Consultar";
  return new Intl.NumberFormat("es-CO", {
    style: "currency",
    currency: "COP",
    minimumFractionDigits: 0,
  }).format(precio);
};

const ProductDetail = ({ producto, onClose, onAddProduct, onIrSolicitud }) => {
  const [cantidad, setCantidad] = useState(1);
  const [mensaje, setMensaje] = useState(null);
  const user = getUser();
  const token = getToken();

  if (!producto) return null;

  const nombre      = producto.nombre      || "Sin nombre";
  const categoria   = producto.categoria   || "Sin categoría";
  const marca       = producto.marca       || null;
  const precio      = producto.precioVenta;
  const stock       = producto.stockActual ?? 0;
  const stockMinimo = producto.stockMinimo ?? 0;
  const imagen      = producto.imagenUrl   || producto.imagen || null;
  const masVendido  = producto.masVendido  || false;
  const tipo        = producto.tipo        || "STOCK";
  const stockBajo   = stock <= stockMinimo && stock > 0;
  const sinStock    = stock === 0;

  const ref = producto.codigo || producto.id || "";
  const whatsappUrl = `https://wa.me/573015335263?text=${encodeURIComponent(
    `Hola, estoy interesado en el producto: ${nombre}${ref ? ` (Ref: ${ref})` : ""}. ¿Podrían brindarme información sobre disponibilidad, precio y tiempo de entrega? Quedo atento. Saludos.`
  )}`;

  const handleOverlayClick = (e) => {
    if (e.target === e.currentTarget) onClose();
  };

  const stockAgotado = tipo === "STOCK" && sinStock;
  const sinStockSuficiente = tipo === "STOCK" && cantidad > stock;

  const handleAgregar = () => {
    if (!token) {
      setMensaje("Debes iniciar sesión primero");
      return;
    }
    if (sinStockSuficiente) {
      setMensaje(`Solo quedan ${stock} unidades disponibles`);
      return;
    }
    onAddProduct(producto, cantidad);
    setMensaje("¡Agregado a la solicitud!");
    setTimeout(() => setMensaje(null), 2000);
  };

  return (
    <div className="product-detail-overlay" onClick={handleOverlayClick}>
      <div className="product-detail">
        <button className="product-detail__back" onClick={onClose}>
          ← Volver al catálogo
        </button>

        <div className="product-detail__grid">
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

          <div className="product-detail__info">
            <span className="product-detail__categoria">{categoria}</span>

            <h1 className="product-detail__nombre">{nombre}</h1>

            {marca && (
              <div className="product-detail__marca">Marca: <strong>{marca}</strong></div>
            )}

            {producto.codigo && (
              <div className="product-detail__codigo">Código: {producto.codigo}</div>
            )}

            <div className="product-detail__tipo-badge">
              {tipo === "ENCARGO" ? "📦 Producto por encargo" : "📦 En stock"}
            </div>

            <div className="product-detail__precio-row">
              <div className="product-detail__precio">{formatPrecio(precio)}</div>
              <div className="product-detail__precio-label">
                Precio<br />referencial
              </div>
            </div>

            <div className={`product-detail__stock ${sinStock ? "out" : stockBajo ? "low" : ""}`}>
              <span className="product-detail__stock-dot" />
              {sinStock
                ? "Sin stock disponible"
                : stockBajo
                ? `Stock bajo — ${stock} unidades`
                : `En stock — ${stock} unidades disponibles`}
            </div>

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

            <div className="product-detail__cantidad">
              <label>Cantidad:</label>
              <div className="product-detail__cantidad-controls">
                <button onClick={() => setCantidad(Math.max(1, cantidad - 1))} disabled={cantidad <= 1}>-</button>
                <span>{cantidad}</span>
                <button onClick={() => setCantidad(cantidad + 1)} disabled={tipo === "STOCK" && cantidad >= stock}>+</button>
              </div>
            </div>

            <div className="product-detail__actions">
              <button className="product-detail__solicitud-btn" onClick={handleAgregar} disabled={stockAgotado || sinStockSuficiente}>
                Agregar a solicitud
              </button>
              <a
                className="product-detail__wsp-btn"
                href={whatsappUrl}
                target="_blank"
                rel="noopener noreferrer"
              >
                <span className="product-detail__wsp-icon">📱</span>
                Cotizar por WhatsApp
              </a>
            </div>

            {mensaje && <div className="product-detail__mensaje">{mensaje}</div>}

            {token && onIrSolicitud && (
              <button className="product-detail__ir-solicitud" onClick={onIrSolicitud}>
                Ir a la solicitud →
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProductDetail;
