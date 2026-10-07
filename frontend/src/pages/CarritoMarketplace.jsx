import { getUser } from "../services/authService";
import { formatPrecio } from "../components/servicio/estadosServicio";
import { AvisoCompatibilidad } from "../components/marketplace/EtiquetasProducto";
import { textoDisponibilidad } from "../components/marketplace/disponibilidad";
import "../css/marketplace.css";

/** Agrupa por tienda conservando el orden en que se agregaron. */
function porTienda(items) {
  const grupos = new Map();
  items.forEach((x) => {
    const id = x.producto.tienda.id;
    if (!grupos.has(id)) grupos.set(id, { tienda: x.producto.tienda, items: [] });
    grupos.get(id).items.push(x);
  });
  return [...grupos.values()];
}

function Linea({ item, onCambiar, onQuitar, onVer }) {
  const p = item.producto;
  return (
    <li className="mk-linea">
      <button className="mk-linea__imagen" onClick={() => onVer(p)} aria-label={`Ver ${p.nombre}`}>
        {p.imagenUrl ? <img src={p.imagenUrl} alt="" loading="lazy" /> : <span aria-hidden="true">Sin foto</span>}
      </button>
      <div className="mk-linea__info">
        <button className="mk-enlace" onClick={() => onVer(p)}>{p.nombre}</button>
        <span className="mk-tarjeta__meta">{[p.marca, p.referencia].filter(Boolean).join(" · ")} · {textoDisponibilidad(p)}</span>
        <AvisoCompatibilidad producto={p} />
      </div>
      <div className="mk-linea__cantidad">
        <label htmlFor={`mk-cant-${p.id}`} className="mk-sr">Cantidad de {p.nombre}</label>
        <input id={`mk-cant-${p.id}`} type="number" min="1" max="99" value={item.cantidad}
          onChange={(e) => onCambiar(p.id, e.target.value)} />
        <button className="mk-enlace mk-enlace--peligro" onClick={() => onQuitar(p.id)}>Quitar</button>
      </div>
      <strong className="mk-linea__precio">{formatPrecio(p.precio * item.cantidad)}</strong>
    </li>
  );
}

/**
 * Carrito del catálogo marketplace. Los productos de las tiendas se pagan juntos (un solo pago, un
 * pedido por tienda); los de "Vendido por DriveMaster" se pagan por la compra normal. Si hay de los
 * dos, se avisa y cada grupo se paga por separado sin perder el otro.
 */
export default function CarritoMarketplace({
  header, carrito, onVolver, onVerProducto, onContinuarCatalogo, onPagarPlataforma, onIrLogin,
  pendientesCompraNormal = 0, onIrCompraNormal,
}) {
  const user = getUser();
  const esCliente = user?.rol === "CLIENTE";
  const { catalogo, plataforma, mezclados } = carrito;
  const vacio = catalogo.length === 0 && plataforma.length === 0;

  const accionPago = (texto, onPagar) => {
    if (!user) return <button className="dm-btn dm-btn--primary" onClick={onIrLogin}>Inicia sesión para pagar</button>;
    if (!esCliente) return <p className="mk-nota">Para comprar entra con una cuenta de cliente.</p>;
    return <button className="dm-btn dm-btn--primary" onClick={onPagar}>{texto}</button>;
  };

  return (
    <div className="mk-pagina">
      {header}
      <main className="dm-container mk-contenido">
        <button className="mk-enlace mk-volver" onClick={onVolver}>← Seguir comprando</button>
        <div className="mk-titulo"><h1>Tu carrito</h1></div>

        {vacio && (
          <div className="mk-vacio">
            <p>Tu carrito está vacío.</p>
            <button className="dm-btn dm-btn--primary" onClick={onVolver}>Ver repuestos de las tiendas</button>
          </div>
        )}

        {mezclados && (
          <div className="mk-aviso mk-aviso--aviso" role="status">
            <strong>Estos productos se pagan por separado.</strong>{" "}
            Los de las tiendas se pagan con un solo pago; los «Vendido por DriveMaster» van por la compra normal.
            No pierdes nada: paga un grupo y el otro se queda aquí.
          </div>
        )}

        {pendientesCompraNormal > 0 && (
          <div className="mk-aviso mk-aviso--aviso" role="status">
            Tienes {pendientesCompraNormal === 1 ? "un producto guardado" : `${pendientesCompraNormal} productos guardados`} de una compra
            anterior en la <strong>compra normal</strong> de DriveMaster.{" "}
            {onIrCompraNormal && <button className="mk-enlace" onClick={onIrCompraNormal}>Continuar la compra normal</button>}
          </div>
        )}

        {catalogo.length > 0 && (
          <section className="mk-grupo" aria-labelledby="mk-grupo-tiendas">
            <h2 id="mk-grupo-tiendas">Productos de las tiendas</h2>
            {porTienda(catalogo).map((g) => (
              <div key={g.tienda.id} className="mk-grupo__tienda">
                <h3>{g.tienda.nombre} <span className="mk-tarjeta__meta">· {g.tienda.ciudad}</span></h3>
                <ul className="mk-lineas">
                  {g.items.map((x) => (
                    <Linea key={x.producto.id} item={x} onCambiar={carrito.cambiarCantidad} onQuitar={carrito.quitar} onVer={onVerProducto} />
                  ))}
                </ul>
              </div>
            ))}
            <div className="mk-grupo__pie">
              <p>Subtotal <strong>{formatPrecio(carrito.subtotalCatalogo)}</strong>
                <span className="mk-tarjeta__meta"> · el IVA y el envío o retiro se calculan en el siguiente paso</span></p>
              {onContinuarCatalogo && accionPago("Continuar con el pago", onContinuarCatalogo)}
            </div>
          </section>
        )}

        {plataforma.length > 0 && (
          <section className="mk-grupo" aria-labelledby="mk-grupo-driveMaster">
            <h2 id="mk-grupo-driveMaster">Vendido por DriveMaster</h2>
            <ul className="mk-lineas">
              {plataforma.map((x) => (
                <Linea key={x.producto.id} item={x} onCambiar={carrito.cambiarCantidad} onQuitar={carrito.quitar} onVer={onVerProducto} />
              ))}
            </ul>
            <div className="mk-grupo__pie">
              <p>Subtotal <strong>{formatPrecio(carrito.subtotalPlataforma)}</strong>
                <span className="mk-tarjeta__meta"> · se paga por la compra normal de DriveMaster</span></p>
              {accionPago("Pagar productos de DriveMaster", () => onPagarPlataforma(plataforma))}
            </div>
          </section>
        )}

        {!vacio && <p className="mk-nota">Los precios se confirman al pagar. No reservamos el stock en el carrito: se descuenta cuando el pago se aprueba.</p>}
      </main>
    </div>
  );
}
