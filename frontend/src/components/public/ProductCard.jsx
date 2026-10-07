import Icon from "./Icon";
import "../../css/product-card.css";

const formatPrecio = (precio) => {
  if (!precio && precio !== 0) return "Consultar";
  return new Intl.NumberFormat("es-CO", {
    style: "currency",
    currency: "COP",
    minimumFractionDigits: 0,
  }).format(precio);
};

/**
 * Etiqueta de compatibilidad. La calcula el backend con el vehículo elegido (solo avisa: un repuesto
 * "no compatible" se muestra igual). Sin vehículo no se afirma nada.
 */
function Compatibilidad({ producto }) {
  const mapa = {
    COMPATIBLE: { estado: "compatible", texto: "Compatible con tu vehículo" },
    NO_COMPATIBLE: { estado: "incompatible", texto: "No compatible con tu vehículo" },
    POR_CONFIRMAR: { estado: "verificar", texto: "Confirma la compatibilidad" },
  };
  const { estado, texto } = mapa[producto.compatibilidad] || { estado: "verificar", texto: "Verificar compatibilidad" };

  return (
    <span className={`dm-compat dm-compat--${estado}`}>
      <Icon name={estado === "compatible" ? "check" : "car"} size={14} />
      {texto}
    </span>
  );
}

/** Estado de inventario según el catálogo global. "ENCARGO" significa bajo pedido, no stock local. */
function EstadoStock({ producto }) {
  if (producto.tipo === "ENCARGO") {
    return (
      <span className="dm-stock is-encargo">
        <i /> Bajo pedido{producto.diasEntrega ? ` · ${producto.diasEntrega} días` : ""}
      </span>
    );
  }
  if (!producto.disponible) {
    return (
      <span className="dm-stock is-out">
        <i /> Sin stock
      </span>
    );
  }
  if (producto.stock != null && producto.stock <= 5) {
    return (
      <span className="dm-stock is-low">
        <i /> Quedan {producto.stock}
      </span>
    );
  }
  return (
    <span className="dm-stock">
      <i /> En stock
    </span>
  );
}

/** Tarjeta de un producto del catálogo global: de DriveMaster o de cualquier tienda aprobada. */
export default function ProductCard({ producto, onClick }) {
  const nombre = producto.nombre || "Sin nombre";
  const categoria = producto.categoria || "Repuesto";
  const tienda = producto.tienda;
  const marca = producto.marca || null;
  const imagen = producto.imagenUrl || null;

  return (
    <button className="dm-card" onClick={() => onClick(producto)}>
      <div className="dm-card__picture">
        <span className="dm-card__category">{categoria}</span>

        {imagen ? (
          <img className="dm-card__image" src={imagen} alt={nombre} loading="lazy" />
        ) : (
          <div className="dm-card__placeholder">
            <Icon name="box" size={44} />
            <span>{categoria}</span>
          </div>
        )}

        {producto.esPlataforma && <span className="dm-card__top">Vendido por DriveMaster</span>}

        <span className="dm-card__open">
          <Icon name="plus" size={18} />
        </span>
      </div>

      <div className="dm-card__info">
        <div className="dm-card__brand">
          <span>{marca || tienda?.nombre || "DriveMaster"}</span>
          {producto.referencia && <em>REF. {producto.referencia}</em>}
        </div>

        <h3>{nombre}</h3>

        {tienda && (
          <p className="dm-card__store">
            {producto.esPlataforma ? "DriveMaster" : tienda.nombre} · {tienda.ciudad}
          </p>
        )}

        <Compatibilidad producto={producto} />
        {producto.avisoCompatibilidad && (
          <p className={`dm-card__aviso dm-card__aviso--${producto.compatibilidad === "NO_COMPATIBLE" ? "error" : "aviso"}`}>
            {producto.avisoCompatibilidad}
          </p>
        )}

        <div className="dm-card__bottom">
          <strong>
            {formatPrecio(producto.precio)}
            <small>COP · {tienda?.ofreceEnvio ? `Envío ${formatPrecio(tienda.costoEnvio)}` : "Retiro en tienda"}</small>
          </strong>
          <EstadoStock producto={producto} />
        </div>

        <div className="dm-card__link">
          Ver repuesto <Icon name="arrow" size={16} />
        </div>
      </div>
    </button>
  );
}
