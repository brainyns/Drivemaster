import { useCallback, useEffect, useRef, useState } from "react";
import { getToken } from "../services/authService";
import { misCompras, pedirCodigoRetiro, confirmarRecepcion, reportarProblema } from "../services/catalogoService";
import { misVentas } from "../services/ventaService";
import { misSolicitudes } from "../services/solicitudService";
import { formatFechaHora, formatPrecio } from "../components/servicio/estadosServicio";
import { ESTADO_COMPRA, ESTADOS_CON_CODIGO, estadoPedido } from "../components/marketplace/estadosCompra";
import { subscribeNotifications } from "../services/websocketService";
import { clasificarCompras, estadoVentaAnterior, solicitudesSinCerrar, pestanaDeCompra } from "../components/marketplace/clasificarCompras";
import ConfirmarAccion from "../components/marketplace/ConfirmarAccion";
import "../css/marketplace.css";
import "../css/mis-compras.css";

const refCompra = (id) => (id || "").slice(-6).toUpperCase();

const PESTANAS = [
  { id: "enCurso", label: "En curso", vacio: "No tienes compras en curso." },
  { id: "anteriores", label: "Anteriores", vacio: "Aún no tienes compras anteriores." },
];

/**
 * Compras del cliente en dos pestañas: "En curso" y "Anteriores". Une las compras del catálogo
 * (pedidos por tienda) con las ventas del flujo anterior que mostraba el Historial de compras,
 * marcadas como "Sistema anterior" (ver clasificarCompras.js). Si quedan solicitudes del carrito
 * anterior sin cerrar, "En curso" avisa y lleva a Mis solicitudes, donde siguen su pago y estado.
 *
 * El código de retiro NUNCA se muestra aquí: llega por correo y, si se pierde, se pide uno nuevo
 * (que reemplaza al anterior). "Ya lo recibí" y "Reportar un problema" piden confirmación.
 */
