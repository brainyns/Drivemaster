import { useEffect, useState } from "react";
import Modal from "./Modal";
import Icon from "./Icon";
import { detalleProducto } from "../../services/catalogoService";
import "../../css/product-detail.css";
import { etiquetaCategoria } from "../../utils/categorias";

const formatPrecio = (precio) => {
  if (!precio && precio !== 0) return "Consultar";
  return new Intl.NumberFormat("es-CO", {
    style: "currency",
    currency: "COP",
    minimumFractionDigits: 0,
  }).format(precio);
};

const WHATSAPP = "573015335263";

/** Bloque de compatibilidad: la calcula el backend con el vehículo elegido y solo avisa. */
function BloqueCompatibilidad({ producto, vehiculo, onIrVehiculo }) {
  const c = producto.compatibilidad;

  if (!vehiculo || !c) {
    return (
      <div className="dm-detail__compat">
        <Icon name="car" size={25} />
        <div>
          <strong>La compatibilidad es lo primero.</strong>
          <p>Identifica tu vehículo y te avisamos si este repuesto le sirve.</p>
          <button onClick={onIrVehiculo}>
            Verificar compatibilidad <Icon name="arrow" size={13} />
          </button>
        </div>
      </div>
    );
  }

  const ok = c === "COMPATIBLE";
  return (
    <div className={`dm-detail__compat ${ok ? "is-ok" : "is-no"}`}>
      <Icon name={ok ? "shield" : "car"} size={25} />
      <div>
        <strong>
          {c === "COMPATIBLE" ? "Compatible con" : c === "NO_COMPATIBLE" ? "No compatible con" : "Confirma que le sirve a"}{" "}
          {vehiculo.marca} {vehiculo.modelo}
        </strong>
        <p>{producto.avisoCompatibilidad || [vehiculo.anio, vehiculo.motor].filter(Boolean).join(" · ")}</p>
        <button onClick={onIrVehiculo}>
          Cambiar vehículo <Icon name="arrow" size={13} />
        </button>
      </div>
    </div>
  );
}

/**
 * Ficha de un producto del catálogo global: la tienda que lo vende (ciudad, dirección, horario y envío)
 * y el mismo repuesto en otras tiendas para comparar. Se agrega al carrito del catálogo.
 */
