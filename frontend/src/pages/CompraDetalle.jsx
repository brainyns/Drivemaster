import { useCallback, useEffect, useState } from "react";
import { obtenerCompra, descargarPdfCompra, recibirCompra, cancelarCompra } from "../services/compraService";
import { obtenerProveedor } from "../services/proveedorService";
import { listarProductosMiTienda } from "../services/productoService";
import { formatPrecio } from "../components/servicio/estadosServicio";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Dato from "../components/ui/Dato";
import Alerta from "../components/ui/Alerta";
import Esqueleto from "../components/ui/Esqueleto";
import ImagenProducto from "../components/marketplace/ImagenProducto";
import Icon from "../components/public/Icon";
import ChipEstado from "../components/ui/ChipEstado";
import ConfirmarAccion from "../components/marketplace/ConfirmarAccion";
import { avisar } from "../components/ui/avisar";
import { ESTADOS_COMPRA, estadoCompra, etiquetaMetodoPago, refCompra, unidades } from "./tienda/comprasTienda";
import { recibePdf } from "./tienda/proveedoresTienda";
import { plural } from "../utils/formato";
import "../css/compras-tienda.css";

const fechaHora = (f) => (f
  ? new Date(f).toLocaleString("es-CO", { day: "2-digit", month: "long", year: "numeric", hour: "2-digit", minute: "2-digit" })
  : "—");

/**
 * Una compra registrada: qué se compró, a quién, su estado y su PDF. Las compras no se editan; una pendiente se marca
 * como recibida (recién ahí se suma el stock) o se cancela (no toca el stock).
 */
