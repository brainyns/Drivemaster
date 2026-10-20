import { useEffect, useMemo, useState } from "react";
import { getToken, getUser } from "../services/authService";
import { cotizarCompra, iniciarPagoCompra, misCompras } from "../services/catalogoService";
import { formatPrecio } from "../components/servicio/estadosServicio";
import PagoLocal from "../components/servicio/PagoLocal";
import { guardarPagoPendiente } from "../components/marketplace/pagoPendiente";
import "../css/marketplace.css";
import "../css/cotizacion.css";
import Icon from "../components/public/Icon";

const CLAVE_VEHICULO = "marketplaceVehiculoId";

const normalizar = (s) => (s || "").normalize("NFD").replace(/[̀-ͯ]/g, "").trim().toLowerCase();

function porTienda(items) {
  const grupos = new Map();
  items.forEach((x) => {
    const id = x.producto.tienda.id;
    if (!grupos.has(id)) grupos.set(id, { tienda: x.producto.tienda, items: [] });
    grupos.get(id).items.push(x);
  });
  return [...grupos.values()];
}

/**
 * Pago de los productos de las tiendas: por cada tienda se elige envío (solo a su misma ciudad) o
 * retiro (siempre disponible, sin costo). El navegador manda solo producto, cantidad, método de
 * entrega y dirección; el precio, el IVA y el envío los calcula el backend (y se muestran tal cual).
 * Un solo pago de Wompi cubre todas las tiendas.
 */