export default function MisCompras({ header, compraInicial = null, onVolver, onIrCatalogo, onIrSolicitudes, onIrHistorial }) {
  const token = getToken();
  const [compras, setCompras] = useState(null);
  const [error, setError] = useState(null);
  // Ventas del flujo anterior: si fallan, el resto de la pantalla sigue funcionando
  const [ventas, setVentas] = useState([]);
  const [errorVentas, setErrorVentas] = useState(false);
  const [solicitudesAbiertas, setSolicitudesAbiertas] = useState(0);
  // null: la elige la compra que se abrió desde un aviso (o "En curso")
  const [pestanaElegida, setPestana] = useState(null);
  const compraVista = useRef(false);
  const [aviso, setAviso] = useState(null);
  const [accion, setAccion] = useState(null); // { tipo: "codigo"|"recibido"|"problema", pedido }
  const pestanasRef = useRef(null);

  // Recarga tras una acción (confirmar, reportar, pedir código)
  const cargar = useCallback(async () => {
    try {
      setCompras(await misCompras(token));
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, [token]);

  // Carga inicial
  useEffect(() => {
    let vivo = true;
    misCompras(token)
      .then((c) => { if (vivo) setCompras(c); })
      .catch((e) => { if (vivo) setError(e.message); });
    misVentas(token)
      .then((v) => { if (vivo) setVentas(Array.isArray(v) ? v : []); })
      .catch(() => { if (vivo) setErrorVentas(true); });
    // Solo para el aviso: si falla, no se muestra
    misSolicitudes(token)
      .then((s) => { if (vivo) setSolicitudesAbiertas(solicitudesSinCerrar(Array.isArray(s) ? s : []).length); })
      .catch(() => {});
    return () => { vivo = false; };
  }, [token]);

  const confirmar = async (motivo) => {
    const { tipo, pedido } = accion;
    if (tipo === "codigo") {
      const r = await pedirCodigoRetiro(token, pedido.id);
      setAviso(r.aviso || "Te enviamos un código nuevo a tu correo.");
    } else if (tipo === "recibido") {
      await confirmarRecepcion(token, pedido.id);
      setAviso("¡Gracias! Quedó registrado que recibiste tu pedido.");
    } else {
      await reportarProblema(token, pedido.id, motivo);
      setAviso("Recibimos tu reporte. DriveMaster lo revisará y la tienda no recibirá el pago mientras tanto.");
    }
    setAccion(null);
    await cargar();
  };

  const config = {
    codigo: {
      titulo: "Pedir un código de retiro nuevo",
      mensaje: "Te lo enviaremos por correo. El código anterior dejará de servir y no se puede recuperar.",
      textoConfirmar: "Enviar código nuevo",
    },
    recibido: {
      titulo: "Confirmar que recibiste el pedido",
      mensaje: "Al confirmar, la tienda recibe el pago de este pedido. Hazlo solo si ya lo tienes contigo.",
      textoConfirmar: "Sí, ya lo recibí",
    },
    problema: {
      titulo: "Reportar un problema con el pedido",
      mensaje: "Pausamos el pago a la tienda y DriveMaster revisará tu caso. Cuéntanos qué pasó.",
      textoConfirmar: "Enviar reporte",
      pedirMotivo: true,
      etiquetaMotivo: "Qué pasó",
      ayudaMotivo: "Por ejemplo: no llegó, llegó dañado o es otro repuesto.",
      peligro: true,
    },
  };

  // Un aviso nuevo de mis compras (pedido enviado, listo para retirar…) actualiza la lista
  useEffect(() => subscribeNotifications((msg) => {
    if (msg?.type === "notificacion" && msg.notificacion?.enlace === "mis-compras") cargar();
  }), [cargar]);

  const grupos = compras ? clasificarCompras(compras, ventas) : null;
  const pestana = pestanaElegida ?? pestanaDeCompra(grupos, compraInicial);

  // Desde un aviso de la campanita: se lleva la vista a esa compra una sola vez, cuando ya cargó
  useEffect(() => {
    if (!compraInicial || !compras || compraVista.current) return;
    const el = document.getElementById(`mk-compra-${compraInicial}`);
    if (!el) return;
    compraVista.current = true;
    el.scrollIntoView({ block: "start", behavior: "smooth" });
    el.focus({ preventScroll: true });
  }, [compraInicial, compras]);
  const sinNada = grupos && grupos.enCurso.length === 0 && grupos.anteriores.length === 0;

  // Pestañas: flechas izquierda y derecha cambian de pestaña (patrón de pestañas WAI-ARIA)
  const onTeclaPestanas = (e) => {
    if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
    e.preventDefault();
    const i = PESTANAS.findIndex((t) => t.id === pestana);
    const siguiente = PESTANAS[(i + (e.key === "ArrowRight" ? 1 : PESTANAS.length - 1)) % PESTANAS.length];
    setPestana(siguiente.id);
    pestanasRef.current?.querySelector(`#mc-tab-${siguiente.id}`)?.focus();
  };

  const renderCompra = (c) => {
    const estado = ESTADO_COMPRA[c.estado] || { label: c.estado, tono: "aviso" };
    return (
      <section key={c.id} id={`mk-compra-${c.id}`} tabIndex={c.id === compraInicial ? -1 : undefined}
        className={`mk-grupo${c.id === compraInicial ? " mk-grupo--resaltada" : ""}`} aria-labelledby={`mk-c-${c.id}`}>
        <header className="mk-compra__cabecera">
          <div>
            <h2 id={`mk-c-${c.id}`}>Compra #{refCompra(c.id)}</h2>
            <span className="mk-tarjeta__meta">{formatFechaHora(c.fechaPago || c.fechaCreacion)}</span>
          </div>
          <div className="mk-compra__resumen">
            <span className={`mk-etiqueta mk-etiqueta--${estado.tono}`}>{estado.label}</span>
            <strong>{formatPrecio(c.total)}</strong>
          </div>
        </header>

        {c.estado === "NO_APLICADA" && (
          <p className="mk-aviso mk-aviso--error">
            {c.motivoNoAplicada || "No pudimos completar tu compra."} No se envió ningún pedido: DriveMaster te devolverá el pago completo
            y te avisará por correo.
          </p>
        )}
        {c.estado === "PENDIENTE_PAGO" && (
          <p className="mk-nota">Esta compra no se pagó. Si ya pagaste, puede tardar unos segundos en confirmarse: actualiza la página.</p>
        )}

        {c.pedidos.map((p) => {
          const e = estadoPedido(p);
          const retiro = p.entrega === "RETIRO";
          return (
            <article key={p.id} className="mk-pedido">
              <div className="mk-pedido__cabecera">
                <h3>{p.tiendaNombre}</h3>
                <span className={`mk-etiqueta mk-etiqueta--${e.tono}`}>{e.label}</span>
              </div>
              <p className="mk-tarjeta__meta">
                {retiro ? "Retiro en la tienda" : "Envío a domicilio"} · {formatPrecio(p.total)}
                {!retiro && p.costoEnvio > 0 ? ` (envío ${formatPrecio(p.costoEnvio)})` : ""}
              </p>
              <p>{e.detalle}</p>

              {retiro && (
                <p className="mk-tarjeta__meta">
                  {p.tiendaDireccion}, {p.tiendaCiudad} · {p.tiendaHorario}
                </p>
              )}
              {!retiro && p.envioDireccion && <p className="mk-tarjeta__meta">Entrega en {p.envioDireccion}, {p.envioCiudad}</p>}
              {!retiro && p.notaDespacho && <p className="mk-tarjeta__meta">Información del envío: {p.notaDespacho}</p>}
              {p.estado === "DESPACHADO" && !p.problemaReportado && p.autoconfirmaDespuesDe && (
                <p className="mk-tarjeta__meta">
                  Si no respondes, lo daremos por recibido el {formatFechaHora(p.autoconfirmaDespuesDe)}.
                </p>
              )}
              {p.plazoDespachoHasta && ["PENDIENTE", "EN_PREPARACION"].includes(p.estado) && (
                <p className="mk-tarjeta__meta">La tienda tiene hasta el {formatFechaHora(p.plazoDespachoHasta)} para {retiro ? "dejarlo listo" : "despacharlo"}.</p>
              )}

              <div className="mk-pedido__acciones">
                {retiro && ESTADOS_CON_CODIGO.includes(p.estado) && (
                  <>
                    <span className="mk-tarjeta__meta">Tu código de retiro llegó a tu correo.</span>
                    <button className="dm-btn dm-btn--secondary" onClick={() => setAccion({ tipo: "codigo", pedido: p })}>
                      Pedir un código nuevo
                    </button>
                  </>
                )}
                {!retiro && p.estado === "DESPACHADO" && !p.problemaReportado && (
                  <>
                    <button className="dm-btn dm-btn--primary" onClick={() => setAccion({ tipo: "recibido", pedido: p })}>Ya lo recibí</button>
                    <button className="dm-btn dm-btn--secondary" onClick={() => setAccion({ tipo: "problema", pedido: p })}>Reportar un problema</button>
                  </>
                )}
              </div>
            </article>
          );
        })}

        <details className="mk-compra__detalle">
          <summary>Ver productos y totales</summary>
          <ul className="mk-resumen-lineas">
            {c.lineas.map((l) => (
              <li key={l.productoId}><span>{l.cantidad} × {l.productoNombre} <small>({l.tiendaNombre})</small></span><span>{formatPrecio(l.subtotal)}</span></li>
            ))}
          </ul>
          <dl className="mk-totales">
            <div><dt>Productos</dt><dd>{formatPrecio(c.subtotal)}</dd></div>
            <div><dt>IVA</dt><dd>{formatPrecio(c.iva)}</dd></div>
            <div><dt>Envío</dt><dd>{formatPrecio(c.costoEnvio)}</dd></div>
            <div className="mk-totales__final"><dt>Total</dt><dd>{formatPrecio(c.total)}</dd></div>
          </dl>
        </details>
      </section>
    );
  };

  // Venta del flujo anterior (lo que antes estaba en Historial de compras): solo consulta
  const renderVentaAnterior = (v) => {
    const estado = estadoVentaAnterior(v);
    return (
      <section key={v.id} className="mk-grupo" aria-labelledby={`mk-v-${v.id}`}>
        <header className="mk-compra__cabecera">
          <div>
            <h2 id={`mk-v-${v.id}`}>Compra #{refCompra(v.id)}</h2>
            <span className="mk-tarjeta__meta">{formatFechaHora(v.fecha)}</span>
          </div>
          <div className="mk-compra__resumen">
            <span className={`mk-etiqueta mk-etiqueta--${estado.tono}`}>{estado.label}</span>
            <strong>{formatPrecio(v.total)}</strong>
          </div>
        </header>
        <p className="mk-tarjeta__meta">
          <span className="mc-origen">Sistema anterior</span>
          {v.pagos?.[0]?.metodo ? ` · Pago: ${v.pagos[0].metodo}` : ""}
          {v.fechaEnvio ? ` · Enviada el ${formatFechaHora(v.fechaEnvio)}` : ""}
          {v.fechaEntrega ? ` · Entregada el ${formatFechaHora(v.fechaEntrega)}` : ""}
        </p>
        {v.productos?.length > 0 && (
          <ul className="mk-resumen-lineas">
            {v.productos.map((p, i) => (
              <li key={i}><span>{p.cantidad} × {p.nombre}</span><span>{formatPrecio(p.subtotal)}</span></li>
            ))}
          </ul>
        )}
      </section>
    );
  };

  const lista = grupos ? grupos[pestana] : [];
  const actual = PESTANAS.find((t) => t.id === pestana);
  const hayVentasAnteriores = grupos?.anteriores.some((x) => x.origen === "anterior");

  const avisoSolicitudes = solicitudesAbiertas > 0 && (
    <p className="mc-aviso" role="status">
      <span>
        Tienes {solicitudesAbiertas} {solicitudesAbiertas === 1 ? "solicitud" : "solicitudes"} del sistema anterior
      </span>
      <span aria-hidden="true">·</span>
      <button className="mk-enlace" onClick={onIrSolicitudes}>Ver</button>
    </p>
  );

  return (
    <div className="dm-page mk-pagina">
      {header}
      <main className="dm-container mk-contenido">
        <div className="mk-titulo">
          <span className="dm-eyebrow"><span className="dm-dot" /> MIS COMPRAS</span>
          <h1>Mis compras</h1>
          <p>El estado de lo que compraste en las tiendas del catálogo.</p>
        </div>

        {aviso && <p className="mk-toast" role="status">{aviso} <button className="mk-enlace" onClick={() => setAviso(null)}>Cerrar</button></p>}
        {error && <p className="mk-aviso mk-aviso--error" role="alert">{error}</p>}
        {!compras && !error && <p className="mk-conteo">Cargando...</p>}

        {sinNada && avisoSolicitudes}
        {sinNada && (
          <div className="mk-vacio">
            <p>Aún no has comprado en las tiendas del catálogo.</p>
            <button className="dm-btn dm-btn--primary" onClick={onIrCatalogo}>Ver repuestos</button>
          </div>
        )}

        {grupos && !sinNada && (
          <>
            <div className="mc-pestanas" role="tablist" aria-label="Mis compras" ref={pestanasRef} onKeyDown={onTeclaPestanas}>
              {PESTANAS.map((t) => (
                <button key={t.id} id={`mc-tab-${t.id}`} role="tab" className="mc-pestana"
                  aria-selected={pestana === t.id} aria-controls="mc-panel" tabIndex={pestana === t.id ? 0 : -1}
                  onClick={() => setPestana(t.id)}>
                  {t.label} <span className="mc-pestana__contador">{grupos[t.id].length}</span>
                </button>
              ))}
            </div>

            <div id="mc-panel" role="tabpanel" aria-labelledby={`mc-tab-${pestana}`} className="mc-panel">
              {pestana === "enCurso" && avisoSolicitudes}
              {lista.length === 0 && <p className="mc-vacio">{actual.vacio}</p>}
              {lista.map((x) => (x.origen === "catalogo" ? renderCompra(x.dato) : renderVentaAnterior(x.dato)))}
              {pestana === "anteriores" && hayVentasAnteriores && onIrHistorial && (
                <p className="mk-nota">
                  ¿Buscas el detalle con el seguimiento de envío del sistema anterior?{" "}
                  <button className="mk-enlace" onClick={onIrHistorial}>Ver el historial anterior</button>
                </p>
              )}
              {errorVentas && (
                <p className="mk-nota" role="status">No pudimos cargar tus compras del sistema anterior. Intenta de nuevo más tarde.</p>
              )}
            </div>
          </>
        )}

        <button className="mk-enlace mk-volver" onClick={onVolver}>← Volver al inicio</button>
      </main>

      {accion && (
        <ConfirmarAccion {...config[accion.tipo]} onConfirmar={confirmar} onCancelar={() => setAccion(null)} />
      )}
    </div>
  );
}