export default function ProductDetail({
  producto: inicial,
  vehiculo,
  onClose,
  onAgregar,
  onIrCarrito,
  onIrVehiculo,
  onAbrirProducto,
}) {
  const [cantidad, setCantidad] = useState(1);
  const [agregado, setAgregado] = useState(false);
  // Detalle completo (con otras tiendas); mientras llega se muestra la tarjeta
  const [completo, setCompleto] = useState({ id: null, producto: null });
  const producto = completo.id === inicial.id && completo.producto ? completo.producto : inicial;
  const otras = completo.id === inicial.id ? completo.producto?.otrasTiendas || [] : [];

  useEffect(() => {
    let vivo = true;
    detalleProducto(inicial.id, vehiculo?.id || "")
      .then((p) => { if (vivo) setCompleto({ id: inicial.id, producto: p }); })
      .catch(() => { /* la ficha se queda con los datos de la tarjeta */ });
    return () => { vivo = false; };
  }, [inicial.id, vehiculo?.id]);

  const nombre = producto.nombre || "Sin nombre";
  const categoria = etiquetaCategoria(producto.categoria) || "Repuesto";
  const marca = producto.marca || null;
  const tienda = producto.tienda;
  const encargo = producto.tipo === "ENCARGO";
  const tope = encargo ? 99 : Math.max(1, Math.min(99, producto.stock ?? 99));
  const sinStock = !producto.disponible;
  const excede = !encargo && producto.stock != null && producto.stock < 20 && cantidad > producto.stock;
  const bloqueado = sinStock || excede;

  const whatsappUrl = `https://wa.me/${WHATSAPP}?text=${encodeURIComponent(
    `Hola, estoy interesado en el producto: ${nombre}${producto.referencia ? ` (Ref: ${producto.referencia})` : ""}. ¿Podrían brindarme información sobre disponibilidad, precio y tiempo de entrega? Quedo atento. Saludos.`
  )}`;

  const agregar = () => {
    // El carrito guarda lo necesario para mostrarse; las otras tiendas no se arrastran al almacenamiento
    const { otrasTiendas, ...ligero } = producto; // eslint-disable-line no-unused-vars
    onAgregar(ligero, cantidad);
    setAgregado(true);
  };

  return (
    <Modal title="FICHA TÉCNICA" onClose={onClose} wide>
      <div className="dm-detail">
        <div className="dm-detail__visual">
          <span className="dm-eyebrow">{categoria}{marca ? ` / ${marca}` : ""}</span>
          {producto.imagenUrl ? (
            <img className="dm-detail__image" src={producto.imagenUrl} alt={nombre} />
          ) : (
            <div className="dm-detail__placeholder">
              <Icon name="box" size={64} />
              <p>Sin imagen del producto</p>
            </div>
          )}
          <span className="dm-detail__note">
            Vendido por {tienda?.nombre}
          </span>
        </div>

        <div className="dm-detail__copy">
          <div className="dm-card__brand">
            <span>{marca || tienda?.nombre}</span>
            {producto.referencia && <em>REF. {producto.referencia}</em>}
          </div>

          <h2>{nombre}</h2>

          <BloqueCompatibilidad producto={producto} vehiculo={vehiculo} onIrVehiculo={onIrVehiculo} />

          <dl className="dm-specs">
            <div>
              <dt>Categoría</dt>
              <dd>{categoria}</dd>
            </div>
            <div>
              <dt>Disponibilidad</dt>
              <dd>
                {encargo
                  ? `Bajo pedido${producto.diasEntrega ? ` · ${producto.diasEntrega} ${producto.diasEntrega === 1 ? "día" : "días"}` : ""}`
                  : sinStock ? "Sin stock" : `${producto.stock}${producto.stock >= 20 ? "+" : ""} ${producto.stock === 1 ? "unidad" : "unidades"}`}
              </dd>
            </div>
            {tienda && (
              <>
                <div><dt>Tienda</dt><dd>{tienda.nombre} · {tienda.ciudad}</dd></div>
                <div><dt>Retiro</dt><dd>{tienda.direccion} · {tienda.horario}</dd></div>
                <div><dt>Envío</dt><dd>{tienda.ofreceEnvio ? `${formatPrecio(tienda.costoEnvio)} · solo dentro de ${tienda.ciudad}` : "Solo retiro en la tienda"}</dd></div>
              </>
            )}
          </dl>

          <div className="dm-detail__price">
            <strong>{formatPrecio(producto.precio)}</strong>
            <span>COP · el IVA y el envío se calculan al pagar</span>
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
                onClick={() => setCantidad(Math.min(tope, cantidad + 1))}
                disabled={cantidad >= tope}
                aria-label="Aumentar cantidad"
              >
                +
              </button>
            </div>
          </div>

          <button className="dm-btn dm-btn--primary dm-btn--full" onClick={agregar} disabled={bloqueado}>
            {sinStock ? "Sin stock disponible" : excede ? `Solo quedan ${producto.stock}` : "Agregar al carrito"}
            {!bloqueado && <Icon name="arrow" size={18} />}
          </button>

          <a
            className="dm-btn dm-btn--secondary dm-btn--full dm-btn--whatsapp"
            href={whatsappUrl}
            target="_blank"
            rel="noopener noreferrer"
          >
            <Icon name="whatsapp" size={19} className="dm-btn__whatsapp" /> Cotizar por WhatsApp
          </a>

          {agregado && (
            <div className="dm-detail__msg" role="status">
              ¡Agregado al carrito!{" "}
              <button className="dm-detail__go" onClick={onIrCarrito}>
                Ir al carrito <Icon name="arrow" size={14} />
              </button>
            </div>
          )}
        </div>
      </div>

      <div className="dm-alternatives">
        <h3>El mismo repuesto en otras tiendas</h3>
        {otras.length === 0 ? (
          <p>Por ahora solo lo vende {tienda?.nombre}.</p>
        ) : (
          <>
            <p>Compara precio, ciudad y envío.</p>
            <div>
              {otras.map((a) => (
                <button key={a.id} onClick={() => onAbrirProducto(a)}>
                  {a.imagenUrl ? <img src={a.imagenUrl} alt="" /> : <span className="dm-alternatives__ph"><Icon name="box" size={26} /></span>}
                  <span>
                    <small>
                      {a.tienda.nombre} · {a.tienda.ciudad}
                      {a.tienda.ofreceEnvio ? ` · envío ${formatPrecio(a.tienda.costoEnvio)}` : " · solo retiro"}
                    </small>
                    <strong>{a.nombre}</strong>
                    <b>
                      {formatPrecio(a.precio)}
                      {a.precio !== producto.precio && (
                        <i className={`dm-diff dm-diff--${a.precio < producto.precio ? "menor" : "mayor"}`}>
                          {" "}({a.precio < producto.precio ? "−" : "+"}{formatPrecio(Math.abs(a.precio - producto.precio))})
                        </i>
                      )}
                    </b>
                  </span>
                  <Icon name="arrow" size={19} />
                </button>
              ))}
            </div>
          </>
        )}
      </div>
    </Modal>
  );
}
