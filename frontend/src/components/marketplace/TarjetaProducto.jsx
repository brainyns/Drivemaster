import { formatPrecio } from "../servicio/estadosServicio";
import { EtiquetaCompatibilidad, EtiquetaVendedor } from "./EtiquetasProducto";
import { textoDisponibilidad } from "./disponibilidad";

/** Tarjeta de un producto del catálogo marketplace: tienda, ciudad, precio y disponibilidad. */
export default function TarjetaProducto({ producto, onVer, onAgregar }) {
  const agotado = !producto.disponible;
  return (
    <article className={`mk-tarjeta${agotado ? " mk-tarjeta--agotado" : ""}`}>
      <button className="mk-tarjeta__imagen" onClick={() => onVer(producto)} aria-label={`Ver ${producto.nombre}`}>
        {producto.imagenUrl
          ? <img src={producto.imagenUrl} alt="" loading="lazy" />
          : <span className="mk-tarjeta__sin-imagen" aria-hidden="true">Sin foto</span>}
      </button>

      <div className="mk-tarjeta__cuerpo">
        <div className="mk-tarjeta__etiquetas">
          <EtiquetaVendedor producto={producto} />
          <EtiquetaCompatibilidad producto={producto} />
        </div>
        <h3 className="mk-tarjeta__nombre">
          <button className="mk-enlace" onClick={() => onVer(producto)}>{producto.nombre}</button>
        </h3>
        <p className="mk-tarjeta__meta">
          {[producto.marca, producto.referencia].filter(Boolean).join(" · ") || "—"}
        </p>
        <p className="mk-tarjeta__meta">
          {producto.tienda?.nombre} · {producto.tienda?.ciudad}
        </p>
        {producto.avisoCompatibilidad && (
          <p className={`mk-tarjeta__aviso mk-tarjeta__aviso--${producto.compatibilidad === "NO_COMPATIBLE" ? "error" : "aviso"}`}>
            {producto.avisoCompatibilidad}
          </p>
        )}

        <div className="mk-tarjeta__pie">
          <div>
            <strong className="mk-tarjeta__precio">{formatPrecio(producto.precio)}</strong>
            <span className="mk-tarjeta__stock">{textoDisponibilidad(producto)}</span>
          </div>
          <button className="dm-btn dm-btn--primary mk-tarjeta__agregar" onClick={() => onAgregar(producto)} disabled={agotado}>
            {agotado ? "Agotado" : "Agregar"}
          </button>
        </div>
      </div>
    </article>
  );
}