function CompraDetalle({ id, onVolver, token }) {
  const [compra, setCompra] = useState(null);
  const [proveedor, setProveedor] = useState(null);
  const [imagenes, setImagenes] = useState({});
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  const [descargando, setDescargando] = useState(false);
  const [errorPdf, setErrorPdf] = useState(null);
  const [confirmar, setConfirmar] = useState(null); // "RECIBIR" | "CANCELAR"

  const cargar = useCallback(async () => {
    try {
      const c = await obtenerCompra(id, token);
      setCompra(c);
      setError(null);
      // La compra guarda solo el id del proveedor; si ya no existe, se muestra sin sus datos
      obtenerProveedor(c.proveedorId, token).then(setProveedor).catch(() => setProveedor(null));
    } catch (e) {
      setError(e.message);
    }
  }, [id, token]);

  useEffect(() => {
    let vivo = true;
    listarProductosMiTienda(token)
      .then((l) => { if (vivo) setImagenes(Object.fromEntries((l || []).map((p) => [p.id, p.imagenUrl]))); })
      .catch(() => {});
    const t = setTimeout(cargar, 0);
    return () => { vivo = false; clearTimeout(t); };
  }, [token, cargar]);

  const descargar = async () => {
    setDescargando(true);
    setErrorPdf(null);
    try {
      await descargarPdfCompra(id, token);
    } catch (e) {
      setErrorPdf(`No pudimos generar el PDF. ${e.message}`);
    } finally {
      setDescargando(false);
    }
  };

  if (!compra) {
    return (
      <div className="ss-panel--tienda cmp-root">
        <EncabezadoPagina titulo="Compra" onVolver={onVolver} textoVolver="Compras" />
        {error ? (
          <Alerta reintentando={reintentando} onReintentar={async () => { setReintentando(true); await cargar(); setReintentando(false); }}>
            No pudimos cargar la compra. {error}
          </Alerta>
        ) : <Esqueleto variante="lista" cantidad={3} texto="Cargando la compra…" />}
      </div>
    );
  }

  const productos = compra.productos || [];
  const subtotal = productos.reduce((s, p) => s + (p.subtotal || 0), 0);
  const total = compra.total ?? subtotal;
  const totalUnidades = unidades(compra);
  const estado = estadoCompra(compra);

  const recibir = async () => {
    setCompra(await recibirCompra(compra.id, token));
    setConfirmar(null);
    avisar(`Compra recibida: se sumaron ${plural(totalUnidades, "unidad", "unidades")} al stock.`);
  };
  const cancelar = async () => {
    setCompra(await cancelarCompra(compra.id, null, token));
    setConfirmar(null);
    avisar("Compra cancelada. El stock no cambió.");
  };

  return (
    <div className="ss-panel--tienda cmp-root">
      <EncabezadoPagina titulo={`Compra ${refCompra(compra.id)}`} onVolver={onVolver} textoVolver="Compras"
        acciones={(
          <>
            <button type="button" className="ui-btn ui-btn--secundario" onClick={descargar} disabled={descargando} aria-busy={descargando}>
              {!descargando && <Icon name="file" size={16} />}{descargando ? "Generando el PDF…" : "Descargar PDF"}
            </button>
            {estado === "PENDIENTE" && (
              <>
                <button type="button" className="ui-btn ui-btn--texto" onClick={() => setConfirmar("CANCELAR")}>Cancelar compra</button>
                <button type="button" className="ui-btn ui-btn--primario" onClick={() => setConfirmar("RECIBIR")}>
                  <Icon name="check" size={16} /> Marcar como recibida
                </button>
              </>
            )}
          </>
        )}>
        <p className="cmp-meta">
          <ChipEstado estado={estado} catalogo={ESTADOS_COMPRA} />
          <span>{fechaHora(compra.fecha)}</span>
          {proveedor?.nombre && <span>{proveedor.nombre}</span>}
          <span>{totalUnidades} {totalUnidades === 1 ? "unidad" : "unidades"}</span>
        </p>
      </EncabezadoPagina>

      {errorPdf && <Alerta className="cmp-alerta" onCerrar={() => setErrorPdf(null)}>{errorPdf}</Alerta>}

      <div className="cmp-form">
        <section className="ui-card ui-table-card cmp-detalle" aria-labelledby="cmp-productos-titulo">
          <div className="ui-card__header">
            <h2 id="cmp-productos-titulo" className="ui-card__titulo">Productos</h2>
            <span className="ui-card__sub">{productos.length} {productos.length === 1 ? "producto" : "productos"}</span>
          </div>
          <div className="ui-table-wrap">
            <table className="ui-table ui-table--tarjetas">
              <thead>
                <tr>
                  <th scope="col">Producto</th>
                  <th scope="col" className="ui-table__num">Cantidad</th>
                  <th scope="col" className="ui-table__num">Costo por unidad</th>
                  <th scope="col" className="ui-table__num">Subtotal</th>
                </tr>
              </thead>
              <tbody>
                {productos.map((p, i) => (
                  <tr key={`${p.productoId}-${i}`}>
                    <td data-label="Producto" className="ui-table__titulo">
                      <span className="cmp-linea">
                        <ImagenProducto src={imagenes[p.productoId]} className="cmp-item__imagen" tamanioIcono={16} />
                        <strong>{p.nombre}</strong>
                      </span>
                    </td>
                    <td data-label="Cantidad" className="ui-table__num">{p.cantidad}</td>
                    <td data-label="Costo por unidad" className="ui-table__num">{formatPrecio(p.costo)}</td>
                    <td data-label="Subtotal" className="ui-table__num"><span className="ui-precio">{formatPrecio(p.subtotal)}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="cmp-total cmp-total--detalle"><span>Total de la compra</span><strong className="ui-precio">{formatPrecio(total)}</strong></div>
        </section>

        <aside className="cmp-form__lateral cmp-lateral">
          <section className="ui-card" aria-labelledby="cmp-proveedor-titulo">
            <div className="ui-card__header"><h2 id="cmp-proveedor-titulo" className="ui-card__titulo">Proveedor</h2></div>
            <div className="ui-card__cuerpo">
              {proveedor ? (
                <dl className="ui-datos cmp-datos">
                  <Dato etiqueta="Nombre">{proveedor.nombre}</Dato>
                  <Dato etiqueta="NIT">{proveedor.nit}</Dato>
                  <Dato etiqueta="Teléfono">{proveedor.telefono}</Dato>
                  <Dato etiqueta="Correo">{proveedor.correo}</Dato>
                  <Dato etiqueta="Método de pago">{etiquetaMetodoPago(compra.metodoPago)}</Dato>
                  <Dato etiqueta="Factura del proveedor">{compra.numeroFactura}</Dato>
                </dl>
              ) : (
                <p className="cmp-tenue cmp-sin-resultados">Este proveedor ya no está en tu lista.</p>
              )}
            </div>
          </section>
          <section className="ui-card" aria-labelledby="cmp-efecto-titulo">
            <div className="ui-card__header"><h2 id="cmp-efecto-titulo" className="ui-card__titulo">Qué hizo esta compra</h2></div>
            <div className="ui-card__cuerpo">
              <ul className="cmp-efectos">
                {estado === "RECIBIDA" && (
                  <li><Icon name="check" size={16} /> Sumó {plural(totalUnidades, "unidad", "unidades")} al stock de tus productos
                    {compra.fechaRecepcion ? ` el ${fechaHora(compra.fechaRecepcion)}` : ""}.</li>
                )}
                {estado === "PENDIENTE" && (
                  <li><Icon name="clock" size={16} /> Aún no suma el stock: lo hará cuando la marques como recibida.</li>
                )}
                {estado === "CANCELADA" && (
                  <li><Icon name="close" size={16} /> Se canceló{compra.fechaCancelacion ? ` el ${fechaHora(compra.fechaCancelacion)}` : ""} y no tocó el stock
                    {compra.motivoCancelacion ? `: ${compra.motivoCancelacion}` : ""}.</li>
                )}
                <li>
                  <Icon name={recibePdf(proveedor) ? "mail" : "alert"} size={16} />
                  {recibePdf(proveedor)
                    ? `Al registrarla le enviamos el PDF a ${proveedor.correo}.`
                    : "Al proveedor no se le envió el PDF (sin correo o con el envío apagado). Puedes descargarlo arriba."}
                </li>
              </ul>
            </div>
          </section>
        </aside>
      </div>

      {confirmar === "RECIBIR" && (
        <ConfirmarAccion titulo="¿La mercancía ya llegó?" textoConfirmar="Marcar como recibida"
          mensaje={`Se suman ${plural(totalUnidades, "unidad", "unidades")} al stock de tus productos. Esto no se puede deshacer.`}
          onConfirmar={recibir} onCancelar={() => setConfirmar(null)} />
      )}
      {confirmar === "CANCELAR" && (
        <ConfirmarAccion titulo={`¿Cancelar la compra ${refCompra(compra.id)}?`} peligro textoConfirmar="Cancelar compra"
          mensaje="La compra queda cancelada y el stock no cambia. Esto no se puede deshacer."
          onConfirmar={cancelar} onCancelar={() => setConfirmar(null)} />
      )}
    </div>
  );
}

export default CompraDetalle;
