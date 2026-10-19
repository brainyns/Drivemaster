import { useEffect, useState } from "react";
import { getUser } from "../services/authService";
import { detalleProducto } from "../services/catalogoService";
import { formatPrecio } from "../components/servicio/estadosServicio";
import { AvisoCompatibilidad, EtiquetaCompatibilidad, EtiquetaVendedor } from "../components/marketplace/EtiquetasProducto";
import { textoDisponibilidad } from "../components/marketplace/disponibilidad";
import { limitarCantidad } from "../hooks/useCarrito";
import ImagenProducto from "../components/marketplace/ImagenProducto";
import TiendaVendedor from "../components/marketplace/TiendaVendedor";
import "../css/marketplace.css";

const CLAVE_VEHICULO = "marketplaceVehiculoId";

/**
 * Detalle de un producto del catálogo marketplace, con su tienda (dirección, horario y envío) y el
 * mismo repuesto en otras tiendas para comparar precio y disponibilidad.
 */
export default function DetalleProductoMarketplace({ header, productoId, onVolver, onVerProducto, onAgregar, onIrCarrito }) {
  const esCliente = getUser()?.rol === "CLIENTE";
  // El componente se monta con key={productoId}: al cambiar de producto empieza limpio
  const [respuesta, setRespuesta] = useState({ producto: null, error: null });
  const { producto, error } = respuesta;
  const [cantidad, setCantidad] = useState(1);
  const [agregado, setAgregado] = useState(false);

  useEffect(() => {
    let vivo = true;
    let vehiculoId = "";
    try { vehiculoId = esCliente ? localStorage.getItem(CLAVE_VEHICULO) || "" : ""; } catch { /* sin almacenamiento */ }
    detalleProducto(productoId, vehiculoId)
      .then((p) => { if (vivo) setRespuesta({ producto: p, error: null }); })
      .catch((e) => { if (vivo) setRespuesta({ producto: null, error: e.message }); });
    return () => { vivo = false; };
  }, [productoId, esCliente]);

  if (error) {
    return (
      <div className="dm-page mk-pagina">
        {header}
        <main className="dm-container mk-contenido">
          <p className="mk-aviso mk-aviso--error" role="alert">{error}</p>
          <button className="dm-btn dm-btn--secondary" onClick={onVolver}>Volver al catálogo</button>
        </main>
      </div>
    );
  }
  if (!producto) return <div className="dm-page mk-pagina">{header}<main className="dm-container mk-contenido"><p className="mk-conteo">Cargando...</p></main></div>;

  const t = producto.tienda;
  const agotado = !producto.disponible;
  const baratas = (producto.otrasTiendas || []).filter((o) => o.precio < producto.precio);

  const agregar = () => {
    onAgregar(producto, limitarCantidad(cantidad, producto));
    setAgregado(true);
  };

  return (
    <div className="dm-page mk-pagina">
      {header}
      <main className="dm-container mk-contenido">
        <button className="mk-enlace mk-volver" onClick={onVolver}>← Volver al catálogo</button>

        <section className="mk-detalle">
          <div className="mk-detalle__imagen">
            <ImagenProducto src={producto.imagenUrl} alt={producto.nombre} tamanioIcono={56} />
          </div>

          <div className="mk-detalle__info">
            <div className="mk-tarjeta__etiquetas">
              <EtiquetaVendedor producto={producto} />
              <EtiquetaCompatibilidad producto={producto} />
            </div>
            <h1>{producto.nombre}</h1>
            <p className="mk-tarjeta__meta">{[producto.marca, producto.categoria, producto.referencia && `Ref. ${producto.referencia}`].filter(Boolean).join(" · ")}</p>
            <AvisoCompatibilidad producto={producto} />

            <p className="mk-detalle__precio">{formatPrecio(producto.precio)}</p>
            <p className="mk-tarjeta__meta">{textoDisponibilidad(producto)} · el IVA y el envío se calculan al pagar</p>

            <div className="mk-detalle__compra">
              <div className="mk-campo mk-campo--cantidad">
                <label htmlFor="mk-cantidad">Cantidad</label>
                <input id="mk-cantidad" type="number" min="1" max="99" value={cantidad} disabled={agotado}
                  onChange={(e) => setCantidad(e.target.value)} onBlur={() => setCantidad(limitarCantidad(cantidad, producto))} />
              </div>
              <button className="dm-btn dm-btn--primary" onClick={agregar} disabled={agotado}>
                {agotado ? "Agotado" : "Agregar al carrito"}
              </button>
            </div>
            {agregado && (
              <p className="mk-toast" role="status">
                Agregado al carrito. <button className="mk-enlace" onClick={onIrCarrito}>Ver carrito</button>
              </p>
            )}
          </div>
        </section>

        <TiendaVendedor tienda={t} />

        <section className="mk-comparar" aria-labelledby="mk-comparar-titulo">
          <h2 id="mk-comparar-titulo">El mismo repuesto en otras tiendas</h2>
          {(producto.otrasTiendas || []).length === 0 ? (
            <p className="mk-nota">Por ahora solo lo vende esta tienda.</p>
          ) : (
            <>
              {baratas.length > 0 && <p className="mk-nota">Hay {baratas.length === 1 ? "una tienda" : `${baratas.length} tiendas`} con menor precio.</p>}
              <div className="mk-tabla-scroll">
                <table className="mk-tabla">
                  <thead>
                    <tr><th>Tienda</th><th>Ciudad</th><th>Disponibilidad</th><th>Envío</th><th className="mk-tabla__num">Precio</th><th /></tr>
                  </thead>
                  <tbody>
                    {[producto, ...producto.otrasTiendas].sort((a, b) => a.precio - b.precio).map((o) => (
                      <tr key={o.id} className={o.id === producto.id ? "mk-tabla__actual" : ""}>
                        <td>{o.tienda.nombre}{o.id === producto.id && <span className="mk-etiqueta">Estás viendo esta</span>}</td>
                        <td>{o.tienda.ciudad}</td>
                        <td>{textoDisponibilidad(o)}</td>
                        <td>{o.tienda.ofreceEnvio ? formatPrecio(o.tienda.costoEnvio) : "Solo retiro"}</td>
                        <td className="mk-tabla__num">
                          {formatPrecio(o.precio)}
                          {o.precio !== producto.precio && (
                            <span className={`mk-diferencia mk-diferencia--${o.precio < producto.precio ? "menor" : "mayor"}`}>
                              {o.precio < producto.precio ? "−" : "+"}{formatPrecio(Math.abs(o.precio - producto.precio))}
                            </span>
                          )}
                        </td>
                        <td>{o.id !== producto.id && <button className="mk-enlace" onClick={() => onVerProducto(o)}>Ver</button>}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </section>
      </main>
    </div>
  );
}
