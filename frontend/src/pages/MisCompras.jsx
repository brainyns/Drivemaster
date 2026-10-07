import { useCallback, useEffect, useState } from "react";
import { getToken } from "../services/authService";
import { misCompras, pedirCodigoRetiro, confirmarRecepcion, reportarProblema } from "../services/catalogoService";
import { formatFechaHora, formatPrecio } from "../components/servicio/estadosServicio";
import { ESTADO_COMPRA, ESTADOS_CON_CODIGO, estadoPedido } from "../components/marketplace/estadosCompra";
import ConfirmarAccion from "../components/marketplace/ConfirmarAccion";
import "../css/marketplace.css";

const refCompra = (id) => (id || "").slice(-6).toUpperCase();

/**
 * Compras del catálogo del cliente: estado de cada pedido por tienda. El código de retiro NUNCA se
 * muestra aquí: llega por correo y, si se pierde, se pide uno nuevo (que reemplaza al anterior).
 * "Ya lo recibí" y "Reportar un problema" piden confirmación.
 */
export default function MisCompras({ header, onVolver, onIrCatalogo }) {
  const token = getToken();
  const [compras, setCompras] = useState(null);
  const [error, setError] = useState(null);
  const [aviso, setAviso] = useState(null);
  const [accion, setAccion] = useState(null); // { tipo: "codigo"|"recibido"|"problema", pedido }

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

  return (
    <div className="mk-pagina">
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

        {compras && compras.length === 0 && (
          <div className="mk-vacio">
            <p>Aún no has comprado en las tiendas del catálogo.</p>
            <button className="dm-btn dm-btn--primary" onClick={onIrCatalogo}>Ver repuestos</button>
          </div>
        )}

        {(compras || []).map((c) => {
          const estado = ESTADO_COMPRA[c.estado] || { label: c.estado, tono: "aviso" };
          return (
            <section key={c.id} className="mk-grupo" aria-labelledby={`mk-c-${c.id}`}>
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
        })}

        <button className="mk-enlace mk-volver" onClick={onVolver}>← Volver al inicio</button>
      </main>

      {accion && (
        <ConfirmarAccion {...config[accion.tipo]} onConfirmar={confirmar} onCancelar={() => setAccion(null)} />
      )}
    </div>
  );
}