export default function CheckoutMarketplace({ header, carrito, onVolverCarrito, onPagada }) {
  const token = getToken();
  const user = getUser();
  const grupos = useMemo(() => porTienda(carrito.catalogo), [carrito.catalogo]);

  const [entregas, setEntregas] = useState({});
  const [envio, setEnvio] = useState({ receptor: user?.nombre || "", telefono: "", direccion: "", ciudad: "", indicaciones: "" });
  const [respuesta, setRespuesta] = useState({ clave: null, datos: null, error: null });
  const [pagando, setPagando] = useState(false);
  const [errorPago, setErrorPago] = useState(null);
  const [pagoLocal, setPagoLocal] = useState(null);

  const tipoDe = (tiendaId) => entregas[tiendaId] || "RETIRO";
  const hayEnvio = grupos.some((g) => tipoDe(g.tienda.id) === "ENVIO");
  const envioCompleto = ["receptor", "telefono", "direccion", "ciudad"].every((k) => envio[k].trim());

  // Envío solo dentro de la ciudad de la tienda: se avisa apenas se escribe la ciudad
  const ciudadesNoCoinciden = hayEnvio && envio.ciudad.trim()
    ? grupos.filter((g) => tipoDe(g.tienda.id) === "ENVIO" && normalizar(g.tienda.ciudad) !== normalizar(envio.ciudad))
    : [];

  const solicitud = useMemo(() => {
    const conEnvio = grupos.some((g) => (entregas[g.tienda.id] || "RETIRO") === "ENVIO");
    let vehiculoId = "";
    try { vehiculoId = localStorage.getItem(CLAVE_VEHICULO) || ""; } catch { /* sin almacenamiento */ }
    return {
      items: carrito.catalogo.map((x) => ({ productoId: x.producto.id, cantidad: x.cantidad })),
      entregas: grupos.map((g) => ({ tiendaId: g.tienda.id, tipo: entregas[g.tienda.id] || "RETIRO" })),
      ...(conEnvio ? {
        envio: {
          receptor: envio.receptor.trim(), telefono: envio.telefono.trim(), direccion: envio.direccion.trim(),
          ciudad: envio.ciudad.trim(), indicaciones: envio.indicaciones.trim() || null,
        },
      } : {}),
      ...(vehiculoId ? { vehiculoId } : {}),
    };
  }, [carrito.catalogo, grupos, entregas, envio]);

  const puedeCotizar = grupos.length > 0 && (!hayEnvio || (envioCompleto && ciudadesNoCoinciden.length === 0));
  const clave = JSON.stringify(solicitud);

  // El backend recalcula todo (y revisa el stock de todas las tiendas) cada vez que algo cambia
  useEffect(() => {
    if (!puedeCotizar) return undefined;
    let vivo = true;
    const t = setTimeout(() => {
      cotizarCompra(token, solicitud)
        .then((datos) => { if (vivo) setRespuesta({ clave, datos, error: null }); })
        .catch((e) => { if (vivo) setRespuesta({ clave, datos: null, error: e.message }); });
    }, 400);
    return () => { vivo = false; clearTimeout(t); };
  }, [clave, puedeCotizar, solicitud, token]);

  const vigente = puedeCotizar && respuesta.clave === clave;
  const calculando = puedeCotizar && respuesta.clave !== clave;
  const cotizacion = vigente ? respuesta.datos : null;
  const errorCotizacion = vigente ? respuesta.error : null;

  // Verificado el pago en local: se vacía el carrito solo si la compra quedó PAGADA (Wompi pudo aprobar y no aplicarse)
  const alVerificar = async () => {
    let pagada = false;
    try {
      const lista = await misCompras(token);
      pagada = lista.some((c) => c.id === pagoLocal?.compraId && c.estado === "PAGADA");
    } catch { /* si no se puede consultar, el carrito se conserva */ }
    onPagada(pagada);
  };

  const cambiarEntrega = (tiendaId, tipo) => setEntregas((e) => ({ ...e, [tiendaId]: tipo }));
  const cambiarEnvio = (e) => setEnvio((v) => ({ ...v, [e.target.name]: e.target.value }));

  const pagar = async () => {
    setPagando(true);
    setErrorPago(null);
    try {
      const data = await iniciarPagoCompra(token, solicitud);
      if (!data?.checkoutUrl) throw new Error("No se pudo iniciar el pago");
      // Se recuerda qué se pagó para vaciarlo del carrito cuando Wompi confirme (aunque el navegador salga a Wompi)
      guardarPagoPendiente(data.compraId, carrito.catalogo.map((x) => x.producto.id));
      if (data.verificacionManual) {
        // Desarrollo local: el backend no ofrece redirección y la persona verifica su pago aquí
        setPagoLocal(data);
        setPagando(false);
      } else {
        window.location.href = data.checkoutUrl;
      }
    } catch (e) {
      setErrorPago(e.message);
      setPagando(false);
    }
  };

  if (grupos.length === 0) {
    return (
      <div className="dm-page mk-pagina">{header}
        <main className="dm-container mk-contenido">
          <div className="mk-vacio">
            <p>No hay productos de tiendas en tu carrito.</p>
            <button className="dm-btn dm-btn--primary" onClick={onVolverCarrito}>Volver al carrito</button>
          </div>
        </main>
      </div>
    );
  }

  const bloqueado = !cotizacion || pagando || !!pagoLocal;

  return (
    <div className="dm-page mk-pagina">
      {header}
      <main className="dm-container mk-contenido">
        <button className="mk-enlace mk-volver" onClick={onVolverCarrito}><Icon name="arrow" size={16} className="ui-volver__icono ui-icono-linea" />Volver al carrito</button>
        <div className="mk-titulo">
          <h1>Entrega y pago</h1>
          <p>Elige cómo recibir el pedido de cada tienda. Pagas una sola vez con Wompi.</p>
        </div>

        {grupos.map((g) => {
          const t = g.tienda;
          const tipo = tipoDe(t.id);
          const detalle = cotizacion?.entregas?.find((e) => e.tiendaId === t.id);
          return (
            <section key={t.id} className="mk-grupo" aria-labelledby={`mk-t-${t.id}`}>
              <h2 id={`mk-t-${t.id}`}>{t.nombre} <span className="mk-tarjeta__meta">· {t.ciudad}</span></h2>
              <ul className="mk-resumen-lineas">
                {g.items.map((x) => (
                  <li key={x.producto.id}><span>{x.cantidad} × {x.producto.nombre}</span></li>
                ))}
              </ul>

              <fieldset className="mk-opciones">
                <legend>¿Cómo lo recibes?</legend>
                <label className={`mk-opcion${tipo === "RETIRO" ? " mk-opcion--activa" : ""}`}>
                  <input type="radio" name={`entrega-${t.id}`} checked={tipo === "RETIRO"} onChange={() => cambiarEntrega(t.id, "RETIRO")} />
                  <span>
                    <strong>Retirar en la tienda · sin costo</strong>
                    <small>{t.direccion}, {t.ciudad} · {t.horario}</small>
                    <small>Te enviamos un código por correo; la tienda lo pide al entregarte el pedido.</small>
                  </span>
                </label>
                <label className={`mk-opcion${tipo === "ENVIO" ? " mk-opcion--activa" : ""}${!t.ofreceEnvio ? " mk-opcion--inactiva" : ""}`}>
                  <input type="radio" name={`entrega-${t.id}`} checked={tipo === "ENVIO"} disabled={!t.ofreceEnvio}
                    onChange={() => cambiarEntrega(t.id, "ENVIO")} />
                  <span>
                    <strong>Envío a domicilio {t.ofreceEnvio ? `· ${formatPrecio(t.costoEnvio)}` : ""}</strong>
                    <small>{t.ofreceEnvio ? `Solo dentro de ${t.ciudad}.` : "Esta tienda no hace envíos: elige retiro."}</small>
                  </span>
                </label>
              </fieldset>

              {detalle && (
                <dl className="mk-totales">
                  <div><dt>Productos</dt><dd>{formatPrecio(detalle.subtotal)}</dd></div>
                  <div><dt>IVA</dt><dd>{formatPrecio(detalle.iva)}</dd></div>
                  <div><dt>Envío</dt><dd>{detalle.tipo === "ENVIO" ? formatPrecio(detalle.costoEnvio) : "Sin costo"}</dd></div>
                  <div className="mk-totales__final"><dt>Total de {t.nombre}</dt><dd>{formatPrecio(detalle.total)}</dd></div>
                </dl>
              )}
            </section>
          );
        })}

        {hayEnvio && (
          <section className="mk-grupo" aria-labelledby="mk-envio-titulo">
            <h2 id="mk-envio-titulo">Dirección de envío</h2>
            <div className="mk-formulario">
              <div className="mk-campo"><label htmlFor="mk-receptor">Quién recibe</label>
                <input id="mk-receptor" name="receptor" value={envio.receptor} onChange={cambiarEnvio} autoComplete="name" /></div>
              <div className="mk-campo"><label htmlFor="mk-telefono">Teléfono</label>
                <input id="mk-telefono" name="telefono" inputMode="tel" value={envio.telefono} onChange={cambiarEnvio} autoComplete="tel" /></div>
              <div className="mk-campo mk-campo--ancho"><label htmlFor="mk-direccion">Dirección</label>
                <input id="mk-direccion" name="direccion" value={envio.direccion} onChange={cambiarEnvio} autoComplete="street-address" /></div>
              <div className="mk-campo"><label htmlFor="mk-ciudad-envio">Ciudad</label>
                <input id="mk-ciudad-envio" name="ciudad" value={envio.ciudad} onChange={cambiarEnvio} autoComplete="address-level2" /></div>
              <div className="mk-campo mk-campo--ancho"><label htmlFor="mk-indicaciones">Indicaciones (opcional)</label>
                <input id="mk-indicaciones" name="indicaciones" maxLength={300} value={envio.indicaciones} onChange={cambiarEnvio} /></div>
            </div>
            {ciudadesNoCoinciden.map((g) => (
              <p key={g.tienda.id} className="mk-aviso mk-aviso--error" role="alert">
                {g.tienda.nombre} solo envía dentro de {g.tienda.ciudad}. Elige retiro en la tienda para esos productos
                o escribe una dirección en {g.tienda.ciudad}.
              </p>
            ))}
            {!envioCompleto && <p className="mk-nota">Completa quién recibe, teléfono, dirección y ciudad para ver el total.</p>}
          </section>
        )}

        {cotizacion?.avisos?.length > 0 && (
          <div className="mk-aviso mk-aviso--aviso" role="status">
            <strong>Ojo con la compatibilidad (no impide la compra):</strong>
            <ul>{cotizacion.avisos.map((a) => <li key={a}>{a}</li>)}</ul>
          </div>
        )}
        {errorCotizacion && <p className="mk-aviso mk-aviso--error" role="alert">{errorCotizacion} Revisa tu carrito y vuelve a intentar.</p>}
        {errorPago && <p className="mk-aviso mk-aviso--error" role="alert">{errorPago}</p>}

        <section className="mk-pago" aria-live="polite">
          <div>
            <span className="mk-tarjeta__meta">Total a pagar</span>
            <strong className="mk-pago__total">
              {calculando ? "Calculando..." : cotizacion ? formatPrecio(cotizacion.total) : "—"}
            </strong>
            {cotizacion && <span className="mk-tarjeta__meta">Productos {formatPrecio(cotizacion.subtotal)} · IVA {formatPrecio(cotizacion.iva)} · Envío {formatPrecio(cotizacion.costoEnvio)}</span>}
          </div>
          <button className="dm-btn dm-btn--primary" onClick={pagar} disabled={bloqueado}>
            {pagando ? "Preparando el pago..." : "Pagar con Wompi"}
          </button>
        </section>
        <p className="mk-nota">El valor lo calcula DriveMaster al pagar. Antes de cobrar revisamos que haya stock en todas las tiendas; si algo se agota después, te devolvemos todo el pago.</p>

        {pagoLocal && (
          <PagoLocal token={token} pago={pagoLocal} onCerrar={() => setPagoLocal(null)} onVerificado={alVerificar} />
        )}
      </main>
    </div>
  );
}
