import { useEffect, useMemo, useState } from "react";
import { crearCompra } from "../services/compraService";
import { listarProveedores } from "../services/proveedorService";
import { listarProductosMiTienda } from "../services/productoService";
import { formatPrecio } from "../components/servicio/estadosServicio";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import SeccionEditable from "../components/ui/SeccionEditable";
import Campo from "../components/ui/Campo";
import Dato from "../components/ui/Dato";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import BarraAccionMovil from "../components/ui/BarraAccionMovil";
import ImagenProducto from "../components/marketplace/ImagenProducto";
import ConfirmarAccion from "../components/marketplace/ConfirmarAccion";
import Icon from "../components/public/Icon";
import { METODOS_PAGO, agregarLinea, erroresCompra, erroresLinea } from "./tienda/comprasTienda";
import { recibePdf } from "./tienda/proveedoresTienda";
import { avisar } from "../components/ui/avisar";
import { plural } from "../utils/formato";
import "../css/compras-tienda.css";

const MAX_RESULTADOS = 6;
const LINEA_VACIA = { producto: null, cantidad: "1", costo: "" };

/**
 * Registrar una compra a un proveedor: recibida (llegó con la compra: se suma el stock al confirmar) o pendiente (se suma
 * al marcarla recibida desde su detalle). Si el proveedor tiene correo y no apagó el envío, le llega el PDF.
 */
