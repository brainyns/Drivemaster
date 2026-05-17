import { useEffect, useRef } from "react";
import "../css/solicitud-drawer.css";

const formatPrecio = (p) =>
  new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", minimumFractionDigits: 0 }).format(p || 0);

export default function SolicitudDrawer({
  abierto, onCerrar, onEnviarSolicitud,
  items, subtotal, iva, total,
  onUpdateQuantity, onRemoveProduct
}) {
  const drawerRef = useRef();

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (drawerRef.current && !drawerRef.current.contains(e.target) && !e.target.closest(".navbar__solicitud-btn")) {
        onCerrar();
      }
    };
    if (abierto) document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [abierto, onCerrar]);

  return (
    <>
      <div className={`sd-overlay ${abierto ? "abierto" : ""}`} onClick={onCerrar} />
      <div ref={drawerRef} className={`sd-drawer ${abierto ? "abierto" : ""}`}>
        <div className="sd-header">
          <h2>Solicitud</h2>
          <span className="sd-header-badge">{items.length} item(s)</span>
          <button className="sd-close" onClick={onCerrar}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>

        {items.length === 0 ? (
          <div className="sd-empty">
            <div className="sd-empty-icon">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="16" y1="13" x2="8" y2="13"/>
                <line x1="16" y1="17" x2="8" y2="17"/>
              </svg>
            </div>
            <p>La solicitud está vacía</p>
            <p className="sd-empty-sub">Agregue productos desde el catálogo</p>
          </div>
        ) : (
          <>
            <div className="sd-items">
              {items.map(item => (
                <div key={item.productoId} className="sd-item">
                  <div className="sd-item-img">
                    {item.imagenUrl ? (
                      <img src={item.imagenUrl} alt={item.nombre} />
                    ) : (
                      <div className="sd-item-img-placeholder">
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                          <rect x="3" y="3" width="18" height="18" rx="2"/>
                          <circle cx="8.5" cy="8.5" r="1.5"/>
                          <path d="M21 15l-5-5L5 21"/>
                        </svg>
                      </div>
                    )}
                  </div>
                  <div className="sd-item-info">
                    <div className="sd-item-nombre">{item.nombre}</div>
                    <div className="sd-item-precio">{formatPrecio(item.precioUnitario)}</div>
                    <div className="sd-item-actions">
                      <button onClick={() => onUpdateQuantity(item.productoId, Math.max(1, item.cantidad - 1))} disabled={item.cantidad <= 1}>-</button>
                      <span>{item.cantidad}</span>
                      <button onClick={() => onUpdateQuantity(item.productoId, item.cantidad + 1)}>+</button>
                      <button className="sd-item-remove" onClick={() => onRemoveProduct(item.productoId)}>
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                        </svg>
                      </button>
                    </div>
                  </div>
                  <div className="sd-item-subtotal">{formatPrecio(item.precioUnitario * item.cantidad)}</div>
                </div>
              ))}
            </div>

            <div className="sd-footer">
              <div className="sd-subtotal">
                <span>Subtotal</span>
                <span>{formatPrecio(subtotal)}</span>
              </div>
              <div className="sd-iva">
                <span>IVA</span>
                <span>{formatPrecio(iva)}</span>
              </div>
              <div className="sd-total">
                <span>Total</span>
                <span>{formatPrecio(total)}</span>
              </div>
              <button className="sd-submit-btn" onClick={onEnviarSolicitud}>
                Enviar solicitud
              </button>
            </div>
          </>
        )}
      </div>
    </>
  );
}

export { formatPrecio };
