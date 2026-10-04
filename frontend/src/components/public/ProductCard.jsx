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
 * Etiqueta de compatibilidad. Sin vehículo seleccionado no se afirma nada:
 * el prototipo del mockup decía "verificar compatibilidad" y aquí se mantiene
 * para no prometer una compatibilidad que todavía no se ha comprobado.
 */
function Compatibilidad({ producto, vehiculo, idsCompatibles }) {
  let estado = "verificar";
  let texto = "Verificar compatibilidad";

  if (vehiculo && idsCompatibles) {
    estado = idsCompatibles.has(producto.id) ? "compatible" : "incompatible";
    texto = estado === "compatible"
      ? "Compatible con tu vehículo"
      : "No compatible con tu vehículo";
  }

  return (
    <span className={`dm-compat dm-compat--${estado}`}>
      <Icon name={estado === "compatible" ? "check" : "car"} size={14} />
      {texto}
    </span>
  );
}

/** Estado de inventario. "ENCARGO" significa bajo pedido, no stock local. */
function EstadoStock({ producto }) {
  const stock = producto.stockActual ?? 0;
  const stockMinimo = producto.stockMinimo ?? 0;
  const esEncargo = producto.tipo === "ENCARGO";

  if (esEncargo) {
    return (
      <span className="dm-stock is-encargo">
        <i /> Bajo pedido
      </span>
    );
  }

  if (stock === 0) {
    return (
      <span className="dm-stock is-out">
        <i /> Sin stock
      </span>
    );
  }

  if (stock <= stockMinimo) {
    return (
      <span className="dm-stock is-low">
        <i /> Quedan {stock}
      </span>
    );
  }

  return (
    <span className="dm-stock">
      <i /> En stock
    </span>
  );
}

export default function ProductCard({ producto, vehiculo, idsCompatibles, onClick }) {
  const nombre    = producto.nombre || "Sin nombre";
  const categoria = producto.categoria || "Repuesto";
  const marca     = producto.marca || null;
  const referencia= producto.codigo || null;
  const precio    = producto.precioVenta;
  const imagen    = producto.imagenUrl || producto.imagen || null;

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

        {producto.masVendido && <span className="dm-card__top">Top ventas</span>}

        <span className="dm-card__open">
          <Icon name="plus" size={18} />
        </span>
      </div>

      <div className="dm-card__info">
        <div className="dm-card__brand">
          <span>{marca || "DriveMaster"}</span>
          {referencia && <em>REF. {referencia}</em>}
        </div>

        <h3>{nombre}</h3>

        <Compatibilidad producto={producto} vehiculo={vehiculo} idsCompatibles={idsCompatibles} />

        <div className="dm-card__bottom">
          <strong>
            {formatPrecio(precio)}
            <small>COP · Precio referencial</small>
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