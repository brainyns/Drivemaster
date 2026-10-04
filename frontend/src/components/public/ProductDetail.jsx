import { useState } from "react";
import Modal from "./Modal";
import Icon from "./Icon";
import { getToken } from "../../services/authService";
import "../../css/product-detail.css";

const formatPrecio = (precio) => {
  if (!precio && precio !== 0) return "Consultar";
  return new Intl.NumberFormat("es-CO", {
    style: "currency",
    currency: "COP",
    minimumFractionDigits: 0,
  }).format(precio);
};

const WHATSAPP = "573015335263";

/** Bloque de compatibilidad: no afirma nada hasta que hay vehículo + consulta. */
function BloqueCompatibilidad({ producto, vehiculo, idsCompatibles, onIrVehiculo }) {
  const evaluado = !!(vehiculo && idsCompatibles);
  const compatible = evaluado && idsCompatibles.has(producto.id);

  if (!evaluado) {
    return (
      <div className="dm-detail__compat">
        <Icon name="car" size={25} />
        <div>
          <strong>{vehiculo ? "Verificando compatibilidad..." : "La compatibilidad es lo primero."}</strong>
          <p>
            {vehiculo
              ? "Estamos consultando los repuestos que le encajan."
              : "Identifica tu vehículo antes de elegir."}
          </p>
          <button onClick={onIrVehiculo}>
            {vehiculo ? "Cambiar vehículo" : "Verificar compatibilidad"} <Icon name="arrow" size={13} />
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className={`dm-detail__compat ${compatible ? "is-ok" : "is-no"}`}>
      <Icon name={compatible ? "shield" : "car"} size={25} />
      <div>
        <strong>
          {compatible ? "Compatible con" : "No compatible con"} {vehiculo.marca} {vehiculo.modelo}
        </strong>
        <p>
          {[vehiculo.anio, vehiculo.motor].filter(Boolean).join(" · ")}
        </p>
        <button onClick={onIrVehiculo}>
          Cambiar vehículo <Icon name="arrow" size={13} />
        </button>
      </div>
    </div>
  );
}

export default function ProductDetail({
  producto,
  vehiculo,
  idsCompatibles,
  onClose,
  onAddProduct,
  onIrSolicitud,
  onIrVehiculo,
  alternativas = [],
  onAbrirAlternativa,
}) {
  const [cantidad, setCantidad] = useState(1);
  const [mensaje, setMensaje] = useState(null);
  const token = getToken();

  if (!producto) return null;

  const nombre      = producto.nombre || "Sin nombre";
  const categoria   = producto.categoria || "Repuesto";
  const marca       = producto.marca || null;
  const referencia  = producto.codigo || null;
  const precio      = producto.precioVenta;
  const stock       = producto.stockActual ?? 0;
  const stockMinimo = producto.stockMinimo ?? 0;
  const imagen      = producto.imagenUrl || producto.imagen || null;
  const tipo        = producto.tipo || "STOCK";

  const esEncargo  = tipo === "ENCARGO";
  const sinStock   = !esEncargo && stock === 0;
  const stockBajo  = !esEncargo && stock > 0 && stock <= stockMinimo;
  const excede     = !esEncargo && cantidad > stock;

  const evaluado    = !!(vehiculo && idsCompatibles);
  const compatible  = evaluado && idsCompatibles.has(producto.id);
  const bloqueado   = sinStock || excede || (evaluado && !compatible);

  const whatsappUrl = `https://wa.me/${WHATSAPP}?text=${encodeURIComponent(
    `Hola, estoy interesado en el producto: ${nombre}${referencia ? ` (Ref: ${referencia})` : ""}. ¿Podrían brindarme información sobre disponibilidad, precio y tiempo de entrega? Quedo atento. Saludos.`
  )}`;

  const handleAgregar = () => {
    if (!token) {
      setMensaje("Debes iniciar sesión primero");
      return;
    }
    if (excede) {
      setMensaje(`Solo quedan ${stock} unidades disponibles`);
      return;
    }
    onAddProduct(producto, cantidad);
    setMensaje("¡Agregado a la solicitud!");
    setTimeout(() => setMensaje(null), 2000);
  };

  return (
    <Modal title="FICHA TÉCNICA" onClose={onClose} wide>
      <div className="dm-detail">
        <div className="dm-detail__visual">
          <span className="dm-eyebrow">{categoria}{marca ? ` / ${marca}` : ""}</span>
          {imagen ? (
            <img className="dm-detail__image" src={imagen} alt={nombre} />
          ) : (
            <div className="dm-detail__placeholder">
              <Icon name="box" size={64} />
              <p>Sin imagen del producto</p>
            </div>
          )}
          <span className="dm-detail__note">
            {producto.masVendido && "Producto más solicitado · "}
            Referencia de catálogo interna
          </span>
        </div>

        <div className="dm-detail__copy">
          <div className="dm-card__brand">
            <span>{marca || "DriveMaster"}</span>
            {referencia && <em>REF. {referencia}</em>}
          </div>

          <h2>{nombre}</h2>

          <BloqueCompatibilidad
            producto={producto}
            vehiculo={vehiculo}
            idsCompatibles={idsCompatibles}
            onIrVehiculo={onIrVehiculo}
          />

          <dl className="dm-specs">
            <div>
              <dt>Categoría</dt>
              <dd>{categoria}</dd>
            </div>
            {referencia && (
              <div>
                <dt>Referencia</dt>
                <dd>{referencia}</dd>
              </div>
            )}
            <div>
              <dt>Disponibilidad</dt>
              <dd>
                {esEncargo
                  ? "Bajo pedido"
                  : sinStock
                    ? "Sin stock"
                    : `${stock} unidades${stockBajo ? " · stock bajo" : ""}`}
              </dd>
            </div>
            {producto.especificaciones &&
              Object.entries(producto.especificaciones).map(([k, v]) => (
                <div key={k}>
                  <dt>{k}</dt>
                  <dd>{v}</dd>
                </div>
              ))}
          </dl>

          {producto.modelosCompatibles?.length > 0 && (
            <div className="dm-detail__models">
              <span>Modelos compatibles</span>
              <div>
                {producto.modelosCompatibles.map((c, i) => (
                  <em key={i}>
                    {c.marca} {c.modelo}
                    {c.anio ? ` ${c.anio}` : ""}
                  </em>
                ))}
              </div>
            </div>
          )}

          <div className="dm-detail__price">
            <strong>{formatPrecio(precio)}</strong>
            <span>COP · Precio referencial</span>
          </div>

          <div className="dm-detail__qty">
            <span>Cantidad</span>
            <div>
              <button
                onClick={() => setCantidad(Math.max(1, cantidad - 1))}
                disabled={cantidad <= 1}
                aria-label="Reducir cantidad"
              >
                −
              </button>
              <strong>{cantidad}</strong>
              <button
                onClick={() => setCantidad(cantidad + 1)}
                disabled={!esEncargo && cantidad >= stock}
                aria-label="Aumentar cantidad"
              >
                +
              </button>
            </div>
          </div>

          <button className="dm-btn dm-btn--primary dm-btn--full" onClick={handleAgregar} disabled={bloqueado}>
            {sinStock
              ? "Sin stock disponible"
              : excede
                ? `Solo quedan ${stock}`
                : evaluado && !compatible
                  ? "No compatible con tu vehículo"
                  : "Agregar a solicitud"}
            {!bloqueado && <Icon name="arrow" size={18} />}
          </button>

          <a
            className="dm-btn dm-btn--secondary dm-btn--full dm-btn--whatsapp"
            href={whatsappUrl}
            target="_blank"
            rel="noopener noreferrer"
          >
            <Icon name="message" size={19} /> Cotizar por WhatsApp
          </a>

          {token && onIrSolicitud && (
            <button className="dm-detail__go" onClick={onIrSolicitud}>
              Ir a mi solicitud <Icon name="arrow" size={14} />
            </button>
          )}

          {mensaje && <div className="dm-detail__msg">{mensaje}</div>}
        </div>
      </div>

      {alternativas.length > 0 && (
        <div className="dm-alternatives">
          <h3>Alternativas para seguir avanzando</h3>
          <p>Compara otras opciones de la misma categoría.</p>
          <div>
            {alternativas.map((a) => (
              <button key={a.id} onClick={() => onAbrirAlternativa(a)}>
                {a.imagenUrl || a.imagen ? (
                  <img src={a.imagenUrl || a.imagen} alt="" />
                ) : (
                  <span className="dm-alternatives__ph"><Icon name="box" size={26} /></span>
                )}
                <span>
                  <small>{a.marca || "DriveMaster"}{a.codigo ? ` · ${a.codigo}` : ""}</small>
                  <strong>{a.nombre}</strong>
                  <b>{formatPrecio(a.precioVenta)}</b>
                </span>
                <Icon name="arrow" size={19} />
              </button>
            ))}
          </div>
        </div>
      )}
    </Modal>
  );
}