function CompraForm({ onVolver, token }) {
  const [proveedores, setProveedores] = useState(null);
  const [productos, setProductos] = useState([]);
  const [proveedorId, setProveedorId] = useState("");
  const [buscar, setBuscar] = useState("");
  const [linea, setLinea] = useState(LINEA_VACIA);
  const [tocadaLinea, setTocadaLinea] = useState(false);
  const [items, setItems] = useState([]);
  const [tocadaCompra, setTocadaCompra] = useState(false);
  const [confirmando, setConfirmando] = useState(false);
  // RECIBIDA (por defecto, como antes de los estados) o PENDIENTE; pago y factura son opcionales
  const [datos, setDatos] = useState({ estado: "RECIBIDA", metodoPago: "", numeroFactura: "" });
  const [erroresServidor, setErroresServidor] = useState({});

  useEffect(() => {
    let vivo = true;
    listarProveedores(token).then((l) => { if (vivo) setProveedores(l || []); }).catch(() => { if (vivo) setProveedores([]); });
    // Solo los productos de la tienda: no se puede comprar (ni subir el stock de) un producto de otra
    listarProductosMiTienda(token).then((l) => { if (vivo) setProductos(l || []); }).catch(() => {});
    return () => { vivo = false; };
  }, [token]);

  const proveedor = proveedores?.find((p) => p.id === proveedorId);
  const resultados = useMemo(() => {
    const q = buscar.trim().toLowerCase();
    if (!q || linea.producto) return [];
    return productos.filter((p) => [p.nombre, p.codigo, p.marca].some((x) => x && x.toLowerCase().includes(q))).slice(0, MAX_RESULTADOS);
  }, [buscar, productos, linea.producto]);
  const erroresL = tocadaLinea ? erroresLinea(linea) : {};
  const erroresC = tocadaCompra ? erroresCompra({ proveedorId, items }) : {};
  const total = items.reduce((s, i) => s + i.subtotal, 0);
  const unidades = items.reduce((s, i) => s + i.cantidad, 0);
  const recibida = datos.estado === "RECIBIDA";
  const efectoStock = recibida
    ? `Se suman ${plural(unidades, "unidad", "unidades")} al stock`
    : `Queda pendiente: el stock se suma cuando la marques como recibida`;
  const cambiarDato = (campo, valor) => {
    setDatos((d) => ({ ...d, [campo]: valor }));
    setErroresServidor((prev) => ({ ...prev, [campo]: undefined }));
  };

  const elegir = (p) => {
    setLinea({ producto: p, cantidad: "1", costo: p.precioCompra != null ? String(p.precioCompra) : "" });
    setBuscar(p.nombre);
    setTocadaLinea(false);
    requestAnimationFrame(() => document.getElementById("cmp-cantidad")?.focus());
  };

  const agregar = (e) => {
    e.preventDefault();
    setTocadaLinea(true);
    if (Object.keys(erroresLinea(linea)).length) return;
    setItems((prev) => agregarLinea(prev, linea));
    setLinea(LINEA_VACIA);
    setBuscar("");
    setTocadaLinea(false);
    document.getElementById("cmp-buscar")?.focus();
  };

  const pedirConfirmacion = () => {
    setTocadaCompra(true);
    if (Object.keys(erroresCompra({ proveedorId, items })).length) return;
    setConfirmando(true);
  };

  const registrar = async () => {
    try {
      await crearCompra({
        proveedorId,
        estado: datos.estado,
        metodoPago: datos.metodoPago || null,
        numeroFactura: datos.numeroFactura.trim() || null,
        productos: items.map((i) => ({ productoId: i.productoId, cantidad: i.cantidad, costo: i.costo })),
      }, token);
    } catch (e) {
      // Un error de un campo (pago o factura) va bajo ese campo; los ítems y los datos se conservan
      if (e.campos) { setErroresServidor(e.campos); setConfirmando(false); return; }
      throw e; // ConfirmarAccion lo muestra en el diálogo
    }
    const pdf = recibePdf(proveedor) ? ` y enviamos el PDF a ${proveedor.correo}` : "";
    avisar(recibida
      ? `Compra registrada: se sumaron ${plural(unidades, "unidad", "unidades")} al stock${pdf}.`
      : `Compra registrada como pendiente${pdf}. Cuando llegue la mercancía, márcala como recibida.`);
    onVolver();
  };

  const botonRegistrar = (clase = "") => (
    <button type="button" className={`ui-btn ui-btn--primario ${clase}`} onClick={pedirConfirmacion}>
      Registrar compra
    </button>
  );

  return (
    <div className="ss-panel--tienda cmp-root">
      <EncabezadoPagina titulo="Nueva compra" onVolver={onVolver} textoVolver="Compras"
        descripcion="Elige el proveedor y agrega los productos. Si ya llegó, el stock se suma al registrarla; si no, regístrala como pendiente." />

      <div className="cmp-form">
        <div className="cmp-form__principal">
          <SeccionEditable id="cmp-proveedor" icono="users" titulo="Proveedor">
            {proveedores && proveedores.length === 0 ? (
              <Alerta tono="aviso">Aún no tienes proveedores. Agrégalos en Proveedores y vuelve a registrar la compra.</Alerta>
            ) : (
              <div className="ui-form">
                <Campo id="cmp-proveedor-select" label="¿A quién le compraste?" error={erroresC.proveedor}>
                  <select className="ui-field__input" value={proveedorId} disabled={!proveedores} onChange={(e) => {
                    const elegido = (proveedores || []).find((p) => p.id === e.target.value);
                    setProveedorId(e.target.value);
                    if (elegido?.condicionPago === "CREDITO" && !datos.metodoPago) cambiarDato("metodoPago", "CREDITO");
                  }}>
                    <option value="">{proveedores ? "Elegir proveedor" : "Cargando proveedores…"}</option>
                    {(proveedores || []).map((p) => <option key={p.id} value={p.id}>{p.nombre}{p.nit ? ` · NIT ${p.nit}` : ""}</option>)}
                  </select>
                </Campo>
                {proveedor && (
                  <>
                    <dl className="ui-datos">
                      <Dato etiqueta="NIT">{proveedor.nit}</Dato>
                      <Dato etiqueta="Teléfono">{proveedor.telefono}</Dato>
                      <Dato etiqueta="Correo">{proveedor.correo}</Dato>
                    </dl>
                    {!recibePdf(proveedor) && (
                      <Alerta tono="info">
                        {proveedor.correo
                          ? "Apagaste el envío del PDF a este proveedor: la compra se registra igual, pero no le enviaremos el PDF."
                          : "Este proveedor no tiene correo: la compra se registra igual, pero no le enviaremos el PDF."}
                      </Alerta>
                    )}
                  </>
                )}
              </div>
            )}
          </SeccionEditable>

          <SeccionEditable id="cmp-estado" icono="clock" titulo="Estado y pago"
            descripcion="Si la mercancía aún no llega, regístrala como pendiente: el stock se suma cuando la recibas.">
            <div className="ui-form">
              <fieldset className="cmp-estados">
                <legend className="ui-sr">Estado de la compra</legend>
                {[["RECIBIDA", "Recibida", "La mercancía llegó: se suma al stock al registrarla."],
                  ["PENDIENTE", "Pendiente", "Aún no llega: el stock se suma al marcarla recibida."]].map(([valor, texto, ayuda]) => (
                  <label key={valor} className={`ui-opcion cmp-estado${datos.estado === valor ? " ui-opcion--elegida" : ""}`}>
                    <input type="radio" className="ui-sr" name="estado" value={valor} checked={datos.estado === valor}
                      onChange={() => cambiarDato("estado", valor)} />
                    <span><strong>{texto}</strong><span>{ayuda}</span></span>
                  </label>
                ))}
              </fieldset>
              {erroresServidor.estado && <p className="ui-field__error" role="alert"><Icon name="alert" size={14} />{erroresServidor.estado}</p>}
              <div className="ui-form__campos ui-form__campos--2">
                <Campo id="cmp-metodo" label="Método de pago" opcional error={erroresServidor.metodoPago}>
                  <select className="ui-field__input" value={datos.metodoPago} onChange={(e) => cambiarDato("metodoPago", e.target.value)}>
                    <option value="">Sin indicar</option>
                    {METODOS_PAGO.map(([v, t]) => <option key={v} value={v}>{t}</option>)}
                  </select>
                </Campo>
                <Campo id="cmp-factura" label="Factura del proveedor" opcional error={erroresServidor.numeroFactura}
                  ayuda="El número que trae su factura, para encontrarla después.">
                  <input className="ui-field__input" value={datos.numeroFactura} maxLength={40} placeholder="Ej. FV-10234"
                    onChange={(e) => cambiarDato("numeroFactura", e.target.value)} />
                </Campo>
              </div>
            </div>
          </SeccionEditable>

          <SeccionEditable id="cmp-productos" icono="box" titulo="Productos"
            descripcion="Busca en tu catálogo, elige el producto, la cantidad y el costo por unidad.">
            <form className="ui-form" onSubmit={agregar} noValidate>
              <Campo id="cmp-buscar" label="Producto" error={erroresL.producto}
                ayuda={linea.producto ? `Stock actual: ${linea.producto.stockActual ?? 0}` : "Por nombre, código o marca."}>
                <input className="ui-field__input" type="search" value={buscar} autoComplete="off" placeholder="Ej. filtro de aceite"
                  onChange={(e) => { setBuscar(e.target.value); if (linea.producto) setLinea(LINEA_VACIA); }} />
              </Campo>
              {resultados.length > 0 && (
                <ul className="cmp-resultados" aria-label="Productos que coinciden">
                  {resultados.map((p) => (
                    <li key={p.id}>
                      <button type="button" className="cmp-resultado" onClick={() => elegir(p)}>
                        <ImagenProducto src={p.imagenUrl} className="cmp-resultado__imagen" tamanioIcono={16} />
                        <span className="cmp-resultado__texto">
                          <strong>{p.nombre}</strong>
                          <span>{[p.codigo, `stock ${p.stockActual ?? 0}`].filter(Boolean).join(" · ")}</span>
                        </span>
                        <span className="cmp-tenue">{p.precioCompra != null ? formatPrecio(p.precioCompra) : ""}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              {buscar.trim() && !linea.producto && resultados.length === 0 && (
                <p className="cmp-tenue cmp-sin-resultados">Ningún producto de tu catálogo coincide con “{buscar.trim()}”.</p>
              )}
              <div className="ui-form__campos ui-form__campos--2">
                <Campo id="cmp-cantidad" label="Cantidad" error={erroresL.cantidad}>
                  <input className="ui-field__input" type="number" min="1" inputMode="numeric" value={linea.cantidad}
                    onChange={(e) => setLinea((l) => ({ ...l, cantidad: e.target.value }))} />
                </Campo>
                <Campo id="cmp-costo" label="Costo por unidad (COP)" error={erroresL.costo}>
                  <input className="ui-field__input" type="number" min="0" inputMode="numeric" value={linea.costo}
                    onChange={(e) => setLinea((l) => ({ ...l, costo: e.target.value }))} />
                </Campo>
              </div>
              <button type="submit" className="ui-btn ui-btn--secundario cmp-agregar"><Icon name="plus" size={16} /> Agregar a la compra</button>
            </form>
          </SeccionEditable>
        </div>

        <aside className="cmp-form__lateral" aria-labelledby="cmp-resumen-titulo">
          <section className="ui-card">
            <div className="ui-card__header">
              <h2 id="cmp-resumen-titulo" className="ui-card__titulo">Resumen</h2>
              {items.length > 0 && <span className="ui-card__sub">{unidades} {unidades === 1 ? "unidad" : "unidades"}</span>}
            </div>
            <div className="ui-card__cuerpo cmp-resumen">
              {items.length === 0 ? (
                <EstadoVacio icono="box" nivel={3} titulo="Aún no hay productos" texto="Lo que agregues aparece aquí con su subtotal." />
              ) : (
                <ul className="cmp-items">
                  {items.map((i) => (
                    <li key={i.productoId} className="cmp-item">
                      <ImagenProducto src={i.imagenUrl} className="cmp-item__imagen" tamanioIcono={16} />
                      <span className="cmp-item__texto">
                        <strong>{i.nombre}</strong>
                        <span>{i.cantidad} × {formatPrecio(i.costo)}</span>
                      </span>
                      <span className="ui-precio">{formatPrecio(i.subtotal)}</span>
                      <button type="button" className="ui-btn ui-btn--texto ui-btn--chico cmp-item__quitar"
                        onClick={() => setItems((prev) => prev.filter((x) => x.productoId !== i.productoId))}
                        aria-label={`Quitar ${i.nombre} de la compra`}>
                        <Icon name="close" size={14} />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              {erroresC.items && <p className="ui-field__error" role="alert"><Icon name="alert" size={14} />{erroresC.items}</p>}
              <div className="cmp-total"><span>Total</span><strong className="ui-precio">{formatPrecio(total)}</strong></div>
              {botonRegistrar("ui-btn--grande ui-btn--bloque cmp-registrar")}
              <p className="cmp-nota">
                <Icon name="check" size={14} /> {recibida ? "Al registrarla se suma el stock de cada producto" : "Queda pendiente: el stock se suma al recibirla"}{recibePdf(proveedor) ? ` y enviamos el PDF a ${proveedor.correo}` : ""}.
              </p>
            </div>
          </section>
        </aside>
      </div>

      <BarraAccionMovil etiqueta="Total de la compra" valor={formatPrecio(total)}
        nota={items.length ? `${unidades} ${unidades === 1 ? "unidad" : "unidades"}` : "Sin productos"}>
        {botonRegistrar()}
      </BarraAccionMovil>

      {confirmando && (
        <ConfirmarAccion titulo={`¿Registrar la compra a ${proveedor?.nombre || "este proveedor"}?`} textoConfirmar="Registrar compra"
          mensaje={`${efectoStock} (total ${formatPrecio(total)})${recibePdf(proveedor) ? ` y enviamos el PDF a ${proveedor.correo}` : ""}. Una compra registrada no se puede editar.`}
          onConfirmar={registrar} onCancelar={() => setConfirmando(false)} />
      )}
    </div>
  );
}

export default CompraForm;
